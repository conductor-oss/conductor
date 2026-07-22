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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.ContentBody;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.ReturnControlPayload;
import software.amazon.awssdk.services.bedrockagentruntime.model.SessionState;

/**
 * {@link ConductorAgentClient} backed by AWS Bedrock Agent Runtime.
 *
 * <p>Bedrock agents use a streaming invoke model — there is no separate status API. Each call to
 * {@code startAgent} or {@code respond} streams the response and buffers it into an in-memory
 * {@link ExecutionState}. Subsequent {@code getAgentStatus} calls read from that state.
 *
 * <p>Activated by {@code conductor.ai.bedrock-agent.enabled=true}.
 */
@Component
public class BedrockAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockAgentClient.class);
    private static final String DEFAULT_REGION = "us-east-1";

    private final CredentialResolutionService credentialResolutionService;
    private final ConcurrentHashMap<String, ExecutionState> executions = new ConcurrentHashMap<>();

    public BedrockAgentClient(CredentialResolutionService credentialResolutionService) {
        this.credentialResolutionService = credentialResolutionService;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_BEDROCK;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String sessionId =
                StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        String agentId = rawConfig(request, "agentId");
        String agentAliasId = rawConfig(request, "agentAliasId");
        String region = StringUtils.defaultIfBlank(rawConfig(request, "region"), DEFAULT_REGION);

        BedrockAgentRuntimeAsyncClient runtimeClient = buildRuntimeClient(request, region);
        InvokeAgentRequest invokeRequest =
                InvokeAgentRequest.builder()
                        .agentId(agentId)
                        .agentAliasId(agentAliasId)
                        .sessionId(sessionId)
                        .inputText(request.getPrompt())
                        .build();

        ExecutionState state = new ExecutionState(sessionId, runtimeClient, agentId, agentAliasId);
        executions.put(sessionId, state);
        invokeAndUpdateState(invokeRequest, state);

        return ConductorAgentStartResponse.builder()
                .executionId(sessionId)
                .agentName(agentId)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(String executionId) {
        ExecutionState state = executions.get(executionId);
        if (state == null) {
            return ConductorAgentStatusResponse.builder()
                    .executionId(executionId)
                    .status(ConductorAgentState.FAILED)
                    .complete(true)
                    .reasonForIncompletion("No execution found for id: " + executionId)
                    .build();
        }
        ConductorAgentState agentState = state.state.get();
        return ConductorAgentStatusResponse.builder()
                .executionId(executionId)
                .status(agentState)
                .complete(
                        agentState == ConductorAgentState.COMPLETED
                                || agentState == ConductorAgentState.FAILED)
                .running(agentState == ConductorAgentState.RUNNING)
                .waiting(agentState == ConductorAgentState.WAITING)
                .output(state.output)
                .pendingTool(state.pendingTool)
                .pendingToolName(state.pendingToolName)
                .build();
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        ExecutionState state = executions.get(request.getExecutionId());
        if (state == null) {
            throw new IllegalStateException(
                    "No execution found for id: " + request.getExecutionId());
        }

        // Build the returnControlInvocationResults from the respond body
        String toolResult = request.getBody() != null ? request.getBody().toString() : "";
        ContentBody contentBody = ContentBody.builder().body(toolResult).build();

        SessionState sessionState =
                SessionState.builder()
                        .returnControlInvocationResults(
                                rb ->
                                        rb.apiResult(
                                                ar ->
                                                        ar.actionGroup(state.pendingToolName)
                                                                .apiPath("/invoke")
                                                                .httpMethod("POST")
                                                                .responseBody(
                                                                        Map.of(
                                                                                "application/json",
                                                                                contentBody))))
                        .build();

        InvokeAgentRequest invokeRequest =
                InvokeAgentRequest.builder()
                        .agentId(state.agentId)
                        .agentAliasId(state.agentAliasId)
                        .sessionId(request.getExecutionId())
                        .sessionState(sessionState)
                        .build();

        invokeAndUpdateState(invokeRequest, state);
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        // Bedrock has no cancel API — clean up local state and log
        log.warn(
                "Bedrock agent runtime does not support cancellation; removing local state for executionId={}",
                request.getExecutionId());
        executions.remove(request.getExecutionId());
    }

    @Override
    public void close() {
        executions.values().forEach(s -> s.runtimeClient.close());
        executions.clear();
    }

    private void invokeAndUpdateState(InvokeAgentRequest invokeRequest, ExecutionState state) {
        state.state.set(ConductorAgentState.RUNNING);
        state.pendingTool = null;
        state.pendingToolName = null;
        StringBuilder textBuffer = new StringBuilder();
        AtomicReference<ReturnControlPayload> returnControl = new AtomicReference<>();

        InvokeAgentResponseHandler handler =
                InvokeAgentResponseHandler.builder()
                        .onResponse(r -> {})
                        .subscriber(
                                InvokeAgentResponseHandler.Visitor.builder()
                                        .onChunk(
                                                chunk -> {
                                                    if (chunk.bytes() != null) {
                                                        textBuffer.append(
                                                                chunk.bytes().asUtf8String());
                                                    }
                                                })
                                        .onReturnControl(returnControl::set)
                                        .build())
                        .build();

        state.runtimeClient.invokeAgent(invokeRequest, handler).join();

        if (returnControl.get() != null) {
            ReturnControlPayload payload = returnControl.get();
            state.state.set(ConductorAgentState.WAITING);
            // Store the first invocation input as the pending tool — callers inspect this
            if (payload.invocationInputs() != null && !payload.invocationInputs().isEmpty()) {
                String toolName =
                        payload.invocationInputs().get(0).apiInvocationInput() != null
                                ? payload.invocationInputs()
                                        .get(0)
                                        .apiInvocationInput()
                                        .actionGroup()
                                : "unknown";
                state.pendingToolName = toolName;
                state.pendingTool = Map.of("tool_name", toolName, "payload", payload.toString());
            }
        } else {
            state.state.set(ConductorAgentState.COMPLETED);
            state.output = Map.of("result", textBuffer.toString());
        }
    }

    private BedrockAgentRuntimeAsyncClient buildRuntimeClient(
            ConductorAgentStartRequest request, String region) {
        String credentialRef = request.getCredentialRef();
        if (StringUtils.isNotBlank(credentialRef)) {
            String accessKeyId =
                    credentialResolutionService.resolve(credentialRef + ".accessKeyId");
            String secretAccessKey =
                    credentialResolutionService.resolve(credentialRef + ".secretAccessKey");
            if (StringUtils.isNotBlank(accessKeyId) && StringUtils.isNotBlank(secretAccessKey)) {
                return BedrockAgentRuntimeAsyncClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                        .build();
            }
        }
        // Fall back to the default credential chain (instance role, env vars, ~/.aws/credentials)
        return BedrockAgentRuntimeAsyncClient.builder().region(Region.of(region)).build();
    }

    private static String rawConfig(ConductorAgentStartRequest request, String key) {
        if (request.getRawConfig() == null) return null;
        Object value = request.getRawConfig().get(key);
        return value != null ? value.toString() : null;
    }

    /** Per-execution mutable state buffered from the Bedrock streaming response. */
    private static class ExecutionState {
        final String sessionId;
        final BedrockAgentRuntimeAsyncClient runtimeClient;
        final String agentId;
        final String agentAliasId;
        final AtomicReference<ConductorAgentState> state =
                new AtomicReference<>(ConductorAgentState.RUNNING);
        volatile Map<String, Object> output;
        volatile Map<String, Object> pendingTool;
        volatile String pendingToolName;

        ExecutionState(
                String sessionId,
                BedrockAgentRuntimeAsyncClient runtimeClient,
                String agentId,
                String agentAliasId) {
            this.sessionId = sessionId;
            this.runtimeClient = runtimeClient;
            this.agentId = agentId;
            this.agentAliasId = agentAliasId;
        }
    }
}
