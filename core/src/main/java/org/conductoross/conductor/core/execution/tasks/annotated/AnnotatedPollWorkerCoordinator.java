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

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.service.ExecutionService;

import lombok.extern.slf4j.Slf4j;

/**
 * Executes {@code @WorkerTask} annotated methods through the standard worker-task contract when
 * {@code conductor.annotated-workers.mode=poll-worker}.
 *
 * <p>Instead of registering annotated methods as async system tasks (whose blocking {@code start()}
 * holds an unacked queue message and is re-executed when the unack window elapses — see issue
 * #1321), each method is polled and executed exactly like a remote custom worker:
 *
 * <ul>
 *   <li>{@link ExecutionService#poll} claims the task: IN_PROGRESS + workerId are persisted and the
 *       queue message is ACKed <b>before</b> the method runs, so redelivery of an in-flight task is
 *       structurally impossible.
 *   <li>Liveness is governed by the task def's {@code responseTimeoutSeconds}; workers with {@link
 *       com.netflix.conductor.sdk.workflow.task.WorkerTask#leaseExtendEnabled()} heartbeat via
 *       lease extension while the method runs.
 *   <li>Results are reported through {@link WorkflowExecutor#updateTask}, which drops late updates
 *       to already-terminal tasks (issue #1322).
 *   <li>A method returning IN_PROGRESS with {@code callbackAfterSeconds} (the long-running LLM/A2A
 *       pattern) is re-queued and re-claimed on the next turn — every turn gets the full claim +
 *       lease protection.
 * </ul>
 */
@Slf4j
@Component
public class AnnotatedPollWorkerCoordinator implements SmartLifecycle {

    public static final String MODE_POLL_WORKER = "poll-worker";

    private final WorkerTaskAnnotationScanner scanner;
    private final ExecutionService executionService;
    private final WorkflowExecutor workflowExecutor;
    private final ExecutionDAOFacade executionDAOFacade;
    private final MetadataDAO metadataDAO;
    private final boolean enabled;
    private final long defaultResponseTimeoutSeconds;
    private final String workerId;

    private final AnnotatedMethodParameterMapper parameterMapper =
            new AnnotatedMethodParameterMapper();
    private final AnnotatedMethodResultMapper resultMapper = new AnnotatedMethodResultMapper();

    private volatile boolean running = false;
    private ExecutorService pollerPool;
    private ScheduledExecutorService leaseExtender;

    public AnnotatedPollWorkerCoordinator(
            WorkerTaskAnnotationScanner scanner,
            ExecutionService executionService,
            WorkflowExecutor workflowExecutor,
            ExecutionDAOFacade executionDAOFacade,
            MetadataDAO metadataDAO,
            @Value("${conductor.annotated-workers.mode:system-task}") String mode,
            @Value("${conductor.annotated-workers.response-timeout-seconds:600}")
                    long defaultResponseTimeoutSeconds) {
        this.scanner = scanner;
        this.executionService = executionService;
        this.workflowExecutor = workflowExecutor;
        this.executionDAOFacade = executionDAOFacade;
        this.metadataDAO = metadataDAO;
        this.enabled = MODE_POLL_WORKER.equals(mode);
        this.defaultResponseTimeoutSeconds = defaultResponseTimeoutSeconds;
        this.workerId = resolveWorkerId();
    }

