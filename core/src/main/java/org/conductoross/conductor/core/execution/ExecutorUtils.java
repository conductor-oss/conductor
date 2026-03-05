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
package org.conductoross.conductor.core.execution;

import java.time.Duration;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;

@Slf4j
public class ExecutorUtils {

    public static Duration computePostpone(
            WorkflowModel workflowModel, Duration workflowOffsetTimeout) {
        long currentTimeMillis = System.currentTimeMillis();
        long workflowOffsetTimeoutSeconds = workflowOffsetTimeout.getSeconds();

        long postponeDurationSeconds = 0;
        for (TaskModel taskModel : workflowModel.getTasks()) {
            TaskDef taskDef = taskModel.getTaskDefinition().orElse(null);
            if (taskModel.getStatus() == TaskModel.Status.IN_PROGRESS) {
                if (taskModel.getTaskType().equals(TASK_TYPE_WAIT)) {
                    if (taskModel.getWaitTimeout() != 0) {
                        long deltaInSeconds =
                                (taskModel.getWaitTimeout() - currentTimeMillis) / 1000;
                        postponeDurationSeconds = (deltaInSeconds > 0) ? deltaInSeconds : 0;
                    }
                } else {
                    // Could taskModel.getResponseTimeoutSeconds() be set and no taskDef?...
                    // not sure but keeping it this way just in case.
                    long responseTimeoutSeconds =
                            taskDef != null
                                    ? taskDef.getResponseTimeoutSeconds()
                                    : taskModel.getResponseTimeoutSeconds();
                    if (responseTimeoutSeconds != 0) {
                        long deltaInSeconds = (currentTimeMillis - taskModel.getStartTime()) / 1000;
                        if (deltaInSeconds > 0 && (responseTimeoutSeconds - deltaInSeconds) >= 0) {
                            postponeDurationSeconds = responseTimeoutSeconds - deltaInSeconds + 1;
                        }
                    }
                }
                break;
            } else if (taskModel.getStatus() == TaskModel.Status.SCHEDULED) {
                if (taskDef != null) {
                    postponeDurationSeconds = getMinTimeout(taskDef);
                }

                if (postponeDurationSeconds <= 0) {
                    if (workflowModel.getWorkflowDefinition().getTimeoutSeconds() > 0) {
                        postponeDurationSeconds =
                                workflowModel.getWorkflowDefinition().getTimeoutSeconds() + 1;
                    }
                }
                break;
            }
        }
        log.trace(
                "postponeDurationSeconds calculated is {} and workflowOffsetTimeoutSeconds is {} for workflow {}",
                postponeDurationSeconds,
                workflowOffsetTimeoutSeconds,
                workflowModel.getWorkflowId());

        if (postponeDurationSeconds > 0) {
            return Duration.ofSeconds(
                    Math.min(postponeDurationSeconds, workflowOffsetTimeoutSeconds));
        }
        return Duration.ofSeconds(workflowOffsetTimeoutSeconds);
    }

    private static long getMinTimeout(TaskDef taskDef) {
        long pollTimeoutSeconds =
                taskDef.getPollTimeoutSeconds() != null ? taskDef.getPollTimeoutSeconds() : 0;
        return Math.min(
                taskDef.getTimeoutSeconds(),
                Math.min(taskDef.getResponseTimeoutSeconds(), pollTimeoutSeconds));
    }
}
