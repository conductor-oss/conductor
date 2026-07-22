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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.ai.agent.credentials.OAuthTokenProvider;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
 * {@link ConductorAgentClient} backed by Azure AI Foundry Agents via the OpenAI
 * Assistants-compatible API.
 *
 * <p>Auth uses Entra ID client credentials flow. Credentials are resolved from the Conductor secret
 * store using the {@code credentialRef} on the start request, with dotted-path sub-keys {@code
 * .client_id}, {@code .client_secret}, and {@code .tenant_id}.
 *
 * <p>Required rawConfig fields:
 *
 * <ul>
 *   <li>{@code assistantId} - the Azure AI Foundry assistant ID (create it via portal or API first)
 *   <li>{@code endpoint} - the agentsEndpointUri for the AI Foundry project (optional if
 *       AZURE_FOUNDRY_ENDPOINT secret is set)
 * </ul>
 */
@Component
public class AzureFoundryAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AzureFoundryAgentClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_SCOPE = "https://management.azure.com/.default";
    private static final String API_VERSION = "2025-05-01";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CredentialResolutionService credentialResolutionService;
    private final OkHttpClient httpClient;
    private final ConcurrentHashMap<String, ExecutionContext> executions =
            new ConcurrentHashMap<>();

    public AzureFoundryAgentClient(
            CredentialResolutionService credentialResolutionService, OkHttpClient httpClient) {
        this.credentialResolutionService = credentialResolutionService;
        this.httpClient = httpClient;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_AZURE_FOUNDRY;
    }

    /**
     * Creates a thread, posts the user message, and starts a run against the configured assistant.
     * Returns the thread ID as the execution ID.
     */
    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String endpoint = resolveEndpoint(request);
        String assistantId = resolveAssistantId(request);
        OAuthTokenProvider tokenProvider = buildTokenProvider(request);

        String token = tokenProvider.getToken();

        // 1. Create thread
        JsonNode threadResult = post(endpoint + "/threads", MAPPER.createObjectNode(), token);
        String threadId = threadResult.path("id").asText();

        // 2. Add user message
        ObjectNode msgBody = MAPPER.createObjectNode();
        msgBody.put("role", "user");
        msgBody.put("content", request.getPrompt());
        post(endpoint + "/threads/" + threadId + "/messages", msgBody, token);

        // 3. Start run
        ObjectNode runBody = MAPPER.createObjectNode();
        runBody.put("assistant_id", assistantId);
        JsonNode runResult = post(endpoint + "/threads/" + threadId + "/runs", runBody, token);
        String runId = runResult.path("id").asText();

        executions.put(threadId, new ExecutionContext(endpoint, assistantId, runId, tokenProvider));

        return ConductorAgentStartResponse.builder()
                .executionId(threadId)
                .agentName(assistantId)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    /**
     * Polls the current run status. Maps Azure run states to {@link ConductorAgentState}:
     *
     * <ul>
     *   <li>completed → COMPLETED with last assistant message as output
     *   <li>requires_action → WAITING with tool call details as pendingTool
     *   <li>failed / expired / cancelled → FAILED / CANCELED
     *   <li>queued / in_progress → RUNNING
     * </ul>
     */
    @Override
    public ConductorAgentStatusResponse getAgentStatus(String executionId) {
        ExecutionContext ctx = executions.get(executionId);
        if (ctx == null) {
            return ConductorAgentStatusResponse.builder()
                    .executionId(executionId)
                    .status(ConductorAgentState.FAILED)
                    .complete(true)
                    .reasonForIncompletion("No execution found for id: " + executionId)
                    .build();
        }
        String token = ctx.tokenProvider.getToken();
        String runUrl = ctx.endpoint + "/threads/" + executionId + "/runs/" + ctx.runId;
        JsonNode run = get(runUrl, token);
        return toStatusResponse(executionId, run, ctx, token);
    }

    /**
     * Submits a tool-call result (when the run is in {@code requires_action} state) or posts a new
     * user message and starts a fresh run (for multi-turn conversation).
     */
    @Override
    public void respond(ConductorAgentRespondRequest request) {
        String executionId = request.getExecutionId();
        ExecutionContext ctx = executions.get(executionId);
        if (ctx == null) {
            throw new IllegalStateException("No execution found for id: " + executionId);
        }
        String token = ctx.tokenProvider.getToken();

        // Check current run state to decide how to respond
        String runUrl = ctx.endpoint + "/threads/" + executionId + "/runs/" + ctx.runId;
        JsonNode run = get(runUrl, token);
        String status = run.path("status").asText();

        if ("requires_action".equals(status)) {
            submitToolOutputs(executionId, ctx, request, token);
        } else {
            // Multi-turn: add message and start new run
            ObjectNode msgBody = MAPPER.createObjectNode();
            msgBody.put("role", "user");
            String content = request.getBody() != null ? request.getBody().toString() : "";
            msgBody.put("content", content);
            post(ctx.endpoint + "/threads/" + executionId + "/messages", msgBody, token);

            ObjectNode runBody = MAPPER.createObjectNode();
            runBody.put("assistant_id", ctx.assistantId);
            JsonNode newRun =
                    post(ctx.endpoint + "/threads/" + executionId + "/runs", runBody, token);
            ctx.runId = newRun.path("id").asText();
        }
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        String executionId = request.getExecutionId();
        ExecutionContext ctx = executions.remove(executionId);
        if (ctx == null) {
            log.warn("cancelAgent called for unknown executionId={}", executionId);
            return;
        }
        String token = ctx.tokenProvider.getToken();
        String cancelUrl =
                ctx.endpoint + "/threads/" + executionId + "/runs/" + ctx.runId + "/cancel";
        try {
            post(cancelUrl, MAPPER.createObjectNode(), token);
        } catch (Exception e) {
            log.warn("Failed to cancel Azure Foundry run {}: {}", ctx.runId, e.getMessage());
        }
    }

    private void submitToolOutputs(
            String threadId,
            ExecutionContext ctx,
            ConductorAgentRespondRequest request,
            String token) {
        JsonNode run = get(ctx.endpoint + "/threads/" + threadId + "/runs/" + ctx.runId, token);
        JsonNode toolCalls =
                run.path("required_action").path("submit_tool_outputs").path("tool_calls");

        ObjectNode body = MAPPER.createObjectNode();
        ArrayNode outputs = body.putArray("tool_outputs");
        String resultJson =
                request.getBody() != null ? MAPPER.valueToTree(request.getBody()).toString() : "{}";

        for (JsonNode tc : toolCalls) {
            ObjectNode o = outputs.addObject();
            o.put("tool_call_id", tc.path("id").asText());
            o.put("output", resultJson);
        }

        String submitUrl =
                ctx.endpoint
                        + "/threads/"
                        + threadId
                        + "/runs/"
                        + ctx.runId
                        + "/submit_tool_outputs";
        post(submitUrl, body, token);
    }

    private ConductorAgentStatusResponse toStatusResponse(
            String threadId, JsonNode run, ExecutionContext ctx, String token) {
        String azureStatus = run.path("status").asText("queued");
        ConductorAgentState state = toState(azureStatus);
        boolean complete =
                state == ConductorAgentState.COMPLETED
                        || state == ConductorAgentState.FAILED
                        || state == ConductorAgentState.CANCELED;

        Map<String, Object> output = null;
        Map<String, Object> pendingTool = null;
        String pendingToolName = null;
        String reason = null;

        if (state == ConductorAgentState.COMPLETED) {
            // Grab the latest assistant message
            JsonNode messages = get(ctx.endpoint + "/threads/" + threadId + "/messages", token);
            for (JsonNode msg : messages.path("data")) {
                if ("assistant".equals(msg.path("role").asText())) {
                    String text = msg.path("content").path(0).path("text").path("value").asText("");
                    output = Map.of("result", text);
                    break;
                }
            }
            executions.remove(threadId);
        } else if (state == ConductorAgentState.WAITING) {
            JsonNode toolCalls =
                    run.path("required_action").path("submit_tool_outputs").path("tool_calls");
            if (toolCalls.isArray() && toolCalls.size() > 0) {
                JsonNode first = toolCalls.get(0);
                pendingToolName = first.path("function").path("name").asText("unknown");
                pendingTool =
                        Map.of(
                                "tool_name", pendingToolName,
                                "tool_call_id", first.path("id").asText(),
                                "arguments", first.path("function").path("arguments").asText("{}"));
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
                .pendingToolName(pendingToolName)
                .reasonForIncompletion(reason)
                .build();
    }

    private static ConductorAgentState toState(String azureStatus) {
        return switch (azureStatus) {
            case "completed" -> ConductorAgentState.COMPLETED;
            case "failed", "expired" -> ConductorAgentState.FAILED;
            case "cancelled" -> ConductorAgentState.CANCELED;
            case "requires_action" -> ConductorAgentState.WAITING;
            default -> ConductorAgentState.RUNNING; // queued, in_progress
        };
    }

    private OAuthTokenProvider buildTokenProvider(ConductorAgentStartRequest request) {
        String credentialRef = request.getCredentialRef();
        if (StringUtils.isBlank(credentialRef)) {
            throw new IllegalArgumentException(
                    "credentialRef is required for Azure Foundry agent requests");
        }
        String clientId = credentialResolutionService.resolve(credentialRef + ".client_id");
        String clientSecret = credentialResolutionService.resolve(credentialRef + ".client_secret");
        String tenantId = credentialResolutionService.resolve(credentialRef + ".tenant_id");

        if (StringUtils.isAnyBlank(clientId, clientSecret, tenantId)) {
            throw new IllegalStateException(
                    "Azure Foundry credential '"
                            + credentialRef
                            + "' must contain client_id, client_secret, and tenant_id");
        }

        String scope =
                StringUtils.defaultIfBlank(
                        rawConfig(request, "scope"),
                        credentialResolutionService.resolve(credentialRef + ".scope"));
        scope = StringUtils.defaultIfBlank(scope, DEFAULT_SCOPE);

        return OAuthTokenProvider.forAzureEntraId(
                httpClient, tenantId, clientId, clientSecret, scope);
    }

    private String resolveEndpoint(ConductorAgentStartRequest request) {
        String endpoint = rawConfig(request, "endpoint");
        if (StringUtils.isBlank(endpoint)) {
            endpoint = credentialResolutionService.resolve("AZURE_FOUNDRY_ENDPOINT");
        }
        if (StringUtils.isBlank(endpoint)) {
            throw new IllegalArgumentException(
                    "Azure Foundry endpoint must be provided via rawConfig.endpoint or AZURE_FOUNDRY_ENDPOINT secret");
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private String resolveAssistantId(ConductorAgentStartRequest request) {
        String id = rawConfig(request, "assistantId");
        if (StringUtils.isBlank(id)) {
            id = rawConfig(request, "agentId");
        }
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException(
                    "rawConfig.assistantId is required for Azure Foundry agent requests");
        }
        return id;
    }

    private JsonNode post(String url, ObjectNode body, String bearerToken) {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        String fullUrl = url.contains("?") ? url : url + "?api-version=" + API_VERSION;
        Request request =
                new Request.Builder()
                        .url(fullUrl)
                        .post(RequestBody.create(bytes, JSON))
                        .header("Authorization", "Bearer " + bearerToken)
                        .build();
        return execute(request, url);
    }

    private JsonNode get(String url, String bearerToken) {
        String fullUrl = url.contains("?") ? url : url + "?api-version=" + API_VERSION;
        Request request =
                new Request.Builder()
                        .url(fullUrl)
                        .get()
                        .header("Authorization", "Bearer " + bearerToken)
                        .build();
        return execute(request, url);
    }

    private JsonNode execute(Request request, String label) {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "Azure Foundry API call to "
                                + label
                                + " failed: HTTP "
                                + response.code()
                                + " — "
                                + responseBody);
            }
            return MAPPER.readTree(responseBody);
        } catch (IOException e) {
            throw new RuntimeException("Azure Foundry API call to " + label + " failed", e);
        }
    }

    private static String rawConfig(ConductorAgentStartRequest request, String key) {
        if (request.getRawConfig() == null) return null;
        Object value = request.getRawConfig().get(key);
        return value != null ? value.toString() : null;
    }

    /** Per-execution state: endpoint, assistant, thread/run IDs, and token provider. */
    private static class ExecutionContext {
        final String endpoint;
        final String assistantId;
        volatile String runId;
        final OAuthTokenProvider tokenProvider;

        ExecutionContext(
                String endpoint,
                String assistantId,
                String runId,
                OAuthTokenProvider tokenProvider) {
            this.endpoint = endpoint;
            this.assistantId = assistantId;
            this.runId = runId;
            this.tokenProvider = tokenProvider;
        }
    }
}
