/*
 * Copyright 2024 Orkes, Inc.
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

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.WorkflowSummary;
import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;

public class WorkflowPriorityTests {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
    }

    @Test
    @DisplayName("Check workflow with priority")
    public void testWorkflowPriority() {
        String workflowName = "workflow-priority-test";
        String taskName = "priority-task";
        // Register workflow
        registerWorkflowDef(workflowName, taskName, metadataClient);
        terminateExistingRunningWorkflows(workflowName);

        List<String> noPriorityWorkflows = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
            startWorkflowRequest.setName(workflowName);
            startWorkflowRequest.setCorrelationId("0");
            String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
            noPriorityWorkflows.add(workflowId);
        }

        try {
            Thread.sleep(Duration.ofSeconds(2).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<String> prioritizedWorkflows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
            startWorkflowRequest.setName(workflowName);
            startWorkflowRequest.setCorrelationId("" + (2+ i));
            //higher priority workflows
            startWorkflowRequest.setPriority(2 + i);
            String higherPriorityWorkflowId = workflowClient.startWorkflow(startWorkflowRequest);
            prioritizedWorkflows.add(higherPriorityWorkflowId);
        }

        AtomicInteger completed = new AtomicInteger(0);
        Map<String, Integer> polledByPriority = new HashMap<>();
        List<Integer> polledPriorities = new ArrayList<>();
        while (completed.get() < 100) {

            //The fetch count should be exactly 10 --> same as the list of prioritized workflows so a single poll does not contain non-prioritized workflows.
            List<Task> polled = taskClient.batchPollTasksByTaskType(taskName, "e2e", 10, 1000);
            assertNotNull(polled);
            polled.forEach(task -> {
                completed.incrementAndGet();
                polledByPriority.put(task.getWorkflowInstanceId(), task.getWorkflowPriority());
                polledPriorities.add(task.getWorkflowPriority());
                TaskResult taskResult = new TaskResult(task);
                taskResult.setStatus(TaskResult.Status.COMPLETED);
                taskClient.updateTask(taskResult);
            });
        }
        for (int i = 0; i < 10; i++) {
            int priority = polledPriorities.get(i);
            assertTrue(priority > 0);
        }


        completed.set(0);
        polledByPriority.clear();
        polledPriorities.clear();
        //Now, let's poll for the second task
        taskName = taskName + "_2";

        while (completed.get() < 100) {

            //The fetch count should be exactly 10 --> same as the list of prioritized workflows so a single poll does not contain non-prioritized workflows.
            List<Task> polled = taskClient.batchPollTasksByTaskType(taskName, "e2e", 10, 1000);
            assertNotNull(polled);
            polled.stream().parallel().forEach(task -> {
                completed.incrementAndGet();
                polledByPriority.put(task.getWorkflowInstanceId(), task.getWorkflowPriority());
                polledPriorities.add(task.getWorkflowPriority());
                TaskResult taskResult = new TaskResult(task);
                taskResult.setStatus(TaskResult.Status.COMPLETED);
                taskClient.updateTask(taskResult);
            });
        }
        for (int i = 0; i < 10; i++) {
            int priority = polledPriorities.get(i);
            assertTrue(priority > 0, "Priority is expected to be > , but found " + priority);
        }

    }

    @Test
    @DisplayName("Check workflow with priority when workflows are terminated")
    public void testWorkflowPriorityWorkflowTerminated() {
        String workflowName = "workflow-priority-test";
        String taskName = "priority-task";
        // Register workflow
        registerWorkflowDef(workflowName, taskName, metadataClient);
        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setPriority(2);
        String lowerPriorityWorkflowId = workflowClient.startWorkflow(startWorkflowRequest);

        startWorkflowRequest.setPriority(1);
        String higherPriorityWorkflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Terminate higher priority workflow
        workflowClient.terminateWorkflows(List.of(higherPriorityWorkflowId), "Terminated by e2e");

        // When task is polled. Task from lower priority workflow comes.
        await().atMost(5, TimeUnit.SECONDS).pollInterval(1,TimeUnit.SECONDS).untilAsserted(() -> {
            Task task = taskClient.pollTask(taskName, "e2e", null);
            assertNotNull(task);
            assertEquals(task.getWorkflowInstanceId(), lowerPriorityWorkflowId);
        });
        terminateExistingRunningWorkflows(workflowName);
    }

    private static void registerWorkflowDef(String workflowName, String taskName, MetadataClient metadataClient) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        taskDef.setRetryCount(0);

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(taskName);
        simpleTask.setName(taskName);
        simpleTask.setTaskDefinition(taskDef);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
        simpleTask.setInputParameters(Map.of("value", "${workflow.input.value}", "order", "123"));

        taskName = taskName + "_2";
        WorkflowTask simpleTask2 = new WorkflowTask();
        simpleTask2.setTaskReferenceName(taskName);
        simpleTask2.setName(taskName);
        simpleTask2.setTaskDefinition(taskDef);
        simpleTask2.setWorkflowTaskType(TaskType.SIMPLE);
        simpleTask2.setInputParameters(Map.of("value", "${workflow.input.value}", "order", "123"));


        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to monitor order state");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(simpleTask, simpleTask2));
        metadataClient.registerWorkflowDef(workflowDef);
        metadataClient.registerTaskDefs(Arrays.asList(taskDef));
    }

    private void terminateExistingRunningWorkflows(String workflowName) {
        //clean up first
        SearchResult<WorkflowSummary> found = workflowClient.search(0,10000, "", "*", "workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
        found.getResults().forEach(workflowSummary -> {
            try {
                workflowClient.terminateWorkflow(workflowSummary.getWorkflowId(), "terminate - priority limiter test - " + workflowName);
                System.out.println("Going to terminate " + workflowSummary.getWorkflowId());
            }catch (Exception ignored) {
            }
        });
    }
}
