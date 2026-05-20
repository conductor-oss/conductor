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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused unit tests for the decide() iterative loop in WorkflowExecutorOps.
 *
 * <p>These tests use a mocked DeciderService so that the loop behaviour (continueLoop flag,
 * time-guard re-queue) can be exercised independently of the full workflow state machine.
 */
public class TestWorkflowExecutorDecideLoop {

    private static final String SYNC_TASK_TYPE = "SYNC_TASK";

    private WorkflowExecutorOps workflowExecutor;
    private DeciderService deciderService;
    private ExecutionDAOFacade executionDAOFacade;
    private QueueDAO queueDAO;
    private ConductorProperties properties;
    private ExecutionLockService executionLockService;
    private SystemTaskRegistry systemTaskRegistry;
    private WorkflowSystemTask mockSyncTask;

    @Before
    public void setUp() {
        deciderService = mock(DeciderService.class);
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        queueDAO = mock(QueueDAO.class);
        properties = mock(ConductorProperties.class);
        executionLockService = mock(ExecutionLockService.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        mockSyncTask = mock(WorkflowSystemTask.class);

        when(properties.getActiveWorkerLastPollTimeout()).thenReturn(Duration.ofSeconds(100));
        when(properties.getTaskExecutionPostponeDuration()).thenReturn(Duration.ofSeconds(60));
        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(30));
        when(properties.getLockLeaseTime()).thenReturn(Duration.ofSeconds(30));
        when(executionLockService.acquireLock(anyString())).thenReturn(true);

        // Set up a synchronous system task so that scheduleTask() sets stateChanged = true.
        when(systemTaskRegistry.isSystemTask(SYNC_TASK_TYPE)).thenReturn(true);
        when(systemTaskRegistry.get(SYNC_TASK_TYPE)).thenReturn(mockSyncTask);
        when(mockSyncTask.isAsync()).thenReturn(false);
        // start() leaves the task in SCHEDULED (non-terminal) so execute() is also called.
        when(mockSyncTask.execute(any(), any(), any())).thenReturn(true);

        workflowExecutor =
                new WorkflowExecutorOps(
                        deciderService,
                        mock(MetadataDAO.class),
                        queueDAO,
                        mock(MetadataMapperService.class),
                        mock(WorkflowStatusListener.class),
                        mock(TaskStatusListener.class),
                        executionDAOFacade,
                        properties,
                        executionLockService,
                        systemTaskRegistry,
                        mock(ParametersUtils.class),
                        mock(IDGenerator.class),
                        Optional.empty());
    }

    /**
     * Regression test for GitHub issue #799.
     *
     * <p>Verify that when the decider repeatedly schedules synchronous system tasks (simulating the
     * behaviour of LAMBDA or INLINE tasks inside a high-iteration DO_WHILE), the decide() loop
     * terminates cleanly without a StackOverflowError.
     *
     * <p>Before the fix, each state-change triggered a recursive decide() call; at a few hundred
     * iterations this blew the stack.
     */
    @Test
    public void testDecideLoopManyIterationsNoStackOverflow() throws Exception {
        int totalIterations = 500;
        AtomicInteger callCount = new AtomicInteger(0);

        WorkflowModel workflow = runningWorkflow("test-wf-overflow");
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);

        // Decider schedules one new synchronous system task per call for the first N calls, then
        // signals completion. Each task has a unique reference name to avoid dedup.
        when(deciderService.decide(any(WorkflowModel.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n > totalIterations) {
                                return completeOutcome();
                            }
                            return schedulingOutcome("task-" + n, "task-ref-" + n);
                        });

        // Act — must not throw StackOverflowError
        WorkflowModel result = workflowExecutor.decide(workflow.getWorkflowId());

        // Assert
        assertNotNull(result);
        assertTrue(
                "Decider should have been called at least " + totalIterations + " times",
                callCount.get() >= totalIterations);
    }

    /**
     * Verify that when the decide loop is still changing state but the lock lease is about to
     * expire, it persists the current workflow state and pushes the workflow ID back onto the
     * decider queue rather than continuing to hold the lock.
     */
    @Test
    public void testDecideLoopRequeuesWhenApproachingLockLeaseTime() throws Exception {
        // Extremely short lease so the time guard fires immediately (maxRuntime = 1 - 100 = -99ms,
        // so decideWatch.getTime() >= maxRuntime on the very first iteration).
        when(properties.getLockLeaseTime()).thenReturn(Duration.ofMillis(1));

        WorkflowModel workflow = runningWorkflow("test-wf-timeout");
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);

        AtomicInteger callCount = new AtomicInteger(0);
        // Decider always returns a new synchronous task (state keeps changing), simulating a loop
        // that would otherwise hold the lock indefinitely.
        when(deciderService.decide(any(WorkflowModel.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            return schedulingOutcome("task-" + n, "task-ref-" + n);
                        });

        // Act
        workflowExecutor.decide(workflow.getWorkflowId());

        // Assert: workflow is persisted and re-queued before the lock expires.
        verify(executionDAOFacade, atLeastOnce()).updateWorkflow(workflow);
        verify(queueDAO, atLeastOnce())
                .push(eq(DECIDER_QUEUE), eq(workflow.getWorkflowId()), eq(0L));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static WorkflowModel runningWorkflow(String id) {
        WorkflowModel wf = new WorkflowModel();
        wf.setWorkflowId(id);
        wf.setStatus(WorkflowModel.Status.RUNNING);
        wf.setCreateTime(System.currentTimeMillis());
        WorkflowDef def = new WorkflowDef();
        def.setName(id);
        def.setVersion(1);
        wf.setWorkflowDefinition(def);
        wf.setOutput(Collections.emptyMap());
        return wf;
    }

    /** Creates a DeciderOutcome that signals workflow completion (isComplete = true). */
    private static DeciderService.DeciderOutcome completeOutcome() throws Exception {
        DeciderService.DeciderOutcome outcome = newOutcome();
        setField(outcome, "isComplete", true);
        return outcome;
    }

    /**
     * Creates a DeciderOutcome that schedules one synchronous system task. scheduleTask() will
     * detect it as a system task, call start(), set startedSystemTasks = true, and return true —
     * which sets stateChanged = true in the decide loop, causing another iteration.
     */
    private static DeciderService.DeciderOutcome schedulingOutcome(String taskId, String refName)
            throws Exception {
        DeciderService.DeciderOutcome outcome = newOutcome();
        TaskModel task = new TaskModel();
        task.setTaskId(taskId);
        task.setReferenceTaskName(refName);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setTaskType(SYNC_TASK_TYPE);
        ((LinkedList<TaskModel>) getField(outcome, "tasksToBeScheduled")).add(task);
        return outcome;
    }

    private static DeciderService.DeciderOutcome newOutcome() throws Exception {
        Constructor<DeciderService.DeciderOutcome> ctor =
                DeciderService.DeciderOutcome.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private static Object getField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }
}
