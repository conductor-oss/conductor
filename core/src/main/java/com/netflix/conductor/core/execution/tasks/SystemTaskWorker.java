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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import com.netflix.conductor.service.ExecutionService;

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

    private final ExecutorService sharedExecutorService;
    private final int systemTaskWorkerThreadCount;
    private final AsyncSystemTaskExecutor asyncSystemTaskExecutor;
    private final ConductorProperties properties;
    private final ExecutionService executionService;
    private final int queuePopTimeout;

    ConcurrentHashMap<String, ExecutionConfig> queueExecutionConfigMap = new ConcurrentHashMap<>();

    public SystemTaskWorker(
            QueueDAO queueDAO,
            AsyncSystemTaskExecutor asyncSystemTaskExecutor,
            ConductorProperties properties,
            ExecutionService executionService) {
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
        this.executionService = executionService;
        this.queuePopTimeout = (int) properties.getSystemTaskQueuePopTimeout().toMillis();

        LOGGER.info("SystemTaskWorker initialized with {} threads", threadCount);
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
        Executors.newSingleThreadExecutor()
                .execute(() -> this.pollAndExecuteLoop(systemTask, queueName));
    }

    private void pollAndExecuteLoop(WorkflowSystemTask systemTask, String queueName) {
        while (isRunning()) {
            boolean executed = pollAndExecute(systemTask, queueName);
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
        // immediately dispatch. Cap at systemTaskMaxPollCount (the batch size knob).
        int batchSize =
                Math.min(semaphoreUtil.availableSlots(), properties.getSystemTaskMaxPollCount());
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
        // bug or future code change violating the single-poller invariant), reset the tasks back to
        // score=now so they are immediately re-deliverable — do NOT leave them invisible for the
        // full 30-second unack timeout.
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
                executionService.ackTaskReceived(taskId);
                CompletableFuture.runAsync(
                                () -> asyncSystemTaskExecutor.execute(systemTask, taskId),
                                executorService)
                        .whenComplete((r, e) -> semaphoreUtil.completeProcessing(1));
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
        return true;
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
            int threads = override.getThreadCount();
            int permits = override.getPermitCount() > 0 ? override.getPermitCount() : threads;
            LOGGER.info(
                    "Task type {} using dedicated pool: threads={}, permits={}",
                    taskType,
                    threads,
                    permits);
            return new ExecutionConfig(
                    threads, "system-task-worker-" + taskType.toLowerCase() + "-%d", permits);
        }

        // Shared pool, but own semaphore. A per-task permitCount override caps concurrency for
        // this type without needing dedicated threads.
        int permits =
                (override != null && override.getPermitCount() > 0)
                        ? override.getPermitCount()
                        : systemTaskWorkerThreadCount;
        if (override != null) {
            LOGGER.info("Task type {} using shared pool with permits={}", taskType, permits);
        }
        return new ExecutionConfig(sharedExecutorService, permits);
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
