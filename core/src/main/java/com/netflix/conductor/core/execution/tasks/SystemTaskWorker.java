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
package com.netflix.conductor.core.execution.tasks;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.SemaphoreUtil;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;

import com.google.common.util.concurrent.Uninterruptibles;

/** The worker that polls and executes an async system task. */
@Component
@ConditionalOnProperty(
        name = "conductor.system-task-workers.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SystemTaskWorker extends LifecycleAwareComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTaskWorker.class);

    private final long pollInterval;
    private final QueueDAO queueDAO;

    private volatile ExecutorService sharedExecutorService;
    private final int systemTaskWorkerThreadCount;
    private final AsyncSystemTaskExecutor asyncSystemTaskExecutor;
    private final ConductorProperties properties;
    private final int queuePopTimeout;
    private final ReentrantReadWriteLock dispatchLock = new ReentrantReadWriteLock();

    ConcurrentHashMap<String, ExecutionConfig> queueExecutionConfigMap = new ConcurrentHashMap<>();

    public SystemTaskWorker(
            QueueDAO queueDAO,
            AsyncSystemTaskExecutor asyncSystemTaskExecutor,
            ConductorProperties properties) {
        this.properties = properties;
        int threadCount = properties.getSystemTaskWorkerThreadCount();
        this.systemTaskWorkerThreadCount = threadCount;
        // All non-isolated queues share one thread pool. Each queue gets its own semaphore (see
        // getExecutionConfig) so one slow/busy queue cannot starve other queues' polling.
        this.sharedExecutorService =
                ExecutionConfig.newThreadPool(threadCount, "system-task-worker-%d");
        this.asyncSystemTaskExecutor = asyncSystemTaskExecutor;
        this.queueDAO = queueDAO;
        this.pollInterval = properties.getSystemTaskWorkerPollInterval().toMillis();
        this.queuePopTimeout = (int) properties.getSystemTaskQueuePopTimeout().toMillis();

        LOGGER.info("SystemTaskWorker initialized with {} threads", threadCount);
    }

    @Override
    public void doStart() {
        // doStop() permanently shuts down every pool it finds (matching real Spring shutdown
        // semantics on process exit). But this component can also be legitimately stopped and
        // restarted within the same JVM — e.g. test-harness specs that arm/disarm the worker
        // between features while sharing one cached Spring context. Detect that case and rebuild:
        // existing ExecutionConfig entries hold references to the now-dead executors, so they
        // must be dropped too, not just the shared pool.
        dispatchLock.writeLock().lock();
        try {
            if (sharedExecutorService.isShutdown()) {
                sharedExecutorService =
                        ExecutionConfig.newThreadPool(
                                systemTaskWorkerThreadCount, "system-task-worker-%d");
                queueExecutionConfigMap.clear();
            }
        } finally {
            dispatchLock.writeLock().unlock();
        }
    }

    @Override
    public void doStop() {
        // Pool worker threads are not daemon threads (BasicThreadFactory falls back to
        // Executors.defaultThreadFactory() when .daemon() isn't set), but that does NOT keep the
        // JVM alive on SIGTERM: Runtime.exit() runs shutdown hooks then calls halt(), which does
        // not wait for ordinary threads. Without draining here, every in-flight task is truncated
        // instantly on every restart/deploy instead of being given a chance to finish.
        Set<ExecutorService> executors = new HashSet<>();
        executors.add(sharedExecutorService);
        queueExecutionConfigMap
                .values()
                .forEach(config -> executors.add(config.getExecutorService()));

        long deadline =
                System.nanoTime() + properties.getSystemTaskWorkerCallbackDuration().toNanos();
        dispatchLock.writeLock().lock();
        try {
            executors.forEach(ExecutorService::shutdown);
        } finally {
            dispatchLock.writeLock().unlock();
        }
        for (ExecutorService executor : executors) {
            long remainingNanos = deadline - System.nanoTime();
            try {
                if (remainingNanos <= 0
                        || !executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                    LOGGER.warn(
                            "System task executor did not drain before the shutdown deadline, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void startPolling(WorkflowSystemTask systemTask) {
        startPolling(systemTask, systemTask.getTaskType());
    }

    public void startPolling(WorkflowSystemTask systemTask, String queueName) {
        ExecutionConfig config = getExecutionConfig(queueName);
        int permits = config.getSemaphoreUtil().availableSlots();
        int poolSize = config.getPoolSize();
        if (poolSize > 0) {
            LOGGER.info(
                    "Starting poller — queue: {}, dedicated pool: {} threads, permits: {}, pollInterval: {} ms",
                    queueName,
                    poolSize,
                    permits,
                    pollInterval);
        } else {
            LOGGER.info(
                    "Starting poller — queue: {}, shared pool: {} threads, permits: {}, pollInterval: {} ms",
                    queueName,
                    systemTaskWorkerThreadCount,
                    permits,
                    pollInterval);
        }
        Thread poller =
                new Thread(
                        () -> this.pollAndExecuteLoop(systemTask, queueName),
                        "system-task-poller-" + queueName);
        poller.setDaemon(true);
        poller.start();
    }

    @VisibleForTesting
    void pollAndExecuteLoop(WorkflowSystemTask systemTask, String queueName) {
        while (true) {
            if (!isRunning()) {
                // Not started yet (startPolling can be called before SmartLifecycle.start(), e.g.
                // by IsolatedTaskQueueProducer during context refresh) or stopped. Idle and
                // re-check so pollers survive stop()/start() cycles instead of exiting for good.
                Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(pollInterval));
                continue;
            }
            boolean executed;
            try {
                executed = pollAndExecute(systemTask, queueName);
            } catch (Throwable t) {
                // No exception raised from pollAndExecute should ever be able to kill this loop —
                // there is no Thread.UncaughtExceptionHandler in this codebase, so an escaped
                // exception here would silently and permanently stop this queue's poller.
                Monitors.recordTaskPollError(
                        QueueUtils.getTaskType(queueName), t.getClass().getSimpleName());
                LOGGER.error("Uncaught error polling/executing queue:{}", queueName, t);
                executed = false;
            }
            if (!executed) {
                Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(pollInterval));
            }
        }
    }

    boolean pollAndExecute(WorkflowSystemTask systemTask, String queueName) {

        ExecutionConfig executionConfig = getExecutionConfig(queueName);
        SemaphoreUtil semaphoreUtil = executionConfig.getSemaphoreUtil();
        ExecutorService executorService = executionConfig.getExecutorService();
        String taskName = QueueUtils.getTaskType(queueName);

        // Use available permits as a backpressure hint: never request more tasks than we can
        // immediately dispatch. Cap at systemTaskMaxPollCount (the batch size knob); values < 1
        // historically mean "no explicit cap" — do not stall polling for such configs.
        int maxPollCount = properties.getSystemTaskMaxPollCount();
        int batchSize = semaphoreUtil.availableSlots();
        if (maxPollCount > 0) {
            batchSize = Math.min(batchSize, maxPollCount);
        }
        if (batchSize <= 0) {
            Monitors.recordSystemTaskWorkerPollingLimited(queueName);
            return false;
        }

        List<String> polledTaskIds;
        try {
            polledTaskIds = queueDAO.pop(queueName, batchSize, queuePopTimeout);
        } catch (Exception e) {
            // Poll failed — no permits were held, nothing to release.
            Monitors.recordTaskPollError(taskName, e.getClass().getSimpleName());
            LOGGER.error("Error polling system task in queue:{}", queueName, e);
            return false;
        }

        Monitors.recordTaskPoll(queueName);
        LOGGER.debug(
                "Polling queue:{}, batchSize:{}, got:{}",
                queueName,
                batchSize,
                polledTaskIds.size());

        polledTaskIds = polledTaskIds.stream().filter(StringUtils::isNotBlank).toList();
        int taskCount = polledTaskIds.size();
        if (taskCount == 0) {
            return false;
        }

        // Acquire exactly as many permits as tasks received. Since this is the only thread that
        // decrements this queue's semaphore and taskCount <= batchSize <= availableSlots at the
        // time of the check above, tryAcquire should always succeed. If it doesn't (e.g. due to a
        // bug or future code change violating the single-poller invariant), reset the tasks so
        // they become re-deliverable as soon as the queue implementation allows (some impls only
        // redeliver popped messages after the unack sweep), instead of silently dropping them.
        if (!semaphoreUtil.acquireSlots(taskCount)) {
            LOGGER.warn(
                    "Could not acquire {} permits for queue {} — resetting tasks for immediate retry",
                    taskCount,
                    queueName);
            for (String taskId : polledTaskIds) {
                try {
                    queueDAO.resetOffsetTime(queueName, taskId);
                } catch (Throwable e) {
                    LOGGER.error(
                            "Failed to reset offset for task {} in queue {} — will retry after unack timeout",
                            taskId,
                            queueName,
                            e);
                }
            }
            return false;
        }

        int permitsToRelease = 0;
        for (String taskId : polledTaskIds) {
            LOGGER.debug(
                    "Task: {} from queue: {} being sent to the workflow executor",
                    taskId,
                    queueName);
            Monitors.recordTaskPollCount(queueName, 1);
            try {
                dispatchLock.readLock().lock();
                try {
                    if (!isRunning() || executorService.isShutdown()) {
                        queueDAO.resetOffsetTime(queueName, taskId);
                        permitsToRelease++;
                        continue;
                    }
                    // Deliberately not acked here: a running task keeps its queue message so
                    // the sweeper's repair does not re-queue it (issue #1321). The executor
                    // extends its visibility before invoking and removes it on completion.
                    CompletableFuture.runAsync(
                                    () -> asyncSystemTaskExecutor.execute(systemTask, taskId),
                                    executorService)
                            .whenComplete((r, e) -> semaphoreUtil.completeProcessing(1));
                } finally {
                    dispatchLock.readLock().unlock();
                }
            } catch (Throwable e) {
                // Dispatch failed for this task — release its permit immediately.
                permitsToRelease++;
                Monitors.recordTaskPollError(taskName, e.getClass().getSimpleName());
                LOGGER.error("Error dispatching task:{} in queue:{}", taskId, queueName, e);
            }
        }
        if (permitsToRelease > 0) {
            semaphoreUtil.completeProcessing(permitsToRelease);
        }
        // Report progress only if at least one task was dispatched. When the whole batch failed
        // (e.g. the execution store is down while the queue store is healthy), returning false
        // makes the poll loop sleep pollInterval instead of hot-spinning through the backlog,
        // churning messages invisible and flooding logs/metrics at pop-latency speed.
        return permitsToRelease < taskCount;
    }

    @VisibleForTesting
    ExecutionConfig getExecutionConfig(String taskQueue) {
        if (QueueUtils.isIsolatedQueue(taskQueue)) {
            return queueExecutionConfigMap.computeIfAbsent(
                    taskQueue, __ -> createIsolatedExecutionConfig());
        }
        return queueExecutionConfigMap.computeIfAbsent(
                taskQueue, __ -> createNonIsolatedExecutionConfig(taskQueue));
    }

    private ExecutionConfig createNonIsolatedExecutionConfig(String taskQueue) {
        String taskType = QueueUtils.getTaskType(taskQueue);
        ConductorProperties.TaskWorkerConfig override = findTaskWorkerConfig(taskType);

        if (override != null && override.getThreadCount() > 0) {
            // Dedicated pool: this task type gets its own threads, isolated from everything else.
            // The thread count doubles as the type's in-flight cap (permits == threads), so the
            // poller never pops more messages than it has free capacity to run.
            int threads = override.getThreadCount();
            LOGGER.info("Task type {} using dedicated pool: {} threads", taskType, threads);
            return new ExecutionConfig(
                    threads, "system-task-worker-" + taskType.toLowerCase() + "-%d");
        }
        if (override != null) {
            LOGGER.warn(
                    "Task type {} has a taskWorkerConfigs entry without a positive threadCount"
                            + " — ignored, sharing the common pool",
                    taskType);
        }

        // Shared pool, but own semaphore, so one slow/busy queue cannot starve other queues'
        // polling. Permits default to the shared pool width — the same effective cap the old
        // single shared semaphore imposed.
        return new ExecutionConfig(sharedExecutorService, systemTaskWorkerThreadCount);
    }

    private ConductorProperties.TaskWorkerConfig findTaskWorkerConfig(String taskType) {
        Map<String, ConductorProperties.TaskWorkerConfig> configs =
                properties.getTaskWorkerConfigs();
        if (configs.isEmpty()) {
            return null;
        }
        // Direct match first, then case-insensitive fallback (YAML preserves case; .properties may
        // not). Lookup result is cached in queueExecutionConfigMap so this is called once per type.
        ConductorProperties.TaskWorkerConfig config = configs.get(taskType);
        if (config != null) {
            return config;
        }
        for (Map.Entry<String, ConductorProperties.TaskWorkerConfig> entry : configs.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(taskType)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private ExecutionConfig createIsolatedExecutionConfig() {
        int threadCount = properties.getIsolatedSystemTaskWorkerThreadCount();
        return new ExecutionConfig(threadCount, "isolated-system-task-worker-%d");
    }
}
