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
package com.netflix.conductor.test.util;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Helper class that initializes task definitions and provides poll/complete/fail utilities for
 * integration tests. Replaces the Groovy WorkflowTestUtil; poll methods now return {@link Task}
 * directly instead of a Spock {@code Tuple}.
 */
@Component
public class WorkflowTestUtil {

    private final MetadataService metadataService;
    private final ExecutionService workflowExecutionService;
    private final WorkflowExecutor workflowExecutor;
    private final QueueDAO queueDAO;
    private final ObjectMapper objectMapper;

    private static final int RETRY_COUNT = 1;
    private static final String TEMP_FILE_PATH = "/input.json";
    private static final String DEFAULT_EMAIL_ADDRESS = "test@harness.com";

    @Autowired
    public WorkflowTestUtil(
            MetadataService metadataService,
            ExecutionService workflowExecutionService,
            WorkflowExecutor workflowExecutor,
            QueueDAO queueDAO,
            ObjectMapper objectMapper) {
        this.metadataService = metadataService;
        this.workflowExecutionService = workflowExecutionService;
        this.workflowExecutor = workflowExecutor;
        this.queueDAO = queueDAO;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void taskDefinitions() {
        WorkflowContext.set(new WorkflowContext("integration_app"));

        IntStream.rangeClosed(0, 20)
                .mapToObj(i -> "integration_task_" + i)
                .filter(name -> !getPersistedTaskDefinition(name).isPresent())
                .map(name -> new TaskDef(name, name, DEFAULT_EMAIL_ADDRESS, 1, 120, 120))
                .forEach(td -> metadataService.registerTaskDef(List.of(td)));

        IntStream.rangeClosed(0, 4)
                .mapToObj(i -> "integration_task_0_RT_" + i)
                .filter(name -> !getPersistedTaskDefinition(name).isPresent())
                .map(name -> new TaskDef(name, name, DEFAULT_EMAIL_ADDRESS, 0, 120, 120))
                .forEach(td -> metadataService.registerTaskDef(List.of(td)));

        metadataService.registerTaskDef(
                List.of(
                        new TaskDef(
                                "short_time_out",
                                "short_time_out",
                                DEFAULT_EMAIL_ADDRESS,
                                1,
                                5,
                                5)));

        TaskDef taskWithResponseTimeOut = new TaskDef();
        taskWithResponseTimeOut.setName("task_rt");
        taskWithResponseTimeOut.setTimeoutSeconds(120);
        taskWithResponseTimeOut.setRetryCount(RETRY_COUNT);
        taskWithResponseTimeOut.setRetryDelaySeconds(0);
        taskWithResponseTimeOut.setResponseTimeoutSeconds(10);
        taskWithResponseTimeOut.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef optionalTask = new TaskDef();
        optionalTask.setName("task_optional");
        optionalTask.setTimeoutSeconds(5);
        optionalTask.setRetryCount(1);
        optionalTask.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        optionalTask.setRetryDelaySeconds(0);
        optionalTask.setResponseTimeoutSeconds(5);
        optionalTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef simpleSubWorkflowTask = new TaskDef();
        simpleSubWorkflowTask.setName("simple_task_in_sub_wf");
        simpleSubWorkflowTask.setRetryCount(0);
        simpleSubWorkflowTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef subWorkflowTask = new TaskDef();
        subWorkflowTask.setName("sub_workflow_task");
        subWorkflowTask.setRetryCount(1);
        subWorkflowTask.setResponseTimeoutSeconds(5);
        subWorkflowTask.setRetryDelaySeconds(0);
        subWorkflowTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef waitTimeOutTask = new TaskDef();
        waitTimeOutTask.setName("waitTimeout");
        waitTimeOutTask.setTimeoutSeconds(2);
        waitTimeOutTask.setResponseTimeoutSeconds(2);
        waitTimeOutTask.setRetryCount(1);
        waitTimeOutTask.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        waitTimeOutTask.setRetryDelaySeconds(10);
        waitTimeOutTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef userTask = new TaskDef();
        userTask.setName("user_task");
        userTask.setTimeoutSeconds(20);
        userTask.setResponseTimeoutSeconds(20);
        userTask.setRetryCount(1);
        userTask.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        userTask.setRetryDelaySeconds(10);
        userTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef concurrentExecutionLimitedTask = new TaskDef();
        concurrentExecutionLimitedTask.setName("test_task_with_concurrency_limit");
        concurrentExecutionLimitedTask.setConcurrentExecLimit(1);
        concurrentExecutionLimitedTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef rateLimitedTask = new TaskDef();
        rateLimitedTask.setName("test_task_with_rateLimits");
        rateLimitedTask.setRateLimitFrequencyInSeconds(10);
        rateLimitedTask.setRateLimitPerFrequency(1);
        rateLimitedTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef rateLimitedSimpleTask = new TaskDef();
        rateLimitedSimpleTask.setName("test_simple_task_with_rateLimits");
        rateLimitedSimpleTask.setRateLimitFrequencyInSeconds(10);
        rateLimitedSimpleTask.setRateLimitPerFrequency(1);
        rateLimitedSimpleTask.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        TaskDef eventTaskX = new TaskDef();
        eventTaskX.setName("eventX");
        eventTaskX.setTimeoutSeconds(10);
        eventTaskX.setResponseTimeoutSeconds(10);
        eventTaskX.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);

        metadataService.registerTaskDef(
                List.of(
                        taskWithResponseTimeOut,
                        optionalTask,
                        simpleSubWorkflowTask,
                        subWorkflowTask,
                        waitTimeOutTask,
                        userTask,
                        eventTaskX,
                        rateLimitedTask,
                        rateLimitedSimpleTask,
                        concurrentExecutionLimitedTask));
    }

