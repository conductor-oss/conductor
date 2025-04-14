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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskResultPojoMethods {

    @Test
    void testDefaultConstructor() {
        TaskResult result = new TaskResult();

        assertNull(result.getWorkflowInstanceId());
        assertNull(result.getTaskId());
        assertNull(result.getReasonForIncompletion());
        assertEquals(0, result.getCallbackAfterSeconds());
        assertNull(result.getWorkerId());
        assertNull(result.getStatus());
        assertNotNull(result.getOutputData());
        assertTrue(result.getOutputData().isEmpty());
        assertNotNull(result.getLogs());
        assertTrue(result.getLogs().isEmpty());
        assertNull(result.getExternalOutputPayloadStoragePath());
        assertNull(result.getSubWorkflowId());
        assertFalse(result.isExtendLease());
    }

    @Test
    void testTaskConstructor() {
        Task task = new Task();
        task.setWorkflowInstanceId("workflow123");
        task.setTaskId("task123");
        task.setReasonForIncompletion("Task not completed");
        task.setCallbackAfterSeconds(60);
        task.setWorkerId("worker123");

        Map<String, Object> outputData = new HashMap<>();
        outputData.put("key1", "value1");
        task.setOutputData(outputData);

        task.setExternalOutputPayloadStoragePath("/path/to/output");
        task.setSubWorkflowId("subworkflow123");
        task.setStatus(Task.Status.COMPLETED);

        TaskResult result = new TaskResult(task);

        assertEquals("workflow123", result.getWorkflowInstanceId());
        assertEquals("task123", result.getTaskId());
        assertEquals("Task not completed", result.getReasonForIncompletion());
        assertEquals(60, result.getCallbackAfterSeconds());
        assertEquals("worker123", result.getWorkerId());
        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(outputData, result.getOutputData());
        assertEquals("/path/to/output", result.getExternalOutputPayloadStoragePath());
        assertEquals("subworkflow123", result.getSubWorkflowId());
    }

    @Test
    void testTaskConstructorWithCanceledStatus() {
        Task task = new Task();
        task.setStatus(Task.Status.CANCELED);

        TaskResult result = new TaskResult(task);

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
    }

    @Test
    void testTaskConstructorWithCompletedWithErrorsStatus() {
        Task task = new Task();
        task.setStatus(Task.Status.COMPLETED_WITH_ERRORS);

        TaskResult result = new TaskResult(task);

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
    }

    @Test
    void testTaskConstructorWithTimedOutStatus() {
        Task task = new Task();
        task.setStatus(Task.Status.TIMED_OUT);

        TaskResult result = new TaskResult(task);

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
    }

    @Test
    void testTaskConstructorWithSkippedStatus() {
        Task task = new Task();
        task.setStatus(Task.Status.SKIPPED);

        TaskResult result = new TaskResult(task);

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
    }

    @Test
    void testTaskConstructorWithScheduledStatus() {
        Task task = new Task();
        task.setStatus(Task.Status.SCHEDULED);

        TaskResult result = new TaskResult(task);

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
    }

    @Test
    void testSettersAndGetters() {
        TaskResult result = new TaskResult();

        result.setWorkflowInstanceId("workflow123");
        result.setTaskId("task123");
        result.setReasonForIncompletion("Task not completed");
        result.setCallbackAfterSeconds(60);
        result.setWorkerId("worker123");
        result.setStatus(TaskResult.Status.COMPLETED);

        Map<String, Object> outputData = new HashMap<>();
        outputData.put("key1", "value1");
        result.setOutputData(outputData);

        result.setExternalOutputPayloadStoragePath("/path/to/output");
        result.setSubWorkflowId("subworkflow123");
        result.setExtendLease(true);

        assertEquals("workflow123", result.getWorkflowInstanceId());
        assertEquals("task123", result.getTaskId());
        assertEquals("Task not completed", result.getReasonForIncompletion());
        assertEquals(60, result.getCallbackAfterSeconds());
        assertEquals("worker123", result.getWorkerId());
        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(outputData, result.getOutputData());
        assertEquals("/path/to/output", result.getExternalOutputPayloadStoragePath());
        assertEquals("subworkflow123", result.getSubWorkflowId());
        assertTrue(result.isExtendLease());
    }

    @Test
    void testAddOutputData() {
        TaskResult result = new TaskResult();

        result.addOutputData("key1", "value1");
        result.addOutputData("key2", 123);

        assertEquals("value1", result.getOutputData().get("key1"));
        assertEquals(123, result.getOutputData().get("key2"));
    }

    @Test
    void testLog() {
        TaskResult result = new TaskResult();

        result.log("Log message 1");
        result.log("Log message 2");

        assertEquals(2, result.getLogs().size());
        assertEquals("Log message 1", result.getLogs().get(0).getLog());
        assertEquals("Log message 2", result.getLogs().get(1).getLog());
    }

    @Test
    void testToString() {
        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId("workflow123");
        result.setTaskId("task123");

        String toString = result.toString();

        assertTrue(toString.contains("workflowInstanceId='workflow123'"));
        assertTrue(toString.contains("taskId='task123'"));
    }

    @Test
    void testStaticComplete() {
        TaskResult result = TaskResult.complete();

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
    }

    @Test
    void testStaticFailed() {
        TaskResult result = TaskResult.failed();

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
    }

    @Test
    void testStaticFailedWithReason() {
        TaskResult result = TaskResult.failed("Error occurred");

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
        assertEquals("Error occurred", result.getReasonForIncompletion());
    }

    @Test
    void testStaticInProgress() {
        TaskResult result = TaskResult.inProgress();

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
    }

    @Test
    void testStaticNewTaskResult() {
        TaskResult result = TaskResult.newTaskResult(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);

        assertEquals(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, result.getStatus());
    }

    @Test
    void testReasonForIncompletionTruncation() {
        TaskResult result = new TaskResult();

        // Create a string longer than 500 characters
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longReason.append("a");
        }

        result.setReasonForIncompletion(longReason.toString());

        // Verify it gets truncated to 500 characters
        assertEquals(500, result.getReasonForIncompletion().length());
    }

    @Test
    void testSetLogs() {
        TaskResult result = new TaskResult();

        TaskExecLog log1 = new TaskExecLog("Log message 1");
        TaskExecLog log2 = new TaskExecLog("Log message 2");

        result.setLogs(java.util.Arrays.asList(log1, log2));

        assertEquals(2, result.getLogs().size());
        assertEquals("Log message 1", result.getLogs().get(0).getLog());
        assertEquals("Log message 2", result.getLogs().get(1).getLog());
    }
}