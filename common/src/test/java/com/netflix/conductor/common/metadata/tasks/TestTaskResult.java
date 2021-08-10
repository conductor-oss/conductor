package com.netflix.conductor.common.metadata.tasks;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

public class TestTaskResult {

    private Task task;
    private TaskResult taskResult;

    @Before
    public void setUp() {
        task = new Task();
        task.setWorkflowInstanceId("workflow-id");
        task.setTaskId("task-id");
        task.setReasonForIncompletion("reason");
        task.setCallbackAfterSeconds(10);
        task.setWorkerId("worker-id");
        task.setOutputData(new HashMap<>());
        task.setExternalOutputPayloadStoragePath("externalOutput");
        task.setStartDelayInSeconds(10);
    }

    @Test
    public void testCanceledTask() {
        task.setStatus(Task.Status.CANCELED);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.FAILED, taskResult.getStatus());
    }

    @Test
    public void testCompletedWithErrorsTask() {
        task.setStatus(Task.Status.COMPLETED_WITH_ERRORS);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.FAILED, taskResult.getStatus());
    }

    @Test
    public void testScheduledTask() {
        task.setStatus(Task.Status.SCHEDULED);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.IN_PROGRESS, taskResult.getStatus());
    }

    @Test
    public void testCompltetedTask() {
        task.setStatus(Task.Status.COMPLETED);
        taskResult = new TaskResult(task);
        validateTaskResult();
        assertEquals(TaskResult.Status.COMPLETED, taskResult.getStatus());
    }

    private void validateTaskResult() {
        assertEquals(task.getWorkflowInstanceId(), taskResult.getWorkflowInstanceId());
        assertEquals(task.getTaskId(), taskResult.getTaskId());
        assertEquals(task.getReasonForIncompletion(), taskResult.getReasonForIncompletion());
        assertEquals(task.getCallbackAfterSeconds(), taskResult.getCallbackAfterSeconds());
        assertEquals(task.getWorkerId(), taskResult.getWorkerId());
        assertEquals(task.getOutputData(), taskResult.getOutputData());
        assertEquals(task.getExternalOutputPayloadStoragePath(), taskResult.getExternalOutputPayloadStoragePath());
        assertEquals(task.getStartDelayInSeconds(), taskResult.getRetryDelaySeconds());
    }
}