    public void clearWorkflows() throws Exception {
        List<String> workflowsWithVersion =
                metadataService.getWorkflowDefs().stream()
                        .map(def -> def.getName() + ":" + def.getVersion())
                        .collect(Collectors.toList());

        for (String workflowWithVersion : workflowsWithVersion) {
            String workflowName = StringUtils.substringBefore(workflowWithVersion, ":");
            int version = Integer.parseInt(StringUtils.substringAfter(workflowWithVersion, ":"));
            List<String> running =
                    workflowExecutionService.getRunningWorkflows(workflowName, version);
            for (String workflowId : running) {
                try {
                    WorkflowModel workflow = workflowExecutor.getWorkflow(workflowId, false);
                    if (!workflow.getStatus().isTerminal()) {
                        workflowExecutor.terminateWorkflow(workflowId, "cleanup");
                    }
                } catch (Exception e) {
                    try {
                        workflowExecutor.terminateWorkflow(workflowId, "cleanup");
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        queueDAO.queuesDetail().keySet().forEach(queueDAO::flush);

        new FileOutputStream(this.getClass().getResource(TEMP_FILE_PATH).getPath()).close();
    }

    public Optional<TaskDef> getPersistedTaskDefinition(String taskDefName) {
        try {
            return Optional.of(metadataService.getTaskDef(taskDefName));
        } catch (NotFoundException nfe) {
            return Optional.empty();
        }
    }

    public void registerWorkflows(String... workflowJsonPaths) {
        for (String path : workflowJsonPaths) {
            metadataService.updateWorkflowDef(readFile(path));
        }
    }

    public WorkflowDef readFile(String path) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path);
        try {
            return objectMapper.readValue(inputStream, WorkflowDef.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read workflow definition from: " + path, e);
        }
    }

    private Task pollForTask(String taskName, String workerId) {
        return await().atMost(5, SECONDS)
                .until(() -> workflowExecutionService.poll(taskName, workerId), notNullValue());
    }

    public Task pollAndFailTask(
            String taskName,
            String workerId,
            String failureReason,
            Map<String, Object> outputParams,
            int waitAtEndSeconds) {
        Task polledTask = pollForTask(taskName, workerId);
        TaskResult taskResult = new TaskResult(polledTask);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskResult.setReasonForIncompletion(failureReason);
        if (outputParams != null) {
            outputParams.forEach((k, v) -> taskResult.getOutputData().put(k, v));
        }
        workflowExecutionService.updateTask(taskResult);
        sleepIfNeeded(waitAtEndSeconds);
        return polledTask;
    }

    public Task pollAndFailTask(String taskName, String workerId, String failureReason) {
        return pollAndFailTask(taskName, workerId, failureReason, null, 0);
    }

    public Task pollAndFailTask(
            String taskName,
            String workerId,
            String failureReason,
            Map<String, Object> outputParams) {
        return pollAndFailTask(taskName, workerId, failureReason, outputParams, 0);
    }

    public Task pollAndCompleteTask(
            String taskName,
            String workerId,
            Map<String, Object> outputParams,
            int waitAtEndSeconds) {
        Task polledTask = pollForTask(taskName, workerId);
        TaskResult taskResult = new TaskResult(polledTask);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        if (outputParams != null) {
            outputParams.forEach((k, v) -> taskResult.getOutputData().put(k, v));
        }
        workflowExecutionService.updateTask(taskResult);
        sleepIfNeeded(waitAtEndSeconds);
        return polledTask;
    }

    public Task pollAndCompleteTask(String taskName, String workerId) {
        return pollAndCompleteTask(taskName, workerId, null, 0);
    }

    public Task pollAndCompleteTask(
            String taskName, String workerId, Map<String, Object> outputParams) {
        return pollAndCompleteTask(taskName, workerId, outputParams, 0);
    }

    public Task pollAndCompleteLargePayloadTask(
            String taskName, String workerId, String outputPayloadPath) {
        Task polledTask = pollForTask(taskName, workerId);
        TaskResult taskResult = new TaskResult(polledTask);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setOutputData(null);
        taskResult.setExternalOutputPayloadStoragePath(outputPayloadPath);
        workflowExecutionService.updateTask(taskResult);
        return polledTask;
    }

    public Task pollAndUpdateTask(
            String taskName,
            String workerId,
            String outputPayloadPath,
            Map<String, Object> outputParams,
            int waitAtEndSeconds) {
        Task polledTask = pollForTask(taskName, workerId);
        TaskResult taskResult = new TaskResult(polledTask);
        taskResult.setStatus(TaskResult.Status.IN_PROGRESS);
        taskResult.setCallbackAfterSeconds(1);
        if (outputPayloadPath != null) {
            taskResult.setOutputData(null);
            taskResult.setExternalOutputPayloadStoragePath(outputPayloadPath);
        } else if (outputParams != null) {
            outputParams.forEach((k, v) -> taskResult.getOutputData().put(k, v));
        }
        workflowExecutionService.updateTask(taskResult);
        sleepIfNeeded(waitAtEndSeconds);
        return polledTask;
    }

    public static void verifyPolledAndAcknowledgedTask(Task polledTask) {
        verifyPolledAndAcknowledgedTask(polledTask, null);
    }

    public static void verifyPolledAndAcknowledgedTask(
            Task polledTask, Map<String, String> expectedTaskInputParams) {
        assert polledTask != null : "The task polled cannot be null";
        if (expectedTaskInputParams != null) {
            expectedTaskInputParams.forEach(
                    (k, v) -> {
                        assert polledTask.getInputData().containsKey(k);
                        assert polledTask.getInputData().get(k).equals(v);
                    });
        }
    }

    public static void verifyPolledAndAcknowledgedLargePayloadTask(Task polledTask) {
        assert polledTask != null : "The task polled cannot be null";
    }

    public static void verifyPayload(Map<String, Object> expected, Map<String, Object> payload) {
        expected.forEach(
                (k, v) -> {
                    assert payload.containsKey(k);
                    assert payload.get(k).equals(v);
                });
    }

    public Task completeTask(
            String taskId,
            String workerId,
            Map<String, Object> outputParams,
            int waitAtEndSeconds) {
        Task polledTask = workflowExecutionService.getTask(taskId);
        if (polledTask == null) {
            return null;
        }
        TaskResult taskResult = new TaskResult(polledTask);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        if (outputParams != null) {
            outputParams.forEach((k, v) -> taskResult.getOutputData().put(k, v));
        }

        Workflow wf0 =
                workflowExecutionService.getExecutionStatus(
                        polledTask.getWorkflowInstanceId(), true);
        workflowExecutionService.updateTask(taskResult);

        try {
            await().atMost(2, TimeUnit.SECONDS)
                    .until(
                            () -> {
                                Workflow wf1 =
                                        workflowExecutionService.getExecutionStatus(
                                                polledTask.getWorkflowInstanceId(), true);
                                return workflowStatusHasChanged(wf0, wf1)
                                        || nextTaskHasBeenScheduled(wf1, polledTask.getTaskId());
                            });
        } catch (Exception ignored) {
            // condition not fulfilled within 2s — continue
        }

        sleepIfNeeded(waitAtEndSeconds);
        return polledTask;
    }

    public Task completeTask(String taskId, String workerId) {
        return completeTask(taskId, workerId, null, 0);
    }

    private static boolean workflowStatusHasChanged(Workflow wf0, Workflow wf1) {
        return wf0.getStatus() != wf1.getStatus();
    }

    private static boolean nextTaskHasBeenScheduled(Workflow wf1, String completedTaskId) {
        return wf1.getTasks().stream()
                .anyMatch(
                        t ->
                                !t.getTaskId().equals(completedTaskId)
                                        && t.getStatus() == Task.Status.SCHEDULED);
    }

    private static void sleepIfNeeded(int seconds) {
        if (seconds > 0) {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
