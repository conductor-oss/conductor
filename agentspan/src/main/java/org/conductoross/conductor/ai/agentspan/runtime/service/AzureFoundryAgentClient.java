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
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.ai.agent.credentials.OAuthTokenProvider;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * <p>Activated by {@code conductor.integrations.ai.enabled=true}, like the other agent clients.
 * Credentials are resolved per request from {@code credentialRef}, so the client registers whether
 * or not Azure Foundry is configured; an unconfigured runtime fails only if a workflow routes to
 * it.
 *
 * <p>The executionId returned by {@link #startAgent} is a URL-safe base64 JSON encoding of the
 * non-sensitive run context {@code {threadId, runId, endpoint, assistantId, apiVersion}}. This
 * allows {@link #getAgentStatus} to reconstruct the Azure run from the task outputData alone —
 * without any in-process memory — making the client safe in multi-replica server deployments where
 * the status-poll invocation may arrive on a different pod than the start invocation.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class AzureFoundryAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AzureFoundryAgentClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_SCOPE = "https://cognitiveservices.azure.com/.default";
    private static final String DEFAULT_API_VERSION = "2025-01-01-preview";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CredentialResolutionService credentialResolutionService;
    private final OkHttpClient httpClient;

    // Keyed by threadId. Used only by respond() and cancelAgent(), which receive the compound
    // executionId but no credentialRef — so they cannot re-authenticate statlessly. getAgentStatus()
    // no longer relies on this map; it decodes run context from the compound executionId instead.
    private final ConcurrentHashMap<String, RespondContext> respondContexts =
            new ConcurrentHashMap<>();

    public AzureFoundryAgentClient(
            CredentialResolutionService credentialResolutionService,
            @Qualifier("conductorAiHttpClient") OkHttpClient httpClient) {
        this.credentialResolutionService = credentialResolutionService;
        this.httpClient = httpClient;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_AZURE_FOUNDRY;
    }

    /**
     * Creates a thread, posts the user message, and starts a run against the configured assistant.
     * Returns a compound executionId that encodes all run context needed for stateless status
     * polling in multi-replica environments.
     */
    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String endpoint = resolveEndpoint(request);
        String assistantId = resolveAssistantId(request);
        String apiVersion = resolveApiVersion(request);
        OAuthTokenProvider tokenProvider = buildTokenProvider(request);

        String token = tokenProvider.getToken();

        // 1. Create thread
        JsonNode threadResult =
                post(endpoint + "/threads", MAPPER.createObjectNode(), token, apiVersion);
        String threadId = threadResult.path("id").asText();

        // 2. Add user message
        ObjectNode msgBody = MAPPER.createObjectNode();
        msgBody.put("role", "user");
        msgBody.put("content", request.getPrompt());
        post(endpoint + "/threads/" + threadId + "/messages", msgBody, token, apiVersion);

        // 3. Start run
        ObjectNode runBody = MAPPER.createObjectNode();
        runBody.put("assistant_id", assistantId);
        JsonNode runResult =
                post(endpoint + "/threads/" + threadId + "/runs", runBody, token, apiVersion);
        String runId = runResult.path("id").asText();

        // Store context for respond() and cancelAgent() — those receive no credentialRef
        respondContexts.put(threadId, new RespondContext(endpoint, assistantId, runId,
                tokenProvider, apiVersion));

        return ConductorAgentStartResponse.builder()
                .executionId(RunContext.encode(threadId, runId, endpoint, assistantId, apiVersion))
                .agentName(assistantId)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    /**
     * Polls the current run status. Decodes the compound executionId to locate the Azure run
     * without in-process memory, then re-authenticates using the original task's {@code
     * credentialRef}. This makes the method safe when called on a different server replica from the
     * one that ran {@link #startAgent}.
     *
     * <p>Maps Azure run states to {@link ConductorAgentState}:
     *
     * <ul>
     *   <li>completed → COMPLETED with last assistant message as output
     *   <li>requires_action → WAITING with tool call details as pendingTool
     *   <li>failed / expired / cancelled → FAILED / CANCELED
     *   <li>queued / in_progress → RUNNING
     * </ul>
     */
    @Override
    public ConductorAgentStatusResponse getAgentStatus(
            String executionId, ConductorAgentRequest request) {
        RunContext ctx;
        try {
            ctx = RunContext.decode(executionId);
        } catch (Exception e) {
            return ConductorAgentStatusResponse.builder()
                    .executionId(executionId)
                    .status(ConductorAgentState.FAILED)
                    .complete(true)
                    .reasonForIncompletion("Cannot decode Azure run context: " + e.getMessage())
                    .build();
        }

        String token;
        // Fast path: reuse the cached token provider from the same pod that called startAgent
        RespondContext cached = respondContexts.get(ctx.threadId);
        if (cached != null) {
            token = cached.tokenProvider.getToken();
        } else {
            // Cross-pod path: re-authenticate from the original task credentialRef
            token = buildTokenProvider(request).getToken();
            log.debug("getAgentStatus cross-pod fallback for threadId={}", ctx.threadId);
        }

        String runUrl = ctx.endpoint + "/threads/" + ctx.threadId + "/runs/" + ctx.runId;
        JsonNode run = get(runUrl, token, ctx.apiVersion);
        return toStatusResponse(ctx, run, token);
    }

    /**
     * Submits a tool-call result (when the run is in {@code requires_action} state) or posts a new
     * user message and starts a fresh run (for multi-turn conversation).
     */
    @Override
    public void respond(ConductorAgentRespondRequest request) {
        RunContext ctx = decodeOrThrow(request.getExecutionId());
        RespondContext rc = respondContexts.get(ctx.threadId);
        if (rc == null) {
            throw new IllegalStateException(
                    "No respond context for threadId " + ctx.threadId
                            + " — multi-turn respond requires the same server instance as startAgent");
        }
        String token = rc.tokenProvider.getToken();

        String runUrl = rc.endpoint + "/threads/" + ctx.threadId + "/runs/" + rc.runId;
        JsonNode run = get(runUrl, token, rc.apiVersion);
        String status = run.path("status").asText();

        if ("requires_action".equals(status)) {
            submitToolOutputs(ctx.threadId, rc, request, token);
        } else {
            // Multi-turn: add message and start new run
            ObjectNode msgBody = MAPPER.createObjectNode();
            msgBody.put("role", "user");
            String content = request.getBody() != null ? request.getBody().toString() : "";
            msgBody.put("content", content);
            post(rc.endpoint + "/threads/" + ctx.threadId + "/messages", msgBody, token,
                    rc.apiVersion);

            ObjectNode runBody = MAPPER.createObjectNode();
            runBody.put("assistant_id", rc.assistantId);
            JsonNode newRun =
                    post(rc.endpoint + "/threads/" + ctx.threadId + "/runs", runBody, token,
                            rc.apiVersion);
            rc.runId = newRun.path("id").asText();
        }
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        RunContext ctx = decodeOrThrow(request.getExecutionId());
        RespondContext rc = respondContexts.remove(ctx.threadId);
        if (rc == null) {
            log.warn("cancelAgent called for unknown threadId={}", ctx.threadId);
            return;
        }
        String token = rc.tokenProvider.getToken();
        String cancelUrl =
                rc.endpoint + "/threads/" + ctx.threadId + "/runs/" + rc.runId + "/cancel";
        try {
            post(cancelUrl, MAPPER.createObjectNode(), token, rc.apiVersion);
        } catch (Exception e) {
            log.warn("Failed to cancel Azure Foundry run {}: {}", rc.runId, e.getMessage());
        }
    }

    private void submitToolOutputs(
            String threadId,
            RespondContext rc,
            ConductorAgentRespondRequest request,
            String token) {
        JsonNode run =
                get(rc.endpoint + "/threads/" + threadId + "/runs/" + rc.runId, token, rc.apiVersion);
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
                rc.endpoint
                        + "/threads/"
                        + threadId
                        + "/runs/"
                        + rc.runId
                        + "/submit_tool_outputs";
        post(submitUrl, body, token, rc.apiVersion);
    }

    private ConductorAgentStatusResponse toStatusResponse(
            RunContext ctx, JsonNode run, String token) {
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
            JsonNode messages =
                    get(ctx.endpoint + "/threads/" + ctx.threadId + "/messages", token, ctx.apiVersion);
            for (JsonNode msg : messages.path("data")) {
                if ("assistant".equals(msg.path("role").asText())) {
                    String text = msg.path("content").path(0).path("text").path("value").asText("");
                    output = Map.of("result", text);
                    break;
                }
            }
            respondContexts.remove(ctx.threadId);
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
                .executionId(ctx.encode())
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

    private JsonNode post(String url, ObjectNode body, String bearerToken, String apiVersion) {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        String fullUrl = url.contains("?") ? url : url + "?api-version=" + apiVersion;
        Request request =
                new Request.Builder()
                        .url(fullUrl)
                        .post(RequestBody.create(bytes, JSON))
                        .header("Authorization", "Bearer " + bearerToken)
                        .build();
        return execute(request, url);
    }

    private JsonNode get(String url, String bearerToken, String apiVersion) {
        String fullUrl = url.contains("?") ? url : url + "?api-version=" + apiVersion;
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

    private String resolveApiVersion(ConductorAgentStartRequest request) {
        String v = rawConfig(request, "apiVersion");
        return StringUtils.isBlank(v) ? DEFAULT_API_VERSION : v;
    }

    private static String rawConfig(ConductorAgentStartRequest request, String key) {
        if (request.getRawConfig() == null) return null;
        Object value = request.getRawConfig().get(key);
        return value != null ? value.toString() : null;
    }

    private static RunContext decodeOrThrow(String executionId) {
        try {
            return RunContext.decode(executionId);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decode Azure run context: " + e.getMessage(), e);
        }
    }

    /**
     * Immutable per-execution context encoded into the returned executionId. Contains only
     * non-sensitive fields needed to reconstruct the Azure API call on any server replica.
     * Credentials are never stored here — they are re-resolved from the task input on each call.
     */
    private static final class RunContext {
        final String threadId;
        final String runId;
        final String endpoint;
        final String assistantId;
        final String apiVersion;

        RunContext(String threadId, String runId, String endpoint, String assistantId,
                String apiVersion) {
            this.threadId = threadId;
            this.runId = runId;
            this.endpoint = endpoint;
            this.assistantId = assistantId;
            this.apiVersion = apiVersion;
        }

        String encode() {
            return encode(threadId, runId, endpoint, assistantId, apiVersion);
        }

        static String encode(String threadId, String runId, String endpoint, String assistantId,
                String apiVersion) {
            try {
                ObjectNode n = MAPPER.createObjectNode();
                n.put("t", threadId);
                n.put("r", runId);
                n.put("e", endpoint);
                n.put("a", assistantId);
                n.put("v", apiVersion);
                return Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(MAPPER.writeValueAsBytes(n));
            } catch (Exception ex) {
                throw new RuntimeException("Failed to encode Azure run context", ex);
            }
        }

        static RunContext decode(String executionId) {
            try {
                byte[] bytes = Base64.getUrlDecoder().decode(executionId);
                JsonNode n = MAPPER.readTree(bytes);
                return new RunContext(
                        n.path("t").asText(),
                        n.path("r").asText(),
                        n.path("e").asText(),
                        n.path("a").asText(),
                        StringUtils.defaultIfBlank(n.path("v").asText(), DEFAULT_API_VERSION));
            } catch (Exception e) {
                throw new RuntimeException(
                        "Cannot decode Azure run context from executionId: " + executionId, e);
            }
        }
    }

    /**
     * Mutable per-execution context held in memory on the pod that called startAgent. Used only by
     * respond() and cancelAgent(), which receive no credentialRef and therefore cannot
     * re-authenticate on a different replica. Single-turn agent calls (the common case) do not use
     * this map — getAgentStatus() is stateless.
     */
    private static final class RespondContext {
        final String endpoint;
        final String assistantId;
        volatile String runId;
        final OAuthTokenProvider tokenProvider;
        final String apiVersion;

        RespondContext(String endpoint, String assistantId, String runId,
                OAuthTokenProvider tokenProvider, String apiVersion) {
            this.endpoint = endpoint;
            this.assistantId = assistantId;
            this.runId = runId;
            this.tokenProvider = tokenProvider;
            this.apiVersion = apiVersion;
        }
    }
}
