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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.sdk.workflow.task.OutputParam;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for mapping method return values to TaskModel status and output data
 * for @WorkerTask annotated methods.
 */
@Slf4j
public class AnnotatedMethodResultMapper {

    private final ObjectMapper objectMapper;

    public AnnotatedMethodResultMapper() {
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    /** Applies a result with the default lifecycle for a synchronous annotated method. */
    public void applyResult(Object invocationResult, TaskModel task, Method method) {
        TaskResult contextResult = new TaskResult();
        contextResult.setStatus(TaskResult.Status.COMPLETED);
        applyResult(invocationResult, task, method, contextResult);
    }

    /**
     * Applies the method invocation result to the task model.
     *
     * @param invocationResult The result returned from the method invocation
     * @param task The task model to update
     * @param method The method that was invoked
     * @param contextResult Lifecycle state accumulated through the current TaskContext
     */
    public void applyResult(
            Object invocationResult, TaskModel task, Method method, TaskResult contextResult) {
        log.debug(
                "annotated task {} invocationResult {} with status {}",
                task.getTaskType(),
                invocationResult,
                task.getStatus());

        if (invocationResult == null) {
            task.setStatus(TaskModel.Status.COMPLETED);
            applyLifecycle(contextResult, task);
            return;
        }

        OutputParam opAnnotation = method.getAnnotatedReturnType().getAnnotation(OutputParam.class);

        if (opAnnotation != null) {
            // Return value should be placed in a named output parameter
            String name = opAnnotation.value();
            task.getOutputData().put(name, invocationResult);
            task.setStatus(TaskModel.Status.COMPLETED);

        } else if (invocationResult instanceof TaskResult) {
            TaskResult result = objectMapper.convertValue(invocationResult, TaskResult.class);
            task.getOutputData().putAll(result.getOutputData());
            applyLifecycle(result, task);
            return;
        } else if (invocationResult instanceof Map) {
            // Return Map becomes output data
            @SuppressWarnings("unchecked")
            Map<String, Object> resultAsMap = (Map<String, Object>) invocationResult;
            task.getOutputData().putAll(resultAsMap);
            task.setStatus(TaskModel.Status.COMPLETED);

        } else if (isPrimitive(invocationResult)) {
            // Primitives (String, Number, Boolean) go into "result" key
            task.getOutputData().put("result", invocationResult);
            task.setStatus(TaskModel.Status.COMPLETED);

        } else if (invocationResult instanceof List) {
            // Lists are converted and placed in "result" key
            List<?> resultAsList = objectMapper.convertValue(invocationResult, List.class);
            task.getOutputData().put("result", resultAsList);
            task.setStatus(TaskModel.Status.COMPLETED);

        } else {
            // POJOs are converted to Map and merged into output data
            @SuppressWarnings("unchecked")
            Map<String, Object> resultAsMap =
                    objectMapper.convertValue(invocationResult, Map.class);
            task.getOutputData().putAll(resultAsMap);
            task.setStatus(TaskModel.Status.COMPLETED);
        }

        applyLifecycle(contextResult, task);
    }

    private void applyLifecycle(TaskResult result, TaskModel task) {
        TaskResult.Status status = result.getStatus();
        if (result.getCallbackAfterSeconds() > 0
                && status != TaskResult.Status.FAILED
                && status != TaskResult.Status.FAILED_WITH_TERMINAL_ERROR
                && status != TaskResult.Status.CANCELED) {
            status = TaskResult.Status.IN_PROGRESS;
        }

        if (status != null) {
            switch (status) {
                case FAILED -> task.setStatus(TaskModel.Status.FAILED);
                case COMPLETED -> task.setStatus(TaskModel.Status.COMPLETED);
                case FAILED_WITH_TERMINAL_ERROR ->
                        task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
                case IN_PROGRESS -> task.setStatus(TaskModel.Status.IN_PROGRESS);
                case CANCELED -> task.setStatus(TaskModel.Status.CANCELED);
            }
        }
        task.setCallbackAfterSeconds(result.getCallbackAfterSeconds());
        task.setReasonForIncompletion(result.getReasonForIncompletion());
        task.setWorkerId(result.getWorkerId());
        task.setSubWorkflowId(result.getSubWorkflowId());
        task.setExternalOutputPayloadStoragePath(result.getExternalOutputPayloadStoragePath());
    }

    private boolean isPrimitive(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
