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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link WebhookConfigDAO}.
 *
 * <p><strong>All stored configs are lost on server restart.</strong> This implementation is
 * suitable for development and single-node testing only. Register a durable implementation (e.g.
 * backed by Postgres or Redis) as a Spring bean to replace it — the
 * {@code @ConditionalOnMissingBean} annotation ensures this class is skipped when another
 * implementation is present.
 */
@Component
@ConditionalOnMissingBean(value = WebhookConfigDAO.class, ignored = InMemoryWebhookConfigDAO.class)
public class InMemoryWebhookConfigDAO implements WebhookConfigDAO {

    private final Map<String, WebhookConfig> store = new ConcurrentHashMap<>();

    @Override
    public void save(String id, WebhookConfig config) {
        store.put(id, config);
    }

    @Override
    public WebhookConfig get(String id) {
        return store.get(id);
    }

    @Override
    public void remove(String id) {
        store.remove(id);
    }

    @Override
    public List<WebhookConfig> getAll() {
        return new ArrayList<>(store.values());
    }
}
