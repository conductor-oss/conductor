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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskPojoMethods {

    @Test
    void testDefaultConstructor() {
        Task task = new Task();
        assertNotNull(task);
        assertNotNull(task.getInputData());
        assertNotNull(task.getOutputData());
        assertTrue(task.getInputData().isEmpty());
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    void testTaskTypeGetterSetter() {
        Task task = new Task();
        String taskType = "HTTP";
        task.setTaskType(taskType);
        assertEquals(taskType, task.getTaskType());
    }

    @Test
    void testStatusGetterSetter() {
        Task task = new Task();
        Task.Status status = Task.Status.COMPLETED;
        task.setStatus(status);
        assertEquals(status, task.getStatus());
    }

    @Test
    void testInputDataGetterSetter() {
        Task task = new Task();
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("key1", "value1");
        inputData.put("key2", 123);
        task.setInputData(inputData);
        assertEquals(inputData, task.getInputData());
        assertEquals("value1", task.getInputData().get("key1"));
        assertEquals(123, task.getInputData().get("key2"));
    }

    @Test
    void testInputDataSetterWithNull() {
        Task task = new Task();
        task.setInputData(null);
        assertNotNull(task.getInputData());
        assertTrue(task.getInputData().isEmpty());
    }

    @Test
    void testReferenceTaskNameGetterSetter() {
        Task task = new Task();
        String refName = "task_ref_name";
        task.setReferenceTaskName(refName);
        assertEquals(refName, task.getReferenceTaskName());
    }

    @Test
    void testCorrelationIdGetterSetter() {
        Task task = new Task();
        String correlationId = "corr123";
        task.setCorrelationId(correlationId);
        assertEquals(correlationId, task.getCorrelationId());
    }

    @Test
    void testRetryCountGetterSetter() {
        Task task = new Task();
        int retryCount = 3;
        task.setRetryCount(retryCount);
        assertEquals(retryCount, task.getRetryCount());
    }

    @Test
    void testScheduledTimeGetterSetter() {
        Task task = new Task();
        long scheduledTime = System.currentTimeMillis();
        task.setScheduledTime(scheduledTime);
        assertEquals(scheduledTime, task.getScheduledTime());
    }

    @Test
    void testStartTimeGetterSetter() {
        Task task = new Task();
        long startTime = System.currentTimeMillis();
        task.setStartTime(startTime);
        assertEquals(startTime, task.getStartTime());
    }

    @Test
    void testEndTimeGetterSetter() {
        Task task = new Task();
        long endTime = System.currentTimeMillis();
        task.setEndTime(endTime);
        assertEquals(endTime, task.getEndTime());
    }

    @Test
    void testStartDelayInSecondsGetterSetter() {
        Task task = new Task();
        int startDelay = 60;
        task.setStartDelayInSeconds(startDelay);
        assertEquals(startDelay, task.getStartDelayInSeconds());
    }

    @Test
    void testRetriedTaskIdGetterSetter() {
        Task task = new Task();
        String retriedTaskId = "task123";
        task.setRetriedTaskId(retriedTaskId);
        assertEquals(retriedTaskId, task.getRetriedTaskId());
    }

    @Test
    void testSeqGetterSetter() {
        Task task = new Task();
        int seq = 5;
        task.setSeq(seq);
        assertEquals(seq, task.getSeq());
    }

    @Test
    void testUpdateTimeGetterSetter() {
        Task task = new Task();
        long updateTime = System.currentTimeMillis();
        task.setUpdateTime(updateTime);
        assertEquals(updateTime, task.getUpdateTime());
    }

    @Test
    void testQueueWaitTimeWithStartAndScheduledTime() {
        Task task = new Task();
        long now = System.currentTimeMillis();
        long scheduled = now - 2000;
        long start = now - 1000;
        task.setScheduledTime(scheduled);
        task.setStartTime(start);
        assertEquals(1000, task.getQueueWaitTime());
    }

    @Test
    void testQueueWaitTimeWithUpdateTimeAndCallback() {
        Task task = new Task();
        long now = System.currentTimeMillis();
        long scheduled = now - 5000;
        long start = now - 4000;
        long update = now - 3000;
        task.setScheduledTime(scheduled);
        task.setStartTime(start);
        task.setUpdateTime(update);
        task.setCallbackAfterSeconds(1);
        long expectedWaitTime = System.currentTimeMillis() - (update + 1000);
        // Using assertTrue with a range check because exact time can vary
        assertTrue(Math.abs(task.getQueueWaitTime() - expectedWaitTime) < 100);
    }

    @Test
    void testRetriedGetterSetter() {
        Task task = new Task();
        task.setRetried(true);
        assertTrue(task.isRetried());
        task.setRetried(false);
        assertFalse(task.isRetried());
    }

    @Test
    void testExecutedGetterSetter() {
        Task task = new Task();
        task.setExecuted(true);
        assertTrue(task.isExecuted());
        task.setExecuted(false);
        assertFalse(task.isExecuted());
    }

    @Test
    void testPollCountGetterSetter() {
        Task task = new Task();
        int pollCount = 3;
        task.setPollCount(pollCount);
        assertEquals(pollCount, task.getPollCount());
    }

    @Test
    void testIncrementPollCount() {
        Task task = new Task();
        task.setPollCount(2);
        task.incrementPollCount();
        assertEquals(3, task.getPollCount());
    }

    @Test
    void testCallbackFromWorkerGetterSetter() {
        Task task = new Task();
        assertTrue(task.isCallbackFromWorker()); // Default is true
        task.setCallbackFromWorker(false);
        assertFalse(task.isCallbackFromWorker());
    }

    @Test
    void testTaskDefNameGetterSetter() {
        Task task = new Task();
        String taskDefName = "task_def";
        task.setTaskDefName(taskDefName);
        assertEquals(taskDefName, task.getTaskDefName());
    }

    @Test
    void testTaskDefNameFallbackToTaskType() {
        Task task = new Task();
        String taskType = "HTTP";
        task.setTaskType(taskType);
        assertEquals(taskType, task.getTaskDefName());
    }

    @Test
    void testResponseTimeoutSecondsGetterSetter() {
        Task task = new Task();
        long timeout = 120L;
        task.setResponseTimeoutSeconds(timeout);
        assertEquals(timeout, task.getResponseTimeoutSeconds());
    }

    @Test
    void testWorkflowInstanceIdGetterSetter() {
        Task task = new Task();
        String workflowId = "workflow123";
        task.setWorkflowInstanceId(workflowId);
        assertEquals(workflowId, task.getWorkflowInstanceId());
    }

    @Test
    void testWorkflowTypeGetterSetter() {
        Task task = new Task();
        String workflowType = "SIMPLE";
        task.setWorkflowType(workflowType);
        assertEquals(workflowType, task.getWorkflowType());
    }

    @Test
    void testTaskIdGetterSetter() {
        Task task = new Task();
        String taskId = "task_id_123";
        task.setTaskId(taskId);
        assertEquals(taskId, task.getTaskId());
    }

    @Test
    void testReasonForIncompletionGetterSetter() {
        Task task = new Task();
        String reason = "Task timed out";
        task.setReasonForIncompletion(reason);
        assertEquals(reason, task.getReasonForIncompletion());
    }

    @Test
    void testReasonForIncompletionTruncation() {
        Task task = new Task();
        String longReason = "a".repeat(600);
        task.setReasonForIncompletion(longReason);
        assertEquals(500, task.getReasonForIncompletion().length());
    }

    @Test
    void testCallbackAfterSecondsGetterSetter() {
        Task task = new Task();
        long callback = 30L;
        task.setCallbackAfterSeconds(callback);
        assertEquals(callback, task.getCallbackAfterSeconds());
    }

    @Test
    void testWorkerIdGetterSetter() {
        Task task = new Task();
        String workerId = "worker_123";
        task.setWorkerId(workerId);
        assertEquals(workerId, task.getWorkerId());
    }

    @Test
    void testOutputDataGetterSetter() {
        Task task = new Task();
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("result", "success");
        outputData.put("count", 42);
        task.setOutputData(outputData);
        assertEquals(outputData, task.getOutputData());
        assertEquals("success", task.getOutputData().get("result"));
        assertEquals(42, task.getOutputData().get("count"));
    }

    @Test
    void testOutputDataSetterWithNull() {
        Task task = new Task();
        task.setOutputData(null);
        assertNotNull(task.getOutputData());
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    void testWorkflowTaskGetterSetter() {
        Task task = new Task();
        WorkflowTask workflowTask = new WorkflowTask();
        task.setWorkflowTask(workflowTask);
        assertEquals(workflowTask, task.getWorkflowTask());
    }

    @Test
    void testDomainGetterSetter() {
        Task task = new Task();
        String domain = "test-domain";
        task.setDomain(domain);
        assertEquals(domain, task.getDomain());
    }

    @Test
    void testTaskDefinitionGetter() {
        Task task = new Task();
        WorkflowTask workflowTask = new WorkflowTask();
        TaskDef taskDef = new TaskDef();
        workflowTask.setTaskDefinition(taskDef);
        task.setWorkflowTask(workflowTask);
        Optional<TaskDef> retrievedTaskDef = task.getTaskDefinition();
        assertTrue(retrievedTaskDef.isPresent());
        assertEquals(taskDef, retrievedTaskDef.get());
    }

    @Test
    void testTaskDefinitionGetterWithNullWorkflowTask() {
        Task task = new Task();
        Optional<TaskDef> retrievedTaskDef = task.getTaskDefinition();
        assertFalse(retrievedTaskDef.isPresent());
    }

    @Test
    void testRateLimitGetterSetters() {
        Task task = new Task();
        int rateLimit = 10;
        int frequency = 60;
        task.setRateLimitPerFrequency(rateLimit);
        task.setRateLimitFrequencyInSeconds(frequency);
        assertEquals(rateLimit, task.getRateLimitPerFrequency());
        assertEquals(frequency, task.getRateLimitFrequencyInSeconds());
    }

    @Test
    void testExternalPayloadPathGetterSetters() {
        Task task = new Task();
        String inputPath = "s3://bucket/input.json";
        String outputPath = "s3://bucket/output.json";
        task.setExternalInputPayloadStoragePath(inputPath);
        task.setExternalOutputPayloadStoragePath(outputPath);
        assertEquals(inputPath, task.getExternalInputPayloadStoragePath());
        assertEquals(outputPath, task.getExternalOutputPayloadStoragePath());
    }

    @Test
    void testIsolationGroupIdGetterSetter() {
        Task task = new Task();
        String isolationGroupId = "group1";
        task.setIsolationGroupId(isolationGroupId);
        assertEquals(isolationGroupId, task.getIsolationGroupId());
    }

    @Test
    void testExecutionNameSpaceGetterSetter() {
        Task task = new Task();
        String executionNameSpace = "ns1";
        task.setExecutionNameSpace(executionNameSpace);
        assertEquals(executionNameSpace, task.getExecutionNameSpace());
    }

    @Test
    void testIterationGetterSetter() {
        Task task = new Task();
        int iteration = 2;
        task.setIteration(iteration);
        assertEquals(iteration, task.getIteration());
    }

    @Test
    void testIsLoopOverTask() {
        Task task = new Task();
        task.setIteration(0);
        assertFalse(task.isLoopOverTask());
        task.setIteration(1);
        assertTrue(task.isLoopOverTask());
    }

    @Test
    void testWorkflowPriorityGetterSetter() {
        Task task = new Task();
        int priority = 100;
        task.setWorkflowPriority(priority);
        assertEquals(priority, task.getWorkflowPriority());
    }

    @Test
    void testSubworkflowChangedGetterSetter() {
        Task task = new Task();
        task.setSubworkflowChanged(true);
        assertTrue(task.isSubworkflowChanged());
        task.setSubworkflowChanged(false);
        assertFalse(task.isSubworkflowChanged());
    }

    @Test
    void testSubWorkflowIdGetterSetter() {
        Task task = new Task();
        String subWorkflowId = "subwf123";
        task.setSubWorkflowId(subWorkflowId);
        assertEquals(subWorkflowId, task.getSubWorkflowId());
    }

    @Test
    void testSubWorkflowIdFromOutputData() {
        Task task = new Task();
        String subWorkflowId = "subwf123";
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("subWorkflowId", subWorkflowId);
        task.setOutputData(outputData);
        assertEquals(subWorkflowId, task.getSubWorkflowId());
    }

    @Test
    void testParentTaskIdGetterSetter() {
        Task task = new Task();
        String parentTaskId = "parent123";
        task.setParentTaskId(parentTaskId);
        assertEquals(parentTaskId, task.getParentTaskId());
    }

    @Test
    void testCopy() {
        Task original = new Task();
        original.setTaskType("HTTP");
        original.setStatus(Task.Status.COMPLETED);
        original.setCallbackAfterSeconds(30);
        original.setCallbackFromWorker(false);
        original.setCorrelationId("corr123");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("key1", "value1");
        original.setInputData(inputData);

        Map<String, Object> outputData = new HashMap<>();
        outputData.put("result", "success");
        original.setOutputData(outputData);

        original.setReferenceTaskName("task_ref");
        original.setStartDelayInSeconds(60);
        original.setTaskDefName("task_def");
        original.setWorkflowInstanceId("workflow123");
        original.setWorkflowType("SIMPLE");
        original.setResponseTimeoutSeconds(120);
        original.setRetryCount(2);
        original.setPollCount(3);
        original.setTaskId("task123");

        WorkflowTask workflowTask = new WorkflowTask();
        original.setWorkflowTask(workflowTask);

        original.setDomain("test-domain");
        original.setRateLimitPerFrequency(10);
        original.setRateLimitFrequencyInSeconds(60);
        original.setExternalInputPayloadStoragePath("s3://bucket/input.json");
        original.setExternalOutputPayloadStoragePath("s3://bucket/output.json");
        original.setWorkflowPriority(100);
        original.setIteration(2);
        original.setExecutionNameSpace("ns1");
        original.setIsolationGroupId("group1");
        original.setSubWorkflowId("subwf123");
        original.setSubworkflowChanged(true);
        original.setParentTaskId("parent123");

        Task copy = original.copy();

        // Assert that all copied fields are equal
        assertEquals(original.getTaskType(), copy.getTaskType());
        assertEquals(original.getStatus(), copy.getStatus());
        assertEquals(original.getCallbackAfterSeconds(), copy.getCallbackAfterSeconds());
        assertEquals(original.isCallbackFromWorker(), copy.isCallbackFromWorker());
        assertEquals(original.getCorrelationId(), copy.getCorrelationId());
        assertEquals(original.getInputData(), copy.getInputData());
        assertEquals(original.getOutputData(), copy.getOutputData());
        assertEquals(original.getReferenceTaskName(), copy.getReferenceTaskName());
        assertEquals(original.getStartDelayInSeconds(), copy.getStartDelayInSeconds());
        assertEquals(original.getTaskDefName(), copy.getTaskDefName());
        assertEquals(original.getWorkflowInstanceId(), copy.getWorkflowInstanceId());
        assertEquals(original.getWorkflowType(), copy.getWorkflowType());
        assertEquals(original.getResponseTimeoutSeconds(), copy.getResponseTimeoutSeconds());
        assertEquals(original.getRetryCount(), copy.getRetryCount());
        assertEquals(original.getPollCount(), copy.getPollCount());
        assertEquals(original.getTaskId(), copy.getTaskId());
        assertEquals(original.getWorkflowTask(), copy.getWorkflowTask());
        assertEquals(original.getDomain(), copy.getDomain());
        assertEquals(original.getRateLimitPerFrequency(), copy.getRateLimitPerFrequency());
        assertEquals(original.getRateLimitFrequencyInSeconds(), copy.getRateLimitFrequencyInSeconds());
        assertEquals(original.getExternalInputPayloadStoragePath(), copy.getExternalInputPayloadStoragePath());
        assertEquals(original.getExternalOutputPayloadStoragePath(), copy.getExternalOutputPayloadStoragePath());
        assertEquals(original.getWorkflowPriority(), copy.getWorkflowPriority());
        assertEquals(original.getIteration(), copy.getIteration());
        assertEquals(original.getExecutionNameSpace(), copy.getExecutionNameSpace());
        assertEquals(original.getIsolationGroupId(), copy.getIsolationGroupId());
        assertEquals(original.getSubWorkflowId(), copy.getSubWorkflowId());
        assertEquals(original.isSubworkflowChanged(), copy.isSubworkflowChanged());
        assertEquals(original.getParentTaskId(), copy.getParentTaskId());
    }

    @Test
    void testDeepCopy() {
        Task original = new Task();
        original.setTaskType("HTTP");
        original.setStatus(Task.Status.COMPLETED);
        original.setStartTime(System.currentTimeMillis());
        original.setScheduledTime(System.currentTimeMillis() - 1000);
        original.setEndTime(System.currentTimeMillis() + 1000);
        original.setWorkerId("worker123");
        original.setReasonForIncompletion("Completed successfully");
        original.setSeq(5);
        original.setParentTaskId("parent123");

        Task deepCopy = original.deepCopy();

        // Assert that all deep copied fields are equal
        assertEquals(original.getStartTime(), deepCopy.getStartTime());
        assertEquals(original.getScheduledTime(), deepCopy.getScheduledTime());
        assertEquals(original.getEndTime(), deepCopy.getEndTime());
        assertEquals(original.getWorkerId(), deepCopy.getWorkerId());
        assertEquals(original.getReasonForIncompletion(), deepCopy.getReasonForIncompletion());
        assertEquals(original.getSeq(), deepCopy.getSeq());
        assertEquals(original.getParentTaskId(), deepCopy.getParentTaskId());
    }

    @Test
    void testToString() {
        Task task = new Task();
        task.setTaskType("HTTP");
        task.setStatus(Task.Status.COMPLETED);

        String toString = task.toString();

        // Just verify it contains some expected content
        assertTrue(toString.contains("taskType='HTTP'"));
        assertTrue(toString.contains("status=" + Task.Status.COMPLETED));
    }

    @Test
    void testEqualsAndHashCode() {
        Task task1 = new Task();
        task1.setTaskId("task123");
        task1.setTaskType("HTTP");

        Task task2 = new Task();
        task2.setTaskId("task123");
        task2.setTaskType("HTTP");

        Task differentTask = new Task();
        differentTask.setTaskId("task456");
        differentTask.setTaskType("SIMPLE");

        // Test equals
        assertEquals(task1, task2);
        assertNotEquals(task1, differentTask);

        // Test hashCode
        assertEquals(task1.hashCode(), task2.hashCode());
        assertNotEquals(task1.hashCode(), differentTask.hashCode());
    }

    @Test
    void testStatusTerminalSuccessfulRetriable() {
        // Test terminal statuses
        assertTrue(Task.Status.COMPLETED.isTerminal());
        assertTrue(Task.Status.COMPLETED_WITH_ERRORS.isTerminal());
        assertTrue(Task.Status.FAILED.isTerminal());
        assertTrue(Task.Status.CANCELED.isTerminal());
        assertTrue(Task.Status.TIMED_OUT.isTerminal());
        assertTrue(Task.Status.SKIPPED.isTerminal());
        assertTrue(Task.Status.FAILED_WITH_TERMINAL_ERROR.isTerminal());
        assertFalse(Task.Status.IN_PROGRESS.isTerminal());
        assertFalse(Task.Status.SCHEDULED.isTerminal());

        // Test successful statuses
        assertTrue(Task.Status.COMPLETED.isSuccessful());
        assertTrue(Task.Status.COMPLETED_WITH_ERRORS.isSuccessful());
        assertTrue(Task.Status.SKIPPED.isSuccessful());
        assertFalse(Task.Status.FAILED.isSuccessful());
        assertFalse(Task.Status.CANCELED.isSuccessful());
        assertFalse(Task.Status.TIMED_OUT.isSuccessful());
        assertFalse(Task.Status.FAILED_WITH_TERMINAL_ERROR.isSuccessful());

        // Test retriable statuses
        assertTrue(Task.Status.FAILED.isRetriable());
        assertTrue(Task.Status.TIMED_OUT.isRetriable());
        assertTrue(Task.Status.IN_PROGRESS.isRetriable());
        assertTrue(Task.Status.SCHEDULED.isRetriable());
        assertFalse(Task.Status.FAILED_WITH_TERMINAL_ERROR.isRetriable());
        assertFalse(Task.Status.CANCELED.isRetriable());
        assertFalse(Task.Status.SKIPPED.isRetriable());
    }
}