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

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.ai.agent.credentials.OAuthTokenProvider;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.ai.agentspan.runtime.service.assistants.AssistantsRunApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;

/**
 * {@link ConductorAgentClient} backed by Azure AI Foundry Agents.
 *
 * <p>The wire protocol is the OpenAI Assistants thread-and-run API, shared with {@link
 * OpenAiAssistantsAgentClient} through {@link AssistantsRunApi}. What this class adds is Azure's
 * auth — Entra ID client credentials, resolved from the Conductor secret store via {@code
 * credentialRef} with sub-keys {@code .client_id}, {@code .client_secret}, {@code .tenant_id} — and
 * the {@code api-version} query parameter.
 *
 * <p>Holds no per-run state. The executionId is the Azure thread id, and the thread is the
 * conversation: the run to act on is always the newest one on it, which Azure names on request.
 * Everything else needed to reach it — endpoint, assistantId, apiVersion, credentialRef, scope — is
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
    private static final String DEFAULT_SCOPE = "https://cognitiveservices.azure.com/.default";
    private static final String DEFAULT_API_VERSION = "2025-01-01-preview";

    // How long a cached token provider is reused before its credentials are re-read from the secret
    // store. Bounds staleness after a credential rotation; a rejected token evicts immediately, so
    // this is only the backstop.
    private static final Duration TOKEN_PROVIDER_TTL = Duration.ofMinutes(10);

    private final CredentialResolutionService credentialResolutionService;
    private final OkHttpClient httpClient;
    private final AssistantsRunApi api;
    private final Clock clock;

    // Caches the token provider, not the token — OAuthTokenProvider already caches and refreshes a
    // token internally, but only for as long as the provider itself lives. Rebuilding one per call
    // threw that cache away and made every 5-second status poll pay a full Entra ID round trip plus
    // three secret-store reads. Keyed per credential and scope, per JVM; holds no run state, so it
    // does not tie an execution to the replica that started it.
    private final ConcurrentHashMap<ProviderKey, CachedProvider> tokenProviders =
            new ConcurrentHashMap<>();

    // Explicit, because the Clock-taking test constructor below would make the choice ambiguous.
    @Autowired
    public AzureFoundryAgentClient(
            CredentialResolutionService credentialResolutionService,
            @Qualifier("conductorAiHttpClient") OkHttpClient httpClient) {
        this(credentialResolutionService, httpClient, Clock.systemUTC());
    }

    // Test seam: lets a test advance time instead of sleeping through the provider TTL.
    AzureFoundryAgentClient(
            CredentialResolutionService credentialResolutionService,
            OkHttpClient httpClient,
            Clock clock) {
        this.credentialResolutionService = credentialResolutionService;
        this.httpClient = httpClient;
        this.api = new AssistantsRunApi(httpClient);
        this.clock = clock;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_AZURE_FOUNDRY;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        Azure azure = azure(request.getCredentialRef(), request.getRawConfig());
        String threadId =
                withTokenEviction(
                        azure,
                        token ->
                                api.createThreadAndRun(azure.target(), token, request.getPrompt()));
        return ConductorAgentStartResponse.builder()
                .executionId(threadId)
                .agentName(azure.target().assistantId())
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(
            String executionId, ConductorAgentRequest request) {
        Azure azure = azure(request.getCredentialRef(), request.getRawConfig());
        return withTokenEviction(azure, token -> api.status(azure.target(), token, executionId));
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        String threadId = request.getExecutionId();
        Azure azure = azure(request.getCredentialRef(), request.getRawConfig());
        withTokenEviction(
                azure,
                token -> {
                    JsonNode run = api.latestRun(azure.target(), token, threadId);
                    if ("requires_action".equals(run.path("status").asText())) {
                        api.submitToolOutputs(
                                azure.target(),
                                token,
                                threadId,
                                run,
                                AgentBodies.toolResults(request, outstandingToolCallIds(run)));
                    } else {
                        // Multi-turn: a new run on the same thread. The caller's executionId stays
                        // valid, because the next poll resolves whichever run is newest.
                        api.addMessageAndStartRun(
                                azure.target(), token, threadId, AgentBodies.toMessage(request));
                    }
                    return null;
                });
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        String threadId = request.getExecutionId();
        Azure azure = azure(request.getCredentialRef(), request.getRawConfig());
        try {
            withTokenEviction(
                    azure,
                    token -> {
                        api.cancelLatestRun(azure.target(), token, threadId);
                        return null;
                    });
        } catch (Exception e) {
            log.warn(
                    "Failed to cancel Azure Foundry run on thread {}: {}",
                    threadId,
                    e.getMessage());
        }
    }

    // Runs one API interaction with a cached token, dropping that cached provider if Azure rejects
    // it. No retry here on purpose — the agent delegate polls again within seconds, well inside its
    // failure budget, and by then the provider has been rebuilt from the secret store.
    private <T> T withTokenEviction(Azure azure, TokenedCall<T> call) {
        String token = tokenProvider(azure.providerKey()).getToken();
        try {
            return call.apply(token);
        } catch (AssistantsRunApi.UnauthorizedException e) {
            tokenProviders.remove(azure.providerKey());
            throw e;
        }
    }

    private interface TokenedCall<T> {
        T apply(String token);
    }

    // Returns a cached provider when one is still fresh, otherwise resolves the credentials and
    // builds a new one. Deliberately get-then-put rather than computeIfAbsent: buildTokenProvider
    // reads the secret store, and running that inside a mapping function would hold a map lock
    // across the I/O. Two threads racing here each build a valid provider and the last write wins.
    private OAuthTokenProvider tokenProvider(ProviderKey key) {
        long now = clock.millis();
        CachedProvider cached = tokenProviders.get(key);
        if (cached != null && !cached.isExpired(now)) {
            return cached.provider();
        }
        // Only a successful build is cached — a missing or incomplete credential must keep raising
        // on every call rather than being remembered as a negative result.
        OAuthTokenProvider provider = buildTokenProvider(key.credentialRef(), resolveScope(key));
        tokenProviders.put(key, new CachedProvider(provider, now + TOKEN_PROVIDER_TTL.toMillis()));
        return provider;
    }

    // Resolved on a cache miss only. The key carries the rawConfig override rather than the
    // resolved value precisely so that looking up a cached provider needs no secret-store read: a
    // given credentialRef and override always resolve to the same scope.
    private String resolveScope(ProviderKey key) {
        String scope =
                StringUtils.defaultIfBlank(
                        key.scopeOverride(),
                        credentialResolutionService.resolve(key.credentialRef() + ".scope"));
        return StringUtils.defaultIfBlank(scope, DEFAULT_SCOPE);
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

    // Everything needed to reach an Azure run, rebuilt from the originating task input on every
    // call. This is what replaces holding per-run state in process.
    private Azure azure(String credentialRef, Map<String, Object> rawConfig) {
        if (StringUtils.isBlank(credentialRef)) {
            throw new IllegalArgumentException(
                    "credentialRef is required for Azure Foundry agent requests");
        }
        String apiVersion =
                StringUtils.defaultIfBlank(rawConfig(rawConfig, "apiVersion"), DEFAULT_API_VERSION);
        AssistantsRunApi.Target target =
                new AssistantsRunApi.Target(
                        resolveEndpoint(rawConfig),
                        resolveAssistantId(rawConfig),
                        "api-version=" + apiVersion,
                        Map.of());
        return new Azure(target, new ProviderKey(credentialRef, rawConfig(rawConfig, "scope")));
    }

    private String resolveEndpoint(Map<String, Object> rawConfig) {
        String endpoint = rawConfig(rawConfig, "endpoint");
        if (StringUtils.isBlank(endpoint)) {
            endpoint = credentialResolutionService.resolve("AZURE_FOUNDRY_ENDPOINT");
        }
        if (StringUtils.isBlank(endpoint)) {
            throw new IllegalArgumentException(
                    "Azure Foundry endpoint must be provided via rawConfig.endpoint or AZURE_FOUNDRY_ENDPOINT secret");
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private static String resolveAssistantId(Map<String, Object> rawConfig) {
        String id = rawConfig(rawConfig, "assistantId");
        if (StringUtils.isBlank(id)) {
            id = rawConfig(rawConfig, "agentId");
        }
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException(
                    "rawConfig.assistantId is required for Azure Foundry agent requests");
        }
        return id;
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

    private record Azure(AssistantsRunApi.Target target, ProviderKey providerKey) {}

    // scopeOverride is the raw rawConfig.scope value, which may be null — never the resolved scope,
    // so that a cache lookup costs no secret-store read.
    private record ProviderKey(String credentialRef, String scopeOverride) {}

    private record CachedProvider(OAuthTokenProvider provider, long expiresAtMillis) {

        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
