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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkflowSweeperTest {

    private static final String WORKFLOW_ID = "workflow-id";

    private Executor sweeperExecutor;
    private QueueDAO queueDAO;
    private WorkflowExecutor workflowExecutor;
    private ExecutionDAO executionDAO;
    private ConductorProperties properties;
    private SweeperProperties sweeperProperties;
    private SystemTaskRegistry systemTaskRegistry;
    private ObjectMapper objectMapper;
    private ExecutionLockService executionLockService;
    private WorkflowSweeper workflowSweeper;

    @Before
    public void setUp() {
        sweeperExecutor = mock(Executor.class);
        queueDAO = mock(QueueDAO.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        executionDAO = mock(ExecutionDAO.class);
        properties = mock(ConductorProperties.class);
        sweeperProperties = mock(SweeperProperties.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        objectMapper = mock(ObjectMapper.class);
        executionLockService = mock(ExecutionLockService.class);

        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(30));
        when(properties.getMaxPostponeDurationSeconds()).thenReturn(Duration.ofSeconds(3600));
        when(properties.getLockLeaseTime()).thenReturn(Duration.ofSeconds(60));
        when(properties.getSweeperThreadCount()).thenReturn(0);

        workflowSweeper =
                new WorkflowSweeper(
                        sweeperExecutor,
                        queueDAO,
                        workflowExecutor,
                        executionDAO,
                        properties,
                        sweeperProperties,
                        systemTaskRegistry,
                        objectMapper,
                        executionLockService);
    }

    @Test
    public void sweepDoesNotRepushTerminalTasks() {
        TaskModel completedTask =
                newTask("completed-task", TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.COMPLETED);
        TaskModel runningWaitTask =
                newTask("wait-task", TaskType.TASK_TYPE_WAIT, TaskModel.Status.IN_PROGRESS);
        runningWaitTask.setWaitTimeout(System.currentTimeMillis() + 60_000);
        WorkflowModel workflow = newWorkflow(List.of(completedTask, runningWaitTask));

        when(executionLockService.acquireLock(WORKFLOW_ID)).thenReturn(true);
        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(workflow);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(workflow);
        when(systemTaskRegistry.isSystemTask(anyString())).thenReturn(false);
        when(queueDAO.containsMessage(TaskType.TASK_TYPE_WAIT, runningWaitTask.getTaskId()))
                .thenReturn(true);

        workflowSweeper.sweep(WORKFLOW_ID);

        verify(queueDAO, never()).push(TaskType.TASK_TYPE_SIMPLE, completedTask.getTaskId(), 0L);
        verify(executionLockService).releaseLock(WORKFLOW_ID);
    }

    @Test
    public void sweepDoesNotRepushNonRepairableInProgressSimpleTask() {
        TaskModel simpleInProgressTask =
                newTask("simple-task", TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.IN_PROGRESS);
        WorkflowModel workflow = newWorkflow(List.of(simpleInProgressTask));

        when(executionLockService.acquireLock(WORKFLOW_ID)).thenReturn(true);
        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(workflow);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(workflow);
        when(systemTaskRegistry.isSystemTask(anyString())).thenReturn(false);

        workflowSweeper.sweep(WORKFLOW_ID);

        verify(queueDAO, never())
                .push(TaskType.TASK_TYPE_SIMPLE, simpleInProgressTask.getTaskId(), 0L);
        verify(executionLockService).releaseLock(WORKFLOW_ID);
    }

    @Test
    public void sweepRepushesRepairableScheduledTaskWhenMessageMissing() {
        TaskModel scheduledTask =
                newTask("scheduled-task", TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.SCHEDULED);
        scheduledTask.setCallbackAfterSeconds(7L);
        WorkflowModel workflow = newWorkflow(List.of(scheduledTask));

        when(executionLockService.acquireLock(WORKFLOW_ID)).thenReturn(true);
        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(workflow);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(workflow);
        when(systemTaskRegistry.isSystemTask(anyString())).thenReturn(false);
        when(queueDAO.containsMessage(TaskType.TASK_TYPE_SIMPLE, scheduledTask.getTaskId()))
                .thenReturn(false);

        workflowSweeper.sweep(WORKFLOW_ID);

        verify(queueDAO, times(1))
                .push(
                        TaskType.TASK_TYPE_SIMPLE,
                        scheduledTask.getTaskId(),
                        scheduledTask.getCallbackAfterSeconds());
        verify(executionLockService).releaseLock(WORKFLOW_ID);
    }

    @Test
    public void sweepRepairsSubWorkflowTaskWhenSubWorkflowIsTerminal() {
        TaskModel subWorkflowTask =
                newTask(
                        "sub-workflow-task",
                        TaskType.TASK_TYPE_SUB_WORKFLOW,
                        TaskModel.Status.IN_PROGRESS);
        subWorkflowTask.setSubWorkflowId("sub-workflow-id");
        WorkflowModel workflow = newWorkflow(List.of(subWorkflowTask));

        WorkflowSystemTask workflowSystemTask = mock(WorkflowSystemTask.class);
        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setStatus(WorkflowModel.Status.COMPLETED);
        subWorkflow.setOutput(Map.of("result", "ok"));
        WorkflowModel terminalWorkflow = new WorkflowModel();
        terminalWorkflow.setWorkflowId(WORKFLOW_ID);
        terminalWorkflow.setStatus(WorkflowModel.Status.COMPLETED);

        when(executionLockService.acquireLock(WORKFLOW_ID)).thenReturn(true);
        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(workflow);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(workflow, terminalWorkflow);
        when(systemTaskRegistry.isSystemTask(TaskType.TASK_TYPE_SUB_WORKFLOW)).thenReturn(true);
        when(systemTaskRegistry.get(TaskType.TASK_TYPE_SUB_WORKFLOW))
                .thenReturn(workflowSystemTask);
        when(workflowSystemTask.isAsync()).thenReturn(true);
        when(workflowSystemTask.isAsyncComplete(subWorkflowTask)).thenReturn(true);
        when(executionDAO.getWorkflow("sub-workflow-id", false)).thenReturn(subWorkflow);

        workflowSweeper.sweep(WORKFLOW_ID);

        verify(executionDAO).updateTask(subWorkflowTask);
        verify(workflowExecutor, times(2)).decide(WORKFLOW_ID);
        verify(queueDAO, never()).push(anyString(), anyString(), anyLong());
        verify(executionLockService).releaseLock(WORKFLOW_ID);
    }

    /*
     * Reproduction of the CloudAware / Mikhail RegressWorkflow ~10-minute pause on his ACTUAL 3.30.2
     * config: conductor.app.sweeper.enabled=true (this new sweeper), no legacy.sweeper.enabled,
     * conductor.app.workflow-offset-timeout=0s, conductor.app.lockLeaseTime=60000ms, redis (redisson)
     * workflow-execution lock. His RegressWorkflow has ~15 JOIN boundaries (14 FORK_JOIN_DYNAMIC + 1
     * static) plus DO_WHILE / SUB_WORKFLOW / DYNAMIC.
     *
     * Stall mechanism: at a JOIN / post-completion boundary the workflow-execution lock is contended.
     * sweep() needs that same lock; on a miss it logs "Couldn't acquire lock to sweep workflow" and
     * BARE-RETURNS (WorkflowSweeper.java:162-165) - it does NOT decide(), does NOT re-queue, and
     * (because the early return precedes the try/finally) does NOT release the lock. So this sweep
     * pass does nothing to rescue the parked workflow; recovery waits on the lock lease / task-queue
     * unack. The only 600s constant in his deployment is the task defs' responseTimeoutSeconds=600
     * (the task-queue unack), which is where the ~10-minute duration comes from once the event-driven
     * decide is missed; the sweeper simply fails to shorten it.
     */
    /*
     * Regression test for the RegressWorkflow ~10-minute pause. Previously sweep() bare-returned on
     * a lock miss (no decide, no re-queue), so a lock-contended workflow parked until its decider
     * entry fired — which a polled task postpones out to responseTimeoutSeconds (600s). The fix
     * re-queues the workflow with a bounded backoff (lockLeaseTime/2 = 30s here) so it is retried in
     * seconds. Before the fix this assertion fails: no push happens on a lock miss.
     */
    @Test
    public void sweepReQueuesOnLockMissWithBoundedBackoffInsteadOfParking() {
        when(executionLockService.acquireLock(WORKFLOW_ID)).thenReturn(false);

        workflowSweeper.sweep(WORKFLOW_ID);

        // Re-queued for a near-term retry: lockLeaseTime(60000ms)/2 = 30s, capped by maxPostpone.
        verify(queueDAO).push(DECIDER_QUEUE, WORKFLOW_ID, 0, 30L);
        // Still no decide on a contended lock, and no lock to release (early return).
        verify(workflowExecutor, never()).decide(WORKFLOW_ID);
        verify(executionLockService, never()).releaseLock(WORKFLOW_ID);
    }

    /*
     * When sweep() DOES hold the lock but decide() cannot (returns null), the new sweeper re-queues
     * the workflow after lockLeaseTime/2. With Mikhail's lockLeaseTime=60000ms that is 30s - NOT 10
     * minutes. This pins that the new sweeper's own recovery is ~30s, proving the observed ~10-min
     * pause is NOT produced by the sweeper's backoff and must originate from the responseTimeout=600
     * task-queue unack (see the reproduction note above).
     */
    @Test
    public void sweepReQueuesWith30sBackoffWhenDecideCannotAcquireLock() {
        WorkflowModel running =
                newWorkflow(
                        List.of(
                                newTask(
                                        "join",
                                        TaskType.TASK_TYPE_JOIN,
                                        TaskModel.Status.IN_PROGRESS)));
        when(executionLockService.acquireLock(WORKFLOW_ID)).thenReturn(true);
        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(running);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(null); // decide couldn't get the lock

        workflowSweeper.sweep(WORKFLOW_ID);

        // lockLeaseTime(60000ms) / 2 = 30s backoff, capped by maxPostpone(3600s).
        verify(queueDAO).push(DECIDER_QUEUE, WORKFLOW_ID, 0, 30L);
        verify(executionLockService).releaseLock(WORKFLOW_ID);
    }

    private WorkflowModel newWorkflow(List<TaskModel> tasks) {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId(WORKFLOW_ID);
        workflowModel.setStatus(WorkflowModel.Status.RUNNING);
        workflowModel.setTasks(tasks);
        return workflowModel;
    }

    private TaskModel newTask(String taskId, String taskType, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId(taskId);
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setReferenceTaskName(taskId);
        return task;
    }
}
