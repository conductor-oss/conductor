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
package com.netflix.conductor.contribs.queue.nats.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;

import com.netflix.conductor.contribs.queue.nats.JetStreamObservableQueue;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import rx.Scheduler;

/**
 * @author andrey.stelmashenko@gmail.com
 */
public class JetStreamEventQueueProvider implements EventQueueProvider {
    public static final String QUEUE_TYPE = "jsm";
    private static final Logger LOG = LoggerFactory.getLogger(JetStreamEventQueueProvider.class);
    private final Map<String, ObservableQueue> queues = new ConcurrentHashMap<>();
    private final JetStreamProperties properties;
    private final ConductorProperties conductorProperties;
    private final Scheduler scheduler;
    private final ApplicationEventPublisher eventPublisher;

    public JetStreamEventQueueProvider(
            ConductorProperties conductorProperties,
            JetStreamProperties properties,
            Scheduler scheduler,
            ApplicationEventPublisher eventPublisher) {
        LOG.info("NATS Event Queue Provider initialized...");
        this.properties = properties;
        this.conductorProperties = conductorProperties;
        this.scheduler = scheduler;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getQueueType() {
        return QUEUE_TYPE;
    }

    @Override
    @NonNull
    public ObservableQueue getQueue(String queueURI) throws IllegalArgumentException {
        LOG.info("Getting obs queue, quri={}", queueURI);
        return queues.computeIfAbsent(
                queueURI,
                q ->
                        new JetStreamObservableQueue(
                                conductorProperties,
                                properties,
                                getQueueType(),
                                queueURI,
                                scheduler,
                                eventPublisher));
    }
}
