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
package org.conductoross.conductor.tasks.webhook.verifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.common.webhook.model.WebhookConfig;

/**
 * Verifies inbound webhook requests against a {@link WebhookConfig}.
 *
 * <p>Implementations are discovered as Spring beans; {@link #getType()} matches {@link
 * WebhookConfig.Verifier} enum values. Concrete implementations (HeaderBasedVerifier,
 * SignatureBasedVerifier, etc.) land in a later PR.
 */
public interface WebhookVerifier {

    /**
     * @return list of error messages; empty list means verification passed.
     */
    List<String> verify(WebhookConfig config, IncomingWebhookEvent event);

    /**
     * @return the {@link WebhookConfig.Verifier} enum value this verifier handles, as a string
     *     (e.g. {@code "HEADER_BASED"}).
     */
    String getType();

    /**
     * Extracts a URL-verification challenge from an inbound POST body, if the configured platform
     * uses one (e.g. Slack's {@code challenge} field).
     *
     * @return the challenge string to echo back, or {@code null} for none.
     */
    default String extractChallenge(IncomingWebhookEvent event, WebhookConfig config) {
        return null;
    }

    /**
     * Handles an inbound GET (ping) request for the configured platform, if any.
     *
     * @return the response body to return, or {@code null} to fall through to the default ping
     *     handling.
     */
    default String handlePing(WebhookConfig config, Map<String, Object> requestParams) {
        return null;
    }

    /** No-error sentinel for {@link #verify(WebhookConfig, IncomingWebhookEvent)}. */
    List<String> OK = Collections.emptyList();
}
