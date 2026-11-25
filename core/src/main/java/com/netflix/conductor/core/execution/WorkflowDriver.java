/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.core.execution;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * Runs the steps of an execution that depend on what decides its next task. In a standard workflow
 * the definition decides ({@link StandardDriver}). In a native workflow the code decides, and adds
 * tasks to the definition as it runs ({@link NativeDriver}). The decider, the executor and the
 * sweeper get the driver of an execution from {@link WorkflowDrivers#of} and do not test which one
 * it is. The JOIN task is the exception: it tests the kind of workflow itself.
 */
public interface WorkflowDriver {

    // ------------------------------------------------------------------ start

    /** Sets up a new execution, before its first decide. */
    void initialize(WorkflowModel workflow);

    /**
     * The tasks to schedule at the start of an execution whose definition has no tasks.
     *
     * @throws TerminateWorkflowException when an execution with no tasks is complete at its start
     */
    List<TaskModel> startWithNoTasks();

    // ------------------------------------------------------------------ decide

    /** Marks a failed task that does not fail its workflow as COMPLETED_WITH_ERRORS. */
    void completeWithErrors(TaskModel task);

    /**
     * Sets the output of the execution from its tasks. Does nothing when the tasks do not decide
     * the output.
     *
     * @param task the task whose output is the output of the execution; null for the last task
     */
    void updateOutput(WorkflowModel workflow, TaskModel task);

    /**
     * Whether the execution is complete.
     *
     * @param tasksComplete the check of the decider that the tasks of the execution are complete
     */
    boolean isComplete(WorkflowModel workflow, BooleanSupplier tasksComplete);

    /** After a decide saved its tasks: lets the code of the workflow continue, if it has code. */
    void continueCode(WorkflowModel workflow);

    /**
     * Sets when the decider looks at the execution again. Changes nothing when the offset is the
     * timeout. A native execution with a continuation to run can only get a shorter time.
     *
     * @param offset the time the executor computed from the tasks of the execution
     * @param timeout the time the decider queue uses when no other time is set
     */
    void postponeDecide(WorkflowModel workflow, Duration offset, Duration timeout);

    /** Whether a running execution has stopped: nothing in it can make it continue. */
    boolean isStalled(WorkflowModel workflow);

    // ------------------------------------------------------------------ restart, retry, rerun

    /**
     * Puts a finished execution back at its beginning for a restart.
     *
     * @param definition the definition the restart found: the latest one, or the execution's own
     * @return the definition the execution now runs
     */
    WorkflowDef resetForRestart(
            WorkflowModel workflow, WorkflowDef definition, boolean useLatestDefinitions);

    /**
     * Retries a failed execution as far as the driver can.
     *
     * @return what is left for the executor to do
     */
    RetryOutcome retry(WorkflowModel workflow);

    /** Puts an execution back at its beginning for a rerun that names no task. */
    void resetForRerun(WorkflowModel workflow);

    /**
     * Checks the task that a rerun names as its start.
     *
     * @throws IllegalArgumentException when a rerun cannot start from the task
     */
    void checkCanRerunFrom(TaskModel rerunFrom);

    /**
     * Whether a rerun that starts from a SUB_WORKFLOW task resets that task in place and leaves the
     * other tasks of the execution as they are.
     */
    boolean rerunsSubWorkflowInPlace();

    /**
     * Removes what the execution did after the task a rerun starts from, and saves the change.
     *
     * @return the tasks the rerun opened again; the executor saves and queues them once the task
     *     the rerun starts from is saved
     */
    List<TaskModel> undoAfter(WorkflowModel workflow, TaskModel rerunFrom);

    /**
     * Makes the SUB_WORKFLOW task of a rerun ready to run again.
     *
     * @param taskInput the new input of the task; null to keep its input
     * @param executor the executor that starts a child workflow
     */
    void rerunSubWorkflowTask(
            WorkflowModel workflow,
            TaskModel task,
            Map<String, Object> taskInput,
            WorkflowExecutor executor);

    /** Whether a rerun runs the task again. */
    boolean runsAgainOnRerun(TaskModel task);

    /**
     * What is left for the executor to do after a driver retried an execution.
     *
     * @param next the step the executor does next
     * @param taskId the task to rerun from, for {@link Next#RERUN_FROM_TASK}; null otherwise
     */
    record RetryOutcome(Next next, String taskId) {

        enum Next {
            /** The executor schedules again each task that failed, timed out or was canceled. */
            RETRY_FAILED_TASKS,
            /** The executor reruns the execution from one task. */
            RERUN_FROM_TASK,
            /** The driver did the whole retry. */
            DONE
        }

        public static RetryOutcome retryFailedTasks() {
            return new RetryOutcome(Next.RETRY_FAILED_TASKS, null);
        }

        public static RetryOutcome rerunFrom(String taskId) {
            return new RetryOutcome(Next.RERUN_FROM_TASK, taskId);
        }

        public static RetryOutcome done() {
            return new RetryOutcome(Next.DONE, null);
        }
    }
}
