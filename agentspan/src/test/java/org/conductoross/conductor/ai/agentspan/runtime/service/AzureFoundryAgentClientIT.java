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

import org.conductoross.conductor.ai.agent.ConductorAgentRequest;
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

import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Integration test for {@link AzureFoundryAgentClient} against a real Azure AI Foundry resource.
 *
 * <p>Skipped automatically when CONDUCTOR_SECRET_CONDUCTOR_AZURE_SP is not set. Run manually:
 *
 * <pre>
 *   export CONDUCTOR_SECRET_CONDUCTOR_AZURE_SP='{"client_id":"...","client_secret":"...","tenant_id":"..."}'
 *   export AZURE_OBO_USER_ASSERTION=&lt;user AAD access token&gt;   # optional, for OBO test
 *   ./gradlew :conductor-agentspan:test --tests "*.AzureFoundryAgentClientIT"
 * </pre>
 *
 * Uses the classic Assistants API (asst_i0j4OpLtZuzdN4jx5hq4b5cW) on shailesh-ai-foundry.
 */
@ExtendWith(MockitoExtension.class)
class AzureFoundryAgentClientIT {

    private static final String ENDPOINT =
            "https://shailesh-ai-foundry.cognitiveservices.azure.com";
    private static final String ASSISTANT_ID = "asst_i0j4OpLtZuzdN4jx5hq4b5cW";
    private static final String AGENT_URL = ENDPOINT + "/openai/assistants/" + ASSISTANT_ID;
    private static final String CRED_REF = "CONDUCTOR_AZURE_SP";

    @Mock CredentialResolutionService credentials;

    private AzureFoundryAgentClient client;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(
                System.getenv("CONDUCTOR_SECRET_CONDUCTOR_AZURE_SP") != null,
                "Skipping — CONDUCTOR_SECRET_CONDUCTOR_AZURE_SP not set");

        String spJson = System.getenv("CONDUCTOR_SECRET_CONDUCTOR_AZURE_SP");
        // Parse client_id, client_secret, tenant_id from JSON env var
        String clientId = extractJsonField(spJson, "client_id");
        String clientSecret = extractJsonField(spJson, "client_secret");
        String tenantId = extractJsonField(spJson, "tenant_id");

        lenient().when(credentials.resolve(CRED_REF + ".client_id")).thenReturn(clientId);
        lenient().when(credentials.resolve(CRED_REF + ".client_secret")).thenReturn(clientSecret);
        lenient().when(credentials.resolve(CRED_REF + ".tenant_id")).thenReturn(tenantId);

        client = new AzureFoundryAgentClient(credentials, new OkHttpClient());
    }

    @Test
    void serviceAccount_listAndInvokeClassicAssistant() throws Exception {
        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder()
                        .credentialRef(CRED_REF)
                        .agentUrl(AGENT_URL)
                        .prompt("Say hello in exactly three words.")
                        .build();

        ConductorAgentStartResponse start = client.startAgent(request);
        assertThat(start.getExecutionId()).isNotBlank();

        ConductorAgentStatusResponse status = pollUntilComplete(start.getExecutionId());

        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(status.getOutput()).containsKey("result");
        String result = status.getOutput().get("result").toString();
        assertThat(result).isNotBlank();
        System.out.println("Azure Foundry (service account) response: " + result);
    }

    @Test
    void obo_invokeClassicAssistant() throws Exception {
        String userAssertion = System.getenv("AZURE_OBO_USER_ASSERTION");
        Assumptions.assumeTrue(
                userAssertion != null, "Skipping OBO test — AZURE_OBO_USER_ASSERTION not set");

        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder()
                        .credentialRef(CRED_REF)
                        .agentUrl(AGENT_URL)
                        .userAssertion(userAssertion)
                        .prompt("Who am I? Reply with just my name or email.")
                        .build();

        ConductorAgentStartResponse start = client.startAgent(request);
        assertThat(start.getExecutionId()).isNotBlank();

        ConductorAgentStatusResponse status = pollUntilComplete(start.getExecutionId());

        assertThat(status.getStatus()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(status.getOutput()).containsKey("result");
        System.out.println("Azure Foundry (OBO) response: " + status.getOutput().get("result"));
    }

    private ConductorAgentStatusResponse pollUntilComplete(String executionId) throws Exception {
        ConductorAgentStatusResponse status = null;
        for (int i = 0; i < 60; i++) {
            status = client.getAgentStatus(executionId, pollRequest());
            if (status.isComplete()) break;
            TimeUnit.MILLISECONDS.sleep(500);
        }
        assertThat(status).isNotNull();
        return status;
    }

    // Minimal JSON field extractor — avoids pulling in a JSON library dependency in tests.
    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        String search = "\"" + field + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colon = json.indexOf(':', keyIdx + search.length());
        if (colon < 0) return null;
        int valueStart = json.indexOf('"', colon + 1);
        if (valueStart < 0) return null;
        int valueEnd = json.indexOf('"', valueStart + 1);
        if (valueEnd < 0) return null;
        return json.substring(valueStart + 1, valueEnd);
    }

    /**
     * The task input a poll carries. A stateless client rebuilds where the run lives from this, so
     * it must name the same agent and credential the start did.
     */
    private ConductorAgentRequest pollRequest() {
        ConductorAgentRequest request = new ConductorAgentRequest();
        request.setCredentialRef(CRED_REF);
        request.setAgentUrl(AGENT_URL);
        return request;
    }
}
