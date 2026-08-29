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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.agentspan.runtime.service.assistants.AssistantsAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * How a call to Azure AI Foundry authenticates, in one of four ways plus an on-behalf-of mode.
 *
 * <p>Resolution order, first match wins:
 *
 * <ol>
 *   <li><b>On-behalf-of</b> — the caller's own Entra identity, when the request asks for it and
 *       carries a user assertion and the credential holds service-principal details. The caller's
 *       SSO token is never forwarded; it is exchanged for a Foundry-scoped one.
 *   <li><b>API key</b> — {@code apiKey}, sent as an {@code api-key} header. No SDK.
 *   <li><b>Service principal</b> — {@code .client_id} + {@code .client_secret} + {@code
 *       .tenant_id}.
 *   <li><b>User-assigned managed identity</b> — {@code .clientId}.
 *   <li><b>Default credential chain</b> — environment, workload identity, managed identity, CLI.
 * </ol>
 *
 * <p>The scope follows the endpoint unless overridden, because Foundry's surfaces do not share one.
 */
public final class AzureFoundryAuth implements AssistantsAuth {

    private static final Logger log = LoggerFactory.getLogger(AzureFoundryAuth.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType FORM =
            MediaType.get("application/x-www-form-urlencoded; charset=utf-8");

    /** Cognitive Services — the classic Assistants surface on {@code openai.azure.com}. */
    public static final String DEFAULT_SCOPE = "https://cognitiveservices.azure.com/.default";

    /** Foundry projects on {@code services.ai.azure.com}. */
    public static final String FOUNDRY_SCOPE = "https://ai.azure.com/.default";

    /** Azure ML online endpoints on {@code inference.ml.azure.com}. */
    public static final String ML_INFERENCE_SCOPE = "https://ml.azure.com/.default";

    private final TokenCredential credential;
    private final String scope;
    private final String apiKey;
    private final String bearerToken;

    private AzureFoundryAuth(
            TokenCredential credential, String scope, String apiKey, String bearerToken) {
        this.credential = credential;
        this.scope = scope;
        this.apiKey = apiKey;
        this.bearerToken = bearerToken;
    }

    public static AzureFoundryAuth ofApiKey(String apiKey) {
        return new AzureFoundryAuth(null, null, apiKey, null);
    }

    public static AzureFoundryAuth ofCredential(TokenCredential credential, String scope) {
        return new AzureFoundryAuth(credential, scope, null, null);
    }

    /** A token already exchanged for the caller — never cached, since it belongs to one person. */
    public static AzureFoundryAuth ofBearer(String bearerToken) {
        return new AzureFoundryAuth(null, null, null, bearerToken);
    }

    /**
     * Whether this may be reused across calls. An SDK credential and an API key belong to the
     * deployment; a token exchanged on behalf of a caller belongs to that caller and must not be.
     */
    public boolean isReusable() {
        return bearerToken == null;
    }

    @Override
    public String headerName() {
        return (credential != null || bearerToken != null) ? "Authorization" : "api-key";
    }

    @Override
    public String headerValue() {
        if (bearerToken != null) {
            return "Bearer " + bearerToken;
        }
        if (credential != null) {
            // The SDK caches and refreshes behind this call, so asking per request is cheap.
            return "Bearer "
                    + credential
                            .getToken(new TokenRequestContext().addScopes(scope))
                            .block()
                            .getToken();
        }
        return apiKey;
    }

    /** The scope a Foundry surface expects, inferred from its endpoint. */
    public static String scopeFor(String endpoint) {
        if (endpoint != null && endpoint.contains("inference.ml.azure.com")) {
            return ML_INFERENCE_SCOPE;
        }
        if (endpoint != null && endpoint.contains("services.ai.azure.com")) {
            return FOUNDRY_SCOPE;
        }
        return DEFAULT_SCOPE;
    }

    /**
     * Builds auth from the credential values the engine substituted into the task input.
     *
     * <p>userAssertion activates on-behalf-of when the credential also holds service-principal
     * details. Without any credential at all it falls back to the deployment's own identity, which
     * is how a cluster on managed identity is meant to be configured. With a credential that is
     * present but unusable it fails instead: running as the deployment would ignore the request to
     * act as the caller and use the server's own, wider privileges.
     */
    public static AzureFoundryAuth resolve(
            Map<String, String> credentials,
            OkHttpClient httpClient,
            String userAssertion,
            String scope) {

        String tenantId = AgentCredentials.value(credentials, "tenant_id");
        String clientId = AgentCredentials.value(credentials, "client_id");
        String clientSecret = AgentCredentials.value(credentials, "client_secret");

        if (StringUtils.isNotBlank(userAssertion)) {
            if (StringUtils.isNoneBlank(tenantId, clientId, clientSecret)) {
                return ofBearer(
                        exchangeOnBehalfOf(
                                httpClient,
                                userAssertion,
                                tenantId,
                                clientId,
                                clientSecret,
                                scope));
            }
            log.warn(
                    "Caller identity was requested but no service principal was supplied to"
                            + " exchange the token with. Falling through to the remaining modes;"
                            + " this fails unless the deployment authenticates on its own.");
        }

        String apiKey = AgentCredentials.apiKey(credentials);
        if (StringUtils.isNotBlank(apiKey)) {
            return ofApiKey(apiKey);
        }

        if (StringUtils.isNoneBlank(clientId, clientSecret, tenantId)) {
            return ofCredential(
                    new ClientSecretCredentialBuilder()
                            .tenantId(tenantId)
                            .clientId(clientId)
                            .clientSecret(clientSecret)
                            .build(),
                    scope);
        }

        String managedIdentityClientId =
                AgentCredentials.value(credentials, "managedIdentityClientId");
        if (StringUtils.isNotBlank(managedIdentityClientId)) {
            return ofCredential(
                    new ManagedIdentityCredentialBuilder()
                            .clientId(managedIdentityClientId)
                            .build(),
                    scope);
        }

        AgentCredentials.rejectPartiallyResolved(credentials, AUTH_KEYS, "Azure");
        return ofCredential(new DefaultAzureCredentialBuilder().build(), scope);
    }

    /** Keys this class can authenticate with; anything else in the map is not a credential. */
    private static final Set<String> AUTH_KEYS =
            Set.of(
                    "apiKey",
                    "api_key",
                    "client_id",
                    "client_secret",
                    "tenant_id",
                    "managedIdentityClientId");

    /**
     * Exchanges a caller's Entra token for one scoped to Foundry, via the OAuth 2.0 on-behalf-of
     * grant. The caller's own token never reaches Foundry.
     */
    static String exchangeOnBehalfOf(
            OkHttpClient httpClient,
            String userAssertion,
            String tenantId,
            String clientId,
            String clientSecret,
            String scope) {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        String form =
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
                        + "&client_id="
                        + encode(clientId)
                        + "&client_secret="
                        + encode(clientSecret)
                        + "&assertion="
                        + encode(userAssertion)
                        + "&scope="
                        + encode(scope)
                        + "&requested_token_use=on_behalf_of";

        Request request =
                new Request.Builder()
                        .url(tokenUrl)
                        .post(RequestBody.create(form.getBytes(StandardCharsets.UTF_8), FORM))
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "On-behalf-of token exchange failed: HTTP "
                                + response.code()
                                + " — "
                                + body);
            }
            JsonNode json = MAPPER.readTree(body);
            String token = json.path("access_token").asText(null);
            if (StringUtils.isBlank(token)) {
                throw new IllegalStateException(
                        "On-behalf-of token exchange returned no access_token: " + body);
            }
            return token;
        } catch (IOException e) {
            throw new IllegalStateException("On-behalf-of token exchange failed", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
