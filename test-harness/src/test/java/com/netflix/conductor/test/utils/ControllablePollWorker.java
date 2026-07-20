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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

/**
 * A controllable blocking annotated worker for redelivery/duplicate-execution specs. It stands in
 * for a long-running provider call (e.g. an LLM chat completion) and supports the multi-turn
 * pattern (IN_PROGRESS + callbackAfterSeconds) used by long-running LLM/A2A workers.
 *
 * <p>Registered in the shared test context; inert unless a spec arms it via {@link #reset()}.
 */
@Component
public class ControllablePollWorker implements AnnotatedSystemTaskWorker {

    public static final String TASK_TYPE = "CONTROLLABLE_POLL_TASK";

    /** Total number of times {@link #run} has been entered since the last {@link #reset}. */
    public final AtomicInteger invocations = new AtomicInteger(0);

    /** Thread name per invocation, for failure diagnostics. */
    public final List<String> invocationLog = new CopyOnWriteArrayList<>();

    /**
     * Attempts less than or equal to this return IN_PROGRESS + callbackAfterSeconds instead of
     * completing, simulating a multi-turn worker (same pattern as LLMWorkers.generateVideo).
     */
    public volatile int inProgressTurns = 0;

    /** Callback the in-progress turns request before re-invocation. */
    public volatile long callbackAfterSeconds = 1;

    /** Attempts greater than or equal to this block on the release latch. */
    public volatile int blockFromAttempt = 1;

    /** Counted down each time {@link #run} is entered (before it blocks). */
    public volatile CountDownLatch enteredRun = new CountDownLatch(1);

    /** Blocking attempts wait on this latch, simulating the provider call. */
    public volatile CountDownLatch release = new CountDownLatch(1);

    /** Resets all control state; call from a spec's setup() before driving a scenario. */
    public void reset() {
        invocations.set(0);
        invocationLog.clear();
        inProgressTurns = 0;
        callbackAfterSeconds = 1;
        blockFromAttempt = 1;
        enteredRun = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    @WorkerTask(value = TASK_TYPE, pollerCount = 2, leaseExtendEnabled = true)
    public TaskResult run() throws InterruptedException {
        int attempt = invocations.incrementAndGet();
        invocationLog.add(
                "attempt "
                        + attempt
                        + " on "
                        + Thread.currentThread().getName()
                        + " at "
                        + System.currentTimeMillis());
        enteredRun.countDown();

        if (attempt >= blockFromAttempt) {
            if (!release.await(30, TimeUnit.SECONDS)) {
                // Fail the task loudly rather than completing and masking a hung spec.
                throw new IllegalStateException(
                        "ControllablePollWorker was never released (attempt " + attempt + ")");
            }
        }

        TaskResult result = new TaskResult(TaskContext.get().getTask());
        result.getOutputData().put("attempt", attempt);
        if (attempt <= inProgressTurns) {
            result.setStatus(TaskResult.Status.IN_PROGRESS);
            result.setCallbackAfterSeconds(callbackAfterSeconds);
        } else {
            result.setStatus(TaskResult.Status.COMPLETED);
        }
        return result;
    }
}
