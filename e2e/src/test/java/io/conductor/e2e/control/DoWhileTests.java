/*
 * Copyright 2026 Conductor Authors.
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class DoWhileTests {

    private static final String WORKFLOW_NAME = "DoWhileEarlyEvalScript";
    private static final String WORKFLOW_NAME_KEEP_LAST_N = "keep_last_n_example";
    private static final String WORKFLOW_NAME_KEEP_LAST_N_2 = "keep_last_n_example_2";
    private static final String WORKFLOW_NAME_KEEP_LAST_N_3 = "keep_last_n_example_3";
    private static final String DO_WHILE_SET_VARIABLE_FIX = "do-while-set-variable-fix";
    private static final List<String> workflowIdsToTerminate = new ArrayList<>();
    private static WorkflowClient workflowClient;
    private static com.netflix.conductor.client.http.TaskClient taskClient;
    private static MetadataClient metadataClient;
    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private static final TypeReference<List<WorkflowDef>> WORKFLOW_DEF_LIST =
            new TypeReference<List<WorkflowDef>>() {};

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        InputStream resource =
                DoWhileTests.class.getResourceAsStream(
                        "/metadata/do_while_early_script_eval_wf.json");
        assert resource != null;
        List<WorkflowDef> workflowDefs =
                objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF_LIST);
        resource =
                DoWhileTests.class.getResourceAsStream("/metadata/do_while_keep_last_n_fix.json");
        assert resource != null;
        workflowDefs.addAll(
                objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF_LIST));
        metadataClient.updateWorkflowDefs(workflowDefs);
        log.info(
                "Updated workflow definitions: {}",
                workflowDefs.stream().map(WorkflowDef::getName).collect(Collectors.toList()));
    }

    @AfterAll
    public static void cleanup() {
        try {
            workflowIdsToTerminate.forEach(
                    id -> {
                        workflowClient.terminateWorkflow(
                                id,
                                String.format(
                                        "Terminated by cleanup in %s",
                                        DoWhileTests.class.getSimpleName()));
                    });
        } catch (Exception e) {
            if (!e.getMessage().contains("Cannot terminate a COMPLETED workflow.")) {
                log.error(
                        "Error while cleaning up in {} : {}",
                        DoWhileTests.class.getSimpleName(),
                        e.getMessage(),
                        e);
            }
        }
    }

    @Test
    public void testDoWhileScriptIsNotEvaledEarlyWhenSyncSystemTaskInside() {

        int errorCounter = 0;
        for (int i = 0; i < 10; ++i) {
            try {
                StartWorkflowRequest request = new StartWorkflowRequest();
                request.setName(WORKFLOW_NAME);
                request.setInput(Map.of("namespace_id", "namespace_spa_sAPhRsGAGAbYNUSNZN3Jts"));
                String workflowId = workflowClient.startWorkflow(request);
                log.info("Started {}", workflowId);
                workflowIdsToTerminate.add(workflowId);

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
                                "query_end_time",
                                "2023-10-11T20:42:00Z",
                                "pipeline_started_at_rfc",
                                "2023-10-11T21:12:27Z",
                                "pipeline_uid",
                                "2WdNxykVDIDX9XpU5mtPtF0bZJi",
                                "namespace_enabled",
                                true));
                taskResult3.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult3);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                Assertions.assertEquals(
                        workflow.getTaskByRefName("if_terminate_condition__1").getStatus(),
                        Task.Status.COMPLETED);
                Assertions.assertEquals(
                        workflow.getTaskByRefName("predictions_daily_bq_export_pipeline_loop")
                                .getStatus(),
                        Task.Status.IN_PROGRESS);
                Assertions.assertEquals(
                        workflow.getTaskByRefName(
                                        "predictions_get_daily_bq_export_pipeline_status_long_running__1")
                                .getStatus(),
                        Task.Status.SCHEDULED);

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

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
                workflow =
                        taskClient.updateTaskSync(
                                workflowId,
                                "predictions_process_daily_bq_export_pipeline_results__1",
                                TaskResult.Status.COMPLETED,
                                Map.of());

                Assertions.assertEquals(
                        Task.Status.COMPLETED,
                        workflow.getTaskByRefName("predictions_daily_bq_export_pipeline_loop")
                                .getStatus());
                Assertions.assertEquals(
                        workflow.getTaskByRefName("return_token_ref").getStatus(),
                        Task.Status.SCHEDULED);

                String taskId6 = workflow.getTaskByRefName("return_token_ref").getTaskId();
                TaskResult taskResult6 = new TaskResult();
                taskResult6.setTaskId(taskId6);
                taskResult6.setStatus(TaskResult.Status.COMPLETED);
                taskResult6.setOutputData(Map.of());
                taskResult6.setWorkflowInstanceId(workflowId);
                taskClient.updateTask(taskResult6);
                workflowClient.runDecider(workflowId);
                workflow = workflowClient.getWorkflow(workflowId, true);
                Assertions.assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            } catch (Exception e) {
                String message = e.getMessage();
                if (message != null
                        && !message.toLowerCase(Locale.ROOT).contains("socket")
                        && !message.toLowerCase(Locale.ROOT).contains("timeout")) {
                    log.info("timeout error, skipping");
                } else {
                    errorCounter++;
                }
                log.error("Error for iteration {}: {}", i, message, e);
            }
        }

        log.info(
                "testDoWhileScriptIsNotEvaledEarlyWhenSyncSystemTaskInside error counter: {}",
                errorCounter);
        if (errorCounter > 0) {
            throw new RuntimeException("Failed");
        }
    }

    @Test
    public void testKeepLstN3() {
        // Fork with do_while each different keepLastN
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(WORKFLOW_NAME_KEEP_LAST_N_3);
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

        // Update all the task till workflow is COMPLETED.
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            taskClient.updateTaskSync(
                    workflowId, "simple_ref_1", TaskResult.Status.COMPLETED, Map.of());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            taskClient.updateTaskSync(
                    workflowId, "simple_ref", TaskResult.Status.COMPLETED, Map.of());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            taskClient.updateTaskSync(
                    workflowId, "simple_ref_2", TaskResult.Status.COMPLETED, Map.of());
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
                            // There must be 18 tasks. 1 fork, set_variable, inline,
                            Assertions.assertEquals(18, workflow.getTasks().size());
                        });
    }

    @Test
    public void testDoWhileSetVariableFix() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(DO_WHILE_SET_VARIABLE_FIX);
        String workflowId = workflowClient.startWorkflow(request);
        // Sleep to allow the workflow to complete.
        // The workflow has two sequential WAIT tasks with 2-second duration each.
        // With conductor-oss postgres queue, WAIT sweeper adds ~10s overhead per task,
        // so 2 tasks need ~25-30s total.
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        await().pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(30))
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
}
