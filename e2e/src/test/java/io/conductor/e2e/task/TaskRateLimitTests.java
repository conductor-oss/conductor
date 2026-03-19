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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
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

@Disabled
public class TaskRateLimitTests {

    @Test
    @DisplayName("Check workflow with simple rate limit by name")
    public void testRateLimitByPerFrequency() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName = "task-rate-limit-test";
        String taskName = "rate-limited-task";

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
        registerWorkflowDef(workflowName, taskName, metadataClient, false);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        //Start two workflows. Only first workflow task should be in_progress
        String workflowId1 = workflowClient.startWorkflow(startWorkflowRequest);
        String workflowId2 = workflowClient.startWorkflow(startWorkflowRequest);

        Workflow workflow1 = workflowClient.getWorkflow(workflowId1, true);
        Workflow workflow2 = workflowClient.getWorkflow(workflowId1, true);

        // Assertions
        Assertions.assertEquals(workflow1.getStatus(), Workflow.WorkflowStatus.RUNNING);
        Assertions.assertEquals(workflow1.getTasks().size(), 1);
        Assertions.assertEquals(workflow2.getStatus(), Workflow.WorkflowStatus.RUNNING);
        Assertions.assertEquals(workflow2.getTasks().size(), 1);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Task task1 = taskClient.pollTask(taskName, "test", null);
            Task task2 = taskClient.pollTask(taskName, "test", null);
            assertNotNull(task1);
            assertNull(task2);
            TaskResult taskResult = new TaskResult();
            taskResult.setTaskId(task1.getTaskId());
            taskResult.setStatus(TaskResult.Status.COMPLETED);
            taskResult.setWorkflowInstanceId(task1.getWorkflowInstanceId());
            taskClient.updateTask(taskResult);
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
            assertEquals(workflowClient.getWorkflow(workflowId1, false).getStatus(), Workflow.WorkflowStatus.COMPLETED);
            assertEquals(workflowClient.getWorkflow(workflowId2, false).getStatus(), Workflow.WorkflowStatus.COMPLETED);
        });
    }

    @Test
    @DisplayName("Check workflow with concurrent exec limit")
    public void testConcurrentExeclimit() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName = "concurrency-limit-test";
        String taskName = "task-concurrency-limit-test";

        //clean up first
        SearchResult<WorkflowSummary> found = workflowClient.search("workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
        found.getResults().forEach(workflowSummary -> {
            try {
                workflowClient.terminateWorkflow(workflowSummary.getWorkflowId(), "terminate");
                System.out.println("Going to terminate " + workflowSummary.getWorkflowId());
            } catch(Exception e){
                if (!(e instanceof ConductorClientException)) {
                    throw e;
                }
            }
        });
        // Register workflow
        registerWorkflowDef(workflowName, taskName, metadataClient, true);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        //Start two workflows. Only first workflow task should be in_progress
        String workflowId1 = workflowClient.startWorkflow(startWorkflowRequest);
        String workflowId2 = workflowClient.startWorkflow(startWorkflowRequest);

        Workflow workflow1 = workflowClient.getWorkflow(workflowId1, true);
        Workflow workflow2 = workflowClient.getWorkflow(workflowId2, true);

        // Assertions
        Assertions.assertEquals(workflow1.getStatus(), Workflow.WorkflowStatus.RUNNING);
        Assertions.assertEquals(workflow1.getTasks().size(), 1);
        Assertions.assertEquals(workflow2.getStatus(), Workflow.WorkflowStatus.RUNNING);
        Assertions.assertEquals(workflow2.getTasks().size(), 1);

        List<Task> tasks = taskClient.batchPollTasksByTaskType(taskName, "test", 2, 1000);
        assertEquals(1, tasks.size(), "found " + tasks.size());

        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(tasks.get(0).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setWorkflowInstanceId(tasks.get(0).getWorkflowInstanceId());
        taskClient.updateTask(taskResult);
        workflowClient.runDecider(tasks.get(0).getWorkflowInstanceId());

        try {
            Thread.sleep(13_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Task2 should not be pollable yet. It should be available only after 10 seconds.
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Task task3 = taskClient.pollTask(taskName, "test", null);
            assertNotNull(task3);
            TaskResult taskResult1 = new TaskResult();
            taskResult1.setTaskId(task3.getTaskId());
            taskResult1.setStatus(TaskResult.Status.COMPLETED);
            taskResult1.setWorkflowInstanceId(task3.getWorkflowInstanceId());
            taskClient.updateTask(taskResult1);
        });
        workflowClient.runDecider(workflowId2);

        assertEquals(workflowClient.getWorkflow(workflowId1, false).getStatus(), Workflow.WorkflowStatus.COMPLETED);
        assertEquals(workflowClient.getWorkflow(workflowId2, false).getStatus(), Workflow.WorkflowStatus.COMPLETED);
    }



    private static void registerWorkflowDef(String workflowName, String taskName, MetadataClient metadataClient, boolean isExecLimit) {
        if (metadataClient.getWorkflowDef(workflowName, 1) != null && metadataClient.getTaskDef(taskName) != null) {
            return;
        }
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        taskDef.setRetryCount(0);
        if (isExecLimit) {
            taskDef.setConcurrentExecLimit(1);
        } else {
            taskDef.setRateLimitPerFrequency(1);
            taskDef.setRateLimitFrequencyInSeconds(10);
        }

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(taskName);
        simpleTask.setName(taskName);
        simpleTask.setTaskDefinition(taskDef);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
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
