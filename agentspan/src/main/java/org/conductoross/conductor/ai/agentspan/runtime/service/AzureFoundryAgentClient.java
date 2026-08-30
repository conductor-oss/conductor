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
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.agent.AgentBodies;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.ai.agentspan.runtime.service.assistants.AssistantsAuth;
import org.conductoross.conductor.ai.agentspan.runtime.service.assistants.AssistantsRunApi;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * {@link ConductorAgentClient} backed by Microsoft Foundry Agents.
 *
 * <p>The wire protocol is the OpenAI Assistants thread-and-run API, shared with {@link
 * OpenAiAssistantsAgentClient} through {@link AssistantsRunApi}. What this class adds is Azure's
 * auth — the Entra ID credential modes in {@link AzureFoundryAuth}, taken from the request's {@code
 * credentials} — and the {@code api-version} query parameter.
 *
 * <p>Holds no per-run state. The executionId is the Azure thread id, and the thread is the
 * conversation: the run to act on is always the newest one on it, which Azure names on request.
 * Everything else needed to reach it — endpoint, assistantId, apiVersion, credentials, scope — is
 * re-derived from the task input Conductor already persists and hands back on every call. So any
 * replica can serve any poll, respond, or cancel, with nothing shared between them.
 *
 * <p>rawConfig keys: {@code assistantId} (required), {@code endpoint} (required unless the {@code
 * AZURE_FOUNDRY_ENDPOINT} secret is set), {@code apiVersion}, {@code scope}.
 *
 * <p>Activated by {@code conductor.integrations.ai.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class AzureFoundryAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AzureFoundryAgentClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    private static final String DEFAULT_API_VERSION = "2025-01-01-preview";

    /** Foundry projects expose agents under their own, newer api-version. */
    private static final String FOUNDRY_PROJECT_API_VERSION = "2025-05-15-preview";

    /** Model inference on Foundry serverless. */
    private static final String INFERENCE_API_VERSION = "2024-05-01-preview";

    // How long resolved auth is reused before the credential is re-read from the secret store.
    // Bounds staleness after a rotation; a rejected token evicts immediately, so this is only the
    // backstop.
    private static final Duration AUTH_TTL = Duration.ofMinutes(10);

    private final OkHttpClient httpClient;
    private final AssistantsRunApi api;
    private final Clock clock;

    // Caches resolved auth, not tokens — an SDK credential refreshes its own token, so what is
    // worth keeping is the credential and the three secret reads behind it. Rebuilding per call
    // made every 5-second poll pay a token round trip plus those reads. Keyed per credential and
    // scope, per JVM; holds no run state. Auth resolved on behalf of a caller is never cached: it
    // belongs to that person, not the deployment.
    private final ConcurrentHashMap<ProviderKey, CachedAuth> resolvedAuth =
            new ConcurrentHashMap<>();

    // Visible for tests: how often auth was actually built. Rebuilding per call would throw away
    // the token the SDK credential caches inside itself, which is what the cache exists to prevent.
    private final java.util.concurrent.atomic.AtomicInteger authResolutions =
            new java.util.concurrent.atomic.AtomicInteger();

    int authResolutions() {
        return authResolutions.get();
    }

    // Explicit, because the Clock-taking test constructor below would make the choice ambiguous.
    @Autowired
    public AzureFoundryAgentClient(@Qualifier("conductorAiHttpClient") OkHttpClient httpClient) {
        this(httpClient, Clock.systemUTC());
    }

    // Test seam: lets a test advance time instead of sleeping through the provider TTL.
    AzureFoundryAgentClient(OkHttpClient httpClient, Clock clock) {
        this.httpClient = httpClient;
        this.api = new AssistantsRunApi(httpClient);
        this.clock = clock;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_MICROSOFT_FOUNDRY;
    }

    @Override
    public java.util.Set<String> agentTypeAliases() {
        return java.util.Set.of(A2AService.AGENT_TYPE_AZURE_FOUNDRY);
    }

    /**
     * Routes to the surface this endpoint actually is. Foundry has three, and they are not one
     * protocol:
     *
     * <ul>
     *   <li>{@code inference.ml.azure.com}, or {@code services.ai.azure.com} without a project —
     *       model inference, synchronous
     *   <li>{@code services.ai.azure.com/api/projects/…} — a project's Responses API, synchronous
     *   <li>anything else, typically {@code openai.azure.com} — classic Assistants threads and
     *       runs, which is the only pollable one
     * </ul>
     */
    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        Azure azure =
                azure(
                        request.getCredentials(),
                        request.getAgentUrl(),
                        request.getRawConfig(),
                        request.isUseCallerIdentity() ? request.getUserAssertion() : null);
        Surface surface = surfaceOf(azure.target().baseUrl(), request.getRawConfig());
        if (surface == Surface.INFERENCE) {
            return withTokenEviction(azure, auth -> startInference(request, azure, auth));
        }
        if (surface == Surface.RESPONSES) {
            return withTokenEviction(azure, auth -> startResponse(request, azure, auth));
        }

        String threadId =
                withTokenEviction(
                        azure,
                        auth -> api.createThreadAndRun(azure.target(), auth, request.getPrompt()));
        return ConductorAgentStartResponse.builder()
                .executionId(threadId)
                .agentName(azure.target().assistantId())
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    /**
     * Model inference — an OpenAI-compatible chat completion. Synchronous, so the answer is
     * reported from the start call and the task never polls; there is no thread or run to poll for.
     */
    private ConductorAgentStartResponse startInference(
            ConductorAgentStartRequest request, Azure azure, AssistantsAuth auth) {
        String endpoint = azure.target().baseUrl();
        String model =
                StringUtils.defaultIfBlank(rawConfig(request.getRawConfig(), "model"), "gpt-4o");
        String instructions = rawConfig(request.getRawConfig(), "instructions");

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        if (StringUtils.isNotBlank(instructions)) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", instructions);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", request.getPrompt());

        // An Azure ML scoring endpoint is the whole URL and takes no api-version; a Foundry
        // serverless /models endpoint wants /chat/completions and one.
        boolean mlEndpoint = endpoint.contains("inference.ml.azure.com");
        String url = mlEndpoint ? endpoint : endpoint + "/chat/completions";
        JsonNode response = postJson(url, body, auth, mlEndpoint ? null : INFERENCE_API_VERSION);

        String text = response.path("choices").path(0).path("message").path("content").asText("");
        return ConductorAgentStartResponse.builder()
                .executionId(
                        StringUtils.defaultIfBlank(
                                response.path("id").asText(null), UUID.randomUUID().toString()))
                .agentName(model)
                .requiredWorkers(Collections.emptyList())
                .state(ConductorAgentState.COMPLETED)
                .output(Map.of("result", text))
                .build();
    }

    /**
     * A Foundry project agent through the Responses API, invoked by reference.
     *
     * <p>The agent is named in the request and Foundry applies its own model, instructions and
     * tools. This replaced reading the agent's definition and replaying it as an anonymous
     * response: that produced the right answer but Azure had no idea which agent it belonged to, so
     * the run appeared in no agent's history and in none of the project's per-agent monitoring.
     * Anything the definition held that was not copied - model parameters, response format,
     * attached knowledge, the pinned version - was quietly lost with it.
     *
     * <p>Synchronous, like inference.
     */
    private ConductorAgentStartResponse startResponse(
            ConductorAgentStartRequest request, Azure azure, AssistantsAuth auth) {
        String endpoint = azure.target().baseUrl();
        String agentId = azure.target().assistantId();

        ObjectNode body = MAPPER.createObjectNode();
        // agent_reference, not agent: the service rejects the latter as deprecated, though parts of
        // the REST documentation still show it.
        ObjectNode agent = body.putObject("agent_reference");
        agent.put("type", "agent_reference");
        agent.put("name", agentId);
        String agentVersion = rawConfig(request.getRawConfig(), "agentVersion");
        if (StringUtils.isNotBlank(agentVersion)) {
            agent.put("version", agentVersion);
        }
        // A conversation makes the turn part of an ongoing thread Foundry keeps; without one the
        // response stands alone, which is what a single AGENT task wants.
        String conversation = rawConfig(request.getRawConfig(), "conversation");
        if (StringUtils.isNotBlank(conversation)) {
            body.put("conversation", conversation);
        }
        ObjectNode message = body.putArray("input").addObject();
        message.put("role", "user");
        message.put("content", request.getPrompt());

        // No api-version: the project's Responses API is versioned in the path itself.
        JsonNode response = postJson(endpoint + "/openai/v1/responses", body, auth, null);
        List<Map<String, Object>> executedTools = extractExecutedTools(response);
        logOutputItems(agentId, response, executedTools);
        return ConductorAgentStartResponse.builder()
                .executionId(response.path("id").asText())
                .agentName(agentId)
                .requiredWorkers(Collections.emptyList())
                .state(ConductorAgentState.COMPLETED)
                .output(Map.of("result", extractResponseText(response)))
                .executedTools(executedTools)
                .build();
    }

    /**
     * The kinds of item a reply was made of, for diagnosing an empty executedTools.
     *
     * <p>A run whose answer cites the web but reports no tool call is either a response that did
     * not carry its tool items or an extraction that did not recognise them, and the two are
     * indistinguishable from the task output alone. Types only, never content: the reply is the
     * user's own data and this is a routine log line.
     */
    private void logOutputItems(
            String agentId, JsonNode response, List<Map<String, Object>> executedTools) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<String> types = new ArrayList<>();
        for (JsonNode item : response.path("output")) {
            types.add(item.path("type").asText("<no type>"));
        }
        log.debug(
                "Foundry response {} for agent {} returned output items {}, of which {} read as"
                        + " tool calls",
                response.path("id").asText(""),
                agentId,
                types,
                executedTools.size());
    }

    /**
     * The tool calls Foundry ran itself while producing this reply, in order.
     *
     * <p>The Responses API returns one output item per step, only some of which are messages. A
     * built-in tool - web search, code interpreter, file search - appears as its own item and never
     * pauses the run, so it is invisible to the pendingTools path that reports the function calls a
     * workflow has to run. Reading them here is what makes an agent's own work show up in the
     * execution rather than only its final answer.
     *
     * <p>The shape differs per tool and Azure adds new ones, so each item's own fields are carried
     * across as they come rather than mapped onto a fixed schema.
     */
    static List<Map<String, Object>> extractExecutedTools(JsonNode response) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (JsonNode output : response.path("output")) {
            String type = output.path("type").asText("");
            if (type.isEmpty() || "message".equals(type) || "reasoning".equals(type)) {
                continue;
            }
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("type", type);
            putIfPresent(call, output, "id", "tool_call_id");
            putIfPresent(call, output, "name", "tool_name");
            putIfPresent(call, output, "status", "status");
            // Whatever the tool was actually given, under whichever key this tool uses for it.
            for (String inputKey : TOOL_INPUT_KEYS) {
                putIfPresent(call, output, inputKey, inputKey);
            }
            calls.add(call);
        }
        return calls;
    }

    // The fields Foundry's built-in tools carry their input in. A tool that names it something else
    // still shows up, with its type and status, which is the part that matters most.
    private static final List<String> TOOL_INPUT_KEYS =
            List.of("action", "arguments", "code", "queries", "query", "container_id");

    private static void putIfPresent(
            Map<String, Object> target, JsonNode source, String field, String as) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        target.put(
                as,
                value.isValueNode() ? value.asText() : MAPPER.convertValue(value, Object.class));
    }

    /** The text parts of a Responses API reply, in order. */
    static String extractResponseText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String part = content.path("text").asText("");
                    if (!part.isEmpty()) {
                        if (text.length() > 0) {
                            text.append('\n');
                        }
                        text.append(part);
                    }
                }
            }
        }
        return text.toString();
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(
            String executionId, ConductorAgentRequest request) {
        Azure azure =
                azure(
                        request.getCredentials(),
                        request.getAgentUrl(),
                        request.getRawConfig(),
                        request.isUseCallerIdentity() ? request.getUserAssertion() : null);
        if (surfaceOf(azure.target().baseUrl(), request.getRawConfig()) != Surface.ASSISTANTS) {
            // These surfaces answer inside startAgent and the result is already in the task output.
            // Reaching here means the task was requeued after that, so the honest answer is the
            // terminal state that no longer needs polling.
            return ConductorAgentStatusResponse.builder()
                    .executionId(executionId)
                    .status(ConductorAgentState.COMPLETED)
                    .complete(true)
                    .build();
        }
        return withTokenEviction(azure, auth -> api.status(azure.target(), auth, executionId));
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        String threadId = request.getExecutionId();
        Azure azure = azure(request.getCredentials(), request.getRawConfig());
        if (surfaceOf(azure.target().baseUrl(), request.getRawConfig()) != Surface.ASSISTANTS) {
            throw new IllegalArgumentException(
                    "This Azure endpoint answers in one shot and has no conversation to continue;"
                            + " start a new AGENT task instead of resuming "
                            + threadId);
        }
        withTokenEviction(
                azure,
                auth -> {
                    JsonNode run = api.latestRun(azure.target(), auth, threadId);
                    if ("requires_action".equals(run.path("status").asText())) {
                        api.submitToolOutputs(
                                azure.target(),
                                auth,
                                threadId,
                                run,
                                AgentBodies.toolResults(request, outstandingToolCallIds(run)));
                    } else {
                        // Multi-turn: a new run on the same thread. The caller's executionId stays
                        // valid, because the next poll resolves whichever run is newest.
                        api.addMessageAndStartRun(
                                azure.target(), auth, threadId, AgentBodies.toMessage(request));
                    }
                    return null;
                });
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        String threadId = request.getExecutionId();
        try {
            // Inside the try: locating the run resolves credentials, and cancellation is
            // best-effort, so a credential that no longer resolves should warn like any other
            // cancel failure rather than propagate.
            Azure azure = azure(request.getCredentials(), request.getRawConfig());
            withTokenEviction(
                    azure,
                    auth -> {
                        api.cancelLatestRun(azure.target(), auth, threadId);
                        return null;
                    });
        } catch (Exception e) {
            log.warn(
                    "Failed to cancel Microsoft Foundry run on thread {}: {}",
                    threadId,
                    e.getMessage());
        }
    }

    // Runs one API interaction with a cached token, dropping that cached provider if Azure rejects
    // it. No retry here on purpose — the agent delegate polls again within seconds, well inside its
    // failure budget, and by then the provider has been rebuilt from the secret store.
    // --- discovery ----------------------------------------------------------------------------

    /**
     * The agents visible at this endpoint, so Foundry agents appear in the agent list alongside
     * agents defined in Conductor. Best effort: one misconfigured credential returns nothing rather
     * than breaking the whole listing.
     */
    public List<AgentSummary> listExternalAgents(Map<String, String> credentials, String endpoint) {
        String base = trimTrailingSlash(endpoint);
        try {
            List<AgentSummary> agents = new ArrayList<>();
            for (JsonNode item : discoverAgents(credentials, base)) {
                agents.add(
                        AgentSummary.builder()
                                .name(item.path("name").asText(item.path("id").asText("unknown")))
                                .version(1)
                                .type(A2AService.AGENT_TYPE_MICROSOFT_FOUNDRY)
                                .description(item.path("description").asText(null))
                                // Azure reports seconds; AgentSummary carries millis.
                                .createTime(item.path("created_at").asLong(0) * 1000L)
                                .build());
            }
            log.debug("Discovered {} Microsoft Foundry agent(s) at {}", agents.size(), base);
            return agents;
        } catch (Exception e) {
            log.warn("Failed to list Microsoft Foundry agents at {}: {}", base, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** One agent's definition by name or id, or null when this endpoint does not have it. */
    public Map<String, Object> getExternalAgentDef(
            String agentName, Map<String, String> credentials, String endpoint) {
        String base = trimTrailingSlash(endpoint);
        try {
            for (JsonNode item : discoverAgents(credentials, base)) {
                if (agentName.equals(item.path("name").asText())
                        || agentName.equals(item.path("id").asText())) {
                    Map<String, Object> definition =
                            MAPPER.convertValue(
                                    item, new TypeReference<LinkedHashMap<String, Object>>() {});
                    definition.put("provider", A2AService.AGENT_TYPE_MICROSOFT_FOUNDRY);
                    definition.put("endpoint", base);
                    return definition;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch Microsoft Foundry agent '{}' at {}: {}",
                    agentName,
                    base,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Lists agents from whichever surface this endpoint is. A Foundry project serves them under
     * {@code /agents}; the classic Assistants endpoint under {@code /openai/assistants}.
     */
    private JsonNode discoverAgents(Map<String, String> credentials, String endpoint) {
        boolean foundryProject = isFoundryProjectEndpoint(endpoint);
        String url =
                endpoint
                        + (foundryProject ? "/agents" : "/openai/assistants")
                        + "?api-version="
                        + (foundryProject ? FOUNDRY_PROJECT_API_VERSION : DEFAULT_API_VERSION);

        String scope =
                StringUtils.defaultIfBlank(
                        AgentCredentials.value(credentials, "scope"),
                        AzureFoundryAuth.scopeFor(endpoint));
        AssistantsAuth auth = AzureFoundryAuth.resolve(credentials, httpClient, null, scope);

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .header(auth.headerName(), auth.headerValue())
                        .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "Microsoft Foundry agent listing failed: HTTP " + response.code());
            }
            JsonNode data = MAPPER.readTree(body).path("data");
            return data.isArray() ? data : MAPPER.createArrayNode();
        } catch (IOException e) {
            throw new IllegalStateException("Microsoft Foundry agent listing failed", e);
        }
    }

    /**
     * Which Foundry surface an endpoint is.
     *
     * <p>Inferred from the hostname, which covers the public cloud. {@code rawConfig.surface} —
     * {@code inference}, {@code responses}, or {@code assistants} — overrides that, because the
     * hostnames of sovereign clouds ({@code .azure.us}, {@code .azure.cn}), private endpoints and
     * proxies do not match the public patterns.
     */
    static Surface surfaceOf(String endpoint, Map<String, Object> rawConfig) {
        String declared = rawConfig(rawConfig, "surface");
        if (StringUtils.isNotBlank(declared)) {
            return switch (declared.trim().toLowerCase()) {
                case "inference" -> Surface.INFERENCE;
                case "responses" -> Surface.RESPONSES;
                case "assistants" -> Surface.ASSISTANTS;
                default ->
                        throw new IllegalArgumentException(
                                "rawConfig.surface must be inference, responses, or assistants; got: "
                                        + declared);
            };
        }
        if (isInferenceEndpoint(endpoint)) {
            return Surface.INFERENCE;
        }
        return isFoundryProjectEndpoint(endpoint) ? Surface.RESPONSES : Surface.ASSISTANTS;
    }

    /** The three APIs Foundry serves, which do not share a protocol. */
    enum Surface {
        /** Chat completions. Synchronous. */
        INFERENCE,
        /** A project's Responses API. Synchronous. */
        RESPONSES,
        /** Classic threads and runs. The only pollable one. */
        ASSISTANTS
    }

    /** A Foundry project endpoint, which serves the newer agent and Responses APIs. */
    static boolean isFoundryProjectEndpoint(String endpoint) {
        return endpoint != null
                && endpoint.contains("services.ai.azure.com")
                && endpoint.contains("/api/projects/");
    }

    /** A model-inference endpoint, which is synchronous and has no thread or run of its own. */
    static boolean isInferenceEndpoint(String endpoint) {
        return endpoint != null
                && (endpoint.contains("inference.ml.azure.com")
                        || (endpoint.contains("services.ai.azure.com")
                                && !endpoint.contains("/api/projects/")));
    }

    private JsonNode postJson(String url, ObjectNode body, AssistantsAuth auth, String apiVersion) {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize request body", e);
        }
        return execute(
                new Request.Builder()
                        .url(withApiVersion(url, apiVersion))
                        .post(RequestBody.create(bytes, JSON))
                        .header(auth.headerName(), auth.headerValue())
                        .build(),
                url);
    }

    private JsonNode getJson(String url, AssistantsAuth auth, String apiVersion) {
        return execute(
                new Request.Builder()
                        .url(withApiVersion(url, apiVersion))
                        .get()
                        .header(auth.headerName(), auth.headerValue())
                        .build(),
                url);
    }

    private static String withApiVersion(String url, String apiVersion) {
        if (StringUtils.isBlank(apiVersion) || url.contains("api-version=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "api-version=" + apiVersion;
    }

    private JsonNode execute(Request request, String label) {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (response.code() == 401 || response.code() == 403) {
                throw new AssistantsRunApi.UnauthorizedException(
                        "Microsoft Foundry call to "
                                + label
                                + " was rejected: HTTP "
                                + response.code());
            }
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "Microsoft Foundry call to "
                                + label
                                + " failed: HTTP "
                                + response.code()
                                + " — "
                                + body);
            }
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Microsoft Foundry call to " + label + " failed", e);
        }
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private <T> T withTokenEviction(Azure azure, AuthedCall<T> call) {
        AssistantsAuth auth = auth(azure);
        try {
            return call.apply(auth);
        } catch (AssistantsRunApi.UnauthorizedException e) {
            resolvedAuth.remove(azure.providerKey());
            throw e;
        }
    }

    private interface AuthedCall<T> {
        T apply(AssistantsAuth auth);
    }

    /**
     * Auth for one call, from cache when it may be reused. Auth resolved on behalf of a caller
     * bypasses the cache: the token is that person's, and keeping it would hand their identity to
     * whoever polls next.
     */
    private AssistantsAuth auth(Azure azure) {
        ProviderKey key = azure.providerKey();
        if (StringUtils.isNotBlank(azure.userAssertion())) {
            return AzureFoundryAuth.resolve(
                    key.credentials(), httpClient, azure.userAssertion(), key.scope());
        }

        long now = clock.millis();
        CachedAuth cached = resolvedAuth.get(key);
        if (cached != null && !cached.isExpired(now)) {
            return cached.auth();
        }
        // Get-then-put rather than computeIfAbsent: building a credential is cheap but not free,
        // and a mapping function would hold a map lock while every other poll waits behind it.
        authResolutions.incrementAndGet();
        AzureFoundryAuth resolved =
                AzureFoundryAuth.resolve(key.credentials(), httpClient, null, key.scope());
        if (resolved.isReusable()) {
            resolvedAuth.put(key, new CachedAuth(resolved, now + AUTH_TTL.toMillis()));
        }
        return resolved;
    }

    // Everything needed to reach an Azure run, rebuilt from the originating task input on every
    // call. This is what replaces holding per-run state in process.
    private Azure azure(Map<String, String> credentials, Map<String, Object> rawConfig) {
        return azure(credentials, null, rawConfig, null);
    }

    /**
     * Everything needed to reach an Azure run, rebuilt from the originating task input on every
     * call. {@code agentUrl} is preferred over {@code rawConfig.endpoint} so every agent type names
     * its location the same way A2A does.
     */
    private Azure azure(
            Map<String, String> credentials,
            String agentUrl,
            Map<String, Object> rawConfig,
            String userAssertion) {
        // Credentials are optional: the caller's own identity or the host's default credential
        // chain may supply them.
        String endpoint = resolveEndpoint(endpointFromUrl(agentUrl), rawConfig);
        String apiVersion =
                StringUtils.defaultIfBlank(rawConfig(rawConfig, "apiVersion"), DEFAULT_API_VERSION);
        AssistantsRunApi.Target target =
                new AssistantsRunApi.Target(
                        endpoint,
                        resolveAssistantId(agentUrl, rawConfig),
                        "api-version=" + apiVersion,
                        Map.of());
        return new Azure(
                target,
                new ProviderKey(credentials, resolveScope(credentials, rawConfig, endpoint)),
                userAssertion);
    }

    /**
     * The scope for this endpoint. Explicit configuration wins, from {@code rawConfig.scope} or a
     * {@code scope} credential; otherwise it follows the Foundry surface, whose several APIs do not
     * share one.
     */
    private static String resolveScope(
            Map<String, String> credentials, Map<String, Object> rawConfig, String endpoint) {
        String configured =
                StringUtils.defaultIfBlank(
                        rawConfig(rawConfig, "scope"),
                        AgentCredentials.value(credentials, "scope"));
        return StringUtils.defaultIfBlank(configured, AzureFoundryAuth.scopeFor(endpoint));
    }

    private static String resolveEndpoint(String agentUrl, Map<String, Object> rawConfig) {
        String endpoint = StringUtils.defaultIfBlank(agentUrl, rawConfig(rawConfig, "endpoint"));
        if (StringUtils.isBlank(endpoint)) {
            throw new IllegalArgumentException(
                    "Microsoft Foundry endpoint must be provided via agentUrl or rawConfig.endpoint."
                            + " An endpoint kept in a secret is written as"
                            + " ${workflow.secrets.NAME}, which Conductor substitutes before the"
                            + " task runs.");
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    /**
     * The assistant to run. {@code rawConfig} wins; otherwise it is taken from the agentUrl, which
     * may name it directly — {@code …/assistants/asst_x} on the classic surface, {@code
     * …/agents/name} on a Foundry project.
     */
    private static String resolveAssistantId(String agentUrl, Map<String, Object> rawConfig) {
        String id =
                StringUtils.defaultIfBlank(
                        rawConfig(rawConfig, "assistantId"), rawConfig(rawConfig, "agentId"));
        if (StringUtils.isBlank(id)) {
            id = agentIdFromUrl(agentUrl);
        }
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException(
                    "The agent must be named, either as rawConfig.assistantId or in agentUrl"
                            + " (…/assistants/asst_x or …/agents/NAME)");
        }
        return id;
    }

    /** The trailing agent name in an agentUrl, or null when it names only the endpoint. */
    static String agentIdFromUrl(String agentUrl) {
        if (StringUtils.isBlank(agentUrl)) {
            return null;
        }
        for (String marker : new String[] {"/agents/", "/assistants/"}) {
            int at = agentUrl.lastIndexOf(marker);
            if (at >= 0) {
                String id = agentUrl.substring(at + marker.length());
                return StringUtils.trimToNull(StringUtils.substringBefore(id, "?"));
            }
        }
        return null;
    }

    /**
     * The endpoint an agentUrl points at, with any trailing agent name removed — so one field can
     * carry both without the agent name ending up in every request path.
     */
    static String endpointFromUrl(String agentUrl) {
        if (StringUtils.isBlank(agentUrl)) {
            return null;
        }
        for (String marker : new String[] {"/agents/", "/assistants/"}) {
            int at = agentUrl.lastIndexOf(marker);
            if (at >= 0) {
                // …/agents/NAME  -> …/api/projects/{proj};  …/assistants/asst_x -> …/openai
                return agentUrl.substring(0, at);
            }
        }
        return agentUrl;
    }

    // The calls the provider is actually waiting on, which is more authoritative than whatever the
    // caller believes is outstanding.
    private static List<String> outstandingToolCallIds(JsonNode run) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> call : AssistantsRunApi.describeToolCalls(run)) {
            ids.add(String.valueOf(call.get("tool_call_id")));
        }
        return ids;
    }

    private static String rawConfig(Map<String, Object> rawConfig, String key) {
        if (rawConfig == null) return null;
        Object value = rawConfig.get(key);
        return value != null ? value.toString() : null;
    }

    private record Azure(
            AssistantsRunApi.Target target, ProviderKey providerKey, String userAssertion) {}

    // What a token actually depends on: who is asking, and which resource the token is for. Two
    // endpoints on the same scope share one token provider, which is correct - a token is scoped to
    // an Azure resource, not to a URL.
    private record ProviderKey(Map<String, String> credentials, String scope) {}

    private record CachedAuth(AzureFoundryAuth auth, long expiresAtMillis) {

        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
