/*
 * Copyright 2023 Conductor Authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ApplicationEventPublisher;

import com.netflix.conductor.contribs.queue.nats.config.JetStreamProperties;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import io.nats.client.*;
import io.nats.client.api.*;
import rx.Observable;
import rx.Scheduler;

/**
 * @author andrey.stelmashenko@gmail.com
 */
public class JetStreamObservableQueue implements ObservableQueue {
    private static final Logger LOG = LoggerFactory.getLogger(JetStreamObservableQueue.class);
    private final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    private final Lock mu = new ReentrantLock();
    private final String queueType;
    private final String subject;
    private final String queueUri;
    private final JetStreamProperties properties;
    private final Scheduler scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ApplicationEventPublisher eventPublisher;
    private Connection nc;
    private JetStreamSubscription sub;
    private Observable<Long> interval;
    private final String queueGroup;

    public JetStreamObservableQueue(
            ConductorProperties conductorProperties,
            JetStreamProperties properties,
            String queueType,
            String queueUri,
            Scheduler scheduler,
            ApplicationEventPublisher eventPublisher) {
        LOG.debug("JSM obs queue create, qtype={}, quri={}", queueType, queueUri);

        this.queueUri = queueUri;
        // If queue specified (e.g. subject:queue) - split to subject & queue
        if (queueUri.contains(":")) {
            this.subject =
                    getQueuePrefix(conductorProperties, properties)
                            + queueUri.substring(0, queueUri.indexOf(':'));
            queueGroup = queueUri.substring(queueUri.indexOf(':') + 1);
        } else {
            this.subject = getQueuePrefix(conductorProperties, properties) + queueUri;
            queueGroup = null;
        }

        this.queueType = queueType;
        this.properties = properties;
        this.scheduler = scheduler;
        this.eventPublisher = eventPublisher;
    }

    public static String getQueuePrefix(
            ConductorProperties conductorProperties, JetStreamProperties properties) {
        String stack = "";
        if (conductorProperties.getStack() != null && conductorProperties.getStack().length() > 0) {
            stack = conductorProperties.getStack() + "_";
        }

        return StringUtils.isBlank(properties.getListenerQueuePrefix())
                ? conductorProperties.getAppId() + "_jsm_notify_" + stack
                : properties.getListenerQueuePrefix();
    }

    @Override
    public Observable<Message> observe() {
        return Observable.create(getOnSubscribe());
    }

    private Observable.OnSubscribe<Message> getOnSubscribe() {
        return subscriber -> {
            interval =
                    Observable.interval(
                            properties.getPollTimeDuration().toMillis(),
                            TimeUnit.MILLISECONDS,
                            scheduler);
            interval.flatMap(
                            (Long x) -> {
                                if (!this.isRunning()) {
                                    LOG.debug(
                                            "Component stopped, skip listening for messages from JSM Queue '{}'",
                                            subject);
                                    return Observable.from(Collections.emptyList());
                                } else {
                                    List<Message> available = new ArrayList<>();
                                    messages.drainTo(available);
                                    if (!available.isEmpty()) {
                                        LOG.debug(
                                                "Processing JSM queue '{}' batch messages count={}",
                                                subject,
                                                available.size());
                                    }
                                    return Observable.from(available);
                                }
                            })
                    .subscribe(subscriber::onNext, subscriber::onError);
        };
    }

    @Override
    public String getType() {
        return queueType;
    }

    @Override
    public String getName() {
        return queueUri;
    }

    @Override
    public String getURI() {
        return getName();
    }

    @Override
    public List<String> ack(List<Message> messages) {
        messages.forEach(m -> ((JsmMessage) m).getJsmMsg().ack());
        return Collections.emptyList();
    }

