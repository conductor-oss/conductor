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
import java.util.UUID;

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
import org.conductoross.conductor.ai.agentspan.runtime.spi.AzureAgentRunContext;
import org.conductoross.conductor.ai.agentspan.runtime.spi.AzureAgentRunStore;
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

/*
 * ConductorAgentClient backed by Azure AI Foundry Agents via the OpenAI Assistants-compatible API.
 *
 * Auth uses Entra ID client credentials flow. Credentials are resolved from the Conductor secret
 * store via credentialRef, with sub-keys .client_id, .client_secret, and .tenant_id.
 *
 * Required rawConfig fields:
 *   assistantId — the Azure AI Foundry assistant ID
 *   endpoint    — the agentsEndpointUri for the AI Foundry project (or set AZURE_FOUNDRY_ENDPOINT)
 *
 * Run context (threadId, runId, endpoint, assistantId, apiVersion, credentialRef) is persisted in
 * an AzureAgentRunStore after startAgent. Any server replica can look up the context and
 * re-authenticate from the stored credentialRef, so getAgentStatus, respond, and cancelAgent all
 * work correctly in multi-replica deployments. The default store is in-process; replace it with a
 * Redis or DB-backed implementation for HA clusters, following the same pattern as SkillMetadataDAO.
 *
 * Activated by conductor.integrations.ai.enabled=true.
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
    private final AzureAgentRunStore runStore;

    public AzureFoundryAgentClient(
            CredentialResolutionService credentialResolutionService,
            @Qualifier("conductorAiHttpClient") OkHttpClient httpClient,
            AzureAgentRunStore runStore) {
        this.credentialResolutionService = credentialResolutionService;
        this.httpClient = httpClient;
        this.runStore = runStore;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_AZURE_FOUNDRY;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String endpoint = resolveEndpoint(request);
        String assistantId = resolveAssistantId(request);
        String apiVersion = resolveApiVersion(request);
        String credentialRef = resolveCredentialRef(request);
        String scope = resolveScope(request, credentialRef);
        OAuthTokenProvider tokenProvider = buildTokenProvider(credentialRef, scope);
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

        String executionId = UUID.randomUUID().toString();
        runStore.save(
                executionId,
                new AzureAgentRunContext(
                        threadId, runId, endpoint, assistantId, apiVersion, credentialRef, scope));

        return ConductorAgentStartResponse.builder()
                .executionId(executionId)
                .agentName(assistantId)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(
            String executionId, ConductorAgentRequest request) {
        AzureAgentRunContext ctx = findContext(executionId);
        // Prefer credentialRef from the live task request (always fresh); fall back to stored.
        String credentialRef =
                StringUtils.defaultIfBlank(request.getCredentialRef(), ctx.getCredentialRef());
        String token = buildTokenProvider(credentialRef, ctx.getScope()).getToken();

        String runUrl =
                ctx.getEndpoint() + "/threads/" + ctx.getThreadId() + "/runs/" + ctx.getRunId();
        JsonNode run = get(runUrl, token, ctx.getApiVersion());
        ConductorAgentStatusResponse response = toStatusResponse(executionId, ctx, run, token);
        if (response.isComplete()) {
            runStore.delete(executionId);
        }
        return response;
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        String executionId = request.getExecutionId();
        AzureAgentRunContext ctx = findContext(executionId);
        String token = buildTokenProvider(ctx.getCredentialRef(), ctx.getScope()).getToken();

        String runUrl =
                ctx.getEndpoint() + "/threads/" + ctx.getThreadId() + "/runs/" + ctx.getRunId();
        JsonNode run = get(runUrl, token, ctx.getApiVersion());
        String status = run.path("status").asText();

        if ("requires_action".equals(status)) {
            submitToolOutputs(ctx, request, token);
        } else {
            // Multi-turn: add message and start new run
            ObjectNode msgBody = MAPPER.createObjectNode();
            msgBody.put("role", "user");
            String content = request.getBody() != null ? request.getBody().toString() : "";
            msgBody.put("content", content);
            post(
                    ctx.getEndpoint() + "/threads/" + ctx.getThreadId() + "/messages",
                    msgBody,
                    token,
                    ctx.getApiVersion());

            ObjectNode runBody = MAPPER.createObjectNode();
            runBody.put("assistant_id", ctx.getAssistantId());
            JsonNode newRun =
                    post(
                            ctx.getEndpoint() + "/threads/" + ctx.getThreadId() + "/runs",
                            runBody,
                            token,
                            ctx.getApiVersion());
            runStore.save(executionId, ctx.withRunId(newRun.path("id").asText()));
        }
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        String executionId = request.getExecutionId();
        AzureAgentRunContext ctx = findContext(executionId);
        String token = buildTokenProvider(ctx.getCredentialRef(), ctx.getScope()).getToken();
        String cancelUrl =
                ctx.getEndpoint()
                        + "/threads/"
                        + ctx.getThreadId()
                        + "/runs/"
                        + ctx.getRunId()
                        + "/cancel";
        try {
            post(cancelUrl, MAPPER.createObjectNode(), token, ctx.getApiVersion());
        } catch (Exception e) {
            log.warn("Failed to cancel Azure Foundry run {}: {}", ctx.getRunId(), e.getMessage());
        }
        runStore.delete(executionId);
    }

    private void submitToolOutputs(
            AzureAgentRunContext ctx, ConductorAgentRespondRequest request, String token) {
        JsonNode run =
                get(
                        ctx.getEndpoint()
                                + "/threads/"
                                + ctx.getThreadId()
                                + "/runs/"
                                + ctx.getRunId(),
                        token,
                        ctx.getApiVersion());
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
                ctx.getEndpoint()
                        + "/threads/"
                        + ctx.getThreadId()
                        + "/runs/"
                        + ctx.getRunId()
                        + "/submit_tool_outputs";
        post(submitUrl, body, token, ctx.getApiVersion());
    }

    private ConductorAgentStatusResponse toStatusResponse(
            String executionId, AzureAgentRunContext ctx, JsonNode run, String token) {
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
                    get(
                            ctx.getEndpoint() + "/threads/" + ctx.getThreadId() + "/messages",
                            token,
                            ctx.getApiVersion());
            for (JsonNode msg : messages.path("data")) {
                if ("assistant".equals(msg.path("role").asText())) {
                    String text = msg.path("content").path(0).path("text").path("value").asText("");
                    output = Map.of("result", text);
                    break;
                }
            }
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
                .executionId(executionId)
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

    private OAuthTokenProvider buildTokenProvider(String credentialRef, String scope) {
        String clientId = credentialResolutionService.resolve(credentialRef + ".client_id");
        String clientSecret = credentialResolutionService.resolve(credentialRef + ".client_secret");
        String tenantId = credentialResolutionService.resolve(credentialRef + ".tenant_id");

        if (StringUtils.isAnyBlank(clientId, clientSecret, tenantId)) {
            throw new IllegalStateException(
                    "Azure Foundry credential '"
                            + credentialRef
                            + "' must contain client_id, client_secret, and tenant_id");
        }

        return OAuthTokenProvider.forAzureEntraId(
                httpClient, tenantId, clientId, clientSecret, scope);
    }

    private AzureAgentRunContext findContext(String executionId) {
        return runStore.find(executionId)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No Azure agent run context found for executionId: "
                                                + executionId));
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

    private String resolveCredentialRef(ConductorAgentStartRequest request) {
        String credentialRef = request.getCredentialRef();
        if (StringUtils.isBlank(credentialRef)) {
            throw new IllegalArgumentException(
                    "credentialRef is required for Azure Foundry agent requests");
        }
        return credentialRef;
    }

    private String resolveScope(ConductorAgentStartRequest request, String credentialRef) {
        String scope =
                StringUtils.defaultIfBlank(
                        rawConfig(request, "scope"),
                        credentialResolutionService.resolve(credentialRef + ".scope"));
        return StringUtils.defaultIfBlank(scope, DEFAULT_SCOPE);
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
}
