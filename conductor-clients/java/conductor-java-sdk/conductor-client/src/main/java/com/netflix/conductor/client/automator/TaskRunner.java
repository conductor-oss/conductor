/*
 * Copyright 2022 Conductor Authors.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.automator.filters.PollFilter;
import com.netflix.conductor.client.config.PropertyFactory;
import com.netflix.conductor.client.events.dispatcher.EventDispatcher;
import com.netflix.conductor.client.events.taskrunner.PollCompleted;
import com.netflix.conductor.client.events.taskrunner.PollFailure;
import com.netflix.conductor.client.events.taskrunner.PollStarted;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionCompleted;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionFailure;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionStarted;
import com.netflix.conductor.client.events.taskrunner.TaskRunnerEvent;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;

class TaskRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskRunner.class);
    private final TaskClient taskClient;
    private final int updateRetryCount;
    private final ExecutorService executorService;
    private final int taskPollTimeout;
    private final Semaphore permits;
    private final Worker worker;
    private final int pollingIntervalInMillis;
    private final String taskType;
    private final int errorAt;
    private int pollingErrorCount;
    private String domain;
    private volatile boolean pollingAndExecuting = true;
    private final List<PollFilter> pollFilters;
    private final EventDispatcher<TaskRunnerEvent> eventDispatcher;
    private final LinkedBlockingQueue<Task> tasksTobeExecuted;
    private final boolean enableUpdateV2;
    private static final int LEASE_EXTEND_RETRY_COUNT = 3;
    private static final double LEASE_EXTEND_DURATION_FACTOR = 0.8;
    private final ScheduledExecutorService leaseExtendExecutorService;
    private Map<String, ScheduledFuture<?>> leaseExtendMap = new HashMap<>();

    TaskRunner(Worker worker,
               TaskClient taskClient,
               int updateRetryCount,
               Map<String, String> taskToDomain,
               String workerNamePrefix,
               int threadCount,
               int taskPollTimeout,
               List<PollFilter> pollFilters,
               EventDispatcher<TaskRunnerEvent> eventDispatcher) {
        this.worker = worker;
        this.taskClient = taskClient;
        this.updateRetryCount = updateRetryCount;
        this.taskPollTimeout = taskPollTimeout;
        this.pollingIntervalInMillis = worker.getPollingInterval();
        this.taskType = worker.getTaskDefName();
        this.permits = new Semaphore(threadCount);
        this.pollFilters = pollFilters;
        this.eventDispatcher = eventDispatcher;
        this.tasksTobeExecuted = new LinkedBlockingQueue<>();
        this.enableUpdateV2 = Boolean.valueOf(System.getProperty("taskUpdateV2", "false"));
        LOGGER.info("taskUpdateV2 is set to {}", this.enableUpdateV2);
        //1. Is there a worker level override?
        this.domain = PropertyFactory.getString(taskType, Worker.PROP_DOMAIN, null);
        if (this.domain == null) {
            //2. If not, is there a blanket override?
            this.domain = PropertyFactory.getString(Worker.PROP_ALL_WORKERS, Worker.PROP_DOMAIN, null);
        }
        if (this.domain == null) {
            //3. was it supplied as part of the config?
            this.domain = taskToDomain.get(taskType);
        }

        int defaultLoggingInterval = 100;
        int errorInterval = PropertyFactory.getInteger(taskType, Worker.PROP_LOG_INTERVAL, 0);
        if (errorInterval == 0) {
            errorInterval = PropertyFactory.getInteger(Worker.PROP_ALL_WORKERS, Worker.PROP_LOG_INTERVAL, 0);
        }
        if (errorInterval == 0) {
            errorInterval = defaultLoggingInterval;
        }
        this.errorAt = errorInterval;
        LOGGER.info("Polling errors will be sampled at every {} error (after the first 100 errors) for taskType {}", this.errorAt, taskType);
        this.executorService = Executors.newFixedThreadPool(threadCount,
                new BasicThreadFactory.Builder()
                        .namingPattern(workerNamePrefix)
                        .uncaughtExceptionHandler(uncaughtExceptionHandler)
                        .build());
        LOGGER.info("Starting Worker for taskType '{}' with {} threads, {} ms polling interval and domain {}",
                taskType,
                threadCount,
                pollingIntervalInMillis,
                domain);
        LOGGER.info("Polling errors for taskType {} will be printed at every {} occurrence.", taskType, errorAt);

        LOGGER.info("Initialized the task lease extend executor");
        leaseExtendExecutorService = Executors.newSingleThreadScheduledExecutor(
            new BasicThreadFactory.Builder()
                .namingPattern("workflow-lease-extend-%d")
                .daemon(true)
                .uncaughtExceptionHandler(uncaughtExceptionHandler)
                .build()
        );
    }

    public void pollAndExecute() {
        Stopwatch stopwatch = null;
        while (pollingAndExecuting) {
            if (Thread.currentThread().isInterrupted()) {
                break; // Exit the loop if interrupted
            }

            try {
                List<Task> tasks = pollTasksForWorker();
                if (tasks.isEmpty()) {
                    if (stopwatch == null) {
                        stopwatch = Stopwatch.createStarted();
                    }
                    Uninterruptibles.sleepUninterruptibly(pollingIntervalInMillis, TimeUnit.MILLISECONDS);
                    continue;
                }
                if (stopwatch != null) {
                    stopwatch.stop();
                    LOGGER.trace("Poller for task {} waited for {} ms before getting {} tasks to execute", taskType, stopwatch.elapsed(TimeUnit.MILLISECONDS), tasks.size());
                    stopwatch = null;
                }
                tasks.forEach(task -> {
                    Future<Task> taskFuture = this.executorService.submit(() -> this.processTask(task));

                    if (task.getResponseTimeoutSeconds() > 0 && worker.leaseExtendEnabled()) {
                        ScheduledFuture<?> scheduledFuture = leaseExtendMap.get(task.getTaskId());
                        if (scheduledFuture != null) {
                            scheduledFuture.cancel(false);
                        }

                        long delay = Math.round(task.getResponseTimeoutSeconds() * LEASE_EXTEND_DURATION_FACTOR);
                        ScheduledFuture<?> leaseExtendFuture = leaseExtendExecutorService.scheduleWithFixedDelay(
                            extendLease(task, taskFuture),
                            delay,
                            delay,
                            TimeUnit.SECONDS
                        );
                        leaseExtendMap.put(task.getTaskId(), leaseExtendFuture);
                    }
                });
            } catch (Throwable t) {
                LOGGER.error(t.getMessage(), t);
            }
        }
    }

    public void shutdown(int timeout) {
        try {
            pollingAndExecuting = false;
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

    private List<Task> pollTasksForWorker() {
        eventDispatcher.publish(new PollStarted(taskType));

        if (worker.paused()) {
            LOGGER.trace("Worker {} has been paused. Not polling anymore!", worker.getClass());
            return List.of();
        }

        for (PollFilter filter : pollFilters) {
            if (!filter.filter(taskType, domain)) {
                LOGGER.trace("Filter returned false, not polling.");
                return List.of();
            }
        }

        int pollCount = 0;
        while (permits.tryAcquire()) {
            pollCount++;
        }

        if (pollCount == 0) {
            return List.of();
        }

        List<Task> tasks = new LinkedList<>();
        Stopwatch stopwatch = Stopwatch.createStarted(); //TODO move this to the top?
        try {
            LOGGER.trace("Polling task of type: {} in domain: '{}' with size {}", taskType, domain, pollCount);
            tasks = pollTask(pollCount);
            permits.release(pollCount - tasks.size());        //release extra permits
            stopwatch.stop();
            long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            LOGGER.debug("Time taken to poll {} task with a batch size of {} is {} ms", taskType, tasks.size(), elapsed);
            eventDispatcher.publish(new PollCompleted(taskType, elapsed));
        } catch (Throwable e) {
            permits.release(pollCount - tasks.size());

            //For the first 100 errors, just print them as is...
            boolean printError = pollingErrorCount < 100 || pollingErrorCount % errorAt == 0;
            pollingErrorCount++;
            if (pollingErrorCount > 10_000_000) {
                //Reset after 10 million errors
                pollingErrorCount = 0;
            }
            if (printError) {
                LOGGER.error("Error polling for taskType: {}, error = {}", taskType, e.getMessage(), e);
            }

            if (stopwatch.isRunning()) {
                stopwatch.stop();
            }

            long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            eventDispatcher.publish(new PollFailure(taskType, elapsed, e));
        }

        return tasks;
    }

    private List<Task> pollTask(int count) {
        if (count < 1) {
            return Collections.emptyList();
        }
        LOGGER.trace("in memory queue size for tasks: {}", tasksTobeExecuted.size());
        List<Task> polled = new ArrayList<>(count);
        tasksTobeExecuted.drainTo(polled, count);
        if(!polled.isEmpty()) {
            return polled;
        }
        String workerId = worker.getIdentity();
        LOGGER.debug("poll {} in the domain {} with batch size {}", taskType, domain, count);
        return taskClient.batchPollTasksInDomain(
                taskType, domain, workerId, count, this.taskPollTimeout);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler =
            (thread, error) -> {
                // JVM may be in unstable state, try to send metrics then exit
                LOGGER.error("Uncaught exception. Thread {} will exit now", thread, error);
            };

    private Task processTask(Task task) {
        eventDispatcher.publish(new TaskExecutionStarted(taskType, task.getTaskId(), worker.getIdentity()));
        LOGGER.trace("Executing task: {} of type: {} in worker: {} at {}", task.getTaskId(), taskType, worker.getClass().getSimpleName(), worker.getIdentity());
        LOGGER.trace("task {} is getting executed after {} ms of getting polled", task.getTaskId(), (System.currentTimeMillis() - task.getStartTime()));
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            executeTask(worker, task);
            stopwatch.stop();
            long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            LOGGER.trace(
                    "Took {} ms to execute and update task with id {}",
                    elapsed,
                    task.getTaskId());
        } catch (Throwable t) {
            task.setStatus(Task.Status.FAILED);
            TaskResult result = new TaskResult(task);
            handleException(t, result, worker, task);
        } finally {
            permits.release();
        }
        return task;
    }

    private void executeTask(Worker worker, Task task) {
        if (task == null || task.getTaskDefName().isEmpty()) {
            LOGGER.warn("Empty task {}", worker.getTaskDefName());
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        TaskResult result = null;
        try {
            LOGGER.trace(
                    "Executing task: {} in worker: {} at {}",
                    task.getTaskId(),
                    worker.getClass().getSimpleName(),
                    worker.getIdentity());
            result = worker.execute(task);
            stopwatch.stop();
            eventDispatcher.publish(new TaskExecutionCompleted(taskType, task.getTaskId(), worker.getIdentity(), stopwatch.elapsed(TimeUnit.MILLISECONDS)));
            result.setWorkflowInstanceId(task.getWorkflowInstanceId());
            result.setTaskId(task.getTaskId());
            result.setWorkerId(worker.getIdentity());
        } catch (Exception e) {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
            }
            eventDispatcher.publish(new TaskExecutionFailure(taskType, task.getTaskId(), worker.getIdentity(), e, stopwatch.elapsed(TimeUnit.MILLISECONDS)));

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
        }

        LOGGER.trace(
                "Task: {} executed by worker: {} at {} with status: {}",
                task.getTaskId(),
                worker.getClass().getSimpleName(),
                worker.getIdentity(),
                result.getStatus());
        Stopwatch updateStopWatch = Stopwatch.createStarted();
        updateTaskResult(updateRetryCount, task, result, worker);
        updateStopWatch.stop();
        LOGGER.trace(
                "Time taken to update the {} {} ms",
                task.getTaskType(),
                updateStopWatch.elapsed(TimeUnit.MILLISECONDS));
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
            if(enableUpdateV2) {
                Task nextTask = retryOperation(taskClient::updateTaskV2, count, result, "updateTaskV2");
                if (nextTask != null) {
                    tasksTobeExecuted.add(nextTask);
                }
            } else {
                retryOperation(
                    (TaskResult taskResult) -> {
                        taskClient.updateTask(taskResult);
                        return null;
                    },
                    count,
                    result,
                    "updateTask");
            }

        } catch (Exception e) {
            worker.onErrorUpdate(task);
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
                LOGGER.error("Error executing {}", opName, e);
                index++;
                Uninterruptibles.sleepUninterruptibly(500L * (count + 1), TimeUnit.MILLISECONDS);
            }
        }
        throw new RuntimeException("Exhausted retries performing " + opName);
    }

    private void handleException(Throwable t, TaskResult result, Worker worker, Task task) {
        LOGGER.error(String.format("Error while executing task %s", task.toString()), t);
        result.setStatus(TaskResult.Status.FAILED);
        result.setReasonForIncompletion("Error while executing the task: " + t);
        StringWriter stringWriter = new StringWriter();
        t.printStackTrace(new PrintWriter(stringWriter));
        result.log(stringWriter.toString());
        updateTaskResult(updateRetryCount, task, result, worker);
    }

    private Runnable extendLease(Task task, Future<Task> taskCompletableFuture) {
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
            } catch (Exception e) {
                LOGGER.error("Failed to extend lease for {}", task.getTaskId(), e);
            }
        };
    }
}
