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
package com.netflix.conductor.contribs.queue.nats.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import com.netflix.conductor.contribs.queue.nats.NATSStreamObservableQueue;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import rx.Scheduler;

/** @author Oleksiy Lysak */
public class NATSStreamEventQueueProvider implements EventQueueProvider {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(NATSStreamEventQueueProvider.class);
    protected final Map<String, NATSStreamObservableQueue> queues = new ConcurrentHashMap<>();
    private final String durableName;
    private final String clusterId;
    private final String natsUrl;
    private final Scheduler scheduler;

    public NATSStreamEventQueueProvider(NATSStreamProperties properties, Scheduler scheduler) {
        LOGGER.info("NATS Stream Event Queue Provider init");
        this.scheduler = scheduler;

        // Get NATS Streaming options
        clusterId = properties.getClusterId();
        durableName = properties.getDurableName();
        natsUrl = properties.getUrl();

        LOGGER.info(
                "NATS Streaming clusterId="
                        + clusterId
                        + ", natsUrl="
                        + natsUrl
                        + ", durableName="
                        + durableName);
        LOGGER.info("NATS Stream Event Queue Provider initialized...");
    }

    @Override
    public String getQueueType() {
        return "nats_stream";
    }

    @Override
    @NonNull
    public ObservableQueue getQueue(String queueURI) {
        NATSStreamObservableQueue queue =
                queues.computeIfAbsent(
                        queueURI,
                        q ->
                                new NATSStreamObservableQueue(
                                        clusterId, natsUrl, durableName, queueURI, scheduler));
        if (queue.isClosed()) {
            queue.open();
        }
        return queue;
    }
}
