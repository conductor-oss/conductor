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
package com.netflix.conductor.test.utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * Async system task whose {@code start()} blocks until the test releases it, and which counts how
 * many times it was invoked.
 *
 * <p>A blocking {@code start()} is what makes duplicate async system-task execution observable: the
 * task stays SCHEDULED in the store for the whole invocation, so anything that re-delivers its
 * queue message during that window causes a second, concurrent invocation under the same task id.
 */
@Component(BlockingSystemTask.NAME)
public class BlockingSystemTask extends WorkflowSystemTask {

    public static final String NAME = "BLOCKING_SYSTEM_TASK";

    private static final AtomicInteger INVOCATIONS = new AtomicInteger();
    private static volatile CountDownLatch entered = new CountDownLatch(1);
    private static volatile CountDownLatch release = new CountDownLatch(1);

    public BlockingSystemTask() {
        super(NAME);
    }

    /** Must be called from the test's setup, before the workflow is started. */
    public static void reset() {
        INVOCATIONS.set(0);
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    public static int invocationCount() {
        return INVOCATIONS.get();
    }

    /** Blocks until {@code start()} has been entered at least once. */
    public static boolean awaitFirstInvocation(long timeoutSeconds) throws InterruptedException {
        return entered.await(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** Unblocks every in-flight invocation. Safe to call more than once. */
    public static void release() {
        release.countDown();
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        INVOCATIONS.incrementAndGet();
        entered.countDown();
        try {
            release.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        task.setStatus(TaskModel.Status.COMPLETED);
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
