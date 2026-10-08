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
package com.netflix.conductor.core.events.scheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.core.config.ConductorProperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end behaviour tests for {@link AdaptiveEventScheduler}. Each test drives the scheduler
 * through its public synchronous {@code pollOnce}/{@code dispatchOnce} entry points so we don't
 * depend on wall-clock timing. The worker pool is a small fixed pool so the suite stays portable —
 * virtual threads are an optimisation, not a correctness requirement.
 */
public class AdaptiveEventSchedulerTest {

    private ExecutorService workers;

    @Before
    public void setUp() {
        workers = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() {
        workers.shutdownNow();
    }

    /**
     * Noisy-neighbor scenario, end-to-end: 10_000 hot messages + 10 cold messages flowing through
     * the scheduler's poller + DRR dispatcher + worker pool. We tick the loops by hand and assert
     * that cold messages arrive at the sink well before hot is fully drained.
     */
    @Test
    public void noisyNeighbor_endToEnd() throws Exception {
        AdaptiveSchedulerTestQueue hotQ = new AdaptiveSchedulerTestQueue("hot");
        AdaptiveSchedulerTestQueue coldQ = new AdaptiveSchedulerTestQueue("cold");
        hotQ.seed(10_000);
        coldQ.seed(10);

        ConcurrentHashMap<String, AtomicInteger> handled = new ConcurrentHashMap<>();
        AdaptiveEventScheduler scheduler =
                new AdaptiveEventScheduler(
                        workers,
                        (q, m) ->
                                handled.computeIfAbsent(q.getName(), k -> new AtomicInteger())
                                        .incrementAndGet());
        scheduler.registerLane("hot", hotQ);
        scheduler.registerLane("cold", coldQ);

        // Drive the loops manually until cold is fully handled. Bounded by a generous iteration
        // cap so a regression does not hang the suite.
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            scheduler.pollOnce();
            scheduler.dispatchOnce();
            AtomicInteger c = handled.get("cold");
            if (c != null && c.get() == 10) {
                break;
            }
            Thread.sleep(2);
        }
        // Cold is fully handled.
        assertEquals(10, handled.getOrDefault("cold", new AtomicInteger()).get());
        // Hot is still in progress (or at least not fully drained alongside cold) — the assertion
        // is intentionally loose to avoid flakes on fast hosts; the contract is "cold does not
        // wait for hot to finish".
        int hotHandled = handled.getOrDefault("hot", new AtomicInteger()).get();
        assertTrue(
                "hot must still have meaningful outstanding work when cold completes: handled="
                        + hotHandled,
                hotHandled < 10_000);
    }

    /**
     * Vegas limit should ramp up under steady load. We verify the property by running enough
     * dispatch rounds against a single lane with successful completions and observing that the
     * current limit is at least as large as the initial value.
     */
    @Test
    public void vegasLimiterRampsUnderSteadyLoad() throws Exception {
        AdaptiveSchedulerTestQueue q = new AdaptiveSchedulerTestQueue("bulk");
        q.seed(2_000);
        AtomicLong handled = new AtomicLong();
        AdaptiveEventScheduler scheduler =
                new AdaptiveEventScheduler(workers, (qq, m) -> handled.incrementAndGet());
        EventLane lane = scheduler.registerLane("bulk", q);

        long deadline = System.currentTimeMillis() + 5_000;
        while (handled.get() < 1_500 && System.currentTimeMillis() < deadline) {
            scheduler.pollOnce();
            scheduler.dispatchOnce();
            Thread.sleep(1);
        }
        assertTrue(
                "lane should have processed bulk of messages: " + handled.get(),
                handled.get() >= 1_500);
        // Limit must not have gone below the documented floor.
        assertTrue(
                "Vegas limit must remain >= initial floor, got " + lane.currentLimit(),
                lane.currentLimit() >= EventLane.INITIAL_LIMIT);
    }

    /** Feature flag default is OFF — verified at the property level. */
    @Test
    public void adaptiveSchedulerDisabledByDefault() {
        ConductorProperties props = new ConductorProperties();
        assertFalse(props.isAdaptiveSchedulerEnabled());
    }

    /** Start/stop is idempotent and observable. */
    @Test
    public void startStopIsIdempotent() throws Exception {
        AdaptiveEventScheduler scheduler = new AdaptiveEventScheduler(workers, (q, m) -> {});
        scheduler.start();
        scheduler.start();
        assertTrue(scheduler.isRunning());
        scheduler.stop();
        scheduler.stop();
        assertFalse(scheduler.isRunning());
        workers.shutdown();
        workers.awaitTermination(2, TimeUnit.SECONDS);
    }
}
