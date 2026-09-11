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

import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link BedrockAgentClient}.
 *
 * <p>Skipped automatically when AWS_ACCESS_KEY_ID is not set. Run manually with real creds:
 *
 * <pre>
 *   export AWS_ACCESS_KEY_ID=...
 *   export AWS_SECRET_ACCESS_KEY=...
 *   export AWS_SESSION_TOKEN=...       # if using temp creds
 *   ./gradlew :conductor-agentspan:test --tests "*.BedrockAgentClientIT"
 * </pre>
 *
 * Uses shailesh-test-agent (KZGTZ8AKK2 / TSTALIASID) in us-east-1.
 */
@ExtendWith(MockitoExtension.class)
class BedrockAgentClientIT {

    private static final String AGENT_ID = "KZGTZ8AKK2";
    private static final String ALIAS_ID = "TSTALIASID";
    private static final String REGION = "us-east-1";
    private static final String AGENT_URL =
            "bedrock://" + AGENT_ID + "/" + ALIAS_ID + "?region=" + REGION;

    @Mock CredentialResolutionService credentials;

    private BedrockAgentClient client;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(
                System.getenv("AWS_ACCESS_KEY_ID") != null, "Skipping — AWS_ACCESS_KEY_ID not set");
        client = new BedrockAgentClient(credentials);
    }

    @Test
    void defaultChain_invokeAgentAndGetResponse() throws Exception {
        // No credentialRef → SDK default chain picks up env vars
        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder()
                        .agentUrl(AGENT_URL)
                        .prompt("What is 2 + 2? Answer in one sentence.")
                        .build();

        ConductorAgentStartResponse start = client.startAgent(request);
        assertThat(start.getExecutionId()).isNotBlank();

        // Poll until complete (Bedrock is synchronous-ish but wrapped async)
        ConductorAgentStatusResponse status = null;
        for (int i = 0; i < 30; i++) {
            status = client.getAgentStatus(start.getExecutionId());
            if (status.isComplete()) break;
            TimeUnit.MILLISECONDS.sleep(500);
        }

        assertThat(status).isNotNull();
        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(status.getOutput()).containsKey("result");
        String result = status.getOutput().get("result").toString();
        assertThat(result).isNotBlank();
        System.out.println("Bedrock response: " + result);
    }

    @Test
    void staticCredentials_invokeAgentAndGetResponse() throws Exception {
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        // Static path uses AwsBasicCredentials (no session token support) — only valid for
        // permanent IAM user keys. Skip when the env has STS session credentials.
        Assumptions.assumeTrue(
                accessKey != null && secretKey != null, "Skipping — static cred env vars not set");
        Assumptions.assumeTrue(
                System.getenv("AWS_SESSION_TOKEN") == null,
                "Skipping — env has session-token (STS) creds; static path needs permanent IAM keys");

        // credentialRef = "CRED", secret has accessKeyId + secretAccessKey
        when(credentials.resolve("CRED.accessKeyId")).thenReturn(accessKey);
        when(credentials.resolve("CRED.secretAccessKey")).thenReturn(secretKey);

        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder()
                        .credentialRef("CRED")
                        .agentUrl(AGENT_URL)
                        .prompt("Say hello in exactly 3 words.")
                        .build();

        ConductorAgentStartResponse start = client.startAgent(request);

        ConductorAgentStatusResponse status = null;
        for (int i = 0; i < 30; i++) {
            status = client.getAgentStatus(start.getExecutionId());
            if (status.isComplete()) break;
            TimeUnit.MILLISECONDS.sleep(500);
        }

        assertThat(status).isNotNull();
        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        String result = status.getOutput().get("result").toString();
        assertThat(result).isNotBlank();
        System.out.println("Bedrock (static creds) response: " + result);
    }
}
