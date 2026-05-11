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

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyInt;
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
                        objectMapper);
    }

    @Test
    public void sweepDoesNotRepushTerminalTasks() {
        TaskModel completedTask =
                newTask("completed-task", TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.COMPLETED);
        TaskModel runningWaitTask =
                newTask("wait-task", TaskType.TASK_TYPE_WAIT, TaskModel.Status.IN_PROGRESS);
        runningWaitTask.setWaitTimeout(System.currentTimeMillis() + 60_000);
        WorkflowModel workflow = newWorkflow(List.of(completedTask, runningWaitTask));

        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(workflow);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(workflow);
        when(systemTaskRegistry.isSystemTask(anyString())).thenReturn(false);
        when(queueDAO.containsMessage(TaskType.TASK_TYPE_WAIT, runningWaitTask.getTaskId()))
                .thenReturn(true);

        workflowSweeper.sweep(WORKFLOW_ID);

        verify(queueDAO, never()).push(TaskType.TASK_TYPE_SIMPLE, completedTask.getTaskId(), 0L);
    }

    @Test
    public void sweepDoesNotRepushNonRepairableInProgressSimpleTask() {
        TaskModel simpleInProgressTask =
                newTask("simple-task", TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.IN_PROGRESS);
        WorkflowModel workflow = newWorkflow(List.of(simpleInProgressTask));

        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(workflow);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(workflow);
        when(systemTaskRegistry.isSystemTask(anyString())).thenReturn(false);

        workflowSweeper.sweep(WORKFLOW_ID);

        verify(queueDAO, never())
                .push(TaskType.TASK_TYPE_SIMPLE, simpleInProgressTask.getTaskId(), 0L);
    }

    @Test
    public void sweepRepushesRepairableScheduledTaskWhenMessageMissing() {
        TaskModel scheduledTask =
                newTask("scheduled-task", TaskType.TASK_TYPE_SIMPLE, TaskModel.Status.SCHEDULED);
        scheduledTask.setCallbackAfterSeconds(7L);
        WorkflowModel workflow = newWorkflow(List.of(scheduledTask));

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
    }

    /**
     * Regression test for conductor-oss/conductor#1058.
     *
     * <p>Previously, sweep() pre-acquired the execution lock and then called
     * workflowExecutor.decide(workflowId), which acquires the same lock internally. With a
     * non-reentrant lock backend (Postgres advisory locks), the inner acquisition always
     * returned false, decide() returned null, and the sweeper re-queued the workflow with a
     * backoff delay — stalling workflows containing a SUB_WORKFLOW task forever after the
     * sub-workflow completed.
     *
     * <p>After the fix, sweep() no longer pre-acquires the lock; decide() handles locking
     * itself. A SUB_WORKFLOW parent whose child has completed must advance to terminal in a
     * single sweep cycle, with no backoff push to the decider queue.
     */
    @Test
    public void sweepAdvancesParentWhenSubWorkflowCompletedWithNonReentrantLock() {
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

        when(workflowExecutor.getWorkflow(WORKFLOW_ID, true)).thenReturn(workflow);
        when(workflowExecutor.decide(WORKFLOW_ID)).thenReturn(workflow, terminalWorkflow);
        when(systemTaskRegistry.isSystemTask(TaskType.TASK_TYPE_SUB_WORKFLOW)).thenReturn(true);
        when(systemTaskRegistry.get(TaskType.TASK_TYPE_SUB_WORKFLOW))
                .thenReturn(workflowSystemTask);
        when(workflowSystemTask.isAsync()).thenReturn(true);
        when(workflowSystemTask.isAsyncComplete(subWorkflowTask)).thenReturn(true);
        when(executionDAO.getWorkflow("sub-workflow-id", false)).thenReturn(subWorkflow);

        workflowSweeper.sweep(WORKFLOW_ID);

        verify(queueDAO, never()).push(anyString(), anyString(), anyInt(), anyLong());
        verify(queueDAO).remove(com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE, WORKFLOW_ID);
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
