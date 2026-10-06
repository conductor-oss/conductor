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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Microsoft Entra ID (Azure AD) OAuth 2.0 provider for delegated access.
 *
 * <p>Default scopes cover Azure AI Foundry. Adding Graph scopes (Mail.Read, Calendars.ReadWrite,
 * etc.) here in the future would extend this to email/calendar use cases without any changes to the
 * controller or registry.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class MicrosoftOAuthProvider implements OAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftOAuthProvider.class);
    private static final String MS_AUTH_BASE = "https://login.microsoftonline.com";

    @Value("${conductor.oauth.microsoft.tenant-id:common}")
    private String tenantId;

    @Value("${conductor.oauth.microsoft.client-id:}")
    private String clientId;

    @Value("${conductor.oauth.microsoft.client-secret:}")
    private String clientSecret;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MicrosoftOAuthProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String providerKey() {
        return "microsoft";
    }

    @Override
    public String buildAuthorizationUrl(
            String key, String secretRef, List<String> scopes, String redirectUri) {
        String scopeStr = String.join(" ", mergeScopes(scopes));
        String state =
                Base64.getUrlEncoder()
                        .encodeToString(
                                ("microsoft:" + key + ":" + secretRef)
                                        .getBytes(StandardCharsets.UTF_8));

        return MS_AUTH_BASE
                + "/"
                + tenantId
                + "/oauth2/v2.0/authorize"
                + "?client_id="
                + encode(clientId)
                + "&response_type=code"
                + "&redirect_uri="
                + encode(redirectUri)
                + "&scope="
                + encode(scopeStr)
                + "&response_mode=query"
                + "&state="
                + encode(state);
    }

    @Override
    public String exchangeCodeForRefreshToken(String code, String redirectUri) {
        Map<String, String> params =
                Map.of(
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", redirectUri,
                        "client_id", clientId,
                        "client_secret", clientSecret);

        String body =
                params.entrySet().stream()
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(Collectors.joining("&"));

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(MS_AUTH_BASE + "/" + tenantId + "/oauth2/v2.0/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Token exchange failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode refreshToken = json.get("refresh_token");
            if (refreshToken == null || refreshToken.isNull()) {
                throw new RuntimeException(
                        "No refresh_token in response — offline_access scope may be missing");
            }
            return refreshToken.asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Token exchange request failed", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
