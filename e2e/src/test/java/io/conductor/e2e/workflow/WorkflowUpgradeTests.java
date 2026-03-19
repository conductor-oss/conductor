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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.*;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.conductor.e2e.util.TestUtil.getResourceAsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Slf4j
@Disabled
public class WorkflowUpgradeTests {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;

    static ObjectMapper objectMapper;

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    @Test
    @DisplayName("Check workflow upgrade works when workflow contains only simple tasks.")
    public void testUpgradeWorkflow1() {
        String workflowName = "upgrade_workflow_simple";

        terminateExistingRunningWorkflows(workflowName);

        // Register workflow
        registerSimpleWorkflowDefs(metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        taskClient.updateTaskSync(workflowId, "simple_task2", TaskResult.Status.FAILED, Map.of("test", "test"));
        taskClient.updateTaskSync(workflowId, "simple_task2", TaskResult.Status.FAILED, Map.of("test", "test"));
        taskClient.updateTaskSync(workflowId, "simple_task2", TaskResult.Status.COMPLETED, Map.of("test", "test"));
        taskClient.updateTaskSync(workflowId, "simple_task4", TaskResult.Status.FAILED, Map.of("test", "test"));

        UpgradeWorkflowRequest upgradeWorkflowRequest = new UpgradeWorkflowRequest();
        Map<String, Object> output = Map.of("updatedBy" , "upgrade");
        upgradeWorkflowRequest.setTaskOutput(Map.of("simple_task3", output,"simple_task1",output));
        upgradeWorkflowRequest.setWorkflowInput(Map.of("name", "orkes"));

        //Upgrade workflow to version 2
        upgradeWorkflowRequest.setVersion(2);
        upgradeWorkflowRequest.setName(workflowName);
        workflowClient.upgradeRunningWorkflow(workflowId, upgradeWorkflowRequest);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertEquals(7, workflow.getTasks().size());
                    assertEquals(2, workflow.getWorkflowDefinition().getVersion());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(0).getStatus());
                    assertEquals(output, workflow.getTasks().get(0).getOutputData());
                    assertEquals(Task.Status.FAILED, workflow.getTasks().get(1).getStatus());
                    assertTrue(workflow.getTasks().get(1).isRetried());
                    assertEquals(Task.Status.FAILED, workflow.getTasks().get(2).getStatus());
                    assertTrue(workflow.getTasks().get(2).isRetried());
                    assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
                    // Last attempt is successful
                    assertFalse(workflow.getTasks().get(3).isRetried());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(4).getStatus());
                    assertEquals(output, workflow.getTasks().get(4).getOutputData());
                    assertEquals(Task.Status.FAILED, workflow.getTasks().get(5).getStatus());
                    assertTrue(workflow.getTasks().get(5).isRetried());
                    assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(6).getStatus());
        });

    }

    @Test
    @DisplayName("Check workflow upgrade works when workflow currently running branch under fork_join and new tasks are added in between and above fork.")
    public void testUpgradeWorkflow2() {

        try {
            terminateExistingRunningWorkflows("upgrade_fork");
            WorkflowDef workflowDef = objectMapper.readValue(getResourceAsString("upgrade_fork.json"), WorkflowDef.class);
            metadataClient.updateWorkflowDefs(List.of(workflowDef));
            workflowDef.getTasks().remove(0);
            workflowDef.getTasks().get(0).getForkTasks().get(0).remove(0);
            workflowDef.getTasks().get(0).getForkTasks().get(0).remove(1);
            workflowDef.getTasks().get(0).getForkTasks().get(1).remove(0);
            workflowDef.getTasks().get(0).getForkTasks().get(1).remove(1);
            workflowDef.getTasks().get(1).setJoinOn(List.of("simple_3", "simple_4"));
            workflowDef.setVersion(1);
            metadataClient.updateWorkflowDefs(List.of(workflowDef));
        } catch (IOException e) {
            // Do nothing
        }

        // Start workflow and call upgrade.
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("upgrade_fork");
        startWorkflowRequest.setVersion(1);
        String workflowId =  workflowClient.startWorkflow(startWorkflowRequest);
        UpgradeWorkflowRequest upgradeWorkflowRequest = new UpgradeWorkflowRequest();
        Map<String, Object> output = Map.of("updatedBy" , "upgrade");
        upgradeWorkflowRequest.setTaskOutput(Map.of("simple_2", output,"simple_1",output, "simple_0", output));
        upgradeWorkflowRequest.setWorkflowInput(Map.of("name", "orkes"));
        upgradeWorkflowRequest.setVersion(2);
        upgradeWorkflowRequest.setName("upgrade_fork");
        workflowClient.upgradeRunningWorkflow(workflowId, upgradeWorkflowRequest);
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertEquals(2, workflow.getWorkflowDefinition().getVersion());
                    assertEquals(7, workflow.getTasks().size());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(0).getStatus());
                    assertEquals(output, workflow.getTasks().get(0).getOutputData());
                    assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(2).getStatus());
                    assertEquals(output, workflow.getTasks().get(2).getOutputData());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(3).getStatus());
                    assertEquals(output, workflow.getTasks().get(3).getOutputData());
                    assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
                    assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
                    assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(6).getStatus());
                });
    }

    @Test
    @DisplayName("Check workflow upgrade works when workflow currently running branch under switch and new tasks are added in between and above switch.")
    public void testUpgradeWorkflow3() {

        try {
            terminateExistingRunningWorkflows("upgrade_switch");
            WorkflowDef workflowDef = objectMapper.readValue(getResourceAsString("upgrade_switch.json"), WorkflowDef.class);
            metadataClient.updateWorkflowDefs(List.of(workflowDef));
            workflowDef.getTasks().get(0).getDecisionCases().get("LONG").remove(0);
            workflowDef.getTasks().get(0).getDecisionCases().get("SHORT").remove(0);
            workflowDef.setVersion(1);
            metadataClient.updateWorkflowDefs(List.of(workflowDef));
        } catch (IOException e) {
            // Do nothing
        }

        // Start workflow and call upgrade.
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("upgrade_switch");
        startWorkflowRequest.setVersion(1);
        String workflowId =  workflowClient.startWorkflow(startWorkflowRequest);
        UpgradeWorkflowRequest upgradeWorkflowRequest = new UpgradeWorkflowRequest();
        Map<String, Object> output = Map.of("updatedBy" , "upgrade");
        upgradeWorkflowRequest.setTaskOutput(Map.of("simple_0", output,"simple_1",output));
        upgradeWorkflowRequest.setWorkflowInput(Map.of("name", "orkes"));
        upgradeWorkflowRequest.setVersion(2);
        upgradeWorkflowRequest.setName("upgrade_switch");
        workflowClient.upgradeRunningWorkflow(workflowId, upgradeWorkflowRequest);
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertEquals(2, workflow.getWorkflowDefinition().getVersion());
                    assertEquals(Map.of("name", "orkes"), workflow.getInput());
                    assertEquals(3, workflow.getTasks().size());
                    assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(1).getStatus());
                    assertEquals(output, workflow.getTasks().get(1).getOutputData());
                    assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
                });
    }

    @Test
    @DisplayName("Check workflow upgrade works when workflow currently running branch under do_while and new tasks are added in between and above do_while.")
    public void testUpgradeWorkflow4() {
        try {
            terminateExistingRunningWorkflows("upgrade_loop");
            WorkflowDef workflowDef = objectMapper.readValue(getResourceAsString("upgrade_loop.json"), WorkflowDef.class);
            metadataClient.updateWorkflowDefs(List.of(workflowDef));
            workflowDef.getTasks().get(0).getLoopOver().remove(0);
            workflowDef.setVersion(1);
            metadataClient.updateWorkflowDefs(List.of(workflowDef));
        } catch (IOException e) {
            // Do nothing
        }

        // Start workflow and call upgrade.
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("upgrade_loop");
        startWorkflowRequest.setVersion(1);
        String workflowId =  workflowClient.startWorkflow(startWorkflowRequest);
        // Complete the first loopver task.
        taskClient.updateTaskSync(workflowId, "simple_1__1", TaskResult.Status.COMPLETED, Map.of("completedBy", "conductor"));
        UpgradeWorkflowRequest upgradeWorkflowRequest = new UpgradeWorkflowRequest();
        Map<String, Object> output = Map.of("updatedBy" , "upgrade");
        upgradeWorkflowRequest.setTaskOutput(Map.of("simple_0__1", output,"simple_0__2", output));
        upgradeWorkflowRequest.setWorkflowInput(Map.of("name", "orkes"));
        upgradeWorkflowRequest.setVersion(2);
        upgradeWorkflowRequest.setName("upgrade_loop");
        workflowClient.upgradeRunningWorkflow(workflowId, upgradeWorkflowRequest);
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertEquals(2, workflow.getWorkflowDefinition().getVersion());
                    assertEquals(Map.of("name", "orkes"), workflow.getInput());
                    assertEquals(5, workflow.getTasks().size());
                    assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(1).getStatus());
                    assertEquals(output, workflow.getTasks().get(1).getOutputData());
                    assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
                    assertEquals(Task.Status.SKIPPED, workflow.getTasks().get(3).getStatus());
                    assertEquals(output, workflow.getTasks().get(3).getOutputData());
                    assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(4).getStatus());
                });

        // When the simple_1 is completed newly added task gets scheduled.
        taskClient.updateTaskSync(workflowId, "simple_1__2", TaskResult.Status.COMPLETED, Map.of("completedBy", "conductor"));
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                    assertEquals(2, workflow.getWorkflowDefinition().getVersion());
                    assertEquals(Map.of("name", "orkes"), workflow.getInput());
                    assertEquals(6, workflow.getTasks().size());
                    assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
                    assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());
                });
    }

    private void terminateExistingRunningWorkflows(String workflowName) {
        //clean up first
        SearchResult<WorkflowSummary> found = workflowClient.search("workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
        System.out.println("Found " + found.getResults().size() + " running workflows to be cleaned up");
        found.getResults().forEach(workflowSummary -> {
            try {
                if(!workflowSummary.getStatus().isTerminal()) {
                    System.out.println("Going to terminate " + workflowSummary.getWorkflowId() + " with status " + workflowSummary.getStatus());
                    workflowClient.terminateWorkflow(workflowSummary.getWorkflowId(), "terminate - upgrade test");
                }
            } catch(Exception e){
                if (!(e instanceof ConductorClientException)) {
                    throw e;
                }
            }
        });
    }

    public static void registerSimpleWorkflowDefs(MetadataClient metadataClient) {
        String taskName1 = "simple_task1";
        String taskName2 = "simple_task2";
        String taskName3 = "simple_task3";
        String taskName4 = "simple_task4";

        TaskDef taskDef = new TaskDef(taskName1);
        taskDef.setRetryCount(0);
        taskDef.setOwnerEmail("test@orkes.io");
        TaskDef taskDef2 = new TaskDef(taskName2);
        taskDef2.setRetryCount(3);
        taskDef2.setOwnerEmail("test@orkes.io");
        TaskDef taskDef3 = new TaskDef(taskName3);
        taskDef3.setRetryCount(0);
        taskDef3.setOwnerEmail("test@orkes.io");
        TaskDef taskDef4 = new TaskDef(taskName4);
        taskDef4.setRetryCount(1);
        taskDef4.setOwnerEmail("test@orkes.io");

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(taskName1);
        simpleTask.setName(taskName1);
        simpleTask.setTaskDefinition(taskDef);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask simpleTask2 = new WorkflowTask();
        simpleTask2.setTaskReferenceName(taskName2);
        simpleTask2.setName(taskName2);
        simpleTask2.setTaskDefinition(taskDef2);
        simpleTask2.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask simpleTask3 = new WorkflowTask();
        simpleTask3.setTaskReferenceName(taskName3);
        simpleTask3.setName(taskName3);
        simpleTask3.setTaskDefinition(taskDef3);
        simpleTask3.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask simpleTask4 = new WorkflowTask();
        simpleTask4.setTaskReferenceName(taskName4);
        simpleTask4.setName(taskName4);
        simpleTask4.setTaskDefinition(taskDef4);
        simpleTask4.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("upgrade_workflow_simple");
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test upgrade api");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(simpleTask2,simpleTask4));
        metadataClient.updateWorkflowDefs(Arrays.asList(workflowDef));
        workflowDef.setTasks(Arrays.asList(simpleTask, simpleTask2, simpleTask3, simpleTask4));
        workflowDef.setVersion(2);
        metadataClient.updateWorkflowDefs(Arrays.asList(workflowDef));
        metadataClient.registerTaskDefs(Arrays.asList(taskDef, taskDef2));
    }

}
