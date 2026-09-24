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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
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
import static org.mockito.Mockito.when;

/**
 * Isolated queues and {@code taskWorkerConfigs} dedicated pools have their own threads and their
 * own permits (permits == threads). They must keep admitting up to their own thread count
 * regardless of the shared pool's load, and must not eat into the shared pool's capacity.
 *
 * <p>Pool sizes are deliberately distinct (shared 4, isolated 3, dedicated 2) so a queue wired to
 * the wrong pool or semaphore shows up as a wrong count.
 *
 * @see <a href="https://github.com/conductor-oss/conductor/issues/1649">#1649</a>
 */
class SystemTaskWorkerOwnPoolAdmissionTest {

    private static final int SHARED_THREADS = 4;
    private static final int ISOLATED_THREADS = 3;
    private static final int DEDICATED_THREADS = 2;

    // systemTaskMaxPollCount: max messages a single pollAndExecute call asks the queue for.
    private static final int MAX_POLL_COUNT = 2;

    // Rounds needed to fill the largest pool, plus extra rounds that must all be refused.
    private static final int ROUNDS_TO_FILL = Math.ceilDiv(SHARED_THREADS, MAX_POLL_COUNT);
    private static final int EXTRA_ROUNDS = 2;
    private static final int POLL_ROUNDS = ROUNDS_TO_FILL + EXTRA_ROUNDS;

    private static final String SHARED_QUEUE = "JOIN";
    private static final String ISOLATED_QUEUE = "EVENT-iso";
    private static final String DEDICATED_QUEUE = "HTTP";

    // Task ids are "<queue>#<n>" so the mocked executor can tell which queue a task came from.
    private static final String ID_SEPARATOR = "#";

    // Gate that keeps started tasks on their threads until tearDown.
    private final CountDownLatch release = new CountDownLatch(1);

    // One latch per queue, opens once that queue's pool has all its threads busy.
    private final Map<String, CountDownLatch> started =
            Map.of(
                    SHARED_QUEUE, new CountDownLatch(SHARED_THREADS),
                    ISOLATED_QUEUE, new CountDownLatch(ISOLATED_THREADS),
                    DEDICATED_QUEUE, new CountDownLatch(DEDICATED_THREADS));

    // Messages handed out by the queue, per queue name.
    private final Map<String, AtomicInteger> popped = new ConcurrentHashMap<>();

    private SystemTaskWorker systemTaskWorker;

    @BeforeEach
    void setUp() {
        systemTaskWorker =
                new SystemTaskWorker(
                        getMockedQueueDAO(),
                        getBlockingAsyncSystemTaskExecutor(),
                        getConductorProperties());
        systemTaskWorker.start();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        systemTaskWorker.stop();
    }

    @Test
    @DisplayName(
            "Isolated queue keeps admitting up to its own thread count while the shared pool is full")
    void isolatedQueue_sharedPoolSaturated_admitsUpToOwnThreadCount() throws Exception {
        saturate(SHARED_QUEUE);
        pollRepeatedly(ISOLATED_QUEUE);
        assertFullyAdmitted(ISOLATED_QUEUE);
    }

    @Test
    @DisplayName(
            "Dedicated pool (taskWorkerConfigs) keeps admitting up to its own thread count while the shared pool is full")
    void dedicatedPool_sharedPoolSaturated_admitsUpToOwnThreadCount() throws Exception {
        saturate(SHARED_QUEUE);
        pollRepeatedly(DEDICATED_QUEUE);
        assertFullyAdmitted(DEDICATED_QUEUE);
    }

    @Test
    @DisplayName("A full isolated queue must NOT consume shared pool capacity")
    void sharedQueue_isolatedQueueSaturated_admitsUpToSharedThreadCount() throws Exception {
        saturate(ISOLATED_QUEUE);
        pollRepeatedly(SHARED_QUEUE);
        assertFullyAdmitted(SHARED_QUEUE);
    }

