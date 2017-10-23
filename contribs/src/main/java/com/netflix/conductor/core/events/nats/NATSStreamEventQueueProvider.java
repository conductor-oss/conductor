/**
 * Copyright 2017 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.core.events.nats;

import com.netflix.conductor.contribs.queue.nats.NATSStreamObservableQueue;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.EventQueues.QueueType;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import io.nats.stan.Connection;
import io.nats.stan.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Oleksiy Lysak
 *
 */
@Singleton
public class NATSStreamEventQueueProvider implements EventQueueProvider {
    private static Logger logger = LoggerFactory.getLogger(NATSStreamEventQueueProvider.class);
    protected Map<String, ObservableQueue> queues = new ConcurrentHashMap<>();
    private Connection connection;
    private String durableName;

    @Inject
    public NATSStreamEventQueueProvider(Configuration config) {
        logger.info("NATS Stream Event Queue Provider init");

        // Get NATS Streaming options
        String clusterId = config.getProperty("io.nats.streaming.clusterId", "test-cluster");
        String clientId = config.getProperty("io.nats.streaming.clientId", UUID.randomUUID().toString());
        String natsUrl = config.getProperty("io.nats.streaming.url", "nats://localhost:4222");
        durableName = config.getProperty("io.nats.streaming.durableName", null);

        logger.info("NATS Streaming clusterId=" + clusterId +
                ", clientId=" + clientId + ", natsUrl=" + natsUrl +
                ", durableName=" + durableName);

        // Init NATS Streaming API
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setClusterId(clusterId);
        connectionFactory.setClientId(clientId);
        connectionFactory.setNatsUrl(natsUrl);

        try {
            connection = connectionFactory.createConnection();
        } catch (Exception e) {
            logger.error("Unable to create NATS Streaming Connection", e);
            throw new RuntimeException(e);
        }

        EventQueues.registerProvider(QueueType.nats_stream, this);
        logger.info("NATS Stream Event Queue Provider initialized...");
    }

    @Override
    public ObservableQueue getQueue(String queueURI) {
        return queues.computeIfAbsent(queueURI, q -> new NATSStreamObservableQueue(connection, queueURI, durableName));
    }
}
