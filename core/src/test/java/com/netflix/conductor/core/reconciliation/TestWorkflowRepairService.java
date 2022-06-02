/*
 * Copyright 2022 Netflix, Inc.
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

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.*;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestWorkflowRepairService {

    private QueueDAO queueDAO;
    private ExecutionDAO executionDAO;
    private ConductorProperties properties;
    private WorkflowRepairService workflowRepairService;
    private SystemTaskRegistry systemTaskRegistry;

    @Before
    public void setUp() {
        executionDAO = mock(ExecutionDAO.class);
        queueDAO = mock(QueueDAO.class);
        properties = mock(ConductorProperties.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        workflowRepairService =
                new WorkflowRepairService(executionDAO, queueDAO, properties, systemTaskRegistry);
    }

    @Test
    public void verifyAndRepairSimpleTaskInScheduledState() {
        TaskModel task = new TaskModel();
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for sync system tasks that fails queue contains
        // check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void verifySimpleTaskInProgressState() {
        TaskModel task = new TaskModel();
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        assertFalse(workflowRepairService.verifyAndRepairTask(task));
        // Verify that queue message is never pushed for simple task in IN_PROGRESS state
        verify(queueDAO, never()).containsMessage(anyString(), anyString());
        verify(queueDAO, never()).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void verifyAndRepairSystemTask() {
        String taskType = "TEST_SYS_TASK";
        TaskModel task = new TaskModel();
        task.setTaskType(taskType);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        when(systemTaskRegistry.isSystemTask("TEST_SYS_TASK")).thenReturn(true);
        when(systemTaskRegistry.get(taskType))
                .thenReturn(
                        new WorkflowSystemTask("TEST_SYS_TASK") {
                            @Override
                            public boolean isAsync() {
                                return true;
                            }

                            @Override
                            public boolean isAsyncComplete(TaskModel task) {
                                return false;
                            }

                            @Override
                            public void start(
                                    WorkflowModel workflow,
                                    TaskModel task,
                                    WorkflowExecutor executor) {
                                super.start(workflow, task, executor);
                            }
                        });

        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for tasks that fails queue contains check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());

        // Verify a system task in IN_PROGRESS state can be recovered.
        reset(queueDAO);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for async System task in IN_PROGRESS state that
        // fails queue contains check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void assertSyncSystemTasksAreNotCheckedAgainstQueue() {
        // Return a Switch task object to init WorkflowSystemTask registry.
        when(systemTaskRegistry.get(TASK_TYPE_DECISION)).thenReturn(new Decision());
        when(systemTaskRegistry.isSystemTask(TASK_TYPE_DECISION)).thenReturn(true);
        when(systemTaskRegistry.get(TASK_TYPE_SWITCH)).thenReturn(new Switch());
        when(systemTaskRegistry.isSystemTask(TASK_TYPE_SWITCH)).thenReturn(true);

        TaskModel task = new TaskModel();
        task.setTaskType(TASK_TYPE_DECISION);
        task.setStatus(TaskModel.Status.SCHEDULED);

        assertFalse(workflowRepairService.verifyAndRepairTask(task));
        // Verify that queue contains is never checked for sync system tasks
        verify(queueDAO, never()).containsMessage(anyString(), anyString());
        // Verify that queue message is never pushed for sync system tasks
        verify(queueDAO, never()).push(anyString(), anyString(), anyLong());

        task = new TaskModel();
        task.setTaskType(TASK_TYPE_SWITCH);
        task.setStatus(TaskModel.Status.SCHEDULED);

        assertFalse(workflowRepairService.verifyAndRepairTask(task));
        // Verify that queue contains is never checked for sync system tasks
        verify(queueDAO, never()).containsMessage(anyString(), anyString());
        // Verify that queue message is never pushed for sync system tasks
        verify(queueDAO, never()).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void assertAsyncCompleteInProgressSystemTasksAreNotCheckedAgainstQueue() {
        TaskModel task = new TaskModel();
        task.setTaskType(TASK_TYPE_SUB_WORKFLOW);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        WorkflowSystemTask workflowSystemTask = new SubWorkflow(new ObjectMapper());
        when(systemTaskRegistry.get(TASK_TYPE_SUB_WORKFLOW)).thenReturn(workflowSystemTask);

        assertTrue(workflowSystemTask.isAsyncComplete(task));

        assertFalse(workflowRepairService.verifyAndRepairTask(task));
        // Verify that queue message is never pushed for async complete system tasks
        verify(queueDAO, never()).containsMessage(anyString(), anyString());
        verify(queueDAO, never()).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void assertAsyncCompleteScheduledSystemTasksAreCheckedAgainstQueue() {
        TaskModel task = new TaskModel();
        task.setTaskType(TASK_TYPE_SUB_WORKFLOW);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        WorkflowSystemTask workflowSystemTask = new SubWorkflow(new ObjectMapper());
        when(systemTaskRegistry.get(TASK_TYPE_SUB_WORKFLOW)).thenReturn(workflowSystemTask);
        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        assertTrue(workflowSystemTask.isAsyncComplete(task));

        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that queue message is never pushed for async complete system tasks
        verify(queueDAO, times(1)).containsMessage(anyString(), anyString());
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void verifyAndRepairParentWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("abcd");
        workflow.setParentWorkflowId("parentWorkflowId");

        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(10));
        when(executionDAO.getWorkflow("abcd", true)).thenReturn(workflow);
        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        workflowRepairService.verifyAndRepairWorkflowTasks("abcd");
        verify(queueDAO, times(1)).containsMessage(anyString(), anyString());
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());
    }
}
