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
package com.netflix.conductor.core.reconciliation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestWorkflowSweeper {

    private ConductorProperties properties;
    private WorkflowExecutor workflowExecutor;
    private WorkflowRepairService workflowRepairService;
    private QueueDAO queueDAO;
    private ExecutionDAOFacade executionDAOFacade;
    private WorkflowSweeper workflowSweeper;
    private ExecutionLockService executionLockService;

    private int defaultPostPoneOffSetSeconds = 1800;
    private int defaulMmaxPostponeDurationSeconds = 2000000;

    @Before
    public void setUp() {
        properties = mock(ConductorProperties.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        queueDAO = mock(QueueDAO.class);
        workflowRepairService = mock(WorkflowRepairService.class);
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        executionLockService = mock(ExecutionLockService.class);
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(defaulMmaxPostponeDurationSeconds));
        workflowSweeper =
                new WorkflowSweeper(
                        workflowExecutor,
                        Optional.of(workflowRepairService),
                        properties,
                        queueDAO,
                        executionDAOFacade,
                        executionLockService);
    }

    @Test
    public void testPostponeDurationForHumanTaskType() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_HUMAN);
        taskModel.setStatus(Status.IN_PROGRESS);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(defaulMmaxPostponeDurationSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        defaultPostPoneOffSetSeconds * 1000);
    }

    @Test
    public void testPostponeDurationForWaitTaskType() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_WAIT);
        taskModel.setStatus(Status.IN_PROGRESS);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(defaulMmaxPostponeDurationSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        defaultPostPoneOffSetSeconds * 1000);
    }

    @Test
    public void testPostponeDurationForWaitTaskTypeWithLongWaitTime() {
        long waitTimeout = 65845;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_WAIT);
        taskModel.setStatus(Status.IN_PROGRESS);
        taskModel.setWaitTimeout(System.currentTimeMillis() + waitTimeout);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(defaulMmaxPostponeDurationSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (waitTimeout / 1000) * 1000);
    }

    @Test
    public void testPostponeDurationForWaitTaskTypeWithLessOneSecondWaitTime() {
        long waitTimeout = 180;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_WAIT);
        taskModel.setStatus(Status.IN_PROGRESS);
        taskModel.setWaitTimeout(System.currentTimeMillis() + waitTimeout);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (waitTimeout / 1000) * 1000);
    }

    @Test
    public void testPostponeDurationForWaitTaskTypeWithZeroWaitTime() {
        long waitTimeout = 0;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_WAIT);
        taskModel.setStatus(Status.IN_PROGRESS);
        taskModel.setWaitTimeout(System.currentTimeMillis() + waitTimeout);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (waitTimeout / 1000) * 1000);
    }

    @Test
    public void testPostponeDurationForTaskInProgress() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        taskModel.setStatus(Status.IN_PROGRESS);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(defaulMmaxPostponeDurationSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        defaultPostPoneOffSetSeconds * 1000);
    }

    @Test
    public void testPostponeDurationForTaskInProgressWithResponseTimeoutSet() {
        long responseTimeout = 200;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        taskModel.setStatus(Status.IN_PROGRESS);
        taskModel.setResponseTimeoutSeconds(responseTimeout);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(defaulMmaxPostponeDurationSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (responseTimeout + 1) * 1000);
    }

    @Test
    public void
            testPostponeDurationForTaskInProgressWithResponseTimeoutSetLongerThanMaxPostponeDuration() {
        long responseTimeout = defaulMmaxPostponeDurationSeconds + 1;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        taskModel.setStatus(Status.IN_PROGRESS);
        taskModel.setResponseTimeoutSeconds(responseTimeout);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(defaulMmaxPostponeDurationSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        defaulMmaxPostponeDurationSeconds * 1000L);
    }

    @Test
    public void testPostponeDurationForTaskInScheduled() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowModel.setWorkflowDefinition(workflowDef);
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        taskModel.setStatus(Status.SCHEDULED);
        taskModel.setReferenceTaskName("task1");
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        defaultPostPoneOffSetSeconds * 1000);
    }

    @Test
    public void testPostponeDurationForTaskInScheduledWithWorkflowTimeoutSet() {
        long workflowTimeout = 1800;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setTimeoutSeconds(workflowTimeout);
        workflowModel.setWorkflowDefinition(workflowDef);
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        taskModel.setStatus(Status.SCHEDULED);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (workflowTimeout + 1) * 1000);
    }

    @Test
    public void testPostponeDurationForTaskInScheduledWithWorkflowTimeoutSetAndNoPollTimeout() {
        long workflowTimeout = 1800;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setTimeoutSeconds(workflowTimeout);
        workflowModel.setWorkflowDefinition(workflowDef);
        TaskDef taskDef = new TaskDef();
        TaskModel taskModel = mock(TaskModel.class);
        workflowModel.setTasks(List.of(taskModel));
        when(taskModel.getTaskDefinition()).thenReturn(Optional.of(taskDef));
        when(taskModel.getStatus()).thenReturn(Status.SCHEDULED);
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (workflowTimeout + 1) * 1000);
    }

    @Test
    public void testPostponeDurationForTaskInScheduledWithNoWorkflowTimeoutSetAndNoPollTimeout() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowModel.setWorkflowDefinition(workflowDef);
        TaskDef taskDef = new TaskDef();
        TaskModel taskModel = mock(TaskModel.class);
        workflowModel.setTasks(List.of(taskModel));
        when(taskModel.getTaskDefinition()).thenReturn(Optional.of(taskDef));
        when(taskModel.getStatus()).thenReturn(Status.SCHEDULED);
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        defaultPostPoneOffSetSeconds * 1000);
    }

    @Test
    public void testPostponeDurationForTaskInScheduledWithNoPollTimeoutSet() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskDef taskDef = new TaskDef();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowModel.setWorkflowDefinition(workflowDef);
        TaskModel taskModel = mock(TaskModel.class);
        workflowModel.setTasks(List.of(taskModel));
        when(taskModel.getStatus()).thenReturn(Status.SCHEDULED);
        when(taskModel.getTaskDefinition()).thenReturn(Optional.of(taskDef));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        defaultPostPoneOffSetSeconds * 1000);
    }

    @Test
    public void testPostponeDurationForTaskInScheduledWithPollTimeoutSet() {
        int pollTimeout = 200;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskDef taskDef = new TaskDef();
        taskDef.setPollTimeoutSeconds(pollTimeout);
        TaskModel taskModel = mock(TaskModel.class);
        ;
        workflowModel.setTasks(List.of(taskModel));
        when(taskModel.getStatus()).thenReturn(Status.SCHEDULED);
        when(taskModel.getTaskDefinition()).thenReturn(Optional.of(taskDef));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (pollTimeout + 1) * 1000);
    }

    @Test
    public void testPostponeDurationChoosesMinimumAcrossTasks() {
        long responseTimeout = 500;
        int pollTimeout = 120;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskModel inProgressTask = new TaskModel();
        inProgressTask.setTaskId("task1");
        inProgressTask.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        inProgressTask.setStatus(Status.IN_PROGRESS);
        inProgressTask.setResponseTimeoutSeconds(responseTimeout);
        TaskDef taskDef = new TaskDef();
        taskDef.setPollTimeoutSeconds(pollTimeout);
        TaskModel scheduledTask = mock(TaskModel.class);
        when(scheduledTask.getStatus()).thenReturn(Status.SCHEDULED);
        when(scheduledTask.getTaskDefinition()).thenReturn(Optional.of(taskDef));
        workflowModel.setTasks(List.of(inProgressTask, scheduledTask));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));

        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);

        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), (pollTimeout + 1) * 1000L);
    }

    @Test
    public void testPostponeDurationForScheduledTaskCappedByMaxPostpone() {
        int pollTimeout = 1000;
        int maxPostponeSeconds = 100;
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        TaskDef taskDef = new TaskDef();
        taskDef.setPollTimeoutSeconds(pollTimeout);
        TaskModel taskModel = mock(TaskModel.class);
        when(taskModel.getStatus()).thenReturn(Status.SCHEDULED);
        when(taskModel.getTaskDefinition()).thenReturn(Optional.of(taskDef));
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(defaultPostPoneOffSetSeconds));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(maxPostponeSeconds));

        workflowSweeper.unack(workflowModel, defaultPostPoneOffSetSeconds);

        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE, workflowModel.getWorkflowId(), maxPostponeSeconds * 1000L);
    }

    /*
     * Reproduction of the CloudAware / Mikhail RegressWorkflow ~10-minute pause reported after
     * upgrading Conductor 3.21.4 -> 3.30.2 (Spanner persistence, Redis queue + locks).
     *
     * IMPORTANT — code-path scope: this exercises the LEGACY sweeper
     * (com.netflix.conductor.core.reconciliation.WorkflowSweeper), which is only active when
     * `conductor.app.legacy.sweeper.enabled=true`. The default 3.30.2 sweeper
     * (org.conductoross.conductor.core.execution.WorkflowSweeper) does NOT use responseTimeout for
     * its re-sweep interval (it uses workflowOffsetTimeout ~30s and lockLeaseTime/2 ~30s). Since the
     * legacy `unack()` is the ONLY place in the codebase that derives a ~600s interval from the task
     * defs, a consistent ~10-minute pause is strong evidence Mikhail is running the legacy sweeper.
     *
     * Mechanism: every task def carries responseTimeoutSeconds=600 (10 min) and
     * pollTimeoutSeconds=1200 (20 min). When a workflow parks (e.g. the event-driven decide after a
     * JOIN boundary is missed), the decider-queue re-evaluation is scheduled by unack(). Post-#707
     * that delay is (responseTimeoutSeconds + 1) for an IN_PROGRESS task -> 601s. Before #707 the
     * fallback was the flat workflowOffsetTimeout (30s), so a missed decide recovered in ~30s and was
     * invisible. This pins the 601s = ~10 min fallback, exactly the observed pause.
     */
    private static final int WORKFLOW_OFFSET_TIMEOUT_3_21_4 = 30; // 3.21.4-era decider offset
    private static final int MIKHAIL_RESPONSE_TIMEOUT = 600; // responseTimeoutSeconds on every task
    private static final int MIKHAIL_POLL_TIMEOUT = 1200; // pollTimeoutSeconds on every task
    private static final int PROD_MAX_POSTPONE = 3600; // ConductorProperties default

    @Test
    public void testRegressWorkflowTenMinutePauseOnInProgressTask() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("regress-wf-1");
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("regress-task-in-progress");
        taskModel.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        taskModel.setStatus(Status.IN_PROGRESS);
        taskModel.setResponseTimeoutSeconds(MIKHAIL_RESPONSE_TIMEOUT);
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(WORKFLOW_OFFSET_TIMEOUT_3_21_4));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(PROD_MAX_POSTPONE));

        workflowSweeper.unack(workflowModel, WORKFLOW_OFFSET_TIMEOUT_3_21_4);

        // 601_000 ms ~= 10 minutes. Pre-#707 this would have been the 30s workflowOffsetTimeout.
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        (MIKHAIL_RESPONSE_TIMEOUT + 1) * 1000L);
    }

    @Test
    public void testRegressWorkflowPauseOnScheduledTaskWithPollTimeout() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("regress-wf-2");
        TaskDef taskDef = new TaskDef();
        taskDef.setPollTimeoutSeconds(MIKHAIL_POLL_TIMEOUT);
        TaskModel taskModel = mock(TaskModel.class);
        when(taskModel.getStatus()).thenReturn(Status.SCHEDULED);
        when(taskModel.getTaskDefinition()).thenReturn(Optional.of(taskDef));
        workflowModel.setTasks(List.of(taskModel));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(WORKFLOW_OFFSET_TIMEOUT_3_21_4));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(PROD_MAX_POSTPONE));

        workflowSweeper.unack(workflowModel, WORKFLOW_OFFSET_TIMEOUT_3_21_4);

        // A workflow parked on a SCHEDULED task (worker not polling) waits pollTimeout+1 = ~20 min.
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        (MIKHAIL_POLL_TIMEOUT + 1) * 1000L);
    }

    /*
     * Faithful "parked at a JOIN boundary" reproduction: the fork child (SIMPLE, IN_PROGRESS,
     * responseTimeout=600) is still active while the JOIN sits SCHEDULED. unack() takes the minimum
     * eligible delay across all active tasks, so the whole workflow is not re-decided for ~10 min.
     */
    @Test
    public void testRegressWorkflowParkedAtJoinBoundaryPausesTenMinutes() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("regress-wf-join");

        TaskModel forkChild = new TaskModel();
        forkChild.setTaskId("fork-child-simple");
        forkChild.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        forkChild.setStatus(Status.IN_PROGRESS);
        forkChild.setResponseTimeoutSeconds(MIKHAIL_RESPONSE_TIMEOUT);

        TaskDef joinDef = new TaskDef();
        joinDef.setPollTimeoutSeconds(MIKHAIL_POLL_TIMEOUT);
        TaskModel joinTask = mock(TaskModel.class);
        when(joinTask.getStatus()).thenReturn(Status.SCHEDULED);
        when(joinTask.getTaskDefinition()).thenReturn(Optional.of(joinDef));

        workflowModel.setTasks(List.of(forkChild, joinTask));
        when(properties.getWorkflowOffsetTimeout())
                .thenReturn(Duration.ofSeconds(WORKFLOW_OFFSET_TIMEOUT_3_21_4));
        when(properties.getMaxPostponeDurationSeconds())
                .thenReturn(Duration.ofSeconds(PROD_MAX_POSTPONE));

        workflowSweeper.unack(workflowModel, WORKFLOW_OFFSET_TIMEOUT_3_21_4);

        // min(responseTimeout+1=601, pollTimeout+1=1201) = 601s ~= 10 minutes.
        verify(queueDAO)
                .setUnackTimeout(
                        DECIDER_QUEUE,
                        workflowModel.getWorkflowId(),
                        (MIKHAIL_RESPONSE_TIMEOUT + 1) * 1000L);
    }

    @Test
    public void testWorkflowOffsetJitter() {
        long offset = 45;
        for (int i = 0; i < 10; i++) {
            long offsetWithJitter = workflowSweeper.workflowOffsetWithJitter(offset);
            assertTrue(offsetWithJitter >= 30);
            assertTrue(offsetWithJitter <= 60);
        }
    }
}
