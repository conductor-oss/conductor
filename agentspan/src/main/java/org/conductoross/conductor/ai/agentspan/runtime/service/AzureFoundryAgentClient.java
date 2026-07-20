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
 * {@link ConductorAgentClient} backed by Azure AI Foundry via the A2A protocol.
 *
 * <p>Auth uses Entra ID client credentials flow. Credentials are resolved from the Conductor
 * secret store using the {@code credentialRef} on the start request, with dotted-path sub-keys
 * {@code .client_id}, {@code .client_secret}, and {@code .tenant_id}.
 *
 * <p>Activated by {@code conductor.ai.azure-foundry.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "conductor.ai.azure-foundry.enabled", havingValue = "true")
public class AzureFoundryAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AzureFoundryAgentClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_SCOPE = "https://management.azure.com/.default";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CredentialResolutionService credentialResolutionService;
    private final OkHttpClient httpClient;
    private final ConcurrentHashMap<String, ExecutionContext> executions = new ConcurrentHashMap<>();

    public AzureFoundryAgentClient(
            CredentialResolutionService credentialResolutionService, OkHttpClient httpClient) {
        this.credentialResolutionService = credentialResolutionService;
        this.httpClient = httpClient;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String endpoint = resolveEndpoint(request);
        OAuthTokenProvider tokenProvider = buildTokenProvider(request);
        String taskId = UUID.randomUUID().toString();

        ObjectNode params = MAPPER.createObjectNode();
        params.put("id", taskId);
        ObjectNode message = params.putObject("message");
        message.put("role", "user");
        ArrayNode parts = message.putArray("parts");
        parts.addObject().put("type", "text").put("text", request.getPrompt());

        String token = tokenProvider.getToken();
        callA2A(endpoint, "tasks/send", params, token);
        executions.put(taskId, new ExecutionContext(endpoint, token, tokenProvider));

        return ConductorAgentStartResponse.builder()
                .executionId(taskId)
                .agentName(endpoint)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

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
        ObjectNode params = MAPPER.createObjectNode();
        params.put("id", executionId);
        JsonNode result = callA2A(ctx.endpoint, "tasks/get", params, ctx.tokenProvider.getToken());
        return toStatusResponse(executionId, result);
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        ExecutionContext ctx = executions.get(request.getExecutionId());
        if (ctx == null) {
            throw new IllegalStateException("No execution found for id: " + request.getExecutionId());
        }
        ObjectNode params = MAPPER.createObjectNode();
        params.put("id", request.getExecutionId());
        ObjectNode message = params.putObject("message");
        message.put("role", "user");
        ArrayNode parts = message.putArray("parts");
        ObjectNode part = parts.addObject();
        part.put("type", "text");
        part.put("text", MAPPER.valueToTree(request.getBody()).toString());
        callA2A(ctx.endpoint, "tasks/send", params, ctx.tokenProvider.getToken());
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        ExecutionContext ctx = executions.remove(request.getExecutionId());
        if (ctx == null) {
            log.warn("cancelAgent called for unknown executionId={}", request.getExecutionId());
            return;
        }
        ObjectNode params = MAPPER.createObjectNode();
        params.put("id", request.getExecutionId());
        callA2A(ctx.endpoint, "tasks/cancel", params, ctx.tokenProvider.getToken());
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

        return OAuthTokenProvider.forAzureEntraId(httpClient, tenantId, clientId, clientSecret, scope);
    }

    private String resolveEndpoint(ConductorAgentStartRequest request) {
        String endpoint = rawConfig(request, "endpoint");
        if (StringUtils.isBlank(endpoint)) {
            endpoint = credentialResolutionService.resolve("AZURE_FOUNDRY_ENDPOINT");
        }
        if (StringUtils.isBlank(endpoint)) {
            throw new IllegalArgumentException(
                    "Azure Foundry endpoint must be provided in rawConfig.endpoint or AZURE_FOUNDRY_ENDPOINT secret");
        }
        return endpoint.endsWith("/") ? endpoint : endpoint + "/";
    }

    private JsonNode callA2A(String endpoint, String method, ObjectNode params, String bearerToken) {
        ObjectNode rpcBody = MAPPER.createObjectNode();
        rpcBody.put("jsonrpc", "2.0");
        rpcBody.put("method", method);
        rpcBody.put("id", UUID.randomUUID().toString());
        rpcBody.set("params", params);

        RequestBody body;
        try {
            body = RequestBody.create(MAPPER.writeValueAsBytes(rpcBody), JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize A2A request", e);
        }

        Request request =
                new Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .header("Authorization", "Bearer " + bearerToken)
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException(
                        "A2A call to " + method + " failed: HTTP " + response.code());
            }
            JsonNode json = MAPPER.readTree(response.body().string());
            if (json.has("error")) {
                throw new RuntimeException(
                        "A2A error from " + method + ": " + json.get("error").toString());
            }
            return json.path("result");
        } catch (IOException e) {
            throw new RuntimeException("A2A call to " + method + " failed", e);
        }
    }

    private ConductorAgentStatusResponse toStatusResponse(String executionId, JsonNode result) {
        String stateStr = result.path("status").path("state").asText("working");
        ConductorAgentState state = toState(stateStr);
        boolean complete = state == ConductorAgentState.COMPLETED || state == ConductorAgentState.FAILED || state == ConductorAgentState.CANCELED;
        Map<String, Object> output = null;
        if (complete && result.has("artifacts")) {
            output = Map.of("artifacts", result.get("artifacts").toString());
        }
        return ConductorAgentStatusResponse.builder()
                .executionId(executionId)
                .status(state)
                .complete(complete)
                .running(state == ConductorAgentState.RUNNING)
                .waiting(state == ConductorAgentState.WAITING)
                .output(output)
                .build();
    }

    private static ConductorAgentState toState(String a2aState) {
        return switch (a2aState) {
            case "completed" -> ConductorAgentState.COMPLETED;
            case "failed" -> ConductorAgentState.FAILED;
            case "canceled" -> ConductorAgentState.CANCELED;
            case "input-required" -> ConductorAgentState.WAITING;
            default -> ConductorAgentState.RUNNING; // submitted, working
        };
    }

    private static String rawConfig(ConductorAgentStartRequest request, String key) {
        if (request.getRawConfig() == null) return null;
        Object value = request.getRawConfig().get(key);
        return value != null ? value.toString() : null;
    }

    /** Per-execution context: endpoint URL and token provider for A2A calls. */
    private record ExecutionContext(String endpoint, String token, OAuthTokenProvider tokenProvider) {}
}
