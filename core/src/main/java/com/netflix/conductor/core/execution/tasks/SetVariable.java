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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SET_VARIABLE;

@Component(TASK_TYPE_SET_VARIABLE)
public class SetVariable extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetVariable.class);

    private final ConductorProperties properties;
    private final ObjectMapper objectMapper;

    public SetVariable(ConductorProperties properties, ObjectMapper objectMapper) {
        super(TASK_TYPE_SET_VARIABLE);
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private boolean validateVariablesSize(
            WorkflowModel workflow, TaskModel task, Map<String, Object> variables) {
        String workflowId = workflow.getWorkflowId();
        long maxThreshold = properties.getMaxWorkflowVariablesPayloadSizeThreshold().toKilobytes();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            this.objectMapper.writeValue(byteArrayOutputStream, variables);
            byte[] payloadBytes = byteArrayOutputStream.toByteArray();
            long payloadSize = payloadBytes.length;

            if (payloadSize > maxThreshold * 1024) {
                String errorMsg =
                        String.format(
                                "The variables payload size: %d of workflow: %s is greater than the permissible limit: %d bytes",
                                payloadSize, workflowId, maxThreshold);
                LOGGER.error(errorMsg);
                task.setReasonForIncompletion(errorMsg);
                return false;
            }
            return true;
        } catch (IOException e) {
            LOGGER.error(
                    "Unable to validate variables payload size of workflow: {}", workflowId, e);
            throw new ApplicationException(ApplicationException.Code.INTERNAL_ERROR, e);
        }
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor provider) {
        Map<String, Object> variables = workflow.getVariables();
        Map<String, Object> input = task.getInputData();
        String taskId = task.getTaskId();
        ArrayList<String> newKeys;
        Map<String, Object> previousValues;

        if (input != null && input.size() > 0) {
            newKeys = new ArrayList<>();
            previousValues = new HashMap<>();
            input.keySet()
                    .forEach(
                            key -> {
                                if (variables.containsKey(key)) {
                                    previousValues.put(key, variables.get(key));
                                } else {
                                    newKeys.add(key);
                                }
                                variables.put(key, input.get(key));
                                LOGGER.debug(
                                        "Task: {} setting value for variable: {}", taskId, key);
                            });
            if (!validateVariablesSize(workflow, task, variables)) {
                // restore previous variables
                previousValues
                        .keySet()
                        .forEach(
                                key -> {
                                    variables.put(key, previousValues.get(key));
                                });
                newKeys.forEach(variables::remove);
                task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
                return true;
            }
        }

        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }
}
