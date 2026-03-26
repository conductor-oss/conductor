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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.config.ObjectMapperProvider;
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
import io.conductor.e2e.util.TestUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class DynamicForkTests {

    @Test
    public void testTaskDynamicForkOptional() {
        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataAdminClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName1 = "DynamicFanInOutTest";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataAdminClient);

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

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("integration_task_3");
        workflowTask3.setTaskReferenceName("xdt2");
        workflowTask3.setOptional(true);

        Map<String, Object> output = new HashMap<>();
        Map<String, Map<String, Object>> input = new HashMap<>();
        input.put("xdt1", Map.of("k1", "v1"));
        input.put("xdt2", Map.of("k2", "v2"));
        output.put("dynamicTasks", Arrays.asList(workflowTask2, workflowTask3));
        output.put("dynamicTasksInput", input);
        taskResult.setOutputData(output);
        taskClient.updateTask(taskResult);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowAdminClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                            assertTrue(workflow1.getTasks().size() == 5);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(3).getTaskId());
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Since the tasks are marked as optional. The workflow should be in running state.
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            try {
                                Workflow workflow1 =
                                        workflowAdminClient.getWorkflow(workflowId, true);
                                assertTrue(workflow1.getTasks().size() == 6);
                                assertEquals(
                                        workflow1.getStatus().name(),
                                        Workflow.WorkflowStatus.RUNNING.name());
                                assertEquals(
                                        workflow1.getTasks().get(2).getStatus().name(),
                                        Task.Status.SCHEDULED.name());
                                assertEquals(
                                        workflow1.getTasks().get(3).getStatus().name(),
                                        Task.Status.FAILED.name());
                                assertEquals(
                                        workflow1.getTasks().get(4).getStatus().name(),
                                        Task.Status.IN_PROGRESS.name());
                                assertEquals(
                                        workflow1.getTasks().get(5).getStatus().name(),
                                        Task.Status.SCHEDULED.name());
                            } catch (Exception e) {

                            }
                        });

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(5).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Workflow should be completed
        await().atMost(100, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowAdminClient.getWorkflow(workflowId, true);
                            assertTrue(workflow1.getTasks().size() == 6);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                        });

        metadataAdminClient.unregisterWorkflowDef(workflowName1, 1);
    }

    @Test
    public void testTaskDynamicForkEmptyTaskName() {

        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataAdminClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName1 = "DynamicFanInOutTest";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataAdminClient);
        // Register a minimal sub-workflow to use by name (conductor-oss has no built-in "http"
        // workflow)
        String subWorkflowName = "http_sub_wf_test";
        SubWorkflowVersionTests.registerSubWorkflow(
                subWorkflowName, "http_sub_task", metadataAdminClient);

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
        workflowTask2.setTaskReferenceName("test_task");
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        workflowTask2.setType(TaskType.SUB_WORKFLOW.name());
        workflowTask2.setSubWorkflowParam(subWorkflowParams);

        Map<String, Object> output = new HashMap<>();
        output.put("dynamicTasks", Arrays.asList(workflowTask2));
        output.put("dynamicTasksInput", Map.of());
        taskResult.setOutputData(output);
        taskClient.updateTask(taskResult);

        await().atMost(30, TimeUnit.SECONDS)
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
                            assertNotNull(workflow1.getTasks().get(2).getSubWorkflowId());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        workflowAdminClient.terminateWorkflows(
                List.of(workflowId, workflow.getTasks().get(2).getSubWorkflowId()), "terminated");
    }

    @Test
    public void testTaskDynamicForkNullTaskReferenceName() {

        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataAdminClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName1 = "DynamicFanInOutTest";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataAdminClient);

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
        workflowTask2.setTaskReferenceName(null);
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("http");
        workflowTask2.setType(TaskType.SUB_WORKFLOW.name());
        workflowTask2.setSubWorkflowParam(subWorkflowParams);

        Map<String, Object> output = new HashMap<>();
        output.put("dynamicTasks", Arrays.asList(workflowTask2));
        output.put("dynamicTasksInput", Map.of());
        taskResult.setOutputData(output);
        taskClient.updateTask(taskResult);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowAdminClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.FAILED.name());
                            assertTrue(workflow1.getTasks().size() == 1);
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertNotNull(workflow1.getReasonForIncompletion());
                            assertTrue(
                                    workflow1
                                            .getReasonForIncompletion()
                                            .contains("Input 'dynamicTasks' is invalid"));
                        });
    }

    @Test
    public void testTaskDynamicForkRetryCount() {

        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataAdminClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName1 = "DynamicFanInOutTest1";

        // Register workflow
        registerWorkflowDef(workflowName1, metadataAdminClient);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowAdminClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowAdminClient.getWorkflow(workflowId, true);

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(0).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("integration_task_2");
        workflowTask2.setTaskReferenceName("xdt1");
        workflowTask2.setOptional(true);
        workflowTask2.setSink("kitchen_sink");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("integration_task_3");
        workflowTask3.setTaskReferenceName("xdt2");
        workflowTask3.setRetryCount(2);

        Map<String, Object> output = new HashMap<>();
        Map<String, Map<String, Object>> input = new HashMap<>();
        input.put("xdt1", Map.of("k1", "v1"));
        input.put("xdt2", Map.of("k2", "v2"));
        output.put("dynamicTasks", Arrays.asList(workflowTask2, workflowTask3));
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
                            assertTrue(workflow1.getTasks().size() == 5);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(2).getWorkflowTask().getSink(),
                                    "kitchen_sink");
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(3).getTaskId());
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Since the retry count is 2 task will be retried.
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowAdminClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                            assertTrue(workflow1.getTasks().size() == 6);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                            assertEquals(
                                    workflow1.getTasks().get(5).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                        });

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        workflow = workflowAdminClient.getWorkflow(workflowId, true);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(workflow.getTasks().get(5).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Workflow should be completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowAdminClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                            assertTrue(workflow1.getTasks().size() >= 6);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(3).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(4).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                            assertEquals(
                                    workflow1.getTasks().get(5).getStatus().name(),
                                    Task.Status.COMPLETED.name());
                        });

        metadataAdminClient.unregisterWorkflowDef(workflowName1, 1);
    }

    @Test
    public void testTaskDynamicForkDuplicateReferenceNameOfForkedTasksWhenMultipleFor() {

        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        String workflowName1 = "TwoIdenticalDynamicForks";
        TaskDef taskDef = new TaskDef("forked_task");

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName1);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setDescription(
                "Workflow to test two identical forks for duplicate forked task reference names");

        WorkflowTask dynamicFork1 = new WorkflowTask();
        dynamicFork1.setInputParameters(
                Map.of(
                        "forkTaskName",
                        taskDef.getName(),
                        "forkTaskInputs",
                        List.of(Map.of("a", 1), Map.of("a", 2))));
        dynamicFork1.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicFork1.setName("dynamicFork1");
        dynamicFork1.setTaskReferenceName("dynamicFork1");
        WorkflowTask join1 = new WorkflowTask();
        join1.setType(TaskType.JOIN.name());
        join1.setName("join1");
        join1.setTaskReferenceName("join1");

        WorkflowTask dynamicFork2 = new WorkflowTask();
        dynamicFork2.setInputParameters(
                Map.of(
                        "forkTaskName",
                        taskDef.getName(),
                        "forkTaskInputs",
                        List.of(Map.of("a", 1), Map.of("a", 2))));
        dynamicFork2.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicFork2.setName("dynamicFork2");
        dynamicFork2.setTaskReferenceName("dynamicFork2");
        WorkflowTask join2 = new WorkflowTask();
        join2.setType(TaskType.JOIN.name());
        join2.setName("join2");
        join2.setTaskReferenceName("join2");

        workflowDef.setTasks(Arrays.asList(dynamicFork1, join1, dynamicFork2, join2));
        startWorkflowRequest.setWorkflowDef(workflowDef);
        String workflowId = workflowAdminClient.startWorkflow(startWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowAdminClient.getWorkflow(workflowId, true);
                            var tasksToUpdate =
                                    workflow.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            t.getTaskType()
                                                                            .equals(
                                                                                    taskDef
                                                                                            .getName())
                                                                    && t.getStatus()
                                                                            .equals(
                                                                                    Task.Status
                                                                                            .SCHEDULED))
                                            .collect(Collectors.toList());
                            tasksToUpdate.forEach(
                                    t -> {
                                        TaskResult result = new TaskResult(t);
                                        result.setOutputData(Map.of("b", true));
                                        result.setStatus(TaskResult.Status.COMPLETED);
                                        taskClient.updateTask(result);
                                    });
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED.name(),
                                    workflow.getStatus().name());
                        });
    }

    @Test
    @SneakyThrows
    @DisplayName("When retrying a forked task ${CPEWF_TASK_ID} gives the correct task id")
    public void testCorrectTaskIdOnRetries() {
        startFailureWorkers();
        var mapper = new ObjectMapperProvider().getObjectMapper();
        var workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        var metadataAdminClient = ApiUtil.METADATA_CLIENT;

        var wfDef =
                mapper.readValue(
                        TestUtil.getResourceAsString("metadata/cpewf_task_id_dyn_fork_wf.json"),
                        WorkflowDef.class);
        metadataAdminClient.updateWorkflowDefs(java.util.List.of(wfDef));

        var taskDef =
                mapper.readValue(
                        TestUtil.getResourceAsString(
                                "metadata/cpewf_task_id_dyn_fork_task_def.json"),
                        TaskDef.class);
        metadataAdminClient.registerTaskDefs(List.of(taskDef));

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wfDef.getName());
        startWorkflowRequest.setVersion(wfDef.getVersion());

        var workflowId = workflowAdminClient.startWorkflow(startWorkflowRequest);

        await().atMost(1, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowAdminClient.getWorkflow(workflowId, false);
                            assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
                        });

        var workflow = workflowAdminClient.getWorkflow(workflowId, true);
        var tasks =
                workflow.getTasks().stream()
                        .filter(it -> "fail_on_purpose".equals(it.getTaskDefName()))
                        .toList();

        assertEquals(3, tasks.size());

        for (Task t : tasks) {
            assertEquals(t.getTaskId(), t.getInputData().get("task_id"));
        }
    }

    @Test
    @SneakyThrows
    @DisplayName("When using a join script by default it joins on all forked tasks")
    public void joinOnScript() {
        var mapper = new ObjectMapperProvider().getObjectMapper();

        var workflowClient = ApiUtil.WORKFLOW_CLIENT;
        var metadataClient = ApiUtil.METADATA_CLIENT;
        var orkesTaskClient = ApiUtil.TASK_CLIENT;

        var wfDef =
                mapper.readValue(
                        TestUtil.getResourceAsString("metadata/dyn_fork_test.json"),
                        WorkflowDef.class);
        metadataClient.updateWorkflowDefs(java.util.List.of(wfDef));

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wfDef.getName());
        startWorkflowRequest.setVersion(wfDef.getVersion());

        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
                            assertEquals(6, wf.getTasks().size());

                            var joinTask = wf.getTasks().get(5);
                            assertEquals(Task.Status.IN_PROGRESS, joinTask.getStatus());
                            assertEquals(TASK_TYPE_JOIN, joinTask.getTaskType());
                            assertEquals("join_1", joinTask.getReferenceTaskName());
                        });
        // complete first and second forked task
        {
            var wf = workflowClient.getWorkflow(workflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            // forked tasks
            var t0 = wf.getTasks().get(2);
            var t1 = wf.getTasks().get(3);
            // SCHEDULED because it was not polled
            assertEquals("simple_1", t0.getReferenceTaskName());
            assertEquals(Task.Status.SCHEDULED, t0.getStatus());

            assertEquals("simple_2", t1.getReferenceTaskName());
            assertEquals(Task.Status.SCHEDULED, t1.getStatus());

            var result0 = new TaskResult(t0);
            result0.setStatus(TaskResult.Status.COMPLETED);
            orkesTaskClient.updateTask(result0);

            var result1 = new TaskResult(t1);
            result1.setStatus(TaskResult.Status.COMPLETED);
            orkesTaskClient.updateTask(result1);
        }

        // join task should remain in progress
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            var wf = workflowClient.getWorkflow(workflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());

            var joinTask = wf.getTasks().get(5);
            assertEquals(Task.Status.IN_PROGRESS, joinTask.getStatus());
            assertEquals(TASK_TYPE_JOIN, joinTask.getTaskType());
            assertEquals("join_1", joinTask.getReferenceTaskName());
        }

        // complete third forked task
        {
            var wf = workflowClient.getWorkflow(workflowId, true);
            var t2 = wf.getTasks().get(4);

            assertEquals("simple_3", t2.getReferenceTaskName());
            assertEquals(Task.Status.SCHEDULED, t2.getStatus());

            var result2 = new TaskResult(t2);
            result2.setStatus(TaskResult.Status.COMPLETED);
            orkesTaskClient.updateTask(result2);
        }

        // join task and workflow should be completed
        await().await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());

                            var joinTask = wf.getTasks().get(5);
                            assertEquals(Task.Status.COMPLETED, joinTask.getStatus());
                            assertEquals(TASK_TYPE_JOIN, joinTask.getTaskType());
                            assertEquals("join_1", joinTask.getReferenceTaskName());
                        });
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
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test retry");
        workflowDef.setTasks(Arrays.asList(inline, dynamicFork, join));
        try {
            metadataClient1.updateWorkflowDefs(java.util.List.of(workflowDef));
            metadataClient1.registerTaskDefs(Arrays.asList(taskDef, taskDef2, taskDef3, taskDef4));
        } catch (Exception e) {
        }
    }

    private static void startFailureWorkers() {
        var taskClient = ApiUtil.TASK_CLIENT;
        var configurer =
                new TaskRunnerConfigurer.Builder(taskClient, List.of(new FailOnPurposeWorker()))
                        .withThreadCount(1)
                        .withTaskPollTimeout(500)
                        .build();
        configurer.init();
    }

    private static class FailOnPurposeWorker implements Worker {
        @Override
        public String getTaskDefName() {
            return "fail_on_purpose";
        }

        @Override
        public TaskResult execute(Task task) {
            var result = new TaskResult(task);
            result.setStatus(TaskResult.Status.FAILED);
            result.getOutputData().put("message", "Failing task on purpose");
            return result;
        }
    }
}
