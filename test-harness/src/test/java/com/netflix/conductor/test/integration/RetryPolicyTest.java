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
package com.netflix.conductor.test.integration;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.test.base.AbstractSpecification;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for retry policy features: - maxRetryDelaySeconds: caps the computed
 * backoff delay so it never exceeds a configured ceiling - backoffJitterMs: adds a random
 * millisecond offset in [0, maxJitterMs] to each retry delay, spreading retries to prevent
 * thundering-herd storms
 */
class RetryPolicyTest extends AbstractSpecification {

    @Autowired private ExecutionDAOFacade executionDAOFacade;

    private static final String TASK_NAME = "retry_policy_task";
    private static final String WORKFLOW_NAME = "retry_policy_wf";

    @BeforeEach
    void setup() {
        WorkflowTask wfTask = new WorkflowTask();
        wfTask.setName(TASK_NAME);
        wfTask.setTaskReferenceName("retry_task_ref");
        wfTask.setType(TaskType.SIMPLE.name());

        WorkflowDef wfDef = new WorkflowDef();
        wfDef.setName(WORKFLOW_NAME);
        wfDef.setVersion(1);
        wfDef.setTasks(List.of(wfTask));
        wfDef.setOwnerEmail("test@example.com");
        wfDef.setTimeoutSeconds(3600);
        wfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        metadataService.registerWorkflowDef(wfDef);
    }

    @AfterEach
    void cleanupDefs() {
        try {
            metadataService.unregisterWorkflowDef(WORKFLOW_NAME, 1);
        } catch (Exception ignored) {
        }
        try {
            metadataService.unregisterTaskDef(TASK_NAME);
        } catch (Exception ignored) {
        }
    }

