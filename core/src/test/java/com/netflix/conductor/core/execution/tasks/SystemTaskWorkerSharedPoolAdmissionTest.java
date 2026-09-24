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
package com.netflix.conductor.core.execution.tasks;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.dao.QueueDAO;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemTaskWorkerSharedPoolAdmissionTest {

    private static final int SHARED_THREADS = 4;

    // systemTaskMaxPollCount: max messages a single pollAndExecute call asks the queue for.
    private static final int MAX_POLL_COUNT = 2;

    // Rounds needed to fill the largest pool, plus extra rounds that must all be refused.
    private static final int ROUNDS_TO_FILL = Math.ceilDiv(SHARED_THREADS, MAX_POLL_COUNT);
    private static final int EXTRA_ROUNDS = 2;
    private static final int POLL_ROUNDS = ROUNDS_TO_FILL + EXTRA_ROUNDS;

    /**
     * Task types on the shared pool must not admit, in aggregate, more tasks than the pool has
     * threads. Each type currently gets its own semaphore sized to the pool width, so K active
     * types admit up to K × threads tasks for only {@code threads} threads.
     *
     * @see <a href="https://github.com/conductor-oss/conductor/issues/1649">#1649</a>
     */
    @Test
    @DisplayName(
            "Shared pool MUST NOT admit more tasks than the number of threads in the pool, across all task types")
    void sharedPoolAdmissionIsBoundedByThreadCount() throws Exception {
        var release =
                new CountDownLatch(
                        1); // gate that keeps started tasks on their threads until the end
        var started = new CountDownLatch(SHARED_THREADS); // opens once all shared threads are busy

        var asyncSystemTaskExecutor = mock(AsyncSystemTaskExecutor.class);
        doAnswer(
                        inv -> {
                            started.countDown();
                            release.await();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());

        var popped = new AtomicInteger(); // messages handed out by the queue
        var systemTaskWorker =
                new SystemTaskWorker(
                        getMockedQueueDAO(popped),
                        asyncSystemTaskExecutor,
                        getConductorProperties());
        systemTaskWorker.start();

        String[] queues = {"JOIN", "START_WORKFLOW", "SUB_WORKFLOW", "EVENT"};
        for (int round = 0; round < POLL_ROUNDS; round++) {
            for (String q : queues) {
                systemTaskWorker.pollAndExecute(new TestSystemTaskWorker.TestTask(), q);
            }
        }

        assertTrue(started.await(5, TimeUnit.SECONDS), SHARED_THREADS + " tasks should be running");
        int admitted = 0;
        for (String q : queues) {
            admitted +=
                    SHARED_THREADS
                            - systemTaskWorker
                                    .getExecutionConfig(q)
                                    .getSemaphoreUtil()
                                    .availableSlots();
        }
        release.countDown();
        systemTaskWorker.stop();

        int permitsTaken = admitted;
        assertAll(
                () ->
                        assertTrue(
                                permitsTaken <= SHARED_THREADS,
                                "Shared-pool queues took "
                                        + permitsTaken
                                        + " permits for "
                                        + SHARED_THREADS
                                        + " threads"),
                // What the queue saw: extra polls must not pop anything once the pool is full
                () ->
                        assertEquals(
                                SHARED_THREADS,
                                popped.get(),
                                "Shared-pool queues popped "
                                        + popped.get()
                                        + " tasks for "
                                        + SHARED_THREADS
                                        + " threads"));
    }

    private QueueDAO getMockedQueueDAO(AtomicInteger seq) {
        var queueDAO = mock(QueueDAO.class);
        when(queueDAO.pop(anyString(), anyInt(), anyInt()))
                .thenAnswer(
                        inv -> {
                            int n = inv.getArgument(1);
                            return IntStream.range(0, n)
                                    .mapToObj(i -> "mocked_task_id_" + seq.incrementAndGet())
                                    .toList();
                        });
        return queueDAO;
    }

    private static ConductorProperties getConductorProperties() {
        ConductorProperties p = mock(ConductorProperties.class);
        when(p.getSystemTaskWorkerThreadCount()).thenReturn(SHARED_THREADS);
        when(p.getSystemTaskMaxPollCount()).thenReturn(MAX_POLL_COUNT);
        when(p.getSystemTaskWorkerCallbackDuration()).thenReturn(Duration.ofSeconds(1));
        when(p.getSystemTaskWorkerPollInterval()).thenReturn(Duration.ofMillis(10));
        when(p.getSystemTaskQueuePopTimeout()).thenReturn(Duration.ofMillis(1));
        when(p.getTaskWorkerConfigs()).thenReturn(new HashMap<>());
        return p;
    }
}
