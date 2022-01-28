/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.contribs.queue.nats;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import io.nats.client.NUID;
import rx.Observable;
import rx.Scheduler;

/** @author Oleksiy Lysak */
public abstract class NATSAbstractQueue implements ObservableQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(NATSAbstractQueue.class);
    protected LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    protected final Lock mu = new ReentrantLock();
    private final String queueType;
    private ScheduledExecutorService execs;
    private final Scheduler scheduler;

    protected final String queueURI;
    protected final String subject;
    protected String queue;

    // Indicates that observe was called (Event Handler) and we must to re-initiate subscription
    // upon reconnection
    private boolean observable;
    private boolean isOpened;
    private volatile boolean running;

    NATSAbstractQueue(String queueURI, String queueType, Scheduler scheduler) {
        this.queueURI = queueURI;
        this.queueType = queueType;
        this.scheduler = scheduler;

        // If queue specified (e.g. subject:queue) - split to subject & queue
        if (queueURI.contains(":")) {
            this.subject = queueURI.substring(0, queueURI.indexOf(':'));
            queue = queueURI.substring(queueURI.indexOf(':') + 1);
        } else {
            this.subject = queueURI;
            queue = null;
        }
        LOGGER.info(
                String.format(
                        "Initialized with queueURI=%s, subject=%s, queue=%s",
                        queueURI, subject, queue));
    }

    void onMessage(String subject, byte[] data) {
        String payload = new String(data);
        LOGGER.info(String.format("Received message for %s: %s", subject, payload));

        Message dstMsg = new Message();
        dstMsg.setId(NUID.nextGlobal());
        dstMsg.setPayload(payload);

        messages.add(dstMsg);
    }

    @Override
    public Observable<Message> observe() {
        LOGGER.info("Observe invoked for queueURI " + queueURI);
        observable = true;

        mu.lock();
        try {
            subscribe();
        } finally {
            mu.unlock();
        }

        Observable.OnSubscribe<Message> onSubscribe =
                subscriber -> {
                    Observable<Long> interval =
                            Observable.interval(100, TimeUnit.MILLISECONDS, scheduler);
                    interval.flatMap(
                                    (Long x) -> {
                                        if (!isRunning()) {
                                            LOGGER.debug(
                                                    "Component stopped, skip listening for messages from NATS Queue");
                                            return Observable.from(Collections.emptyList());
                                        } else {
                                            List<Message> available = new LinkedList<>();
                                            messages.drainTo(available);

                                            if (!available.isEmpty()) {
                                                AtomicInteger count = new AtomicInteger(0);
                                                StringBuilder buffer = new StringBuilder();
                                                available.forEach(
                                                        msg -> {
                                                            buffer.append(msg.getId())
                                                                    .append("=")
                                                                    .append(msg.getPayload());
                                                            count.incrementAndGet();

                                                            if (count.get() < available.size()) {
                                                                buffer.append(",");
                                                            }
                                                        });
                                                LOGGER.info(
                                                        String.format(
                                                                "Batch from %s to conductor is %s",
                                                                subject, buffer.toString()));
                                            }

                                            return Observable.from(available);
                                        }
                                    })
                            .subscribe(subscriber::onNext, subscriber::onError);
                };
        return Observable.create(onSubscribe);
    }

    @Override
    public String getType() {
        return queueType;
    }

    @Override
    public String getName() {
        return queueURI;
    }

    @Override
    public String getURI() {
        return queueURI;
    }

    @Override
    public List<String> ack(List<Message> messages) {
        return Collections.emptyList();
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {}

    @Override
    public long size() {
        return messages.size();
    }

    @Override
    public void publish(List<Message> messages) {
        messages.forEach(
                message -> {
                    try {
                        String payload = message.getPayload();
                        publish(subject, payload.getBytes());
                        LOGGER.info(String.format("Published message to %s: %s", subject, payload));
                    } catch (Exception ex) {
                        LOGGER.error(
                                "Failed to publish message "
                                        + message.getPayload()
                                        + " to "
                                        + subject,
                                ex);
                        throw new RuntimeException(ex);
                    }
                });
    }

    @Override
    public boolean rePublishIfNoAck() {
        return true;
    }

    @Override
    public void close() {
        LOGGER.info("Closing connection for " + queueURI);
        mu.lock();
        try {
            if (execs != null) {
                execs.shutdownNow();
                execs = null;
            }
            closeSubs();
            closeConn();
            isOpened = false;
        } finally {
            mu.unlock();
        }
    }

    public void open() {
        // do nothing if not closed
        if (isOpened) {
            return;
        }

        mu.lock();
        try {
            try {
                connect();

                // Re-initiated subscription if existed
                if (observable) {
                    subscribe();
                }
            } catch (Exception ignore) {
            }

            execs = Executors.newScheduledThreadPool(1);
            execs.scheduleAtFixedRate(this::monitor, 0, 500, TimeUnit.MILLISECONDS);
            isOpened = true;
        } finally {
            mu.unlock();
        }
    }

    private void monitor() {
        if (isConnected()) {
            return;
        }

        LOGGER.error("Monitor invoked for " + queueURI);
        mu.lock();
        try {
            closeSubs();
            closeConn();

            // Connect
            connect();

            // Re-initiated subscription if existed
            if (observable) {
                subscribe();
            }
        } catch (Exception ex) {
            LOGGER.error("Monitor failed with " + ex.getMessage() + " for " + queueURI, ex);
        } finally {
            mu.unlock();
        }
    }

    public boolean isClosed() {
        return !isOpened;
    }

    void ensureConnected() {
        if (!isConnected()) {
            throw new RuntimeException("No nats connection");
        }
    }

    @Override
    public void start() {
        LOGGER.info("Started listening to {}:{}", getClass().getSimpleName(), queueURI);
        running = true;
    }

    @Override
    public void stop() {
        LOGGER.info("Stopped listening to {}:{}", getClass().getSimpleName(), queueURI);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    abstract void connect();

    abstract boolean isConnected();

    abstract void publish(String subject, byte[] data) throws Exception;

    abstract void subscribe();

    abstract void closeSubs();

    abstract void closeConn();
}
