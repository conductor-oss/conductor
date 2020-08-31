/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.core.utils.SemaphoreUtil;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import com.netflix.conductor.service.ExecutionService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the threadpool used by system task workers for execution.
 */
class SystemTaskExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTaskExecutor.class);

    private final int callbackTime;
    private final QueueDAO queueDAO;

    ExecutionConfig defaultExecutionConfig;
    private final WorkflowExecutor workflowExecutor;
    private final Configuration config;
    private final int maxPollCount;
    private final ExecutionService executionService;

    ConcurrentHashMap<String, ExecutionConfig> queueExecutionConfigMap = new ConcurrentHashMap<>();

    SystemTaskExecutor(QueueDAO queueDAO, WorkflowExecutor workflowExecutor, Configuration config, ExecutionService executionService) {
        this.config = config;
        int threadCount = config.getSystemTaskWorkerThreadCount();
        this.callbackTime = config.getSystemTaskWorkerCallbackSeconds();

        String threadNameFormat = "system-task-worker-%d";
        this.defaultExecutionConfig = new ExecutionConfig(threadCount, threadNameFormat);
        this.workflowExecutor = workflowExecutor;
        this.queueDAO = queueDAO;
        this.maxPollCount = config.getSystemTaskMaxPollCount();
        this.executionService = executionService;

        LOGGER.info("Initialized the SystemTaskExecutor with {} threads and callback time: {} seconds", threadCount,
            callbackTime);
    }

    void pollAndExecute(String queueName) {
        // get the remaining capacity of worker queue to prevent queue full exception
        ExecutionConfig executionConfig = getExecutionConfig(queueName);
        SemaphoreUtil semaphoreUtil = executionConfig.getSemaphoreUtil();
        ExecutorService executorService = executionConfig.getExecutorService();
        String taskName = QueueUtils.getTaskType(queueName);

        if (!semaphoreUtil.acquireSlots(1)) {
            // no available permits, do not poll
            Monitors.recordSystemTaskWorkerPollingLimited(queueName);
            return;
        }

        int acquiredSlots = 1;

        try {
            //Since already one slot is acquired, now try if maxSlot-1 is available
            int slotsToAcquire = Math.min(semaphoreUtil.availableSlots(), maxPollCount - 1);

            // Try to acquires remaining permits to achieve maxPollCount
            if (slotsToAcquire > 0 && semaphoreUtil.acquireSlots(slotsToAcquire)) {
                acquiredSlots += slotsToAcquire;
            }
            LOGGER.debug("Polling queue: {} with {} slots acquired", queueName, acquiredSlots);

            List<String> polledTaskIds = queueDAO.pop(queueName, acquiredSlots, 200);

            Monitors.recordTaskPoll(queueName);
            LOGGER.debug("Polling queue:{}, got {} tasks", queueName, polledTaskIds.size());

            if (polledTaskIds.size() > 0) {
                // Immediately release unused permits when polled no. of messages are less than acquired permits
                if (polledTaskIds.size() < acquiredSlots) {
                    semaphoreUtil.completeProcessing(acquiredSlots - polledTaskIds.size());
                }

                for (String taskId : polledTaskIds) {
                    if (StringUtils.isNotBlank(taskId)) {
                        LOGGER.debug("Task: {} from queue: {} being sent to the workflow executor", taskId, queueName);
                        Monitors.recordTaskPollCount(queueName, "", 1);

                        WorkflowSystemTask systemTask = SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.get(taskName);
                        executionService.ackTaskReceived(taskId);

                        CompletableFuture<Void> taskCompletableFuture = CompletableFuture.runAsync(() ->
                                workflowExecutor.executeSystemTask(systemTask, taskId, callbackTime), executorService);

                        // release permit after processing is complete
                        taskCompletableFuture.whenComplete((r, e) -> semaphoreUtil.completeProcessing(1));
                    } else {
                        semaphoreUtil.completeProcessing(1);
                    }
                }
            } else {
                // no task polled, release permit
                semaphoreUtil.completeProcessing(acquiredSlots);
            }
        } catch (Exception e) {
            // release the permit if exception is thrown during polling, because the thread would not be busy
            semaphoreUtil.completeProcessing(acquiredSlots);
            Monitors.recordTaskPollError(taskName, "", e.getClass().getSimpleName());
            LOGGER.error("Error polling system task in queue:{}", queueName, e);
        }
    }

    @VisibleForTesting
    ExecutionConfig getExecutionConfig(String taskQueue) {
        if (!QueueUtils.isIsolatedQueue(taskQueue)) {
            return this.defaultExecutionConfig;
        }
        return queueExecutionConfigMap.computeIfAbsent(taskQueue, __ -> this.createExecutionConfig());
    }

    private ExecutionConfig createExecutionConfig() {
        int threadCount = config.getSystemTaskWorkerIsolatedThreadCount();
        String threadNameFormat = "isolated-system-task-worker-%d";
        return new ExecutionConfig(threadCount, threadNameFormat);
    }
}
