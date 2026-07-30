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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Best-effort terminal-workflow exporter for OCG agent-run capture.
 *
 * <p>The exporter sends raw durable run data and never summarizes it. OCG owns folding,
 * summarization, fallback behavior, versioning, ranking, feedback, and retention.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class OcgAgentRunExporter implements WorkflowStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcgAgentRunExporter.class);
    private static final int MAX_REQUEST_BYTES = 10 * 1024 * 1024;
    private static final int TARGET_REQUEST_BYTES = 9_500_000;
    private static final Set<String> TOOL_TYPES = Set.of("SIMPLE", "HTTP", "CALL_MCP_TOOL");
    private static final Set<String> INTERNAL_TYPES =
            Set.of(
                    "LLM_CHAT_COMPLETE",
                    "LIST_MCP_TOOLS",
                    "LIST_API_TOOLS",
                    "SWITCH",
                    "DO_WHILE",
                    "INLINE",
                    "SET_VARIABLE",
                    "FORK_JOIN_DYNAMIC",
                    "JOIN",
                    "HUMAN",
                    "TERMINATE");

    private final ObjectMapper mapper;
    private final Function<String, String> credentialResolver;
    private final HttpClient client;
    private final Duration timeout;
    private final int maxAttempts;

    @Autowired
    public OcgAgentRunExporter(
            ObjectMapper mapper, CredentialResolutionService credentialResolutionService) {
        this(
                mapper,
                credentialResolutionService::resolve,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(5),
                2);
    }

    OcgAgentRunExporter(
            ObjectMapper mapper,
            Function<String, String> credentialResolver,
            HttpClient client,
            Duration timeout,
            int maxAttempts) {
        this.mapper = mapper;
        this.credentialResolver = credentialResolver;
        this.client = client;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void onWorkflowCompletedIfEnabled(WorkflowModel workflow) {
        export(workflow);
    }

    @Override
    public void onWorkflowTerminatedIfEnabled(WorkflowModel workflow) {
        export(workflow);
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        export(workflow);
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        export(workflow);
    }

    /** Starts capture without waiting for OCG; all failures are contained in the returned stage. */
    CompletionStage<Void> export(WorkflowModel workflow) {
        if (workflow == null || workflow.hasParent())
            return CompletableFuture.completedFuture(null);

        LongTermMemoryConfig config = memoryConfig(workflow);
        if (config == null || isBlank(config.getOcgUrl()) || isBlank(config.getCredential())) {
            return CompletableFuture.completedFuture(null);
        }

        String credential;
        byte[] body;
        Map<String, Object> payload;
        try {
            credential = credentialResolver.apply(config.getCredential());
            if (isBlank(credential)) {
                LOGGER.warn(
                        "Skipping OCG run capture for workflow {}: credential '{}' is unavailable",
                        workflow.getWorkflowId(),
                        config.getCredential());
                return CompletableFuture.completedFuture(null);
            }
            payload = buildPayload(workflow, config);
            body = encodeWithinLimit(payload);
            if (body.length > MAX_REQUEST_BYTES) {
                LOGGER.warn(
                        "Skipping OCG run capture for workflow {}: input and result exceed the OCG request limit",
                        workflow.getWorkflowId());
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception e) {
            LOGGER.warn(
                    "Unable to prepare OCG run capture for workflow {}: {}",
                    workflow.getWorkflowId(),
                    e.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        URI endpoint =
                URI.create(config.getOcgUrl().replaceAll("/+$", "") + "/api/v1/memories/agent-run");
        HttpRequest request =
                HttpRequest.newBuilder(endpoint)
                        .timeout(timeout)
                        .header("X-API-Key", credential)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        String sessionId = String.valueOf(payload.get("session_id"));
        return send(request, workflow.getWorkflowId(), sessionId, 1);
    }

    private CompletionStage<Void> send(
            HttpRequest request, String workflowId, String sessionId, int attempt) {
        CompletableFuture<HttpResponse<Void>> response;
        try {
            response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            return failedAttempt(request, workflowId, sessionId, attempt, e);
        }
        return response.handle(
                        (value, error) -> {
                            if (error != null)
                                return failedAttempt(
                                        request, workflowId, sessionId, attempt, error);
                            if (value.statusCode() >= 500 && attempt < maxAttempts) {
                                return send(request, workflowId, sessionId, attempt + 1);
                            }
                            if (value.statusCode() != 202) {
                                LOGGER.warn(
                                        "OCG run capture for workflow {} returned HTTP {}",
                                        workflowId,
                                        value.statusCode());
                            } else {
                                LOGGER.debug(
                                        "Queued OCG run capture for workflow {}, session {}",
                                        workflowId,
                                        sessionId);
                            }
                            return CompletableFuture.<Void>completedFuture(null);
                        })
                .thenCompose(Function.identity());
    }

    private CompletionStage<Void> failedAttempt(
            HttpRequest request,
            String workflowId,
            String sessionId,
            int attempt,
            Throwable error) {
        if (attempt < maxAttempts) return send(request, workflowId, sessionId, attempt + 1);
        LOGGER.warn(
                "OCG run capture unavailable for workflow {} after {} attempts: {}",
                workflowId,
                attempt,
                rootMessage(error));
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private LongTermMemoryConfig memoryConfig(WorkflowModel workflow) {
        WorkflowDef definition = workflow.getWorkflowDefinition();
        if (definition == null || definition.getMetadata() == null) return null;
        Object agentDef = definition.getMetadata().get("agentDef");
        if (!(agentDef instanceof Map<?, ?> map)) return null;
        Object memory = map.get("longTermMemory");
        if (!(memory instanceof Map<?, ?>)) return null;
        return mapper.convertValue(memory, LongTermMemoryConfig.class);
    }

    Map<String, Object> buildPayload(WorkflowModel workflow, LongTermMemoryConfig config) {
        Map<String, Object> input = workflow.getInput() == null ? Map.of() : workflow.getInput();
        Map<String, Object> output = workflow.getOutput() == null ? Map.of() : workflow.getOutput();
        String sessionId = stringValue(input.get("session_id"), workflow.getWorkflowId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", stringValue(config.getAgent(), "agentspan"));
        String user = stringValue(config.getUser(), stringValue(input.get("user"), null));
        if (!isBlank(user)) payload.put("user", user.startsWith("user:") ? user : "user:" + user);
        payload.put("session_id", sessionId);
        payload.put("turn_id", workflow.getWorkflowId());
        copyString(input, payload, "repo");
        copyString(input, payload, "branch");
        copyString(input, payload, "cwd");
        payload.put("input", stringValue(input.get("prompt"), ""));
        payload.put("events", events(workflow));
        payload.put("result", jsonString(output.get("result")));
        payload.put("outcome", outcome(workflow));
        long startedAt = startTime(workflow);
        long endedAt = workflow.getEndTime() > 0 ? workflow.getEndTime() : startedAt;
        payload.put("started_at", Instant.ofEpochMilli(startedAt).toString());
        payload.put("ended_at", Instant.ofEpochMilli(endedAt).toString());
        return payload;
    }

    private List<Map<String, Object>> events(WorkflowModel workflow) {
        if (workflow.getTasks() == null) return List.of();
        List<TaskModel> tasks = new ArrayList<>(workflow.getTasks());
        tasks.sort(Comparator.comparingInt(TaskModel::getSeq));
        List<Map<String, Object>> events = new ArrayList<>();
        for (TaskModel task : tasks) {
            String taskType = task.getTaskType();
            boolean subagent = "SUB_WORKFLOW".equals(taskType);
            if (!subagent && !isToolTask(task)) continue;

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", subagent ? "subagent" : "tool_call");
            event.put("name", eventName(task, subagent));
            event.put("detail", jsonString(redact(task.getInputData())));
            boolean error = task.getStatus() != null && !task.getStatus().isSuccessful();
            Object eventOutput = task.getOutputData();
            if ((eventOutput == null || (eventOutput instanceof Map<?, ?> map && map.isEmpty()))
                    && error) {
                eventOutput = task.getReasonForIncompletion();
            }
            event.put("output", jsonString(redact(eventOutput)));
            event.put("is_error", error);
            events.add(event);
        }
        return events;
    }

    private boolean isToolTask(TaskModel task) {
        String type = task.getTaskType();
        if (type == null || INTERNAL_TYPES.contains(type)) return false;
        String ref = task.getReferenceTaskName();
        if (ref != null && ref.startsWith("_fw_")) return false;
        return TOOL_TYPES.contains(type) || task.getTaskDefinition().isPresent();
    }

    private String eventName(TaskModel task, boolean subagent) {
        if (subagent) {
            Object workflowName = task.getInputData().get("subWorkflowName");
            return stringValue(workflowName, task.getReferenceTaskName());
        }
        Object toolName = task.getInputData().get("toolName");
        if (toolName == null) toolName = task.getInputData().get("method");
        return stringValue(
                toolName, stringValue(task.getTaskDefName(), task.getReferenceTaskName()));
    }

    private Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new LinkedHashMap<>();
            map.forEach(
                    (key, item) -> {
                        String name = String.valueOf(key);
                        String lower = name.toLowerCase(Locale.ROOT);
                        if (lower.contains("secret")
                                || lower.contains("password")
                                || lower.contains("token")
                                || lower.contains("credential")
                                || lower.equals("authorization")
                                || lower.equals("x-api-key")
                                || lower.equals("apikey")
                                || lower.equals("api_key")) {
                            clean.put(name, "[REDACTED]");
                        } else {
                            clean.put(name, redact(item));
                        }
                    });
            return clean;
        }
        if (value instanceof List<?> list) return list.stream().map(this::redact).toList();
        return value;
    }

    @SuppressWarnings("unchecked")
    byte[] encodeWithinLimit(Map<String, Object> payload) throws JsonProcessingException {
        byte[] encoded = mapper.writeValueAsBytes(payload);
        if (encoded.length <= TARGET_REQUEST_BYTES) return encoded;
        List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get("events");
        if (events.isEmpty()) return encoded;

        int perFieldChars = Math.max(256, TARGET_REQUEST_BYTES / (events.size() * 4));
        for (Map<String, Object> event : events) {
            event.put("detail", truncate(String.valueOf(event.get("detail")), perFieldChars));
            event.put("output", truncate(String.valueOf(event.get("output")), perFieldChars));
        }
        encoded = mapper.writeValueAsBytes(payload);
        while (encoded.length > TARGET_REQUEST_BYTES && perFieldChars > 256) {
            perFieldChars /= 2;
            for (Map<String, Object> event : events) {
                event.put("detail", truncate(String.valueOf(event.get("detail")), perFieldChars));
                event.put("output", truncate(String.valueOf(event.get("output")), perFieldChars));
            }
            encoded = mapper.writeValueAsBytes(payload);
        }
        return encoded;
    }

    private String jsonString(Object value) {
        if (value == null) return "";
        if (value instanceof String string) return string;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String outcome(WorkflowModel workflow) {
        if (workflow.getStatus() == WorkflowModel.Status.COMPLETED) return "success";
        if (workflow.getStatus() == WorkflowModel.Status.TERMINATED) return "interrupted";
        return "error";
    }

    private static long startTime(WorkflowModel workflow) {
        if (workflow.getCreateTime() != null && workflow.getCreateTime() > 0) {
            return workflow.getCreateTime();
        }
        return workflow.getTasks() == null
                ? 0
                : workflow.getTasks().stream()
                        .mapToLong(
                                task ->
                                        task.getStartTime() > 0
                                                ? task.getStartTime()
                                                : task.getScheduledTime())
                        .filter(value -> value > 0)
                        .min()
                        .orElse(0);
    }

    private static void copyString(
            Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank())
            target.put(key, String.valueOf(value));
    }

    private static String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…[truncated]";
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
