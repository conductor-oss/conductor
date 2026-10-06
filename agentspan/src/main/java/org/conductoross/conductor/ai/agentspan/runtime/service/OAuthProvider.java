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

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy for a single OAuth provider (Microsoft, Google, etc.).
 *
 * <p>Implement this interface and register it as a Spring bean to add a new provider — no changes
 * needed in the controller or registry.
 */
public interface OAuthProvider {

    /** Short identifier matching the {@code provider} field in {@code requiredDelegations}. */
    String providerKey();

    /**
     * Builds the authorization URL the UI opens as a popup.
     *
     * @param key delegation key from the workflow def
     * @param secretRef secret name where the refresh token will be stored after consent
     * @param scopes scopes declared by the workflow author; provider ensures {@code offline_access}
     *     is present
     * @param redirectUri full callback URL registered with the provider
     */
    String buildAuthorizationUrl(
            String key, String secretRef, List<String> scopes, String redirectUri);

    /**
     * Exchanges an authorization code for a refresh token.
     *
     * @param code the code received at the callback URL
     * @param redirectUri must match the one used in {@link #buildAuthorizationUrl}
     * @return refresh token string to persist as a Conductor secret
     */
    String exchangeCodeForRefreshToken(String code, String redirectUri);

    /**
     * Merges the author-supplied scopes with any scopes the provider must always include. Default
     * implementation ensures {@code offline_access} is present (required for refresh token).
     */
    default List<String> mergeScopes(List<String> authorScopes) {
        List<String> merged = new ArrayList<>(authorScopes != null ? authorScopes : List.of());
        if (!merged.contains("offline_access")) {
            merged.add("offline_access");
        }
        return merged;
    }
}
