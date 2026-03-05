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
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;

@Slf4j
public class ExecutorUtils {

    public static Duration computePostpone(
            WorkflowModel workflowModel,
            Duration workflowOffsetTimeout,
            Duration maxPostponeDuration) {
        long currentTimeMillis = System.currentTimeMillis();
        long workflowOffsetTimeoutSeconds = workflowOffsetTimeout.getSeconds();
        long maxPostponeSeconds = maxPostponeDuration.getSeconds();

        Long postponeDurationSeconds = null;
        for (TaskModel taskModel : workflowModel.getTasks()) {
            Long candidateSeconds = null;
            if (taskModel.getStatus() == TaskModel.Status.IN_PROGRESS) {
                if (taskModel.getTaskType().equals(TASK_TYPE_WAIT)) {
                    if (taskModel.getWaitTimeout() == 0) {
                        candidateSeconds = workflowOffsetTimeoutSeconds;
                    } else {
                        long deltaInSeconds =
                                (taskModel.getWaitTimeout() - currentTimeMillis) / 1000;
                        candidateSeconds = (deltaInSeconds > 0) ? deltaInSeconds : 0;
                    }
                } else if (taskModel.getTaskType().equals(TaskType.TASK_TYPE_HUMAN)) {
                    candidateSeconds = workflowOffsetTimeoutSeconds;
                } else {
                    TaskDef taskDef = taskModel.getTaskDefinition().orElse(null);
                    long responseTimeoutSeconds =
                            taskDef != null
                                    ? taskDef.getResponseTimeoutSeconds()
                                    : taskModel.getResponseTimeoutSeconds();
                    if (responseTimeoutSeconds != 0) {
                        long elapsedSeconds =
                                Math.max(0, (currentTimeMillis - taskModel.getStartTime()) / 1000);
                        long remainingSeconds = responseTimeoutSeconds - elapsedSeconds + 1;
                        candidateSeconds = Math.max(0, remainingSeconds);
                    } else {
                        candidateSeconds = workflowOffsetTimeoutSeconds;
                    }
                }
            } else if (taskModel.getStatus() == TaskModel.Status.SCHEDULED) {
                TaskDef taskDef = taskModel.getTaskDefinition().orElse(null);
                if (taskDef != null
                        && taskDef.getPollTimeoutSeconds() != null
                        && taskDef.getPollTimeoutSeconds() != 0) {
                    candidateSeconds = taskDef.getPollTimeoutSeconds().longValue() + 1;
                } else {
                    long workflowTimeoutSeconds =
                            workflowModel.getWorkflowDefinition() != null
                                    ? workflowModel.getWorkflowDefinition().getTimeoutSeconds()
                                    : 0;
                    if (workflowTimeoutSeconds != 0) {
                        candidateSeconds = workflowTimeoutSeconds + 1;
                    } else {
                        candidateSeconds = workflowOffsetTimeoutSeconds;
                    }
                }
            }

            if (candidateSeconds == null) {
                continue;
            }
            if (candidateSeconds < 0) {
                candidateSeconds = 0L;
            }
            if (maxPostponeSeconds > 0 && candidateSeconds > maxPostponeSeconds) {
                candidateSeconds = maxPostponeSeconds;
            }
            if (postponeDurationSeconds == null || candidateSeconds < postponeDurationSeconds) {
                postponeDurationSeconds = candidateSeconds;
            }
        }

        long unackSeconds = (postponeDurationSeconds != null) ? postponeDurationSeconds : 0;
        log.trace(
                "postponeDurationSeconds calculated is {} and workflowOffsetTimeoutSeconds is {} for workflow {}",
                unackSeconds,
                workflowOffsetTimeoutSeconds,
                workflowModel.getWorkflowId());
        return Duration.ofSeconds(Math.max(0, unackSeconds));
    }
}
