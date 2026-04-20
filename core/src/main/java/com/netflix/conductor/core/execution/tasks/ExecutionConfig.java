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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.utils.SemaphoreUtil;

class ExecutionConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionConfig.class);

    private final ExecutorService executorService;
    private final SemaphoreUtil semaphoreUtil;
    private final String name;

    ExecutionConfig(int threadCount, String threadNameFormat) {
        this.name = threadNameFormat;
        this.executorService =
                Executors.newFixedThreadPool(
                        threadCount,
                        new BasicThreadFactory.Builder().namingPattern(threadNameFormat).build());

        this.semaphoreUtil = new SemaphoreUtil(threadCount);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public SemaphoreUtil getSemaphoreUtil() {
        return semaphoreUtil;
    }

    /**
     * Gracefully shuts down the executor service, waiting for in-flight tasks to complete.
     *
     * @param timeoutSeconds maximum time to wait for tasks to complete
     */
    public void shutdown(long timeoutSeconds) {
        LOGGER.info("Shutting down executor service: {}", name);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                LOGGER.warn(
                        "Executor service {} did not terminate in {} seconds, forcing shutdown",
                        name,
                        timeoutSeconds);
                executorService.shutdownNow();
            } else {
                LOGGER.info("Executor service {} shutdown completed gracefully", name);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for executor service {} to terminate", name);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
