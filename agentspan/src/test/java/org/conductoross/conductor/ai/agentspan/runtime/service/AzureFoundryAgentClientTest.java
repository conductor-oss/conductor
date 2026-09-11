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

import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.ManagedIdentityCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the pure routing, parsing, transformation, and auth-detection logic in {@link
 * AzureFoundryAgentClient}. No Azure credentials or network calls required.
 */
@ExtendWith(MockitoExtension.class)
class AzureFoundryAgentClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock CredentialResolutionService credentials;

    private AzureFoundryAgentClient client;

    @BeforeEach
    void setUp() {
        client = new AzureFoundryAgentClient(credentials, new OkHttpClient());
    }

    // -------------------------------------------------------------------------
    // extractAgentIdFromUrl
    // -------------------------------------------------------------------------

    @Nested
    class ExtractAgentIdFromUrl {

        @Test
        void foundryProjectAgentName() {
            assertThat(
                            AzureFoundryAgentClient.extractAgentIdFromUrl(
                                    "https://res.services.ai.azure.com/api/projects/proj/agents/shailesh-analyst"))
                    .isEqualTo("shailesh-analyst");
        }

        @Test
        void classicAssistantId() {
            assertThat(
                            AzureFoundryAgentClient.extractAgentIdFromUrl(
                                    "https://res.openai.azure.com/openai/assistants/asst_abc123"))
                    .isEqualTo("asst_abc123");
        }

        @Test
        void trailingSlashStripped() {
            assertThat(
                            AzureFoundryAgentClient.extractAgentIdFromUrl(
                                    "https://res.services.ai.azure.com/api/projects/proj/agents/my-agent/"))
                    .isEqualTo("my-agent");
        }

        @Test
        void nullUrlReturnsNull() {
            assertThat(AzureFoundryAgentClient.extractAgentIdFromUrl(null)).isNull();
        }

        @Test
        void urlWithNoAgentOrAssistantMarkerReturnsNull() {
            assertThat(
                            AzureFoundryAgentClient.extractAgentIdFromUrl(
                                    "https://res.openai.azure.com/openai"))
                    .isNull();
        }

        @Test
        void lastMarkerWinsWhenBothAppearInUrl() {
            // Contrived but ensures lastIndexOf semantics: /agents/ wins over /assistants/
            assertThat(
                            AzureFoundryAgentClient.extractAgentIdFromUrl(
                                    "https://res.example.com/assistants/old/agents/final-name"))
                    .isEqualTo("final-name");
        }
    }

    // -------------------------------------------------------------------------
    // isInferenceEndpoint
    // -------------------------------------------------------------------------

    @Nested
    class IsInferenceEndpoint {

        @Test
        void mlInferenceHostIsInference() {
            assertThat(
                            AzureFoundryAgentClient.isInferenceEndpoint(
                                    "https://my-endpoint.inference.ml.azure.com/score"))
                    .isTrue();
        }

        @Test
        void foundryModelsEndpointWithoutProjectPathIsInference() {
            assertThat(
                            AzureFoundryAgentClient.isInferenceEndpoint(
                                    "https://res.services.ai.azure.com/models"))
                    .isTrue();
        }

        @Test
        void foundryProjectEndpointIsNotInference() {
            assertThat(
                            AzureFoundryAgentClient.isInferenceEndpoint(
                                    "https://res.services.ai.azure.com/api/projects/my-proj"))
                    .isFalse();
        }

        @Test
        void classicOpenAiEndpointIsNotInference() {
            assertThat(
                            AzureFoundryAgentClient.isInferenceEndpoint(
                                    "https://res.openai.azure.com/openai"))
                    .isFalse();
        }

        @Test
        void nullReturnsFalse() {
            assertThat(AzureFoundryAgentClient.isInferenceEndpoint(null)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // isFoundryProjectEndpoint
    // -------------------------------------------------------------------------

    @Nested
    class IsFoundryProjectEndpoint {

        @Test
        void servicesAiWithProjectPathIsFoundryProject() {
            assertThat(
                            AzureFoundryAgentClient.isFoundryProjectEndpoint(
                                    "https://res.services.ai.azure.com/api/projects/my-proj"))
                    .isTrue();
        }

        @Test
        void servicesAiWithoutProjectPathIsNotFoundryProject() {
            assertThat(
                            AzureFoundryAgentClient.isFoundryProjectEndpoint(
                                    "https://res.services.ai.azure.com/models"))
                    .isFalse();
        }

        @Test
        void classicOpenAiEndpointIsNotFoundryProject() {
            assertThat(
                            AzureFoundryAgentClient.isFoundryProjectEndpoint(
                                    "https://res.openai.azure.com/openai"))
                    .isFalse();
        }

        @Test
        void mlInferenceEndpointIsNotFoundryProject() {
            assertThat(
                            AzureFoundryAgentClient.isFoundryProjectEndpoint(
                                    "https://my.inference.ml.azure.com/score"))
                    .isFalse();
        }

        @Test
        void nullReturnsFalse() {
            assertThat(AzureFoundryAgentClient.isFoundryProjectEndpoint(null)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Three-way routing is mutually exclusive
    // -------------------------------------------------------------------------

    @Nested
    class RoutingMutualExclusion {

        @Test
        void inferenceAndFoundryProjectNeverBothTrue() {
            // Every URL must route to exactly one of the three paths
            String[] urls = {
                "https://res.inference.ml.azure.com/score",
                "https://res.services.ai.azure.com/models",
                "https://res.services.ai.azure.com/api/projects/proj",
                "https://res.openai.azure.com/openai"
            };
            for (String url : urls) {
                boolean inf = AzureFoundryAgentClient.isInferenceEndpoint(url);
                boolean foundry = AzureFoundryAgentClient.isFoundryProjectEndpoint(url);
                assertThat(inf && foundry)
                        .as("URL should not match both inference and foundry-project: " + url)
                        .isFalse();
            }
        }
    }

    // -------------------------------------------------------------------------
    // toResponsesApiTools
    // -------------------------------------------------------------------------

    @Nested
    class ToResponsesApiTools {

        @Test
        void codeInterpreterGetsContainerInjected() {
            ArrayNode tools = MAPPER.createArrayNode();
            tools.addObject().put("type", "code_interpreter");

            JsonNode result = AzureFoundryAgentClient.toResponsesApiTools(tools);

            assertThat(result.size()).isEqualTo(1);
            JsonNode ci = result.get(0);
            assertThat(ci.path("type").asText()).isEqualTo("code_interpreter");
            assertThat(ci.path("container").path("type").asText()).isEqualTo("auto");
        }

        @Test
        void webSearchPassesThroughUnchanged() {
            ArrayNode tools = MAPPER.createArrayNode();
            tools.addObject().put("type", "web_search");

            JsonNode result = AzureFoundryAgentClient.toResponsesApiTools(tools);

            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).path("type").asText()).isEqualTo("web_search");
            assertThat(result.get(0).has("container")).isFalse();
        }

        @Test
        void fileSearchWithVectorStoreIdsPassesThroughUnchanged() {
            ArrayNode tools = MAPPER.createArrayNode();
            ObjectNode fs = tools.addObject();
            fs.put("type", "file_search");
            fs.putArray("vector_store_ids").add("vs_abc123");

            JsonNode result = AzureFoundryAgentClient.toResponsesApiTools(tools);

            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).path("type").asText()).isEqualTo("file_search");
            assertThat(result.get(0).path("vector_store_ids").get(0).asText())
                    .isEqualTo("vs_abc123");
        }

        @Test
        void allThreeToolsMixedCorrectly() {
            ArrayNode tools = MAPPER.createArrayNode();
            tools.addObject().put("type", "web_search");
            tools.addObject().put("type", "code_interpreter");
            ObjectNode fs = tools.addObject();
            fs.put("type", "file_search");
            fs.putArray("vector_store_ids").add("vs_xyz");

            JsonNode result = AzureFoundryAgentClient.toResponsesApiTools(tools);

            assertThat(result.size()).isEqualTo(3);
            assertThat(result.get(0).has("container")).isFalse();
            assertThat(result.get(1).path("container").path("type").asText()).isEqualTo("auto");
            assertThat(result.get(2).path("type").asText()).isEqualTo("file_search");
            assertThat(result.get(2).has("container")).isFalse();
        }

        @Test
        void emptyArrayStaysEmpty() {
            JsonNode result = AzureFoundryAgentClient.toResponsesApiTools(MAPPER.createArrayNode());
            assertThat(result.size()).isEqualTo(0);
        }
    }

    // -------------------------------------------------------------------------
    // buildAuthState — mode detection (no Azure token calls made)
    // -------------------------------------------------------------------------

    // CredentialResolutionService is a multi-key lookup (apiKey, client_id, scope, …).
    // Lenient strictness avoids false "PotentialStubbingProblem" for keys we don't care about.
    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class BuildAuthState {

        private static final String ENDPOINT =
                "https://res.services.ai.azure.com/api/projects/proj";

        @Test
        void apiKeySecretProducesApiKeyMode() {
            when(credentials.resolve("CRED.apiKey")).thenReturn("my-key-value");

            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(request("CRED"), ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("api-key");
            assertThat(auth.headerValue()).isEqualTo("my-key-value");
            assertThat(auth.credential).isNull();
        }

        @Test
        void clientSecretTrioProducesClientSecretCredential() {
            when(credentials.resolve("CRED.client_id")).thenReturn("cid");
            when(credentials.resolve("CRED.client_secret")).thenReturn("csec");
            when(credentials.resolve("CRED.tenant_id")).thenReturn("tid");

            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(request("CRED"), ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("Authorization");
            assertThat(auth.credential).isInstanceOf(ClientSecretCredential.class);
        }

        @Test
        void camelCaseClientIdAloneProducesManagedIdentityCredential() {
            when(credentials.resolve("CRED.clientId")).thenReturn("uami-client-id");

            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(request("CRED"), ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("Authorization");
            assertThat(auth.credential).isInstanceOf(ManagedIdentityCredential.class);
        }

        @Test
        void noCredentialRefProducesDefaultAzureCredential() {
            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(requestNoCredRef(), ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("Authorization");
            assertThat(auth.credential).isInstanceOf(DefaultAzureCredential.class);
        }

        @Test
        void apiKeyTakesPrecedenceOverClientSecret() {
            when(credentials.resolve("CRED.apiKey")).thenReturn("key-wins");
            when(credentials.resolve("CRED.client_id")).thenReturn("should-be-ignored");

            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(request("CRED"), ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("api-key");
        }

        @Test
        void incompleteClientSecretFallsThroughToDefaultAzureCredential() {
            // Only client_id present — no secret or tenant → not a valid SP → falls to Default
            when(credentials.resolve("CRED.client_id")).thenReturn("cid");

            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(request("CRED"), ENDPOINT);

            assertThat(auth.credential).isInstanceOf(DefaultAzureCredential.class);
        }

        // --- scope auto-detection ---

        @Test
        void mlInferenceEndpointGetsMlScope() {
            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(
                            requestNoCredRef(), "https://res.inference.ml.azure.com/score");

            assertThat(auth.scope).isEqualTo("https://ml.azure.com/.default");
        }

        @Test
        void foundryProjectEndpointGetsAiScope() {
            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(requestNoCredRef(), ENDPOINT);

            assertThat(auth.scope).isEqualTo("https://ai.azure.com/.default");
        }

        @Test
        void classicOpenAiEndpointGetsCognitiveServicesScope() {
            AzureFoundryAgentClient.AuthState auth =
                    client.buildAuthState(
                            requestNoCredRef(), "https://res.openai.azure.com/openai");

            assertThat(auth.scope).isEqualTo("https://cognitiveservices.azure.com/.default");
        }
    }

    // -------------------------------------------------------------------------
    // resolveEndpoint — fallback priority and SSRF guard
    // -------------------------------------------------------------------------

    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class ResolveEndpoint {

        @Test
        void rawConfigEndpoint_usedWhenAgentUrlBlank() {
            // agentUrl is blank → should fall back to rawConfig.endpoint
            ConductorAgentStartRequest req =
                    ConductorAgentStartRequest.builder()
                            .rawConfig(Map.of("endpoint", "https://res.openai.azure.com/openai"))
                            .prompt("hi")
                            .build();

            String resolved = client.resolveEndpoint(req);

            assertThat(resolved).isEqualTo("https://res.openai.azure.com/openai");
        }

        @Test
        void agentUrl_takesPreferenceOverRawConfig() {
            ConductorAgentStartRequest req =
                    ConductorAgentStartRequest.builder()
                            .agentUrl("https://wins.openai.azure.com/openai")
                            .rawConfig(Map.of("endpoint", "https://loses.openai.azure.com/openai"))
                            .prompt("hi")
                            .build();

            String resolved = client.resolveEndpoint(req);

            assertThat(resolved).isEqualTo("https://wins.openai.azure.com/openai");
        }

        @Test
        void validateAzureHost_rejectsNonAzureHost() {
            ConductorAgentStartRequest req =
                    ConductorAgentStartRequest.builder()
                            .agentUrl("https://attacker.example.com/steal")
                            .prompt("hi")
                            .build();

            assertThatThrownBy(() -> client.resolveEndpoint(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not an allowed Azure domain");
        }

        @Test
        void validateAzureHost_acceptsKnownAzureDomains() {
            String[] valid = {
                "https://res.openai.azure.com/openai",
                "https://res.cognitiveservices.azure.com/",
                "https://res.services.ai.azure.com/api/projects/p/agents/a",
                "https://ep.inference.ml.azure.com/score"
            };
            for (String url : valid) {
                ConductorAgentStartRequest req =
                        ConductorAgentStartRequest.builder().agentUrl(url).prompt("hi").build();
                // should not throw
                assertThat(client.resolveEndpoint(req)).isNotBlank();
            }
        }
    }

    // -------------------------------------------------------------------------
    // OBO identity passthrough — buildAuthState with useCallerIdentity
    // -------------------------------------------------------------------------

    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class OboAuthTest {

        private static final String ENDPOINT =
                "https://res.services.ai.azure.com/api/projects/proj";

        // useCallerIdentity=true + callerEntraToken + complete SP creds → OBO bearer
        @Test
        void buildAuthState_usesOboBearer_whenCallerTokenAndCredentialsPresent() {
            when(credentials.resolve("SP.tenant_id")).thenReturn("tid");
            when(credentials.resolve("SP.client_id")).thenReturn("cid");
            when(credentials.resolve("SP.client_secret")).thenReturn("csec");

            AzureFoundryAgentClient spy = spy(client);
            doReturn("foundry-token-abc")
                    .when(spy)
                    .exchangeOboToken(anyString(), eq("tid"), eq("cid"), eq("csec"), anyString());

            ConductorAgentStartRequest req =
                    ConductorAgentStartRequest.builder()
                            .credentialRef("SP")
                            .agentUrl(
                                    "https://res.services.ai.azure.com/api/projects/proj/agents/test")
                            .prompt("hi")
                            .useCallerIdentity(true)
                            .userAssertion("sso-token-xyz")
                            .build();

            AzureFoundryAgentClient.AuthState auth = spy.buildAuthState(req, ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("Authorization");
            assertThat(auth.headerValue()).isEqualTo("Bearer foundry-token-abc");
            assertThat(auth.credential).isNull();
        }

        // useCallerIdentity=true but callerEntraToken absent → falls back to SP credential
        @Test
        void buildAuthState_fallsBackToCredentials_whenCallerTokenMissing() {
            when(credentials.resolve("SP.client_id")).thenReturn("cid");
            when(credentials.resolve("SP.client_secret")).thenReturn("csec");
            when(credentials.resolve("SP.tenant_id")).thenReturn("tid");

            ConductorAgentStartRequest req =
                    ConductorAgentStartRequest.builder()
                            .credentialRef("SP")
                            .agentUrl(
                                    "https://res.services.ai.azure.com/api/projects/proj/agents/test")
                            .prompt("hi")
                            .useCallerIdentity(true)
                            // callerEntraToken absent — OBO is skipped
                            .build();

            AzureFoundryAgentClient.AuthState auth = client.buildAuthState(req, ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("Authorization");
            assertThat(auth.credential).isInstanceOf(ClientSecretCredential.class);
        }

        // useCallerIdentity=false → OBO path never entered, API key wins
        @Test
        void buildAuthState_ignoredWhenUseCallerIdentityFalse() {
            when(credentials.resolve("SP.apiKey")).thenReturn("my-api-key");

            ConductorAgentStartRequest req =
                    ConductorAgentStartRequest.builder()
                            .credentialRef("SP")
                            .agentUrl(
                                    "https://res.services.ai.azure.com/api/projects/proj/agents/test")
                            .prompt("hi")
                            .useCallerIdentity(false)
                            .userAssertion("sso-token-xyz")
                            .build();

            AzureFoundryAgentClient.AuthState auth = client.buildAuthState(req, ENDPOINT);

            assertThat(auth.headerName()).isEqualTo("api-key");
            assertThat(auth.headerValue()).isEqualTo("my-api-key");
        }

        // useCallerIdentity=true + callerEntraToken present but SP creds incomplete →
        // DefaultAzureCredential
        @Test
        void buildAuthState_fallsBackToDefault_whenCallerIdentityRequestedButCredsIncomplete() {
            // Only partial SP creds — OBO cannot be performed
            when(credentials.resolve("SP.client_id")).thenReturn("cid");
            // tenant_id and client_secret deliberately not set

            ConductorAgentStartRequest req =
                    ConductorAgentStartRequest.builder()
                            .credentialRef("SP")
                            .agentUrl(
                                    "https://res.services.ai.azure.com/api/projects/proj/agents/test")
                            .prompt("hi")
                            .useCallerIdentity(true)
                            .userAssertion("sso-token-xyz")
                            .build();

            AzureFoundryAgentClient.AuthState auth = client.buildAuthState(req, ENDPOINT);

            assertThat(auth.credential).isInstanceOf(DefaultAzureCredential.class);
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static ConductorAgentStartRequest request(String credRef) {
        return ConductorAgentStartRequest.builder()
                .credentialRef(credRef)
                .agentUrl("https://res.services.ai.azure.com/api/projects/proj/agents/test-agent")
                .prompt("hello")
                .build();
    }

    private static ConductorAgentStartRequest requestNoCredRef() {
        return ConductorAgentStartRequest.builder()
                .agentUrl("https://res.services.ai.azure.com/api/projects/proj/agents/test-agent")
                .prompt("hello")
                .build();
    }
}
