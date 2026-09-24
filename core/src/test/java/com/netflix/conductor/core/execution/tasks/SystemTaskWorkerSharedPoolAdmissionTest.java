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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.dao.QueueDAO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task types on the shared pool must not admit, in aggregate, more tasks than the pool has threads,
 * however many types are active and however their pollers interleave.
 *
 * @see <a href="https://github.com/conductor-oss/conductor/issues/1649">#1649</a>
 */
class SystemTaskWorkerSharedPoolAdmissionTest {

    private static final int SHARED_THREADS = 4;

    // systemTaskMaxPollCount: max messages a single pollAndExecute call asks the queue for.
    private static final int MAX_POLL_COUNT = 2;

    // Rounds needed to fill the largest pool, plus extra rounds that must all be refused.
    private static final int ROUNDS_TO_FILL = Math.ceilDiv(SHARED_THREADS, MAX_POLL_COUNT);
    private static final int EXTRA_ROUNDS = 2;
    private static final int POLL_ROUNDS = ROUNDS_TO_FILL + EXTRA_ROUNDS;

    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    // Asserting that pollers do NOT over-pop needs a window: many poll intervals.
    private static final Duration OBSERVATION_WINDOW = POLL_INTERVAL.multipliedBy(50);

    private static final List<String> SHARED_QUEUES =
            List.of("JOIN", "START_WORKFLOW", "SUB_WORKFLOW", "EVENT");

    // Gate that keeps started tasks on their threads until tearDown.
    private final CountDownLatch release = new CountDownLatch(1);

    // Opens once all shared threads are busy.
    private final CountDownLatch started = new CountDownLatch(SHARED_THREADS);

    // Messages handed out by the queue, across all shared queues.
    private final AtomicInteger popped = new AtomicInteger();

    private QueueDAO queueDAO;
    private SystemTaskWorker systemTaskWorker;

    @BeforeEach
    void setUp() {
        queueDAO = getMockedQueueDAO();
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, getBlockingAsyncSystemTaskExecutor(), getConductorProperties());
        systemTaskWorker.start();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        systemTaskWorker.stop();
    }

    @Test
    @DisplayName(
            "Shared pool MUST NOT admit more tasks than the number of threads in the pool, across all task types")
    void sharedPoolAdmissionIsBoundedByThreadCount() throws Exception {
        for (int round = 0; round < POLL_ROUNDS; round++) {
            for (String queue : SHARED_QUEUES) {
                systemTaskWorker.pollAndExecute(new TestSystemTaskWorker.TestTask(), queue);
            }
        }

        assertAllThreadsBusy();
        assertPoppedExactlyThreadCount();
    }

    @Test
    @DisplayName(
            "Shared pool: concurrent pollers MUST NOT pop more tasks than the number of threads in the pool")
    void concurrentPollers_sharedPool_popAtMostThreadCount() throws Exception {
        SHARED_QUEUES.forEach(
                queue -> systemTaskWorker.startPolling(new TestSystemTaskWorker.TestTask(), queue));

        assertAllThreadsBusy();
        Thread.sleep(OBSERVATION_WINDOW.toMillis());

        assertPoppedExactlyThreadCount();
        // A poller that pops without holding permits has to hand the messages back.
        verify(queueDAO, never()).resetOffsetTime(anyString(), anyString());
    }

    private void assertAllThreadsBusy() throws InterruptedException {
        assertTrue(
                started.await(5, TimeUnit.SECONDS),
                "All " + SHARED_THREADS + " shared threads should be busy");
    }

    private void assertPoppedExactlyThreadCount() {
        assertEquals(
                SHARED_THREADS,
                popped.get(),
                "Shared-pool queues popped "
                        + popped.get()
                        + " tasks for "
                        + SHARED_THREADS
                        + " threads");
    }

    private AsyncSystemTaskExecutor getBlockingAsyncSystemTaskExecutor() {
        var asyncSystemTaskExecutor = mock(AsyncSystemTaskExecutor.class);
        doAnswer(
                        inv -> {
                            started.countDown();
                            release.await();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());
        return asyncSystemTaskExecutor;
    }

    private QueueDAO getMockedQueueDAO() {
        var queueDAO = mock(QueueDAO.class);
        when(queueDAO.pop(anyString(), anyInt(), anyInt()))
                .thenAnswer(
                        inv -> {
                            int n = inv.getArgument(1);
                            return IntStream.range(0, n)
                                    .mapToObj(i -> "mocked_task_id_" + popped.incrementAndGet())
                                    .toList();
                        });
        return queueDAO;
    }

    private static ConductorProperties getConductorProperties() {
        var p = mock(ConductorProperties.class);
        when(p.getSystemTaskWorkerThreadCount()).thenReturn(SHARED_THREADS);
        when(p.getSystemTaskMaxPollCount()).thenReturn(MAX_POLL_COUNT);
        when(p.getSystemTaskWorkerCallbackDuration()).thenReturn(Duration.ofSeconds(1));
        when(p.getSystemTaskWorkerPollInterval()).thenReturn(POLL_INTERVAL);
        when(p.getSystemTaskQueuePopTimeout()).thenReturn(Duration.ofMillis(1));
        when(p.getTaskWorkerConfigs()).thenReturn(new HashMap<>());
        return p;
    }
}
