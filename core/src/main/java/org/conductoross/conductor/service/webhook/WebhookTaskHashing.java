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
package org.conductoross.conductor.service.webhook;

import java.util.Map;
import java.util.TreeSet;

import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.model.TaskModel;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;

/**
 * Hash computation shared by every {@link WebhookTaskService} impl. Must be identical across impls
 * — tasks registered via one backing must be findable by hash via any other.
 *
 * <p>Match keys are sorted; each value's contribution is {@link Object#toString()}. Stable for
 * strings, numbers, and booleans. Not stable for nested {@link Map} values whose toString reflects
 * iteration order (HashMap, etc.). Mirrors the Orkes Redis impl verbatim; any change to value-side
 * canonicalization needs to land in Orkes too or OSS and Orkes deployments will compute different
 * hashes for the same input.
 */
public final class WebhookTaskHashing {

    private WebhookTaskHashing() {}

    public static String computeHash(TaskModel task, int workflowVersion) {
        // Validate matches first so a missing-matches NonTransientException isn't masked
        // by an NPE inside removeIterationFromTaskRefName when the task is incomplete.
        Map<String, Object> matches = requireMatches(task);
        return computeHash(
                task.getWorkflowType(),
                workflowVersion,
                TaskUtils.removeIterationFromTaskRefName(task.getReferenceTaskName()),
                matches);
    }

    public static String computeHash(
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

    @SuppressWarnings("unchecked")
    public static Map<String, Object> requireMatches(TaskModel task) {
        Map<String, Object> inputData = task.getInputData();
        Map<String, Object> matches =
                inputData == null ? null : (Map<String, Object>) inputData.get("matches");
        if (matches == null) {
            throw new NonTransientException("Webhook task missing matches field");
        }
        return matches;
    }

    /**
     * Like {@link #computeHash(TaskModel, int)} but returns {@code null} when the task has no
     * {@code matches} input instead of throwing. Use on cancel paths where the task may have been
     * cancelled before reaching IN_PROGRESS (and therefore before {@code put} was ever called).
     */
    @SuppressWarnings("unchecked")
    public static String computeHashIfPresent(TaskModel task, int workflowVersion) {
        Map<String, Object> inputData = task.getInputData();
        if (inputData == null || !inputData.containsKey("matches")) {
            return null;
        }
        Map<String, Object> matches = (Map<String, Object>) inputData.get("matches");
        if (matches == null) {
            return null;
        }
        return computeHash(
                task.getWorkflowType(),
                workflowVersion,
                TaskUtils.removeIterationFromTaskRefName(task.getReferenceTaskName()),
                matches);
    }
}
