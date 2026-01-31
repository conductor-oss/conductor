/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.e2e;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.e2e.util.ConductorClientUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class DoWhileTests {

    private static final String[] WORKFLOW_FILES = {
        "/metadata/do_while_auto_terminate.json",
        "/metadata/do_while_early_eval_script.json",
        "/metadata/do_while_keep_last_n.json",
        "/metadata/do_while_keep_last_n_2.json",
        "/metadata/do_while_keep_last_n_3.json",
        "/metadata/do_while_set_variable_fix.json",
        "/metadata/do_while_stackoverflower.json",
        "/metadata/do_while_wait_subworkflow.json",
        "/metadata/do_while_wait_switch.json"
    };
    private static final List<String> workflowIdsToTerminate = new ArrayList<>();
    private static WorkflowClient workflowClient;
    private static TaskClient taskClient;
    private static MetadataClient metadataClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<WorkflowDef> WORKFLOW_DEF = new TypeReference<>() {};

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        workflowClient = ConductorClientUtil.getWorkflowClient();
        taskClient = ConductorClientUtil.getTaskClient();
        metadataClient = ConductorClientUtil.getMetadataClient();

        for (String file : WORKFLOW_FILES) {
            InputStream resource = DoWhileTests.class.getResourceAsStream(file);
            assert resource != null;
            WorkflowDef workflowDef =
                    objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF);
            try {
                metadataClient.registerWorkflowDef(workflowDef);
                log.info("Registered workflow definition: {}", workflowDef.getName());
            } catch (Exception e) {
                log.warn(
                        "Could not register workflow {}: {}",
                        workflowDef.getName(),
                        e.getMessage());
            }
        }
    }

    @AfterAll
    public static void cleanup() {
        for (String id : workflowIdsToTerminate) {
            try {
                workflowClient.terminateWorkflow(
                        id, "Terminated by cleanup in " + DoWhileTests.class.getSimpleName());
            } catch (Exception e) {
                if (!e.getMessage().contains("cannot be terminated")) {
                    log.warn("Error while cleaning up workflow {}: {}", id, e.getMessage());
                }
            }
        }
    }

    @Test
    public void testDoWhileScriptIsNotEvaluatedEarlyWhenSyncSystemTaskInside() {
        int errorCounter = 0;
        for (int i = 0; i < 10; ++i) {
            try {
                StartWorkflowRequest request = new StartWorkflowRequest();
                request.setName("do_while_early_eval_script");
                request.setInput(Map.of("namespace_id", "namespace_spa_sAPhRsGAGAbYNUSNZN3Jts"));
                String workflowId = workflowClient.startWorkflow(request);
                log.info("Started {}", workflowId);
                workflowIdsToTerminate.add(workflowId);

                Uninterruptibles.sleepUninterruptibly(300, TimeUnit.MILLISECONDS);
                String taskId1 =
                        workflowClient
                                .getWorkflow(workflowId, true)
                                .getTaskByRefName("reserve_token_ref")
                                .getTaskId();
                TaskResult taskResult1 = new TaskResult();
                taskResult1.setTaskId(taskId1);
                taskResult1.setStatus(TaskResult.Status.COMPLETED);
                taskResult1.setOutputData(Map.of("token_id", "2WdNxfi5p4lGPzbZD3IpDvruaWs"));
                taskResult1.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult1);

                Uninterruptibles.sleepUninterruptibly(300, TimeUnit.MILLISECONDS);
                String taskId2 =
                        workflowClient
                                .getWorkflow(workflowId, true)
                                .getTaskByRefName("acquire_token_ref")
                                .getTaskId();
                TaskResult taskResult2 = new TaskResult();
                taskResult2.setTaskId(taskId2);
                taskResult2.setStatus(TaskResult.Status.COMPLETED);
                taskResult2.setOutputData(Map.of("acquired_at", 1697058745255L));
                taskResult2.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult2);

                Uninterruptibles.sleepUninterruptibly(300, TimeUnit.MILLISECONDS);
                String taskId3 =
                        workflowClient
                                .getWorkflow(workflowId, true)
                                .getTaskByRefName("predictions_start_daily_bq_export_pipeline__1")
                                .getTaskId();
                TaskResult taskResult3 = new TaskResult();
                taskResult3.setTaskId(taskId3);
                taskResult3.setStatus(TaskResult.Status.COMPLETED);
                taskResult3.setOutputData(
                        Map.of(
                                "query_end_time", "2023-10-11T20:42:00Z",
                                "pipeline_started_at_rfc", "2023-10-11T21:12:27Z",
                                "pipeline_uid", "2WdNxykVDIDX9XpU5mtPtF0bZJi",
                                "namespace_enabled", true));
                taskResult3.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult3);

                Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);
                Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                Assertions.assertEquals(
                        Task.Status.COMPLETED,
                        workflow.getTaskByRefName("if_terminate_condition__1").getStatus());
                Assertions.assertEquals(
                        Task.Status.IN_PROGRESS,
                        workflow.getTaskByRefName("predictions_daily_bq_export_pipeline_loop")
                                .getStatus());
                Assertions.assertEquals(
                        Task.Status.SCHEDULED,
                        workflow.getTaskByRefName(
                                        "predictions_get_daily_bq_export_pipeline_status_long_running__1")
                                .getStatus());

                String taskId4 =
                        workflow.getTaskByRefName(
                                        "predictions_get_daily_bq_export_pipeline_status_long_running__1")
                                .getTaskId();
                TaskResult taskResult4 = new TaskResult();
                taskResult4.setTaskId(taskId4);
                taskResult4.setStatus(TaskResult.Status.COMPLETED);
                taskResult4.setOutputData(Map.of());
                taskResult4.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult4);

                Uninterruptibles.sleepUninterruptibly(300, TimeUnit.MILLISECONDS);
                String taskId5 =
                        workflowClient
                                .getWorkflow(workflowId, true)
                                .getTaskByRefName(
                                        "predictions_process_daily_bq_export_pipeline_results__1")
                                .getTaskId();
                TaskResult taskResult5 = new TaskResult();
                taskResult5.setTaskId(taskId5);
                taskResult5.setStatus(TaskResult.Status.COMPLETED);
                taskResult5.setOutputData(Map.of());
                taskResult5.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult5);

                Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
                workflow = workflowClient.getWorkflow(workflowId, true);
                Assertions.assertEquals(
                        Task.Status.COMPLETED,
                        workflow.getTaskByRefName("predictions_daily_bq_export_pipeline_loop")
                                .getStatus());
                Assertions.assertEquals(
                        Task.Status.SCHEDULED,
                        workflow.getTaskByRefName("return_token_ref").getStatus());

                String taskId6 = workflow.getTaskByRefName("return_token_ref").getTaskId();
                TaskResult taskResult6 = new TaskResult();
                taskResult6.setTaskId(taskId6);
                taskResult6.setStatus(TaskResult.Status.COMPLETED);
                taskResult6.setOutputData(Map.of());
                taskResult6.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult6);

                Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
                workflow = workflowClient.getWorkflow(workflowId, true);
                Assertions.assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            } catch (Exception e) {
                String message = e.getMessage();
                if (message != null
                        && (message.toLowerCase(Locale.ROOT).contains("socket")
                                || message.toLowerCase(Locale.ROOT).contains("timeout"))) {
                    log.info("timeout error, skipping");
                } else {
                    errorCounter++;
                    log.error("Error for iteration {}: {}", i, message, e);
                }
            }
        }

        log.info(
                "testDoWhileScriptIsNotEvaledEarlyWhenSyncSystemTaskInside error counter: {}",
                errorCounter);
        if (errorCounter > 0) {
            throw new RuntimeException("Failed with " + errorCounter + " errors");
        }
    }

    @Test
    public void testKeepLastN() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_keep_last_n");
        request.setInput(
                Map.of(
                        "batch",
                        List.of(
                                List.of(1, 2),
                                List.of(3, 4),
                                List.of(5, 6),
                                List.of(7, 8),
                                List.of(9, 10))));
        String workflowId = workflowClient.startWorkflow(request);
        log.debug("Started {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        await().pollInterval(5, TimeUnit.SECONDS)
                .atMost(60, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            Assertions.assertNotNull(workflow);
                            Assertions.assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
                            Assertions.assertNotNull(workflow.getTasks());
                            // There must be 11 tasks. 1 do_while and 2 inline, 2 fork, 4
                            // sub-workflow, 2 join.
                            Assertions.assertEquals(11, workflow.getTasks().size());
                        });
    }

    @Test
    public void testKeepLastN2() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_keep_last_n_2");
        request.setInput(
                Map.of(
                        "batch",
                        List.of(
                                List.of(1, 2),
                                List.of(3, 4),
                                List.of(5, 6),
                                List.of(7, 8),
                                List.of(9, 10))));
        String workflowId = workflowClient.startWorkflow(request);
        log.debug("Started {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        await().pollInterval(2000, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            Assertions.assertNotNull(workflow);
                            Assertions.assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
                            Assertions.assertNotNull(workflow.getTasks());
                            // There must be 32 tasks. 21 for first do_while and 11 for second
                            // do_while
                            Assertions.assertEquals(32, workflow.getTasks().size());
                        });
    }

    @Test
    public void testKeepLastN3() {
        // Fork with do_while each different keepLastN
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_keep_last_n_3");
        request.setInput(
                Map.of(
                        "batch",
                        List.of(
                                List.of(1, 2),
                                List.of(3, 4),
                                List.of(5, 6),
                                List.of(7, 8),
                                List.of(9, 10))));
        String workflowId = workflowClient.startWorkflow(request);
        workflowIdsToTerminate.add(workflowId);

        // Update all the tasks till workflow is COMPLETED.
        for (int i = 0; i < 5; i++) {
            Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
            completeTaskByRefName(workflowId, "simple_ref_1");
            Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
            completeTaskByRefName(workflowId, "simple_ref");
            Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
            completeTaskByRefName(workflowId, "simple_ref_2");
        }

        await().pollInterval(2000, TimeUnit.MILLISECONDS)
                .atMost(25, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            Assertions.assertNotNull(workflow);
                            Assertions.assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
                            Assertions.assertNotNull(workflow.getTasks());
                            // There must be 18 tasks.
                            Assertions.assertEquals(18, workflow.getTasks().size());
                        });
    }

    @Test
    public void testDoWhileSetVariableFix() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_set_variable_fix");
        String workflowId = workflowClient.startWorkflow(request);
        workflowIdsToTerminate.add(workflowId);

        // Sleep to allow the workflow to complete
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        await().pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertNotNull(workflow.getTasks());
                            assertEquals(8, workflow.getTasks().size());
                            assertEquals(
                                    "wait_ref_1__1",
                                    workflow.getTasks().get(5).getReferenceTaskName());
                            // Check that second iteration is scheduled after the second wait task.
                            assertTrue(
                                    workflow.getTasks().get(6).getStartTime()
                                            > workflow.getTasks().get(5).getEndTime());
                            assertTrue(
                                    workflow.getTasks().get(7).getStartTime()
                                            > workflow.getTasks().get(5).getEndTime());
                        });
    }

    @Test
    public void testDoWhileWaitSwitch() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_wait_switch");
        request.setVersion(1);
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started workflow {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        // Update the wait task 10 times: 9 times with "b" and 1 time with "a"
        for (int i = 0; i < 10; i++) {
            final int iteration = i;
            // Poll every second to check if the workflow is in the wait task
            await().pollInterval(1, TimeUnit.SECONDS)
                    .atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                                assertNotNull(workflow);

                                // Find the wait task in the current iteration
                                Task waitTask =
                                        workflow.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                t.getTaskType().equals("WAIT")
                                                                        && t.getStatus()
                                                                                == Task.Status
                                                                                        .IN_PROGRESS)
                                                .findFirst()
                                                .orElse(null);

                                assertNotNull(
                                        waitTask,
                                        "Wait task should be in progress at iteration "
                                                + iteration);
                            });

            // Get the workflow and find the wait task
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task waitTask =
                    workflow.getTasks().stream()
                            .filter(
                                    t ->
                                            t.getTaskType().equals("WAIT")
                                                    && t.getStatus() == Task.Status.IN_PROGRESS)
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Wait task not found"));

            // Update the wait task with the appropriate result
            TaskResult taskResult = new TaskResult();
            taskResult.setTaskId(waitTask.getTaskId());
            taskResult.setStatus(TaskResult.Status.COMPLETED);
            taskResult.setWorkflowInstanceId(workflowId);

            if (i < 9) {
                // First 9 times: update with "b"
                taskResult.setOutputData(Map.of("result", "b"));
                log.info("Updating wait task iteration {} with result 'b'", i);
            } else {
                // 10th time: update with "a"
                taskResult.setOutputData(Map.of("result", "a"));
                log.info("Updating wait task iteration {} with result 'a'", i);
            }

            taskClient.updateTask(taskResult);
        }

        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    workflow.getStatus(),
                                    "Workflow should be completed");
                        });

        // Assert that the do_while iterated 10 times
        Workflow finalWorkflow = workflowClient.getWorkflow(workflowId, true);
        Task doWhileTask = finalWorkflow.getTaskByRefName("do_while_ref");
        assertNotNull(doWhileTask, "do_while task should exist");

        // Check the iteration count in the output
        Object iteration = doWhileTask.getOutputData().get("iteration");
        assertNotNull(iteration, "iteration field should exist in do_while task output");
        assertEquals(10, iteration, "do_while should have iterated 10 times");

        log.info("Test completed successfully. Do-while iterated {} times", iteration);
    }

    @Test
    public void testDoWhileAutoTerminate() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_auto_terminate");
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started workflow {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        // Await for the workflow to finish
        // This workflow runs automatically and stops when iteration > 25
        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    workflow.getStatus(),
                                    "Workflow should be completed");
                        });

        // Assert that the do_while iterated 26 times
        Workflow finalWorkflow = workflowClient.getWorkflow(workflowId, true);
        Task doWhileTask = finalWorkflow.getTaskByRefName("do_while_ref");
        assertNotNull(doWhileTask, "do_while task should exist");

        Object iteration = doWhileTask.getOutputData().get("iteration");
        assertNotNull(iteration, "iteration field should exist in do_while task output");
        assertEquals(26, iteration, "do_while should have iterated 26 times");

        log.info("Test completed successfully. Do-while iterated {} times", iteration);
    }

    @Test
    public void testStackoverflower() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_stackoverflower");
        request.setInput(Map.of("n", 999));
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started workflow {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    workflow.getStatus(),
                                    "Workflow should be completed");
                        });

        Workflow finalWorkflow = workflowClient.getWorkflow(workflowId, true);
        Task loopTask = finalWorkflow.getTaskByRefName("loop_ref");
        assertNotNull(loopTask, "loop task should exist");

        Object iteration = loopTask.getOutputData().get("iteration");
        assertNotNull(iteration, "iteration field should exist in loop task output");
        assertEquals(999, iteration, "loop should have iterated 999 times");

        log.info("Test completed successfully. Loop iterated {} times", iteration);
    }

    private void completeTaskByRefName(String workflowId, String refName) {
        try {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task task =
                    workflow.getTasks().stream()
                            .filter(
                                    t ->
                                            t.getReferenceTaskName().contains(refName)
                                                    && t.getStatus() == Task.Status.SCHEDULED)
                            .findFirst()
                            .orElse(null);
            if (task != null) {
                TaskResult result = new TaskResult();
                result.setTaskId(task.getTaskId());
                result.setWorkflowInstanceId(workflowId);
                result.setStatus(TaskResult.Status.COMPLETED);
                result.setOutputData(Map.of());
                taskClient.updateTask(result);
            }
        } catch (Exception e) {
            log.warn("Could not complete task {}: {}", refName, e.getMessage());
        }
    }
}
