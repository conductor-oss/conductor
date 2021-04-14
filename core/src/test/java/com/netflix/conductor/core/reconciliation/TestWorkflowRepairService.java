/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.Decision;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_DECISION;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TestWorkflowRepairService {

    private QueueDAO queueDAO;
    private WorkflowRepairService workflowRepairService;
    private SystemTaskRegistry systemTaskRegistry;

    @Before
    public void setUp() {
        ExecutionDAO executionDAO = mock(ExecutionDAO.class);
        queueDAO = mock(QueueDAO.class);
        ConductorProperties properties = mock(ConductorProperties.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        workflowRepairService = new WorkflowRepairService(executionDAO, queueDAO, properties, systemTaskRegistry);
    }

    @Test
    public void verifyAndRepairSimpleTaskInScheduledState() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setStatus(Task.Status.SCHEDULED);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for sync system tasks that fails queue contains check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void verifySimpleTaskInProgressState() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setStatus(Task.Status.IN_PROGRESS);
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
        Task task = new Task();
        task.setTaskType(taskType);
        task.setStatus(Task.Status.SCHEDULED);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        when(systemTaskRegistry.isSystemTask("TEST_SYS_TASK")).thenReturn(true);
        when(systemTaskRegistry.get(taskType)).thenReturn(new WorkflowSystemTask("TEST_SYS_TASK") {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public boolean isAsyncComplete(Task task) {
                return false;
            }

            @Override
            public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
                super.start(workflow, task, executor);
            }
        });

        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for tasks that fails queue contains check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());

        // Verify a system task in IN_PROGRESS state can be recovered.
        reset(queueDAO);
        task.setStatus(Task.Status.IN_PROGRESS);
        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for async System task in IN_PROGRESS state that fails queue contains check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void assertSyncSystemTasksAreNotCheckedAgainstQueue() {
        // Return a Decision object to init WorkflowSystemTask registry.
        when(systemTaskRegistry.get(TASK_TYPE_DECISION)).thenReturn(new Decision());
        when(systemTaskRegistry.isSystemTask(TASK_TYPE_DECISION)).thenReturn(true);

        Task task = new Task();
        task.setTaskType(TASK_TYPE_DECISION);
        task.setStatus(Task.Status.SCHEDULED);

        assertFalse(workflowRepairService.verifyAndRepairTask(task));
        // Verify that queue contains is never checked for sync system tasks
        verify(queueDAO, never()).containsMessage(anyString(), anyString());
        // Verify that queue message is never pushed for sync system tasks
        verify(queueDAO, never()).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void assertAsyncCompleteSystemTasksAreNotCheckedAgainstQueue() {
        Task task = new Task();
        task.setTaskType(TASK_TYPE_SUB_WORKFLOW);
        task.setStatus(Task.Status.IN_PROGRESS);
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
}
