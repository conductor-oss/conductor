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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.ai.agentspan.runtime.service.assistants.AssistantsRunApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;

/**
 * {@link ConductorAgentClient} backed by the OpenAI Assistants API.
 *
 * <p>Shares its whole wire protocol with {@link AzureFoundryAgentClient} through {@link
 * AssistantsRunApi} — the thread-and-run model is the same API. What differs is auth (a plain API
 * key rather than Entra ID client credentials), the base URL, and the {@code OpenAI-Beta} header.
 *
 * <p>Holds no per-run state. The executionId is the OpenAI thread id, and everything else needed to
 * reach a run is re-derived from the task input Conductor already persists, so any replica can
 * serve any poll, respond, or cancel.
 *
 * <p>Credentials: {@code credentialRef} names a secret holding the API key, either flat or under a
 * {@code .api_key} sub-key.
 *
 * <p>rawConfig keys: {@code assistantId} (required), {@code baseUrl} (optional override for a
 * compatible endpoint or a proxy).
 *
 * <p>Activated by {@code conductor.integrations.ai.enabled=true}, like the other agent clients.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class OpenAiAssistantsAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAssistantsAgentClient.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    // Required by the Assistants API for the v2 thread-and-run surface.
    private static final Map<String, String> ASSISTANTS_V2_HEADERS =
            Map.of("OpenAI-Beta", "assistants=v2");

    private final CredentialResolutionService credentialResolutionService;
    private final AssistantsRunApi api;

    public OpenAiAssistantsAgentClient(
            CredentialResolutionService credentialResolutionService,
            @Qualifier("conductorAiHttpClient") OkHttpClient httpClient) {
        this.credentialResolutionService = credentialResolutionService;
        this.api = new AssistantsRunApi(httpClient);
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_OPENAI_ASSISTANTS;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        AssistantsRunApi.Target target = target(request.getRawConfig());
        String threadId =
                api.createThreadAndRun(
                        target, apiKey(request.getCredentialRef()), request.getPrompt());
        return ConductorAgentStartResponse.builder()
                .executionId(threadId)
                .agentName(target.assistantId())
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(
            String executionId, ConductorAgentRequest request) {
        return api.status(
                target(request.getRawConfig()), apiKey(request.getCredentialRef()), executionId);
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        String threadId = request.getExecutionId();
        AssistantsRunApi.Target target = target(request.getRawConfig());
        String token = apiKey(request.getCredentialRef());

        JsonNode run = api.latestRun(target, token, threadId);
        if ("requires_action".equals(run.path("status").asText())) {
            api.submitToolOutputs(
                    target,
                    token,
                    threadId,
                    run,
                    AgentBodies.toolResults(request, outstandingToolCallIds(run)));
        } else {
            api.addMessageAndStartRun(target, token, threadId, AgentBodies.toMessage(request));
        }
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        try {
            api.cancelLatestRun(
                    target(request.getRawConfig()),
                    apiKey(request.getCredentialRef()),
                    request.getExecutionId());
        } catch (Exception e) {
            log.warn(
                    "Failed to cancel OpenAI Assistants run on thread {}: {}",
                    request.getExecutionId(),
                    e.getMessage());
        }
    }

    private AssistantsRunApi.Target target(Map<String, Object> rawConfig) {
        String assistantId = rawConfig(rawConfig, "assistantId");
        if (StringUtils.isBlank(assistantId)) {
            throw new IllegalArgumentException(
                    "rawConfig.assistantId is required for OpenAI Assistants agent requests");
        }
        String baseUrl =
                StringUtils.defaultIfBlank(rawConfig(rawConfig, "baseUrl"), DEFAULT_BASE_URL);
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        // No shared query string: unlike Azure, OpenAI takes no api-version.
        return new AssistantsRunApi.Target(baseUrl, assistantId, "", ASSISTANTS_V2_HEADERS);
    }

    // Read per call rather than cached: a static API key needs no token exchange, so this is one
    // secret-store read and no network round trip.
    private String apiKey(String credentialRef) {
        if (StringUtils.isBlank(credentialRef)) {
            throw new IllegalArgumentException(
                    "credentialRef is required for OpenAI Assistants agent requests");
        }
        // The sub-key first: a secret stored as JSON resolves flat to the whole document, which is
        // not blank and would otherwise be sent as the key.
        String key = credentialResolutionService.resolve(credentialRef + ".api_key");
        if (StringUtils.isBlank(key)) {
            key = credentialResolutionService.resolve(credentialRef);
        }
        if (StringUtils.isBlank(key)) {
            throw new IllegalStateException(
                    "OpenAI credential '"
                            + credentialRef
                            + "' must hold the API key, either directly or under .api_key");
        }
        return key;
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
}
