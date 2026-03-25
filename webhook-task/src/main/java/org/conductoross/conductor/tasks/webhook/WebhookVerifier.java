/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for authenticating inbound webhook requests.
 *
 * <p>Each implementation handles one verification approach (e.g. header matching, HMAC signature).
 * Implementations are registered as Spring beans and discovered automatically. The active verifier
 * for a request is selected via {@link WebhookConfig#getVerifier()}.
 *
 * <p>Ported from Orkes Enterprise; no org-specific concerns here.
 */
public interface WebhookVerifier {

    /**
     * Verifies that the inbound event is authentic according to this strategy.
     *
     * @param config the webhook configuration containing the expected secrets/headers
     * @param event the inbound event to verify
     * @return a list of error messages; empty list means verification passed
     */
    List<String> verify(WebhookConfig config, IncomingWebhookEvent event);

    /**
     * Returns the {@link WebhookConfig.Verifier} type string this implementation handles (e.g.
     * {@code "HEADER_BASED"}).
     */
    String getType();

    /**
     * Extracts a challenge response value from the event if the sender requires an echo-back (e.g.
     * Slack URL verification). Returns {@code null} if no challenge is required.
     *
     * <p>When this returns a non-null value, the inbound request is treated as a challenge
     * handshake rather than a real event — no tasks are completed.
     */
    default String extractChallenge(IncomingWebhookEvent event, WebhookConfig config) {
        return null;
    }

    /**
     * Handles a GET ping request sent by the webhook provider to verify the endpoint is live.
     * Returns the response body to echo back, or {@code null} if this is not a ping.
     */
    default String handlePing(WebhookConfig config, Map<String, Object> requestParams) {
        return null;
    }
}
