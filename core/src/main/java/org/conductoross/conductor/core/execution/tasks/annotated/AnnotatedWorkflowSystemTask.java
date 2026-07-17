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
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.QueueDAO;
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

    private final QueueDAO queueDAO;

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
        this(taskType, method, bean, annotation, null);
    }

    /**
     * Creates a new AnnotatedWorkflowSystemTask.
     *
     * @param taskType The task type name
     * @param method The annotated method to invoke
     * @param bean The Spring bean instance containing the method
     * @param annotation The @WorkerTask annotation metadata
     * @param queueDAO Used to extend the task queue message's visibility over blocking method
     *     invocations; may be null, in which case visibility is not managed
     */
    public AnnotatedWorkflowSystemTask(
            String taskType, Method method, Object bean, WorkerTask annotation, QueueDAO queueDAO) {
        super(taskType);
        this.method = method;
        this.bean = bean;
        this.annotation = annotation;
        this.parameterMapper = new AnnotatedMethodParameterMapper();
        this.resultMapper = new AnnotatedMethodResultMapper();
        this.queueDAO = queueDAO;
    }

    @Override
    public boolean isAsync() {
        // Always use async polling for annotated tasks
        return true;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        // The annotated method runs synchronously and can block for the full response timeout
        // (e.g. an LLM provider call), during which the task row stays SCHEDULED and nothing
        // renews the queue message's lease. If the QueueDAO's unack/redelivery window is shorter
        // than the invocation, the message is redelivered mid-execution and a second
        // system-task-worker invokes the same method again (duplicate provider call). Extend the
        // message's visibility to cover the blocking invocation before it starts.
        extendQueueVisibility(task);
        execute(workflow, task, workflowExecutor);
    }

    private void extendQueueVisibility(TaskModel task) {
        if (queueDAO == null) {
            return;
        }
        task.getTaskDefinition()
                .filter(taskDef -> taskDef.getResponseTimeoutSeconds() > 0)
                .ifPresent(
                        taskDef -> {
                            try {
                                queueDAO.setUnackTimeout(
                                        QueueUtils.getQueueName(task),
                                        task.getTaskId(),
                                        1000L * taskDef.getResponseTimeoutSeconds());
                            } catch (Exception e) {
                                log.warn(
                                        "Unable to extend queue visibility for task {}/{}",
                                        getTaskType(),
                                        task.getTaskId(),
                                        e);
                            }
                        });
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
            task.setReasonForIncompletion(getRootCauseMessage(e));
            return true;
        } finally {
            TaskContext.clear();
        }
    }

    private void handleInvocationException(TaskModel task, InvocationTargetException e) {
        Throwable cause = e.getCause();

        log.error("Error executing annotated task " + getTaskType(), cause);

        String message = getRootCauseMessage(cause);
        if (cause instanceof NonRetryableException) {
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion("Non-retryable error: " + message);
        } else {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion("Task execution failed: " + message);
        }
    }

    /**
     * Walk the exception cause chain to build a message that includes the root cause. This prevents
     * wrapper exceptions (e.g. "Failed to generate content") from hiding the actual error (e.g.
     * "404: This model is no longer available").
     */
    private String getRootCauseMessage(Throwable t) {
        if (t == null) return "unknown error";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root == t) {
            return t.getMessage();
        }
        return t.getMessage() + " — caused by: " + root.getMessage();
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
