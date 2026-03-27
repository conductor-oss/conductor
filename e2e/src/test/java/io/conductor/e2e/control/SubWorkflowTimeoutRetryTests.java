/*
 * Copyright 2023 Conductor Authors.
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class SubWorkflowTimeoutRetryTests {

    private static WorkflowClient workflowClient;

    private static TaskClient taskClient;

    private static MetadataClient metadataClient;

    private static ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private static TypeReference<List<WorkflowDef>> WORKFLOW_DEF_LIST =
            new TypeReference<List<WorkflowDef>>() {};

    private static final String WORKFLOW_NAME = "integration_test_wf_with_sub_wf";

    private static Map<String, String> taskToDomainMap = new HashMap<>();

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        InputStream resource =
                SubWorkflowTimeoutRetryTests.class.getResourceAsStream(
                        "/metadata/sub_workflow_tests.json");
        List<WorkflowDef> workflowDefs =
                objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF_LIST);
        metadataClient.updateWorkflowDefs(workflowDefs);
        Set<String> tasks = new HashSet<>();
        for (WorkflowDef workflowDef : workflowDefs) {
            List<WorkflowTask> allTasks = workflowDef.collectTasks();
            tasks.addAll(
                    allTasks.stream()
                            .filter(tt -> !tt.getType().equals("SIMPLE"))
                            .map(t -> t.getType())
                            .collect(Collectors.toSet()));

            tasks.addAll(
                    allTasks.stream()
                            .filter(tt -> tt.getType().equals("SIMPLE"))
                            .map(t -> t.getName())
                            .collect(Collectors.toSet()));
        }
        log.info(
                "Updated workflow definitions: {}",
                workflowDefs.stream().map(def -> def.getName()).collect(Collectors.toList()));
    }

    @Test
    public void test() {

        String correlationId = "wf_with_subwf_test_1";
        Map<String, Object> input = Map.of("param1", "p1 value", "subwf", "sub_workflow");

        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(WORKFLOW_NAME);
        request.setVersion(1);
        request.setCorrelationId(correlationId);
        request.setInput(input);
        String workflowInstanceId = workflowClient.startWorkflow(request);

        log.info("Started {} ", workflowInstanceId);
        pollAndCompleteTask(workflowInstanceId, "integration_task_1", Map.of());
        Workflow workflow = workflowClient.getWorkflow(workflowInstanceId, true);
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 =
                                    workflowClient.getWorkflow(workflowInstanceId, true);
                            assertNotNull(workflow1);
                            assertEquals(2, workflow1.getTasks().size());
                            assertEquals(
                                    Task.Status.COMPLETED, workflow1.getTasks().get(0).getStatus());
                            assertEquals(
                                    TaskType.SUB_WORKFLOW.name(),
                                    workflow1.getTasks().get(1).getTaskType());
                            assertEquals(
                                    Task.Status.IN_PROGRESS,
                                    workflow1.getTasks().get(1).getStatus());
                        });
        workflow = workflowClient.getWorkflow(workflowInstanceId, true);
        String subWorkflowId = workflow.getTasks().get(1).getSubWorkflowId();
        log.info("Sub workflow Id {} ", subWorkflowId);

        assertNotNull(subWorkflowId);
        Workflow subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());

        // Wait for 7 seconds which is > 5 sec timeout for the workflow
        try {
            Thread.sleep(7000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        workflowClient.runDecider(workflowInstanceId);

        workflow = workflowClient.getWorkflow(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(Task.Status.CANCELED, workflow.getTasks().get(1).getStatus());

        // Verify that the sub-workflow is terminated
        subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, subWorkflow.getStatus());

        // Retry sub-workflow
        workflowClient.retryLastFailedTask(subWorkflowId);

        // Sub workflow should be in the running state now
        subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(Task.Status.CANCELED, subWorkflow.getTasks().get(0).getStatus());
        assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(1).getStatus());
    }

    private Task pollAndCompleteTask(
            String workflowInstanceId, String taskName, Map<String, Object> output) {
        Workflow workflow = workflowClient.getWorkflow(workflowInstanceId, true);
        if (workflow == null) {
            return null;
        }
        Optional<Task> optional =
                workflow.getTasks().stream()
                        .filter(task -> task.getTaskDefName().equals(taskName))
                        .findFirst();
        if (optional.isEmpty()) {
            return null;
        }
        Task task = optional.get();
        task.setStatus(Task.Status.COMPLETED);
        task.getOutputData().putAll(output);
        taskClient.updateTask(new TaskResult(task));

        return task;
    }
}
