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
package com.netflix.conductor.client.automator;

import com.google.common.base.Preconditions;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.telemetry.MetricsContainer;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.discovery.EurekaClient;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configures automated polling of tasks and execution via the registered {@link Worker}s.
 */
public class TaskRunnerConfigurer {

    private ScheduledExecutorService scheduledExecutorService;

    private final EurekaClient eurekaClient;
    private final TaskClient taskClient;
    private List<Worker> workers = new LinkedList<>();
    private final int sleepWhenRetry;
    private final int updateRetryCount;
    private final int threadCount;
    private final String workerNamePrefix;
    private final Map<String/*taskType*/, String/*domain*/> taskToDomain;

    private TaskPollExecutor taskPollExecutor;

    /**
     * @see TaskRunnerConfigurer.Builder
     * @see TaskRunnerConfigurer#init()
     */
    private TaskRunnerConfigurer(Builder builder) {
        this.eurekaClient = builder.eurekaClient;
        this.taskClient = builder.taskClient;
        this.sleepWhenRetry = builder.sleepWhenRetry;
        this.updateRetryCount = builder.updateRetryCount;
        this.workerNamePrefix = builder.workerNamePrefix;
        this.taskToDomain = builder.taskToDomain;
        builder.workers.forEach(workers::add);
        this.threadCount = (builder.threadCount == -1) ? workers.size() : builder.threadCount;
    }

    /**
     * Builder used to create the instances of TaskRunnerConfigurer
     */
    public static class Builder {

        private String workerNamePrefix = "workflow-worker-%d";
        private int sleepWhenRetry = 500;
        private int updateRetryCount = 3;
        private int threadCount = -1;
        private Iterable<Worker> workers;
        private EurekaClient eurekaClient;
        private TaskClient taskClient;
        private Map<String/*taskType*/, String/*domain*/> taskToDomain = new HashMap<>();

        public Builder(TaskClient taskClient, Iterable<Worker> workers) {
            Preconditions.checkNotNull(taskClient, "TaskClient cannot be null");
            Preconditions.checkNotNull(workers, "Workers cannot be null");
            this.taskClient = taskClient;
            this.workers = workers;
        }

        /**
         * @param workerNamePrefix prefix to be used for worker names, defaults to workflow-worker- if not supplied.
         * @return Returns the current instance.
         */
        public Builder withWorkerNamePrefix(String workerNamePrefix) {
            this.workerNamePrefix = workerNamePrefix;
            return this;
        }

        /**
         * @param sleepWhenRetry time in milliseconds, for which the thread should sleep when task update call fails,
         *                       before retrying the operation.
         * @return Returns the current instance.
         */
        public Builder withSleepWhenRetry(int sleepWhenRetry) {
            this.sleepWhenRetry = sleepWhenRetry;
            return this;
        }

        /**
         * @param updateRetryCount number of times to retry the failed updateTask operation
         * @return Builder instance
         * @see #withSleepWhenRetry(int)
         */
        public Builder withUpdateRetryCount(int updateRetryCount) {
            this.updateRetryCount = updateRetryCount;
            return this;
        }

        /**
         * @param threadCount # of threads assigned to the workers. Should be at-least the size of taskWorkers to avoid
         *                    starvation in a busy system.
         * @return Builder instance
         */
        public Builder withThreadCount(int threadCount) {
            if (threadCount < 1) {
                throw new IllegalArgumentException("No. of threads cannot be less than 1");
            }
            this.threadCount = threadCount;
            return this;
        }

        /**
         * @param eurekaClient Eureka client - used to identify if the server is in discovery or not.  When the server
         *                     goes out of discovery, the polling is terminated. If passed null, discovery check is not
         *                     done.
         * @return Builder instance
         */
        public Builder withEurekaClient(EurekaClient eurekaClient) {
            this.eurekaClient = eurekaClient;
            return this;
        }

        public Builder withTaskToDomain(Map<String, String> taskToDomain) {
            this.taskToDomain = taskToDomain;
            return this;
        }

        /**
         * Builds an instance of the TaskRunnerConfigurer.
         * <p>
         * Please see {@link TaskRunnerConfigurer#init()} method. The method must be called after this constructor for
         * the polling to start.
         * </p>
         */
        public TaskRunnerConfigurer build() {
            return new TaskRunnerConfigurer(this);
        }
    }

    /**
     * @return Thread Count for the executor pool
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * @return sleep time in millisecond before task update retry is done when receiving error from the Conductor server
     */
    public int getSleepWhenRetry() {
        return sleepWhenRetry;
    }

    /**
     * @return Number of times updateTask should be retried when receiving error from Conductor server
     */
    public int getUpdateRetryCount() {
        return updateRetryCount;
    }

    /**
     * @return prefix used for worker names
     */
    public String getWorkerNamePrefix() {
        return workerNamePrefix;
    }

    /**
     * Starts the polling. Must be called after {@link TaskRunnerConfigurer.Builder#build()} method.
     */
    public synchronized void init() {
        MetricsContainer.incrementInitializationCount(this.getClass().getCanonicalName());
        this.taskPollExecutor = new TaskPollExecutor(eurekaClient, taskClient, threadCount,
            updateRetryCount, taskToDomain, workerNamePrefix);

        this.scheduledExecutorService = Executors.newScheduledThreadPool(workers.size());
        workers.forEach(
            worker -> scheduledExecutorService.scheduleWithFixedDelay(() -> taskPollExecutor.pollAndExecute(worker),
                worker.getPollingInterval(), worker.getPollingInterval(), TimeUnit.MILLISECONDS));
    }

    /**
     * Invoke this method within a PreDestroy block within your application to facilitate a graceful shutdown of your
     * worker, during process termination.
     */
    public void shutdown() {
        taskPollExecutor.shutdownExecutorService(scheduledExecutorService);
    }
}
