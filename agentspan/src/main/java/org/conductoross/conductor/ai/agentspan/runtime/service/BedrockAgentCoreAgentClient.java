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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.ListAgentRuntimesRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.ListAgentRuntimesResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * {@link ConductorAgentClient} backed by AWS Bedrock AgentCore.
 *
 * <p>Bedrock AgentCore is distinct from classic Bedrock Agents: it runs agent runtimes (custom
 * containers) invoked via {@code InvokeAgentRuntime}, identified by {@code agentRuntimeId} rather
 * than {@code agentId + agentAliasId}. This client routes tasks whose {@code agentType} is {@code
 * bedrock-agentcore} to the AgentCore runtime.
 *
 * <p>Invocation is synchronous — the response comes back in one call — so {@code startAgent} blocks
 * until the runtime finishes and always returns a terminal
 * ({@link ConductorAgentState#COMPLETED}) state. The runtime's response payload is returned as the
 * {@code result} key in the task output.
 *
 * <p>Credentials are resolved in order: static key-pair, IAM role assumption, then the SDK default
 * chain. The {@code agentRuntimeId} may be supplied via {@code rawConfig.agentRuntimeId} or as an
 * {@code agentUrl} with the scheme {@code bedrock-agentcore://RUNTIME_ID}.
 *
 * <p>Activated by {@code conductor.integrations.ai.enabled=true}, like the other agent clients.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class BedrockAgentCoreAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockAgentCoreAgentClient.class);
    private static final String DEFAULT_REGION = "us-east-1";

    private static final Set<String> AWS_AUTH_KEYS =
            Set.of(
                    "apiKey",
                    "api_key",
                    "accessKeyId",
                    "secretAccessKey",
                    "roleArn",
                    "roleSessionName",
                    "externalId");

    // One SDK client per (region, credentials) — building one per invocation leaks Netty pools.
    private final ConcurrentHashMap<ClientKey, BedrockAgentCoreClient> runtimeClients =
            new ConcurrentHashMap<>();

    public BedrockAgentCoreAgentClient() {}

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_BEDROCK_AGENTCORE;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String agentRuntimeId = resolveAgentRuntimeId(request.getAgentUrl(), request.getRawConfig());
        String region = StringUtils.defaultIfBlank(
                rawConfig(request.getRawConfig(), "region"), DEFAULT_REGION);
        String sessionId =
                StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        String responseText =
                invoke(agentRuntimeId, sessionId, request.getPrompt(), request.getCredentials(), region);

        return ConductorAgentStartResponse.builder()
                .executionId(sessionId)
                .agentName(agentRuntimeId)
                .requiredWorkers(Collections.emptyList())
                .state(ConductorAgentState.COMPLETED)
                .output(Map.of("result", responseText))
                .build();
    }

    /**
     * AgentCore is synchronous — by the time {@code startAgent} returns the turn is complete.
     * Nothing to poll.
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
        log.warn(
                "BedrockAgentCore does not support mid-turn responds; ignoring for executionId={}",
                request.getExecutionId());
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        log.warn(
                "BedrockAgentCore does not support cancellation; ignoring for executionId={}",
                request.getExecutionId());
    }

    @Override
    public void close() {
        runtimeClients.values().forEach(BedrockAgentCoreClient::close);
        runtimeClients.clear();
    }

    // --- discovery ------------------------------------------------------------------

    /**
     * Lists all agent runtimes visible with the given credentials. Best-effort: a credential that
     * cannot list returns nothing rather than failing the whole agent listing.
     */
    public List<AgentSummary> listExternalAgents(Map<String, String> credentials, String region) {
        String resolvedRegion = StringUtils.defaultIfBlank(region, DEFAULT_REGION);
        try (BedrockAgentCoreControlClient control = controlClient(credentials, resolvedRegion)) {
            List<AgentSummary> agents = new ArrayList<>();
            String nextToken = null;
            do {
                ListAgentRuntimesRequest.Builder req =
                        ListAgentRuntimesRequest.builder().maxResults(100);
                if (nextToken != null) {
                    req.nextToken(nextToken);
                }
                ListAgentRuntimesResponse response = control.listAgentRuntimes(req.build());
                for (var runtime : response.agentRuntimes()) {
                    agents.add(
                            AgentSummary.builder()
                                    .name(runtime.agentRuntimeName())
                                    .version(1)
                                    .type(A2AService.AGENT_TYPE_BEDROCK_AGENTCORE)
                                    .description(runtime.description())
                                    .updateTime(
                                            runtime.lastUpdatedAt() != null
                                                    ? runtime.lastUpdatedAt().toEpochMilli()
                                                    : 0L)
                                    .build());
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
            log.debug(
                    "Discovered {} Bedrock AgentCore runtime(s) in {}",
                    agents.size(),
                    resolvedRegion);
            return agents;
        } catch (Exception e) {
            log.warn(
                    "Failed to list Bedrock AgentCore runtimes in {}: {}",
                    resolvedRegion,
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    // --- invocation -----------------------------------------------------------------

    private String invoke(
            String agentRuntimeId,
            String sessionId,
            String prompt,
            Map<String, String> credentials,
            String region) {
        BedrockAgentCoreClient client =
                runtimeClients.computeIfAbsent(new ClientKey(region, credentials), this::buildClient);
        String payload = prompt != null ? prompt : "";
        InvokeAgentRuntimeRequest req =
                InvokeAgentRuntimeRequest.builder()
                        .agentRuntimeId(agentRuntimeId)
                        .sessionId(sessionId)
                        .payload(SdkBytes.fromUtf8String(payload))
                        .build();
        InvokeAgentRuntimeResponse response = client.invokeAgentRuntime(req);
        SdkBytes body = response.payload();
        return body != null ? body.asUtf8String() : "";
    }

    // --- helpers --------------------------------------------------------------------

    private String resolveAgentRuntimeId(String agentUrl, Map<String, Object> rawConfig) {
        String fromConfig = rawConfig(rawConfig, "agentRuntimeId");
        if (StringUtils.isNotBlank(fromConfig)) {
            return fromConfig;
        }
        if (StringUtils.isNotBlank(agentUrl)) {
            return runtimeIdFromUrl(agentUrl);
        }
        throw new IllegalArgumentException(
                "Bedrock AgentCore requires agentRuntimeId in rawConfig or as"
                        + " agentUrl bedrock-agentcore://RUNTIME_ID");
    }

    static String runtimeIdFromUrl(String agentUrl) {
        String prefix = "bedrock-agentcore://";
        if (!StringUtils.startsWith(agentUrl, prefix)) {
            throw new IllegalArgumentException(
                    "A Bedrock AgentCore agentUrl must start with bedrock-agentcore://, got: "
                            + agentUrl);
        }
        String runtimeId = agentUrl.substring(prefix.length());
        int query = runtimeId.indexOf('?');
        if (query >= 0) {
            runtimeId = runtimeId.substring(0, query);
        }
        if (StringUtils.isBlank(runtimeId)) {
            throw new IllegalArgumentException(
                    "Bedrock AgentCore agentUrl names no runtime: " + agentUrl);
        }
        return runtimeId;
    }

    private BedrockAgentCoreClient buildClient(ClientKey key) {
        return BedrockAgentCoreClient.builder()
                .region(Region.of(key.region()))
                .credentialsProvider(credentialsFor(key.credentials(), key.region()))
                .build();
    }

    private BedrockAgentCoreControlClient controlClient(
            Map<String, String> credentials, String region) {
        return BedrockAgentCoreControlClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsFor(credentials, region))
                .build();
    }

    AwsCredentialsProvider credentialsFor(Map<String, String> credentials, String region) {
        String accessKeyId = AgentCredentials.value(credentials, "accessKeyId");
        String secretAccessKey = AgentCredentials.value(credentials, "secretAccessKey");
        if (StringUtils.isNoneBlank(accessKeyId, secretAccessKey)) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        String roleArn = AgentCredentials.value(credentials, "roleArn");
        if (StringUtils.isNotBlank(roleArn)) {
            AssumeRoleRequest.Builder assumeRole =
                    AssumeRoleRequest.builder()
                            .roleArn(roleArn)
                            .roleSessionName(
                                    StringUtils.defaultIfBlank(
                                            AgentCredentials.value(credentials, "roleSessionName"),
                                            "conductor-bedrock-agentcore"));
            String externalId = AgentCredentials.value(credentials, "externalId");
            if (StringUtils.isNotBlank(externalId)) {
                assumeRole.externalId(externalId);
            }
            return StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(StsClient.builder().region(Region.of(region)).build())
                    .refreshRequest(assumeRole.build())
                    .build();
        }
        AgentCredentials.rejectPartiallyResolved(credentials, AWS_AUTH_KEYS, "AWS");
        return DefaultCredentialsProvider.create();
    }

    private static String rawConfig(Map<String, Object> rawConfig, String key) {
        if (rawConfig == null) return null;
        Object value = rawConfig.get(key);
        return value != null ? value.toString() : null;
    }

    private record ClientKey(String region, Map<String, String> credentials) {}
}
