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

    /**
     * Computes how long to postpone the next sweep/re-check of a workflow so that the scheduler
     * does not poll more frequently than necessary.
     *
     * <p>Algorithm:
     *
     * <ol>
     *   <li>Iterate over every task in the workflow and derive a <em>candidate</em> postpone
     *       duration based on the task's status and type:
     *       <ul>
     *         <li><b>SCHEDULED</b> – the worker has not polled yet.
     *             <ul>
     *               <li>If the task definition has a non-zero {@code pollTimeoutSeconds}: candidate
     *                   = remaining seconds until the poll window expires ({@code
     *                   pollTimeoutSeconds - elapsedSecondsSinceScheduled + 1}), floored at 0.
     *               <li>Else if the workflow definition has a non-zero {@code timeoutSeconds}:
     *                   candidate = {@code workflowTimeoutSeconds + 1}.
     *               <li>Otherwise: candidate = {@code workflowOffsetTimeout}.
     *             </ul>
     *         <li><b>IN_PROGRESS / WAIT task</b>
     *             <ul>
     *               <li>{@code waitTimeout == 0} (indefinite wait): candidate = {@code
     *                   workflowOffsetTimeout}.
     *               <li>{@code waitTimeout > 0}: candidate = remaining milliseconds until {@code
     *                   waitTimeout} converted to seconds, floored at 0.
     *             </ul>
     *         <li><b>IN_PROGRESS / HUMAN task</b>: candidate = {@code workflowOffsetTimeout}.
     *         <li><b>IN_PROGRESS / all other tasks</b>
     *             <ul>
     *               <li>The effective {@code responseTimeoutSeconds} is the task definition value
     *                   when non-zero, otherwise the task model value (allowing workflow-task-level
     *                   overrides to be honoured even when the task def has no timeout).
     *               <li>If the effective {@code responseTimeoutSeconds} is non-zero: candidate =
     *                   {@code responseTimeoutSeconds - elapsedSeconds + 1}, floored at 0 (the +1
     *                   gives a one-second buffer past the deadline before re-evaluating).
     *               <li>Otherwise: candidate = {@code workflowOffsetTimeout}.
     *             </ul>
     *         <li><b>Any other status</b> (COMPLETED, FAILED, …): skipped — no candidate produced.
     *       </ul>
     *   <li>Each candidate is clamped to 0 if negative, then capped at {@code maxPostponeDuration}
     *       when {@code maxPostponeDuration > 0}.
     *   <li>The final postpone duration is the <em>minimum</em> of all candidates, so the workflow
     *       is re-checked as soon as the soonest task needs attention.
     *   <li>If no eligible tasks produced a candidate (e.g. all tasks are terminal), fall back to
     *       {@code workflowOffsetTimeout}.
     * </ol>
     *
     * @param workflowModel the workflow whose tasks are inspected
     * @param workflowOffsetTimeout default postpone used when no task-level deadline is available
     * @param maxPostponeDuration hard upper bound on the returned duration (ignored when zero or
     *     negative)
     * @return the duration to wait before the next workflow sweep, never negative
     */
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
                            (taskDef != null && taskDef.getResponseTimeoutSeconds() != 0)
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
                    long scheduledElapsedSeconds =
                            Math.max(0, (currentTimeMillis - taskModel.getScheduledTime()) / 1000);
                    long remainingPollSeconds =
                            taskDef.getPollTimeoutSeconds() - scheduledElapsedSeconds + 1;
                    candidateSeconds = Math.max(0, remainingPollSeconds);
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

        long unackSeconds =
                (postponeDurationSeconds != null)
                        ? postponeDurationSeconds
                        : workflowOffsetTimeoutSeconds;
        log.trace(
                "postponeDurationSeconds calculated is {} and workflowOffsetTimeoutSeconds is {} for workflow {}",
                unackSeconds,
                workflowOffsetTimeoutSeconds,
                workflowModel.getWorkflowId());
        return Duration.ofSeconds(Math.max(0, unackSeconds));
    }
}
