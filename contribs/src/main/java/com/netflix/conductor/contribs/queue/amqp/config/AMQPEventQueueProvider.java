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
package com.netflix.conductor.contribs.queue.amqp.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import com.netflix.conductor.contribs.queue.amqp.AMQPObservableQueue;
import com.netflix.conductor.contribs.queue.amqp.AMQPObservableQueue.Builder;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;

/** @author Ritu Parathody */
public class AMQPEventQueueProvider implements EventQueueProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(AMQPEventQueueProvider.class);
    protected Map<String, AMQPObservableQueue> queues = new ConcurrentHashMap<>();
    private final boolean useExchange;
    private final AMQPEventQueueProperties properties;
    private final String queueType;

    public AMQPEventQueueProvider(
            AMQPEventQueueProperties properties, String queueType, boolean useExchange) {
        this.properties = properties;
        this.queueType = queueType;
        this.useExchange = useExchange;
    }

    @Override
    public String getQueueType() {
        return queueType;
    }

    @Override
    @NonNull
    public ObservableQueue getQueue(String queueURI) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Retrieve queue with URI {}", queueURI);
        }
        // Build the queue with the inner Builder class of AMQPObservableQueue
        return queues.computeIfAbsent(queueURI, q -> new Builder(properties).build(useExchange, q));
    }
}
