/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.core.events;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.StartAgent;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.events.EventHandler.TaskDetails;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * Action Processor subscribes to the Event Actions queue and processes the actions (e.g. start
 * workflow etc)
 */
@Component
public class SimpleActionProcessor implements ActionProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleActionProcessor.class);

    private final WorkflowExecutor workflowExecutor;
    private final ParametersUtils parametersUtils;
    private final JsonUtils jsonUtils;

    public SimpleActionProcessor(
            WorkflowExecutor workflowExecutor,
            ParametersUtils parametersUtils,
            JsonUtils jsonUtils) {
        this.workflowExecutor = workflowExecutor;
        this.parametersUtils = parametersUtils;
        this.jsonUtils = jsonUtils;
    }

    public Map<String, Object> execute(
            Action action, Object payloadObject, String event, String messageId) {

        LOGGER.debug(
                "Executing action: {} for event: {} with messageId:{}",
                action.getAction(),
                event,
                messageId);

        Object jsonObject = payloadObject;
        if (action.isExpandInlineJSON()) {
            jsonObject = jsonUtils.expand(payloadObject);
        }

        switch (action.getAction()) {
            case start_workflow:
                return startWorkflow(action, jsonObject, event, messageId);
            case complete_task:
                return completeTask(
                        action,
                        jsonObject,
                        action.getComplete_task(),
                        TaskModel.Status.COMPLETED,
                        event,
                        messageId);
            case fail_task:
                return completeTask(
                        action,
                        jsonObject,
                        action.getFail_task(),
                        TaskModel.Status.FAILED,
                        event,
                        messageId);
            case start_agent:
                return startAgent(action, jsonObject, event, messageId);
            default:
                break;
        }
        throw new UnsupportedOperationException(
                "Action not supported " + action.getAction() + " for event " + event);
    }

    private Map<String, Object> completeTask(
            Action action,
            Object payload,
            TaskDetails taskDetails,
            TaskModel.Status status,
            String event,
            String messageId) {

        Map<String, Object> input = new HashMap<>();
        input.put("workflowId", taskDetails.getWorkflowId());
        input.put("taskId", taskDetails.getTaskId());
        input.put("taskRefName", taskDetails.getTaskRefName());
        input.put("reasonForIncompletion", taskDetails.getReasonForIncompletion());
        input.putAll(taskDetails.getOutput());

        Map<String, Object> replaced = parametersUtils.replace(input, payload);
        String workflowId = (String) replaced.get("workflowId");
        String taskId = (String) replaced.get("taskId");
        String taskRefName = (String) replaced.get("taskRefName");
        String reasonForIncompletion =
                Optional.ofNullable(replaced.get("reasonForIncompletion"))
                        .map(Object::toString)
                        .orElse(null);

        TaskModel taskModel = null;
        if (StringUtils.isNotEmpty(taskId)) {
            taskModel = workflowExecutor.getTask(taskId);
        } else if (StringUtils.isNotEmpty(workflowId) && StringUtils.isNotEmpty(taskRefName)) {
            WorkflowModel workflow = workflowExecutor.getWorkflow(workflowId, true);
            if (workflow == null) {
                replaced.put("error", "No workflow found with ID: " + workflowId);
                return replaced;
            }
            taskModel = workflow.getTaskByRefName(taskRefName);
            // Task can be loopover task.In such case find corresponding task and update
            List<TaskModel> loopOverTaskList =
                    workflow.getTasks().stream()
                            .filter(
                                    t ->
                                            TaskUtils.removeIterationFromTaskRefName(
                                                            t.getReferenceTaskName())
                                                    .equals(taskRefName))
                            .collect(Collectors.toList());
            if (!loopOverTaskList.isEmpty()) {
                // Find loopover task with the highest iteration value
                taskModel =
                        loopOverTaskList.stream()
                                .sorted(Comparator.comparingInt(TaskModel::getIteration).reversed())
                                .findFirst()
                                .get();
            }
        }

        if (taskModel == null) {
            replaced.put(
                    "error",
                    "No task found with taskId: "
                            + taskId
                            + ", reference name: "
                            + taskRefName
                            + ", workflowId: "
                            + workflowId);
            return replaced;
        }

        taskModel.setStatus(status);
        taskModel.setOutputData(replaced);
        taskModel.setOutputMessage(taskDetails.getOutputMessage());
        if (!status.isSuccessful()) {
            taskModel.setReasonForIncompletion(reasonForIncompletion);
        }
        taskModel.addOutput("conductor.event.messageId", messageId);
        taskModel.addOutput("conductor.event.name", event);

        try {
            workflowExecutor.updateTask(new TaskResult(taskModel.toTask()));
            LOGGER.debug(
                    "Updated task: {} in workflow:{} with status: {} for event: {} for message:{}",
                    taskId,
                    workflowId,
                    status,
                    event,
                    messageId);
        } catch (RuntimeException e) {
            Monitors.recordEventActionError(
                    action.getAction().name(), taskModel.getTaskType(), event);
            LOGGER.error(
                    "Error updating task: {} in workflow: {} in action: {} for event: {} for message: {}",
                    taskDetails.getTaskRefName(),
                    taskDetails.getWorkflowId(),
                    action.getAction(),
                    event,
                    messageId,
                    e);
            replaced.put("error", e.getMessage());
            throw e;
        }
        return replaced;
    }

    private Map<String, Object> startWorkflow(
            Action action, Object payload, String event, String messageId) {
        StartWorkflow params = action.getStart_workflow();
        Map<String, Object> output = new HashMap<>();
        try {
            Map<String, Object> inputParams = params.getInput();
            Map<String, Object> workflowInput = parametersUtils.replace(inputParams, payload);

            Map<String, Object> paramsMap = new HashMap<>();
            // extracting taskToDomain map from the event payload
            paramsMap.put("taskToDomain", "${taskToDomain}");
            Optional.ofNullable(params.getCorrelationId())
                    .ifPresent(value -> paramsMap.put("correlationId", value));
            Map<String, Object> replaced = parametersUtils.replace(paramsMap, payload);

            // if taskToDomain is absent from event handler definition, and taskDomain Map is passed
            // as a part of payload
            // then assign payload taskToDomain map to the new workflow instance
            final Map<String, String> taskToDomain =
                    params.getTaskToDomain() != null
                            ? params.getTaskToDomain()
                            : (Map<String, String>) replaced.get("taskToDomain");

            workflowInput.put("conductor.event.messageId", messageId);
            workflowInput.put("conductor.event.name", event);

            StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
            startWorkflowInput.setName(params.getName());
            startWorkflowInput.setVersion(params.getVersion());
            startWorkflowInput.setCorrelationId(
                    Optional.ofNullable(replaced.get("correlationId"))
                            .map(Object::toString)
                            .orElse(params.getCorrelationId()));
            startWorkflowInput.setWorkflowInput(workflowInput);
            startWorkflowInput.setEvent(event);
            if (!CollectionUtils.isEmpty(taskToDomain)) {
                startWorkflowInput.setTaskToDomain(taskToDomain);
            }

            String workflowId = workflowExecutor.startWorkflow(startWorkflowInput);

            output.put("workflowId", workflowId);
            LOGGER.debug(
                    "Started workflow: {}/{}/{} for event: {} for message:{}",
                    params.getName(),
                    params.getVersion(),
                    workflowId,
                    event,
                    messageId);

        } catch (RuntimeException e) {
            Monitors.recordEventActionError(action.getAction().name(), params.getName(), event);
            LOGGER.error(
                    "Error starting workflow: {}, version: {}, for event: {} for message: {}",
                    params.getName(),
                    params.getVersion(),
                    event,
                    messageId,
                    e);
            output.put("error", e.getMessage());
            throw e;
        }
        return output;
    }

    private Map<String, Object> startAgent(
            Action action, Object payload, String event, String messageId) {
        StartAgent params = action.getStart_agent();
        Map<String, Object> output = new HashMap<>();
        try {
            Map<String, Object> paramsMap = new HashMap<>();
            paramsMap.put("name", params.getName());
            Optional.ofNullable(params.getPrompt())
                    .ifPresent(value -> paramsMap.put("prompt", value));
            Optional.ofNullable(params.getSessionId())
                    .ifPresent(value -> paramsMap.put("sessionId", value));
            Optional.ofNullable(params.getIdempotencyKey())
                    .ifPresent(value -> paramsMap.put("idempotencyKey", value));
            if (params.getContext() != null) {
                paramsMap.put("context", params.getContext());
            }
            if (params.getMedia() != null) {
                paramsMap.put("media", params.getMedia());
            }
            Map<String, Object> replaced = parametersUtils.replace(paramsMap, payload);

            AgentStartRequest request = new AgentStartRequest();
            request.setName(
                    Optional.ofNullable(replaced.get("name"))
                            .map(Object::toString)
                            .orElse(params.getName()));
            request.setVersion(params.getVersion());
            request.setPrompt(
                    Optional.ofNullable(replaced.get("prompt")).map(Object::toString).orElse(null));
            request.setSessionId(
                    Optional.ofNullable(replaced.get("sessionId"))
                            .map(Object::toString)
                            .orElse(null));
            request.setIdempotencyKey(
                    Optional.ofNullable(replaced.get("idempotencyKey"))
                            .map(Object::toString)
                            .orElse(null));
            if (replaced.get("context") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resolvedContext = (Map<String, Object>) replaced.get("context");
                request.setContext(resolvedContext);
            }
            if (replaced.get("media") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> resolvedMedia = (List<String>) replaced.get("media");
                request.setMedia(resolvedMedia);
            }

            AgentStartResponse response = workflowExecutor.startAgentExecution(request);

            output.put("executionId", response.getExecutionId());
            output.put("agentName", response.getAgentName());
            LOGGER.debug(
                    "Started agent: {}/{}/{} for event: {} for message:{}",
                    params.getName(),
                    params.getVersion(),
                    response.getExecutionId(),
                    event,
                    messageId);

        } catch (RuntimeException e) {
            Monitors.recordEventActionError(action.getAction().name(), params.getName(), event);
            LOGGER.error(
                    "Error starting agent: {}, version: {}, for event: {} for message: {}",
                    params.getName(),
                    params.getVersion(),
                    event,
                    messageId,
                    e);
            output.put("error", e.getMessage());
            throw e;
        }
        return output;
    }
}
