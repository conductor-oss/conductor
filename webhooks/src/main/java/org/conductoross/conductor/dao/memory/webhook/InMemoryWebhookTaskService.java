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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.model.TaskModel;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;

@Component
public class InMemoryWebhookTaskService implements WebhookTaskService {

    // hash → Set<taskId>
    private final ConcurrentHashMap<String, Set<String>> hashToTasks = new ConcurrentHashMap<>();

    // webhookId → Set<taskId>
    private final ConcurrentHashMap<String, Set<String>> webhookIdToTasks =
            new ConcurrentHashMap<>();

    // taskId → webhookId (reverse index so remove(hash, taskId) can clean up webhookId index)
    private final ConcurrentHashMap<String, String> taskToWebhookId = new ConcurrentHashMap<>();

    @Override
    public void put(TaskModel task, int workflowVersion) {
        Map<String, Object> inputData = task.getInputData();
        if (inputData == null) {
            throw new NonTransientException("Task input data is null for task " + task.getTaskId());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> matches = (Map<String, Object>) inputData.get("matches");
        if (matches == null) {
            throw new NonTransientException("Missing matches field in task " + task.getTaskId());
        }

        String hash =
                computeHash(
                        task.getWorkflowType(),
                        workflowVersion,
                        TaskUtils.removeIterationFromTaskRefName(task.getReferenceTaskName()),
                        matches);

        hashToTasks.computeIfAbsent(hash, k -> ConcurrentHashMap.newKeySet()).add(task.getTaskId());

        Object rawWebhookId = matches.get("webhookId");
        if (rawWebhookId instanceof String webhookId && isValidWebhookId(webhookId)) {
            webhookIdToTasks
                    .computeIfAbsent(webhookId, k -> ConcurrentHashMap.newKeySet())
                    .add(task.getTaskId());
            taskToWebhookId.put(task.getTaskId(), webhookId);
        }
    }

    @Override
    public Set<String> get(String hash) {
        Set<String> tasks = hashToTasks.get(hash);
        return tasks == null ? new HashSet<>() : new HashSet<>(tasks);
    }

    @Override
    public void remove(String hash, String taskId) {
        hashToTasks.computeIfPresent(
                hash,
                (k, set) -> {
                    set.remove(taskId);
                    return set.isEmpty() ? null : set;
                });

        String webhookId = taskToWebhookId.remove(taskId);
        if (webhookId != null) {
            webhookIdToTasks.computeIfPresent(
                    webhookId,
                    (k, set) -> {
                        set.remove(taskId);
                        return set.isEmpty() ? null : set;
                    });
        }
    }

    @Override
    public Set<String> getByWebhookId(String webhookId) {
        Set<String> tasks = webhookIdToTasks.get(webhookId);
        return tasks == null ? new HashSet<>() : new HashSet<>(tasks);
    }

    @Override
    public void removeByWebhookId(String webhookId, String taskId) {
        webhookIdToTasks.computeIfPresent(
                webhookId,
                (k, set) -> {
                    set.remove(taskId);
                    return set.isEmpty() ? null : set;
                });
        taskToWebhookId.remove(taskId);
    }

    private static String computeHash(
            String workflowType, int version, String refName, Map<String, Object> matches) {
        StringBuilder sb =
                new StringBuilder(
                        workflowType + WEBHOOK_DELIMITER + version + WEBHOOK_DELIMITER + refName);
        new TreeSet<>(matches.keySet())
                .forEach(k -> sb.append(WEBHOOK_DELIMITER).append(matches.get(k)));
        return sb.toString();
    }

    private static boolean isValidWebhookId(String webhookId) {
        if (webhookId.isEmpty() || webhookId.length() > 256) {
            return false;
        }
        for (char c : webhookId.toCharArray()) {
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                return false;
            }
        }
        return true;
    }
}
