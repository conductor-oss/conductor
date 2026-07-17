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
     * Marker persisted into the task's outputData while an invocation of the annotated method is in
     * flight on some worker thread. A redelivered queue message finds the marker and skips
     * re-execution ({@link #execute} returns false without invoking the method), preventing
     * duplicate invocations of blocking calls (issue #1321). Removed before the invocation's result
     * is applied, so it never appears in completed output and never blocks legitimate IN_PROGRESS +
     * callbackAfterSeconds re-executions (used by LLM/A2A workers).
     */
    public static final String IN_FLIGHT_MARKER = "_annotatedTaskInFlight";

    /**
     * Creates a new AnnotatedWorkflowSystemTask without duplicate-execution protection (no way to
     * persist the in-flight claim). Used by tests.
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
     * @param executionDAOFacade Used to persist the in-flight claim before the blocking method
     *     invocation; may be null, in which case redelivered messages re-execute as before
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

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        execute(workflow, task, workflowExecutor);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        // The annotated method runs synchronously and can block for minutes (e.g. an LLM provider
        // call) with nothing persisted until it returns, so a queue redelivery mid-invocation
        // makes a second worker execute the same method again (issue #1321). An in-flight claim
        // persisted before the invocation lets the redelivered execution detect this and back off.
        if (task.getOutputData().containsKey(IN_FLIGHT_MARKER)) {
            log.warn(
                    "Task {}/{} is already being executed (in-flight claim by {}); skipping"
                            + " redelivered execution",
                    getTaskType(),
                    task.getTaskId(),
                    task.getOutputData().get(IN_FLIGHT_MARKER));
            return false;
        }
        claimInFlight(task);

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

            // Release the claim before applying the result so it never leaks into completed
            // output, and so a legitimate IN_PROGRESS + callbackAfterSeconds result (long-running
            // workers) is re-executed normally on its callback.
            task.getOutputData().remove(IN_FLIGHT_MARKER);

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
            task.getOutputData().remove(IN_FLIGHT_MARKER);
            TaskContext.clear();
        }
    }

    /**
     * Persists an IN_PROGRESS claim (status + in-flight marker) BEFORE the blocking invocation, so
     * a redelivered queue message sees the claim and does not re-execute the method. Without the
     * persist, the task row stays SCHEDULED for the whole invocation and a second worker cannot
     * tell the difference between "never started" and "in flight". Best effort: if the claim cannot
     * be persisted, execution proceeds with the historical (unprotected) behavior.
     */
    private void claimInFlight(TaskModel task) {
        if (executionDAOFacade == null) {
            return;
        }
        try {
            task.setStatus(TaskModel.Status.IN_PROGRESS);
            task.getOutputData()
                    .put(IN_FLIGHT_MARKER, Utils.getServerId() + "/" + System.currentTimeMillis());
            executionDAOFacade.updateTask(task);
        } catch (Exception e) {
            log.warn(
                    "Unable to persist in-flight claim for task {}/{}; duplicate-execution"
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
        if (bean instanceof AnnotatedTaskCancellationHandler cancellationHandler) {
            String reason =
                    task.getReasonForIncompletion() != null
                            ? task.getReasonForIncompletion()
                            : "Annotated task canceled by workflow " + workflowId;
            try {
                cancellationHandler.cancel(getTaskType(), task.toTask(), reason);
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
