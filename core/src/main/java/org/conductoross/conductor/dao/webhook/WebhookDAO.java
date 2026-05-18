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
import java.util.Map;

import org.conductoross.conductor.common.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.common.webhook.model.WebhookConfig;

/**
 * Persists webhook configurations, matcher indexes, and inbound event audit records.
 *
 * <p>Implementations: {@code InMemoryWebhookDAO} (single-node, default), {@code
 * RedisWebhookDAO} / {@code PostgresWebhookDAO} (multi-node, land in later PRs).
 *
 * <p>Implementations are responsible for org-level isolation when applicable; the interface itself
 * takes no orgId. Callers invoke {@link WebhookOrgContextProvider#applyContext(String)} before DAO
 * access.
 */
public interface WebhookDAO {

    void createWebhook(String id, WebhookConfig config);

    WebhookConfig getWebhook(String id);

    List<WebhookConfig> getAllWebhooks();

    void removeWebhook(String id);

    void createMatchers(String webhookId, Map<String, Map<String, Object>> matchers);

    Map<String, Map<String, Object>> getMatchers(String webhookId);

    void removeMatchers(String webhookId);

    void createIncomingWebhookEvent(String id, IncomingWebhookEvent event);

    IncomingWebhookEvent getIncomingWebhookEvent(String id);

    void removeIncomingWebhookEvent(String id);
}
