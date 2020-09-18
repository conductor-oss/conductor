package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.tasks.Decision;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Before;
import org.junit.Test;

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
    public void verifyAndRepairTask() {
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
}
