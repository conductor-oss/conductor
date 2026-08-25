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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.ContentBody;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.ReturnControlPayload;
import software.amazon.awssdk.services.bedrockagentruntime.model.SessionState;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

// ConductorAgentClient backed by AWS Bedrock Agent Runtime.
// Bedrock uses a streaming invoke model — startAgent/respond stream the response into an
// in-memory ExecutionState; getAgentStatus reads from that state (no separate status API).
//
// Auth modes (auto-detected from credentialRef secret fields):
//   Static credentials:  secret has accessKeyId + secretAccessKey → StaticCredentialsProvider
//   AssumeRole:          secret has roleArn → StsAssumeRoleCredentialsProvider (temp creds,
//                        auto-refresh); optional roleSessionName and externalId fields
//   Default chain:       no credentialRef → SDK default (env vars, EC2/ECS role, ~/.aws)
//
// Activated by conductor.integrations.ai.enabled=true; an unconfigured runtime fails only if used.
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
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

        String[] agentCoords = resolveAgentCoords(request);
        String agentId = agentCoords[0];
        String agentAliasId = agentCoords[1];
        String region =
                agentCoords.length > 2 && StringUtils.isNotBlank(agentCoords[2])
                        ? agentCoords[2]
                        : StringUtils.defaultIfBlank(rawConfig(request, "region"), DEFAULT_REGION);

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

    /**
     * Discover agents from a configured AWS Bedrock region. Uses {@code bedrock-agent:ListAgents}
     * and returns one {@link org.conductoross.conductor.common.metadata.agent.AgentSummary} per
     * agent. Auth follows the same pattern as {@code buildRuntimeClient}: static credentials →
     * AssumeRole → default chain (env vars, instance role, ~/.aws/credentials).
     *
     * <p>Returns an empty list on auth or API failure so a misconfigured entry doesn't break the
     * whole agent listing.
     */
    /**
     * Discover agents from an AWS Bedrock region. Auth follows the same pattern as {@code
     * buildRuntimeClient}: static credentials → AssumeRole → default chain. The region and
     * credentialRef are read from a secret that has a {@code region} field — no separate
     * application.properties config needed.
     *
     * @param credentialRef name of the secret in Conductor's store (may be null/blank for default
     *     AWS credential chain)
     * @param region AWS region to query
     */
    public List<org.conductoross.conductor.common.metadata.agent.AgentSummary> listExternalAgents(
            String credentialRef, String region) {
        region = StringUtils.defaultIfBlank(region, "us-east-1");
        AwsCredentialsProvider credentialsProvider =
                buildCredentialsProvider(credentialRef, region);
        try (software.amazon.awssdk.services.bedrockagent.BedrockAgentClient mgmtClient =
                software.amazon.awssdk.services.bedrockagent.BedrockAgentClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(credentialsProvider)
                        .build()) {

            List<org.conductoross.conductor.common.metadata.agent.AgentSummary> result =
                    new ArrayList<>();
            String nextToken = null;
            do {
                software.amazon.awssdk.services.bedrockagent.model.ListAgentsRequest.Builder req =
                        software.amazon.awssdk.services.bedrockagent.model.ListAgentsRequest
                                .builder()
                                .maxResults(100);
                if (nextToken != null) req.nextToken(nextToken);
                software.amazon.awssdk.services.bedrockagent.model.ListAgentsResponse response =
                        mgmtClient.listAgents(req.build());

                for (software.amazon.awssdk.services.bedrockagent.model.AgentSummary agent :
                        response.agentSummaries()) {
                    result.add(
                            org.conductoross.conductor.common.metadata.agent.AgentSummary.builder()
                                    .name(agent.agentName())
                                    .version(1)
                                    .type("bedrock")
                                    .description(agent.description())
                                    .updateTime(
                                            agent.updatedAt() != null
                                                    ? agent.updatedAt().toEpochMilli()
                                                    : 0L)
                                    .build());
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

            log.debug("Discovered {} Bedrock agents in region {}", result.size(), region);
            return result;
        } catch (Exception e) {
            log.warn("Failed to list Bedrock agents in region {}: {}", region, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch the raw definition for a named Bedrock agent. Lists agents to find the ID, then calls
     * GetAgent for full details. Returns null if not found.
     */
    public Map<String, Object> getExternalAgentDef(
            String agentName, String credentialRef, String region) {
        region = StringUtils.defaultIfBlank(region, "us-east-1");
        AwsCredentialsProvider credentialsProvider =
                buildCredentialsProvider(credentialRef, region);
        try (software.amazon.awssdk.services.bedrockagent.BedrockAgentClient mgmtClient =
                software.amazon.awssdk.services.bedrockagent.BedrockAgentClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(credentialsProvider)
                        .build()) {
            // Find the agent ID by name
            String agentId = null;
            String nextToken = null;
            outer:
            do {
                software.amazon.awssdk.services.bedrockagent.model.ListAgentsRequest.Builder req =
                        software.amazon.awssdk.services.bedrockagent.model.ListAgentsRequest
                                .builder()
                                .maxResults(100);
                if (nextToken != null) req.nextToken(nextToken);
                software.amazon.awssdk.services.bedrockagent.model.ListAgentsResponse response =
                        mgmtClient.listAgents(req.build());
                for (software.amazon.awssdk.services.bedrockagent.model.AgentSummary agent :
                        response.agentSummaries()) {
                    if (agentName.equals(agent.agentName())) {
                        agentId = agent.agentId();
                        break outer;
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

            if (agentId == null) return null;

            software.amazon.awssdk.services.bedrockagent.model.GetAgentResponse agentResponse =
                    mgmtClient.getAgent(
                            software.amazon.awssdk.services.bedrockagent.model.GetAgentRequest
                                    .builder()
                                    .agentId(agentId)
                                    .build());
            software.amazon.awssdk.services.bedrockagent.model.Agent agent = agentResponse.agent();

            Map<String, Object> def = new java.util.LinkedHashMap<>();
            def.put("agentId", agent.agentId());
            def.put("agentName", agent.agentName());
            def.put("description", agent.description());
            def.put("instruction", agent.instruction());
            def.put("foundationModel", agent.foundationModel());
            def.put(
                    "agentStatus",
                    agent.agentStatus() != null ? agent.agentStatus().toString() : null);
            def.put("agentArn", agent.agentArn());
            def.put("idleSessionTTLInSeconds", agent.idleSessionTTLInSeconds());
            def.put(
                    "createdAt",
                    agent.createdAt() != null ? agent.createdAt().toEpochMilli() : null);
            def.put(
                    "updatedAt",
                    agent.updatedAt() != null ? agent.updatedAt().toEpochMilli() : null);
            def.put("provider", "bedrock");
            def.put("region", region);
            return def;
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch Bedrock agent def for '{}' in region {}: {}",
                    agentName,
                    region,
                    e.getMessage());
            return null;
        }
    }

    // Resolve AWS credentials from credentialRef: static keys → AssumeRole → default chain.
    private AwsCredentialsProvider buildCredentialsProvider(String credentialRef, String region) {
        if (StringUtils.isNotBlank(credentialRef)) {
            String accessKeyId =
                    credentialResolutionService.resolve(credentialRef + ".accessKeyId");
            String secretAccessKey =
                    credentialResolutionService.resolve(credentialRef + ".secretAccessKey");
            if (StringUtils.isNotBlank(accessKeyId) && StringUtils.isNotBlank(secretAccessKey)) {
                return StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey));
            }
            String roleArn = credentialResolutionService.resolve(credentialRef + ".roleArn");
            if (StringUtils.isNotBlank(roleArn)) {
                String roleSessionName =
                        StringUtils.defaultIfBlank(
                                credentialResolutionService.resolve(
                                        credentialRef + ".roleSessionName"),
                                "conductor-bedrock-discovery");
                return software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
                        .builder()
                        .stsClient(
                                software.amazon.awssdk.services.sts.StsClient.builder()
                                        .region(Region.of(region))
                                        .build())
                        .refreshRequest(
                                software.amazon.awssdk.services.sts.model.AssumeRoleRequest
                                        .builder()
                                        .roleArn(roleArn)
                                        .roleSessionName(roleSessionName)
                                        .build())
                        .build();
            }
        }
        return DefaultCredentialsProvider.create();
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

    // Auth detection order — first match wins:
    //   1. accessKeyId + secretAccessKey → static long-lived credentials
    //   2. roleArn                       → AssumeRole (temp creds, auto-refreshed by SDK)
    //   3. fallthrough                   → SDK default chain (env vars, EC2/ECS role, ~/.aws)
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

            String roleArn = credentialResolutionService.resolve(credentialRef + ".roleArn");
            if (StringUtils.isNotBlank(roleArn)) {
                String roleSessionName =
                        StringUtils.defaultIfBlank(
                                credentialResolutionService.resolve(
                                        credentialRef + ".roleSessionName"),
                                "conductor-bedrock");
                String externalId =
                        credentialResolutionService.resolve(credentialRef + ".externalId");
                AssumeRoleRequest.Builder assumeReq =
                        AssumeRoleRequest.builder()
                                .roleArn(roleArn)
                                .roleSessionName(roleSessionName);
                if (StringUtils.isNotBlank(externalId)) {
                    assumeReq.externalId(externalId);
                }
                StsAssumeRoleCredentialsProvider provider =
                        StsAssumeRoleCredentialsProvider.builder()
                                .stsClient(StsClient.builder().region(Region.of(region)).build())
                                .refreshRequest(assumeReq.build())
                                .build();
                return BedrockAgentRuntimeAsyncClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(provider)
                        .build();
            }
        }
        // Fall back to the default credential chain (env vars, EC2/ECS role, ~/.aws/credentials)
        return BedrockAgentRuntimeAsyncClient.builder().region(Region.of(region)).build();
    }

    // Parses agentUrl (bedrock://AGENTID/ALIASID or bedrock://AGENTID/ALIASID?region=us-west-2)
    // into [agentId, aliasId] or [agentId, aliasId, region]. Throws if either ID is missing.
    private static String[] resolveAgentCoords(ConductorAgentStartRequest request) {
        String agentUrl = request.getAgentUrl();
        if (StringUtils.isBlank(agentUrl) || !agentUrl.startsWith("bedrock://")) {
            throw new IllegalArgumentException(
                    "Bedrock agentUrl must be in the form bedrock://AGENTID/ALIASID"
                            + " (optionally with ?region=<region>)");
        }
        String path = agentUrl.substring("bedrock://".length());
        String region = null;
        if (path.contains("?")) {
            String query = path.substring(path.indexOf('?') + 1);
            path = path.substring(0, path.indexOf('?'));
            for (String param : query.split("&")) {
                if (param.startsWith("region=")) {
                    region = param.substring("region=".length());
                }
            }
        }
        String[] parts = path.split("/", 2);
        String agentId = parts[0];
        if (StringUtils.isBlank(agentId)) {
            throw new IllegalArgumentException("Bedrock agentUrl is missing agentId: " + agentUrl);
        }
        if (parts.length < 2 || StringUtils.isBlank(parts[1])) {
            throw new IllegalArgumentException(
                    "Bedrock agentUrl is missing aliasId: "
                            + agentUrl
                            + " — use bedrock://AGENTID/ALIASID");
        }
        String aliasId = parts[1];
        return region != null
                ? new String[] {agentId, aliasId, region}
                : new String[] {agentId, aliasId};
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
