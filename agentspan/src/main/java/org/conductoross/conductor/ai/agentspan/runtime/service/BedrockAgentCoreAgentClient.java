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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.ListAgentRuntimesRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * {@link ConductorAgentClient} backed by AWS Bedrock AgentCore Runtime (OSS edition).
 *
 * <p>Credentials arrive via the standard Conductor secret pattern — the workflow task input
 * references secrets as {@code ${workflow.secrets.NAME.field}} — and are resolved before the task
 * reaches here. Three auth modes, by first match:
 *
 * <ol>
 *   <li>{@code accessKeyId} + {@code secretAccessKey} — static IAM key pair
 *   <li>{@code roleArn} — Conductor assumes this role via STS; {@code externalId} honoured when present
 *   <li>Default AWS credential chain — instance/task role, environment vars, {@code ~/.aws/credentials}
 * </ol>
 *
 * <p>The agent runtime ARN is taken from {@code rawConfig.agentRuntimeId}; an optional qualifier
 * from {@code rawConfig.qualifier}.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class BedrockAgentCoreAgentClient implements ConductorAgentClient {

    private static final Set<String> AWS_AUTH_KEYS =
            Set.of("accessKeyId", "secretAccessKey", "roleArn", "externalId");

    private static final Logger log = LoggerFactory.getLogger(BedrockAgentCoreAgentClient.class);
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String AGENT_TYPE = "bedrock-agentcore";

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String agentRuntimeArn = rawConfig(request.getRawConfig(), "agentRuntimeId");
        if (StringUtils.isBlank(agentRuntimeArn)) {
            throw new IllegalArgumentException(
                    "bedrock-agentcore task requires rawConfig.agentRuntimeId (the runtime ARN)");
        }
        String qualifier = rawConfig(request.getRawConfig(), "qualifier");
        String region = StringUtils.defaultIfBlank(rawConfig(request.getRawConfig(), "region"), DEFAULT_REGION);
        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        AwsCredentialsProvider credentials = credentialsFor(request.getCredentials(), region);
        String result = invoke(agentRuntimeArn, qualifier, sessionId, request.getPrompt(), credentials, region);

        return ConductorAgentStartResponse.builder()
                .executionId(sessionId)
                .agentName(agentRuntimeArn)
                .requiredWorkers(Collections.emptyList())
                .state(ConductorAgentState.COMPLETED)
                .output(Map.of("result", result))
                .build();
    }

    /**
     * AgentCore is synchronous: the whole turn completes in {@code startAgent}. If this is called
     * it means the task was re-queued after the result was already recorded — report terminal completion.
     */
    @Override
    public ConductorAgentStatusResponse getAgentStatus(String executionId, ConductorAgentRequest request) {
        return ConductorAgentStatusResponse.builder()
                .executionId(executionId)
                .status(ConductorAgentState.COMPLETED)
                .complete(true)
                .build();
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        // AgentCore has no tool-call / return-control protocol; nothing to respond to.
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        log.warn(
                "Bedrock AgentCore does not support cancellation; ignoring cancel for executionId={}",
                request.getExecutionId());
    }

    // --- discovery ---

    /**
     * Lists available AgentCore runtimes visible with this credential. The secret that carries
     * these credentials must use the key {@code agentcoreRegion} (not {@code region}) so the
     * service can distinguish an AgentCore secret from a plain Bedrock secret.
     *
     * <p>Discovery is best-effort: any error returns an empty list rather than failing the whole
     * agent listing.
     */
    public List<AgentSummary> listExternalAgents(Map<String, String> credentials, String region) {
        String resolvedRegion = StringUtils.defaultIfBlank(region, DEFAULT_REGION);
        AwsCredentialsProvider credentialsProvider = credentialsFor(credentials, resolvedRegion);
        try (BedrockAgentCoreControlClient controlClient =
                BedrockAgentCoreControlClient.builder()
                        .region(Region.of(resolvedRegion))
                        .credentialsProvider(credentialsProvider)
                        .build()) {
            List<AgentSummary> agents = new ArrayList<>();
            controlClient
                    .listAgentRuntimes(ListAgentRuntimesRequest.builder().build())
                    .agentRuntimes()
                    .forEach(
                            runtime ->
                                    agents.add(
                                            AgentSummary.builder()
                                                    .name(StringUtils.defaultIfBlank(
                                                            runtime.agentRuntimeName(),
                                                            runtime.agentRuntimeId()))
                                                    .type(AGENT_TYPE)
                                                    .endpoint(runtime.agentRuntimeArn())
                                                    .description(runtime.agentRuntimeId())
                                                    .build()));
            log.debug(
                    "Discovered {} AgentCore runtime(s) in {}", agents.size(), resolvedRegion);
            return agents;
        } catch (Exception e) {
            log.warn(
                    "Failed to list AgentCore runtimes in {}: {}", resolvedRegion, e.getMessage());
            return Collections.emptyList();
        }
    }

    // --- private helpers ---

    private String invoke(
            String agentRuntimeArn,
            String qualifier,
            String sessionId,
            String prompt,
            AwsCredentialsProvider credentialsProvider,
            String region) {
        InvokeAgentRuntimeRequest.Builder reqBuilder =
                InvokeAgentRuntimeRequest.builder()
                        .agentRuntimeArn(agentRuntimeArn)
                        .runtimeSessionId(sessionId)
                        .payload(SdkBytes.fromUtf8String(prompt != null ? prompt : ""));
        if (StringUtils.isNotBlank(qualifier)) {
            reqBuilder.qualifier(qualifier);
        }
        try (BedrockAgentCoreClient client =
                        BedrockAgentCoreClient.builder()
                                .region(Region.of(region))
                                .credentialsProvider(credentialsProvider)
                                .build();
                ResponseInputStream<InvokeAgentRuntimeResponse> stream =
                        client.invokeAgentRuntime(reqBuilder.build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read Bedrock AgentCore response for session " + sessionId, e);
        }
    }

    AwsCredentialsProvider credentialsFor(Map<String, String> credentials, String region) {
        String accessKeyId = AgentCredentials.value(credentials, "accessKeyId");
        String secretAccessKey = AgentCredentials.value(credentials, "secretAccessKey");
        if (StringUtils.isNoneBlank(accessKeyId, secretAccessKey)) {
            String sessionToken = AgentCredentials.value(credentials, "sessionToken");
            if (StringUtils.isNotBlank(sessionToken)) {
                return StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken));
            }
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }

        String roleArn = AgentCredentials.value(credentials, "roleArn");
        if (StringUtils.isNotBlank(roleArn)) {
            AssumeRoleRequest.Builder assumeRole =
                    AssumeRoleRequest.builder()
                            .roleArn(roleArn)
                            .roleSessionName("conductor-agentcore");
            String externalId = AgentCredentials.value(credentials, "externalId");
            if (StringUtils.isNotBlank(externalId)) {
                assumeRole.externalId(externalId);
            }
            return StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(StsClient.builder().region(Region.of(region)).build())
                    .refreshRequest(assumeRole.build())
                    .build();
        }

        AgentCredentials.rejectPartiallyResolved(credentials, AWS_AUTH_KEYS, "AWS (bedrock-agentcore)");
        return DefaultCredentialsProvider.create();
    }

    private static String rawConfig(Map<String, Object> rawConfig, String key) {
        if (rawConfig == null) return null;
        Object value = rawConfig.get(key);
        return value != null ? value.toString() : null;
    }
}
