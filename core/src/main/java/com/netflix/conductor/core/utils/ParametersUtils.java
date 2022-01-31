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
package com.netflix.conductor.core.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.utils.EnvUtils;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/** Used to parse and resolve the JSONPath bindings in the workflow and task definitions. */
@Component
public class ParametersUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParametersUtils.class);

    private final ObjectMapper objectMapper;
    private final TypeReference<Map<String, Object>> map = new TypeReference<>() {};

    public ParametersUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getTaskInput(
            Map<String, Object> inputParams,
            WorkflowModel workflow,
            TaskDef taskDefinition,
            String taskId) {
        if (workflow.getWorkflowDefinition().getSchemaVersion() > 1) {
            return getTaskInputV2(inputParams, workflow, taskId, taskDefinition);
        }
        return getTaskInputV1(workflow, inputParams);
    }

    public Map<String, Object> getTaskInputV2(
            Map<String, Object> input,
            WorkflowModel workflow,
            String taskId,
            TaskDef taskDefinition) {
        Map<String, Object> inputParams;

        if (input != null) {
            inputParams = clone(input);
        } else {
            inputParams = new HashMap<>();
        }
        if (taskDefinition != null && taskDefinition.getInputTemplate() != null) {
            clone(taskDefinition.getInputTemplate()).forEach(inputParams::putIfAbsent);
        }

        Map<String, Map<String, Object>> inputMap = new HashMap<>();

        Map<String, Object> workflowParams = new HashMap<>();
        workflowParams.put("input", workflow.getInput());
        workflowParams.put("output", workflow.getOutput());
        workflowParams.put("status", workflow.getStatus());
        workflowParams.put("workflowId", workflow.getWorkflowId());
        workflowParams.put("parentWorkflowId", workflow.getParentWorkflowId());
        workflowParams.put("parentWorkflowTaskId", workflow.getParentWorkflowTaskId());
        workflowParams.put("workflowType", workflow.getWorkflowName());
        workflowParams.put("version", workflow.getWorkflowVersion());
        workflowParams.put("correlationId", workflow.getCorrelationId());
        workflowParams.put("reasonForIncompletion", workflow.getReasonForIncompletion());
        workflowParams.put("schemaVersion", workflow.getWorkflowDefinition().getSchemaVersion());
        workflowParams.put("variables", workflow.getVariables());

        inputMap.put("workflow", workflowParams);

        // For new workflow being started the list of tasks will be empty
        workflow.getTasks().stream()
                .map(TaskModel::getReferenceTaskName)
                .map(workflow::getTaskByRefName)
                .forEach(
                        task -> {
                            Map<String, Object> taskParams = new HashMap<>();
                            taskParams.put("input", task.getInputData());
                            taskParams.put("output", task.getOutputData());
                            taskParams.put("taskType", task.getTaskType());
                            if (task.getStatus() != null) {
                                taskParams.put("status", task.getStatus().toString());
                            }
                            taskParams.put("referenceTaskName", task.getReferenceTaskName());
                            taskParams.put("retryCount", task.getRetryCount());
                            taskParams.put("correlationId", task.getCorrelationId());
                            taskParams.put("pollCount", task.getPollCount());
                            taskParams.put("taskDefName", task.getTaskDefName());
                            taskParams.put("scheduledTime", task.getScheduledTime());
                            taskParams.put("startTime", task.getStartTime());
                            taskParams.put("endTime", task.getEndTime());
                            taskParams.put("workflowInstanceId", task.getWorkflowInstanceId());
                            taskParams.put("taskId", task.getTaskId());
                            taskParams.put(
                                    "reasonForIncompletion", task.getReasonForIncompletion());
                            taskParams.put("callbackAfterSeconds", task.getCallbackAfterSeconds());
                            taskParams.put("workerId", task.getWorkerId());
                            inputMap.put(
                                    task.isLoopOverTask()
                                            ? TaskUtils.removeIterationFromTaskRefName(
                                                    task.getReferenceTaskName())
                                            : task.getReferenceTaskName(),
                                    taskParams);
                        });

        Configuration option =
                Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        DocumentContext documentContext = JsonPath.parse(inputMap, option);
        Map<String, Object> replacedTaskInput = replace(inputParams, documentContext, taskId);
        if (taskDefinition != null && taskDefinition.getInputTemplate() != null) {
            // If input for a given key resolves to null, try replacing it with one from
            // inputTemplate, if it exists.
            replacedTaskInput.replaceAll(
                    (key, value) ->
                            (value == null) ? taskDefinition.getInputTemplate().get(key) : value);
        }
        return replacedTaskInput;
    }

    // deep clone using json - POJO
    private Map<String, Object> clone(Map<String, Object> inputTemplate) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(inputTemplate);
            return objectMapper.readValue(bytes, map);
        } catch (IOException e) {
            throw new RuntimeException("Unable to clone input params", e);
        }
    }

    public Map<String, Object> replace(Map<String, Object> input, Object json) {
        Object doc;
        if (json instanceof String) {
            doc = JsonPath.parse(json.toString());
        } else {
            doc = json;
        }
        Configuration option =
                Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        DocumentContext documentContext = JsonPath.parse(doc, option);
        return replace(input, documentContext, null);
    }

    public Object replace(String paramString) {
        Configuration option =
                Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        DocumentContext documentContext = JsonPath.parse(Collections.emptyMap(), option);
        return replaceVariables(paramString, documentContext, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replace(
            Map<String, Object> input, DocumentContext documentContext, String taskId) {
        Map<String, Object> result = new HashMap<>();
        for (Entry<String, Object> e : input.entrySet()) {
            Object newValue;
            Object value = e.getValue();
            if (value instanceof String) {
                newValue = replaceVariables(value.toString(), documentContext, taskId);
            } else if (value instanceof Map) {
                // recursive call
                newValue = replace((Map<String, Object>) value, documentContext, taskId);
            } else if (value instanceof List) {
                newValue = replaceList((List<?>) value, taskId, documentContext);
            } else {
                newValue = value;
            }
            result.put(e.getKey(), newValue);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object replaceList(List<?> values, String taskId, DocumentContext io) {
        List<Object> replacedList = new LinkedList<>();
        for (Object listVal : values) {
            if (listVal instanceof String) {
                Object replaced = replaceVariables(listVal.toString(), io, taskId);
                replacedList.add(replaced);
            } else if (listVal instanceof Map) {
                Object replaced = replace((Map<String, Object>) listVal, io, taskId);
                replacedList.add(replaced);
            } else if (listVal instanceof List) {
                Object replaced = replaceList((List<?>) listVal, taskId, io);
                replacedList.add(replaced);
            } else {
                replacedList.add(listVal);
            }
        }
        return replacedList;
    }

    private Object replaceVariables(
            String paramString, DocumentContext documentContext, String taskId) {
        String[] values = paramString.split("(?=(?<!\\$)\\$\\{)|(?<=})");
        Object[] convertedValues = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            convertedValues[i] = values[i];
            if (values[i].startsWith("${") && values[i].endsWith("}")) {
                String paramPath = values[i].substring(2, values[i].length() - 1);
                // if the paramPath is blank, meaning no value in between ${ and }
                // like ${}, ${  } etc, set the value to empty string
                if (StringUtils.isBlank(paramPath)) {
                    convertedValues[i] = "";
                    continue;
                }
                if (EnvUtils.isEnvironmentVariable(paramPath)) {
                    String sysValue = EnvUtils.getSystemParametersValue(paramPath, taskId);
                    if (sysValue != null) {
                        convertedValues[i] = sysValue;
                    }

                } else {
                    try {
                        convertedValues[i] = documentContext.read(paramPath);
                    } catch (Exception e) {
                        LOGGER.warn(
                                "Error reading documentContext for paramPath: {}. Exception: {}",
                                paramPath,
                                e);
                        convertedValues[i] = null;
                    }
                }
            } else if (values[i].contains("$${")) {
                convertedValues[i] = values[i].replaceAll("\\$\\$\\{", "\\${");
            }
        }

        Object retObj = convertedValues[0];
        // If the parameter String was "v1 v2 v3" then make sure to stitch it back
        if (convertedValues.length > 1) {
            for (int i = 0; i < convertedValues.length; i++) {
                Object val = convertedValues[i];
                if (val == null) {
                    val = "";
                }
                if (i == 0) {
                    retObj = val;
                } else {
                    retObj = retObj + "" + val.toString();
                }
            }
        }
        return retObj;
    }

    @Deprecated
    // Workflow schema version 1 is deprecated and new workflows should be using version 2
    private Map<String, Object> getTaskInputV1(
            WorkflowModel workflow, Map<String, Object> inputParams) {
        Map<String, Object> input = new HashMap<>();
        if (inputParams == null) {
            return input;
        }
        Map<String, Object> workflowInput = workflow.getInput();
        inputParams.forEach(
                (paramName, value) -> {
                    String paramPath = "" + value;
                    String[] paramPathComponents = paramPath.split("\\.");
                    Preconditions.checkArgument(
                            paramPathComponents.length == 3,
                            "Invalid input expression for "
                                    + paramName
                                    + ", paramPathComponents.size="
                                    + paramPathComponents.length
                                    + ", expression="
                                    + paramPath);

                    String source = paramPathComponents[0]; // workflow, or task reference name
                    String type = paramPathComponents[1]; // input/output
                    String name = paramPathComponents[2]; // name of the parameter
                    if ("workflow".equals(source)) {
                        input.put(paramName, workflowInput.get(name));
                    } else {
                        TaskModel task = workflow.getTaskByRefName(source);
                        if (task != null) {
                            if ("input".equals(type)) {
                                input.put(paramName, task.getInputData().get(name));
                            } else {
                                input.put(paramName, task.getOutputData().get(name));
                            }
                        }
                    }
                });
        return input;
    }

    public Map<String, Object> getWorkflowInput(
            WorkflowDef workflowDef, Map<String, Object> inputParams) {
        if (workflowDef != null && workflowDef.getInputTemplate() != null) {
            clone(workflowDef.getInputTemplate()).forEach(inputParams::putIfAbsent);
        }
        return inputParams;
    }
}
