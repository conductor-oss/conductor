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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.conductoross.conductor.dao.SecretsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles the Microsoft OAuth 2.0 authorization code flow for delegated access.
 *
 * <p>Builds authorization URLs and exchanges auth codes for refresh tokens, which are stored as
 * Conductor secrets so workflows can act on behalf of consenting users even in scheduled/background
 * runs.
 */
@Service
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    private static final String MS_AUTH_BASE = "https://login.microsoftonline.com";

    @Value("${conductor.oauth.microsoft.tenant-id:common}")
    private String tenantId;

    @Value("${conductor.oauth.microsoft.client-id:}")
    private String clientId;

    @Value("${conductor.oauth.microsoft.client-secret:}")
    private String clientSecret;

    @Value("${conductor.oauth.base-redirect-url:http://localhost:8080}")
    private String baseRedirectUrl;

    private final SecretsDAO secretsDAO;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OAuthTokenService(SecretsDAO secretsDAO, ObjectMapper objectMapper) {
        this.secretsDAO = secretsDAO;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Builds the Microsoft authorization URL that the UI opens as a popup. The {@code secretRef}
     * and {@code key} are encoded in the {@code state} parameter so the callback knows where to
     * store the resulting refresh token.
     */
    public String buildAuthorizationUrl(String key, String secretRef, List<String> scopes) {
        String scopeStr = String.join(" ", scopes);
        if (!scopeStr.contains("offline_access")) {
            scopeStr += " offline_access";
        }

        String state = Base64.getUrlEncoder().encodeToString(
                (key + ":" + secretRef).getBytes(StandardCharsets.UTF_8));

        return MS_AUTH_BASE + "/" + tenantId + "/oauth2/v2.0/authorize"
                + "?client_id=" + encode(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + encode(callbackUrl())
                + "&scope=" + encode(scopeStr)
                + "&response_mode=query"
                + "&state=" + encode(state);
    }

    /**
     * Handles the OAuth callback: exchanges the authorization code for tokens, stores the refresh
     * token as a Conductor secret, and returns the key+secretRef from the state so the UI can
     * confirm which delegation was completed.
     *
     * @return decoded state string in {@code "key:secretRef"} format
     */
    public String handleCallback(String code, String state) {
        String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        String[] parts = decoded.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid state parameter");
        }
        String key = parts[0];
        String secretRef = parts[1];

        String refreshToken = exchangeCodeForRefreshToken(code);
        secretsDAO.putSecret(secretRef, refreshToken);
        log.info("Stored delegated refresh token under secret '{}' for key '{}'", secretRef, key);

        return decoded;
    }

    private String exchangeCodeForRefreshToken(String code) {
        Map<String, String> params = Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", callbackUrl(),
                "client_id", clientId,
                "client_secret", clientSecret);

        String body = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MS_AUTH_BASE + "/" + tenantId + "/oauth2/v2.0/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Token exchange failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode refreshToken = json.get("refresh_token");
            if (refreshToken == null || refreshToken.isNull()) {
                throw new RuntimeException("No refresh_token in response — ensure offline_access scope was requested");
            }
            return refreshToken.asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Token exchange request failed", e);
        }
    }

    private String callbackUrl() {
        return baseRedirectUrl + "/api/oauth/callback";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
