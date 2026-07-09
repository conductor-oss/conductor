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
package org.conductoross.conductor.core.utils;

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
     * Applies dynamic rate limiting to a task model. Checks for runtime overrides supplied at
     * workflow start and applies them when present, otherwise falls back to the static values
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
     * <p>The by-def-name lookup prefers {@link TaskDef#getName()} when available because it is the
     * authoritative source for the task definition identifier. For system task types where no
     * {@link TaskDef} is registered, {@link WorkflowTask#getName()} (the user-chosen task name in
     * the workflow definition) is used as a fallback.
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

        int perFreq = taskDefinition != null ? taskDefinition.getRateLimitPerFrequency() : 0;
        int freqSecs = taskDefinition != null ? taskDefinition.getRateLimitFrequencyInSeconds() : 0;

        Map<String, TaskRateLimitOverride> overrides =
                Optional.ofNullable(workflowModel.getTaskRateLimitOverrides()).orElse(Map.of());

        String defName = taskDefinition != null ? taskDefinition.getName() : workflowTask.getName();
        TaskRateLimitOverride override =
                overrides.getOrDefault(workflowTask.getTaskReferenceName(), overrides.get(defName));

        if (override != null) {
            if (override.getRateLimitPerFrequency() != null) {
                perFreq = override.getRateLimitPerFrequency();
            }
            if (override.getRateLimitFrequencyInSeconds() != null) {
                freqSecs = override.getRateLimitFrequencyInSeconds();
            }
        }

        taskModel.setRateLimitPerFrequency(perFreq);
        taskModel.setRateLimitFrequencyInSeconds(freqSecs);
    }

    private TaskMapperUtils() {}
}
