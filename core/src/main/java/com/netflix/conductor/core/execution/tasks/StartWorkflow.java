/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core.execution.tasks;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Validator;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.operation.StartWorkflowOperation;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_START_WORKFLOW;
import static com.netflix.conductor.model.TaskModel.Status.COMPLETED;
import static com.netflix.conductor.model.TaskModel.Status.FAILED;

@Component(TASK_TYPE_START_WORKFLOW)
public class StartWorkflow extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartWorkflow.class);

    private static final String WORKFLOW_ID = "workflowId";
    private static final String START_WORKFLOW_PARAMETER = "startWorkflow";

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final StartWorkflowOperation startWorkflowOperation;

    public StartWorkflow(
            ObjectMapper objectMapper,
            Validator validator,
            StartWorkflowOperation startWorkflowOperation) {
        super(TASK_TYPE_START_WORKFLOW);
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.startWorkflowOperation = startWorkflowOperation;
    }

    @Override
    public void start(
            WorkflowModel workflow, TaskModel taskModel, WorkflowExecutor workflowExecutor) {
        StartWorkflowRequest request = getRequest(taskModel);
        if (request == null) {
            return;
        }

        if (request.getTaskToDomain() == null || request.getTaskToDomain().isEmpty()) {
            Map<String, String> workflowTaskToDomainMap = workflow.getTaskToDomain();
            if (workflowTaskToDomainMap != null) {
                request.setTaskToDomain(new HashMap<>(workflowTaskToDomainMap));
            }
        }

        // set the correlation id of starter workflow, if its empty in the StartWorkflowRequest
        request.setCorrelationId(
                StringUtils.defaultIfBlank(
                        request.getCorrelationId(), workflow.getCorrelationId()));

        try {
            String workflowId = startWorkflow(request, workflow.getWorkflowId());
            taskModel.addOutput(WORKFLOW_ID, workflowId);
            taskModel.setStatus(COMPLETED);
        } catch (TransientException te) {
            LOGGER.info(
                    "A transient backend error happened when task {} in {} tried to start workflow {}.",
                    taskModel.getTaskId(),
                    workflow.toShortString(),
                    request.getName());
        } catch (Exception ae) {

            taskModel.setStatus(FAILED);
            taskModel.setReasonForIncompletion(ae.getMessage());
            LOGGER.error(
                    "Error starting workflow: {} from workflow: {}",
                    request.getName(),
                    workflow.toShortString(),
                    ae);
        }
    }

    private StartWorkflowRequest getRequest(TaskModel taskModel) {
        Map<String, Object> taskInput = taskModel.getInputData();

        StartWorkflowRequest startWorkflowRequest = null;

        if (taskInput.get(START_WORKFLOW_PARAMETER) == null) {
            taskModel.setStatus(FAILED);
            taskModel.setReasonForIncompletion(
                    "Missing '" + START_WORKFLOW_PARAMETER + "' in input data.");
        } else {
            try {
                startWorkflowRequest =
                        objectMapper.convertValue(
                                taskInput.get(START_WORKFLOW_PARAMETER),
                                StartWorkflowRequest.class);

                var violations = validator.validate(startWorkflowRequest);
                if (!violations.isEmpty()) {
                    StringBuilder reasonForIncompletion =
                            new StringBuilder(START_WORKFLOW_PARAMETER)
                                    .append(" validation failed. ");
                    for (var violation : violations) {
                        reasonForIncompletion
                                .append("'")
                                .append(violation.getPropertyPath().toString())
                                .append("' -> ")
                                .append(violation.getMessage())
                                .append(". ");
                    }
                    taskModel.setStatus(FAILED);
                    taskModel.setReasonForIncompletion(reasonForIncompletion.toString());
                    startWorkflowRequest = null;
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Error reading StartWorkflowRequest for {}", taskModel, e);
                taskModel.setStatus(FAILED);
                taskModel.setReasonForIncompletion(
                        "Error reading StartWorkflowRequest. " + e.getMessage());
            }
        }

        return startWorkflowRequest;
    }

    private String startWorkflow(StartWorkflowRequest request, String workflowId) {
        StartWorkflowInput input = new StartWorkflowInput(request);
        input.setTriggeringWorkflowId(workflowId);
        return startWorkflowOperation.execute(input);
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
