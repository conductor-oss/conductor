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
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.ListAgentRuntimesRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.ListAgentRuntimesResponse;

/**
 * {@link ConductorAgentClient} backed by AWS Bedrock AgentCore.
 *
 * <p>Bedrock AgentCore runs custom agent containers identified by {@code agentRuntimeArn} and
 * invoked via {@code InvokeAgentRuntime}. Unlike classic Bedrock Agents, invocation is synchronous
 * — the response comes back in one streaming call, so {@code startAgent} blocks until completion
 * and always returns a terminal state.
 *
 * <p>Credentials: static key-pair via {@code credentialRef}, or the SDK default chain.
 * Agent type: {@code bedrock-agentcore}.
 *
 * <p>Activated by {@code conductor.integrations.ai.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class BedrockAgentCoreAgentClient implements ConductorAgentClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockAgentCoreAgentClient.class);
    private static final String DEFAULT_REGION = "us-east-1";

    private final CredentialResolutionService credentialResolutionService;
    private final ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();

    public BedrockAgentCoreAgentClient(CredentialResolutionService credentialResolutionService) {
        this.credentialResolutionService = credentialResolutionService;
    }

    @Override
    public String agentType() {
        return A2AService.AGENT_TYPE_BEDROCK_AGENTCORE;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        String agentRuntimeArn = rawConfig(request, "agentRuntimeId");
        if (StringUtils.isBlank(agentRuntimeArn)) {
            throw new IllegalArgumentException(
                    "Bedrock AgentCore requires agentRuntimeId (ARN) in the task rawConfig");
        }
        String region = StringUtils.defaultIfBlank(rawConfig(request, "region"), DEFAULT_REGION);
        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        String responseText = invoke(agentRuntimeArn, sessionId, request.getPrompt(), request, region);
        results.put(sessionId, responseText);

        return ConductorAgentStartResponse.builder()
                .executionId(sessionId)
                .agentName(agentRuntimeArn)
                .requiredWorkers(Collections.emptyList())
                .build();
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(String executionId) {
        String result = results.remove(executionId);
        return ConductorAgentStatusResponse.builder()
                .executionId(executionId)
                .status(ConductorAgentState.COMPLETED)
                .complete(true)
                .output(result != null ? Map.of("result", result) : Collections.emptyMap())
                .build();
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        log.warn("BedrockAgentCore does not support mid-turn responds; ignoring executionId={}",
                request.getExecutionId());
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        log.warn("BedrockAgentCore does not support cancellation; ignoring executionId={}",
                request.getExecutionId());
    }

    // --- discovery ------------------------------------------------------------------

    public List<AgentSummary> listExternalAgents(String credentialRef, String region) {
        String resolvedRegion = StringUtils.defaultIfBlank(region, DEFAULT_REGION);
        try (BedrockAgentCoreControlClient control = buildControlClient(credentialRef, resolvedRegion)) {
            List<AgentSummary> agents = new ArrayList<>();
            String nextToken = null;
            do {
                ListAgentRuntimesRequest.Builder req = ListAgentRuntimesRequest.builder().maxResults(100);
                if (nextToken != null) req.nextToken(nextToken);
                ListAgentRuntimesResponse response = control.listAgentRuntimes(req.build());
                for (var runtime : response.agentRuntimes()) {
                    agents.add(AgentSummary.builder()
                            .name(runtime.agentRuntimeName())
                            .version(1)
                            .type(A2AService.AGENT_TYPE_BEDROCK_AGENTCORE)
                            .description(runtime.description())
                            .updateTime(runtime.lastUpdatedAt() != null ? runtime.lastUpdatedAt().toEpochMilli() : 0L)
                            .build());
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
            log.debug("Discovered {} Bedrock AgentCore runtime(s) in {}", agents.size(), resolvedRegion);
            return agents;
        } catch (Exception e) {
            log.warn("Failed to list Bedrock AgentCore runtimes in {}: {}", resolvedRegion, e.getMessage());
            return Collections.emptyList();
        }
    }

    // --- invocation -----------------------------------------------------------------

    private String invoke(String agentRuntimeArn, String sessionId, String prompt,
            ConductorAgentStartRequest request, String region) {
        try (BedrockAgentCoreClient client = buildRuntimeClient(request, region);
             ResponseInputStream<InvokeAgentRuntimeResponse> stream = client.invokeAgentRuntime(
                     InvokeAgentRuntimeRequest.builder()
                             .agentRuntimeArn(agentRuntimeArn)
                             .runtimeSessionId(sessionId)
                             .payload(SdkBytes.fromUtf8String(prompt != null ? prompt : ""))
                             .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Bedrock AgentCore response for session " + sessionId, e);
        }
    }

    private BedrockAgentCoreClient buildRuntimeClient(ConductorAgentStartRequest request, String region) {
        String credentialRef = request.getCredentialRef();
        if (StringUtils.isNotBlank(credentialRef)) {
            String accessKeyId = credentialResolutionService.resolve(credentialRef + ".accessKeyId");
            String secretAccessKey = credentialResolutionService.resolve(credentialRef + ".secretAccessKey");
            if (StringUtils.isNoneBlank(accessKeyId, secretAccessKey)) {
                return BedrockAgentCoreClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                        .build();
            }
        }
        return BedrockAgentCoreClient.builder().region(Region.of(region)).build();
    }

    private BedrockAgentCoreControlClient buildControlClient(String credentialRef, String region) {
        if (StringUtils.isNotBlank(credentialRef)) {
            String accessKeyId = credentialResolutionService.resolve(credentialRef + ".accessKeyId");
            String secretAccessKey = credentialResolutionService.resolve(credentialRef + ".secretAccessKey");
            if (StringUtils.isNoneBlank(accessKeyId, secretAccessKey)) {
                return BedrockAgentCoreControlClient.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                        .build();
            }
        }
        return BedrockAgentCoreControlClient.builder().region(Region.of(region)).build();
    }

    private static String rawConfig(ConductorAgentStartRequest request, String key) {
        if (request.getRawConfig() == null) return null;
        Object value = request.getRawConfig().get(key);
        return value != null ? value.toString() : null;
    }
}
