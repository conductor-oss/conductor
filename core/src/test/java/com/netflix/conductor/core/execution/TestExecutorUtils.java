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
package com.netflix.conductor.core.execution;

import java.time.Duration;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;

/**
 * Unit tests for ExecutorUtils.computePostpone() to ensure correct postpone duration calculation
 * for workflow rescheduling in the decider queue.
 */
public class TestExecutorUtils {

    private WorkflowModel workflow;
    private WorkflowDef workflowDef;
    private Duration workflowOffsetTimeout;
    private Duration maxPostponeDuration;

    @Before
    public void setup() {
        workflowDef = new WorkflowDef();
        workflowDef.setName("test_workflow");
        workflowDef.setTimeoutSeconds(0);

        workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setWorkflowId("test-workflow-id");

        workflowOffsetTimeout = Duration.ofSeconds(30);
        maxPostponeDuration = Duration.ofSeconds(300);
    }

    @Test
    public void testComputePostpone_WaitTaskWithTimeout() {
        // Create a WAIT task with specific wait timeout
        TaskModel waitTask = new TaskModel();
        waitTask.setTaskType(TaskType.TASK_TYPE_WAIT);
        waitTask.setStatus(TaskModel.Status.IN_PROGRESS);
        long futureTime = System.currentTimeMillis() + 60000; // 60 seconds in future
        waitTask.setWaitTimeout(futureTime);

        workflow.setTasks(List.of(waitTask));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should be capped at workflowOffsetTimeout (30 seconds) with jitter
        // Jitter can be ±1/3, so 30 ± 10 = [20, 40] seconds
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() >= 20);
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_WaitTaskWithZeroTimeout() {
        // Create a WAIT task without specific timeout
        TaskModel waitTask = new TaskModel();
        waitTask.setTaskType(TaskType.TASK_TYPE_WAIT);
        waitTask.setStatus(TaskModel.Status.IN_PROGRESS);
        waitTask.setWaitTimeout(0);

        workflow.setTasks(List.of(waitTask));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should return the default workflow offset timeout (30 seconds, with jitter)
        // Jitter can be ±1/3, so 30 ± 10 = [20, 40] seconds
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() >= 20);
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_HumanTask() {
        // HUMAN tasks should always use default workflowOffsetTimeout
        TaskModel humanTask = new TaskModel();
        humanTask.setTaskType("HUMAN");
        humanTask.setStatus(TaskModel.Status.IN_PROGRESS);
        humanTask.setStartTime(System.currentTimeMillis());

        workflow.setTasks(List.of(humanTask));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should return the default workflow offset timeout (30 seconds, with jitter)
        // Jitter can be ±1/3, so 30 ± 10 = [20, 40] seconds
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() >= 20);
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_InProgressTaskWithResponseTimeout() {
        // Task with response timeout
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test_task");
        taskDef.setResponseTimeoutSeconds(120L);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);

        TaskModel task = new TaskModel();
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setStartTime(System.currentTimeMillis() - 10000); // Started 10 seconds ago
        task.setResponseTimeoutSeconds(120);
        task.setWorkflowTask(workflowTask);

        workflow.setTasks(List.of(task));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should be approximately 120 - 10 + 1 = 111 seconds, but capped at workflowOffsetTimeout
        // (30s)
        // Then jitter: 30 ± 10 = [20, 40] seconds
        assertTrue(
                "Postpone should be capped at workflow offset (20-40s)", result.getSeconds() >= 20);
        assertTrue(
                "Postpone should be capped at workflow offset (20-40s)", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_ScheduledTaskWithPollTimeout() {
        // Task definition with poll timeout
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test_task");
        taskDef.setPollTimeoutSeconds(45);
        taskDef.setResponseTimeoutSeconds(300);
        taskDef.setTimeoutSeconds(600);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);

        TaskModel task = new TaskModel();
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setWorkflowTask(workflowTask);

        workflow.setTasks(List.of(task));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should use minimum of pollTimeout, responseTimeout, timeout = 45 seconds (with jitter)
        // But capped at workflowOffsetTimeout (30 seconds)
        // Jitter: 30 ± 10 = [20, 40] seconds
        assertTrue(
                "Postpone should be between 20-40 seconds (capped at workflow offset)",
                result.getSeconds() >= 20);
        assertTrue(
                "Postpone should be between 20-40 seconds (capped at workflow offset)",
                result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_ScheduledTaskWithWorkflowTimeout() {
        // Workflow with timeout, task without specific timeout
        workflowDef.setTimeoutSeconds(180);

        TaskModel task = new TaskModel();
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.SCHEDULED);

        workflow.setTasks(List.of(task));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should be capped at workflowOffsetTimeout (30 seconds with jitter)
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() >= 20);
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_MaxPostponeCapping() {
        // Test that postpone is capped at maxPostponeDuration
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test_task");
        taskDef.setResponseTimeoutSeconds(1000L); // Very large timeout

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);

        TaskModel task = new TaskModel();
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setStartTime(System.currentTimeMillis());
        task.setResponseTimeoutSeconds(1000);
        task.setWorkflowTask(workflowTask);

        workflow.setTasks(List.of(task));

        Duration shortMaxPostpone = Duration.ofSeconds(60);
        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, shortMaxPostpone);

