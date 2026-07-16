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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * A test-only async {@link WorkflowSystemTask} whose {@link #start} blocks under external control
 * (via latches) and counts how many times it is invoked. It reproduces the mechanism behind issues
 * #1321 (async system task running longer than the queue unack window executed twice) and #1322
 * (late completion of a duplicate attempt overwriting the terminal task record).
 *
 * <p>It is {@code isAsync()==true} and {@code isAsyncComplete()==false}, exactly matching the class
 * of tasks (e.g. an agent-loop LLM chat-complete turn) affected by the reported bugs: the executor
 * runs {@link #start} synchronously while the task stays SCHEDULED in the store.
 */
@Component(ControllableSystemTask.NAME)
public class ControllableSystemTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ControllableSystemTask.class);

    public static final String NAME = "CONTROLLABLE_SYS_TASK";

    /** Total number of times {@link #start} has been entered since the last {@link #reset}. */
    public final AtomicInteger startInvocations = new AtomicInteger(0);

    /** Counted down each time a {@link #start} invocation is entered (before it blocks). */
    public volatile CountDownLatch enteredStart;

    /** #1321 mode: every {@link #start} invocation blocks on this single latch. */
    public volatile CountDownLatch release;

    /** #1322 mode: when true, invocation N completes only after {@code releaseAttemptN} fires. */
    public volatile boolean orderedMode = false;

    public volatile CountDownLatch releaseAttempt1;
    public volatile CountDownLatch releaseAttempt2;

    /** Wall-clock time at which each attempt's {@link #start} set the task terminal. */
    public volatile long attempt1EndTime;

    public volatile long attempt2EndTime;

    public ControllableSystemTask() {
        super(NAME);
        LOGGER.info("Initialized test system task - {}", NAME);
    }

    /** Reset all control state; call from a spec's setup() before driving a scenario. */
    public void reset() {
        startInvocations.set(0);
        enteredStart = null;
        release = null;
        orderedMode = false;
        releaseAttempt1 = null;
        releaseAttempt2 = null;
        attempt1EndTime = 0;
        attempt2EndTime = 0;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        int attempt = startInvocations.incrementAndGet();
        LOGGER.info(
                "ControllableSystemTask.start invocation #{} for task {}",
                attempt,
                task.getTaskId());
        if (enteredStart != null) {
            enteredStart.countDown();
        }
        try {
            if (orderedMode) {
                // #1322: two concurrent attempts (two workers that both popped the SCHEDULED task).
                // Attempt 1 finishes first (completing the task + workflow); attempt 2 finishes
                // LATE, and its terminal write must not overwrite attempt 1's record.
                if (attempt == 1) {
                    await(releaseAttempt1);
                    Map<String, Object> output = new HashMap<>();
                    output.put("attempt", 1);
                    output.put("marker", "FIRST");
                    task.setOutputData(output);
                    attempt1EndTime = System.currentTimeMillis();
                } else {
                    await(releaseAttempt2);
                    Map<String, Object> output = new HashMap<>();
                    output.put("attempt", attempt);
                    output.put("marker", "SECOND");
                    task.setOutputData(output);
                    attempt2EndTime = System.currentTimeMillis();
                }
                task.setStatus(TaskModel.Status.COMPLETED);
            } else {
                // #1321: block for the duration of "execution", representing a long-running
                // system-task operation (e.g. an LLM provider call) that outlasts the unack window.
                await(release);
                Map<String, Object> output = new HashMap<>();
                output.put("attempt", attempt);
                task.setOutputData(output);
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        if (latch != null) {
            // Bounded to keep the test fast even if something goes wrong.
            latch.await(30, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
