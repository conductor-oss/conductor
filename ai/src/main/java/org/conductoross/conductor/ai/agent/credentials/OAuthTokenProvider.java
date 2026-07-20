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
package org.conductoross.conductor.ai.agent.credentials;

import java.io.IOException;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * {@link TokenProvider} that acquires short-lived tokens via OAuth 2.0 client credentials grant.
 *
 * <p>Tokens are cached and refreshed automatically 60 seconds before expiry. Thread-safe.
 */
public class OAuthTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenProvider.class);
    private static final int REFRESH_BUFFER_SECONDS = 60;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient httpClient;
    private final String tokenEndpointUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public OAuthTokenProvider(
            OkHttpClient httpClient,
            String tokenEndpointUrl,
            String clientId,
            String clientSecret,
            String scope) {
        this.httpClient = httpClient;
        this.tokenEndpointUrl = tokenEndpointUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    /** Factory for Azure Entra ID (formerly Azure Active Directory) client credentials flow. */
    public static OAuthTokenProvider forAzureEntraId(
            OkHttpClient httpClient,
            String tenantId,
            String clientId,
            String clientSecret,
            String scope) {
        String endpoint =
                "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        return new OAuthTokenProvider(httpClient, endpoint, clientId, clientSecret, scope);
    }

    @Override
    public synchronized String getToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
            refresh();
        }
        return cachedToken;
    }

    private void refresh() {
        FormBody body =
                new FormBody.Builder()
                        .add("grant_type", "client_credentials")
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("scope", scope)
                        .build();

        Request request = new Request.Builder().url(tokenEndpointUrl).post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException(
                        "OAuth token request failed: HTTP " + response.code());
            }
            JsonNode json = MAPPER.readTree(response.body().string());
            cachedToken = json.get("access_token").asText();
            long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600L;
            expiresAt = Instant.now().plusSeconds(expiresIn);
            log.debug("OAuth token refreshed, expires at {}", expiresAt);
        } catch (IOException e) {
            throw new RuntimeException("Failed to acquire OAuth token from " + tokenEndpointUrl, e);
        }
    }
}
