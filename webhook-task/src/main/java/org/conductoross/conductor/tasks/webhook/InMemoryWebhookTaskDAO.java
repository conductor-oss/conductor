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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link WebhookTaskDAO}.
 *
 * <p><strong>Single-node only.</strong> Registered task mappings are held in memory and will be
 * lost on server restart. For clustered or production-grade deployments, replace this bean with an
 * implementation backed by durable storage (Postgres, Redis, etc.).
 */
@Component
@ConditionalOnMissingBean(value = WebhookTaskDAO.class, ignored = InMemoryWebhookTaskDAO.class)
public class InMemoryWebhookTaskDAO implements WebhookTaskDAO {

    private final ConcurrentHashMap<String, Set<String>> store = new ConcurrentHashMap<>();

    @Override
    public void put(String hash, String taskId) {
        store.computeIfAbsent(hash, k -> ConcurrentHashMap.newKeySet()).add(taskId);
    }

    @Override
    public List<String> get(String hash) {
        Set<String> ids = store.get(hash);
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    @Override
    public void remove(String hash, String taskId) {
        store.computeIfPresent(
                hash,
                (k, ids) -> {
                    ids.remove(taskId);
                    return ids.isEmpty() ? null : ids;
                });
    }
}
