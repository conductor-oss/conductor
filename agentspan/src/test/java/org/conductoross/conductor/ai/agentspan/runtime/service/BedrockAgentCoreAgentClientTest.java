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

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BedrockAgentCoreAgentClient}.
 *
 * <p>AgentCore is synchronous — a full-turn invoke happens inside {@code startAgent} and there is
 * no polling API. These tests cover credential resolution, config validation, and the no-op
 * terminal methods without making real AWS calls.
 */
class BedrockAgentCoreAgentClientTest {

    private BedrockAgentCoreAgentClient client;

    @BeforeEach
    void setUp() {
        client = new BedrockAgentCoreAgentClient();
    }

    @Test
    void reportsItsOwnAgentType() {
        assertThat(client.agentType()).isEqualTo("bedrock-agentcore");
    }

    // ── credentialsFor() ─────────────────────────────────────────────────

    @Test
    void staticKeysProduceBasicCredentials() {
        var provider =
                client.credentialsFor(
                        Map.of(
                                "accessKeyId", "AKIAIOSFODNN7EXAMPLE",
                                "secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"),
                        "us-east-1");

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
        AwsCredentials resolved = provider.resolveCredentials();
        assertThat(resolved).isInstanceOf(AwsBasicCredentials.class);
        assertThat(resolved.accessKeyId()).isEqualTo("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    void sessionTokenProducesSessionCredentials() {
        // Temporary STS credentials (ASIA* prefix) require a session token.
        var provider =
                client.credentialsFor(
                        Map.of(
                                "accessKeyId", "ASIAIOSFODNN7EXAMPLE",
                                "secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                                "sessionToken", "AQoDYXdzEJr..."),
                        "us-east-1");

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
        AwsCredentials resolved = provider.resolveCredentials();
        assertThat(resolved).isInstanceOf(AwsSessionCredentials.class);
        assertThat(((AwsSessionCredentials) resolved).sessionToken()).isEqualTo("AQoDYXdzEJr...");
    }

    @Test
    void emptyCredentialsMapFallsBackToDefaultChain() {
        // No accessKeyId, secretAccessKey, or roleArn → host AWS chain (instance role, env,
        // ~/.aws).
        var provider = client.credentialsFor(Map.of(), "us-east-1");

        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
    }

    // ── startAgent() ─────────────────────────────────────────────────────

    @Test
    void startAgentThrowsWhenRuntimeArnIsMissing() {
        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder()
                        .prompt("hello")
                        .credentials(Map.of("accessKeyId", "AKIA_TEST", "secretAccessKey", "s"))
                        .rawConfig(Map.of("region", "us-east-1"))
                        .build();

        assertThatThrownBy(() -> client.startAgent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentRuntimeId");
    }

    // ── getAgentStatus() ─────────────────────────────────────────────────

    @Test
    void statusIsAlwaysTerminalBecauseInvocationIsSync() {
        ConductorAgentRequest request = new ConductorAgentRequest();

        ConductorAgentStatusResponse response = client.getAgentStatus("session-1", request);

        // The whole turn completes inside startAgent; polling would never find a running call.
        assertThat(response.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(response.isComplete()).isTrue();
    }

    // ── cancelAgent() ────────────────────────────────────────────────────

    @Test
    void cancelIsANoOpBecauseAgentCoreHasNoCancelApi() {
        // Must not throw — there is no state and no cancel API to call.
        client.cancelAgent(
                ConductorAgentCancelRequest.builder()
                        .executionId("session-1")
                        .reason("parent cancelled")
                        .build());
    }

    // ── listExternalAgents() ─────────────────────────────────────────────

    @Test
    void listExternalAgentsReturnsEmptyListOnError() {
        // Using a fake region makes the SDK unable to resolve the AgentCore control-plane hostname;
        // the method must swallow the error and return empty rather than propagating.
        List<AgentSummary> agents =
                client.listExternalAgents(
                        Map.of("accessKeyId", "AKIA_TEST", "secretAccessKey", "secret"),
                        "us-fake-1");

        assertThat(agents).isEmpty();
    }
}
