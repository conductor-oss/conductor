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
package io.conductor.e2e.task;

import java.util.*;
import java.util.concurrent.TimeUnit;

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
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class BackoffTests {

    private static WorkflowClient workflowClient;

    private static TaskClient taskClient;

    private static MetadataClient metadataClient;

    private static ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private static final String WORKFLOW_NAME = "retry_logic_test";

    private static TaskRunnerConfigurer configurer;

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;

        ConductorWorkflow workflow = new ConductorWorkflow(null);
        workflow.setName(WORKFLOW_NAME);
        workflow.setVersion(1);

        List<TaskDef> taskDefs = new ArrayList<>();
        int i = 0;
        for (TaskDef.RetryLogic value : TaskDef.RetryLogic.values()) {
            TaskDef taskDef = new TaskDef();
            taskDef.setName("retry_" + i++);
            taskDef.setRetryLogic(value);
            taskDef.setBackoffScaleFactor(2);
            taskDef.setRetryDelaySeconds(2);
            taskDef.setRetryCount(3);
            taskDefs.add(taskDef);

            workflow.add(new SimpleTask(taskDef.getName(), taskDef.getName()));
        }

        metadataClient.registerTaskDefs(taskDefs);
        metadataClient.updateWorkflowDefs(Arrays.asList(workflow.toWorkflowDef()));
        startWorkers(taskDefs);
    }

    @AfterAll
    public static void cleanup() {
        if (configurer != null) {
            try {
                configurer.shutdown();
            } catch (Exception e) {
            }
        }
    }

    @Test
    public void testRetryLogic() {
        try {
            StartWorkflowRequest request = new StartWorkflowRequest();
            request.setName(WORKFLOW_NAME);
            request.setVersion(1);
            request.setInput(Map.of());
            String id = workflowClient.startWorkflow(request);
            log.info("Started Retry logic workflow {} ", id);

            await().pollInterval(3, TimeUnit.SECONDS)
                    .atMost(1, TimeUnit.MINUTES)
                    .untilAsserted(
                            () -> {
                                Workflow workflow = workflowClient.getWorkflow(id, true);
                                assertNotNull(workflow);
                                assertEquals(
                                        Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
                            });

            Workflow workflow = workflowClient.getWorkflow(id, true);
            assertNotNull(workflow);
            assertEquals(9, workflow.getTasks().size());
            List<Task> tasks = workflow.getTasks();
            assertTaskRetryLogic(tasks);
        } catch (Exception e) {
        }
    }

    private void assertTaskRetryLogic(List<Task> runs) {
        for (int i = 1; i < runs.size(); i++) {
            Task task = runs.get(i);
            TaskDef.RetryLogic retryLogic = task.getTaskDefinition().get().getRetryLogic();
            long delay = task.getTaskDefinition().get().getRetryDelaySeconds() * 1000;
            long backoffRate = task.getTaskDefinition().get().getBackoffScaleFactor();
            switch (retryLogic) {
                case FIXED:
                    long diff = task.getStartTime() - task.getScheduledTime();
                    long expectedDelay = delay;
                    assertTrue(
                            diff >= (expectedDelay - 1),
                            "delay "
                                    + diff
                                    + " not within the range of expected "
                                    + expectedDelay
                                    + ", taskId = "
                                    + task.getReferenceTaskName()
                                    + ":"
                                    + task.getTaskId());
                    break;
                case LINEAR_BACKOFF:
                    diff = task.getStartTime() - task.getScheduledTime();
                    expectedDelay = task.getRetryCount() * delay * backoffRate;
                    assertTrue(
                            diff >= (expectedDelay - 1),
                            "delay "
                                    + diff
                                    + " not within the range of expected "
                                    + expectedDelay
                                    + ", taskId = "
                                    + task.getReferenceTaskName()
                                    + ":"
                                    + task.getTaskId());
                    break;
                case EXPONENTIAL_BACKOFF:
                    diff = task.getStartTime() - task.getScheduledTime();
                    if (task.getRetryCount() == 0) {
                        expectedDelay = 0;
                    } else {
                        expectedDelay = (long) (Math.pow(2, task.getRetryCount() - 1) * (delay));
                    }
                    assertTrue(
                            diff >= (expectedDelay - 1),
                            "delay "
                                    + diff
                                    + " not within the range of expected "
                                    + expectedDelay
                                    + ", taskId = "
                                    + task.getReferenceTaskName()
                                    + ":"
                                    + task.getTaskId());
                    break;
                default:
                    break;
            }
        }
    }

    private static void startWorkers(List<TaskDef> tasks) {
        List<Worker> workers = new ArrayList<>();
        for (TaskDef task : tasks) {
            workers.add(new TestWorker(task.getName()));
        }

        configurer =
                new TaskRunnerConfigurer.Builder(taskClient, workers)
                        .withThreadCount(1)
                        .withTaskPollTimeout(10)
                        .build();
        configurer.init();
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
            if (task.getRetryCount() < 2) {
                result.setStatus(TaskResult.Status.FAILED);
            } else {
                result.setStatus(TaskResult.Status.COMPLETED);
            }

            return result;
        }

        @Override
        public int getPollingInterval() {
            return 100;
        }
    }
}