    @Override
    public void publish(List<Message> messages) {
        try (Connection conn = Nats.connect(properties.getUrl())) {
            JetStream js = conn.jetStream();
            for (Message msg : messages) {
                js.publish(subject, msg.getPayload().getBytes());
            }
        } catch (IOException | JetStreamApiException e) {
            throw new NatsException("Failed to publish to jsm", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsException("Failed to publish to jsm", e);
        }
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {
        // do nothing, not supported
    }

    @Override
    public long size() {
        try {
            return sub.getConsumerInfo().getNumPending();
        } catch (IOException | JetStreamApiException e) {
            LOG.warn("Failed to get stream '{}' info", subject);
        }
        return 0;
    }

    @Override
    public void start() {
        mu.lock();
        try {
            natsConnect();
        } finally {
            mu.unlock();
        }
    }

    @Override
    public void stop() {
        interval.unsubscribeOn(scheduler);
        try {
            if (nc != null) {
                nc.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Failed to close Nats connection", e);
        }
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }

    private void natsConnect() {
        if (running.get()) {
            return;
        }
        LOG.info("Starting JSM observable, name={}", queueUri);
        try {
            Nats.connectAsynchronously(
                    new Options.Builder()
                            .connectionListener(
                                    (conn, type) -> {
                                        LOG.info("Connection to JSM updated: {}", type);
                                        if (ConnectionListener.Events.CLOSED.equals(type)) {
                                            LOG.error(
                                                    "Could not reconnect to NATS! Changing liveness status to {}!",
                                                    LivenessState.BROKEN);
                                            AvailabilityChangeEvent.publish(
                                                    eventPublisher, type, LivenessState.BROKEN);
                                        }
                                        this.nc = conn;
                                        subscribeOnce(conn, type);
                                    })
                            .errorListener(new LoggingNatsErrorListener())
                            .server(properties.getUrl())
                            .maxReconnects(properties.getMaxReconnects())
                            .build(),
                    true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsException("Failed to connect to JSM", e);
        }
    }

    private void createStream(JetStreamManagement jsm) {
        StreamConfiguration streamConfig =
                StreamConfiguration.builder()
                        .name(subject)
                        .replicas(properties.getReplicas())
                        .retentionPolicy(RetentionPolicy.Limits)
                        .maxBytes(properties.getStreamMaxBytes())
                        .storageType(StorageType.get(properties.getStreamStorageType()))
                        .build();

        try {
            StreamInfo streamInfo = jsm.addStream(streamConfig);
            LOG.debug("Updated stream, info: {}", streamInfo);
        } catch (IOException | JetStreamApiException e) {
            LOG.error("Failed to add stream: " + streamConfig, e);
            AvailabilityChangeEvent.publish(eventPublisher, e, LivenessState.BROKEN);
        }
    }

    private void subscribeOnce(Connection nc, ConnectionListener.Events type) {
        if (type.equals(ConnectionListener.Events.CONNECTED)
                || type.equals(ConnectionListener.Events.RECONNECTED)) {
            JetStreamManagement jsm;
            try {
                jsm = nc.jetStreamManagement();
            } catch (IOException e) {
                throw new NatsException("Failed to get jsm management", e);
            }
            createStream(jsm);
            var consumerConfig = createConsumer(jsm);
            subscribe(nc, consumerConfig);
        }
    }

    private ConsumerConfiguration createConsumer(JetStreamManagement jsm) {
        ConsumerConfiguration consumerConfig =
                ConsumerConfiguration.builder()
                        .name(properties.getDurableName())
                        .deliverGroup(queueGroup)
                        .durable(properties.getDurableName())
                        .ackWait(properties.getAckWait())
                        .maxDeliver(properties.getMaxDeliver())
                        .maxAckPending(properties.getMaxAckPending())
                        .ackPolicy(AckPolicy.Explicit)
                        .deliverSubject(subject + "-deliver")
                        .deliverPolicy(DeliverPolicy.New)
                        .build();

        try {
            jsm.addOrUpdateConsumer(subject, consumerConfig);
            return consumerConfig;
        } catch (IOException | JetStreamApiException e) {
            throw new NatsException("Failed to add/update consumer", e);
        }
    }

    private void subscribe(Connection nc, ConsumerConfiguration consumerConfig) {
        try {
            JetStream js = nc.jetStream();

            PushSubscribeOptions pso =
                    PushSubscribeOptions.builder().configuration(consumerConfig).stream(subject)
                            .bind(true)
                            .build();

            LOG.debug("Subscribing jsm, subject={}, options={}", subject, pso);
            sub =
                    js.subscribe(
                            subject,
                            queueGroup,
                            nc.createDispatcher(),
                            msg -> {
                                var message = new JsmMessage();
                                message.setJsmMsg(msg);
                                message.setId(NUID.nextGlobal());
                                message.setPayload(new String(msg.getData()));
                                messages.add(message);
                            },
                            /*autoAck*/ false,
                            pso);
            LOG.debug("Subscribed successfully {}", sub.getConsumerInfo());
            this.running.set(true);
        } catch (IOException | JetStreamApiException e) {
            throw new NatsException("Failed to subscribe", e);
        }
    }
}
