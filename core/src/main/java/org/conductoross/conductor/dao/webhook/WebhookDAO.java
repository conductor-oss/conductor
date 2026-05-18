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

import java.util.List;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;

/**
 * Persists webhook configurations, matcher indexes, and inbound event audit records.
 *
 * <p>Implementations: {@code InMemoryWebhookDAO} (single-node, default), {@code RedisWebhookDAO} /
 * {@code PostgresWebhookDAO} (multi-node, land in later PRs).
 *
 * <p><b>Mutation contract:</b> objects returned from {@code get*} methods must not be mutated by
 * callers. Implementations may return live references to stored state; deserializing impls
 * (Redis/Postgres) return fresh objects, in-memory impls do not. To modify a stored value,
 * construct a new instance and write it back via the corresponding {@code create*} method.
 */
public interface WebhookDAO {

    void createWebhook(String id, WebhookConfig config);

    /**
     * @see WebhookDAO — returned config must not be mutated by the caller.
     */
    WebhookConfig getWebhook(String id);

    /**
     * @see WebhookDAO — the returned list is a fresh collection, but its elements must not be
     *     mutated.
     */
    List<WebhookConfig> getAllWebhooks();

    void removeWebhook(String id);

    void createIncomingWebhookEvent(String id, IncomingWebhookEvent event);

    /**
     * @see WebhookDAO — the returned event must not be mutated by the caller.
     */
    IncomingWebhookEvent getIncomingWebhookEvent(String id);

    void removeIncomingWebhookEvent(String id);
}
