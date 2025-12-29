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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.conductoross.conductor.model.SignalResponse;
import org.conductoross.conductor.model.TaskRun;
import org.conductoross.conductor.model.WorkflowRun;
import org.conductoross.conductor.model.WorkflowSignalReturnStrategy;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NotificationResultTest {

    private WorkflowModel targetWorkflow;
    private WorkflowModel blockingWorkflow;
    private TaskModel blockingTask;
    private String requestId;

    @Before
    public void setUp() {
        requestId = "req123";

        // Create target workflow
        targetWorkflow = createWorkflow("workflow123", WorkflowModel.Status.RUNNING);

        // Create blocking workflow (could be a sub-workflow)
        blockingWorkflow = createWorkflow("blockingWorkflow456", WorkflowModel.Status.RUNNING);

        // Create a blocking task
        blockingTask = createTask("blockingTask1", "WAIT", TaskModel.Status.IN_PROGRESS);
    }

    @Test
    public void testToResponse_EmptyBlockingTasks_TargetWorkflowStrategy() {
        // Given
        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(new ArrayList<>())
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.TARGET_WORKFLOW, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof WorkflowRun);
        WorkflowRun workflowRun = (WorkflowRun) response;
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getWorkflowId());
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getTargetWorkflowId());
        assertEquals(requestId, workflowRun.getRequestId());
        assertEquals(WorkflowSignalReturnStrategy.TARGET_WORKFLOW, workflowRun.getResponseType());
    }

    @Test
    public void testToResponse_EmptyBlockingTasks_BlockingWorkflowStrategy() {
        // Given
        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(new ArrayList<>())
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_WORKFLOW, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof WorkflowRun);
        WorkflowRun workflowRun = (WorkflowRun) response;
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getWorkflowId());
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getTargetWorkflowId());
    }

    @Test
    public void testToResponse_EmptyBlockingTasks_BlockingTaskStrategy() {
        // Given
        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(new ArrayList<>())
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_TASK, requestId);

        // Then
        assertNull(response); // Should return null for BLOCKING_TASK when no tasks present
    }

    @Test
    public void testToResponse_EmptyBlockingTasks_BlockingTaskInputStrategy() {
        // Given
        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(new ArrayList<>())
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_TASK_INPUT, requestId);

        // Then
        assertNull(response); // Should return null for BLOCKING_TASK_INPUT when no tasks present
    }

    @Test
    public void testToResponse_EmptyBlockingTasks_NullBlockingTasks() {
        // Given
        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(null)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.TARGET_WORKFLOW, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof WorkflowRun);
    }

    @Test
    public void testToResponse_WithBlockingTasks_TargetWorkflowStrategy() {
        // Given
        List<TaskModel> blockingTasks = new ArrayList<>();
        blockingTasks.add(blockingTask);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.TARGET_WORKFLOW, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof WorkflowRun);
        WorkflowRun workflowRun = (WorkflowRun) response;
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getWorkflowId());
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getTargetWorkflowId());
        assertEquals(requestId, workflowRun.getRequestId());
        assertEquals(WorkflowSignalReturnStrategy.TARGET_WORKFLOW, workflowRun.getResponseType());
    }

    @Test
    public void testToResponse_WithBlockingTasks_BlockingWorkflowStrategy() {
        // Given
        List<TaskModel> blockingTasks = new ArrayList<>();
        blockingTasks.add(blockingTask);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_WORKFLOW, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof WorkflowRun);
        WorkflowRun workflowRun = (WorkflowRun) response;
        assertEquals(blockingWorkflow.getWorkflowId(), workflowRun.getWorkflowId());
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getTargetWorkflowId());
        assertEquals(requestId, workflowRun.getRequestId());
        assertEquals(WorkflowSignalReturnStrategy.BLOCKING_WORKFLOW, workflowRun.getResponseType());
    }

    @Test
    public void testToResponse_WithBlockingTasks_BlockingTaskStrategy() {
        // Given
        List<TaskModel> blockingTasks = new ArrayList<>();
        blockingTasks.add(blockingTask);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_TASK, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof TaskRun);
        TaskRun taskRun = (TaskRun) response;
        assertEquals(blockingTask.getTaskId(), taskRun.getTaskId());
        assertEquals(blockingTask.getReferenceTaskName(), taskRun.getReferenceTaskName());
        assertEquals(targetWorkflow.getWorkflowId(), taskRun.getTargetWorkflowId());
        assertEquals(requestId, taskRun.getRequestId());
        assertEquals(WorkflowSignalReturnStrategy.BLOCKING_TASK, taskRun.getResponseType());
    }

    @Test
    public void testToResponse_WithBlockingTasks_BlockingTaskInputStrategy() {
        // Given
        List<TaskModel> blockingTasks = new ArrayList<>();
        blockingTasks.add(blockingTask);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_TASK_INPUT, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof TaskRun);
        TaskRun taskRun = (TaskRun) response;
        assertEquals(blockingTask.getTaskId(), taskRun.getTaskId());
        assertEquals(blockingTask.getReferenceTaskName(), taskRun.getReferenceTaskName());
        assertEquals(targetWorkflow.getWorkflowId(), taskRun.getTargetWorkflowId());
        assertEquals(requestId, taskRun.getRequestId());
        assertEquals(WorkflowSignalReturnStrategy.BLOCKING_TASK_INPUT, taskRun.getResponseType());
    }

    @Test
    public void testToResponse_WithMultipleBlockingTasks() {
        // Given - Multiple blocking tasks, should return first one
        List<TaskModel> blockingTasks = new ArrayList<>();
        TaskModel task1 = createTask("task1", "WAIT", TaskModel.Status.IN_PROGRESS);
        TaskModel task2 = createTask("task2", "SIMPLE", TaskModel.Status.COMPLETED);
        blockingTasks.add(task1);
        blockingTasks.add(task2);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_TASK, requestId);

        // Then
        assertNotNull(response);
        assertTrue(response instanceof TaskRun);
        TaskRun taskRun = (TaskRun) response;
        assertEquals(task1.getTaskId(), taskRun.getTaskId()); // Should return first task
        assertEquals(task1.getReferenceTaskName(), taskRun.getReferenceTaskName());
    }

    @Test
    public void testGetWorkflowRun_AllFieldsSet() {
        // Given
        List<TaskModel> blockingTasks = new ArrayList<>();
        blockingTasks.add(blockingTask);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.TARGET_WORKFLOW, requestId);

        // Then
        WorkflowRun workflowRun = (WorkflowRun) response;
        assertEquals(targetWorkflow.getWorkflowId(), workflowRun.getWorkflowId());
        assertEquals(requestId, workflowRun.getRequestId());
        assertEquals(targetWorkflow.getCorrelationId(), workflowRun.getCorrelationId());
        assertEquals(targetWorkflow.getInput(), workflowRun.getInput());
        assertEquals(targetWorkflow.getOutput(), workflowRun.getOutput());
        assertEquals(targetWorkflow.getCreatedBy(), workflowRun.getCreatedBy());
        assertEquals(targetWorkflow.getPriority(), workflowRun.getPriority());
        assertEquals(targetWorkflow.getVariables(), workflowRun.getVariables());
        assertEquals(
                Workflow.WorkflowStatus.valueOf(targetWorkflow.getStatus().name()),
                workflowRun.getStatus());
        assertNotNull(workflowRun.getTasks());
        assertEquals(targetWorkflow.getTasks().size(), workflowRun.getTasks().size());
    }

    @Test
    public void testToTaskRun_AllFieldsSet() {
        // Given
        TaskModel task = createTask("testTask", "SIMPLE", TaskModel.Status.COMPLETED);
        task.setTaskDefName("simpleTaskDef");
        task.setWorkflowType("testWorkflowType");
        task.setReasonForIncompletion("N/A");
        task.setWorkflowPriority(5);
        task.setCorrelationId("corr456");
        task.setWorkerId("worker123");
        task.setRetryCount(2);
        task.setRetriedTaskId("retriedTask789");

        List<TaskModel> blockingTasks = new ArrayList<>();
        blockingTasks.add(task);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.BLOCKING_TASK, requestId);

        // Then
        TaskRun taskRun = (TaskRun) response;
        assertEquals(task.getTaskType(), taskRun.getTaskType());
        assertEquals(task.getTaskId(), taskRun.getTaskId());
        assertEquals(task.getReferenceTaskName(), taskRun.getReferenceTaskName());
        assertEquals(task.getRetryCount(), taskRun.getRetryCount());
        assertEquals(task.getTaskDefName(), taskRun.getTaskDefName());
        assertEquals(task.getRetriedTaskId(), taskRun.getRetriedTaskId());
        assertEquals(task.getWorkflowType(), taskRun.getWorkflowType());
        assertEquals(task.getReasonForIncompletion(), taskRun.getReasonForIncompletion());
        assertEquals(task.getWorkflowPriority(), taskRun.getPriority());
        assertEquals(task.getWorkflowInstanceId(), taskRun.getWorkflowId());
        assertEquals(task.getCorrelationId(), taskRun.getCorrelationId());
        assertEquals(task.getStatus().name(), taskRun.getStatus().name());
        assertEquals(task.getInputData(), taskRun.getInput());
        assertEquals(task.getOutputData(), taskRun.getOutput());
        assertEquals(task.getWorkerId(), taskRun.getCreatedBy());
        assertEquals(task.getStartTime(), taskRun.getCreateTime());
        assertEquals(task.getUpdateTime(), taskRun.getUpdateTime());
        assertEquals(targetWorkflow.getWorkflowId(), taskRun.getTargetWorkflowId());
        assertEquals(targetWorkflow.getStatus().toString(), taskRun.getTargetWorkflowStatus());
        assertEquals(requestId, taskRun.getRequestId());
    }

    @Test
    public void testBuilder_AllFields() {
        // Given / When
        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(List.of(blockingTask))
                        .build();

        // Then
        assertNotNull(result);
        assertEquals(targetWorkflow, result.getTargetWorkflow());
        assertEquals(blockingWorkflow, result.getBlockingWorkflow());
        assertNotNull(result.getBlockingTasks());
        assertEquals(1, result.getBlockingTasks().size());
    }

    @Test
    public void testWorkflowRun_TasksCopiedCorrectly() {
        // Given
        TaskModel task1 = createTask("task1", "SIMPLE", TaskModel.Status.COMPLETED);
        TaskModel task2 = createTask("task2", "WAIT", TaskModel.Status.IN_PROGRESS);
        targetWorkflow.getTasks().add(task1);
        targetWorkflow.getTasks().add(task2);

        List<TaskModel> blockingTasks = new ArrayList<>();
        blockingTasks.add(task1);

        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(targetWorkflow)
                        .blockingWorkflow(blockingWorkflow)
                        .blockingTasks(blockingTasks)
                        .build();

        // When
        SignalResponse response =
                result.toResponse(WorkflowSignalReturnStrategy.TARGET_WORKFLOW, requestId);

        // Then
        WorkflowRun workflowRun = (WorkflowRun) response;
        assertNotNull(workflowRun.getTasks());
        assertEquals(2, workflowRun.getTasks().size());
        assertEquals(
                task1.getReferenceTaskName(), workflowRun.getTasks().get(0).getReferenceTaskName());
        assertEquals(
                task2.getReferenceTaskName(), workflowRun.getTasks().get(1).getReferenceTaskName());
    }

    // Helper methods
    private WorkflowModel createWorkflow(String workflowId, WorkflowModel.Status status) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setStatus(status);
        workflow.setCorrelationId("corr123");
        workflow.setInput(new HashMap<>());
        workflow.getInput().put("inputKey", "inputValue");
        workflow.setOutput(new HashMap<>());
        workflow.getOutput().put("outputKey", "outputValue");
        workflow.setTasks(new ArrayList<>());
        workflow.setCreatedBy("testUser");
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setUpdatedTime(System.currentTimeMillis());
        workflow.setPriority(0);
        workflow.setVariables(new HashMap<>());
        workflow.getVariables().put("var1", "value1");
        return workflow;
    }

    private TaskModel createTask(
            String referenceTaskName, String taskType, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-" + referenceTaskName);
        task.setReferenceTaskName(referenceTaskName);
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setWorkflowInstanceId("workflow123");
        task.setCorrelationId("corr123");
        task.setInputData(new HashMap<>());
        task.getInputData().put("inputKey", "inputValue");
        task.setOutputData(new HashMap<>());
        task.getOutputData().put("outputKey", "outputValue");
        task.setTaskDefName(taskType);
        task.setWorkflowType("testWorkflow");
        task.setWorkerId("worker1");
        task.setStartTime(System.currentTimeMillis());
        task.setUpdateTime(System.currentTimeMillis());
        return task;
    }
}
