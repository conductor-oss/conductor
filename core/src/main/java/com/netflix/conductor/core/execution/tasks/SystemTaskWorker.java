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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    ExecutionConfig defaultExecutionConfig;
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
        this.defaultExecutionConfig = new ExecutionConfig(threadCount, "system-task-worker-%d");
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
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(
                        () -> this.pollAndExecute(systemTask, queueName),
                        1000,
                        pollInterval,
                        TimeUnit.MILLISECONDS);
        LOGGER.info("Started listening for task: {} in queue: {}", systemTask, queueName);
    }

    void pollAndExecute(WorkflowSystemTask systemTask, String queueName) {
        if (!isRunning()) {
            LOGGER.debug(
                    "{} stopped. Not polling for task: {}", getClass().getSimpleName(), systemTask);
            return;
        }

        ExecutionConfig executionConfig = getExecutionConfig(queueName);
        SemaphoreUtil semaphoreUtil = executionConfig.getSemaphoreUtil();
        ExecutorService executorService = executionConfig.getExecutorService();
        String taskName = QueueUtils.getTaskType(queueName);
        final int systemTaskMaxPollCount = properties.getSystemTaskMaxPollCount();
        int maxSystemTasksToAcquire =
                (systemTaskMaxPollCount < 1
                                || systemTaskMaxPollCount
                                        > properties.getSystemTaskWorkerThreadCount())
                        ? properties.getSystemTaskWorkerThreadCount()
                        : systemTaskMaxPollCount;
        int messagesToAcquire = Math.min(semaphoreUtil.availableSlots(), maxSystemTasksToAcquire);

        try {
            if (messagesToAcquire <= 0 || !semaphoreUtil.acquireSlots(messagesToAcquire)) {
                // no available slots, do not poll
                Monitors.recordSystemTaskWorkerPollingLimited(queueName);
                return;
            }

            LOGGER.debug("Polling queue: {} with {} slots acquired", queueName, messagesToAcquire);

            List<String> polledTaskIds =
                    queueDAO.pop(queueName, messagesToAcquire, queuePopTimeout);

            Monitors.recordTaskPoll(queueName);
            LOGGER.debug("Polling queue:{}, got {} tasks", queueName, polledTaskIds.size());

            if (polledTaskIds.size() > 0) {
                // Immediately release unused slots when number of messages acquired is less than
                // acquired slots
                if (polledTaskIds.size() < messagesToAcquire) {
                    semaphoreUtil.completeProcessing(messagesToAcquire - polledTaskIds.size());
                }

                for (String taskId : polledTaskIds) {
                    if (StringUtils.isNotBlank(taskId)) {
                        LOGGER.debug(
                                "Task: {} from queue: {} being sent to the workflow executor",
                                taskId,
                                queueName);
                        Monitors.recordTaskPollCount(queueName, 1);

                        executionService.ackTaskReceived(taskId);

                        CompletableFuture<Void> taskCompletableFuture =
                                CompletableFuture.runAsync(
                                        () -> asyncSystemTaskExecutor.execute(systemTask, taskId),
                                        executorService);

                        // release permit after processing is complete
                        taskCompletableFuture.whenComplete(
                                (r, e) -> semaphoreUtil.completeProcessing(1));
                    } else {
                        semaphoreUtil.completeProcessing(1);
                    }
                }
            } else {
                // no task polled, release permit
                semaphoreUtil.completeProcessing(messagesToAcquire);
            }
        } catch (Exception e) {
            // release the permit if exception is thrown during polling, because the thread would
            // not be busy
            semaphoreUtil.completeProcessing(messagesToAcquire);
            Monitors.recordTaskPollError(taskName, e.getClass().getSimpleName());
            LOGGER.error("Error polling system task in queue:{}", queueName, e);
        }
    }

    @VisibleForTesting
    ExecutionConfig getExecutionConfig(String taskQueue) {
        if (!QueueUtils.isIsolatedQueue(taskQueue)) {
            return this.defaultExecutionConfig;
        }
        return queueExecutionConfigMap.computeIfAbsent(
                taskQueue, __ -> this.createExecutionConfig());
    }

    private ExecutionConfig createExecutionConfig() {
        int threadCount = properties.getIsolatedSystemTaskWorkerThreadCount();
        String threadNameFormat = "isolated-system-task-worker-%d";
        return new ExecutionConfig(threadCount, threadNameFormat);
    }
}