    @Override
    public void start() {
        running = true;
        if (!enabled) {
            return;
        }
        List<WorkerTaskAnnotationScanner.AnnotatedWorker> workers = scanner.getPollWorkers();
        if (workers.isEmpty()) {
            log.info("Annotated poll-worker mode enabled but no @WorkerTask methods discovered");
            return;
        }
        AtomicInteger threadCounter = new AtomicInteger();
        pollerPool =
                Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName(
                                    "annotated-poll-worker-" + threadCounter.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        });
        if (workers.stream().anyMatch(worker -> worker.annotation().leaseExtendEnabled())) {
            leaseExtender =
                    Executors.newSingleThreadScheduledExecutor(
                            runnable -> {
                                Thread thread = new Thread(runnable);
                                thread.setName("annotated-poll-worker-lease-extender");
                                thread.setDaemon(true);
                                return thread;
                            });
        }
        for (WorkerTaskAnnotationScanner.AnnotatedWorker worker : workers) {
            registerTaskDefIfAbsent(worker.taskType());
            int pollerCount = Math.max(1, worker.annotation().pollerCount());
            for (int i = 0; i < pollerCount; i++) {
                pollerPool.submit(() -> pollLoop(worker));
            }
            log.info(
                    "Started {} poller(s) for annotated worker task {} (worker contract mode)",
                    pollerCount,
                    worker.taskType());
        }
    }

    @Override
    public void stop() {
        running = false;
        if (pollerPool != null) {
            pollerPool.shutdownNow();
        }
        if (leaseExtender != null) {
            leaseExtender.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Registers a task def so the USER_DEFINED task mapper can schedule the type and so the
     * response-timeout lease is explicit. No-op when a def already exists (deployments can tune it
     * like any worker task def).
     */
    private void registerTaskDefIfAbsent(String taskType) {
        try {
            if (metadataDAO.getTaskDef(taskType) != null) {
                return;
            }
            TaskDef taskDef = new TaskDef(taskType);
            taskDef.setDescription("Auto-registered for @WorkerTask " + taskType);
            taskDef.setRetryCount(0);
            taskDef.setTimeoutSeconds(0);
            taskDef.setResponseTimeoutSeconds(defaultResponseTimeoutSeconds);
            taskDef.setOwnerEmail("annotated-workers@conductoross.org");
            metadataDAO.createTaskDef(taskDef);
            log.info(
                    "Auto-registered task def for annotated worker task {} (responseTimeout={}s)",
                    taskType,
                    defaultResponseTimeoutSeconds);
        } catch (Exception e) {
            // Another node may have won the registration race; the def existing is all we need.
            log.warn("Could not auto-register task def for {}: {}", taskType, e.getMessage());
        }
    }

    private void pollLoop(WorkerTaskAnnotationScanner.AnnotatedWorker worker) {
        String domain =
                worker.annotation().domain().isBlank() ? null : worker.annotation().domain();
        int pollTimeout = Math.max(100, worker.annotation().pollTimeout());
        int pollingInterval = Math.max(10, worker.annotation().pollingInterval());
        while (running) {
            try {
                List<Task> tasks =
                        executionService.poll(worker.taskType(), workerId, domain, 1, pollTimeout);
                if (tasks == null || tasks.isEmpty()) {
                    TimeUnit.MILLISECONDS.sleep(pollingInterval);
                    continue;
                }
                for (Task task : tasks) {
                    executeClaimedTask(worker, task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("Error in annotated poll-worker loop for {}", worker.taskType(), e);
                try {
                    TimeUnit.MILLISECONDS.sleep(pollingInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Executes a task that {@link ExecutionService#poll} has already claimed (IN_PROGRESS
     * persisted, queue message ACKed) and reports the result through the worker update contract.
     */
    private void executeClaimedTask(WorkerTaskAnnotationScanner.AnnotatedWorker worker, Task task) {
        TaskModel taskModel = executionDAOFacade.getTaskModel(task.getTaskId());
        if (taskModel == null || taskModel.getStatus().isTerminal()) {
            return;
        }
        TaskContext taskContext = TaskContext.set(taskModel.toTask());
        taskContext.getTaskResult().setStatus(TaskResult.Status.COMPLETED);
        taskContext.getTaskResult().setCallbackAfterSeconds(0);
        ScheduledFuture<?> leaseExtension = maybeScheduleLeaseExtension(worker, taskModel);
        try {
            Object[] parameters = parameterMapper.mapParameters(taskModel, worker.method());
            Object result = worker.method().invoke(worker.bean(), parameters);
            resultMapper.applyResult(
                    result, taskModel, worker.method(), taskContext.getTaskResult());
        } catch (InvocationTargetException e) {
            applyInvocationFailure(taskModel, e.getCause());
        } catch (Exception e) {
            log.error("Error executing annotated worker task {}", worker.taskType(), e);
            taskModel.setStatus(TaskModel.Status.FAILED);
            taskModel.setReasonForIncompletion(rootCauseMessage(e));
        } finally {
            if (leaseExtension != null) {
                leaseExtension.cancel(false);
            }
            TaskContext.clear();
        }
        reportResult(taskModel);
    }

    private ScheduledFuture<?> maybeScheduleLeaseExtension(
            WorkerTaskAnnotationScanner.AnnotatedWorker worker, TaskModel taskModel) {
        if (!worker.annotation().leaseExtendEnabled()) {
            return null;
        }
        // A third of the lease keeps the task alive with margin for two missed heartbeats.
        long interval = Math.max(1, defaultResponseTimeoutSeconds / 3);
        String taskId = taskModel.getTaskId();
        String workflowInstanceId = taskModel.getWorkflowInstanceId();
        return leaseExtender.scheduleAtFixedRate(
                () -> {
                    try {
                        TaskResult lease = new TaskResult();
                        lease.setTaskId(taskId);
                        lease.setWorkflowInstanceId(workflowInstanceId);
                        lease.setWorkerId(workerId);
                        lease.setExtendLease(true);
                        workflowExecutor.updateTask(lease);
                        log.debug("Extended lease for in-flight annotated task {}", taskId);
                    } catch (Exception e) {
                        log.warn("Lease extension failed for task {}: {}", taskId, e.getMessage());
                    }
                },
                interval,
                interval,
                TimeUnit.SECONDS);
    }

    private void applyInvocationFailure(TaskModel taskModel, Throwable cause) {
        log.error("Annotated worker task {} failed", taskModel.getTaskType(), cause);
        if (cause instanceof NonRetryableException) {
            taskModel.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            taskModel.setReasonForIncompletion("Non-retryable error: " + rootCauseMessage(cause));
        } else {
            taskModel.setStatus(TaskModel.Status.FAILED);
            taskModel.setReasonForIncompletion("Task execution failed: " + rootCauseMessage(cause));
        }
    }

    /**
     * Reports the outcome through the same update contract a remote worker uses. Retried a few
     * times: dropping the result of an already-paid provider call over a transient store/queue
     * error would force a full re-execution after the response timeout.
     */
    private void reportResult(TaskModel taskModel) {
        TaskResult taskResult = new TaskResult(taskModel.toTask());
        taskResult.setWorkerId(workerId);
        taskResult.setStatus(toResultStatus(taskModel.getStatus()));
        for (int attempt = 1; ; attempt++) {
            try {
                workflowExecutor.updateTask(taskResult);
                return;
            } catch (Exception e) {
                if (attempt >= 3) {
                    // The claim stands (task is IN_PROGRESS); the response-timeout lease governs
                    // recovery, exactly as it would for a remote worker whose update failed.
                    log.error(
                            "Failed to report result for annotated worker task {}/{}",
                            taskModel.getTaskType(),
                            taskModel.getTaskId(),
                            e);
                    return;
                }
                log.warn(
                        "Retrying result update for task {} (attempt {}): {}",
                        taskModel.getTaskId(),
                        attempt,
                        e.getMessage());
                try {
                    TimeUnit.MILLISECONDS.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private TaskResult.Status toResultStatus(TaskModel.Status status) {
        return switch (status) {
            case COMPLETED -> TaskResult.Status.COMPLETED;
            case FAILED_WITH_TERMINAL_ERROR -> TaskResult.Status.FAILED_WITH_TERMINAL_ERROR;
            case IN_PROGRESS, SCHEDULED -> TaskResult.Status.IN_PROGRESS;
            case CANCELED -> TaskResult.Status.CANCELED;
            default -> TaskResult.Status.FAILED;
        };
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMessage = root.getMessage() != null ? root.getMessage() : root.toString();
        if (root != throwable && throwable.getMessage() != null) {
            return throwable.getMessage() + " (root cause: " + rootMessage + ")";
        }
        return rootMessage;
    }

    private static String resolveWorkerId() {
        try {
            return "annotated-poll-worker@" + InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "annotated-poll-worker";
        }
    }
}