    @Test
    @DisplayName("A full dedicated pool (taskWorkerConfigs) must NOT consume shared pool capacity")
    void sharedQueue_dedicatedPoolSaturated_admitsUpToSharedThreadCount() throws Exception {
        saturate(DEDICATED_QUEUE);
        pollRepeatedly(SHARED_QUEUE);
        assertFullyAdmitted(SHARED_QUEUE);
    }

    /** Fills the queue's pool: every thread busy, every permit taken. */
    private void saturate(String queue) throws InterruptedException {
        pollRepeatedly(queue);
        assertFullyAdmitted(queue);
    }

    /**
     * The queue admitted exactly its pool width: that many permits taken, that many messages
     * popped, and all its threads busy.
     */
    private void assertFullyAdmitted(String queue) throws InterruptedException {
        assertEquals(poolWidth(queue), admitted(queue), queue + " permits taken");
        assertEquals(poolWidth(queue), popped(queue), queue + " messages popped");
        assertAllThreadsBusy(queue);
    }

    /** Polls more times than needed to fill any pool; see {@link #POLL_ROUNDS}. */
    private void pollRepeatedly(String queue) {
        for (int round = 0; round < POLL_ROUNDS; round++) {
            systemTaskWorker.pollAndExecute(new TestSystemTaskWorker.TestTask(), queue);
        }
    }

    private int admitted(String queue) {
        return poolWidth(queue)
                - systemTaskWorker.getExecutionConfig(queue).getSemaphoreUtil().availableSlots();
    }

    private int popped(String queue) {
        return popped.getOrDefault(queue, new AtomicInteger()).get();
    }

    private void assertAllThreadsBusy(String queue) throws InterruptedException {
        assertTrue(
                started.get(queue).await(5, TimeUnit.SECONDS),
                queue + " should have all " + poolWidth(queue) + " threads busy");
    }

    private static int poolWidth(String queue) {
        return switch (queue) {
            case SHARED_QUEUE -> SHARED_THREADS;
            case ISOLATED_QUEUE -> ISOLATED_THREADS;
            case DEDICATED_QUEUE -> DEDICATED_THREADS;
            default -> throw new IllegalArgumentException(queue);
        };
    }

    private AsyncSystemTaskExecutor getBlockingAsyncSystemTaskExecutor() {
        var asyncSystemTaskExecutor = mock(AsyncSystemTaskExecutor.class);
        doAnswer(
                        inv -> {
                            String taskId = inv.getArgument(1);
                            started.get(StringUtils.substringBefore(taskId, ID_SEPARATOR))
                                    .countDown();
                            release.await();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());
        return asyncSystemTaskExecutor;
    }

    private QueueDAO getMockedQueueDAO() {
        var queueDAO = mock(QueueDAO.class);
        var seq = new AtomicInteger();
        when(queueDAO.pop(anyString(), anyInt(), anyInt()))
                .thenAnswer(
                        inv -> {
                            String queue = inv.getArgument(0);
                            int n = inv.getArgument(1);
                            popped.computeIfAbsent(queue, __ -> new AtomicInteger()).addAndGet(n);
                            return IntStream.range(0, n)
                                    .mapToObj(i -> queue + ID_SEPARATOR + seq.incrementAndGet())
                                    .toList();
                        });
        return queueDAO;
    }

    private static ConductorProperties getConductorProperties() {
        var dedicated = new ConductorProperties.TaskWorkerConfig();
        dedicated.setThreadCount(DEDICATED_THREADS);

        var p = mock(ConductorProperties.class);
        when(p.getSystemTaskWorkerThreadCount()).thenReturn(SHARED_THREADS);
        when(p.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(ISOLATED_THREADS);
        when(p.getSystemTaskMaxPollCount()).thenReturn(MAX_POLL_COUNT);
        when(p.getSystemTaskWorkerCallbackDuration()).thenReturn(Duration.ofSeconds(1));
        when(p.getSystemTaskWorkerPollInterval()).thenReturn(Duration.ofMillis(10));
        when(p.getSystemTaskQueuePopTimeout()).thenReturn(Duration.ofMillis(1));
        when(p.getTaskWorkerConfigs()).thenReturn(Map.of(DEDICATED_QUEUE, dedicated));
        return p;
    }
}
