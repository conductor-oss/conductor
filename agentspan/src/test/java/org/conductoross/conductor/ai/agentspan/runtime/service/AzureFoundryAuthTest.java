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

import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
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

    private InMemorySecretsDAO secrets;
    private CredentialResolutionService credentials;
    private OkHttpClient httpClient;

    @BeforeEach
    void setUp() {
        secrets = new InMemorySecretsDAO();
        credentials = new CredentialResolutionService(secrets);
        httpClient = new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build();
    }

    @AfterEach
    void tearDown() {
        httpClient.dispatcher().executorService().shutdown();
    }

    private AzureFoundryAuth resolve(String credentialRef) {
        return AzureFoundryAuth.resolve(
                credentials, httpClient, credentialRef, null, AzureFoundryAuth.DEFAULT_SCOPE);
    }

    @Test
    void anApiKeyIsSentAsAnApiKeyHeaderWithNoSdkInvolved() {
        secrets.put("CRED", """
                {"apiKey":"sk-azure"}""");

        AzureFoundryAuth auth = resolve("CRED");

        assertThat(auth.headerName()).isEqualTo("api-key");
        assertThat(auth.headerValue()).isEqualTo("sk-azure");
        assertThat(auth.isReusable()).isTrue();
    }

    @Test
    void anApiKeyWinsOverAServicePrincipalOnTheSameCredential() {
        secrets.put(
                "CRED",
                """
                {"apiKey":"sk-azure","client_id":"cid","client_secret":"cs","tenant_id":"tid"}""");

        // First match wins, and the API key needs no token exchange at all.
        assertThat(resolve("CRED").headerName()).isEqualTo("api-key");
    }

    @Test
    void aServicePrincipalBecomesABearerCredential() {
        secrets.put(
                "CRED",
                """
                {"client_id":"cid","client_secret":"cs","tenant_id":"tid"}""");

        AzureFoundryAuth auth = resolve("CRED");

        assertThat(auth.headerName()).isEqualTo("Authorization");
        assertThat(auth.isReusable()).isTrue();
    }

    @Test
    void aManagedIdentityClientIdBecomesABearerCredential() {
        secrets.put("CRED", """
                {"clientId":"mi-client"}""");

        AzureFoundryAuth auth = resolve("CRED");

        assertThat(auth.headerName()).isEqualTo("Authorization");
        assertThat(auth.isReusable()).isTrue();
    }

    @Test
    void noCredentialFallsBackToTheDefaultChain() {
        // A deployment running on managed identity configures nothing at all.
        assertThat(resolve(null).headerName()).isEqualTo("Authorization");
        assertThat(resolve("NOT_STORED").headerName()).isEqualTo("Authorization");
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
            secrets.put(
                    "CRED",
                    """
                    {"client_id":"cid","client_secret":"cs","tenant_id":"tid"}""");

            AzureFoundryAuth auth =
                    AzureFoundryAuth.resolve(
                            credentials,
                            redirectedTo(entra),
                            "CRED",
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
    void anIncompleteServicePrincipalFallsBackInsteadOfFailingTheCall() {
        secrets.put("CRED", """
                {"tenant_id":"tid"}""");

        AzureFoundryAuth auth =
                AzureFoundryAuth.resolve(
                        credentials,
                        httpClient,
                        "CRED",
                        "callers-sso-token",
                        AzureFoundryAuth.FOUNDRY_SCOPE);

        // A cluster that asks for caller identity without the credential to exchange it still runs,
        // as the deployment's own identity, rather than failing every agent call.
        assertThat(auth.isReusable()).isTrue();
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
