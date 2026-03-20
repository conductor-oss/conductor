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
package io.conductor.e2e.task;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class ConcurrentExecLimitTests {

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

    private void terminateExistingRunningWorkflows(String workflowName) {
        // clean up first
        SearchResult<WorkflowSummary> found =
                workflowClient.search(
                        "workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
        System.out.println(
                "Found " + found.getResults().size() + " running workflows to be cleaned up");
        found.getResults()
                .forEach(
                        workflowSummary -> {
                            try {
                                System.out.println(
                                        "[ConcurrentExecLimit] Going to terminate "
                                                + workflowSummary.getWorkflowId()
                                                + " with status "
                                                + workflowSummary.getStatus());
                                workflowClient.terminateWorkflow(
                                        workflowSummary.getWorkflowId(),
                                        "terminate - ConcurrentExecLimit test");
                            } catch (Exception e) {
                                if (!(e instanceof ConductorClientException)) {
                                    throw e;
                                }
                            }
                        });
    }

    @SneakyThrows
    protected static String getResourceAsString(String classpathResource) {
        InputStream stream =
                ConcurrentExecLimitTests.class
                        .getClassLoader()
                        .getResourceAsStream(classpathResource);
        byte[] data = IOUtils.toByteArray(stream);
        return new String(data);
    }

    @Test
    @DisplayName("Check concurrent exec limit test")
    public void testTerminateForDataInconsistency() {
        String workflowName = "exec_limit_check";
        String taskName = "exec_limit";

        terminateExistingRunningWorkflows(workflowName);

        try {
            WorkflowDef workflowDef =
                    objectMapper.readValue(
                            getResourceAsString("exec_limit_workflow.json"), WorkflowDef.class);
            metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
            TaskDef tasKDef = new TaskDef(taskName);
            tasKDef.setConcurrentExecLimit(2);
            metadataClient.registerTaskDefs(List.of(tasKDef));
        } catch (IOException e) {
            log.error("Failed to read and register workflow", e);
        }

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        workflowClient.getWorkflow(workflowId, true);

        // With default taskExecutionPostponeDuration=60s, the 6-task fork needs up to ~180s to
        // complete.
        // To speed up, set conductor.app.taskExecutionPostponeDuration=10s in server config.
        await().atMost(180, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            try {
                                List<Task> task =
                                        taskClient.batchPollTasksByTaskType(
                                                taskName, "worker", 10, 1000);
                                if (task != null) {
                                    System.out.println("Got " + task.size() + " tasks...");
                                    assertTrue(task.size() <= 2);
                                    task.forEach(
                                            task1 -> {
                                                taskClient.updateTaskSync(
                                                        workflowId,
                                                        task1.getReferenceTaskName(),
                                                        TaskResult.Status.COMPLETED,
                                                        Map.of("updatedBy", "e2e"));
                                            });
                                }
                                assertEquals(
                                        Workflow.WorkflowStatus.COMPLETED,
                                        workflowClient.getWorkflow(workflowId, false).getStatus());
                            } catch (Exception e) {
                                if (!(e instanceof ConductorClientException)) {
                                    throw e;
                                }
                            }
                        });
    }
}