        // Should be capped at workflowOffsetTimeout (30 seconds) which is smaller
        // Jitter: 30 ± 10 = [20, 40] seconds
        assertTrue("Postpone should be capped at workflow offset", result.getSeconds() >= 20);
        assertTrue("Postpone should be capped at workflow offset", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_NoTasksUsesDefault() {
        // No tasks in workflow
        workflow.setTasks(List.of());

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should return workflowOffsetTimeout with jitter
        // Jitter: 30 ± 10 = [20, 40] seconds
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() >= 20);
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_OnlyTerminalTasks() {
        // Only terminal tasks should use default
        TaskModel completedTask = new TaskModel();
        completedTask.setTaskType("SIMPLE");
        completedTask.setStatus(TaskModel.Status.COMPLETED);

        TaskModel failedTask = new TaskModel();
        failedTask.setTaskType("SIMPLE");
        failedTask.setStatus(TaskModel.Status.FAILED);

        workflow.setTasks(List.of(completedTask, failedTask));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should return workflowOffsetTimeout with jitter
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() >= 20);
        assertTrue("Postpone should be between 20-40 seconds", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_FirstNonTerminalTaskIsUsed() {
        // When multiple tasks exist, should use first non-terminal task
        TaskModel completedTask = new TaskModel();
        completedTask.setTaskType("SIMPLE");
        completedTask.setStatus(TaskModel.Status.COMPLETED);

        TaskModel waitTask = new TaskModel();
        waitTask.setTaskType(TaskType.TASK_TYPE_WAIT);
        waitTask.setStatus(TaskModel.Status.IN_PROGRESS);
        waitTask.setWaitTimeout(System.currentTimeMillis() + 45000); // 45 seconds

        TaskModel scheduledTask = new TaskModel();
        scheduledTask.setTaskType("SIMPLE");
        scheduledTask.setStatus(TaskModel.Status.SCHEDULED);

        workflow.setTasks(List.of(completedTask, waitTask, scheduledTask));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should use the WAIT task (first non-terminal), capped at workflowOffsetTimeout
        // Jitter: 30 ± 10 = [20, 40] seconds
        assertTrue("Postpone should be capped at workflow offset", result.getSeconds() >= 20);
        assertTrue("Postpone should be capped at workflow offset", result.getSeconds() <= 40);
    }

    @Test
    public void testApplyJitter_SmallDuration() {
        // Small durations (<=3) should not have jitter
        long result = ExecutorUtils.applyJitter(3);
        assertEquals("Small duration should not have jitter", 3, result);

        result = ExecutorUtils.applyJitter(2);
        assertEquals("Small duration should not have jitter", 2, result);

        result = ExecutorUtils.applyJitter(1);
        assertEquals("Small duration should not have jitter", 1, result);
    }

    @Test
    public void testApplyJitter_LargeDuration() {
        // Test jitter is within bounds for larger duration
        long duration = 90;
        long result = ExecutorUtils.applyJitter(duration);

        // Jitter should be ±1/3, so 90 ± 30 = [60, 120]
        assertTrue("Jitter should be within bounds", result >= 60);
        assertTrue("Jitter should be within bounds", result <= 120);
    }

    @Test
    public void testApplyJitter_MultipleCallsVary() {
        // Verify jitter actually produces different values (randomness check)
        long duration = 60;
        boolean foundDifferent = false;

        for (int i = 0; i < 100; i++) {
            long result = ExecutorUtils.applyJitter(duration);
            if (result != duration) {
                foundDifferent = true;
                break;
            }
        }

        assertTrue(
                "Jitter should produce varied results over multiple calls (within range)",
                foundDifferent);
    }

    @Test
    public void testComputePostpone_PastWaitTimeout() {
        // WAIT task with timeout in the past should return 0
        TaskModel waitTask = new TaskModel();
        waitTask.setTaskType(TaskType.TASK_TYPE_WAIT);
        waitTask.setStatus(TaskModel.Status.IN_PROGRESS);
        waitTask.setWaitTimeout(System.currentTimeMillis() - 10000); // 10 seconds in past

        workflow.setTasks(List.of(waitTask));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should use workflowOffsetTimeout since calculated postpone is 0
        assertTrue("Postpone should use default timeout", result.getSeconds() >= 20);
        assertTrue("Postpone should use default timeout", result.getSeconds() <= 40);
    }

    @Test
    public void testComputePostpone_ResponseTimeoutAlreadyExceeded() {
        // Task that has already exceeded its response timeout
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test_task");
        taskDef.setResponseTimeoutSeconds(30L);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);

        TaskModel task = new TaskModel();
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setStartTime(System.currentTimeMillis() - 60000); // Started 60 seconds ago
        task.setResponseTimeoutSeconds(30);
        task.setWorkflowTask(workflowTask);

        workflow.setTasks(List.of(task));

        Duration result =
                ExecutorUtils.computePostpone(workflow, workflowOffsetTimeout, maxPostponeDuration);

        // Should use default workflowOffsetTimeout
        assertTrue("Postpone should use default timeout", result.getSeconds() >= 20);
        assertTrue("Postpone should use default timeout", result.getSeconds() <= 40);
    }
}
