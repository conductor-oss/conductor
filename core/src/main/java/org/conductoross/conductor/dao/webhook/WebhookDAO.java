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
package org.conductoross.conductor.dao.webhook;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;

public interface WebhookDAO {
    IncomingWebhookEvent getWebhookEvent(String messageId);

    WebhookConfig getWebhook(String webhookId);

    Map<String, Map<String, Object>> getMatchers(String webhookId);

    void createWebhook(String id, WebhookConfig webhookConfig);

    void removeWebhookEvent(String id);

    void removeWebhook(String id);

    void removeMatchers(String id);

    List<WebhookConfig> getAllWebhooks();

    void createMatchers(
            WebhookConfig webhookConfig,
            Map<String, Integer> receiverWorkflowNamesToVersionsOverride);

    void createIncomingWebhookEvent(String id, IncomingWebhookEvent incomingWebhookEvent);

    /**
     * Atomically record a webhook event signature for replay protection. Returns {@code true} iff
     * the {@code (webhookId, signature)} pair has not been recorded in the last {@code ttl}. A
     * returned {@code false} means the inbound event is a replay.
     *
     * <p>The default implementation accepts everything — backends that don't yet implement dedup
     * retain the prior behavior. Implement to enforce replay protection.
     *
     * @param webhookId the webhook config id receiving the event (scopes the dedup namespace)
     * @param signature the verifier-supplied signature string from the inbound request
     * @param ttl how long the signature is remembered; events with the same signature inside this
     *     window are rejected as replays
     */
    default boolean tryRecordSignature(String webhookId, String signature, Duration ttl) {
        return true;
    }
}
