/*
 * Copyright 2025 Conductor Authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

/**
 * A test-only annotated system-task worker whose {@link #run} blocks under external control (via
 * latches) and counts how many times it is invoked. It stands in for a long-running provider call
 * (e.g. an agent-loop LLM_CHAT_COMPLETE turn) and is registered through the real
 * WorkerTaskAnnotationScanner / AnnotatedWorkflowSystemTask production path, reproducing the
 * mechanism behind issue #1321 (async system task running longer than the queue unack window
 * executed twice).
 */
@Component
public class ControllableWorker implements AnnotatedSystemTaskWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ControllableWorker.class);

    public static final String TASK_TYPE = "CONTROLLABLE_SYS_TASK";

    /** Total number of times {@link #run} has been entered since the last {@link #reset}. */
    public final AtomicInteger invocations = new AtomicInteger(0);

    /** Counted down each time a {@link #run} invocation is entered (before it blocks). */
    public volatile CountDownLatch enteredRun;

    /** Every {@link #run} invocation blocks on this latch, simulating the provider call. */
    public volatile CountDownLatch release;

    /** When positive, the first invocation requests another callback after this many seconds. */
    public volatile int firstCallbackAfterSeconds;

    /** Reset all control state; call from a spec's setup() before driving a scenario. */
    public void reset() {
        invocations.set(0);
        enteredRun = null;
        release = null;
        firstCallbackAfterSeconds = 0;
    }

    @WorkerTask(TASK_TYPE)
    public Map<String, Object> run() throws InterruptedException {
        int attempt = invocations.incrementAndGet();
        LOGGER.info("ControllableWorker.run invocation #{}", attempt);
        if (enteredRun != null) {
            enteredRun.countDown();
        }
        if (release != null) {
            // Bounded to keep the test fast even if something goes wrong.
            release.await(30, TimeUnit.SECONDS);
        }
        if (attempt == 1 && firstCallbackAfterSeconds > 0) {
            TaskContext.get().getTaskResult().setStatus(TaskResult.Status.IN_PROGRESS);
            TaskContext.get().setCallbackAfter(firstCallbackAfterSeconds);
        }
        Map<String, Object> output = new HashMap<>();
        output.put("attempt", attempt);
        return output;
    }
}
