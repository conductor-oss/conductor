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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
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
import software.amazon.awssdk.services.bedrockagentruntime.model.InvocationInputMember;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.ReturnControlPayload;
import software.amazon.awssdk.services.bedrockagentruntime.model.SessionState;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * {@link ConductorAgentClient} backed by AWS Bedrock Agent Runtime.
 *
 * <p>Bedrock has no status API. {@code InvokeAgent} streams the whole turn, so by the time {@code
 * startAgent} or {@code respond} returns, the agent has either finished or blocked on a tool call.
 * Both therefore report the outcome directly — {@code startAgent} through {@link
 * ConductorAgentStartResponse#getState()}, {@code respond} through {@link
 * #respondWithStatus(ConductorAgentRespondRequest)} — which puts the result in the owning task's
 * output. Nothing is buffered here, so there is no poll for a later replica to get wrong and no
 * result to lose on restart.
 *
 * <p>The conversation itself lives in Bedrock, keyed by the session id this client returns as the
 * executionId. Continuing it needs only that id plus the task input, so {@code respond} works on
 * any replica.
 *
 * <p>Activated by {@code conductor.integrations.ai.enabled=true}, like the other agent clients.
 * Credentials are resolved per request from {@code credentialRef}, falling back to the default AWS
 * credential chain, so the client registers whether or not Bedrock is configured; an unconfigured
 * runtime fails only if a workflow routes to it.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class BedrockAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockAgentClient.class);
    private static final String DEFAULT_REGION = "us-east-1";

    private final CredentialResolutionService credentialResolutionService;

    // One SDK client per credential and region, not per execution. Each one owns Netty event loops
    // and a connection pool, so building one per invocation leaked both — the map used to hold
    // every
    // execution ever started, and nothing removed a completed one.
    private final ConcurrentHashMap<ClientKey, BedrockAgentRuntimeAsyncClient> runtimeClients =
            new ConcurrentHashMap<>();

    public BedrockAgentClient(CredentialResolutionService credentialResolutionService) {
        this.credentialResolutionService = credentialResolutionService;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_BEDROCK;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        BedrockTarget target =
                target(request.getCredentialRef(), request.getAgentUrl(), request.getRawConfig());
        String sessionId =
                StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        InvokeAgentRequest invokeRequest =
                InvokeAgentRequest.builder()
                        .agentId(target.agentId())
                        .agentAliasId(target.agentAliasId())
                        .sessionId(sessionId)
                        .inputText(request.getPrompt())
                        .build();

        Turn turn = invoke(target, invokeRequest);

        return ConductorAgentStartResponse.builder()
                .executionId(sessionId)
                .agentName(target.agentId())
                .requiredWorkers(Collections.emptyList())
                .state(turn.state())
                .output(turn.output())
                .pendingTool(turn.pendingTool())
                .pendingTools(turn.pendingTools())
                .pendingToolName(turn.pendingToolName())
                .build();
    }

    /**
     * Bedrock cannot be polled — a turn is over before the call that started it returns, and its
     * result is already in the task output. Reaching here means the task was requeued after the
     * result was recorded, so the honest answer is the terminal state that no longer needs polling.
     */
    @Override
    public ConductorAgentStatusResponse getAgentStatus(
            String executionId, ConductorAgentRequest request) {
        return ConductorAgentStatusResponse.builder()
                .executionId(executionId)
                .status(ConductorAgentState.COMPLETED)
                .complete(true)
                .build();
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        respondWithStatus(request);
    }

    @Override
    public ConductorAgentStatusResponse respondWithStatus(ConductorAgentRespondRequest request) {
        String sessionId = request.getExecutionId();
        BedrockTarget target = target(request.getCredentialRef(), request.getRawConfig());

        // The action group to answer comes from the pending tool the last turn reported, carried on
        // the request rather than remembered here.
        String actionGroup = pendingToolName(request);
        if (StringUtils.isBlank(actionGroup)) {
            throw new IllegalArgumentException(
                    "Bedrock respond requires the pending tool's name; none was carried on the request");
        }

        ContentBody contentBody = ContentBody.builder().body(AgentBodies.toJson(request)).build();
        SessionState sessionState =
                SessionState.builder()
                        .returnControlInvocationResults(
                                rb ->
                                        rb.apiResult(
                                                ar ->
                                                        ar.actionGroup(actionGroup)
                                                                .apiPath("/invoke")
                                                                .httpMethod("POST")
                                                                .responseBody(
                                                                        Map.of(
                                                                                "application/json",
                                                                                contentBody))))
                        .build();

        InvokeAgentRequest invokeRequest =
                InvokeAgentRequest.builder()
                        .agentId(target.agentId())
                        .agentAliasId(target.agentAliasId())
                        .sessionId(sessionId)
                        .sessionState(sessionState)
                        .build();

        Turn turn = invoke(target, invokeRequest);
        return ConductorAgentStatusResponse.builder()
                .executionId(sessionId)
                .status(turn.state())
                .complete(turn.state() == ConductorAgentState.COMPLETED)
                .waiting(turn.state() == ConductorAgentState.WAITING)
                .output(turn.output())
                .pendingTool(turn.pendingTool())
                .pendingTools(turn.pendingTools())
                .pendingToolName(turn.pendingToolName())
                .build();
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        // Bedrock Agent Runtime has no cancel API, and this client holds nothing to clean up.
        log.warn(
                "Bedrock agent runtime does not support cancellation; ignoring cancel for executionId={}",
                request.getExecutionId());
    }

    @Override
    public void close() {
        runtimeClients.values().forEach(BedrockAgentRuntimeAsyncClient::close);
        runtimeClients.clear();
    }

    // --- discovery ----------------------------------------------------------------------------

    /**
     * The Bedrock agents visible with this credential, so they appear in the agent list alongside
     * agents defined in Conductor. Discovery is best effort: a credential that cannot list returns
     * nothing rather than failing the whole listing.
     */
    public List<AgentSummary> listExternalAgents(String credentialRef, String region) {
        String resolvedRegion = StringUtils.defaultIfBlank(region, DEFAULT_REGION);
        try (software.amazon.awssdk.services.bedrockagent.BedrockAgentClient management =
                managementClient(credentialRef, resolvedRegion)) {
            List<AgentSummary> agents = new ArrayList<>();
            String nextToken = null;
            do {
                var request =
                        software.amazon.awssdk.services.bedrockagent.model.ListAgentsRequest
                                .builder()
                                .maxResults(100);
                if (nextToken != null) {
                    request.nextToken(nextToken);
                }
                var response = management.listAgents(request.build());
                for (var agent : response.agentSummaries()) {
                    agents.add(
                            AgentSummary.builder()
                                    .name(agent.agentName())
                                    .version(1)
                                    .type(A2AService.AGENT_TYPE_BEDROCK)
                                    .description(agent.description())
                                    .updateTime(
                                            agent.updatedAt() != null
                                                    ? agent.updatedAt().toEpochMilli()
                                                    : 0L)
                                    .build());
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
            log.debug("Discovered {} Bedrock agent(s) in {}", agents.size(), resolvedRegion);
            return agents;
        } catch (Exception e) {
            log.warn("Failed to list Bedrock agents in {}: {}", resolvedRegion, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * The full definition of one Bedrock agent by name, or null when this credential cannot see it.
     * Bedrock addresses agents by id, so the name is resolved through a listing first.
     */
    public Map<String, Object> getExternalAgentDef(
            String agentName, String credentialRef, String region) {
        String resolvedRegion = StringUtils.defaultIfBlank(region, DEFAULT_REGION);
        try (software.amazon.awssdk.services.bedrockagent.BedrockAgentClient management =
                managementClient(credentialRef, resolvedRegion)) {
            String agentId = null;
            String nextToken = null;
            while (agentId == null) {
                var request =
                        software.amazon.awssdk.services.bedrockagent.model.ListAgentsRequest
                                .builder()
                                .maxResults(100);
                if (nextToken != null) {
                    request.nextToken(nextToken);
                }
                var response = management.listAgents(request.build());
                for (var agent : response.agentSummaries()) {
                    if (agentName.equals(agent.agentName())) {
                        agentId = agent.agentId();
                        break;
                    }
                }
                nextToken = response.nextToken();
                if (nextToken == null) {
                    break;
                }
            }
            if (agentId == null) {
                return null;
            }

            var agent =
                    management
                            .getAgent(
                                    software.amazon.awssdk.services.bedrockagent.model
                                            .GetAgentRequest.builder()
                                            .agentId(agentId)
                                            .build())
                            .agent();

            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("agentId", agent.agentId());
            definition.put("agentName", agent.agentName());
            definition.put("description", agent.description());
            definition.put("instruction", agent.instruction());
            definition.put("foundationModel", agent.foundationModel());
            definition.put(
                    "agentStatus",
                    agent.agentStatus() != null ? agent.agentStatus().toString() : null);
            definition.put("agentArn", agent.agentArn());
            definition.put("idleSessionTTLInSeconds", agent.idleSessionTTLInSeconds());
            definition.put(
                    "createdAt",
                    agent.createdAt() != null ? agent.createdAt().toEpochMilli() : null);
            definition.put(
                    "updatedAt",
                    agent.updatedAt() != null ? agent.updatedAt().toEpochMilli() : null);
            definition.put("provider", A2AService.AGENT_TYPE_BEDROCK);
            definition.put("region", resolvedRegion);
            return definition;
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch Bedrock agent '{}' in {}: {}",
                    agentName,
                    resolvedRegion,
                    e.getMessage());
            return null;
        }
    }

    private software.amazon.awssdk.services.bedrockagent.BedrockAgentClient managementClient(
            String credentialRef, String region) {
        return software.amazon.awssdk.services.bedrockagent.BedrockAgentClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsFor(credentialRef, region))
                .build();
    }

    /** Runs one InvokeAgent turn to completion and reduces the stream to its outcome. */
    private Turn invoke(BedrockTarget target, InvokeAgentRequest invokeRequest) {
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

        runtimeClient(target).invokeAgent(invokeRequest, handler).join();

        ReturnControlPayload payload = returnControl.get();
        if (payload == null) {
            return new Turn(
                    ConductorAgentState.COMPLETED,
                    Map.of("result", textBuffer.toString()),
                    null,
                    List.of(),
                    null);
        }
        return toolTurn(payload);
    }

    /**
     * Reduces a return-control payload to the tools the agent is waiting on. Extracted so the
     * fan-out is testable without an AWS round trip.
     */
    static Turn toolTurn(ReturnControlPayload payload) {
        // Every input the agent handed back, not just the first: a model may ask for several
        // independent tools in one turn, and reporting one leaves the rest unrunnable.
        List<Map<String, Object>> pendingTools = new ArrayList<>();
        if (payload.invocationInputs() != null) {
            int index = 0;
            for (InvocationInputMember input : payload.invocationInputs()) {
                String toolName =
                        input.apiInvocationInput() != null
                                ? input.apiInvocationInput().actionGroup()
                                : "unknown";
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("tool_name", toolName);
                // Bedrock names no call id, so derive a stable one per position in the turn.
                entry.put("tool_call_id", toolName + "#" + index++);
                entry.put("payload", input.toString());
                pendingTools.add(entry);
            }
        }
        if (pendingTools.isEmpty()) {
            Map<String, Object> unknown = new LinkedHashMap<>();
            unknown.put("tool_name", "unknown");
            unknown.put("tool_call_id", "unknown#0");
            unknown.put("payload", payload.toString());
            pendingTools.add(unknown);
        }
        return new Turn(
                ConductorAgentState.WAITING,
                null,
                pendingTools.get(0),
                pendingTools,
                String.valueOf(pendingTools.get(0).get("tool_name")));
    }

    private BedrockAgentRuntimeAsyncClient runtimeClient(BedrockTarget target) {
        return runtimeClients.computeIfAbsent(target.clientKey(), this::buildClient);
    }

    // Visible for tests: proves one SDK client is shared per credential and region rather than
    // created per execution, which is what used to leak a Netty pool per agent invocation.
    int openRuntimeClients() {
        return runtimeClients.size();
    }

    // Visible for tests: resolves and warms the shared client the way an invoke would, without
    // performing one.
    void warmRuntimeClient(String credentialRef, Map<String, Object> rawConfig) {
        runtimeClient(target(credentialRef, rawConfig));
    }

    private BedrockAgentRuntimeAsyncClient buildClient(ClientKey key) {
        return BedrockAgentRuntimeAsyncClient.builder()
                .region(Region.of(key.region()))
                .credentialsProvider(credentialsFor(key.credentialRef(), key.region()))
                .build();
    }

    /**
     * AWS credentials for a reference, first match wins:
     *
     * <ol>
     *   <li>static {@code .accessKeyId} + {@code .secretAccessKey}
     *   <li>{@code .roleArn} — assumed via STS, the SDK refreshing the temporary credentials;
     *       {@code .roleSessionName} and {@code .externalId} are honored when present
     *   <li>the SDK default chain — environment, instance or task role, {@code ~/.aws/credentials}
     * </ol>
     */
    AwsCredentialsProvider credentialsFor(String credentialRef, String region) {
        if (StringUtils.isNotBlank(credentialRef)) {
            String accessKeyId =
                    credentialResolutionService.resolve(credentialRef + ".accessKeyId");
            String secretAccessKey =
                    credentialResolutionService.resolve(credentialRef + ".secretAccessKey");
            if (StringUtils.isNoneBlank(accessKeyId, secretAccessKey)) {
                return StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey));
            }

            String roleArn = credentialResolutionService.resolve(credentialRef + ".roleArn");
            if (StringUtils.isNotBlank(roleArn)) {
                AssumeRoleRequest.Builder assumeRole =
                        AssumeRoleRequest.builder()
                                .roleArn(roleArn)
                                .roleSessionName(
                                        StringUtils.defaultIfBlank(
                                                credentialResolutionService.resolve(
                                                        credentialRef + ".roleSessionName"),
                                                "conductor-bedrock"));
                String externalId =
                        credentialResolutionService.resolve(credentialRef + ".externalId");
                if (StringUtils.isNotBlank(externalId)) {
                    assumeRole.externalId(externalId);
                }
                return StsAssumeRoleCredentialsProvider.builder()
                        .stsClient(StsClient.builder().region(Region.of(region)).build())
                        .refreshRequest(assumeRole.build())
                        .build();
            }
        }
        return DefaultCredentialsProvider.create();
    }

    private BedrockTarget target(String credentialRef, Map<String, Object> rawConfig) {
        return target(credentialRef, null, rawConfig);
    }

    /**
     * Everything needed to reach the agent, rebuilt from the originating task input on every call.
     * The agent may be named in {@code rawConfig} or as a {@code bedrock://AGENTID/ALIASID}
     * agentUrl, so every agent type can use the same top-level field.
     */
    private BedrockTarget target(
            String credentialRef, String agentUrl, Map<String, Object> rawConfig) {
        String agentId = rawConfig(rawConfig, "agentId");
        String agentAliasId = rawConfig(rawConfig, "agentAliasId");
        String region = rawConfig(rawConfig, "region");

        if (StringUtils.isAnyBlank(agentId, agentAliasId) && StringUtils.isNotBlank(agentUrl)) {
            String[] coordinates = coordinatesFromUrl(agentUrl);
            agentId = StringUtils.defaultIfBlank(agentId, coordinates[0]);
            agentAliasId = StringUtils.defaultIfBlank(agentAliasId, coordinates[1]);
            region = StringUtils.defaultIfBlank(region, coordinates[2]);
        }

        if (StringUtils.isAnyBlank(agentId, agentAliasId)) {
            throw new IllegalArgumentException(
                    "The agent must be named, either as rawConfig.agentId and rawConfig.agentAliasId"
                            + " or as agentUrl bedrock://AGENTID/ALIASID");
        }
        return new BedrockTarget(
                agentId,
                agentAliasId,
                new ClientKey(StringUtils.defaultIfBlank(region, DEFAULT_REGION), credentialRef));
    }

    /**
     * Splits {@code bedrock://AGENTID/ALIASID} — optionally {@code ?region=} — into agent id, alias
     * id, and region. Region is null when the URL does not carry one.
     */
    static String[] coordinatesFromUrl(String agentUrl) {
        if (!StringUtils.startsWith(agentUrl, "bedrock://")) {
            throw new IllegalArgumentException(
                    "A Bedrock agentUrl must look like bedrock://AGENTID/ALIASID"
                            + " (optionally with ?region=<region>), got: "
                            + agentUrl);
        }
        String path = agentUrl.substring("bedrock://".length());
        String region = null;
        int query = path.indexOf('?');
        if (query >= 0) {
            for (String param : path.substring(query + 1).split("&")) {
                if (param.startsWith("region=")) {
                    region = StringUtils.trimToNull(param.substring("region=".length()));
                }
            }
            path = path.substring(0, query);
        }
        String[] parts = path.split("/", 2);
        if (StringUtils.isBlank(parts[0])) {
            throw new IllegalArgumentException("Bedrock agentUrl names no agent: " + agentUrl);
        }
        if (parts.length < 2 || StringUtils.isBlank(parts[1])) {
            throw new IllegalArgumentException(
                    "Bedrock agentUrl names no alias: "
                            + agentUrl
                            + " — use bedrock://AGENTID/ALIASID");
        }
        return new String[] {parts[0], parts[1], region};
    }

    private static String pendingToolName(ConductorAgentRespondRequest request) {
        if (request.getPendingTool() == null) {
            return null;
        }
        Object toolName = request.getPendingTool().get("tool_name");
        return toolName != null ? toolName.toString() : null;
    }

    private static String rawConfig(Map<String, Object> rawConfig, String key) {
        if (rawConfig == null) return null;
        Object value = rawConfig.get(key);
        return value != null ? value.toString() : null;
    }

    /** Outcome of a single InvokeAgent turn. Package-private so the reduction can be tested. */
    record Turn(
            ConductorAgentState state,
            Map<String, Object> output,
            Map<String, Object> pendingTool,
            List<Map<String, Object>> pendingTools,
            String pendingToolName) {}

    private record BedrockTarget(String agentId, String agentAliasId, ClientKey clientKey) {}

    /**
     * Identity of a shareable SDK client. Both parts are free to compute — the credential behind
     * the reference is resolved when a client is built, not to look one up.
     */
    private record ClientKey(String region, String credentialRef) {}
}
