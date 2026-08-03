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
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// ConductorAgentClient backed by Azure AI — supports three agent/model types and four auth modes.
//
// Agent types (auto-detected from agentUrl):
//   Classic Assistants:  https://my-resource.openai.azure.com/openai/assistants/asst_xxx
//   Foundry project:
// https://my-resource.services.ai.azure.com/api/projects/{proj}/agents/{name}
//   AI Inference:        https://my-resource.services.ai.azure.com/models  (or
// *.inference.ml.azure.com)
//
// Auth modes (auto-detected from credentialRef secret fields):
//   API key:             secret has apiKey → api-key header
//   Client credentials:  secret has client_id + client_secret + tenant_id → Bearer via
// ClientSecretCredential
//   Managed identity:    secret has clientId only → Bearer via ManagedIdentityCredential
// (user-assigned)
//                        no credentialRef or empty secret → system-assigned MI /
// DefaultAzureCredential
//   DefaultAzureCredential: no credentialRef → full chain (env vars → workload identity → MI → CLI)
//
// Activated by conductor.integrations.ai.enabled=true; an unconfigured runtime fails only if used.
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class AzureFoundryAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AzureFoundryAgentClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // OAuth scopes — auto-detected from endpoint URL, overridable via rawConfig.scope
    private static final String DEFAULT_SCOPE = "https://cognitiveservices.azure.com/.default";
    private static final String FOUNDRY_SCOPE = "https://ai.azure.com/.default";
    private static final String ML_INFERENCE_SCOPE = "https://ml.azure.com/.default";

    private static final String DEFAULT_API_VERSION = "2025-01-01-preview";
    private static final String FOUNDRY_PROJECT_API_VERSION = "2025-05-15-preview";
    private static final String INFERENCE_API_VERSION = "2024-05-01-preview";
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

    // Routes to the correct API based on endpoint URL:
    //   inference.ml.azure.com or .../models  → Azure AI Inference (chat/completions)
    //   services.ai.azure.com/api/projects/…  → Foundry project Responses API
    //   openai.azure.com/openai/…             → Classic Assistants Threads/Runs API
    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String endpoint = resolveEndpoint(request);
        AuthState auth = buildAuthState(request, endpoint);
        if (isInferenceEndpoint(endpoint)) {
            return startAgentInference(request, endpoint, auth);
        }
        if (isFoundryProjectEndpoint(endpoint)) {
            return startAgentResponses(request, endpoint, auth);
        }
        return startAgentClassic(request, endpoint, auth);
    }

    // Azure AI Inference: POST /chat/completions — OpenAI-compatible, stateless, synchronous.
    // Supports any model deployed on Foundry serverless (services.ai.azure.com/models) or
    // Azure ML online endpoints (*.inference.ml.azure.com).
    private ConductorAgentStartResponse startAgentInference(
            ConductorAgentStartRequest request, String endpoint, AuthState auth) {
        String model = StringUtils.defaultIfBlank(rawConfig(request, "model"), "gpt-4o");
        String systemPrompt = rawConfig(request, "instructions");

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        if (StringUtils.isNotBlank(systemPrompt)) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", request.getPrompt());

        // Azure ML scoring endpoints are the full URL — no extra path or api-version appended.
        // Foundry serverless /models endpoints use /chat/completions with api-version.
        boolean isMlEndpoint = endpoint.contains("inference.ml.azure.com");
        String url = isMlEndpoint ? endpoint : endpoint + "/chat/completions";
        String apiVersion = isMlEndpoint ? null : INFERENCE_API_VERSION;

        JsonNode response = post(url, body, auth, apiVersion);
        String text = response.path("choices").path(0).path("message").path("content").asText("");
        String execId =
                StringUtils.defaultIfBlank(
                        response.path("id").asText(), UUID.randomUUID().toString());

        ExecutionContext ctx =
                new ExecutionContext(endpoint, model, null, auth, INFERENCE_API_VERSION);
        ctx.output = Map.of("result", text);
        ctx.completed = true;
        executions.put(execId, ctx);

        return ConductorAgentStartResponse.builder()
                .executionId(execId)
                .agentName(model)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    // Foundry project agents: POST /openai/responses — synchronous, result available immediately.
    // Tools and instructions are fetched from the agent definition and forwarded to the Responses
    // API so that web_search, code_interpreter, and file_search actually run.
    private ConductorAgentStartResponse startAgentResponses(
            ConductorAgentStartRequest request, String endpoint, AuthState auth) {
        String agentId = resolveAssistantId(request);
        String model = rawConfig(request, "model");

        JsonNode agentDef = fetchAgentDefinition(endpoint, agentId, auth);
        String instructions =
                StringUtils.defaultIfBlank(
                        rawConfig(request, "instructions"),
                        agentDef.path("instructions").asText(""));

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", StringUtils.defaultIfBlank(model, "gpt-4o"));
        if (StringUtils.isNotBlank(instructions)) {
            body.put("instructions", instructions);
        }
        JsonNode tools = agentDef.path("tools");
        if (tools.isArray() && !tools.isEmpty()) {
            body.set("tools", toResponsesApiTools(tools));
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
    // queued/in_progress → RUNNING. Inference and Responses API executions are already complete.
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

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        String executionId = request.getExecutionId();
        ExecutionContext ctx = executions.get(executionId);
        if (ctx == null) {
            throw new IllegalStateException("No execution found for id: " + executionId);
        }

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
        if (ctx.completed) return;
        String cancelUrl =
                ctx.endpoint + "/threads/" + executionId + "/runs/" + ctx.runId + "/cancel";
        try {
            post(cancelUrl, MAPPER.createObjectNode(), ctx.auth, ctx.apiVersion);
        } catch (Exception e) {
            log.warn("Failed to cancel Azure run {}: {}", ctx.runId, e.getMessage());
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
            default -> ConductorAgentState.RUNNING;
        };
    }

    // Auth detection order:
    //   1. apiKey in secret          → API key header (no SDK)
    //   2. client_id/secret/tenant   → ClientSecretCredential (Service Principal)
    //   3. clientId only             → ManagedIdentityCredential (user-assigned)
    //   4. no credentialRef or empty → DefaultAzureCredential (env vars → MI → CLI)
    AuthState buildAuthState(ConductorAgentStartRequest request, String endpoint) {
        String scope = resolveScope(request, endpoint);
        String credentialRef = request.getCredentialRef();

        if (StringUtils.isNotBlank(credentialRef)) {
            // API key
            String apiKey = credentialResolutionService.resolve(credentialRef + ".apiKey");
            if (StringUtils.isNotBlank(apiKey)) {
                return new AuthState(apiKey);
            }

            // Service Principal (client credentials)
            String clientId = credentialResolutionService.resolve(credentialRef + ".client_id");
            String clientSecret =
                    credentialResolutionService.resolve(credentialRef + ".client_secret");
            String tenantId = credentialResolutionService.resolve(credentialRef + ".tenant_id");
            if (StringUtils.isNoneBlank(clientId, clientSecret, tenantId)) {
                TokenCredential cred =
                        new ClientSecretCredentialBuilder()
                                .tenantId(tenantId)
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                .build();
                return new AuthState(cred, scope);
            }

            // User-assigned managed identity
            String miClientId = credentialResolutionService.resolve(credentialRef + ".clientId");
            if (StringUtils.isNotBlank(miClientId)) {
                TokenCredential cred =
                        new ManagedIdentityCredentialBuilder().clientId(miClientId).build();
                return new AuthState(cred, scope);
            }
        }

        // DefaultAzureCredential: env vars → workload identity → managed identity → Azure CLI
        return new AuthState(new DefaultAzureCredentialBuilder().build(), scope);
    }

    // Scope auto-detected from endpoint URL; override via rawConfig.scope or credentialRef.scope.
    private String resolveScope(ConductorAgentStartRequest request, String endpoint) {
        String scope =
                StringUtils.defaultIfBlank(
                        rawConfig(request, "scope"),
                        StringUtils.isNotBlank(request.getCredentialRef())
                                ? credentialResolutionService.resolve(
                                        request.getCredentialRef() + ".scope")
                                : null);
        if (StringUtils.isNotBlank(scope)) return scope;

        if (endpoint.contains("inference.ml.azure.com")) return ML_INFERENCE_SCOPE;
        if (endpoint.contains("services.ai.azure.com")) return FOUNDRY_SCOPE;
        return DEFAULT_SCOPE;
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
        // e.g. …/agents/shailesh-analyst  → …/api/projects/{proj}
        //      …/assistants/asst_xxx      → …/openai
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
        // Supports …/agents/shailesh-analyst (Foundry) and …/assistants/asst_xxx (classic).
        String id = extractAgentIdFromUrl(request.getAgentUrl());
        if (StringUtils.isBlank(id)) id = rawConfig(request, "assistantId");
        if (StringUtils.isBlank(id)) id = rawConfig(request, "agentId");
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException(
                    "Agent ID must be in agentUrl (…/agents/NAME or …/assistants/asst_xxx)"
                            + " or rawConfig.assistantId");
        }
        return id;
    }

    // Parses agent/assistant ID out of an agentUrl that embeds it in the path.
    // Returns null if neither /agents/ nor /assistants/ is present.
    static String extractAgentIdFromUrl(String url) {
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

    static boolean isInferenceEndpoint(String endpoint) {
        return endpoint != null
                && (endpoint.contains("inference.ml.azure.com")
                        || (endpoint.contains("services.ai.azure.com")
                                && !endpoint.contains("/api/projects/")));
    }

    static boolean isFoundryProjectEndpoint(String endpoint) {
        return endpoint != null
                && endpoint.contains("services.ai.azure.com")
                && endpoint.contains("/api/projects/");
    }

    // Adapts agent definition tools to the Responses API format.
    // code_interpreter needs a container object; other tools pass through unchanged.
    static JsonNode toResponsesApiTools(JsonNode definitionTools) {
        ArrayNode result = MAPPER.createArrayNode();
        for (JsonNode tool : definitionTools) {
            String type = tool.path("type").asText();
            if ("code_interpreter".equals(type)) {
                ObjectNode t = MAPPER.createObjectNode();
                t.put("type", "code_interpreter");
                t.putObject("container").put("type", "auto");
                result.add(t);
            } else {
                result.add(tool);
            }
        }
        return result;
    }

    // Fetches the agent's latest version definition (instructions + tools) from the Foundry API.
    // Returns an empty ObjectNode on failure so callers can safely path-navigate without null
    // checks.
    private JsonNode fetchAgentDefinition(String endpoint, String agentId, AuthState auth) {
        try {
            JsonNode agent =
                    get(endpoint + "/agents/" + agentId, auth, FOUNDRY_PROJECT_API_VERSION);
            return agent.path("versions").path("latest").path("definition");
        } catch (Exception e) {
            log.warn("Could not fetch definition for agent {}: {}", agentId, e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    private JsonNode post(String url, ObjectNode body, AuthState auth, String apiVersion) {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        String fullUrl =
                (apiVersion != null && !url.contains("?"))
                        ? url + "?api-version=" + apiVersion
                        : url;
        Request request =
                new Request.Builder()
                        .url(fullUrl)
                        .post(RequestBody.create(bytes, JSON))
                        .header(auth.headerName(), auth.headerValue())
                        .build();
        return execute(request, url);
    }

    private JsonNode get(String url, AuthState auth, String apiVersion) {
        String fullUrl =
                (apiVersion != null && !url.contains("?"))
                        ? url + "?api-version=" + apiVersion
                        : url;
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
                        "Azure API call to "
                                + label
                                + " failed: HTTP "
                                + response.code()
                                + " — "
                                + responseBody);
            }
            return MAPPER.readTree(responseBody);
        } catch (IOException e) {
            throw new RuntimeException("Azure API call to " + label + " failed", e);
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

    private String resolveApiVersion(ConductorAgentStartRequest request) {
        String v = rawConfig(request, "apiVersion");
        return StringUtils.isBlank(v) ? DEFAULT_API_VERSION : v;
    }

    private static String rawConfig(ConductorAgentStartRequest request, String key) {
        if (request.getRawConfig() == null) return null;
        Object value = request.getRawConfig().get(key);
        return value != null ? value.toString() : null;
    }

    // Per-execution state: endpoint, assistant/model, thread/run IDs, auth, and API version.
    // For Inference and Responses API executions, completed=true and output is set immediately.
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

    // Holds either a static API key or an Azure SDK TokenCredential + scope.
    // The SDK caches and auto-refreshes tokens, so headerValue() is fast on repeated calls.
    static class AuthState {
        final TokenCredential credential;
        final String scope;
        final String apiKey;

        AuthState(String apiKey) {
            this.credential = null;
            this.scope = null;
            this.apiKey = apiKey;
        }

        AuthState(TokenCredential credential, String scope) {
            this.credential = credential;
            this.scope = scope;
            this.apiKey = null;
        }

        String headerName() {
            return credential != null ? "Authorization" : "api-key";
        }

        String headerValue() {
            if (credential != null) {
                return "Bearer "
                        + credential
                                .getToken(new TokenRequestContext().addScopes(scope))
                                .block()
                                .getToken();
            }
            return apiKey;
        }
    }
}
