/*
 * Copyright 2023 Orkes, Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.util.concurrent.Uninterruptibles;

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

import io.orkes.conductor.client.util.ClientTestUtil;
import io.orkes.conductor.client.util.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskClientTests {

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
            for (String referenceName : runningTasks) {
                System.out.println("Updating " + referenceName);
                try {
                    workflow = taskClient.updateTaskSync(workflowId, referenceName, TaskResult.Status.COMPLETED, new TaskOutput());
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

    private static class TaskOutput {
        private String name = "hello";

        private BigDecimal value = BigDecimal.TEN;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }
    }
}
