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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.service.webhook.WebhookTaskService;

import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.model.TaskModel;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;

/**
 * Default single-node implementation of {@link WebhookTaskService}.
 *
 * <p>Backed by an in-process map; suitable for single-server deployments and tests. Multi-node
 * deployments should bind {@code RedisWebhookTaskService} (lands in a later PR) instead.
 */
public class InMemoryWebhookTaskService implements WebhookTaskService {

    private final ConcurrentHashMap<String, Set<String>> storage = new ConcurrentHashMap<>();

    @Override
    public void put(TaskModel task, int workflowVersion) {
        Map<String, Object> expectedMatches = getAndValidateExpectedMatches(task);
        String hash = computeHash(task, workflowVersion, expectedMatches);
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

    private String computeHash(
            TaskModel task, int workflowVersion, Map<String, Object> expectedMatches) {
        return computeHash(
                task.getWorkflowType(),
                workflowVersion,
                TaskUtils.removeIterationFromTaskRefName(task.getReferenceTaskName()),
                expectedMatches);
    }

    /**
     * Matches keys are sorted, but each value's contribution is just {@link Object#toString()}.
     * That is stable for strings, numbers, and booleans. It is <b>not</b> stable for nested {@link
     * Map} values whose toString reflects iteration order (HashMap, etc.) — those can hash
     * differently across JVM runs even for equal inputs. Mirrors the Orkes Redis impl verbatim; any
     * change to value-side canonicalization needs to land in Orkes too or hashes diverge between
     * OSS and Orkes deployments.
     */
    private String computeHash(
            String workflowName,
            int workflowVersion,
            String taskReferenceName,
            Map<String, Object> expectedMatches) {
        TreeSet<String> sortedFields = new TreeSet<>(expectedMatches.keySet());
        StringBuilder hash =
                new StringBuilder(
                        workflowName
                                + WEBHOOK_DELIMITER
                                + workflowVersion
                                + WEBHOOK_DELIMITER
                                + taskReferenceName);
        for (String field : sortedFields) {
            hash.append(WEBHOOK_DELIMITER).append(expectedMatches.get(field));
        }
        return hash.toString();
    }

    private Map<String, Object> getAndValidateExpectedMatches(TaskModel task) {
        Map<String, Object> inputData = task.getInputData();
        @SuppressWarnings("unchecked")
        Map<String, Object> matches =
                inputData == null ? null : (Map<String, Object>) inputData.get("matches");
        if (matches == null) {
            throw new NonTransientException("Webhook task missing matches field");
        }
        return matches;
    }
}
