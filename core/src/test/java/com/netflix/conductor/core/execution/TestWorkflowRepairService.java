package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.tasks.Decision;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestWorkflowRepairService {

    ExecutionDAO executionDAO;
    QueueDAO queueDAO;
    Configuration configuration;
    WorkflowRepairService workflowRepairService;

    @Before
    public void setUp() {
        executionDAO = mock(ExecutionDAO.class);
        queueDAO = mock(QueueDAO.class);
        configuration = mock(Configuration.class);
        workflowRepairService = new WorkflowRepairService(executionDAO, queueDAO, configuration);
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
        Task task = new Task();
        task.setTaskType("TEST_SYS_TASK");
        task.setStatus(Task.Status.SCHEDULED);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        // Create a Custom system task to init WorkflowSystemTask registry.
        WorkflowSystemTask workflowSystemTask = new WorkflowSystemTask("TEST_SYS_TASK") {
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
        };

        when(queueDAO.containsMessage(anyString(), anyString())).thenReturn(false);

        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for tasks that fails queue contains check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());

        // Verify a system task in IN_PROGRESS state can be recovered.
        Mockito.reset(queueDAO);
        task.setStatus(Task.Status.IN_PROGRESS);
        assertTrue(workflowRepairService.verifyAndRepairTask(task));
        // Verify that a new queue message is pushed for async System task in IN_PROGRESS state that fails queue contains check.
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyLong());
    }

    @Test
    public void assertSyncSystemTasksAreNotCheckedAgainstQueue() {
        // Create a Decision object to init WorkflowSystemTask registry.
        Decision decision = new Decision();

        Task task = new Task();
        task.setTaskType("DECISION");
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
        task.setTaskType("SUB_WORKFLOW");
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskId("abcd");
        task.setCallbackAfterSeconds(60);

        WorkflowSystemTask workflowSystemTask = new SubWorkflow();

        assertTrue(workflowSystemTask.isAsyncComplete(task));

        assertFalse(workflowRepairService.verifyAndRepairTask(task));
        // Verify that queue message is never pushed for async complete system tasks
        verify(queueDAO, never()).containsMessage(anyString(), anyString());
        verify(queueDAO, never()).push(anyString(), anyString(), anyLong());
    }
}
