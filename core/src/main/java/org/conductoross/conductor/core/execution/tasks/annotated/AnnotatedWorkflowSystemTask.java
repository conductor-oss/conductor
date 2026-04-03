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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that wraps a @WorkerTask annotated method as a WorkflowSystemTask. This enables
 * annotation-based system task development while maintaining compatibility with the existing
 * SystemTaskWorkerCoordinator infrastructure.
 */
@Slf4j
public class AnnotatedWorkflowSystemTask extends WorkflowSystemTask {

    @Getter private final Method method;

    @Getter private final Object bean;

    @Getter private final WorkerTask annotation;

    private final AnnotatedMethodParameterMapper parameterMapper;

    private final AnnotatedMethodResultMapper resultMapper;

    /**
     * Creates a new AnnotatedWorkflowSystemTask.
     *
     * @param taskType The task type name
     * @param method The annotated method to invoke
     * @param bean The Spring bean instance containing the method
     * @param annotation The @WorkerTask annotation metadata
     */
    public AnnotatedWorkflowSystemTask(
            String taskType, Method method, Object bean, WorkerTask annotation) {
        super(taskType);
        this.method = method;
        this.bean = bean;
        this.annotation = annotation;
        this.parameterMapper = new AnnotatedMethodParameterMapper();
        this.resultMapper = new AnnotatedMethodResultMapper();
    }

    @Override
    public boolean isAsync() {
        // Always use async polling for annotated tasks
        return true;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        execute(workflow, task, workflowExecutor);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        TaskContext.set(task.toTask());
        try {
            log.debug(
                    "Executing annotated task {} for workflow {}",
                    getTaskType(),
                    workflow.getWorkflowId());

            // Map task parameters to method parameters
            Object[] parameters = parameterMapper.mapParameters(task, method);

            // Invoke the annotated method
            Object result = method.invoke(bean, parameters);

            // Apply the result to the task
            resultMapper.applyResult(result, task, method);

            log.debug(
                    "Completed annotated task {} with status {}", getTaskType(), task.getStatus());

            return true;

        } catch (InvocationTargetException e) {
            handleInvocationException(task, e);
            return true;
        } catch (Exception e) {
            log.error("error executing annotated task " + getTaskType(), e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            return true;
        } finally {
            TaskContext.clear();
        }
    }

    private void handleInvocationException(TaskModel task, InvocationTargetException e) {
        Throwable cause = e.getCause();

        log.error("Error executing annotated task " + getTaskType(), cause);

        if (cause instanceof NonRetryableException) {
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion("Non-retryable error: " + cause.getMessage());
        } else {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion("Task execution failed: " + cause.getMessage());
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        // Default implementation - annotated tasks typically don't need custom cancel
        // logic
        log.debug(
                "Cancelling annotated task {} for workflow {}",
                getTaskType(),
                workflow.getWorkflowId());
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long maxOffset) {
        return taskModel.getCallbackAfterSeconds() > 0
                ? Optional.of(taskModel.getCallbackAfterSeconds())
                : Optional.empty();
    }
}
