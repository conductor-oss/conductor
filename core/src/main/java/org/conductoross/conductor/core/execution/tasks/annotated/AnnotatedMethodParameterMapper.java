/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.core.execution.tasks.annotated;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.sdk.workflow.task.InputParam;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

// Annotations copied from java-sdk to avoid external dependency

/**
 * Utility class for mapping TaskModel parameters to method invocation parameters for @WorkerTask
 * annotated methods.
 */
public class AnnotatedMethodParameterMapper {

    private final ObjectMapper objectMapper;

    public AnnotatedMethodParameterMapper() {
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Maps a TaskModel to the parameters required by the annotated method.
     *
     * @param task The task model to map from
     * @param method The method to map parameters for
     * @return Array of parameter values ready for method invocation
     */
    public Object[] mapParameters(TaskModel task, Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Parameter[] parameters = method.getParameters();

        // Special case: single TaskModel parameter
        if (parameterTypes.length == 1 && parameterTypes[0].equals(TaskModel.class)) {
            return new Object[] {task};
        }

        // Special case: single Map parameter (task input data)
        if (parameterTypes.length == 1 && parameterTypes[0].equals(Map.class)) {
            return new Object[] {task.getInputData()};
        }

        // General case: may have @InputParam, @WorkflowInstanceIdInputParam, or plain
        // types
        return mapAnnotatedParameters(task, parameterTypes, parameters, method);
    }

    private Object[] mapAnnotatedParameters(
            TaskModel task, Class<?>[] parameterTypes, Parameter[] parameters, Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] values = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Annotation[] paramAnnotation = parameterAnnotations[i];

            // WorkflowInstanceIdInputParam not available in SDK v3.x - uncomment when
            // upgrading
            // if (containsWorkflowInstanceIdInputParamAnnotation(paramAnnotation)) {
            // validateParameterForWorkflowInstanceId(parameters[i]);
            // values[i] = task.getWorkflowInstanceId();
            // } else
            if (paramAnnotation.length > 0) {
                Type type = parameters[i].getParameterizedType();
                Class<?> parameterType = parameterTypes[i];
                values[i] = getInputValue(task, parameterType, type, paramAnnotation);
            } else {
                // No annotation - convert entire input data to parameter type
                values[i] = objectMapper.convertValue(task.getInputData(), parameterTypes[i]);
            }
        }

        return values;
    }

    // WorkflowInstanceIdInputParam not available in SDK v3.x - uncomment when
    // upgrading
    // private boolean containsWorkflowInstanceIdInputParamAnnotation(Annotation[]
    // annotations) {
    // return Arrays.stream(annotations)
    // .map(Annotation::annotationType)
    // .anyMatch(WorkflowInstanceIdInputParam.class::equals);
    // }
    //
    // private void validateParameterForWorkflow InstanceId(Parameter parameter) {
    // if (!parameter.getType().equals(String.class)) {
    // throw new IllegalArgumentException(
    // "Parameter "
    // + parameter
    // + " is annotated with "
    // + WorkflowInstanceIdInputParam.class.getSimpleName()
    // + " but is not of type "
    // + String.class.getSimpleName()
    // + ".");
    // }
    // }

    private Object getInputValue(
            TaskModel task, Class<?> parameterType, Type type, Annotation[] paramAnnotation) {
        InputParam ip = findInputParamAnnotation(paramAnnotation);

        if (ip == null) {
            return objectMapper.convertValue(task.getInputData(), parameterType);
        }

        final String name = ip.value();
        final Object value = task.getInputData().get(name);
        if (value == null) {
            return null;
        }

        if (List.class.isAssignableFrom(parameterType)) {
            return convertToParameterizedList(value, type);
        } else {
            return objectMapper.convertValue(value, parameterType);
        }
    }

    private Object convertToParameterizedList(Object value, Type type) {
        List<?> list = objectMapper.convertValue(value, List.class);
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Class<?> typeOfParameter = (Class<?>) parameterizedType.getActualTypeArguments()[0];
            List<Object> parameterizedList = new ArrayList<>();
            for (Object item : list) {
                parameterizedList.add(objectMapper.convertValue(item, typeOfParameter));
            }
            return parameterizedList;
        } else {
            return list;
        }
    }

    private static InputParam findInputParamAnnotation(Annotation[] paramAnnotation) {
        return (InputParam)
                Arrays.stream(paramAnnotation)
                        .filter(ann -> ann.annotationType().equals(InputParam.class))
                        .findFirst()
                        .orElse(null);
    }
}
