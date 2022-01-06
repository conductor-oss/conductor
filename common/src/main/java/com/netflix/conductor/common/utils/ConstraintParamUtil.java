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
package com.netflix.conductor.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.EnvUtils.SystemParameters;

@SuppressWarnings("unchecked")
public class ConstraintParamUtil {

    /**
     * Validates inputParam and returns a list of errors if input is not valid.
     *
     * @param input {@link Map} of inputParameters
     * @param taskName TaskName of inputParameters
     * @param workflow WorkflowDef
     * @return {@link List} of error strings.
     */
    public static List<String> validateInputParam(
            Map<String, Object> input, String taskName, WorkflowDef workflow) {
        ArrayList<String> errorList = new ArrayList<>();

        for (Entry<String, Object> e : input.entrySet()) {
            Object value = e.getValue();
            if (value instanceof String) {
                errorList.addAll(
                        extractParamPathComponentsFromString(
                                e.getKey(), value.toString(), taskName, workflow));
            } else if (value instanceof Map) {
                // recursive call
                errorList.addAll(
                        validateInputParam((Map<String, Object>) value, taskName, workflow));
            } else if (value instanceof List) {
                errorList.addAll(
                        extractListInputParam(e.getKey(), (List<?>) value, taskName, workflow));
            } else {
                e.setValue(value);
            }
        }
        return errorList;
    }

    private static List<String> extractListInputParam(
            String key, List<?> values, String taskName, WorkflowDef workflow) {
        ArrayList<String> errorList = new ArrayList<>();
        for (Object listVal : values) {
            if (listVal instanceof String) {
                errorList.addAll(
                        extractParamPathComponentsFromString(
                                key, listVal.toString(), taskName, workflow));
            } else if (listVal instanceof Map) {
                errorList.addAll(
                        validateInputParam((Map<String, Object>) listVal, taskName, workflow));
            } else if (listVal instanceof List) {
                errorList.addAll(extractListInputParam(key, (List<?>) listVal, taskName, workflow));
            }
        }
        return errorList;
    }

    private static List<String> extractParamPathComponentsFromString(
            String key, String value, String taskName, WorkflowDef workflow) {
        ArrayList<String> errorList = new ArrayList<>();

        if (value == null) {
            String message = String.format("key: %s input parameter value: is null", key);
            errorList.add(message);
            return errorList;
        }

        String[] values = value.split("(?=(?<!\\$)\\$\\{)|(?<=\\})");

        for (String s : values) {
            if (s.startsWith("${") && s.endsWith("}")) {
                String paramPath = s.substring(2, s.length() - 1);

                if (StringUtils.containsWhitespace(paramPath)) {
                    String message =
                            String.format(
                                    "key: %s input parameter value: %s is not valid",
                                    key, paramPath);
                    errorList.add(message);
                } else if (EnvUtils.isEnvironmentVariable(paramPath)) {
                    // if it one of the predefined enums skip validation
                    boolean isPredefinedEnum = false;

                    for (SystemParameters systemParameters : SystemParameters.values()) {
                        if (systemParameters.name().equals(paramPath)) {
                            isPredefinedEnum = true;
                            break;
                        }
                    }

                    if (!isPredefinedEnum) {
                        String sysValue = EnvUtils.getSystemParametersValue(paramPath, "");
                        if (sysValue == null) {
                            String errorMessage =
                                    String.format(
                                            "environment variable: %s for given task: %s"
                                                    + " input value: %s"
                                                    + " of input parameter: %s is not valid",
                                            paramPath, taskName, key, value);
                            errorList.add(errorMessage);
                        }
                    }
                } // workflow, or task reference name
                else {
                    String[] components = paramPath.split("\\.");
                    if (!"workflow".equals(components[0])) {
                        WorkflowTask task = workflow.getTaskByRefName(components[0]);
                        if (task == null) {
                            String message =
                                    String.format(
                                            "taskReferenceName: %s for given task: %s input value: %s of input"
                                                    + " parameter: %s"
                                                    + " is not defined in workflow definition.",
                                            components[0], taskName, key, value);
                            errorList.add(message);
                        }
                    }
                }
            }
        }
        return errorList;
    }
}
