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
package org.conductoross.conductor.ai.a2a;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.model.A2ACancelRequest;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancels a running agent — a remote A2A task ({@code tasks/cancel}, agentType {@code "a2a"}) or a
 * conductor agent execution ({@code terminateWorkflow}, agentType {@code "conductor"}).
 *
 * <p>A plain {@link WorkflowSystemTask} rather than an {@code @WorkerTask}: it completes entirely
 * within {@link #start}, so it's synchronous ({@link #isAsync} defaults to {@code false}) and gets
 * the {@link WorkflowExecutor} handed to it by the engine instead of needing it injected as a
 * Spring dependency.
 */
@Slf4j
@Component(CancelAgentTask.TASK_TYPE)
@Conditional(AIIntegrationEnabledCondition.class)
public class CancelAgentTask extends WorkflowSystemTask {

    public static final String TASK_TYPE = "CANCEL_AGENT";

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final A2AService a2aService;

    public CancelAgentTask(A2AService a2aService) {
        super(TASK_TYPE);
        this.a2aService = a2aService;
        log.debug("{} initialized", TASK_TYPE);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        A2ACancelRequest request = parseRequest(task);
        if (A2AService.isConductorAgentType(request.getAgentType())) {
            cancelConductorAgent(task, executor, request);
        } else {
            cancelRemoteA2aAgent(task, request);
        }
    }

    private void cancelConductorAgent(
            TaskModel task, WorkflowExecutor executor, A2ACancelRequest request) {
        String executionId = StringUtils.trimToNull(request.getExecutionId());
        if (executionId == null) {
            fail(task, "CANCEL_AGENT (conductor) requires 'executionId'", true);
            return;
        }
        String reason =
                StringUtils.firstNonBlank(request.getReason(), "Cancelled by CANCEL_AGENT task");
        try {
            log.debug("Terminating conductor agent execution {}: {}", executionId, reason);
            executor.terminateWorkflow(executionId, reason);
            task.addOutput("executionId", executionId);
            task.addOutput("canceled", true);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            fail(
                    task,
                    "Failed to terminate conductor agent execution "
                            + executionId
                            + ": "
                            + e.getMessage(),
                    false);
        }
    }

    private void cancelRemoteA2aAgent(TaskModel task, A2ACancelRequest request) {
        if (!A2AService.isA2aAgentType(request.getAgentType())) {
            fail(
                    task,
                    "Unsupported agentType '"
                            + request.getAgentType()
                            + "' (supported: 'a2a', 'conductor')",
                    true);
            return;
        }
        if (StringUtils.isBlank(request.getAgentUrl())) {
            fail(task, "CANCEL_AGENT requires 'agentUrl'", true);
            return;
        }
        if (StringUtils.isBlank(request.getTaskId())) {
            fail(task, "CANCEL_AGENT requires 'taskId'", true);
            return;
        }
        try {
            log.debug("Canceling A2A task {} on {}", request.getTaskId(), request.getAgentUrl());
            Object a2aTask =
                    a2aService.cancelTask(
                            request.getAgentUrl(), request.getTaskId(), request.getHeaders());
            task.addOutput("task", objectMapper.convertValue(a2aTask, Map.class));
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            fail(task, "Failed to cancel A2A task: " + e.getMessage(), false);
        }
    }

    private A2ACancelRequest parseRequest(TaskModel task) {
        return objectMapper.convertValue(task.getInputData(), A2ACancelRequest.class);
    }

    private void fail(TaskModel task, String reason, boolean nonRetryable) {
        task.setStatus(
                nonRetryable
                        ? TaskModel.Status.FAILED_WITH_TERMINAL_ERROR
                        : TaskModel.Status.FAILED);
        task.setReasonForIncompletion(reason);
    }
}
