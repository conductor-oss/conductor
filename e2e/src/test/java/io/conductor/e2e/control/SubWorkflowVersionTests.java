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
package io.conductor.e2e.control;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.RegistrationUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubWorkflowVersionTests {

    @Test
    public void testSubWorkflowNullVersion() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String parentWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String subWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        RegistrationUtil.registerWorkflowWithSubWorkflowDef(
                parentWorkflowName, subWorkflowName, taskName, metadataClient);
        WorkflowDef workflowDef = metadataClient.getWorkflowDef(parentWorkflowName, 1);
        // Set sub workflow version to null
        workflowDef.getTasks().get(0).getSubWorkflowParam().setVersion(null);
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWorkflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // User1 should be able to complete task/workflow
        String subWorkflowId =
                workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getSubWorkflowId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subWorkflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId(
                workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId());
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        await().atMost(42, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                        });

        // Cleanup
        metadataClient.unregisterWorkflowDef(parentWorkflowName, 1);
        metadataClient.unregisterWorkflowDef(subWorkflowName, 1);
        metadataClient.unregisterTaskDef(taskName);
    }

    @Test
    public void testSubWorkflowEmptyVersion() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String parentWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String subWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        RegistrationUtil.registerWorkflowWithSubWorkflowDef(
                parentWorkflowName, subWorkflowName, taskName, metadataClient);
        WorkflowDef workflowDef = metadataClient.getWorkflowDef(parentWorkflowName, 1);
        WorkflowDef subWorkflowDef = metadataClient.getWorkflowDef(subWorkflowName, null);
        subWorkflowDef.setVersion(1);
        metadataClient.updateWorkflowDefs(java.util.List.of(subWorkflowDef));
        subWorkflowDef.setVersion(2);
        metadataClient.updateWorkflowDefs(java.util.List.of(subWorkflowDef));
        // Set sub workflow version to empty in parent workflow definition
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        workflowDef.getTasks().get(0).setSubWorkflowParam(subWorkflowParams);
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWorkflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // User1 should be able to complete task/workflow
        String subWorkflowId =
                workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getSubWorkflowId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subWorkflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId(
                workflowClient.getWorkflow(subWorkflowId, true).getTasks().get(0).getTaskId());
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        // Check sub-workflow is executed with the latest version.
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                            assertEquals(
                                    workflow1
                                            .getTasks()
                                            .get(0)
                                            .getWorkflowTask()
                                            .getSubWorkflowParam()
                                            .getVersion(),
                                    2);
                        });

        // Cleanup
        metadataClient.unregisterWorkflowDef(parentWorkflowName, 1);
        metadataClient.unregisterWorkflowDef(subWorkflowName, 1);
        metadataClient.unregisterTaskDef(taskName);
    }

    @Test
    public void testDynamicSubWorkflow() {
        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataAdminClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName1 = "DynamicFanInOutTest_Version";
        String subWorkflowName = "test_subworkflow";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataAdminClient);
        registerSubWorkflow(subWorkflowName, "test_task", metadataAdminClient);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowAdminClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowAdminClient.getWorkflow(workflowId, true);
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(0).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");
        workflowTask2.setType(TaskType.SUB_WORKFLOW.name());
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        workflowTask2.setSubWorkflowParam(subWorkflowParams);

        Map<String, Object> output = new HashMap<>();
        Map<String, Map<String, Object>> input = new HashMap<>();
        input.put("xdt1", Map.of("k1", "v1"));
        output.put("dynamicTasks", Arrays.asList(workflowTask2));
        output.put("dynamicTasksInput", input);
        taskResult.setOutputData(output);
        taskClient.updateTask(taskResult);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowAdminClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                            assertTrue(workflow1.getTasks().size() == 4);
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Workflow should be completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowAdminClient.getWorkflow(workflowId, true);
                            assertTrue(workflow1.getTasks().size() == 4);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1
                                            .getTasks()
                                            .get(2)
                                            .getInputData()
                                            .get("subWorkflowVersion"),
                                    1);
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                        });

        metadataAdminClient.unregisterWorkflowDef(workflowName1, 1);
    }

    private void registerWorkflowDef(String workflowName, MetadataClient metadataClient1) {
        TaskDef taskDef = new TaskDef("dt1");
        taskDef.setOwnerEmail("test@orkes.io");

        TaskDef taskDef4 = new TaskDef("integration_task_2");
        taskDef4.setOwnerEmail("test@orkes.io");

        TaskDef taskDef3 = new TaskDef("integration_task_3");
        taskDef3.setOwnerEmail("test@orkes.io");

        TaskDef taskDef2 = new TaskDef("dt2");
        taskDef2.setOwnerEmail("test@orkes.io");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("dt2");
        workflowTask.setName("dt2");
        workflowTask.setTaskDefinition(taskDef2);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName("dt1");
        inline.setName("dt1");
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask join = new WorkflowTask();
        join.setTaskReferenceName("join_dynamic");
        join.setName("join_dynamic");
        join.setWorkflowTaskType(TaskType.JOIN);

        WorkflowTask dynamicFork = new WorkflowTask();
        dynamicFork.setTaskReferenceName("dynamicFork");
        dynamicFork.setName("dynamicFork");
        dynamicFork.setTaskDefinition(taskDef);
        dynamicFork.setWorkflowTaskType(TaskType.FORK_JOIN_DYNAMIC);
        dynamicFork.setInputParameters(
                Map.of(
                        "dynamicTasks",
                        "${dt1.output.dynamicTasks}",
                        "dynamicTasksInput",
                        "${dt1.output.dynamicTasksInput}"));
        dynamicFork.setDynamicForkTasksParam("dynamicTasks");
        dynamicFork.setDynamicForkTasksInputParamName("dynamicTasksInput");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test retry");
        workflowDef.setTasks(Arrays.asList(inline, dynamicFork, join));
        try {
            metadataClient1.updateWorkflowDefs(java.util.List.of(workflowDef));
            metadataClient1.registerTaskDefs(Arrays.asList(taskDef, taskDef2, taskDef3, taskDef4));
        } catch (Exception e) {
        }
    }

    public static void registerSubWorkflow(
            String subWorkflowName, String taskName, MetadataClient metadataClient) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        taskDef.setRetryCount(0);

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName(taskName);
        inline.setName(taskName);
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.SIMPLE);
        inline.setInputParameters(Map.of("evaluatorType", "graaljs", "expression", "true;"));

        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName(subWorkflowName);
        subworkflowDef.setOwnerEmail("test@orkes.io");
        subworkflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        subworkflowDef.setDescription("Sub Workflow to test retry");
        subworkflowDef.setTasks(Arrays.asList(inline));
        subworkflowDef.setTimeoutSeconds(600);
        subworkflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);

        metadataClient.updateWorkflowDefs(java.util.List.of(subworkflowDef));
    }
}
