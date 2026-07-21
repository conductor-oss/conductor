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

import org.conductoross.conductor.core.execution.tasks.TaskCancellationHandler;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
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

    /**
     * Queue used to reserve (postpone) the task's own message before a blocking invocation. May be
     * null in unit tests that exercise the invocation directly; when null the reserve is skipped.
     */
    private final QueueDAO queueDAO;

    /**
     * Creates a new AnnotatedWorkflowSystemTask.
     *
     * @param taskType The task type name
     * @param method The annotated method to invoke
     * @param bean The Spring bean instance containing the method
     * @param annotation The @WorkerTask annotation metadata
     * @param queueDAO Queue used to reserve the task's message before a blocking call
     */
    public AnnotatedWorkflowSystemTask(
            String taskType, Method method, Object bean, WorkerTask annotation, QueueDAO queueDAO) {
        super(taskType);
        this.method = method;
        this.bean = bean;
        this.annotation = annotation;
        this.queueDAO = queueDAO;
        this.parameterMapper = new AnnotatedMethodParameterMapper();
        this.resultMapper = new AnnotatedMethodResultMapper();
    }

    /** Convenience constructor without a queue (test use); the reserve step is skipped. */
    public AnnotatedWorkflowSystemTask(
            String taskType, Method method, Object bean, WorkerTask annotation) {
        this(taskType, method, bean, annotation, null);
    }

    @Override
    public boolean isAsync() {
        // Always use async polling for annotated tasks
        return true;
    }

    /**
     * Postpone applied to the task's queue message while a blocking invocation is in flight, when
     * the task carries no response timeout. Doubles as the crash-recovery redelivery delay.
     */
    static final long DEFAULT_IN_FLIGHT_POSTPONE_SECONDS = 3600;

    /**
     * start() and execute() do the same thing: reserve the task's queue message, then invoke the
     * annotated method. The annotated method runs synchronously and can block for minutes (e.g. an
     * LLM provider call); reserving first keeps the message present and invisible for the duration
     * of the call, so the sweeper's repair check does not re-push it and a mid-call redelivery
     * cannot hand the same task to a second worker (issue #1321). Long-running workers (LLM/A2A)
     * that return IN_PROGRESS + callbackAfterSeconds set their own re-invocation cadence, which the
     * engine's post-execution postpone then honors.
     */
    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        execute(workflow, task, workflowExecutor);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        reserveQueueMessage(task);
        return invokeWorker(workflow, task);
    }

    /**
     * Postpone the task's own queue message out past the blocking call, directly on the queue.
     * Postponing the message (not touching callbackAfterSeconds) keeps it present so the sweeper's
     * repair check leaves it alone, without inflating the response-timeout budget the way routing
     * through {@code updateTask(callbackAfterSeconds=responseTimeout)} would.
     */
    private void reserveQueueMessage(TaskModel task) {
        if (queueDAO == null) {
            return;
        }
        long postpone =
                task.getResponseTimeoutSeconds() > 0
                        ? task.getResponseTimeoutSeconds()
                        : DEFAULT_IN_FLIGHT_POSTPONE_SECONDS;
        queueDAO.postpone(
                QueueUtils.getQueueName(task),
                task.getTaskId(),
                task.getWorkflowPriority(),
                postpone);
    }

    private boolean invokeWorker(WorkflowModel workflow, TaskModel task) {
        TaskContext taskContext = TaskContext.set(task.toTask());
        // A plain annotated-worker return value is terminal by default. Long-running workers can
        // override this execution state through their TaskContext without leaking TaskResult into
        // their public output POJO.
        taskContext.getTaskResult().setStatus(TaskResult.Status.COMPLETED);
        taskContext.getTaskResult().setCallbackAfterSeconds(0);
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
            resultMapper.applyResult(result, task, method, taskContext.getTaskResult());

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
        String workflowId =
                workflow != null ? workflow.getWorkflowId() : task.getWorkflowInstanceId();
        log.debug("Cancelling annotated task {} for workflow {}", getTaskType(), workflowId);
        if (bean instanceof TaskCancellationHandler cancellationHandler) {
            String reason =
                    task.getReasonForIncompletion() != null
                            ? task.getReasonForIncompletion()
                            : "Annotated task canceled by workflow " + workflowId;
            try {
                cancellationHandler.cancel(task.toTask(), reason);
            } catch (Exception e) {
                // Cancellation is best effort. The Conductor task must still reach a terminal
                // state even when the downstream agent is temporarily unavailable.
                log.warn(
                        "Failed to propagate cancellation for annotated task {}: {}",
                        getTaskType(),
                        e.getMessage(),
                        e);
            }
        }
        if (task.getStatus() == null || !task.getStatus().isTerminal()) {
            task.setStatus(TaskModel.Status.CANCELED);
        }
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long maxOffset) {
        return taskModel.getCallbackAfterSeconds() > 0
                ? Optional.of(taskModel.getCallbackAfterSeconds())
                : Optional.empty();
    }
}
