/*
 * Copyright 2020 Netflix, Inc.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.events.EventHandler.TaskDetails;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.dal.ModelMapper;
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
    private final ModelMapper modelMapper;
    private final ParametersUtils parametersUtils;
    private final JsonUtils jsonUtils;

    public SimpleActionProcessor(
            WorkflowExecutor workflowExecutor,
            ModelMapper modelMapper,
            ParametersUtils parametersUtils,
            JsonUtils jsonUtils) {
        this.workflowExecutor = workflowExecutor;
        this.modelMapper = modelMapper;
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
        input.putAll(taskDetails.getOutput());

        Map<String, Object> replaced = parametersUtils.replace(input, payload);
        String workflowId = (String) replaced.get("workflowId");
        String taskId = (String) replaced.get("taskId");
        String taskRefName = (String) replaced.get("taskRefName");

        TaskModel task = null;
        if (StringUtils.isNotEmpty(taskId)) {
            task = workflowExecutor.getTask(taskId);
        } else if (StringUtils.isNotEmpty(workflowId) && StringUtils.isNotEmpty(taskRefName)) {
            WorkflowModel workflow = workflowExecutor.getWorkflow(workflowId, true);
            if (workflow == null) {
                replaced.put("error", "No workflow found with ID: " + workflowId);
                return replaced;
            }
            task = workflow.getTaskByRefName(taskRefName);
        }

        if (task == null) {
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

        task.setStatus(status);
        task.setOutputData(replaced);
        task.setOutputMessage(taskDetails.getOutputMessage());
        task.getOutputData().put("conductor.event.messageId", messageId);
        task.getOutputData().put("conductor.event.name", event);

        try {
            workflowExecutor.updateTask(new TaskResult(modelMapper.getTask(task)));
            LOGGER.debug(
                    "Updated task: {} in workflow:{} with status: {} for event: {} for message:{}",
                    taskId,
                    workflowId,
                    status,
                    event,
                    messageId);
        } catch (RuntimeException e) {
            Monitors.recordEventActionError(action.getAction().name(), task.getTaskType(), event);
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
            Optional.ofNullable(params.getCorrelationId())
                    .ifPresent(value -> paramsMap.put("correlationId", value));
            Map<String, Object> replaced = parametersUtils.replace(paramsMap, payload);

            workflowInput.put("conductor.event.messageId", messageId);
            workflowInput.put("conductor.event.name", event);

            String workflowId =
                    workflowExecutor.startWorkflow(
                            params.getName(),
                            params.getVersion(),
                            Optional.ofNullable(replaced.get("correlationId"))
                                    .map(Object::toString)
                                    .orElse(params.getCorrelationId()),
                            workflowInput,
                            null,
                            event,
                            params.getTaskToDomain());
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
}