    /**
     * Poll until the next SCHEDULED task is available to be polled (handles callbackAfter delays).
     */
    private Task pollUntilAvailable() {
        Task[] polled = {null};
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            polled[0] = workflowExecutionService.poll(TASK_NAME, "test-worker");
                            return polled[0] != null;
                        });
        return polled[0];
    }

    private static TaskResult failedResult(Task task) {
        TaskResult r = new TaskResult(task);
        r.setStatus(TaskResult.Status.FAILED);
        return r;
    }

    // -------------------------------------------------------------------------
    // maxRetryDelaySeconds
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Exponential backoff delay is capped by maxRetryDelaySeconds")
    void exponentialBackoffDelayIsCappedByMaxRetryDelaySeconds() throws Exception {
        // given: A task def with exponential backoff, base delay 2 s, cap 3 s
        // retry 0: 2 * 2^0 = 2 s (below cap); retry 1: 2 * 2^1 = 4 s → capped to 3 s
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(5);
        taskDef.setRetryDelaySeconds(2);
        taskDef.setRetryLogic(TaskDef.RetryLogic.EXPONENTIAL_BACKOFF);
        taskDef.setMaxRetryDelaySeconds(3);
        taskDef.setTimeoutSeconds(3600);
        taskDef.setResponseTimeoutSeconds(3600);
        metadataService.registerTaskDef(List.of(taskDef));

        // when: Start the workflow and fail the task (retry 0 — raw = 2 s, below cap)
        String wfId = startWorkflow(WORKFLOW_NAME, 1, "retry-cap-exp", new HashMap<>(), null);
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: Rescheduled task (retry 0) has callbackAfterSeconds = 2 (below the 3 s cap)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Task rescheduled =
                                    workflowExecutionService
                                            .getExecutionStatus(wfId, true)
                                            .getTasks()
                                            .stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .findFirst()
                                            .orElse(null);
                            return rescheduled != null
                                    && rescheduled.getCallbackAfterSeconds() == 2;
                        });

        Task rescheduled0 =
                workflowExecutionService.getExecutionStatus(wfId, true).getTasks().stream()
                        .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        assertNotNull(rescheduled0);
        assertEquals(2, rescheduled0.getCallbackAfterSeconds());

        // when: Fail the task again (retry 1 — raw = 4 s, above cap)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: Rescheduled task (retry 1) has callbackAfterSeconds = 3 (capped from 4 s)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            List<Task> tasks =
                                    workflowExecutionService
                                            .getExecutionStatus(wfId, true)
                                            .getTasks();
                            List<Task> scheduled =
                                    tasks.stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .toList();
                            if (scheduled.isEmpty()) return false;
                            Task last = scheduled.get(scheduled.size() - 1);
                            return last.getCallbackAfterSeconds() == 3;
                        });

        List<Task> tasks1 = workflowExecutionService.getExecutionStatus(wfId, true).getTasks();
        List<Task> scheduled1 =
                tasks1.stream().filter(t -> t.getStatus() == Task.Status.SCHEDULED).toList();
        assertFalse(scheduled1.isEmpty());
        Task rescheduled1 = scheduled1.get(scheduled1.size() - 1);
        assertNotNull(rescheduled1);
        assertEquals(3, rescheduled1.getCallbackAfterSeconds());
    }

    @Test
    @DisplayName("Linear backoff delay is capped by maxRetryDelaySeconds")
    void linearBackoffDelayIsCappedByMaxRetryDelaySeconds() throws Exception {
        // given: A task def with linear backoff (scale=2), base delay 2 s, cap 5 s
        // retry 0: 2 * 2 * 1 = 4 s (below cap 5 s); retry 1: 2 * 2 * 2 = 8 s → capped to 5 s
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(5);
        taskDef.setRetryDelaySeconds(2);
        taskDef.setRetryLogic(TaskDef.RetryLogic.LINEAR_BACKOFF);
        taskDef.setBackoffScaleFactor(2);
        taskDef.setMaxRetryDelaySeconds(5);
        taskDef.setTimeoutSeconds(3600);
        taskDef.setResponseTimeoutSeconds(3600);
        metadataService.registerTaskDef(List.of(taskDef));

        // when: Start the workflow and fail the task (retry 0 — raw = 4 s, below cap)
        String wfId = startWorkflow(WORKFLOW_NAME, 1, "retry-cap-lin", new HashMap<>(), null);
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: Rescheduled task has callbackAfterSeconds = 4 (below cap)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Task rescheduled =
                                    workflowExecutionService
                                            .getExecutionStatus(wfId, true)
                                            .getTasks()
                                            .stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .findFirst()
                                            .orElse(null);
                            return rescheduled != null
                                    && rescheduled.getCallbackAfterSeconds() == 4;
                        });

        Task rescheduled0 =
                workflowExecutionService.getExecutionStatus(wfId, true).getTasks().stream()
                        .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        assertNotNull(rescheduled0);
        assertEquals(4, rescheduled0.getCallbackAfterSeconds());

        // when: Fail the task again (retry 1 — raw = 8 s, above cap)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: Rescheduled task has callbackAfterSeconds = 5 (capped from 8 s)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            List<Task> tasks =
                                    workflowExecutionService
                                            .getExecutionStatus(wfId, true)
                                            .getTasks();
                            List<Task> scheduled =
                                    tasks.stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .toList();
                            if (scheduled.isEmpty()) return false;
                            Task last = scheduled.get(scheduled.size() - 1);
                            return last.getCallbackAfterSeconds() == 5;
                        });

        List<Task> tasks1 = workflowExecutionService.getExecutionStatus(wfId, true).getTasks();
        List<Task> scheduled1 =
                tasks1.stream().filter(t -> t.getStatus() == Task.Status.SCHEDULED).toList();
        assertFalse(scheduled1.isEmpty());
        Task rescheduled1 = scheduled1.get(scheduled1.size() - 1);
        assertNotNull(rescheduled1);
        assertEquals(5, rescheduled1.getCallbackAfterSeconds());
    }

    // -------------------------------------------------------------------------
    // backoffJitterMs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("backoffJitterMs adds a random offset in [0, maxJitterMs] to the retry delay")
    void backoffJitterMsAddsRandomOffsetToRetryDelay() throws Exception {
        // given: A task def with fixed retry (60 s) and 5000 ms of jitter
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(3);
        taskDef.setRetryDelaySeconds(60);
        taskDef.setRetryLogic(TaskDef.RetryLogic.FIXED);
        taskDef.setBackoffJitterMs(5000);
        taskDef.setTimeoutSeconds(3600);
        taskDef.setResponseTimeoutSeconds(3600);
        metadataService.registerTaskDef(List.of(taskDef));

        // when: Start the workflow and fail the task once
        String wfId = startWorkflow(WORKFLOW_NAME, 1, "jitter-test", new HashMap<>(), null);
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: callbackAfterSeconds stays at the base delay (60)
        // and: callbackAfterMs on the TaskModel is in [60_000, 65_000] — base + up to maxJitterMs
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Task rescheduled =
                                    workflowExecutionService
                                            .getExecutionStatus(wfId, true)
                                            .getTasks()
                                            .stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .findFirst()
                                            .orElse(null);
                            if (rescheduled == null) return false;
                            if (rescheduled.getCallbackAfterSeconds() != 60) return false;
                            TaskModel taskModel =
                                    executionDAOFacade.getTaskModel(rescheduled.getTaskId());
                            return taskModel.getCallbackAfterMs() >= 60_000
                                    && taskModel.getCallbackAfterMs() <= 65_000;
                        });

        Task rescheduled =
                workflowExecutionService.getExecutionStatus(wfId, true).getTasks().stream()
                        .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        assertNotNull(rescheduled);
        assertEquals(60, rescheduled.getCallbackAfterSeconds());
        TaskModel taskModel = executionDAOFacade.getTaskModel(rescheduled.getTaskId());
        assertTrue(taskModel.getCallbackAfterMs() >= 60_000);
        assertTrue(taskModel.getCallbackAfterMs() <= 65_000);
    }

    @Test
    @DisplayName(
            "backoffJitterMs of zero produces no jitter — callbackAfterMs equals base delay exactly")
    void backoffJitterMsOfZeroProducesNoJitter() throws Exception {
        // given: A task def with zero jitter
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(3);
        taskDef.setRetryDelaySeconds(30);
        taskDef.setRetryLogic(TaskDef.RetryLogic.FIXED);
        taskDef.setBackoffJitterMs(0);
        taskDef.setTimeoutSeconds(3600);
        taskDef.setResponseTimeoutSeconds(3600);
        metadataService.registerTaskDef(List.of(taskDef));

        // when:
        String wfId = startWorkflow(WORKFLOW_NAME, 1, "no-jitter-test", new HashMap<>(), null);
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: callbackAfterMs is exactly 30_000 (no jitter added)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Task rescheduled =
                                    workflowExecutionService
                                            .getExecutionStatus(wfId, true)
                                            .getTasks()
                                            .stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .findFirst()
                                            .orElse(null);
                            if (rescheduled == null) return false;
                            TaskModel taskModel =
                                    executionDAOFacade.getTaskModel(rescheduled.getTaskId());
                            return taskModel.getCallbackAfterMs() == 30_000;
                        });

        Task rescheduled =
                workflowExecutionService.getExecutionStatus(wfId, true).getTasks().stream()
                        .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        assertNotNull(rescheduled);
        TaskModel taskModel = executionDAOFacade.getTaskModel(rescheduled.getTaskId());
        assertEquals(30_000L, taskModel.getCallbackAfterMs());
    }

    // -------------------------------------------------------------------------
    // cap + jitter combined
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "Cap is applied before jitter — callbackAfterMs is in [cap_ms, cap_ms + maxJitterMs]")
    void capIsAppliedBeforeJitter() throws Exception {
        // given: Exponential backoff: base=2s, cap=3s, jitter up to 2000ms
        // retry 0: raw=2s, below cap; retry 1: raw=4s → capped to 3s; callbackAfterMs in [3000,
        // 5000]
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(5);
        taskDef.setRetryDelaySeconds(2);
        taskDef.setRetryLogic(TaskDef.RetryLogic.EXPONENTIAL_BACKOFF);
        taskDef.setMaxRetryDelaySeconds(3);
        taskDef.setBackoffJitterMs(2000);
        taskDef.setTimeoutSeconds(3600);
        taskDef.setResponseTimeoutSeconds(3600);
        metadataService.registerTaskDef(List.of(taskDef));

        // when: Start workflow and fail the task (retry 0 — raw=2s, below cap)
        String wfId = startWorkflow(WORKFLOW_NAME, 1, "cap-jitter-test", new HashMap<>(), null);
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // wait for retry 0 to be rescheduled before polling again
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                        .getExecutionStatus(wfId, true)
                                        .getTasks()
                                        .stream()
                                        .anyMatch(t -> t.getStatus() == Task.Status.SCHEDULED));

        // and: Fail again (retry 1 — raw=4s → capped to 3s)
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: callbackAfterSeconds = 3 (the cap) and callbackAfterMs is in [3_000, 5_000]
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            List<Task> tasks =
                                    workflowExecutionService
                                            .getExecutionStatus(wfId, true)
                                            .getTasks();
                            List<Task> scheduled =
                                    tasks.stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .toList();
                            if (scheduled.isEmpty()) return false;
                            Task last = scheduled.get(scheduled.size() - 1);
                            if (last.getCallbackAfterSeconds() != 3) return false;
                            TaskModel taskModel = executionDAOFacade.getTaskModel(last.getTaskId());
                            return taskModel.getCallbackAfterMs() >= 3_000
                                    && taskModel.getCallbackAfterMs() <= 5_000;
                        });

        List<Task> finalTasks = workflowExecutionService.getExecutionStatus(wfId, true).getTasks();
        List<Task> finalScheduled =
                finalTasks.stream().filter(t -> t.getStatus() == Task.Status.SCHEDULED).toList();
        assertFalse(finalScheduled.isEmpty());
        Task last = finalScheduled.get(finalScheduled.size() - 1);
        assertNotNull(last);
        assertEquals(3, last.getCallbackAfterSeconds());
        TaskModel taskModel = executionDAOFacade.getTaskModel(last.getTaskId());
        assertTrue(taskModel.getCallbackAfterMs() >= 3_000);
        assertTrue(taskModel.getCallbackAfterMs() <= 5_000);
    }

    // =========================================================================
    // totalTimeoutSeconds
    // =========================================================================

    @Test
    @DisplayName("totalTimeoutSeconds=0 is disabled — retries continue as normal")
    void totalTimeoutSecondsZeroIsDisabled() throws Exception {
        // given:
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(5);
        taskDef.setRetryDelaySeconds(0);
        taskDef.setRetryLogic(TaskDef.RetryLogic.FIXED);
        taskDef.setTotalTimeoutSeconds(0); // disabled
        taskDef.setTimeoutSeconds(3600);
        taskDef.setResponseTimeoutSeconds(3600);
        metadataService.registerTaskDef(List.of(taskDef));

        // when:
        String wfId =
                startWorkflow(WORKFLOW_NAME, 1, "total-timeout-disabled", new HashMap<>(), null);
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: retry is scheduled (total timeout disabled)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowExecutionService.getExecutionStatus(wfId, true);
                            return wf.getTasks().size() >= 2
                                    && wf.getTasks().get(wf.getTasks().size() - 1).getStatus()
                                            == Task.Status.SCHEDULED;
                        });

        Workflow wf = workflowExecutionService.getExecutionStatus(wfId, true);
        assertTrue(wf.getTasks().size() >= 2);
        assertEquals(
                Task.Status.SCHEDULED, wf.getTasks().get(wf.getTasks().size() - 1).getStatus());
    }

    @Test
    @DisplayName("totalTimeoutSeconds exceeded on retry — workflow fails with no further retries")
    void totalTimeoutSecondsExceededOnRetry() throws Exception {
        // given: A task with retries allowed but total budget of 5 s
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(10);
        taskDef.setRetryDelaySeconds(0);
        taskDef.setRetryLogic(TaskDef.RetryLogic.FIXED);
        taskDef.setTotalTimeoutSeconds(5);
        taskDef.setTimeoutSeconds(
                0); // disabled so constraint (timeoutSeconds <= totalTimeout) passes
        taskDef.setResponseTimeoutSeconds(1);
        taskDef.setTimeoutPolicy(TaskDef.TimeoutPolicy.TIME_OUT_WF);
        metadataService.registerTaskDef(List.of(taskDef));

        // when: Start the workflow, burn some time, then fail the task
        String wfId =
                startWorkflow(WORKFLOW_NAME, 1, "total-timeout-exceeded", new HashMap<>(), null);

        // Retrieve the internal task model and backdate firstScheduledTime to exceed the budget
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                        .getExecutionStatus(wfId, true)
                                        .getTasks()
                                        .stream()
                                        .anyMatch(t -> t.getStatus() == Task.Status.SCHEDULED));

        Task scheduled = workflowExecutionService.getExecutionStatus(wfId, true).getTasks().get(0);
        TaskModel taskModel = executionDAOFacade.getTaskModel(scheduled.getTaskId());
        taskModel.setFirstScheduledTime(System.currentTimeMillis() - 60_000); // 60s ago > 5s budget
        executionDAOFacade.updateTask(taskModel);

        // Now fail the task — retry() should detect total timeout exceeded and terminate workflow
        workflowExecutionService.updateTask(failedResult(pollUntilAvailable()));

        // then: Workflow fails (total timeout exceeded, no more retries)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowExecutionService.getExecutionStatus(wfId, false);
                            return wf.getStatus() == Workflow.WorkflowStatus.TIMED_OUT
                                    || wf.getStatus() == Workflow.WorkflowStatus.FAILED;
                        });

        Workflow wf = workflowExecutionService.getExecutionStatus(wfId, false);
        assertTrue(
                wf.getStatus() == Workflow.WorkflowStatus.TIMED_OUT
                        || wf.getStatus() == Workflow.WorkflowStatus.FAILED);
    }

    @Test
    @DisplayName(
            "totalTimeoutSeconds exceeded in checkTotalTimeout — IN_PROGRESS task is timed out")
    void totalTimeoutSecondsExceededInProgressTaskIsTimedOut() throws Exception {
        // given: A task with RETRY timeout policy and a 5 s total budget
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setRetryCount(10);
        taskDef.setRetryDelaySeconds(0);
        taskDef.setRetryLogic(TaskDef.RetryLogic.FIXED);
        taskDef.setTotalTimeoutSeconds(5);
        taskDef.setTimeoutSeconds(0); // disabled so constraint passes
        taskDef.setResponseTimeoutSeconds(1);
        taskDef.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        metadataService.registerTaskDef(List.of(taskDef));

        // when: Start the workflow, poll the task (IN_PROGRESS), then backdate firstScheduledTime
        String wfId =
                startWorkflow(WORKFLOW_NAME, 1, "total-timeout-inprogress", new HashMap<>(), null);
        Task polled = pollUntilAvailable();

        // Backdate firstScheduledTime on the IN_PROGRESS task to exceed total budget
        TaskModel taskModel = executionDAOFacade.getTaskModel(polled.getTaskId());
        taskModel.setFirstScheduledTime(System.currentTimeMillis() - 60_000);
        executionDAOFacade.updateTask(taskModel);

        // Trigger a decide cycle (sweep) to fire checkTotalTimeout
        sweep(wfId);

        // then: Task is TIMED_OUT by checkTotalTimeout (RETRY policy sets status, doesn't terminate
        // wf yet)
        await().atMost(30, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowExecutionService.getExecutionStatus(wfId, true);
                            Task timedOutTask =
                                    wf.getTasks().stream()
                                            .filter(t -> t.getTaskId().equals(polled.getTaskId()))
                                            .findFirst()
                                            .orElse(null);
                            return timedOutTask != null
                                    && timedOutTask.getStatus() == Task.Status.TIMED_OUT;
                        });

        Workflow wf = workflowExecutionService.getExecutionStatus(wfId, true);
        Task timedOutTask =
                wf.getTasks().stream()
                        .filter(t -> t.getTaskId().equals(polled.getTaskId()))
                        .findFirst()
                        .orElse(null);
        assertNotNull(timedOutTask);
        assertEquals(Task.Status.TIMED_OUT, timedOutTask.getStatus());
    }
}
