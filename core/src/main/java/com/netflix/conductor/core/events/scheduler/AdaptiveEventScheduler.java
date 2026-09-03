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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;

/**
 * Self-tuning adaptive scheduler that replaces the legacy thread-per-event subscription model.
 *
 * <p>The scheduler runs two single-threaded loops:
 *
 * <ol>
 *   <li><b>Poller</b>: round-robins over registered lanes, calls {@link ObservableQueue#poll(int,
 *       Duration)} sized by each lane's buffer headroom so we never fetch more than we can hold.
 *       This applies natural backpressure to the broker.
 *   <li><b>Dispatcher</b>: runs {@link DrrDispatcher#dispatch} repeatedly, sinking each popped
 *       message to the worker executor along with the Vegas limiter listener that must be closed
 *       when the message finishes. Idle rounds park briefly to avoid hot-spinning.
 * </ol>
 *
 * <p>The worker executor is supplied by the caller so the same scheduler can run on a virtual-
 * thread executor (Java 21+) in production or a small fixed pool in tests. The scheduler never runs
 * handler logic itself; it only orchestrates fairness and capacity.
 *
 * <p>Lifecycle is idempotent: {@link #start()} and {@link #stop()} can be called repeatedly. All
 * mutating operations on the lane set are safe under concurrent registration thanks to the
 * underlying {@link ConcurrentHashMap}; the loops snapshot {@code lanes.values()} per iteration.
 */
public final class AdaptiveEventScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveEventScheduler.class);

    /** Poll-call upper bound. The actual batch is min(this, lane buffer headroom). */
    static final int MAX_POLL_BATCH = 64;

    /** Per-queue poll wait. Short so the poller stays responsive when many queues are empty. */
    static final Duration POLL_TIMEOUT = Duration.ofMillis(100);

    /** Backoff when the dispatcher finds nothing to do, prevents busy-spin on idle systems. */
    static final Duration IDLE_PARK = Duration.ofMillis(5);

    private final Map<String, EventLane> lanes = new ConcurrentHashMap<>();
    private final DrrDispatcher dispatcher;
    private final ExecutorService workerExecutor;
    private final BiConsumer<ObservableQueue, Message> messageHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollerThread;
    private Thread dispatcherThread;

    public AdaptiveEventScheduler(
            ExecutorService workerExecutor, BiConsumer<ObservableQueue, Message> messageHandler) {
        this(workerExecutor, messageHandler, new DrrDispatcher());
    }

    AdaptiveEventScheduler(
            ExecutorService workerExecutor,
            BiConsumer<ObservableQueue, Message> messageHandler,
            DrrDispatcher dispatcher) {
        this.workerExecutor = workerExecutor;
        this.messageHandler = messageHandler;
        this.dispatcher = dispatcher;
    }

    /** Register a queue under {@code event} and start tracking it as a lane. Idempotent. */
    public EventLane registerLane(String event, ObservableQueue queue) {
        return lanes.computeIfAbsent(event, e -> new EventLane(e, queue));
    }

    /** Deregister a lane. Buffered messages are discarded; the broker will redeliver on no-ack. */
    public void deregisterLane(String event) {
        lanes.remove(event);
    }

    public Map<String, EventLane> lanes() {
        return Map.copyOf(lanes);
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ThreadFactory pollerTf =
                new BasicThreadFactory.Builder()
                        .namingPattern("adaptive-event-poller")
                        .daemon(true)
                        .build();
        ThreadFactory dispatcherTf =
                new BasicThreadFactory.Builder()
                        .namingPattern("adaptive-event-dispatcher")
                        .daemon(true)
                        .build();
        pollerThread = pollerTf.newThread(this::pollLoop);
        dispatcherThread = dispatcherTf.newThread(this::dispatchLoop);
        pollerThread.start();
        dispatcherThread.start();
        LOGGER.info("AdaptiveEventScheduler started");
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (pollerThread != null) {
            pollerThread.interrupt();
        }
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
        LOGGER.info("AdaptiveEventScheduler stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * One pass of the poller loop, exposed for deterministic testing. Walks every lane, computes
     * the batch size from buffer headroom, and offers polled messages into the buffer. Messages
     * that don't fit are dropped to nack so the broker can redeliver — this only happens if the
     * buffer is racing the dispatcher and is the safety valve for the bounded-buffer design.
     */
    int pollOnce() {
        int polled = 0;
        for (EventLane lane : lanes.values()) {
            int headroom = lane.buffer().remainingCapacity();
            if (headroom <= 0) {
                lane.incrementPollSkipped();
                continue;
            }
            int batch = Math.min(MAX_POLL_BATCH, headroom);
            List<Message> msgs;
            try {
                msgs = lane.queue().poll(batch, POLL_TIMEOUT);
            } catch (Exception e) {
                LOGGER.warn("Poll failed for event {}: {}", lane.event(), e.toString());
                continue;
            }
            if (msgs == null || msgs.isEmpty()) {
                continue;
            }
            for (Message m : msgs) {
                if (!lane.buffer().offer(m)) {
                    // Buffer filled between headroom check and offer; nack so broker redelivers.
                    lane.incrementDropped();
                    try {
                        lane.queue().nack(List.of(m));
                    } catch (Exception ignored) {
                        // best effort; underlying queue may not support nack
                    }
                } else {
                    polled++;
                }
            }
        }
        return polled;
    }

    /** One dispatcher pass. Returns the number of messages handed to the worker executor. */
    int dispatchOnce() {
        return dispatcher.dispatch(
                lanes.values(),
                (lane, ticket) ->
                        workerExecutor.execute(
                                () -> {
                                    try {
                                        messageHandler.accept(lane.queue(), ticket.message());
                                        ticket.listener().onSuccess();
                                    } catch (Throwable t) {
                                        // Vegas treats onDropped as a signal to decrease the
                                        // per-lane limit, which is the correct response to a
                                        // handler failure: it indicates capacity loss.
                                        ticket.listener().onDropped();
                                        LOGGER.error(
                                                "Handler failed for event {}: {}",
                                                lane.event(),
                                                t.toString(),
                                                t);
                                    }
                                }));
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int polled = pollOnce();
                if (polled == 0) {
                    Thread.sleep(IDLE_PARK.toMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                LOGGER.error("Poller loop error: {}", t.toString(), t);
            }
        }
    }

    private void dispatchLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int dispatched = dispatchOnce();
                if (dispatched == 0) {
                    Thread.sleep(IDLE_PARK.toMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                LOGGER.error("Dispatcher loop error: {}", t.toString(), t);
            }
        }
    }

    /**
     * Build a worker {@link ExecutorService} for handler execution. Uses Java 21+ {@code
     * Executors.newVirtualThreadPerTaskExecutor()} when available so high-cardinality event sets do
     * not require a correspondingly large pool of platform threads. Falls back to a cached thread
     * pool on older runtimes.
     */
    public static ExecutorService newWorkerExecutor() {
        try {
            // Reflective lookup keeps source compatible with Java 17 build paths while still
            // using the virtual-thread API at runtime on Java 21+.
            return (ExecutorService)
                    Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (ReflectiveOperationException e) {
            LOGGER.info(
                    "Virtual threads unavailable on this JVM, falling back to cached pool: {}",
                    e.toString());
            ThreadFactory tf =
                    new BasicThreadFactory.Builder()
                            .namingPattern("adaptive-event-worker-%d")
                            .daemon(true)
                            .build();
            return Executors.newCachedThreadPool(tf);
        }
    }
}
