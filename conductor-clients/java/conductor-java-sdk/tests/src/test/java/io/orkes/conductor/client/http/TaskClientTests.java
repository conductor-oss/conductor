/*
 * Copyright 2023 Conductor Authors.
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
package io.orkes.conductor.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.enums.Consistency;
import io.orkes.conductor.client.enums.ReturnStrategy;
import io.orkes.conductor.client.model.SignalResponse;
import io.orkes.conductor.client.util.ClientTestUtil;
import io.orkes.conductor.client.util.TestUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.util.concurrent.Uninterruptibles;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TaskClientTests {

    private static final String COMPLEX_WF_NAME = "complex_wf_signal_test";
    private static final String SUB_WF_1_NAME = "complex_wf_signal_test_subworkflow_1";
    private static final String SUB_WF_2_NAME = "complex_wf_signal_test_subworkflow_2";
    private static OrkesTaskClient taskClient;
    private static OrkesWorkflowClient workflowClient;
    private static OrkesMetadataClient metadataClient;
    private static WorkflowExecutor workflowExecutor;
    private static String workflowName = "";

    @BeforeAll
    public static void setup() throws IOException {
        taskClient = ClientTestUtil.getOrkesClients().getTaskClient();
        metadataClient = ClientTestUtil.getOrkesClients().getMetadataClient();
        workflowClient = ClientTestUtil.getOrkesClients().getWorkflowClient();
        InputStream is = TaskClientTests.class.getResourceAsStream("/sdk_test.json");
        ObjectMapper om = new ObjectMapperProvider().getObjectMapper();
        WorkflowDef workflowDef = om.readValue(new InputStreamReader(is), WorkflowDef.class);
        metadataClient.registerWorkflowDef(workflowDef, true);
        workflowName = workflowDef.getName();
        workflowExecutor = new WorkflowExecutor(ClientTestUtil.getClient(), 10);
        registerWorkflows();
    }

    @AfterAll
    public static void cleanUp() {
        // Clean up resources
        metadataClient.unregisterWorkflowDef(COMPLEX_WF_NAME, 1);
        metadataClient.unregisterWorkflowDef(SUB_WF_1_NAME, 1);
        metadataClient.unregisterWorkflowDef(SUB_WF_2_NAME, 1);
    }

    private static void registerWorkflows() {
        try {
            registerWorkflow(COMPLEX_WF_NAME);
            registerWorkflow(SUB_WF_1_NAME);
            registerWorkflow(SUB_WF_2_NAME);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register workflows", e);
        }
    }

    public static void registerWorkflow(String workflowName) throws Exception {
        WorkflowDef workflowDef = TestUtil.getWorkflowDef("/metadata/" + workflowName + ".json");
        metadataClient.registerWorkflowDef(workflowDef);
    }

    @Test
    public void testUpdateByRefName() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(workflowName);
        request.setVersion(1);
        request.setInput(new HashMap<>());
        String workflowId = workflowClient.startWorkflow(request);
        System.out.println(workflowId);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Assertions.assertNotNull(workflow);

        System.out.println("Running test for workflow: " + workflowId);

        int maxLoop = 10;
        int count = 0;
        while (!workflow.getStatus().isTerminal() && count < maxLoop) {
            workflow.getTasks().stream().filter(t -> !t.getStatus().isTerminal() && t.getWorkflowTask().getType().equals("SIMPLE")).forEach(running -> {
                String referenceName = running.getReferenceTaskName();
                System.out.println("Updating " + referenceName + ", and its status is " + running.getStatus());
                taskClient.updateTaskSync(workflowId, referenceName, TaskResult.Status.COMPLETED, Map.of("k", "value"));
            });
            count++;
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            workflow = workflowClient.getWorkflow(workflowId, true);
        }
        Assertions.assertTrue(count <= maxLoop, "count " + count + " is not less than maxLoop " + maxLoop);
        workflow = workflowClient.getWorkflow(workflowId, true);
        Assertions.assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testUpdateByRefNameSync() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(workflowName);
        request.setVersion(1);
        request.setInput(new HashMap<>());
        String workflowId = workflowClient.startWorkflow(request);
        System.out.println(workflowId);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Assertions.assertNotNull(workflow);

        int maxLoop = 10;
        int count = 0;
        while (!workflow.getStatus().isTerminal() && count < maxLoop) {
            workflow = workflowClient.getWorkflow(workflowId, true);
            List<String> runningTasks = workflow.getTasks().stream()
                    .filter(task -> !task.getStatus().isTerminal() && task.getTaskType().equals("there_is_no_worker"))
                    .map(Task::getReferenceTaskName)
                    .collect(Collectors.toList());
            System.out.println("Running tasks: " + runningTasks);
            if (runningTasks.isEmpty()) {
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                count++;
                continue;
            }
            // Converting TaskOutput class to Map to resolve Jackson's afterburner module and class loading issues
            Map<String, Object> output = new HashMap<>();
            output.put("name", "hello");
            output.put("value", BigDecimal.TEN);
            for (String referenceName : runningTasks) {
                System.out.println("Updating " + referenceName);
                try {
                    workflow = taskClient.updateTaskSync(workflowId, referenceName, TaskResult.Status.COMPLETED, output);
                    System.out.println("Workflow: " + workflow);
                } catch (ConductorClientException ConductorClientException) {
                    // 404 == task was updated already and there are no pending tasks
                    if (ConductorClientException.getStatus() != 404) {
                        Assertions.fail(ConductorClientException);
                    }
                }
            }
            count++;
        }
        Assertions.assertTrue(count < maxLoop);
        workflow = workflowClient.getWorkflow(workflowId, true);
        Assertions.assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testTaskLog() throws Exception {
        var workflowName = "random_workflow_name_1hqiuwhjasdsadqqwe";
        var taskName1 = "random_task_name_1najsbdha";
        var taskName2 = "random_task_name_1bhasvdgasvd12y378t";

        var taskDef1 = new TaskDef(taskName1);
        taskDef1.setRetryCount(0);
        taskDef1.setOwnerEmail("test@orkes.io");
        var taskDef2 = new TaskDef(taskName2);
        taskDef2.setRetryCount(0);
        taskDef2.setOwnerEmail("test@orkes.io");

        TestUtil.retryMethodCall(
                () -> metadataClient.registerTaskDefs(List.of(taskDef1, taskDef2)));

        var wf = new ConductorWorkflow<>(workflowExecutor);
        wf.setName(workflowName);
        wf.setVersion(1);
        wf.add(new SimpleTask(taskName1, taskName1));
        wf.add(new SimpleTask(taskName2, taskName2));
        TestUtil.retryMethodCall(
                () -> wf.registerWorkflow(true));

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setInput(new HashMap<>());
        var workflowId = (String) TestUtil.retryMethodCall(
                () -> workflowClient.startWorkflow(startWorkflowRequest));
        System.out.println("Started workflow with id: " + workflowId);

        var task = (Task) TestUtil.retryMethodCall(
                () -> taskClient.pollTask(taskName1, "random worker", null));
        Assertions.assertNotNull(task);
        var taskId = task.getTaskId();

        TestUtil.retryMethodCall(
                () -> taskClient.logMessageForTask(taskId, "random message"));
        var logs = (List<TaskExecLog>) TestUtil.retryMethodCall(
                () -> taskClient.getTaskLogs(taskId));
        Assertions.assertNotNull(logs);
        var details = (Task) TestUtil.retryMethodCall(
                () -> taskClient.getTaskDetails(taskId));
        Assertions.assertNotNull(details);
        TestUtil.retryMethodCall(
                () -> taskClient.requeuePendingTasksByTaskType(taskName2));
        TestUtil.retryMethodCall(
                () -> taskClient.getQueueSizeForTask(taskName1));
        TestUtil.retryMethodCall(
                () -> taskClient.getQueueSizeForTask(taskName1, null, null, null));
        TestUtil.retryMethodCall(
                () -> taskClient.batchPollTasksByTaskType(taskName2, "random worker id", 5, 3000));
    }

    @Test
    public void testUnsupportedMethods() {
        // Not supported by Orkes Conductor Server
        var ex = Assertions.assertThrows(ConductorClientException.class,
                () -> taskClient.searchV2(4, 20, "sort", "freeText", "query"));
        Assertions.assertEquals(404, ex.getStatus());
    }

    // Simple helper to start workflow and return workflowId
    private String startComplexWorkflow(Consistency consistency, ReturnStrategy returnStrategy) throws Exception {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(COMPLEX_WF_NAME);
        request.setVersion(1);

        var run = workflowClient.executeWorkflowWithReturnStrategy(request, null, 10, consistency, returnStrategy);
        var workflow = run.get(10, TimeUnit.SECONDS);

        assertNotNull(workflow);
        String workflowId = workflow.getTargetWorkflowId();

        // Wait for initial execution
        Thread.sleep(20);
        return workflowId;
    }

    // Simple helper to validate any signal response
    private void validateSignalResponse(SignalResponse response, ReturnStrategy expectedStrategy) {
        assertNotNull(response);
        assertEquals(expectedStrategy, response.getResponseType());
        assertNotNull(response.getWorkflowId());
        assertNotNull(response.getTargetWorkflowId());
    }

    // Helper method to test all helper methods for a given response
    private void validateHelperMethods(SignalResponse response, ReturnStrategy strategy) {
        switch (strategy) {
            case TARGET_WORKFLOW:
            case BLOCKING_WORKFLOW:
                // Should work for workflow responses
                assertDoesNotThrow(() -> response.getWorkflow(), "getWorkflow() should work for " + strategy);
                assertNotNull(response.getWorkflow(), "getWorkflow() should return non-null workflow");

                // Should throw for task methods
                assertThrows(IllegalStateException.class, () -> response.getBlockingTask(),
                        "getBlockingTask() should throw for " + strategy);
                assertThrows(IllegalStateException.class, () -> response.getTaskInput(),
                        "getTaskInput() should throw for " + strategy);
                break;

            case BLOCKING_TASK:
                // Should work for task responses
                assertDoesNotThrow(() -> response.getBlockingTask(), "getBlockingTask() should work for " + strategy);
                assertNotNull(response.getBlockingTask(), "getBlockingTask() should return non-null task");

                // Should throw for workflow and task input methods
                assertThrows(IllegalStateException.class, () -> response.getWorkflow(),
                        "getWorkflow() should throw for " + strategy);
                assertThrows(IllegalStateException.class, () -> response.getTaskInput(),
                        "getTaskInput() should throw for " + strategy);
                break;

            case BLOCKING_TASK_INPUT:
                // Should work for both task and task input methods
                assertDoesNotThrow(() -> response.getBlockingTask(), "getBlockingTask() should work for " + strategy);
                assertDoesNotThrow(() -> response.getTaskInput(), "getTaskInput() should work for " + strategy);
                assertNotNull(response.getBlockingTask(), "getBlockingTask() should return non-null task");
                assertNotNull(response.getTaskInput(), "getTaskInput() should return non-null input");

                // Should throw for workflow method
                assertThrows(IllegalStateException.class, () -> response.getWorkflow(),
                        "getWorkflow() should throw for " + strategy);
                break;
        }
    }

    // Simple helper to complete workflow
    private void completeWorkflow(String workflowId) throws Exception {
        // Signal twice to complete
        taskClient.signal(workflowId, Task.Status.COMPLETED, Map.of("result", "signal1"));
        taskClient.signal(workflowId, Task.Status.COMPLETED, Map.of("result", "signal2"));

        // Wait for completion
        var finalWorkflow = TestUtil.waitForWorkflowStatus(workflowClient, workflowId,
                Workflow.WorkflowStatus.COMPLETED, 5000, 100);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, finalWorkflow.getStatus());
    }

// Now all tests become very simple:

    @Test
    void testSyncTargetWorkflow() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.SYNCHRONOUS, ReturnStrategy.TARGET_WORKFLOW);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.TARGET_WORKFLOW);

        validateSignalResponse(response, ReturnStrategy.TARGET_WORKFLOW);
        assertTrue(response.isTargetWorkflow());
        validateHelperMethods(response, ReturnStrategy.TARGET_WORKFLOW);

        completeWorkflow(workflowId);
    }

    @Test
    void testSyncBlockingWorkflow() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.SYNCHRONOUS, ReturnStrategy.BLOCKING_WORKFLOW);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.BLOCKING_WORKFLOW);

        validateSignalResponse(response, ReturnStrategy.BLOCKING_WORKFLOW);
        assertTrue(response.isBlockingWorkflow());
        validateHelperMethods(response, ReturnStrategy.BLOCKING_WORKFLOW);

        completeWorkflow(workflowId);
    }

    @Test
    void testSyncBlockingTask() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.SYNCHRONOUS, ReturnStrategy.BLOCKING_TASK);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.BLOCKING_TASK);

        validateSignalResponse(response, ReturnStrategy.BLOCKING_TASK);
        assertTrue(response.isBlockingTask());
        validateHelperMethods(response, ReturnStrategy.BLOCKING_TASK);

        completeWorkflow(workflowId);
    }

    @Test
    void testSyncBlockingTaskInput() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.SYNCHRONOUS, ReturnStrategy.BLOCKING_TASK_INPUT);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.BLOCKING_TASK_INPUT);

        validateSignalResponse(response, ReturnStrategy.BLOCKING_TASK_INPUT);
        assertTrue(response.isBlockingTaskInput());
        validateHelperMethods(response, ReturnStrategy.BLOCKING_TASK_INPUT);

        completeWorkflow(workflowId);
    }

    // Durable tests - just copy the above and change Consistency
    @Test
    void testDurableTargetWorkflow() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.REGION_DURABLE, ReturnStrategy.TARGET_WORKFLOW);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.TARGET_WORKFLOW);

        validateSignalResponse(response, ReturnStrategy.TARGET_WORKFLOW);
        assertTrue(response.isTargetWorkflow());
        validateHelperMethods(response, ReturnStrategy.TARGET_WORKFLOW);

        completeWorkflow(workflowId);
    }

    @Test
    void testDurableBlockingWorkflow() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.REGION_DURABLE, ReturnStrategy.BLOCKING_WORKFLOW);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.BLOCKING_WORKFLOW);

        validateSignalResponse(response, ReturnStrategy.BLOCKING_WORKFLOW);
        assertTrue(response.isBlockingWorkflow());
        validateHelperMethods(response, ReturnStrategy.BLOCKING_WORKFLOW);

        completeWorkflow(workflowId);
    }

    @Test
    void testDurableBlockingTask() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.REGION_DURABLE, ReturnStrategy.BLOCKING_TASK);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.BLOCKING_TASK);

        validateSignalResponse(response, ReturnStrategy.BLOCKING_TASK);
        assertTrue(response.isBlockingTask());
        validateHelperMethods(response, ReturnStrategy.BLOCKING_TASK);

        completeWorkflow(workflowId);
    }

    @Test
    void testDurableBlockingTaskInput() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.REGION_DURABLE, ReturnStrategy.BLOCKING_TASK_INPUT);

        var response = taskClient.signal(workflowId, Task.Status.COMPLETED,
                Map.of("result", "test"), ReturnStrategy.BLOCKING_TASK_INPUT);

        validateSignalResponse(response, ReturnStrategy.BLOCKING_TASK_INPUT);
        assertTrue(response.isBlockingTaskInput());
        validateHelperMethods(response, ReturnStrategy.BLOCKING_TASK_INPUT);

        completeWorkflow(workflowId);
    }

    @Test
    void testDefaultReturnStrategy() throws Exception {
        String workflowId = startComplexWorkflow(Consistency.SYNCHRONOUS, ReturnStrategy.TARGET_WORKFLOW);

        // Don't specify return strategy - should default to TARGET_WORKFLOW
        var response = taskClient.signal(workflowId, Task.Status.COMPLETED, Map.of("result", "test"));

        validateSignalResponse(response, ReturnStrategy.TARGET_WORKFLOW);
        assertTrue(response.isTargetWorkflow());
        validateHelperMethods(response, ReturnStrategy.TARGET_WORKFLOW);

        completeWorkflow(workflowId);
    }
}
