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

// ConductorAgentClient backed by Azure AI Foundry Agents via the OpenAI Assistants-compatible API.
// Supports two auth modes selected by the credential secret:
//   OAuth (Entra ID client credentials): secret must contain client_id, client_secret, tenant_id.
//   API key: secret must contain apiKey (used as the api-key header).
// Required: agentUrl with the agent/assistant ID embedded in the path:
//   Classic:      https://my-resource.openai.azure.com/openai/assistants/asst_xxx
//   Foundry proj: https://my-resource.services.ai.azure.com/api/projects/{proj}/agents/{name}
// rawConfig.assistantId is still accepted as a fallback for existing workflow definitions.
// Activated by conductor.integrations.ai.enabled=true; an unconfigured runtime fails only if used.
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class AzureFoundryAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AzureFoundryAgentClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_SCOPE = "https://cognitiveservices.azure.com/.default";
    // New Foundry project endpoints (services.ai.azure.com) require the ai.azure.com audience.
    private static final String FOUNDRY_SCOPE = "https://ai.azure.com/.default";
    private static final String DEFAULT_API_VERSION = "2025-01-01-preview";
    private static final String FOUNDRY_PROJECT_API_VERSION = "2025-05-15-preview";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CredentialResolutionService credentialResolutionService;
    private final OkHttpClient httpClient;
    private final ConcurrentHashMap<String, ExecutionContext> executions =
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

    // Routes to the Responses API (new Foundry project endpoints) or classic Threads/Runs API.
    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String endpoint = resolveEndpoint(request);
        AuthState auth = buildAuthState(request);
        if (isFoundryProjectEndpoint(endpoint)) {
            return startAgentResponses(request, endpoint, auth);
        }
        return startAgentClassic(request, endpoint, auth);
    }

    // New Foundry project agents: POST /openai/responses — synchronous, result available
    // immediately.
    private ConductorAgentStartResponse startAgentResponses(
            ConductorAgentStartRequest request, String endpoint, AuthState auth) {
        String agentId = resolveAssistantId(request);
        String model = rawConfig(request, "model");
        String instructions = resolveInstructions(request, endpoint, agentId, auth);

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", StringUtils.defaultIfBlank(model, "gpt-4o"));
        if (StringUtils.isNotBlank(instructions)) {
            body.put("instructions", instructions);
        }
        ArrayNode input = body.putArray("input");
        ObjectNode userMsg = input.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", request.getPrompt());

        JsonNode response =
                post(endpoint + "/openai/responses", body, auth, FOUNDRY_PROJECT_API_VERSION);
        String responseId = response.path("id").asText();
        String text = extractResponseText(response);

        ExecutionContext ctx =
                new ExecutionContext(endpoint, agentId, null, auth, FOUNDRY_PROJECT_API_VERSION);
        ctx.output = Map.of("result", text);
        ctx.completed = true;
        executions.put(responseId, ctx);

        return ConductorAgentStartResponse.builder()
                .executionId(responseId)
                .agentName(agentId)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    // Classic Azure OpenAI Assistants: thread → message → run → poll.
    private ConductorAgentStartResponse startAgentClassic(
            ConductorAgentStartRequest request, String endpoint, AuthState auth) {
        String assistantId = resolveAssistantId(request);
        String apiVersion = resolveApiVersion(request);

        JsonNode threadResult =
                post(endpoint + "/threads", MAPPER.createObjectNode(), auth, apiVersion);
        String threadId = threadResult.path("id").asText();

        ObjectNode msgBody = MAPPER.createObjectNode();
        msgBody.put("role", "user");
        msgBody.put("content", request.getPrompt());
        post(endpoint + "/threads/" + threadId + "/messages", msgBody, auth, apiVersion);

        ObjectNode runBody = MAPPER.createObjectNode();
        runBody.put("assistant_id", assistantId);
        JsonNode runResult =
                post(endpoint + "/threads/" + threadId + "/runs", runBody, auth, apiVersion);
        String runId = runResult.path("id").asText();

        executions.put(
                threadId, new ExecutionContext(endpoint, assistantId, runId, auth, apiVersion));

        return ConductorAgentStartResponse.builder()
                .executionId(threadId)
                .agentName(assistantId)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    // Polls the run status and maps Azure states to ConductorAgentState:
    // completed → COMPLETED, requires_action → WAITING, failed/expired/cancelled → FAILED/CANCELED,
    // queued/in_progress → RUNNING. Responses API executions are already complete on first check.
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
        if (ctx.completed) {
            executions.remove(executionId);
            return ConductorAgentStatusResponse.builder()
                    .executionId(executionId)
                    .status(ConductorAgentState.COMPLETED)
                    .complete(true)
                    .output(ctx.output)
                    .build();
        }
        String runUrl = ctx.endpoint + "/threads/" + executionId + "/runs/" + ctx.runId;
        JsonNode run = get(runUrl, ctx.auth, ctx.apiVersion);
        return toStatusResponse(executionId, run, ctx);
    }

    // Submits a tool-call result (requires_action state) or posts a new user message for
    // multi-turn.
    @Override
    public void respond(ConductorAgentRespondRequest request) {
        String executionId = request.getExecutionId();
        ExecutionContext ctx = executions.get(executionId);
        if (ctx == null) {
            throw new IllegalStateException("No execution found for id: " + executionId);
        }

        // Check current run state to decide how to respond
        String runUrl = ctx.endpoint + "/threads/" + executionId + "/runs/" + ctx.runId;
        JsonNode run = get(runUrl, ctx.auth, ctx.apiVersion);
        String status = run.path("status").asText();

        if ("requires_action".equals(status)) {
            submitToolOutputs(executionId, ctx, request);
        } else {
            // Multi-turn: add message and start new run
            ObjectNode msgBody = MAPPER.createObjectNode();
            msgBody.put("role", "user");
            String content = request.getBody() != null ? request.getBody().toString() : "";
            msgBody.put("content", content);
            post(
                    ctx.endpoint + "/threads/" + executionId + "/messages",
                    msgBody,
                    ctx.auth,
                    ctx.apiVersion);

            ObjectNode runBody = MAPPER.createObjectNode();
            runBody.put("assistant_id", ctx.assistantId);
            JsonNode newRun =
                    post(
                            ctx.endpoint + "/threads/" + executionId + "/runs",
                            runBody,
                            ctx.auth,
                            ctx.apiVersion);
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
        String cancelUrl =
                ctx.endpoint + "/threads/" + executionId + "/runs/" + ctx.runId + "/cancel";
        try {
            post(cancelUrl, MAPPER.createObjectNode(), ctx.auth, ctx.apiVersion);
        } catch (Exception e) {
            log.warn("Failed to cancel Azure Foundry run {}: {}", ctx.runId, e.getMessage());
        }
    }

    private void submitToolOutputs(
            String threadId, ExecutionContext ctx, ConductorAgentRespondRequest request) {
        JsonNode run =
                get(
                        ctx.endpoint + "/threads/" + threadId + "/runs/" + ctx.runId,
                        ctx.auth,
                        ctx.apiVersion);
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
        post(submitUrl, body, ctx.auth, ctx.apiVersion);
    }

    private ConductorAgentStatusResponse toStatusResponse(
            String threadId, JsonNode run, ExecutionContext ctx) {
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
                            ctx.endpoint + "/threads/" + threadId + "/messages",
                            ctx.auth,
                            ctx.apiVersion);
            for (JsonNode msg : messages.path("data")) {
                if ("assistant".equals(msg.path("role").asText())) {
                    output = Map.of("result", extractText(msg.path("content")));
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

    // Returns an API-key AuthState if credentialRef contains apiKey, otherwise OAuth.
    private AuthState buildAuthState(ConductorAgentStartRequest request) {
        String credentialRef = request.getCredentialRef();
        if (StringUtils.isBlank(credentialRef)) {
            throw new IllegalArgumentException(
                    "credentialRef is required for Azure Foundry agent requests");
        }

        String apiKey = credentialResolutionService.resolve(credentialRef + ".apiKey");
        if (StringUtils.isNotBlank(apiKey)) {
            return new AuthState(apiKey);
        }

        String clientId = credentialResolutionService.resolve(credentialRef + ".client_id");
        String clientSecret = credentialResolutionService.resolve(credentialRef + ".client_secret");
        String tenantId = credentialResolutionService.resolve(credentialRef + ".tenant_id");

        if (StringUtils.isAnyBlank(clientId, clientSecret, tenantId)) {
            throw new IllegalStateException(
                    "Azure Foundry credential '"
                            + credentialRef
                            + "' must contain either apiKey, or client_id + client_secret + tenant_id");
        }

        String scope =
                StringUtils.defaultIfBlank(
                        rawConfig(request, "scope"),
                        credentialResolutionService.resolve(credentialRef + ".scope"));
        if (StringUtils.isBlank(scope)) {
            String endpoint = request.getAgentUrl();
            scope =
                    (endpoint != null && endpoint.contains("services.ai.azure.com"))
                            ? FOUNDRY_SCOPE
                            : DEFAULT_SCOPE;
        }

        return new AuthState(
                OAuthTokenProvider.forAzureEntraId(
                        httpClient, tenantId, clientId, clientSecret, scope));
    }

    private String resolveEndpoint(ConductorAgentStartRequest request) {
        String url = request.getAgentUrl();
        if (StringUtils.isBlank(url)) {
            url = credentialResolutionService.resolve("AZURE_FOUNDRY_ENDPOINT");
        }
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException(
                    "Azure Foundry endpoint must be provided via agentUrl or the AZURE_FOUNDRY_ENDPOINT secret");
        }
        // Strip embedded agent/assistant ID so callers always get the bare base endpoint.
        // e.g. …/agents/shailesh-analyst  → …
        //      …/assistants/asst_xxx      → …
        for (String marker : new String[] {"/agents/", "/assistants/"}) {
            int idx = url.lastIndexOf(marker);
            if (idx >= 0) {
                url = url.substring(0, idx);
                break;
            }
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String resolveAssistantId(ConductorAgentStartRequest request) {
        // Prefer ID embedded directly in agentUrl — no rawConfig needed.
        // Supports …/agents/shailesh-analyst (new Foundry) and …/assistants/asst_xxx (classic).
        String id = extractAgentIdFromUrl(request.getAgentUrl());
        if (StringUtils.isBlank(id)) {
            id = rawConfig(request, "assistantId");
        }
        if (StringUtils.isBlank(id)) {
            id = rawConfig(request, "agentId");
        }
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException(
                    "Agent ID must be in agentUrl (…/agents/NAME or …/assistants/asst_xxx)"
                            + " or rawConfig.assistantId");
        }
        return id;
    }

    // Parses the agent/assistant ID out of an agentUrl that embeds it in the path.
    // Returns null if neither /agents/ nor /assistants/ is present.
    private static String extractAgentIdFromUrl(String url) {
        if (url == null) return null;
        for (String marker : new String[] {"/agents/", "/assistants/"}) {
            int idx = url.lastIndexOf(marker);
            if (idx >= 0) {
                String id = url.substring(idx + marker.length());
                if (id.endsWith("/")) id = id.substring(0, id.length() - 1);
                return StringUtils.isBlank(id) ? null : id;
            }
        }
        return null;
    }

    private JsonNode post(String url, ObjectNode body, AuthState auth, String apiVersion) {
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
                        .header(auth.headerName(), auth.headerValue())
                        .build();
        return execute(request, url);
    }

    private JsonNode get(String url, AuthState auth, String apiVersion) {
        String fullUrl = url.contains("?") ? url : url + "?api-version=" + apiVersion;
        Request request =
                new Request.Builder()
                        .url(fullUrl)
                        .get()
                        .header(auth.headerName(), auth.headerValue())
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

    private static boolean isFoundryProjectEndpoint(String endpoint) {
        return endpoint != null && endpoint.contains("services.ai.azure.com");
    }

    // Resolves agent instructions: rawConfig.instructions first, then fetches from agents API.
    private String resolveInstructions(
            ConductorAgentStartRequest request, String endpoint, String agentId, AuthState auth) {
        String instructions = rawConfig(request, "instructions");
        if (StringUtils.isNotBlank(instructions)) {
            return instructions;
        }
        try {
            JsonNode agent =
                    get(endpoint + "/agents/" + agentId, auth, FOUNDRY_PROJECT_API_VERSION);
            return agent.path("versions")
                    .path("latest")
                    .path("definition")
                    .path("instructions")
                    .asText("");
        } catch (Exception e) {
            log.warn("Could not fetch instructions for agent {}: {}", agentId, e.getMessage());
            return null;
        }
    }

    private static String extractResponseText(JsonNode response) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String text = content.path("text").asText("");
                    if (!text.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(text);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String resolveApiVersion(ConductorAgentStartRequest request) {
        String v = rawConfig(request, "apiVersion");
        return StringUtils.isBlank(v) ? DEFAULT_API_VERSION : v;
    }

    // Returns the text value from the first content part with "type": "text".
    // Assistants with code interpreter may return an image_file part before the text part,
    // so content[0] is not always text.
    private static String extractText(JsonNode contentArray) {
        for (JsonNode part : contentArray) {
            if ("text".equals(part.path("type").asText())) {
                return part.path("text").path("value").asText("");
            }
        }
        return "";
    }

    private static String rawConfig(ConductorAgentStartRequest request, String key) {
        if (request.getRawConfig() == null) return null;
        Object value = request.getRawConfig().get(key);
        return value != null ? value.toString() : null;
    }

    // Per-execution state: endpoint, assistant, thread/run IDs, auth, and API version.
    // For Responses API executions, completed=true and output is set immediately on startAgent.
    private static class ExecutionContext {
        final String endpoint;
        final String assistantId;
        volatile String runId;
        final AuthState auth;
        final String apiVersion;
        volatile boolean completed;
        volatile Map<String, Object> output;

        ExecutionContext(
                String endpoint,
                String assistantId,
                String runId,
                AuthState auth,
                String apiVersion) {
            this.endpoint = endpoint;
            this.assistantId = assistantId;
            this.runId = runId;
            this.auth = auth;
            this.apiVersion = apiVersion;
        }
    }

    // Holds either an OAuth token provider or a static API key.
    // headerName/headerValue produce the correct HTTP header for each mode.
    private static class AuthState {
        private final OAuthTokenProvider tokenProvider;
        private final String apiKey;

        AuthState(OAuthTokenProvider tokenProvider) {
            this.tokenProvider = tokenProvider;
            this.apiKey = null;
        }

        AuthState(String apiKey) {
            this.tokenProvider = null;
            this.apiKey = apiKey;
        }

        String headerName() {
            return tokenProvider != null ? "Authorization" : "api-key";
        }

        String headerValue() {
            return tokenProvider != null ? "Bearer " + tokenProvider.getToken() : apiKey;
        }
    }
}
