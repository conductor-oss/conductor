/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.kafkaeq.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.kafkaeq.eventqueue.KafkaObservableQueue.Builder;

public class KafkaEventQueueProvider implements EventQueueProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaEventQueueProvider.class);

    private final Map<String, ObservableQueue> queues = new ConcurrentHashMap<>();
    private final KafkaEventQueueProperties properties;

    public KafkaEventQueueProvider(KafkaEventQueueProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getQueueType() {
        return "kafka";
    }

    @Override
    public ObservableQueue getQueue(String queueURI) {
        LOGGER.info("Creating KafkaObservableQueue for topic: {}", queueURI);

        return queues.computeIfAbsent(queueURI, q -> new Builder(properties).build(queueURI));
    }
}
