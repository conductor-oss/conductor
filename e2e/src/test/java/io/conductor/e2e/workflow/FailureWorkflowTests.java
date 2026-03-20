/*
 * Copyright 2022 Conductor Authors.
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
package io.conductor.e2e.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class FailureWorkflowTests {
    private static TypeReference<List<WorkflowDef>> WORKFLOW_DEF_LIST =
            new TypeReference<List<WorkflowDef>>() {};

    private static TypeReference<List<TaskDef>> TASK_DEF_LIST =
            new TypeReference<List<TaskDef>>() {};
    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;
    static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void init() throws IOException {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        // Load metadata
        InputStream resource =
                FailureWorkflowTests.class.getResourceAsStream("/metadata/workflow_data.json");
        if (resource != null) {
            List<WorkflowDef> workflowDefs =
                    objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF_LIST);
            metadataClient.updateWorkflowDefs(workflowDefs);
        }

        resource = FailureWorkflowTests.class.getResourceAsStream("/metadata/tasks_data.json");
        if (resource != null) {
            List<TaskDef> taskDefs =
                    objectMapper.readValue(new InputStreamReader(resource), TASK_DEF_LIST);
            metadataClient.registerTaskDefs(taskDefs);
        }
    }

    @Test
    @DisplayName("Check failed workflows do not loose tasks")
    // Bulkhead will hit the limit and fail the workflow so skipping this is api orchestration is
    // enabled
    public void testFailureTasksAreNotLost() {
        String workflowName = "this_will_fail";
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        List<String> workflowIds = new ArrayList<>();
        List<String> retried = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
            workflowIds.add(workflowId);
        }
        boolean allDone = workflowIds.isEmpty();
        int maxIterations = 1000;
        while (!allDone && maxIterations > 0) {
            maxIterations--;
            List<String> done = new ArrayList<>();
            for (int i = 0; i < workflowIds.size(); i++) {
                String workflowId = workflowIds.get(i);
                Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                if (workflow.getStatus().isTerminal()) {
                    assertTrue(!workflow.getTasks().isEmpty());
                    done.add(workflowId);
                    workflowClient.retryLastFailedTask(workflowId);
                    retried.add(workflowId);
                }
            }
            workflowIds.removeAll(done);
            allDone = workflowIds.isEmpty();
        }

        for (String workflowId : retried) {
            try {
                await().atMost(15, TimeUnit.SECONDS)
                        .pollInterval(10, TimeUnit.MILLISECONDS)
                        .untilAsserted(
                                () -> {
                                    Workflow workflow =
                                            workflowClient.getWorkflow(workflowId, true);
                                    assertEquals(
                                            Workflow.WorkflowStatus.FAILED, workflow.getStatus());
                                    assertTrue(!workflow.getTasks().isEmpty());
                                });
            } catch (Exception e) {
                if (!(e instanceof ConductorClientException)) {
                    throw e;
                }
            }
        }
    }

    @Test
    @DisplayName("Check failure workflow input as passed properly")
    public void testFailureWorkflowInputs() {
        String workflowName = "failure-workflow-test";
        String taskDefName = "simple-task1";
        String taskDefName2 = "simple-task2";

        // Register workflow
        registerWorkflowDefWithFailureWorkflow(
                workflowName, taskDefName, taskDefName2, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(33, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertTrue(
                                    !workflowClient
                                            .getWorkflow(workflowId, true)
                                            .getTasks()
                                            .isEmpty());
                        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);

        // Fail the simple task
        Map<String, Object> output = new HashMap<>();
        output.put("status", "completed");
        output.put("reason", "inserted");
        workflow =
                taskClient.updateTaskSync(
                        workflowId,
                        workflow.getTasks().get(0).getReferenceTaskName(),
                        TaskResult.Status.COMPLETED,
                        output);

        String reason = "Employee not found";
        String taskId = workflow.getTasks().get(1).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        taskResult.setReasonForIncompletion(reason);
        taskClient.updateTask(taskResult);

        // Wait for workflow to get failed
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    Workflow.WorkflowStatus.FAILED.name(),
                                    workflow1.getStatus().name());
                            assertNotNull(workflow1.getOutput().get("conductor.failure_workflow"));
                        });

        // Check failure workflow has complete parent workflow information
        workflow = workflowClient.getWorkflow(workflowId, false);
        String failureWorkflowId =
                workflow.getOutput().get("conductor.failure_workflow").toString();

        workflow = workflowClient.getWorkflow(failureWorkflowId, false);
        // Assert on input attributes
        assertNotNull(workflow.getInput().get("failedWorkflow"));
        assertNotNull(workflow.getInput().get("failureTaskId"));
        assertNotNull(workflow.getInput().get("workflowId"));
        assertEquals("FAILED", workflow.getInput().get("failureStatus").toString());
        assertTrue(
                workflow.getInput().get("reason").toString().contains(reason),
                "Reason should contain '" + reason + "'");
        Map<String, Object> input = (Map<String, Object>) workflow.getInput().get("failedWorkflow");

        assertNotNull(input.get("tasks"));
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) input.get("tasks");
        assertNotNull(tasks.get(0).get("outputData"));
        Map<String, String> task1Output = (Map<String, String>) tasks.get(0).get("outputData");
        assertEquals("inserted", task1Output.get("reason"));
        assertEquals("completed", task1Output.get("status"));
    }

    private void registerWorkflowDefWithFailureWorkflow(
            String workflowName,
            String taskName1,
            String taskName2,
            MetadataClient metadataClient) {

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName(taskName1);
        inline.setName(taskName1);
        inline.setWorkflowTaskType(TaskType.SIMPLE);
        inline.setInputParameters(Map.of("evaluatorType", "graaljs", "expression", "true;"));

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(taskName2);
        simpleTask.setName(taskName2);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask simpleTask2 = new WorkflowTask();
        simpleTask2.setTaskReferenceName(taskName2);
        simpleTask2.setName(taskName2);
        simpleTask2.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef failureWorkflow = new WorkflowDef();
        failureWorkflow.setName("failure_workflow");
        failureWorkflow.setOwnerEmail("test@orkes.io");
        failureWorkflow.setInputParameters(Arrays.asList("value", "inlineValue"));
        failureWorkflow.setDescription("Workflow to monitor order state");
        failureWorkflow.setTimeoutSeconds(600);
        failureWorkflow.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        failureWorkflow.setTasks(Arrays.asList(simpleTask2));
        metadataClient.updateWorkflowDefs(java.util.List.of(failureWorkflow));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to monitor order state");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setFailureWorkflow("failure_workflow");
        workflowDef.getOutputParameters().put("status", "${" + taskName1 + ".output.status}");
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(inline, simpleTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }
}
