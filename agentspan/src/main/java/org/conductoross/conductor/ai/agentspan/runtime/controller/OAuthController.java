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
package org.conductoross.conductor.ai.agentspan.runtime.controller;

import java.util.List;

import org.conductoross.conductor.ai.agentspan.runtime.service.OAuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints for the delegated-access OAuth 2.0 flow.
 *
 * <ul>
 *   <li>{@code GET /api/oauth/authorize} — returns the provider authorization URL for the UI to
 *       open as a popup
 *   <li>{@code GET /api/oauth/callback} — receives the authorization code from the provider,
 *       exchanges it for a refresh token, stores it as a secret, and closes the popup
 * </ul>
 */
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final OAuthTokenService oAuthTokenService;

    /**
     * Returns the Microsoft authorization URL the UI should open as a popup.
     *
     * @param key       the {@code key} field from the workflow's {@code requiredDelegations} entry
     * @param secretRef the secret name where the refresh token will be stored
     * @param scopes    space-separated OAuth scopes (e.g. {@code "https://ai.azure.com/.default offline_access"})
     */
    @GetMapping("/authorize")
    public ResponseEntity<String> authorize(
            @RequestParam("key") String key,
            @RequestParam("secretRef") String secretRef,
            @RequestParam("scopes") String scopes) {

        List<String> scopeList = List.of(scopes.split("\\s+"));
        String url = oAuthTokenService.buildAuthorizationUrl(key, secretRef, scopeList);
        return ResponseEntity.ok(url);
    }

    /**
     * OAuth callback — Microsoft redirects here after the user consents. Exchanges the code for a
     * refresh token, stores it, and serves a small HTML page that notifies the opener popup and
     * closes itself.
     */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription) {

        if (error != null) {
            log.warn("OAuth callback received error: {} — {}", error, errorDescription);
            return ResponseEntity.ok(closePopupHtml(false, null, error));
        }

        try {
            String decoded = oAuthTokenService.handleCallback(code, state);
            String key = decoded.split(":", 2)[0];
            return ResponseEntity.ok(closePopupHtml(true, key, null));
        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return ResponseEntity.ok(closePopupHtml(false, null, e.getMessage()));
        }
    }

    /**
     * Serves a minimal HTML page that posts a message to the parent window and closes the popup.
     * The UI listens for {@code window.addEventListener('message', ...)} to detect completion.
     */
    private String closePopupHtml(boolean success, String key, String errorMsg) {
        String payload = success
                ? "{\"type\":\"oauth-complete\",\"success\":true,\"key\":\"" + key + "\"}"
                : "{\"type\":\"oauth-complete\",\"success\":false,\"error\":\"" + escapeJson(errorMsg) + "\"}";

        return "<!DOCTYPE html><html><body><script>"
                + "try { window.opener.postMessage(" + payload + ", '*'); } catch(e) {}"
                + "window.close();"
                + "</script><p>"
                + (success ? "Authorization complete. You may close this window." : "Authorization failed: " + escapeHtml(errorMsg))
                + "</p></body></html>";
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
