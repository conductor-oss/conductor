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
package org.conductoross.conductor.dao.memory.webhook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;

/**
 * Default single-node implementation of {@link WebhookDAO}.
 *
 * <p>Backed by in-process maps; suitable for single-server deployments and tests. Multi-node
 * deployments should bind a persistent implementation (lands in a later PR).
 */
public class InMemoryWebhookDAO implements WebhookDAO {

    private final ConcurrentHashMap<String, WebhookConfig> configs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Map<String, Object>>> matchers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IncomingWebhookEvent> events =
            new ConcurrentHashMap<>();

    @Override
    public void createWebhook(String id, WebhookConfig config) {
        configs.put(id, config);
    }

    @Override
    public WebhookConfig getWebhook(String id) {
        return configs.get(id);
    }

    @Override
    public List<WebhookConfig> getAllWebhooks() {
        return new ArrayList<>(configs.values());
    }

    @Override
    public void removeWebhook(String id) {
        configs.remove(id);
    }

    @Override
    public void createMatchers(String webhookId, Map<String, Map<String, Object>> matcherIndex) {
        matchers.put(webhookId, matcherIndex);
    }

    @Override
    public Map<String, Map<String, Object>> getMatchers(String webhookId) {
        Map<String, Map<String, Object>> stored = matchers.get(webhookId);
        return stored == null ? Collections.emptyMap() : stored;
    }

    @Override
    public void removeMatchers(String webhookId) {
        matchers.remove(webhookId);
    }

    @Override
    public void createIncomingWebhookEvent(String id, IncomingWebhookEvent event) {
        events.put(id, event);
    }

    @Override
    public IncomingWebhookEvent getIncomingWebhookEvent(String id) {
        return events.get(id);
    }

    @Override
    public void removeIncomingWebhookEvent(String id) {
        events.remove(id);
    }
}
