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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
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
public class SubWorkflowTests {

    private static WorkflowClient workflowClient;

    private static TaskClient taskClient;

    private static MetadataClient metadataClient;

    private static ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private static TypeReference<List<WorkflowDef>> WORKFLOW_DEF_LIST =
            new TypeReference<List<WorkflowDef>>() {};

    private static final String WORKFLOW_NAME = "sub_workflow_test";

    private static Map<String, String> taskToDomainMap = new HashMap<>();

    private static TaskRunnerConfigurer configurer;

    private static TaskRunnerConfigurer configurerNoDomain;

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        InputStream resource =
                SubWorkflowTests.class.getResourceAsStream("/metadata/workflows.json");
        List<WorkflowDef> workflowDefs =
                objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF_LIST);
        metadataClient.updateWorkflowDefs(workflowDefs);
        Set<String> tasks = new HashSet<>();
        List<String> internalTasks =
                List.of(
                        "HTTP",
                        "BUSINESS_RULE",
                        "AWS_LAMBDA",
                        "JDBC",
                        "WAIT_FOR_EVENT",
                        "PUBLISH_BUSINESS_STATE",
                        "WAIT",
                        "WAIT_FOR_WEBHOOK",
                        "DECISION",
                        "SWITCH",
                        "DYNAMIC",
                        "JOIN",
                        "DO_WHILE",
                        "FORK_JOIN_DYNAMIC",
                        "FORK_JOIN",
                        "JSON_JQ_TRANSFORM",
                        "FORK");
        for (WorkflowDef workflowDef : workflowDefs) {
            List<WorkflowTask> allTasks = workflowDef.collectTasks();
            tasks.addAll(
                    allTasks.stream()
                            .filter(
                                    tt ->
                                            !tt.getType().equals("SIMPLE")
                                                    && !internalTasks.contains(tt.getType()))
                            .map(t -> t.getType())
                            .collect(Collectors.toSet()));

            tasks.addAll(
                    allTasks.stream()
                            .filter(
                                    tt ->
                                            tt.getType().equals("SIMPLE")
                                                    && !internalTasks.contains(tt.getType()))
                            .map(t -> t.getName())
                            .collect(Collectors.toSet()));
        }
        startWorkers(tasks);
        log.info(
                "Updated workflow definitions: {}",
                workflowDefs.stream().map(def -> def.getName()).collect(Collectors.toList()));
    }

    @AfterAll
    public static void cleanup() {
        try {
            if (configurer != null) {
                configurer.shutdown();
            }
            if (configurerNoDomain != null) {
                configurerNoDomain.shutdown();
            }
        } catch (Throwable t) {
            // Ignore any issue with shutdown
        }
    }

    @Test
    public void testSubWorkflowWithDomain() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(WORKFLOW_NAME);
        request.setTaskToDomain(taskToDomainMap);
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started {}", workflowId);
        assertSubworkflowWithDomain(workflowId);

        int restartCount = 2;
        for (int i = 0; i < restartCount; i++) {
            workflowClient.restart(workflowId, true);
            assertSubworkflowWithDomain(workflowId);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void assertSubworkflowWithDomain(String workflowId) {
        await().atMost(120, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED.name(),
                                    workflow.getStatus().name());
                            Map<String, String> workflowTaskToDomain = workflow.getTaskToDomain();
                            assertNotNull(workflowTaskToDomain);
                            assertTrue(!workflowTaskToDomain.isEmpty());
                            for (Map.Entry<String, String> taskToDomain :
                                    workflowTaskToDomain.entrySet()) {
                                String taskName = taskToDomain.getKey();
                                String domain = taskToDomain.getValue();
                                assertEquals(domain, taskToDomainMap.get(taskName));
                            }
                            workflow.getTasks().stream()
                                    .filter(t -> t.getTaskType().equals("SUB_WORKFLOW"))
                                    .forEach(
                                            subWorkflowTask -> {
                                                String subWorkflowId =
                                                        subWorkflowTask.getSubWorkflowId();
                                                Workflow subWorkflow =
                                                        workflowClient.getWorkflow(
                                                                subWorkflowId, true);
                                                Map<String, String> subWorkflowDomainMap =
                                                        subWorkflow.getTaskToDomain();
                                                assertNotNull(subWorkflowDomainMap);
                                                assertTrue(!subWorkflowDomainMap.isEmpty());

                                                for (Map.Entry<String, String> taskToDomain :
                                                        subWorkflowDomainMap.entrySet()) {
                                                    String taskName = taskToDomain.getKey();
                                                    String domain = taskToDomain.getValue();
                                                    assertEquals(
                                                            domain, taskToDomainMap.get(taskName));
                                                }

                                                SubWorkflowParams subWorkflowParams =
                                                        subWorkflowTask
                                                                .getWorkflowTask()
                                                                .getSubWorkflowParam();
                                                if (subWorkflowParams.getWorkflowDefinition()
                                                        == null) {
                                                    Integer version =
                                                            subWorkflowParams.getVersion();
                                                    log.info(
                                                            "version is {} for {} / {}",
                                                            version,
                                                            workflowId,
                                                            subWorkflowTask.getReferenceTaskName());
                                                    // version=null and version=0 both mean "use
                                                    // latest" in conductor-oss
                                                    if (version == null || version == 0) {
                                                        assertEquals(
                                                                3,
                                                                subWorkflow.getWorkflowVersion());
                                                    } else {
                                                        assertEquals(
                                                                version,
                                                                subWorkflow.getWorkflowVersion());
                                                    }
                                                }
                                            });
                        });
    }

    @Test
    public void testSubworkflowExecutionWithOutDomains() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(WORKFLOW_NAME);
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started {}", workflowId);
        assertSubworkflowExecutionWithOutDomains(workflowId);

        int restartCount = 2;
        for (int i = 0; i < restartCount; i++) {
            workflowClient.restart(workflowId, true);
            assertSubworkflowExecutionWithOutDomains(workflowId);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void assertSubworkflowExecutionWithOutDomains(String workflowId) {
        await().atMost(120, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());

                            Map<String, String> workflowTaskToDomain = workflow.getTaskToDomain();
                            assertEquals(0, workflowTaskToDomain.size());

                            workflow.getTasks().stream()
                                    .filter(t -> t.getTaskType().equals("SUB_WORKFLOW"))
                                    .forEach(
                                            subWorkflowTask -> {
                                                String subWorkflowId =
                                                        subWorkflowTask.getSubWorkflowId();
                                                Workflow subWorkflow =
                                                        workflowClient.getWorkflow(
                                                                subWorkflowId, true);
                                                Map<String, String> subWorkflowDomainMap =
                                                        subWorkflow.getTaskToDomain();
                                                assertEquals(0, subWorkflowDomainMap.size());

                                                SubWorkflowParams subWorkflowParams =
                                                        subWorkflowTask
                                                                .getWorkflowTask()
                                                                .getSubWorkflowParam();
                                                if (subWorkflowParams.getWorkflowDefinition()
                                                        == null) {
                                                    Integer version =
                                                            subWorkflowParams.getVersion();
                                                    log.info(
                                                            "version is {} for {} / {}",
                                                            version,
                                                            workflowId,
                                                            subWorkflowTask.getReferenceTaskName());
                                                    // version=null and version=0 both mean "use
                                                    // latest" in conductor-oss
                                                    if (version == null || version == 0) {
                                                        assertEquals(
                                                                3,
                                                                subWorkflow.getWorkflowVersion());
                                                    } else {
                                                        assertEquals(
                                                                version,
                                                                subWorkflow.getWorkflowVersion());
                                                    }
                                                }
                                            });
                        });
    }

    private static void startWorkers(Set<String> tasks) {
        log.info("Starting workers for {} with domainMap", tasks, taskToDomainMap);
        List<Worker> workers = new ArrayList<>();
        // Use unique prefix to prevent conflicts with other test classes
        String uniquePrefix = "SubWorkflowTests_" + UUID.randomUUID().toString().substring(0, 8);
        for (String task : tasks) {
            workers.add(new TestWorker(task));
            taskToDomainMap.put(task, uniquePrefix + "_" + UUID.randomUUID().toString());
        }
        configurer =
                new TaskRunnerConfigurer.Builder(taskClient, workers)
                        .withTaskToDomain(taskToDomainMap)
                        .withThreadCount(10)
                        .withTaskPollTimeout(10)
                        .build();
        configurer.init();

        configurerNoDomain =
                new TaskRunnerConfigurer.Builder(taskClient, workers)
                        .withThreadCount(10)
                        .withTaskPollTimeout(10)
                        .build();
        configurerNoDomain.init();
    }

    private static class TestWorker implements Worker {

        private String name;

        public TestWorker(String name) {
            this.name = name;
        }

        @Override
        public String getTaskDefName() {
            return name;
        }

        @Override
        public TaskResult execute(Task task) {
            TaskResult result = new TaskResult(task);
            result.getOutputData().put("number", 42);
            result.setStatus(TaskResult.Status.COMPLETED);
            return result;
        }

        @Override
        public int getPollingInterval() {
            return 100;
        }
    }
}
