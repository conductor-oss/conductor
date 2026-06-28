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

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import com.netflix.conductor.core.utils.SemaphoreUtil;

class ExecutionConfig {

    private final ExecutorService executorService;
    private final SemaphoreUtil semaphoreUtil;

    /** Dedicated pool size, or -1 when using the shared pool. */
    private final int poolSize;

    /** Isolated queues: own dedicated pool + own semaphore, permits == threadCount. */
    ExecutionConfig(int threadCount, String threadNameFormat) {
        this(newThreadPool(threadCount, threadNameFormat), threadCount, threadCount);
    }

    /** Per-task-type override: own dedicated pool with explicit permit count. */
    ExecutionConfig(int threadCount, String threadNameFormat, int permits) {
        this(newThreadPool(threadCount, threadNameFormat), permits, threadCount);
    }

    /**
     * Non-isolated queues: share the given pool but each gets its own semaphore, so a slow/busy
     * queue cannot exhaust a shared permit pool and starve other queues' polling.
     */
    ExecutionConfig(ExecutorService executorService, int permits) {
        this(executorService, permits, -1);
    }

    /** Test-only: inject a pre-built semaphore (e.g. a mock). */
    ExecutionConfig(ExecutorService executorService, SemaphoreUtil semaphoreUtil) {
        this.executorService = executorService;
        this.semaphoreUtil = semaphoreUtil;
        this.poolSize = -1;
    }

    private ExecutionConfig(ExecutorService executorService, int permits, int poolSize) {
        this.executorService = executorService;
        this.semaphoreUtil = new SemaphoreUtil(permits);
        this.poolSize = poolSize;
    }

    static ExecutorService newThreadPool(int threadCount, String threadNameFormat) {
        return Executors.newFixedThreadPool(
                threadCount,
                new BasicThreadFactory.Builder().namingPattern(threadNameFormat).build());
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public SemaphoreUtil getSemaphoreUtil() {
        return semaphoreUtil;
    }

    /** Dedicated thread-pool size, or -1 when this config uses the shared pool. */
    public int getPoolSize() {
        return poolSize;
    }
}
