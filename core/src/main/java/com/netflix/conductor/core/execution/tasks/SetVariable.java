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
package com.netflix.conductor.core.execution.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetVariable extends WorkflowSystemTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetVariable.class);
    public static final String NAME = "SET_VARIABLE";

    private final Configuration configuration;
    private final ObjectMapper objectMapper;

    @Inject
    public SetVariable(Configuration configuration, ObjectMapper objectMapper) {
        super(NAME);
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        LOGGER.info(NAME + " task initialized...");
    }

    private boolean validateVariablesSize(Workflow workflow, Task task, Map<String, Object> variables) {
        String workflowId = workflow.getWorkflowId();
        long maxThreshold = configuration.getMaxWorkflowVariablesPayloadSizeThresholdKB();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            this.objectMapper.writeValue(byteArrayOutputStream, variables);
            byte[] payloadBytes = byteArrayOutputStream.toByteArray();
            long payloadSize = payloadBytes.length;

            if (payloadSize > maxThreshold * 1024) {
                String errorMsg = String.format("The variables payload size: %dB of workflow: %s is greater than the permissible limit: %dKB", payloadSize, workflowId, maxThreshold);
                LOGGER.error(errorMsg);
                task.setReasonForIncompletion(errorMsg);
                return false;
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Unable to validate variables payload size of workflow: {}", workflowId, e);
            throw new ApplicationException(ApplicationException.Code.INTERNAL_ERROR, e);
        }
    }

    @Override
    public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) {
        Map<String, Object> variables = workflow.getVariables();
        Map<String, Object> input = task.getInputData();
        String taskId = task.getTaskId();
        ArrayList<String> newKeys;
        Map<String, Object> previousValues;

        if (input != null && input.size() > 0) {
            newKeys = new ArrayList<>();
            previousValues = new HashMap<>();
            input.keySet().forEach(key -> {
                if (variables.containsKey(key)) {
                    previousValues.put(key, variables.get(key));
                } else {
                    newKeys.add(key);
                }
                variables.put(key, input.get(key));
                LOGGER.debug("Task: {} setting value for variable: {}", taskId, key);
            });
            if (!validateVariablesSize(workflow, task, variables)) {
                // restore previous variables
                previousValues.keySet().forEach(key -> {
                    variables.put(key, previousValues.get(key));
                });
                newKeys.forEach(variables::remove);
                task.setStatus(Task.Status.FAILED_WITH_TERMINAL_ERROR);
                return true;
            }
        }

        task.setStatus(Task.Status.COMPLETED);
        return true;
    }
}
