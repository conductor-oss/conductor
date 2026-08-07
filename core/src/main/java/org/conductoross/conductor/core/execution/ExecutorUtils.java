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
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_DO_WHILE;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK_JOIN;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK_JOIN_DYNAMIC;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HUMAN;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;

@Slf4j
public class ExecutorUtils {

    private static boolean isActiveSubWorkflow(TaskModel taskModel) {
        return TaskType.TASK_TYPE_SUB_WORKFLOW.equals(taskModel.getTaskType())
                && (taskModel.getStatus() == TaskModel.Status.SCHEDULED
                        || taskModel.getStatus() == TaskModel.Status.IN_PROGRESS);
    }

    /**
     * Computes how long to postpone the next sweep/re-check of a workflow so that the scheduler
     * does not poll more frequently than necessary.
     *
     * <p>Special case first: if the only pending work is HUMAN task(s) (looking past a wrapping
     * {@code DO_WHILE}, and provided the definition has no FORK), the workflow is deferred by
     * {@link #humanOnlyPostpone} to the human's timeout deadline, or {@code maxPostponeDuration}
     * when the human has no timeout — it is waiting on a person, not the sweeper. Otherwise the
     * per-task algorithm below runs.
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

        // A workflow whose only pending work is HUMAN task(s) is waiting on a person, not the
        // sweeper: re-deciding every offset just churns the decider queue. Defer it to maxPostpone
        // instead. Unlike PR #1171, we keep the workflow IN the decider queue (deferred, not
        // removed) so the periodic re-sweep still recovers it if the completion event is ever
        // missed. Completion wakes it promptly regardless: updateTask() calls decide()
        // synchronously.
        Long humanOnlyPostpone = humanOnlyPostpone(workflowModel, maxPostponeSeconds);
        if (humanOnlyPostpone != null) {
            return Duration.ofSeconds(humanOnlyPostpone);
        }

        Long postponeDurationSeconds = null;
        for (TaskModel taskModel : workflowModel.getTasks()) {
            Long candidateSeconds = null;
            if (isActiveSubWorkflow(taskModel)) {
                // Sub-workflow progress is driven by Conductor's internal orchestration rather than
                // external worker polling or task-specific timeout signals. Revisit it on the
                // normal workflow offset so launch retries and child-completion observation
                // converge quickly.
                candidateSeconds = workflowOffsetTimeoutSeconds;
            } else if (taskModel.getStatus() == TaskModel.Status.IN_PROGRESS) {
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

    /**
     * When the only pending work is HUMAN task(s) waiting on a person (ignoring a wrapping {@code
     * DO_WHILE} loop), returns the seconds to defer the next sweep ({@code maxPostponeSeconds}).
     * Returns {@code null} the moment any other task is still pending, or when the definition
     * contains a FORK — in which case the caller keeps the normal offset cadence.
     *
     * <p>The FORK exclusion is deliberate and gated on the static definition, not the live task
     * list: a fork branch parked on a HUMAN can momentarily be the only pending leaf, and some
     * persistence layers can transiently omit the still-pending JOIN from the live task list
     * mid-decide. Deferring then would strip the branch-coordination re-sweep and strand the
     * workflow. The definition is race-free, so it is the safe thing to gate on.
     */
    static Long humanOnlyPostpone(WorkflowModel workflowModel, long maxPostponeSeconds) {
        if (maxPostponeSeconds <= 0) {
            return null; // no ceiling configured — cannot defer safely
        }
        long now = System.currentTimeMillis();
        boolean anyHuman = false;
        long postponeSeconds = maxPostponeSeconds;
        for (TaskModel taskModel : workflowModel.getTasks()) {
            TaskModel.Status status = taskModel.getStatus();
            if (status != TaskModel.Status.IN_PROGRESS && status != TaskModel.Status.SCHEDULED) {
                continue; // terminal — not pending
            }
            String taskType = taskModel.getTaskType();
            if (TASK_TYPE_HUMAN.equals(taskType)) {
                anyHuman = true;
                // Wake by the human's own timeout deadline so DeciderService.checkTaskTimeout can
                // enforce it on schedule; only a human with no timeout configured is deferred the
                // full maxPostpone (indefinite).
                long timeoutSeconds = humanTimeoutSeconds(taskModel, now);
                long candidateSeconds =
                        (timeoutSeconds > 0)
                                ? Math.min(timeoutSeconds, maxPostponeSeconds)
                                : maxPostponeSeconds;
                postponeSeconds = Math.min(postponeSeconds, candidateSeconds);
            } else if (TASK_TYPE_DO_WHILE.equals(taskType)) {
                // only wraps the running HUMAN leaf inside its loop
            } else {
                return null; // any other pending leaf (incl. FORK/JOIN, null type) — normal cadence
            }
        }
        if (!anyHuman || hasForkStructure(workflowModel)) {
            return null;
        }
        return postponeSeconds;
    }

    /**
     * Seconds until a HUMAN task's soonest configured timeout deadline (the OSS analog of an SLA),
     * derived from its {@link TaskDef} {@code timeoutSeconds}/{@code responseTimeoutSeconds} and
     * its start time. Returns {@code 0} when the task has no timeout configured (indefinite) or has
     * not started yet — matching {@code DeciderService.checkTaskTimeout}, which does not time out a
     * task with {@code timeoutSeconds <= 0} or {@code startTime <= 0}.
     */
    private static long humanTimeoutSeconds(TaskModel taskModel, long now) {
        TaskDef taskDef = taskModel.getTaskDefinition().orElse(null);
        if (taskDef == null || taskModel.getStartTime() <= 0) {
            return 0;
        }
        long deadlineSeconds = 0;
        if (taskDef.getTimeoutSeconds() > 0) {
            deadlineSeconds = taskDef.getTimeoutSeconds();
        }
        if (taskDef.getResponseTimeoutSeconds() > 0) {
            deadlineSeconds =
                    (deadlineSeconds == 0)
                            ? taskDef.getResponseTimeoutSeconds()
                            : Math.min(deadlineSeconds, taskDef.getResponseTimeoutSeconds());
        }
        if (deadlineSeconds == 0) {
            return 0; // no timeout configured — indefinite
        }
        long elapsedSeconds = Math.max(0, (now - taskModel.getStartTime()) / 1000);
        // +1 gives a one-second buffer past the deadline before re-evaluating; floor at 1 so an
        // already-overdue task wakes on the next sweep rather than immediately churning.
        return Math.max(1, deadlineSeconds - elapsedSeconds + 1);
    }

    private static boolean hasForkStructure(WorkflowModel workflowModel) {
        WorkflowDef workflowDef = workflowModel.getWorkflowDefinition();
        if (workflowDef == null) {
            return false;
        }
        for (WorkflowTask workflowTask : workflowDef.collectTasks()) {
            String type = workflowTask.getType();
            if (TASK_TYPE_FORK_JOIN.equals(type) || TASK_TYPE_FORK_JOIN_DYNAMIC.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
