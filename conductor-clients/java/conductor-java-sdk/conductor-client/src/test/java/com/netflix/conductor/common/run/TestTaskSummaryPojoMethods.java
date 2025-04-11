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
package com.netflix.conductor.common.run;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskSummaryPojoMethods {

    @Test
    void testWorkflowIdGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String workflowId = "workflow-123";
        taskSummary.setWorkflowId(workflowId);
        assertEquals(workflowId, taskSummary.getWorkflowId());
    }

    @Test
    void testWorkflowTypeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String workflowType = "SIMPLE_WORKFLOW";
        taskSummary.setWorkflowType(workflowType);
        assertEquals(workflowType, taskSummary.getWorkflowType());
    }

    @Test
    void testCorrelationIdGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String correlationId = "corr-123";
        taskSummary.setCorrelationId(correlationId);
        assertEquals(correlationId, taskSummary.getCorrelationId());
    }

    @Test
    void testScheduledTimeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String scheduledTime = "2023-10-15T10:00:00Z";
        taskSummary.setScheduledTime(scheduledTime);
        assertEquals(scheduledTime, taskSummary.getScheduledTime());
    }

    @Test
    void testStartTimeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String startTime = "2023-10-15T10:05:00Z";
        taskSummary.setStartTime(startTime);
        assertEquals(startTime, taskSummary.getStartTime());
    }

    @Test
    void testUpdateTimeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String updateTime = "2023-10-15T10:10:00Z";
        taskSummary.setUpdateTime(updateTime);
        assertEquals(updateTime, taskSummary.getUpdateTime());
    }

    @Test
    void testEndTimeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String endTime = "2023-10-15T10:15:00Z";
        taskSummary.setEndTime(endTime);
        assertEquals(endTime, taskSummary.getEndTime());
    }

    @Test
    void testStatusGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        Task.Status status = Task.Status.COMPLETED;
        taskSummary.setStatus(status);
        assertEquals(status, taskSummary.getStatus());
    }

    @Test
    void testReasonForIncompletionGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String reasonForIncompletion = "Task timeout";
        taskSummary.setReasonForIncompletion(reasonForIncompletion);
        assertEquals(reasonForIncompletion, taskSummary.getReasonForIncompletion());
    }

    @Test
    void testExecutionTimeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        long executionTime = 5000L;
        taskSummary.setExecutionTime(executionTime);
        assertEquals(executionTime, taskSummary.getExecutionTime());
    }

    @Test
    void testQueueWaitTimeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        long queueWaitTime = 2000L;
        taskSummary.setQueueWaitTime(queueWaitTime);
        assertEquals(queueWaitTime, taskSummary.getQueueWaitTime());
    }

    @Test
    void testTaskDefNameGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String taskDefName = "process_data";
        taskSummary.setTaskDefName(taskDefName);
        assertEquals(taskDefName, taskSummary.getTaskDefName());
    }

    @Test
    void testTaskTypeGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String taskType = "SIMPLE";
        taskSummary.setTaskType(taskType);
        assertEquals(taskType, taskSummary.getTaskType());
    }

    @Test
    void testInputGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String input = "{\"key\":\"value\"}";
        taskSummary.setInput(input);
        assertEquals(input, taskSummary.getInput());
    }

    @Test
    void testOutputGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String output = "{\"result\":\"success\"}";
        taskSummary.setOutput(output);
        assertEquals(output, taskSummary.getOutput());
    }

    @Test
    void testTaskIdGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String taskId = "task-456";
        taskSummary.setTaskId(taskId);
        assertEquals(taskId, taskSummary.getTaskId());
    }

    @Test
    void testExternalInputPayloadStoragePathGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String path = "s3://bucket/inputs/task-456";
        taskSummary.setExternalInputPayloadStoragePath(path);
        assertEquals(path, taskSummary.getExternalInputPayloadStoragePath());
    }

    @Test
    void testExternalOutputPayloadStoragePathGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        String path = "s3://bucket/outputs/task-456";
        taskSummary.setExternalOutputPayloadStoragePath(path);
        assertEquals(path, taskSummary.getExternalOutputPayloadStoragePath());
    }

    @Test
    void testWorkflowPriorityGetterSetter() {
        TaskSummary taskSummary = new TaskSummary();
        int priority = 10;
        taskSummary.setWorkflowPriority(priority);
        assertEquals(priority, taskSummary.getWorkflowPriority());
    }

    @Test
    void testEqualsAndHashCode() {
        TaskSummary taskSummary1 = new TaskSummary();
        TaskSummary taskSummary2 = new TaskSummary();

        // Initially both should be equal
        assertEquals(taskSummary1, taskSummary2);
        assertEquals(taskSummary1.hashCode(), taskSummary2.hashCode());

        // Modify one object
        taskSummary1.setTaskId("task-123");

        // Now they should be different
        assertNotEquals(taskSummary1, taskSummary2);

        // Make them equal again
        taskSummary2.setTaskId("task-123");
        assertEquals(taskSummary1, taskSummary2);
        assertEquals(taskSummary1.hashCode(), taskSummary2.hashCode());
    }

    @Test
    void testToString() {
        TaskSummary taskSummary = new TaskSummary();
        taskSummary.setTaskId("task-123");
        taskSummary.setWorkflowId("workflow-456");

        String toString = taskSummary.toString();

        // Verify toString contains key field values
        assertTrue(toString.contains("task-123"));
        assertTrue(toString.contains("workflow-456"));
    }
}