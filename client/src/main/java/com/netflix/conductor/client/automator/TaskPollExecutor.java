/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.client.automator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.conductor.client.config.PropertyFactory;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.telemetry.MetricsContainer;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.discovery.EurekaClient;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.patterns.ThreadPoolMonitor;

/**
 * Manages the threadpool used by the workers for execution and server communication (polling and
 * task update).
 */
class TaskPollExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskPollExecutor.class);

    private static final Registry REGISTRY = Spectator.globalRegistry();

    private final EurekaClient eurekaClient;
    private final TaskClient taskClient;
    private final int updateRetryCount;
    private final ExecutorService executorService;
    private final Map<String, PollingSemaphore> pollingSemaphoreMap;
    private final Map<String /*taskType*/, String /*domain*/> taskToDomain;

    private static final String DOMAIN = "domain";
    private static final String OVERRIDE_DISCOVERY = "pollOutOfDiscovery";
    private static final String ALL_WORKERS = "all";

    private static final int LEASE_EXTEND_RETRY_COUNT = 3;
    private static final double LEASE_EXTEND_DURATION_FACTOR = 0.8;
    private ScheduledExecutorService leaseExtendExecutorService;
    Map<String /* ID of the task*/, ScheduledFuture<?>> leaseExtendMap = new HashMap<>();

    TaskPollExecutor(
            EurekaClient eurekaClient,
            TaskClient taskClient,
            int updateRetryCount,
            Map<String, String> taskToDomain,
            String workerNamePrefix,
            Map<String, Integer> taskThreadCount) {
        this.eurekaClient = eurekaClient;
        this.taskClient = taskClient;
        this.updateRetryCount = updateRetryCount;
        this.taskToDomain = taskToDomain;

        this.pollingSemaphoreMap = new HashMap<>();
        int totalThreadCount = 0;
        for (Map.Entry<String, Integer> entry : taskThreadCount.entrySet()) {
            String taskType = entry.getKey();
            int count = entry.getValue();
            totalThreadCount += count;
            pollingSemaphoreMap.put(taskType, new PollingSemaphore(count));
        }

        LOGGER.info("Initialized the TaskPollExecutor with {} threads", totalThreadCount);
        this.executorService =
                Executors.newFixedThreadPool(
                        totalThreadCount,
                        new BasicThreadFactory.Builder()
                                .namingPattern(workerNamePrefix)
                                .uncaughtExceptionHandler(uncaughtExceptionHandler)
                                .build());
        ThreadPoolMonitor.attach(REGISTRY, (ThreadPoolExecutor) executorService, workerNamePrefix);

        LOGGER.info("Initialized the task lease extend executor");
        leaseExtendExecutorService =
                Executors.newSingleThreadScheduledExecutor(
                        new BasicThreadFactory.Builder()
                                .namingPattern("workflow-lease-extend-%d")
                                .daemon(true)
                                .uncaughtExceptionHandler(uncaughtExceptionHandler)
                                .build());
    }

    void pollAndExecute(Worker worker) {
        Boolean discoveryOverride =
                Optional.ofNullable(
                                PropertyFactory.getBoolean(
                                        worker.getTaskDefName(), OVERRIDE_DISCOVERY, null))
                        .orElseGet(
                                () ->
                                        PropertyFactory.getBoolean(
                                                ALL_WORKERS, OVERRIDE_DISCOVERY, false));

        if (eurekaClient != null
                && !eurekaClient.getInstanceRemoteStatus().equals(InstanceStatus.UP)
                && !discoveryOverride) {
            LOGGER.debug("Instance is NOT UP in discovery - will not poll");
            return;
        }

        if (worker.paused()) {
            MetricsContainer.incrementTaskPausedCount(worker.getTaskDefName());
            LOGGER.debug("Worker {} has been paused. Not polling anymore!", worker.getClass());
            return;
        }

        String taskType = worker.getTaskDefName();
        PollingSemaphore pollingSemaphore = getPollingSemaphore(taskType);

        int slotsToAcquire = pollingSemaphore.availableSlots();
        if (slotsToAcquire <= 0 || !pollingSemaphore.acquireSlots(slotsToAcquire)) {
            return;
        }
        int acquiredTasks = 0;
        try {
            String domain =
                    Optional.ofNullable(PropertyFactory.getString(taskType, DOMAIN, null))
                            .orElseGet(
                                    () ->
                                            Optional.ofNullable(
                                                            PropertyFactory.getString(
                                                                    ALL_WORKERS, DOMAIN, null))
                                                    .orElse(taskToDomain.get(taskType)));

            LOGGER.debug("Polling task of type: {} in domain: '{}'", taskType, domain);

            List<Task> tasks =
                    MetricsContainer.getPollTimer(taskType)
                            .record(
                                    () ->
                                            taskClient.batchPollTasksInDomain(
                                                    taskType,
                                                    domain,
                                                    worker.getIdentity(),
                                                    slotsToAcquire,
                                                    worker.getBatchPollTimeoutInMS()));
            acquiredTasks = tasks.size();
            for (Task task : tasks) {
                if (Objects.nonNull(task) && StringUtils.isNotBlank(task.getTaskId())) {
                    MetricsContainer.incrementTaskPollCount(taskType, 1);
                    LOGGER.debug(
                            "Polled task: {} of type: {} in domain: '{}', from worker: {}",
                            task.getTaskId(),
                            taskType,
                            domain,
                            worker.getIdentity());

                    CompletableFuture<Task> taskCompletableFuture =
                            CompletableFuture.supplyAsync(
                                    () -> processTask(task, worker, pollingSemaphore),
                                    executorService);

                    if (task.getResponseTimeoutSeconds() > 0 && worker.leaseExtendEnabled()) {
                        ScheduledFuture<?> leaseExtendFuture =
                                leaseExtendExecutorService.scheduleWithFixedDelay(
                                        extendLease(task, taskCompletableFuture),
                                        Math.round(
                                                task.getResponseTimeoutSeconds()
                                                        * LEASE_EXTEND_DURATION_FACTOR),
                                        Math.round(
                                                task.getResponseTimeoutSeconds()
                                                        * LEASE_EXTEND_DURATION_FACTOR),
                                        TimeUnit.SECONDS);
                        leaseExtendMap.put(task.getTaskId(), leaseExtendFuture);
                    }

                    taskCompletableFuture.whenComplete(this::finalizeTask);
                } else {
                    // no task was returned in the poll, release the permit
                    pollingSemaphore.complete(1);
                }
            }
        } catch (Exception e) {
            MetricsContainer.incrementTaskPollErrorCount(worker.getTaskDefName(), e);
            LOGGER.error("Error when polling for tasks", e);
        }

        // immediately release unused permits
        pollingSemaphore.complete(slotsToAcquire - acquiredTasks);
    }

    void shutdown(int timeout) {
        shutdownAndAwaitTermination(executorService, timeout);
        shutdownAndAwaitTermination(leaseExtendExecutorService, timeout);
        leaseExtendMap.clear();
    }

    void shutdownAndAwaitTermination(ExecutorService executorService, int timeout) {
        try {
            executorService.shutdown();
            if (executorService.awaitTermination(timeout, TimeUnit.SECONDS)) {
                LOGGER.debug("tasks completed, shutting down");
            } else {
                LOGGER.warn(String.format("forcing shutdown after waiting for %s second", timeout));
                executorService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            LOGGER.warn("shutdown interrupted, invoking shutdownNow");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler =
            (thread, error) -> {
                // JVM may be in unstable state, try to send metrics then exit
                MetricsContainer.incrementUncaughtExceptionCount();
                LOGGER.error("Uncaught exception. Thread {} will exit now", thread, error);
            };

    private Task processTask(Task task, Worker worker, PollingSemaphore pollingSemaphore) {
        LOGGER.debug(
                "Executing task: {} of type: {} in worker: {} at {}",
                task.getTaskId(),
                task.getTaskDefName(),
                worker.getClass().getSimpleName(),
                worker.getIdentity());
        try {
            executeTask(worker, task);
        } catch (Throwable t) {
            task.setStatus(Task.Status.FAILED);
            TaskResult result = new TaskResult(task);
            handleException(t, result, worker, task);
        } finally {
            pollingSemaphore.complete(1);
        }
        return task;
    }

    private void executeTask(Worker worker, Task task) {
        StopWatch stopwatch = new StopWatch();
        stopwatch.start();
        TaskResult result = null;
        try {
            LOGGER.debug(
                    "Executing task: {} in worker: {} at {}",
                    task.getTaskId(),
                    worker.getClass().getSimpleName(),
                    worker.getIdentity());
            result = worker.execute(task);
            result.setWorkflowInstanceId(task.getWorkflowInstanceId());
            result.setTaskId(task.getTaskId());
            result.setWorkerId(worker.getIdentity());
        } catch (Exception e) {
            LOGGER.error(
                    "Unable to execute task: {} of type: {}",
                    task.getTaskId(),
                    task.getTaskDefName(),
                    e);
            if (result == null) {
                task.setStatus(Task.Status.FAILED);
                result = new TaskResult(task);
            }
            handleException(e, result, worker, task);
        } finally {
            stopwatch.stop();
            MetricsContainer.getExecutionTimer(worker.getTaskDefName())
                    .record(stopwatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        }

        LOGGER.debug(
                "Task: {} executed by worker: {} at {} with status: {}",
                task.getTaskId(),
                worker.getClass().getSimpleName(),
                worker.getIdentity(),
                result.getStatus());
        updateTaskResult(updateRetryCount, task, result, worker);
    }

    private void finalizeTask(Task task, Throwable throwable) {
        if (throwable != null) {
            LOGGER.error(
                    "Error processing task: {} of type: {}",
                    task.getTaskId(),
                    task.getTaskType(),
                    throwable);
            MetricsContainer.incrementTaskExecutionErrorCount(task.getTaskType(), throwable);
        } else {
            LOGGER.debug(
                    "Task:{} of type:{} finished processing with status:{}",
                    task.getTaskId(),
                    task.getTaskDefName(),
                    task.getStatus());
            String taskId = task.getTaskId();
            ScheduledFuture<?> leaseExtendFuture = leaseExtendMap.get(taskId);
            if (leaseExtendFuture != null) {
                leaseExtendFuture.cancel(true);
                leaseExtendMap.remove(taskId);
            }
        }
    }

    private void updateTaskResult(int count, Task task, TaskResult result, Worker worker) {
        try {
            // upload if necessary
            Optional<String> optionalExternalStorageLocation =
                    retryOperation(
                            (TaskResult taskResult) -> upload(taskResult, task.getTaskType()),
                            count,
                            result,
                            "evaluateAndUploadLargePayload");

            if (optionalExternalStorageLocation.isPresent()) {
                result.setExternalOutputPayloadStoragePath(optionalExternalStorageLocation.get());
                result.setOutputData(null);
            }

            retryOperation(
                    (TaskResult taskResult) -> {
                        taskClient.updateTask(taskResult);
                        return null;
                    },
                    count,
                    result,
                    "updateTask");
        } catch (Exception e) {
            worker.onErrorUpdate(task);
            MetricsContainer.incrementTaskUpdateErrorCount(worker.getTaskDefName(), e);
            LOGGER.error(
                    String.format(
                            "Failed to update result: %s for task: %s in worker: %s",
                            result.toString(), task.getTaskDefName(), worker.getIdentity()),
                    e);
        }
    }

    private Optional<String> upload(TaskResult result, String taskType) {
        try {
            return taskClient.evaluateAndUploadLargePayload(result.getOutputData(), taskType);
        } catch (IllegalArgumentException iae) {
            result.setReasonForIncompletion(iae.getMessage());
            result.setOutputData(null);
            result.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
            return Optional.empty();
        }
    }

    private <T, R> R retryOperation(Function<T, R> operation, int count, T input, String opName) {
        int index = 0;
        while (index < count) {
            try {
                return operation.apply(input);
            } catch (Exception e) {
                index++;
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ie) {
                    LOGGER.error("Retry interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Exhausted retries performing " + opName);
    }

    private void handleException(Throwable t, TaskResult result, Worker worker, Task task) {
        LOGGER.error(String.format("Error while executing task %s", task.toString()), t);
        MetricsContainer.incrementTaskExecutionErrorCount(worker.getTaskDefName(), t);
        result.setStatus(TaskResult.Status.FAILED);
        result.setReasonForIncompletion("Error while executing the task: " + t);

        StringWriter stringWriter = new StringWriter();
        t.printStackTrace(new PrintWriter(stringWriter));
        result.log(stringWriter.toString());

        updateTaskResult(updateRetryCount, task, result, worker);
    }

    private PollingSemaphore getPollingSemaphore(String taskType) {
        return pollingSemaphoreMap.get(taskType);
    }

    private Runnable extendLease(Task task, CompletableFuture<Task> taskCompletableFuture) {
        return () -> {
            if (taskCompletableFuture.isDone()) {
                LOGGER.warn(
                        "Task processing for {} completed, but its lease extend was not cancelled",
                        task.getTaskId());
                return;
            }
            LOGGER.info("Attempting to extend lease for {}", task.getTaskId());
            try {
                TaskResult result = new TaskResult(task);
                result.setExtendLease(true);
                retryOperation(
                        (TaskResult taskResult) -> {
                            taskClient.updateTask(taskResult);
                            return null;
                        },
                        LEASE_EXTEND_RETRY_COUNT,
                        result,
                        "extend lease");
                MetricsContainer.incrementTaskLeaseExtendCount(task.getTaskDefName(), 1);
            } catch (Exception e) {
                MetricsContainer.incrementTaskLeaseExtendErrorCount(task.getTaskDefName(), e);
                LOGGER.error("Failed to extend lease for {}", task.getTaskId(), e);
            }
        };
    }
}
