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
import java.util.concurrent.Executor;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestWorkflowSweeper {

    private ConductorProperties properties;
    private SweeperProperties sweeperProperties;
    private WorkflowExecutor workflowExecutor;
    private QueueDAO queueDAO;
    private ExecutionDAO executionDAO;
    private WorkflowSweeper workflowSweeper;
    private SystemTaskRegistry systemTaskRegistry;
    private ObjectMapper objectMapper;
    private Executor executor;

    @Before
    public void setUp() {
        properties = mock(ConductorProperties.class);
        sweeperProperties = mock(SweeperProperties.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        queueDAO = mock(QueueDAO.class);
        executionDAO = mock(ExecutionDAO.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        objectMapper = new ObjectMapper();
        executor = mock(Executor.class);

        when(properties.getMaxPostponeDurationSeconds()).thenReturn(Duration.ofSeconds(2000000));
        when(properties.getSweeperThreadCount()).thenReturn(1);
        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(1800));
        when(properties.getLockLeaseTime()).thenReturn(Duration.ofSeconds(60));
        when(sweeperProperties.getSweepBatchSize()).thenReturn(10);
        when(sweeperProperties.getQueuePopTimeout()).thenReturn(100);

        workflowSweeper =
                new WorkflowSweeper(
                        executor,
                        queueDAO,
                        workflowExecutor,
                        executionDAO,
                        properties,
                        sweeperProperties,
                        systemTaskRegistry,
                        objectMapper);
    }

    @Test
    public void testSweepTerminalWorkflow() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("1");
        workflowModel.setStatus(WorkflowModel.Status.COMPLETED);

        when(workflowExecutor.getWorkflow("1", true)).thenReturn(workflowModel);

        workflowSweeper.sweep("1");

        verify(queueDAO).remove(DECIDER_QUEUE, "1");
        verify(workflowExecutor, never()).decideWithLock(any(WorkflowModel.class));
    }

    @Test
    public void testSweepNullWorkflow() {
        when(workflowExecutor.getWorkflow("1", true)).thenReturn(null);

        workflowSweeper.sweep("1");

        verify(queueDAO).remove(DECIDER_QUEUE, "1");
    }

    @Test
    public void testSweepRunningWorkflow() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("2");
        workflowModel.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setReferenceTaskName("task1");
        taskModel.setTaskType("SIMPLE");
        taskModel.setStatus(Status.IN_PROGRESS);
        workflowModel.setTasks(List.of(taskModel));

        when(workflowExecutor.getWorkflow("2", true)).thenReturn(workflowModel);
        when(workflowExecutor.decideWithLock(any(WorkflowModel.class))).thenReturn(workflowModel);

        workflowSweeper.sweep("2");

        verify(workflowExecutor).decideWithLock(any(WorkflowModel.class));
    }

    @Test
    public void testSweepCannotGetLock() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("3");
        workflowModel.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setReferenceTaskName("task1");
        taskModel.setTaskType("SIMPLE");
        taskModel.setStatus(Status.IN_PROGRESS);
        workflowModel.setTasks(List.of(taskModel));

        when(workflowExecutor.getWorkflow("3", true)).thenReturn(workflowModel);
        when(workflowExecutor.decideWithLock(any(WorkflowModel.class))).thenReturn(null);

        workflowSweeper.sweep("3");

        verify(workflowExecutor).decideWithLock(any(WorkflowModel.class));
        verify(queueDAO).push(eq(DECIDER_QUEUE), eq("3"), eq(0), anyLong());
    }

    @Test
    public void testSweepRepairsScheduledTask() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("4");
        workflowModel.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setReferenceTaskName("task1");
        taskModel.setTaskType("SIMPLE");
        taskModel.setStatus(Status.SCHEDULED);
        workflowModel.setTasks(List.of(taskModel));

        when(workflowExecutor.getWorkflow("4", true)).thenReturn(workflowModel);
        when(workflowExecutor.decideWithLock(any(WorkflowModel.class))).thenReturn(workflowModel);
        when(systemTaskRegistry.isSystemTask("SIMPLE")).thenReturn(false);
        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        workflowSweeper.sweep("4");

        verify(queueDAO).push(anyString(), eq("task1"), anyLong());
    }

    @Test
    public void testSweepDoesNotRepairTaskAlreadyInQueue() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("5");
        workflowModel.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task1");
        taskModel.setReferenceTaskName("task1");
        taskModel.setTaskType("SIMPLE");
        taskModel.setStatus(Status.SCHEDULED);
        workflowModel.setTasks(List.of(taskModel));

        when(workflowExecutor.getWorkflow("5", true)).thenReturn(workflowModel);
        when(workflowExecutor.decideWithLock(any(WorkflowModel.class))).thenReturn(workflowModel);
        when(systemTaskRegistry.isSystemTask("SIMPLE")).thenReturn(false);
        when(queueDAO.containsMessage(anyString(), eq("task1"))).thenReturn(true);

        workflowSweeper.sweep("5");

        verify(queueDAO, never()).push(anyString(), eq("task1"), anyLong());
    }

    @Test
    public void testSweepSubWorkflowTask() {
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowId("6");
        workflowModel.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel subWorkflowTask = new TaskModel();
        subWorkflowTask.setTaskId("task1");
        subWorkflowTask.setReferenceTaskName("task1");
        subWorkflowTask.setTaskType(TaskType.TASK_TYPE_SUB_WORKFLOW);
        subWorkflowTask.setStatus(Status.IN_PROGRESS);
        subWorkflowTask.setSubWorkflowId("sub-wf-1");
        workflowModel.setTasks(List.of(subWorkflowTask));

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setWorkflowId("sub-wf-1");
        subWorkflow.setStatus(WorkflowModel.Status.COMPLETED);

        when(workflowExecutor.getWorkflow("6", true)).thenReturn(workflowModel);
        when(workflowExecutor.decideWithLock(any(WorkflowModel.class))).thenReturn(workflowModel);
        when(executionDAO.getWorkflow("sub-wf-1", false)).thenReturn(subWorkflow);

        workflowSweeper.sweep("6");

        verify(executionDAO).updateTask(subWorkflowTask);
    }
}
