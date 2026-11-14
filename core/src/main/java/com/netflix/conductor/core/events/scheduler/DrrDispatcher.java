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

import java.util.Collection;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.conductor.core.events.queue.Message;

/**
 * Deficit Round Robin dispatcher over a set of {@link EventLane}s.
 *
 * <p>Standard DRR: each scheduling round visits every active lane once and adds a fixed {@code
 * quantum} to its deficit. Inside the visit the lane is drained while (a) it has buffered messages,
 * (b) its deficit is positive, and (c) the per-lane Vegas {@link Limiter} grants an in-flight slot.
 * Each successful dispatch decrements the deficit by 1 (we use a unit cost model — one message ≈
 * one unit of work — because the Vegas limiter, not DRR, accounts for service latency).
 *
 * <p>The algorithm has three properties we rely on:
 *
 * <ul>
 *   <li><b>Max-min fairness with O(1) amortized cost</b>: starving lanes accrue deficit across
 *       rounds, so as soon as their limiter or buffer permits they get to spend it;
 *   <li><b>No head-of-line blocking</b>: a hot lane that exhausts its limiter is skipped within a
 *       round; the dispatcher does not wait for it;
 *   <li><b>No starvation under skew</b>: a cold lane with 10 messages will be visited every round
 *       just like the 10k-message hot lane, so its messages exit the buffer within one round.
 * </ul>
 *
 * <p>This class is intentionally stateless across rounds beyond what each lane holds; callers
 * provide the lane collection and a dispatch sink. Single-threaded use: the {@code dispatch} method
 * must be invoked from one thread (the scheduler's dispatcher loop). The lane's deficit and the
 * limiter's in-flight counter are the only shared state read here.
 */
public final class DrrDispatcher {

    /**
     * Per-round credit added to each lane. With quantum=1 we get the simplest correct DRR: every
     * round, every lane gets one dispatch slot (subject to limiter and buffer). Higher quanta trade
     * fairness granularity for fewer round-trips. We keep the quantum tunable for tests but never
     * exposed as user configuration.
     */
    private final int quantum;

    public DrrDispatcher() {
        this(1);
    }

    public DrrDispatcher(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("DRR quantum must be > 0, got " + quantum);
        }
        this.quantum = quantum;
    }

    /**
     * Execute one DRR round over {@code lanes} and return the number of messages dispatched.
     *
     * <p>For every lane that has buffered messages this round adds {@code quantum} credit, then
     * pops messages while credit and a limiter permit are both available. The {@code sink} receives
     * the lane and its message; it is the sink's responsibility to schedule the actual work
     * asynchronously (e.g. submit to a worker pool) and release the limiter listener when the work
     * completes. This decoupling keeps the dispatcher loop tight: it never blocks on downstream
     * execution.
     */
    public int dispatch(Collection<EventLane> lanes, BiConsumer<EventLane, DispatchTicket> sink) {
        int dispatched = 0;
        for (EventLane lane : lanes) {
            // DRR convention: lanes with an empty buffer reset to zero deficit so they cannot
            // accumulate unbounded credit while idle. This prevents a long-idle lane from
            // monopolising the dispatcher for many rounds the moment a single message arrives.
            if (lane.buffer().isEmpty()) {
                lane.resetDeficit();
                continue;
            }
            lane.addDeficit(quantum);
            while (lane.deficit() > 0 && !lane.buffer().isEmpty()) {
                Optional<Limiter.Listener> listener = lane.limiter().acquire(null);
                if (!listener.isPresent()) {
                    // Vegas says this lane is at its current ceiling — break out of this lane and
                    // visit the next one. The remaining deficit carries to the next round.
                    break;
                }
                Message msg = lane.buffer().poll();
                if (msg == null) {
                    // Race: another consumer drained the lane between our check and poll. Release
                    // the permit we just acquired and move on.
                    listener.get().onIgnore();
                    break;
                }
                lane.consumeDeficit();
                lane.incrementDispatched();
                sink.accept(lane, new DispatchTicket(msg, listener.get()));
                dispatched++;
            }
        }
        return dispatched;
    }

    /**
     * Hands a popped message to the worker pool together with the limiter listener that must be
     * notified when the work finishes. The sink (and only the sink) is responsible for invoking
     * exactly one of {@code onSuccess}, {@code onDropped}, or {@code onIgnore} on the listener.
     */
    public static final class DispatchTicket {
        private final Message message;
        private final Limiter.Listener listener;

        DispatchTicket(Message message, Limiter.Listener listener) {
            this.message = message;
            this.listener = listener;
        }

        public Message message() {
            return message;
        }

        public Limiter.Listener listener() {
            return listener;
        }
    }
}
