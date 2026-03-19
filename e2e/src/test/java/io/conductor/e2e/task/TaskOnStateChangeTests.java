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
package io.conductor.e2e.task;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StateChangeEvent;
import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class TaskOnStateChangeTests {

    @Test
    @DisplayName("Check task on state change event")
    public void testOnStateChangeNormalFlow() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName = "on-state-change-test";
        String taskName = "on-state-change-task";

        //clean up first
        SearchResult<WorkflowSummary> found = workflowClient.search("workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
        found.getResults().forEach(workflowSummary -> {
            try {
                workflowClient.terminateWorkflow(workflowSummary.getWorkflowId(), "terminate2");
                System.out.println("Going to terminate " + workflowSummary.getWorkflowId());
            } catch(Exception e){
                if (!(e instanceof ConductorClientException)) {
                    throw e;
                }
            }
        });

        // Register workflow
        registerWorkflowDef(workflowName, taskName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        //Start two workflows. Only first workflow task should be in_progress
        String workflowId1 = workflowClient.startWorkflow(startWorkflowRequest);

        Workflow workflow1 = workflowClient.getWorkflow(workflowId1, true);

        // Assertions
        Assertions.assertEquals(Workflow.WorkflowStatus.RUNNING, workflow1.getStatus());
        Assertions.assertEquals(1, workflow1.getTasks().size());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Task task1 = taskClient.pollTask("on_state_changed", "test", null);
            assertNotNull(task1);
        });

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            // Task2 should be available to poll
            Task task2 = taskClient.pollTask(taskName, "test", null);
            assertNotNull(task2);
            TaskResult taskResult = new TaskResult();
            taskResult.setTaskId(task2.getTaskId());
            taskResult.setStatus(TaskResult.Status.COMPLETED);
            taskResult.setWorkflowInstanceId(task2.getWorkflowInstanceId());
            taskClient.updateTask(taskResult);
        });

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            // Assert both workflows completed
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflowClient.getWorkflow(workflowId1, false).getStatus());
        });
    }

    private static void registerWorkflowDef(String workflowName, String taskName, MetadataClient metadataClient) {
        try {
            if (metadataClient.getWorkflowDef(workflowName, 1) != null && metadataClient.getTaskDef(taskName) != null) {
                return;
            }
        } catch (Exception e) {}
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        taskDef.setRetryCount(0);

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(taskName);
        simpleTask.setName(taskName);
        simpleTask.setTaskDefinition(taskDef);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
        StateChangeEvent stateChangeEvent = new StateChangeEvent();
        stateChangeEvent.setPayload(Map.of("emitBy", "e2e"));
        stateChangeEvent.setType("on_state_changed");
        simpleTask.setOnStateChange(Map.of("onScheduled, onStart, onFailed, onSuccess, onCancelled", List.of(stateChangeEvent)));
        simpleTask.setInputParameters(Map.of("value", "${workflow.input.value}", "order", "123"));


        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to monitor order state");
        workflowDef.setTasks(Arrays.asList(simpleTask));
        metadataClient.registerWorkflowDef(workflowDef);
        metadataClient.registerTaskDefs(Arrays.asList(taskDef));
    }
}
