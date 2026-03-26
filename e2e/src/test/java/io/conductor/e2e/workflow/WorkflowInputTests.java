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
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkflowInputTests {

    private static ObjectMapper objectMapper;

    @Test
    public void testWorkflowSearchPermissions() throws IOException {
        objectMapper = new ObjectMapper();
        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataAdminClient = ApiUtil.METADATA_CLIENT;
        String taskName1 = "task_input_test";
        String workflowName1 = "workflow_input_test";

        // Register workflow
        try {
            registerWorkflowDef(workflowName1, taskName1, metadataAdminClient);
        } catch (Exception e) {
        }

        // Trigger two workflows
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setInput(
                objectMapper.readValue(
                        "{\"a\":[{\"b\":[\"${workflow.functions.date_utc}\"]}], \"c\":\"${workflow.c}\", \"d\":\"${workflow.task2.output.a}\"}",
                        Map.class));

        String workflowId = workflowAdminClient.startWorkflow(startWorkflowRequest);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowAdminClient.getWorkflow(workflowId, false);
                            assertEquals(workflow.getInput().get("c"), "${workflow.c}");
                            assertEquals(
                                    workflow.getInput().get("d"), "${workflow.task2.output.a}");
                        });
    }

    private void registerWorkflowDef(
            String workflowName, String taskName, MetadataClient metadataClient1) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName(taskName);
        workflowTask.setName(taskName);
        workflowTask.setTaskDefinition(taskDef);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setInputParameters(Map.of("value", "${workflow.input.value}", "order", "123"));
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to monitor order state");
        workflowDef.setTasks(Arrays.asList(workflowTask));
        metadataClient1.updateWorkflowDefs(java.util.List.of(workflowDef));
        metadataClient1.registerTaskDefs(Arrays.asList(taskDef));
    }
}
