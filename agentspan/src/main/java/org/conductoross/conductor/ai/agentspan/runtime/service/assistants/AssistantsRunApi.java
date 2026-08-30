/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.ai.agentspan.runtime.service.assistants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The OpenAI Assistants thread-and-run protocol, shared by every endpoint that speaks it — OpenAI
 * itself and Microsoft Foundry, which differ only in how they authenticate, where they live, and
 * which extra query parameters and headers they want.
 *
 * <p>Deliberately stateless. A thread id plus a {@link Target} rebuilt from task input is enough to
 * reach any run, so callers keep nothing between calls and any replica can serve any request. The
 * run to act on is always the newest one on the thread, which the endpoint names on request; that
 * is also what lets a new run started by {@link #addMessageAndStartRun} stay reachable under the
 * unchanged thread id.
 */
public class AssistantsRunApi {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Logger LOG = LoggerFactory.getLogger(AssistantsRunApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    private final OkHttpClient httpClient;

    public AssistantsRunApi(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Where an assistant lives and how to talk to it.
     *
     * @param baseUrl endpoint root, no trailing slash
     * @param assistantId the assistant or agent to run
     * @param query extra query string every request carries, without a leading "?" — Azure's {@code
     *     api-version}; empty for OpenAI
     * @param headers extra headers every request carries beyond Authorization — OpenAI's {@code
     *     OpenAI-Beta}; may be empty
     */
    public record Target(
            String baseUrl, String assistantId, String query, Map<String, String> headers) {}

    /**
     * Creates a thread, posts the prompt, starts a run, and returns the thread id. Note, this is
     * not about JVM thread, but rather the agent conversation thread
     */
    public String createThreadAndRun(Target target, AssistantsAuth auth, String prompt) {
        JsonNode thread = post(target, auth, "/threads", MAPPER.createObjectNode());
        String threadId = thread.path("id").asText();

        ObjectNode message = MAPPER.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);
        post(target, auth, "/threads/" + threadId + "/messages", message);

        startRun(target, auth, threadId);
        return threadId;
    }

    /**
     * The newest run on the thread, returned with its full status in a single call — so locating
     * the current run costs no more than fetching a remembered run id would.
     */
    public JsonNode latestRun(Target target, AssistantsAuth auth, String threadId) {
        JsonNode runs = get(target, auth, "/threads/" + threadId + "/runs", "limit=1&order=desc");
        JsonNode data = runs.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException(
                    "Assistants thread " + threadId + " has no runs to report on");
        }
        return data.get(0);
    }

    /** Maps the newest run on the thread onto a Conductor status snapshot. */
    public ConductorAgentStatusResponse status(
            Target target, AssistantsAuth auth, String threadId) {
        JsonNode run = latestRun(target, auth, threadId);
        ConductorAgentState state = toState(run.path("status").asText("queued"));
        boolean complete =
                state == ConductorAgentState.COMPLETED
                        || state == ConductorAgentState.FAILED
                        || state == ConductorAgentState.CANCELED;

        Map<String, Object> output = null;
        Map<String, Object> pendingTool = null;
        List<Map<String, Object>> pendingTools = List.of();
        String pendingToolName = null;
        String reason = null;

        List<Map<String, Object>> executedTools = List.of();

        if (state == ConductorAgentState.COMPLETED) {
            output = latestAssistantMessage(target, auth, threadId);
            executedTools = executedToolCalls(target, auth, threadId, run.path("id").asText());
        } else if (state == ConductorAgentState.WAITING) {
            pendingTools = describeToolCalls(run);
            if (!pendingTools.isEmpty()) {
                pendingTool = pendingTools.get(0);
                pendingToolName = String.valueOf(pendingTool.get("tool_name"));
            }
        } else if (state == ConductorAgentState.FAILED) {
            reason = run.path("last_error").path("message").asText("Run failed");
        }

        return ConductorAgentStatusResponse.builder()
                .executionId(threadId)
                .status(state)
                .complete(complete)
                .running(state == ConductorAgentState.RUNNING)
                .waiting(state == ConductorAgentState.WAITING)
                .output(output)
                .pendingTool(pendingTool)
                .pendingTools(pendingTools)
                .pendingToolName(pendingToolName)
                .executedTools(executedTools)
                .reasonForIncompletion(reason)
                .build();
    }

    /**
     * Answers the tool calls a run is blocked on, each with its own result.
     *
     * <p>The provider will not resume until every outstanding call has an output, which is what
     * used to justify replaying one result across all of them — a reply the API accepts and the
     * model then reasons from, even though it answers a question no tool was asked. So a reply that
     * does not cover every call is rejected here instead: a wrong answer that looks right is worse
     * than a failed task.
     *
     * @param resultsByToolCallId one entry per {@code tool_call_id}, already serialized
     */
    public void submitToolOutputs(
            Target target,
            AssistantsAuth auth,
            String threadId,
            JsonNode run,
            Map<String, String> resultsByToolCallId) {
        ObjectNode body = MAPPER.createObjectNode();
        ArrayNode outputs = body.putArray("tool_outputs");
        List<String> unanswered = new ArrayList<>();
        for (JsonNode toolCall : toolCalls(run)) {
            String toolCallId = toolCall.path("id").asText();
            String result = resultsByToolCallId.get(toolCallId);
            if (result == null) {
                unanswered.add(
                        toolCall.path("function").path("name").asText("unknown")
                                + " ("
                                + toolCallId
                                + ")");
                continue;
            }
            ObjectNode output = outputs.addObject();
            output.put("tool_call_id", toolCallId);
            output.put("output", result);
        }
        if (!unanswered.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot resume run "
                            + run.path("id").asText()
                            + ": no result supplied for "
                            + unanswered
                            + ". Every tool the agent asked for must be answered.");
        }
        post(
                target,
                auth,
                "/threads/"
                        + threadId
                        + "/runs/"
                        + run.path("id").asText()
                        + "/submit_tool_outputs",
                body);
    }

    /** Continues the conversation: appends a user message and starts a fresh run on the thread. */
    public void addMessageAndStartRun(
            Target target, AssistantsAuth auth, String threadId, String content) {
        ObjectNode message = MAPPER.createObjectNode();
        message.put("role", "user");
        message.put("content", content);
        post(target, auth, "/threads/" + threadId + "/messages", message);
        startRun(target, auth, threadId);
    }

    /** Cancels whichever run is currently newest on the thread. */
    public void cancelLatestRun(Target target, AssistantsAuth auth, String threadId) {
        String runId = latestRun(target, auth, threadId).path("id").asText();
        post(
                target,
                auth,
                "/threads/" + threadId + "/runs/" + runId + "/cancel",
                MAPPER.createObjectNode());
    }

    /**
     * Every tool call on a blocked run, in the order the model asked for them. The first is also
     * surfaced as {@code pendingTool} for callers that only handle one tool per turn.
     */
    public static List<Map<String, Object>> describeToolCalls(JsonNode run) {
        List<Map<String, Object>> described = new ArrayList<>();
        for (JsonNode toolCall : toolCalls(run)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tool_name", toolCall.path("function").path("name").asText("unknown"));
            entry.put("tool_call_id", toolCall.path("id").asText());
            entry.put("arguments", toolCall.path("function").path("arguments").asText("{}"));
            described.add(entry);
        }
        return described;
    }

    public static JsonNode toolCalls(JsonNode run) {
        return run.path("required_action").path("submit_tool_outputs").path("tool_calls");
    }

    private void startRun(Target target, AssistantsAuth auth, String threadId) {
        ObjectNode runBody = MAPPER.createObjectNode();
        runBody.put("assistant_id", target.assistantId());
        post(target, auth, "/threads/" + threadId + "/runs", runBody);
    }

    /**
     * The tool calls the platform ran itself during this run, from its steps.
     *
     * <p>A built-in tool - code interpreter, file search - runs inside the platform and never sets
     * requires_action, so it never appears in the pendingTools the workflow is asked to run. The
     * run object does not carry it either; only the run's steps do. Fetched once, when the run
     * reaches a terminal state, rather than on every poll.
     *
     * <p>Best effort: a run that finished correctly is not failed over missing step detail.
     */
    private List<Map<String, Object>> executedToolCalls(
            Target target, AssistantsAuth auth, String threadId, String runId) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        try {
            JsonNode steps =
                    get(
                            target,
                            auth,
                            "/threads/" + threadId + "/runs/" + runId + "/steps",
                            "order=asc");
            List<Map<String, Object>> calls = new ArrayList<>();
            for (JsonNode step : steps.path("data")) {
                for (JsonNode call : step.path("step_details").path("tool_calls")) {
                    String type = call.path("type").asText("");
                    Map<String, Object> described = new LinkedHashMap<>();
                    described.put("type", type);
                    described.put("tool_call_id", call.path("id").asText(""));
                    described.put("status", step.path("status").asText(""));
                    // Each tool nests its own detail under a key named after itself.
                    JsonNode detail = call.path(type);
                    if (!detail.isMissingNode() && !detail.isNull()) {
                        described.put("input", MAPPER.convertValue(detail, Object.class));
                    }
                    calls.add(described);
                }
            }
            return calls;
        } catch (Exception e) {
            LOG.warn(
                    "Could not read run steps for thread {} run {}: {}",
                    threadId,
                    runId,
                    e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> latestAssistantMessage(
            Target target, AssistantsAuth auth, String threadId) {
        // order=desc so the first assistant message is the newest, rather than relying on the
        // endpoint's default ordering.
        JsonNode messages = get(target, auth, "/threads/" + threadId + "/messages", "order=desc");
        for (JsonNode message : messages.path("data")) {
            if ("assistant".equals(message.path("role").asText())) {
                return Map.of("result", extractText(message));
            }
        }
        return null;
    }

    /**
     * The text part of an assistant message.
     *
     * <p>Scans the parts rather than taking the first: an assistant with code interpreter returns
     * an {@code image_file} part ahead of the text, so {@code content[0].text} is empty for exactly
     * the agents most likely to produce a chart.
     */
    private static String extractText(JsonNode message) {
        for (JsonNode part : message.path("content")) {
            if ("text".equals(part.path("type").asText())) {
                return part.path("text").path("value").asText("");
            }
        }
        return "";
    }

    private static ConductorAgentState toState(String status) {
        return switch (status) {
            case "completed" -> ConductorAgentState.COMPLETED;
            case "failed", "expired" -> ConductorAgentState.FAILED;
            case "cancelled" -> ConductorAgentState.CANCELED;
            case "requires_action" -> ConductorAgentState.WAITING;
            default -> ConductorAgentState.RUNNING; // queued, in_progress
        };
    }

    private JsonNode post(Target target, AssistantsAuth auth, String path, ObjectNode body) {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        return execute(
                request(target, auth, path, null).post(RequestBody.create(bytes, JSON)).build(),
                path);
    }

    private JsonNode get(Target target, AssistantsAuth auth, String path, String extraQuery) {
        return execute(request(target, auth, path, extraQuery).get().build(), path);
    }

    private Request.Builder request(
            Target target, AssistantsAuth auth, String path, String extraQuery) {
        StringBuilder url = new StringBuilder(target.baseUrl()).append(path);
        String query = joinQuery(target.query(), extraQuery);
        if (!query.isEmpty()) {
            url.append('?').append(query);
        }
        Request.Builder builder =
                new Request.Builder()
                        .url(url.toString())
                        .header(auth.headerName(), auth.headerValue());
        if (target.headers() != null) {
            target.headers().forEach(builder::header);
        }
        return builder;
    }

    private static String joinQuery(String base, String extra) {
        String left = base == null ? "" : base;
        String right = extra == null ? "" : extra;
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + "&" + right;
    }

    private JsonNode execute(Request request, String label) {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (response.code() == 401 || response.code() == 403) {
                // Distinguished so the caller can drop a cached credential: the likeliest cause is
                // a key or secret rotated since the token was built.
                throw new UnauthorizedException(
                        "Assistants API call to "
                                + label
                                + " was rejected: HTTP "
                                + response.code());
            }
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "Assistants API call to "
                                + label
                                + " failed: HTTP "
                                + response.code()
                                + " — "
                                + body);
            }
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new RuntimeException("Assistants API call to " + label + " failed", e);
        }
    }

    /** The endpoint rejected our bearer token. */
    public static class UnauthorizedException extends RuntimeException {

        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
