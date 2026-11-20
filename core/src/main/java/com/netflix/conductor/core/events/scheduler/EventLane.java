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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;

/**
 * Per-event scheduling state for the adaptive scheduler. One lane per registered event string.
 *
 * <p>Holds:
 *
 * <ul>
 *   <li>a bounded in-process buffer that decouples polling from dispatch and lets the poller apply
 *       backpressure when the consumer side cannot keep up;
 *   <li>a Vegas-style {@link Limiter} that auto-tunes the per-lane in-flight ceiling using Little's
 *       Law (arrival rate × observed latency) — no operator input;
 *   <li>a DRR deficit counter, updated each scheduling round by the {@link DrrDispatcher}.
 * </ul>
 *
 * <p>The lane intentionally exposes no setters for {@code weight} because the design point is
 * max-min fairness across lanes (weight ≡ 1) with all elasticity expressed through the limiter.
 * That separation — fairness in the scheduler, capacity in the controller — keeps both loops
 * stable.
 */
public final class EventLane {

    /** Default per-lane buffer capacity. Sized to absorb a few poll batches without blocking. */
    public static final int DEFAULT_BUFFER_CAPACITY = 1024;

    /** Initial Vegas limit. AIMD ramps this up from 2 within the first few seconds of traffic. */
    public static final int INITIAL_LIMIT = 2;

    /** Hard ceiling on the per-lane limit, keeps a single hot lane from owning the whole pool. */
    public static final int MAX_LIMIT = 256;

    private final String event;
    private final ObservableQueue queue;
    private final ArrayBlockingQueue<Message> buffer;
    private final SimpleLimiter<Void> limiter;

    private int deficit;

    // Observability counters. Read by metrics exporters; written only by the dispatcher thread
    // (deficit) or by atomic increment from worker threads (the counters below).
    private final AtomicLong dispatchedTotal = new AtomicLong();
    private final AtomicLong droppedTotal = new AtomicLong();
    private final AtomicLong pollSkippedBackpressureTotal = new AtomicLong();

    public EventLane(String event, ObservableQueue queue, int bufferCapacity) {
        this.event = event;
        this.queue = queue;
        this.buffer = new ArrayBlockingQueue<>(bufferCapacity);
        // VegasLimit: AIMD with latency-RTT comparison. Self-tunes within ~10s of steady load.
        // Bounds are guard-rails on top of Vegas, not replacements for it.
        this.limiter =
                SimpleLimiter.newBuilder()
                        .limit(
                                VegasLimit.newBuilder()
                                        .initialLimit(INITIAL_LIMIT)
                                        .maxConcurrency(MAX_LIMIT)
                                        .build())
                        .build();
    }

    public EventLane(String event, ObservableQueue queue) {
        this(event, queue, DEFAULT_BUFFER_CAPACITY);
    }

    public String event() {
        return event;
    }

    public ObservableQueue queue() {
        return queue;
    }

    public ArrayBlockingQueue<Message> buffer() {
        return buffer;
    }

    public Limiter<Void> limiter() {
        return limiter;
    }

    /** DRR deficit accessor. Only the dispatcher thread mutates this. */
    public int deficit() {
        return deficit;
    }

    public void addDeficit(int quantum) {
        this.deficit += quantum;
    }

    public void consumeDeficit() {
        this.deficit--;
    }

    /** Reset deficit when the lane is idle, so it cannot bank unbounded credit across rounds. */
    public void resetDeficit() {
        this.deficit = 0;
    }

    /** Current Vegas concurrency limit. Exposed for metrics + tests. */
    public int currentLimit() {
        return limiter.getLimit();
    }

    public int currentInFlight() {
        return limiter.getInflight();
    }

    public long dispatchedTotal() {
        return dispatchedTotal.get();
    }

    public long droppedTotal() {
        return droppedTotal.get();
    }

    public long pollSkippedBackpressureTotal() {
        return pollSkippedBackpressureTotal.get();
    }

    public void incrementDispatched() {
        dispatchedTotal.incrementAndGet();
    }

    public void incrementDropped() {
        droppedTotal.incrementAndGet();
    }

    public void incrementPollSkipped() {
        pollSkippedBackpressureTotal.incrementAndGet();
    }
}
