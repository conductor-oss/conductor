/*
 * Copyright 2026 Conductor Authors.
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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import com.netflix.conductor.service.ExecutionService;

import lombok.extern.slf4j.Slf4j;

/**
 * Executes @WorkerTask annotated methods through the standard worker poll/update path instead of
 * the async system-task machinery. Active only when {@code conductor.annotated-workers.mode} is
 * {@code poll-worker}.
 *
 * <p>Execution model (identical to a remote SDK worker, minus the HTTP hop — this host calls the
 * same {@link ExecutionService} the task API endpoints delegate to):
 *
 * <ol>
 *   <li>{@link ExecutionService#poll} pops the queue message, moves the task to IN_PROGRESS,
 *       persists it, and ACKS (removes) the message — the queue's redelivery sweep can never
 *       redeliver a task that is being executed.
 *   <li>The annotated method is invoked via the same {@link AnnotatedWorkflowSystemTask} adapter
 *       used in system-task mode (same parameter/result mapping, TaskContext, and exception
 *       semantics).
 *   <li>{@link ExecutionService#updateTask} reports the result; the decider advances the workflow.
 * </ol>
 *
 * <p>Recovery: if the JVM dies mid-invocation, the task sits IN_PROGRESS until the decider's
 * response timeout ({@code responseTimeoutSeconds} on the task def, auto-registered by this host
 * when absent) times it out and schedules a retry per the def's retry policy.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = WorkerTaskAnnotationScanner.MODE_PROPERTY,
        havingValue = WorkerTaskAnnotationScanner.MODE_POLL_WORKER)
public class AnnotatedWorkerPollingHost implements SmartLifecycle {

    /** Defaults applied when a task type has no registered TaskDef. */
    static final long DEFAULT_RESPONSE_TIMEOUT_SECONDS = 3600;

    static final int DEFAULT_RETRY_COUNT = 3;

    /**
     * When false, the server only schedules/queues annotated task types (and still auto-registers
     * their task defs); execution is left entirely to external workers polling the task API — e.g.
     * a standalone workers process built from the same @WorkerTask beans and the conductor client,
     * which is exactly orkes-conductor's independently-scalable workers topology.
     */
    public static final String POLLING_ENABLED_PROPERTY =
            "conductor.annotated-workers.polling-host.enabled";

    private final WorkerTaskAnnotationScanner scanner;
    private final ExecutionService executionService;
    private final MetadataDAO metadataDAO;
    private final String workerId;
    private final boolean pollingEnabled;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService pollerPool;

    public AnnotatedWorkerPollingHost(
            WorkerTaskAnnotationScanner scanner,
            @Lazy ExecutionService executionService,
            @Lazy MetadataDAO metadataDAO,
            @Value("${" + POLLING_ENABLED_PROPERTY + ":true}") boolean pollingEnabled) {
        this.scanner = scanner;
        this.executionService = executionService;
        this.metadataDAO = metadataDAO;
        this.pollingEnabled = pollingEnabled;
        this.workerId = Utils.getServerId() + "-annotated-poll-worker";
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        var workers = scanner.getDiscoveredWorkers();
        if (workers.isEmpty()) {
            log.info("No @WorkerTask annotated workers discovered; polling host idle");
            return;
        }

        workers.forEach(worker -> registerTaskDefIfAbsent(worker.getTaskType()));

        if (!pollingEnabled) {
            log.info(
                    "In-process polling disabled ({}=false); {} annotated task type(s) are queued"
                            + " for external workers polling the task API",
                    POLLING_ENABLED_PROPERTY,
                    workers.size());
            return;
        }

        int totalThreads =
                workers.stream().mapToInt(w -> Math.max(1, w.getAnnotation().threadCount())).sum();
        AtomicInteger threadCounter = new AtomicInteger();
        pollerPool =
                Executors.newScheduledThreadPool(
                        totalThreads,
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName(
                                    "annotated-poll-worker-" + threadCounter.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        });

        for (AnnotatedWorkflowSystemTask worker : workers) {
            WorkerTask annotation = worker.getAnnotation();
            int threads = Math.max(1, annotation.threadCount());
            long pollingIntervalMs = Math.max(1, annotation.pollingInterval());
            for (int i = 0; i < threads; i++) {
                pollerPool.scheduleWithFixedDelay(
                        () -> pollAndExecute(worker),
                        pollingIntervalMs,
                        pollingIntervalMs,
                        TimeUnit.MILLISECONDS);
            }
            log.info(
                    "Polling annotated task type {} with {} thread(s) every {}ms",
                    worker.getTaskType(),
                    threads,
                    pollingIntervalMs);
        }
    }

    void pollAndExecute(AnnotatedWorkflowSystemTask worker) {
        if (!running.get()) {
            return;
        }
        try {
            String domain =
                    StringUtils.isBlank(worker.getAnnotation().domain())
                            ? null
                            : worker.getAnnotation().domain();
            Task polled = executionService.poll(worker.getTaskType(), workerId, domain);
            if (polled == null) {
                return;
            }
            execute(worker, polled);
        } catch (Exception e) {
            log.error("Error in poll loop for annotated task type {}", worker.getTaskType(), e);
        }
    }

    private void execute(AnnotatedWorkflowSystemTask worker, Task polled) {
        TaskModel shim = toTaskModel(polled);
        WorkflowModel workflowShim = new WorkflowModel();
        workflowShim.setWorkflowId(polled.getWorkflowInstanceId());

        // Same adapter as system-task mode: parameter mapping, TaskContext lifecycle, result
        // mapping, and NonRetryableException -> FAILED_WITH_TERMINAL_ERROR semantics.
        worker.execute(workflowShim, shim, null);

        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId(polled.getWorkflowInstanceId());
        result.setTaskId(polled.getTaskId());
        result.setWorkerId(workerId);
        result.setStatus(toTaskResultStatus(shim.getStatus()));
        result.setReasonForIncompletion(shim.getReasonForIncompletion());
        result.setCallbackAfterSeconds(shim.getCallbackAfterSeconds());
        result.getOutputData().putAll(shim.getOutputData());

        executionService.updateTask(result);
    }

    private TaskModel toTaskModel(Task polled) {
        TaskModel task = new TaskModel();
        task.setTaskId(polled.getTaskId());
        task.setTaskType(polled.getTaskType());
        task.setTaskDefName(polled.getTaskDefName());
        task.setReferenceTaskName(polled.getReferenceTaskName());
        task.setWorkflowInstanceId(polled.getWorkflowInstanceId());
        task.setWorkflowType(polled.getWorkflowType());
        task.setCorrelationId(polled.getCorrelationId());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setRetryCount(polled.getRetryCount());
        task.setPollCount(polled.getPollCount());
        task.setWorkerId(workerId);
        task.setScheduledTime(polled.getScheduledTime());
        task.setStartTime(polled.getStartTime());
        task.setCallbackAfterSeconds(0);
        task.setWorkflowPriority(polled.getWorkflowPriority());
        // The polled Task carries the substituted-secrets input; use it verbatim.
        if (polled.getInputData() != null) {
            task.setInputData(new java.util.HashMap<>(polled.getInputData()));
        }
        return task;
    }

    private TaskResult.Status toTaskResultStatus(TaskModel.Status status) {
        if (status == null) {
            return TaskResult.Status.COMPLETED;
        }
        return switch (status) {
            case FAILED -> TaskResult.Status.FAILED;
            case FAILED_WITH_TERMINAL_ERROR -> TaskResult.Status.FAILED_WITH_TERMINAL_ERROR;
            case IN_PROGRESS -> TaskResult.Status.IN_PROGRESS;
            case CANCELED -> TaskResult.Status.CANCELED;
            default -> TaskResult.Status.COMPLETED;
        };
    }

    /**
     * On the poll path the task def is the recovery mechanism ({@code responseTimeoutSeconds}
     * drives the decider's retry of a dead worker), so every annotated task type must have one.
     * Mirrors orkes-conductor's workers-side def registration.
     */
    void registerTaskDefIfAbsent(String taskType) {
        try {
            if (metadataDAO.getTaskDef(taskType) != null) {
                return;
            }
            TaskDef taskDef = new TaskDef();
            taskDef.setName(taskType);
            taskDef.setDescription("Auto-registered for @WorkerTask " + taskType);
            taskDef.setOwnerEmail("annotated-workers@conductor-oss.org");
            taskDef.setResponseTimeoutSeconds(DEFAULT_RESPONSE_TIMEOUT_SECONDS);
            taskDef.setRetryCount(DEFAULT_RETRY_COUNT);
            taskDef.setRetryLogic(TaskDef.RetryLogic.EXPONENTIAL_BACKOFF);
            taskDef.setRetryDelaySeconds(2);
            taskDef.setBackoffScaleFactor(2);
            taskDef.setTimeoutSeconds(0);
            metadataDAO.createTaskDef(taskDef);
            log.info("Auto-registered task def for annotated task type {}", taskType);
        } catch (Exception e) {
            log.warn("Unable to auto-register task def for {}", taskType, e);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (pollerPool != null) {
            pollerPool.shutdownNow();
        }
        log.info("Annotated worker polling host stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
