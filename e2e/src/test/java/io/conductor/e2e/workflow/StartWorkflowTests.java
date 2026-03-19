/*
 * Copyright 2022 Orkes, Inc.
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

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import io.conductor.e2e.util.ApiUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class StartWorkflowTests {

    private final WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
    private final MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;

    @Test
    public void testStartWorkflowWithNameAndVersion() {
        Assumptions.assumeFalse("true".equalsIgnoreCase(System.getenv("SECURITY_DISABLED")),
                "Skipping test: conductor.security.enabled is false");
        String starterWorkflowName = "START_WF_" + RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String targetWorkflowName = "TARGET_WF_" + RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String taskName = "TASK_" + RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        try {
            // Register target workflow and starter workflow
            registerTargetWorkflow(targetWorkflowName, taskName);
            registerStarterWorkflow(starterWorkflowName, targetWorkflowName, taskName);

            // Start the starter workflow
            StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
            startWorkflowRequest.setName(starterWorkflowName);
            startWorkflowRequest.setVersion(1);
            startWorkflowRequest.setInput(Map.of("targetInput", "test_value"));

            String starterWorkflowId = workflowClient.startWorkflow(startWorkflowRequest);

            // Wait for starter workflow to complete
            await().atMost(5, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow starterWorkflow = workflowClient.getWorkflow(starterWorkflowId, true);
                assertEquals(Workflow.WorkflowStatus.COMPLETED.name(), starterWorkflow.getStatus().name());
                assertEquals(1, starterWorkflow.getTasks().size());
                assertEquals("START_WORKFLOW", starterWorkflow.getTasks().get(0).getTaskType());
                assertEquals(TaskResult.Status.COMPLETED.name(), starterWorkflow.getTasks().get(0).getStatus().name());
            });

            // Get the started workflow ID
            Workflow starterWorkflow = workflowClient.getWorkflow(starterWorkflowId, true);
            String startedWorkflowId = (String) starterWorkflow.getTasks().get(0).getOutputData().get("workflowId");
            assertNotNull(startedWorkflowId);

            // Verify the started workflow is running
            Workflow startedWorkflow = workflowClient.getWorkflow(startedWorkflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING.name(), startedWorkflow.getStatus().name());
            assertEquals(targetWorkflowName, startedWorkflow.getWorkflowName());
            assertNotNull(startedWorkflow.getCreatedBy());

            workflowClient.terminateWorkflows(List.of(starterWorkflow.getWorkflowId()), "e2e");

        } finally {
            cleanup(starterWorkflowName, targetWorkflowName, taskName);
        }
    }

    private void registerTargetWorkflow(String workflowName, String taskName) {
        // Register task definition
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setOwnerEmail("test@orkes.io");
        metadataClient.registerTaskDefs(Arrays.asList(taskDef));

        // Create workflow task
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName(taskName);
        workflowTask.setName(taskName);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setInputParameters(Map.of("input", "${workflow.input.targetInput}"));

        // Create workflow definition
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("targetInput"));
        workflowDef.setDescription("Target workflow for START_WORKFLOW tests");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(workflowTask));

        metadataClient.registerWorkflowDef(workflowDef);
    }

    private void registerStarterWorkflow(String starterWorkflowName, String targetWorkflowName, String taskName) {
        // Create START_WORKFLOW task
        WorkflowTask startWorkflowTask = new WorkflowTask();
        startWorkflowTask.setTaskReferenceName("start_workflow_task");
        startWorkflowTask.setName("START_WORKFLOW");
        startWorkflowTask.setWorkflowTaskType(TaskType.START_WORKFLOW);

        Map<String, Object> inputParams = new HashMap<>();
        Map<String, Object> startWorkflowParam = new HashMap<>();
        startWorkflowParam.put("name", targetWorkflowName);
        startWorkflowParam.put("version", 1);
        startWorkflowParam.put("input", Map.of("targetInput", "${workflow.input.targetInput}"));
        inputParams.put("startWorkflow", startWorkflowParam);

        startWorkflowTask.setInputParameters(inputParams);

        // Create starter workflow definition
        WorkflowDef starterWorkflowDef = new WorkflowDef();
        starterWorkflowDef.setName(starterWorkflowName);
        starterWorkflowDef.setVersion(1);
        starterWorkflowDef.setOwnerEmail("test@orkes.io");
        starterWorkflowDef.setInputParameters(Arrays.asList("targetInput"));
        starterWorkflowDef.setDescription("Starter workflow for START_WORKFLOW tests");
        starterWorkflowDef.setTimeoutSeconds(600);
        starterWorkflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        starterWorkflowDef.setTasks(Arrays.asList(startWorkflowTask));

        metadataClient.registerWorkflowDef(starterWorkflowDef);
    }

    private void cleanup(String starterWorkflowName, String targetWorkflowName, String taskName) {
        try {
            metadataClient.unregisterWorkflowDef(starterWorkflowName, 1);
            metadataClient.unregisterWorkflowDef(targetWorkflowName, 1);
            metadataClient.unregisterTaskDef(taskName);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
