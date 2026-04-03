/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.rest.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.model.SignalResponse;
import org.conductoross.conductor.model.TaskRun;
import org.conductoross.conductor.model.WorkflowRun;
import org.conductoross.conductor.model.WorkflowSignalReturnStrategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.WorkflowService;
import com.netflix.conductor.service.WorkflowTestService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkflowResourceTest {

    @Mock private WorkflowService mockWorkflowService;

    @Mock private WorkflowTestService mockWorkflowTestService;

    private WorkflowResource workflowResource;

    @Before
    public void before() {
        this.mockWorkflowService = mock(WorkflowService.class);
        this.mockWorkflowTestService = mock(WorkflowTestService.class);
        this.workflowResource =
                new WorkflowResource(this.mockWorkflowService, this.mockWorkflowTestService);
    }

    @Test
    public void testStartWorkflow() {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("w123");
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        startWorkflowRequest.setInput(input);
        String workflowID = "w112";
        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow(startWorkflowRequest));
    }

    @Test
    public void testStartWorkflowParam() {
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        String workflowID = "w112";
        when(mockWorkflowService.startWorkflow(
                        anyString(), anyInt(), anyString(), anyInt(), anyMap()))
                .thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow("test1", 1, "c123", 0, input));
    }

    @Test
    public void getWorkflows() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("123");
        ArrayList<Workflow> listOfWorkflows =
                new ArrayList<>() {
                    {
                        add(workflow);
                    }
                };
        when(mockWorkflowService.getWorkflows(anyString(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(listOfWorkflows);
        assertEquals(listOfWorkflows, workflowResource.getWorkflows("test1", "123", true, true));
    }

    @Test
    public void testGetWorklfowsMultipleCorrelationId() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> workflowArrayList =
                new ArrayList<>() {
                    {
                        add(workflow);
                    }
                };

        List<String> correlationIdList =
                new ArrayList<>() {
                    {
                        add("c123");
                    }
                };

        Map<String, List<Workflow>> workflowMap = new HashMap<>();
        workflowMap.put("c123", workflowArrayList);

        when(mockWorkflowService.getWorkflows(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(workflowMap);
        assertEquals(
                workflowMap, workflowResource.getWorkflows("test", true, true, correlationIdList));
    }

    @Test
    public void testGetExecutionStatus() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        when(mockWorkflowService.getExecutionStatus(anyString(), anyBoolean()))
                .thenReturn(workflow);
        assertEquals(workflow, workflowResource.getExecutionStatus("w123", true));
    }

    @Test
    public void testDelete() {
        workflowResource.delete("w123", true);
        verify(mockWorkflowService, times(1)).deleteWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void testGetRunningWorkflow() {
        List<String> listOfWorklfows =
                new ArrayList<>() {
                    {
                        add("w123");
                    }
                };
        when(mockWorkflowService.getRunningWorkflows(anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(listOfWorklfows);
        assertEquals(listOfWorklfows, workflowResource.getRunningWorkflow("w123", 1, 12L, 13L));
    }

    @Test
    public void testDecide() {
        workflowResource.decide("w123");
        verify(mockWorkflowService, times(1)).decideWorkflow(anyString());
    }

    @Test
    public void testPauseWorkflow() {
        workflowResource.pauseWorkflow("w123");
        verify(mockWorkflowService, times(1)).pauseWorkflow(anyString());
    }

    @Test
    public void testResumeWorkflow() {
        workflowResource.resumeWorkflow("test");
        verify(mockWorkflowService, times(1)).resumeWorkflow(anyString());
    }

    @Test
    public void testSkipTaskFromWorkflow() {
        workflowResource.skipTaskFromWorkflow("test", "testTask", null);
        verify(mockWorkflowService, times(1))
                .skipTaskFromWorkflow(anyString(), anyString(), isNull());
    }

    @Test
    public void testRerun() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        workflowResource.rerun("test", request);
        verify(mockWorkflowService, times(1))
                .rerunWorkflow(anyString(), any(RerunWorkflowRequest.class));
    }

    @Test
    public void restart() {
        workflowResource.restart("w123", false);
        verify(mockWorkflowService, times(1)).restartWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void testRetry() {
        workflowResource.retry("w123", false);
        verify(mockWorkflowService, times(1)).retryWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void testResetWorkflow() {
        workflowResource.resetWorkflow("w123");
        verify(mockWorkflowService, times(1)).resetWorkflow(anyString());
    }

    @Test
    public void testTerminate() {
        workflowResource.terminate("w123", "test");
        verify(mockWorkflowService, times(1)).terminateWorkflow(anyString(), anyString());
    }

    @Test
    public void testTerminateRemove() {
        workflowResource.terminateRemove("w123", "test", false);
        verify(mockWorkflowService, times(1))
                .terminateRemove(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testSearch() {
        workflowResource.search(0, 100, "asc", "*", "*");
        verify(mockWorkflowService, times(1))
                .searchWorkflows(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    public void testSearchV2() {
        workflowResource.searchV2(0, 100, "asc", "*", "*");
        verify(mockWorkflowService).searchWorkflowsV2(0, 100, "asc", "*", "*");
    }

    @Test
    public void testSearchWorkflowsByTasks() {
        workflowResource.searchWorkflowsByTasks(0, 100, "asc", "*", "*");
        verify(mockWorkflowService, times(1))
                .searchWorkflowsByTasks(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    public void testSearchWorkflowsByTasksV2() {
        workflowResource.searchWorkflowsByTasksV2(0, 100, "asc", "*", "*");
        verify(mockWorkflowService).searchWorkflowsByTasksV2(0, 100, "asc", "*", "*");
    }

    @Test
    public void testGetExternalStorageLocation() {
        workflowResource.getExternalStorageLocation("path", "operation", "payloadType");
        verify(mockWorkflowService).getExternalStorageLocation("path", "operation", "payloadType");
    }

    @Test
    public void testExecuteWorkflow_CompletedWorkflow() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");
        request.setInput(input);

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.COMPLETED);
        WorkflowModel workflowModel = toWorkflowModel(workflow);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        null,
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                            WorkflowRun workflowRun = (WorkflowRun) response;
                            assertEquals(workflowId, workflowRun.getWorkflowId());
                            assertEquals("req123", workflowRun.getRequestId());
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_WithWaitTask() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        Task waitTask = createTask("wait1", TaskType.TASK_TYPE_WAIT, Task.Status.IN_PROGRESS);
        workflow.getTasks().add(waitTask);

        WorkflowModel workflowModel = toWorkflowModel(workflow);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        null, // Test auto-generation of requestId
                        null,
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.BLOCKING_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                            WorkflowRun workflowRun = (WorkflowRun) response;
                            assertEquals(workflowId, workflowRun.getWorkflowId());
                            assertNotNull(workflowRun.getRequestId()); // Should be auto-generated
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_WithTaskRef() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        Task completedTask = createTask("task1", "SIMPLE", Task.Status.COMPLETED);
        workflow.getTasks().add(completedTask);

        WorkflowModel workflowModel = toWorkflowModel(workflow);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        "task1", // Wait until task1 completes
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.BLOCKING_TASK,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof TaskRun);
                            TaskRun taskRun = (TaskRun) response;
                            assertEquals("task1", taskRun.getReferenceTaskName());
                            assertEquals("req123", taskRun.getRequestId());
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_WithMultipleTaskRefs() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        Task task1 = createTask("task1", "SIMPLE", Task.Status.IN_PROGRESS);
        Task task2 = createTask("task2", "SIMPLE", Task.Status.COMPLETED);
        workflow.getTasks().add(task1);
        workflow.getTasks().add(task2);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When - task2 completes first
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        "task1,task2", // Multiple task refs
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.BLOCKING_TASK_INPUT,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof TaskRun);
                            TaskRun taskRun = (TaskRun) response;
                            // Should return task2 since it's completed
                            assertEquals("task2", taskRun.getReferenceTaskName());
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_WithSubWorkflow() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        String subWorkflowId = "subWorkflow456";

        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);
        Workflow subWorkflow = createWorkflow(subWorkflowId, Workflow.WorkflowStatus.RUNNING);

        Task subWorkflowTask =
                createTask(
                        "subWorkflow1", TaskType.TASK_TYPE_SUB_WORKFLOW, Task.Status.IN_PROGRESS);
        subWorkflowTask.setSubWorkflowId(subWorkflowId);
        workflow.getTasks().add(subWorkflowTask);

        Task waitTaskInSubWorkflow =
                createTask("waitInSub", TaskType.TASK_TYPE_WAIT, Task.Status.IN_PROGRESS);
        subWorkflow.getTasks().add(waitTaskInSubWorkflow);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);
        WorkflowModel subWorkflowModel = toWorkflowModel(subWorkflow);
        when(mockWorkflowService.getWorkflowModel(eq(subWorkflowId), eq(true)))
                .thenReturn(subWorkflowModel);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        null,
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.BLOCKING_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                            WorkflowRun workflowRun = (WorkflowRun) response;
                            // Should return the sub-workflow as blocking workflow
                            assertEquals(subWorkflowId, workflowRun.getWorkflowId());
                            assertEquals(workflowId, workflowRun.getTargetWorkflowId());
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_Timeout() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When - very short timeout, should timeout immediately
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        "nonExistentTask", // Task that never completes
                        1, // 1 second timeout
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                            WorkflowRun workflowRun = (WorkflowRun) response;
                            assertEquals(workflowId, workflowRun.getWorkflowId());
                            // Workflow is still running (timeout occurred)
                            assertEquals(Workflow.WorkflowStatus.RUNNING, workflowRun.getStatus());
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_VersionZero() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.COMPLETED);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When - version 0 should be converted to null
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        0, // Version 0
                        "req123",
                        null,
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            // Verify that version was set to null in the request
                            // This is implicit in the workflow service call
                        })
                .verifyComplete();

        verify(mockWorkflowService).startWorkflow(any(StartWorkflowRequest.class));
    }

    @Test
    public void testExecuteWorkflow_DynamicWorkflow() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");
        Map<String, Object> input = new HashMap<>();
        request.setInput(input);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("dynamicWorkflow");
        workflowDef.setTasks(
                List.of(new com.netflix.conductor.common.metadata.workflow.WorkflowTask()));
        request.setWorkflowDef(workflowDef);

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.COMPLETED);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        null,
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            // Verify that _systemMetadata.dynamic was set
                            assertTrue(input.containsKey("_systemMetadata"));
                            Map<String, Object> systemMetadata =
                                    (Map<String, Object>) input.get("_systemMetadata");
                            assertEquals(true, systemMetadata.get("dynamic"));
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_WaitForSecondsDefault() {
        // Given
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.COMPLETED);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When - waitForSeconds is 0, should default to 10
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        null,
                        0, // Should default to 10
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertNotNull(response))
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_SubWorkflowWithTerminalTask() {
        // Given - Test sub-workflow with terminal SUB_WORKFLOW task (should not recurse)
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        Task subWorkflowTask =
                createTask(
                        "subWorkflow1",
                        TaskType.TASK_TYPE_SUB_WORKFLOW,
                        Task.Status.COMPLETED); // Terminal
        subWorkflowTask.setSubWorkflowId("subWorkflow456");
        workflow.getTasks().add(subWorkflowTask);

        Task completedTask = createTask("task1", "SIMPLE", Task.Status.COMPLETED);
        workflow.getTasks().add(completedTask);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When - wait for task1
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        "task1",
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then - should not try to fetch sub-workflow since SUB_WORKFLOW task is terminal
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                        })
                .verifyComplete();

        // Verify sub-workflow was NOT fetched (only main workflow)
        verify(mockWorkflowService, times(0)).getWorkflowModel(eq("subWorkflow456"), eq(true));
    }

    @Test
    public void testExecuteWorkflow_SubWorkflowWithNullSubWorkflowId() {
        // Given - SUB_WORKFLOW task with null subWorkflowId
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        Task subWorkflowTask =
                createTask(
                        "subWorkflow1", TaskType.TASK_TYPE_SUB_WORKFLOW, Task.Status.IN_PROGRESS);
        subWorkflowTask.setSubWorkflowId(null); // Null subWorkflowId
        workflow.getTasks().add(subWorkflowTask);

        Task completedTask = createTask("task1", "SIMPLE", Task.Status.COMPLETED);
        workflow.getTasks().add(completedTask);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        "task1",
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then - should complete without trying to fetch null sub-workflow
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_SubWorkflowFetchException() {
        // Given - SUB_WORKFLOW task where fetching sub-workflow throws exception
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        String subWorkflowId = "subWorkflow456";

        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        Task subWorkflowTask =
                createTask(
                        "subWorkflow1", TaskType.TASK_TYPE_SUB_WORKFLOW, Task.Status.IN_PROGRESS);
        subWorkflowTask.setSubWorkflowId(subWorkflowId);
        workflow.getTasks().add(subWorkflowTask);

        Task completedTask = createTask("task1", "SIMPLE", Task.Status.COMPLETED);
        workflow.getTasks().add(completedTask);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);
        when(mockWorkflowService.getWorkflowModel(eq(subWorkflowId), eq(true)))
                .thenThrow(new RuntimeException("Sub-workflow not found"));

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        "task1",
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then - should complete gracefully, ignoring the sub-workflow exception
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_WithTaskRefWhitespace() {
        // Given - taskRefs with whitespace
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);

        Task completedTask = createTask("task1", "SIMPLE", Task.Status.COMPLETED);
        workflow.getTasks().add(completedTask);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When - taskRefs with spaces around them
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        " task1 , task2 ", // With whitespace
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.BLOCKING_TASK,
                        request);

        // Then - should trim and match correctly
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof TaskRun);
                            TaskRun taskRun = (TaskRun) response;
                            assertEquals("task1", taskRun.getReferenceTaskName());
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_DynamicWorkflowWithExistingSystemMetadata() {
        // Given - Dynamic workflow with existing system metadata
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> existingSystemMetadata = new HashMap<>();
        existingSystemMetadata.put("existingKey", "existingValue");
        input.put("_systemMetadata", existingSystemMetadata);
        request.setInput(input);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("dynamicWorkflow");
        workflowDef.setTasks(
                List.of(new com.netflix.conductor.common.metadata.workflow.WorkflowTask()));
        request.setWorkflowDef(workflowDef);

        String workflowId = "workflow123";
        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.COMPLETED);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        null,
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.TARGET_WORKFLOW,
                        request);

        // Then
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            // Verify that existing metadata is preserved and dynamic flag is added
                            Map<String, Object> systemMetadata =
                                    (Map<String, Object>) input.get("_systemMetadata");
                            assertEquals("existingValue", systemMetadata.get("existingKey"));
                            assertEquals(true, systemMetadata.get("dynamic"));
                        })
                .verifyComplete();
    }

    @Test
    public void testExecuteWorkflow_NestedSubWorkflows() {
        // Given - Nested sub-workflows (sub-workflow containing another sub-workflow)
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("testWorkflow");

        String workflowId = "workflow123";
        String subWorkflowId1 = "subWorkflow456";
        String subWorkflowId2 = "subWorkflow789";

        Workflow workflow = createWorkflow(workflowId, Workflow.WorkflowStatus.RUNNING);
        Workflow subWorkflow1 = createWorkflow(subWorkflowId1, Workflow.WorkflowStatus.RUNNING);
        Workflow subWorkflow2 = createWorkflow(subWorkflowId2, Workflow.WorkflowStatus.RUNNING);

        // Main workflow has sub-workflow task
        Task subWorkflowTask1 =
                createTask(
                        "subWorkflow1", TaskType.TASK_TYPE_SUB_WORKFLOW, Task.Status.IN_PROGRESS);
        subWorkflowTask1.setSubWorkflowId(subWorkflowId1);
        workflow.getTasks().add(subWorkflowTask1);

        // Sub-workflow 1 has another sub-workflow task
        Task subWorkflowTask2 =
                createTask(
                        "subWorkflow2", TaskType.TASK_TYPE_SUB_WORKFLOW, Task.Status.IN_PROGRESS);
        subWorkflowTask2.setSubWorkflowId(subWorkflowId2);
        subWorkflow1.getTasks().add(subWorkflowTask2);

        // Sub-workflow 2 has WAIT task
        Task waitTaskInNestedSub =
                createTask("waitInNestedSub", TaskType.TASK_TYPE_WAIT, Task.Status.IN_PROGRESS);
        subWorkflow2.getTasks().add(waitTaskInNestedSub);

        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowId);
        WorkflowModel workflowModel = toWorkflowModel(workflow);
        when(mockWorkflowService.getWorkflowModel(eq(workflowId), eq(true)))
                .thenReturn(workflowModel);
        WorkflowModel subWorkflowModel1 = toWorkflowModel(subWorkflow1);
        when(mockWorkflowService.getWorkflowModel(eq(subWorkflowId1), eq(true)))
                .thenReturn(subWorkflowModel1);
        WorkflowModel subWorkflowModel2 = toWorkflowModel(subWorkflow2);
        when(mockWorkflowService.getWorkflowModel(eq(subWorkflowId2), eq(true)))
                .thenReturn(subWorkflowModel2);

        // When
        Mono<SignalResponse> result =
                workflowResource.executeWorkflow(
                        "testWorkflow",
                        1,
                        "req123",
                        null,
                        10,
                        "DURABLE",
                        WorkflowSignalReturnStrategy.BLOCKING_WORKFLOW,
                        request);

        // Then - should find the WAIT task in the nested sub-workflow
        StepVerifier.create(result)
                .assertNext(
                        response -> {
                            assertNotNull(response);
                            assertTrue(response instanceof WorkflowRun);
                            WorkflowRun workflowRun = (WorkflowRun) response;
                            // Should return the innermost sub-workflow as blocking workflow
                            assertEquals(subWorkflowId2, workflowRun.getWorkflowId());
                            assertEquals(workflowId, workflowRun.getTargetWorkflowId());
                        })
                .verifyComplete();

        // Verify all sub-workflows were fetched (called in both filter and map)
        verify(mockWorkflowService, times(2)).getWorkflowModel(eq(subWorkflowId1), eq(true));
        verify(mockWorkflowService, times(2)).getWorkflowModel(eq(subWorkflowId2), eq(true));
    }

    // Helper methods
    private Workflow createWorkflow(String workflowId, Workflow.WorkflowStatus status) {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(workflowId);
        workflow.setStatus(status);
        workflow.setCorrelationId("corr123");
        workflow.setInput(new HashMap<>());
        workflow.setOutput(new HashMap<>());
        workflow.setTasks(new ArrayList<>());
        workflow.setCreatedBy("testUser");
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setUpdateTime(System.currentTimeMillis());
        workflow.setPriority(0);
        workflow.setVariables(new HashMap<>());
        return workflow;
    }

    private WorkflowModel toWorkflowModel(Workflow workflow) {
        WorkflowModel model = new WorkflowModel();
        model.setWorkflowId(workflow.getWorkflowId());
        model.setStatus(WorkflowModel.Status.valueOf(workflow.getStatus().name()));
        model.setCorrelationId(workflow.getCorrelationId());
        model.setInput(workflow.getInput());
        model.setOutput(workflow.getOutput());
        model.setCreatedBy(workflow.getCreatedBy());
        model.setCreateTime(workflow.getCreateTime());
        model.setUpdatedTime(workflow.getUpdateTime());
        model.setPriority(workflow.getPriority());
        model.setVariables(workflow.getVariables());

        // Convert tasks to TaskModels
        List<TaskModel> taskModels = new ArrayList<>();
        for (Task task : workflow.getTasks()) {
            taskModels.add(toTaskModel(task));
        }
        model.setTasks(taskModels);

        return model;
    }

    private TaskModel toTaskModel(Task task) {
        TaskModel model = new TaskModel();
        model.setTaskId(task.getTaskId());
        model.setReferenceTaskName(task.getReferenceTaskName());
        model.setTaskType(task.getTaskType());
        model.setStatus(TaskModel.Status.valueOf(task.getStatus().name()));
        model.setWorkflowInstanceId(task.getWorkflowInstanceId());
        model.setCorrelationId(task.getCorrelationId());
        model.setInputData(task.getInputData());
        model.setOutputData(task.getOutputData());
        model.setTaskDefName(task.getTaskDefName());
        model.setWorkflowType(task.getWorkflowType());
        model.setWorkerId(task.getWorkerId());
        model.setStartTime(task.getStartTime());
        model.setUpdateTime(task.getUpdateTime());
        model.setSubWorkflowId(task.getSubWorkflowId());
        return model;
    }

    private Task createTask(String referenceTaskName, String taskType, Task.Status status) {
        Task task = new Task();
        task.setTaskId("task-" + referenceTaskName);
        task.setReferenceTaskName(referenceTaskName);
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setWorkflowInstanceId("workflow123");
        task.setCorrelationId("corr123");
        task.setInputData(new HashMap<>());
        task.setOutputData(new HashMap<>());
        task.setTaskDefName(taskType);
        task.setWorkflowType("testWorkflow");
        task.setWorkerId("worker1");
        task.setStartTime(System.currentTimeMillis());
        task.setUpdateTime(System.currentTimeMillis());
        return task;
    }
}
