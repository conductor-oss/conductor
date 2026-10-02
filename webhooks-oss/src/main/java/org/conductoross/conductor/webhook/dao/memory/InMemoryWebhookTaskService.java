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
package org.conductoross.conductor.webhook.dao.memory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.service.webhook.WebhookTaskHashing;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.netflix.conductor.model.TaskModel;

/**
 * Default single-node implementation of {@link WebhookTaskService}.
 *
 * <p>Backed by an in-process map; suitable for single-server deployments and tests. Multi-node
 * deployments should bind {@code RedisWebhookTaskService} (lands in a later PR) instead.
 */
@Component
@ConditionalOnMissingBean(name = "webhookTaskService")
public class InMemoryWebhookTaskService implements WebhookTaskService {

    private final ConcurrentHashMap<String, Set<String>> storage = new ConcurrentHashMap<>();

    @Override
    public void put(TaskModel task, int workflowVersion) {
        String hash = WebhookTaskHashing.computeHash(task, workflowVersion);
        storage.compute(
                hash,
                (key, taskIds) -> {
                    Set<String> bucket = taskIds == null ? ConcurrentHashMap.newKeySet() : taskIds;
                    bucket.add(task.getTaskId());
                    return bucket;
                });
    }

    @Override
    public Set<String> get(String hash) {
        Set<String> taskIds = storage.get(hash);
        return taskIds == null ? Collections.emptySet() : new HashSet<>(taskIds);
    }

    @Override
    public void remove(String hash, String taskId) {
        storage.computeIfPresent(
                hash,
                (key, taskIds) -> {
                    taskIds.remove(taskId);
                    return taskIds.isEmpty() ? null : taskIds;
                });
    }

    @Override
    public void remove(TaskModel task, int workflowVersion) {
        String hash = WebhookTaskHashing.computeHashIfPresent(task, workflowVersion);
        if (hash != null) remove(hash, task.getTaskId());
    }
}
