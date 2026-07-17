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
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.Utils;
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

    private final ExecutionDAOFacade executionDAOFacade;

    /**
     * Postpone applied to a queue message redelivered while an invocation is in flight, when the
     * task def carries no response timeout. Keeps redelivered messages quiet until well past any
     * realistic blocking call, and doubles as the crash-recovery retry delay.
     */
    static final long DEFAULT_IN_FLIGHT_POSTPONE_SECONDS = 3600;

    /**
     * Creates a new AnnotatedWorkflowSystemTask without duplicate-execution protection (no way to
     * persist the IN_PROGRESS transition). Used by tests.
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
     * @param executionDAOFacade Used to persist the task's IN_PROGRESS transition before the
     *     blocking method invocation; may be null, in which case redelivered messages re-execute as
     *     before
     */
    public AnnotatedWorkflowSystemTask(
            String taskType,
            Method method,
            Object bean,
            WorkerTask annotation,
            ExecutionDAOFacade executionDAOFacade) {
        super(taskType);
        this.method = method;
        this.bean = bean;
        this.annotation = annotation;
        this.parameterMapper = new AnnotatedMethodParameterMapper();
        this.resultMapper = new AnnotatedMethodResultMapper();
        this.executionDAOFacade = executionDAOFacade;
    }

    @Override
    public boolean isAsync() {
        // Always use async polling for annotated tasks
        return true;
    }

    /** SCHEDULED means the task needs to be started: transition to IN_PROGRESS and invoke. */
    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        invokeWorker(workflow, task);
    }

    /**
     * Called by the engine when the task is already IN_PROGRESS. Per the system-task status
     * contract: IN_PROGRESS means an invocation is in flight — don't do anything. The one exception
     * is a worker-requested re-execution: long-running workers (LLM/A2A) return IN_PROGRESS with
     * {@code callbackAfterSeconds > 0} and expect to be re-invoked when the callback fires.
     */
    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        if (task.getStatus() == TaskModel.Status.IN_PROGRESS
                && task.getCallbackAfterSeconds() == 0) {
            log.debug(
                    "Task {}/{} is IN_PROGRESS with no callback due; skipping redelivered"
                            + " execution",
                    getTaskType(),
                    task.getTaskId());
            return false;
        }
        return invokeWorker(workflow, task);
    }

    private boolean invokeWorker(WorkflowModel workflow, TaskModel task) {
        // The annotated method runs synchronously and can block for minutes (e.g. an LLM provider
        // call). Persist the IN_PROGRESS transition BEFORE invoking so a redelivered queue
        // message sees the real status and backs off (issue #1321) — otherwise the task row stays
        // SCHEDULED for the whole invocation and a second worker re-executes the method.
        markInProgress(task);

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

    /**
     * Persists the standard SCHEDULED → IN_PROGRESS transition before the blocking invocation
     * (resetting callbackAfterSeconds so an in-flight redelivery is distinguishable from a
     * worker-requested callback). Best effort: if the transition cannot be persisted, execution
     * proceeds with the historical (unprotected) behavior.
     */
    private void markInProgress(TaskModel task) {
        if (executionDAOFacade == null) {
            return;
        }
        try {
            task.setStatus(TaskModel.Status.IN_PROGRESS);
            task.setCallbackAfterSeconds(0);
            task.setWorkerId(Utils.getServerId());
            executionDAOFacade.updateTask(task);
        } catch (Exception e) {
            log.warn(
                    "Unable to persist IN_PROGRESS transition for task {}/{}; duplicate-execution"
                            + " protection unavailable for this invocation",
                    getTaskType(),
                    task.getTaskId(),
                    e);
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
        if (taskModel.getCallbackAfterSeconds() > 0) {
            return Optional.of(taskModel.getCallbackAfterSeconds());
        }
        if (taskModel.getStatus() == TaskModel.Status.IN_PROGRESS) {
            // A redelivered message for an in-flight invocation: postpone it far enough to cover
            // the blocking call (short default postpones would also make the persisted
            // callbackAfterSeconds look like a worker-requested callback on the next redelivery,
            // re-invoking the method mid-flight). Doubles as the crash-recovery retry delay.
            long postpone =
                    taskModel.getResponseTimeoutSeconds() > 0
                            ? taskModel.getResponseTimeoutSeconds()
                            : DEFAULT_IN_FLIGHT_POSTPONE_SECONDS;
            return Optional.of(postpone);
        }
        return Optional.empty();
    }
}
