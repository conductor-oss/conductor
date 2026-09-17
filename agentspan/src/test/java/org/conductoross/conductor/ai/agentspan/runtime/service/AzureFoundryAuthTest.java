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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which of Foundry's auth modes a credential selects, and what the on-behalf-of exchange sends.
 *
 * <p>Mode selection is asserted without resolving a token: the SDK-backed modes would reach Entra
 * ID for real, and what matters here is that the right mode was chosen. The API-key and
 * on-behalf-of modes need no SDK, so those are exercised end to end.
 */
class AzureFoundryAuthTest {

    private OkHttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build();
    }

    @AfterEach
    void tearDown() {
        httpClient.dispatcher().executorService().shutdown();
    }

    private AzureFoundryAuth resolve(Map<String, String> credentials) {
        return AzureFoundryAuth.resolve(
                credentials, httpClient, null, AzureFoundryAuth.DEFAULT_SCOPE);
    }

    @Test
    void anApiKeyIsSentAsAnApiKeyHeaderWithNoSdkInvolved() {
        AzureFoundryAuth auth = resolve(Map.of("apiKey", "sk-azure"));

        assertThat(auth.headerName()).isEqualTo("api-key");
        assertThat(auth.headerValue()).isEqualTo("sk-azure");
        assertThat(auth.isReusable()).isTrue();
    }

    @Test
    void anApiKeyIsAcceptedUnderEitherSpelling() {
        // Azure writes apiKey and OpenAI writes api_key; a workflow author configuring a second
        // provider should not have to discover that.
        assertThat(resolve(Map.of("apiKey", "sk-one")).headerValue()).isEqualTo("sk-one");
        assertThat(resolve(Map.of("api_key", "sk-two")).headerValue()).isEqualTo("sk-two");
    }

    @Test
    void anApiKeyWinsOverAServicePrincipalOnTheSameCredential() {
        // First match wins, and the API key needs no token exchange at all.
        assertThat(
                        resolve(
                                        Map.of(
                                                "apiKey", "sk-azure",
                                                "client_id", "cid",
                                                "client_secret", "cs",
                                                "tenant_id", "tid"))
                                .headerName())
                .isEqualTo("api-key");
    }

    @Test
    void aServicePrincipalBecomesABearerCredential() {
        AzureFoundryAuth auth =
                resolve(Map.of("client_id", "cid", "client_secret", "cs", "tenant_id", "tid"));

        assertThat(auth.headerName()).isEqualTo("Authorization");
        assertThat(auth.isReusable()).isTrue();
    }

    @Test
    void aManagedIdentityClientIdBecomesABearerCredential() {
        AzureFoundryAuth auth = resolve(Map.of("managedIdentityClientId", "mi-client"));

        assertThat(auth.headerName()).isEqualTo("Authorization");
        assertThat(auth.isReusable()).isTrue();
    }

    @Test
    void noCredentialFallsBackToTheDefaultChain() {
        // A deployment running on managed identity configures nothing at all.
        assertThat(resolve(null).headerName()).isEqualTo("Authorization");
        assertThat(resolve(Map.of()).headerName()).isEqualTo("Authorization");
    }

    @Test
    void anUnsubstitutedSecretReferenceIsRejected() {
        // Conductor does not substitute secrets for task input held in external payload storage.
        // Passing the literal on would make every lookup miss and drop us to the host's own
        // identity — the agent would run as someone else with no error at all.
        assertThatThrownBy(
                        () ->
                                resolve(
                                        Map.of(
                                                "client_id",
                                                "${workflow.secrets.AZURE_CRED.client_id}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client_id")
                .hasMessageContaining("unresolved secret reference");
    }

    @Test
    void scopeFollowsTheFoundrySurface() {
        assertThat(AzureFoundryAuth.scopeFor("https://x.inference.ml.azure.com/score"))
                .isEqualTo(AzureFoundryAuth.ML_INFERENCE_SCOPE);
        assertThat(AzureFoundryAuth.scopeFor("https://p.services.ai.azure.com/api/projects/p1"))
                .isEqualTo(AzureFoundryAuth.FOUNDRY_SCOPE);
        assertThat(AzureFoundryAuth.scopeFor("https://r.openai.azure.com/openai"))
                .isEqualTo(AzureFoundryAuth.DEFAULT_SCOPE);
    }

    @Test
    void noAgentClientCanReachTheSecretStore() throws Exception {
        // The architectural rule this change exists to enforce: Conductor resolves
        // ${workflow.secrets.*} for a task before it runs, so clients are handed values. A client
        // holding a secret store would be resolving credentials from inside task execution again.
        for (Class<?> client :
                List.of(
                        AzureFoundryAgentClient.class,
                        BedrockAgentClient.class,
                        OpenAiAssistantsAgentClient.class,
                        AzureFoundryAuth.class)) {
            assertThat(client.getDeclaredFields())
                    .as("%s must not hold a secret store", client.getSimpleName())
                    .noneMatch(
                            field ->
                                    field.getType()
                                            .getName()
                                            .endsWith("CredentialResolutionService"));
            for (var constructor : client.getDeclaredConstructors()) {
                assertThat(constructor.getParameterTypes())
                        .as("%s must not be given a secret store", client.getSimpleName())
                        .noneMatch(type -> type.getName().endsWith("CredentialResolutionService"));
            }
        }
    }

    // --- on behalf of the caller --------------------------------------------------------------

    @Test
    void aCallerAssertionIsExchangedForAFoundryScopedToken() throws Exception {
        MockWebServer entra = new MockWebServer();
        entra.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody("{\"access_token\":\"exchanged-token\"}")
                        .addHeader("Content-Type", "application/json"));
        entra.start();
        try {
            String token =
                    AzureFoundryAuth.exchangeOnBehalfOf(
                            redirectedTo(entra),
                            "callers-sso-token",
                            "tid",
                            "cid",
                            "cs",
                            AzureFoundryAuth.FOUNDRY_SCOPE);

            assertThat(token).isEqualTo("exchanged-token");

            RecordedRequest sent = entra.takeRequest();
            String body = sent.getBody().readUtf8();
            assertThat(body)
                    .contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer");
            assertThat(body).contains("requested_token_use=on_behalf_of");
            assertThat(body).contains("assertion=callers-sso-token");
        } finally {
            entra.shutdown();
        }
    }

    @Test
    void aCallersTokenIsNeverReusedAcrossRequests() throws Exception {
        MockWebServer entra = new MockWebServer();
        entra.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody("{\"access_token\":\"exchanged-token\"}")
                        .addHeader("Content-Type", "application/json"));
        entra.start();
        try {
            AzureFoundryAuth auth =
                    AzureFoundryAuth.resolve(
                            Map.of("client_id", "cid", "client_secret", "cs", "tenant_id", "tid"),
                            redirectedTo(entra),
                            "callers-sso-token",
                            AzureFoundryAuth.FOUNDRY_SCOPE);

            assertThat(auth.headerValue()).isEqualTo("Bearer exchanged-token");
            // The token belongs to one person; caching it would hand their identity to the next
            // poll.
            assertThat(auth.isReusable()).isFalse();
        } finally {
            entra.shutdown();
        }
    }

    @Test
    void anIncompleteServicePrincipalFailsRatherThanRunningAsTheDeployment() {
        // Half a service principal cannot be exchanged, and running as the deployment instead
        // would quietly ignore the request to act as the caller and use the server's own, wider
        // privileges. That is the silent wrong-identity bug, not a graceful degradation.
        assertThatThrownBy(
                        () ->
                                AzureFoundryAuth.resolve(
                                        Map.of("tenant_id", "tid"),
                                        httpClient,
                                        "callers-sso-token",
                                        AzureFoundryAuth.FOUNDRY_SCOPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void credentialsThatAllResolveToNullFailInsteadOfFallingBack() {
        // What a missing or malformed secret actually looks like by the time it reaches a client:
        // ${workflow.secrets.NAME.key} resolves to null, so every value arrives blank and no mode
        // matches. Falling through to the deployment's own identity here is how a broken secret
        // turns into an agent silently running as somebody else.
        Map<String, String> allNull = new HashMap<>();
        allNull.put("client_id", null);
        allNull.put("client_secret", null);
        allNull.put("tenant_id", null);

        assertThatThrownBy(() -> resolve(allNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client_id")
                .hasMessageContaining("client_secret")
                .hasMessageContaining("tenant_id")
                .hasMessageContaining("every one of them is empty");
    }

    @Test
    void aPartlyResolvedCredentialSaysWhichKeysArrivedAndWhichDidNot() {
        // Reporting "none of them resolved" for a credential that partly resolved sends the reader
        // to the secret when the problem is the task. Both halves have to be named.
        Map<String, String> partial = new HashMap<>();
        partial.put("client_id", "cid");
        partial.put("client_secret", null);
        partial.put("tenant_id", null);

        assertThatThrownBy(() -> resolve(partial))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("set [client_id], which resolved")
                .hasMessageContaining("[client_secret, tenant_id], which did not");
    }

    @Test
    void aCredentialStoredWithItsQuotesIsRejectedRatherThanSent() {
        // A flat ${workflow.secrets.NAME} is handed over exactly as stored, so quotes a .env file
        // kept ride along into the provider and come back as an opaque authentication failure.
        assertThatThrownBy(() -> resolve(Map.of("apiKey", "'sk-azure'")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey")
                .hasMessageContaining("starts and ends with a '");

        assertThatThrownBy(() -> resolve(Map.of("client_secret", "\"shh\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client_secret");
    }

    @Test
    void aQuoteInsideACredentialIsLeftAlone() {
        // Only a value wrapped on both ends is suspect; one that merely contains a quote is a
        // perfectly ordinary secret and must not be rejected.
        assertThat(resolve(Map.of("apiKey", "sk-a'b'c")).headerName()).isEqualTo("api-key");
    }

    @Test
    void aScopeOverrideAloneIsNotMistakenForABrokenCredential() {
        // scope rides in the same map but is not something to authenticate with, so a deployment
        // that only overrides the scope still reaches the default chain.
        assertThat(resolve(Map.of("scope", AzureFoundryAuth.FOUNDRY_SCOPE)).headerName())
                .isEqualTo("Authorization");
    }

    @Test
    void aRejectedExchangeSaysWhatTheEndpointReturned() throws Exception {
        MockWebServer entra = new MockWebServer();
        entra.enqueue(
                new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"));
        entra.start();
        try {
            assertThatThrownBy(
                            () ->
                                    AzureFoundryAuth.exchangeOnBehalfOf(
                                            redirectedTo(entra),
                                            "expired",
                                            "tid",
                                            "cid",
                                            "cs",
                                            AzureFoundryAuth.FOUNDRY_SCOPE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("invalid_grant");
        } finally {
            entra.shutdown();
        }
    }

    /** Sends the hardcoded Entra ID host to the mock server instead. */
    private static OkHttpClient redirectedTo(MockWebServer server) {
        return new OkHttpClient.Builder()
                .addInterceptor(
                        chain -> {
                            okhttp3.Request request = chain.request();
                            if ("login.microsoftonline.com".equals(request.url().host())) {
                                request =
                                        request.newBuilder()
                                                .url(
                                                        request.url()
                                                                .newBuilder()
                                                                .scheme("http")
                                                                .host(server.getHostName())
                                                                .port(server.getPort())
                                                                .build())
                                                .build();
                            }
                            return chain.proceed(request);
                        })
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }
}
