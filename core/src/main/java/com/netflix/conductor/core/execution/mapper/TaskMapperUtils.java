/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.core.execution.mapper;

import java.util.Map;
import java.util.Optional;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest.TaskRateLimitOverride;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/** Utility methods for task mappers to avoid code duplication. */
public class TaskMapperUtils {

    /**
     * Applies dynamic rate limiting to a task model. This method checks for runtime overrides in
     * the workflow model and applies them if present, otherwise falls back to the static values
     * defined in the task definition.
     *
     * <p>The lookup order for overrides is:
     *
     * <ol>
     *   <li>By task reference name (most specific, applies to this task instance only)
     *   <li>By task definition name (applies to all instances of this task type)
     *   <li>Fallback to static TaskDef values (default behavior)
     * </ol>
     *
     * @param workflowModel the workflow model containing potential rate limit overrides
     * @param workflowTask the workflow task definition
     * @param taskDefinition the task definition containing default rate limit values
     * @param taskModel the task model to apply rate limits to
     */
    public static void applyRateLimits(
            WorkflowModel workflowModel,
            WorkflowTask workflowTask,
            TaskDef taskDefinition,
            TaskModel taskModel) {

        // Start with defaults from TaskDef
        int perFreq = taskDefinition != null ? taskDefinition.getRateLimitPerFrequency() : 0;
        int freqSecs = taskDefinition != null ? taskDefinition.getRateLimitFrequencyInSeconds() : 0;

        // Get overrides from WorkflowModel (if any)
        Map<String, TaskRateLimitOverride> overrides =
                Optional.ofNullable(workflowModel.getTaskRateLimitOverrides()).orElse(Map.of());

        // Look up override by reference name first, then by task name
        TaskRateLimitOverride override =
                overrides.getOrDefault(
                        workflowTask.getTaskReferenceName(), overrides.get(workflowTask.getName()));

        // Apply override values if present
        if (override != null) {
            if (override.getRateLimitPerFrequency() != null) {
                perFreq = override.getRateLimitPerFrequency();
            }
            if (override.getRateLimitFrequencyInSeconds() != null) {
                freqSecs = override.getRateLimitFrequencyInSeconds();
            }
        }

        // Set the final values on the TaskModel
        taskModel.setRateLimitPerFrequency(perFreq);
        taskModel.setRateLimitFrequencyInSeconds(freqSecs);
    }

    // Private constructor to prevent instantiation
    private TaskMapperUtils() {}
}
