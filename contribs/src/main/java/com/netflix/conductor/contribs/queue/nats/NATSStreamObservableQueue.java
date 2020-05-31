/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.contribs.queue.nats;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nats.streaming.StreamingConnection;
import io.nats.streaming.StreamingConnectionFactory;
import io.nats.streaming.Subscription;
import io.nats.streaming.SubscriptionOptions;
import rx.Scheduler;

/**
 * @author Oleksiy Lysak
 */
public class NATSStreamObservableQueue extends NATSAbstractQueue {
    private static final  Logger logger = LoggerFactory.getLogger(NATSStreamObservableQueue.class);
    private final StreamingConnectionFactory fact;
    private StreamingConnection conn;
    private Subscription subs;
    private final String durableName;

    public NATSStreamObservableQueue(String clusterId, String natsUrl, String durableName, String queueURI, Scheduler scheduler) {
        super(queueURI, "nats_stream", scheduler);
        this.fact = new StreamingConnectionFactory();
        this.fact.setClusterId(clusterId);
        this.fact.setClientId(UUID.randomUUID().toString());
        this.fact.setNatsUrl(natsUrl);
        this.durableName = durableName;
        open();
    }
    
    @Override
    public boolean isConnected() {
        return (conn != null && conn.getNatsConnection() != null && conn.getNatsConnection().isConnected());
    }
    
    @Override
    public void connect() {
        try {
            StreamingConnection temp = fact.createConnection();
            logger.info("Successfully connected for " + queueURI);
            
            temp.getNatsConnection().setReconnectedCallback((event) ->
                    logger.warn("onReconnect. Reconnected back for " + queueURI));
            temp.getNatsConnection().setDisconnectedCallback((event ->
                    logger.warn("onDisconnect. Disconnected for " + queueURI)));
            
            conn = temp;
        } catch (Exception e) {
            logger.error("Unable to establish nats streaming connection for " + queueURI, e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void subscribe() {
        // do nothing if already subscribed
        if (subs != null) {
            return;
        }
        
        try {
            ensureConnected();
            
            SubscriptionOptions subscriptionOptions = new SubscriptionOptions
                    .Builder().durableName(durableName).build();
            
            // Create subject/queue subscription if the queue has been provided
            if (StringUtils.isNotEmpty(queue)) {
                logger.info("No subscription. Creating a queue subscription. subject={}, queue={}", subject, queue);
                subs = conn.subscribe(subject, queue, msg -> onMessage(msg.getSubject(), msg.getData()), subscriptionOptions);
            } else {
                logger.info("No subscription. Creating a pub/sub subscription. subject={}", subject);
                subs = conn.subscribe(subject, msg -> onMessage(msg.getSubject(), msg.getData()), subscriptionOptions);
            }
        } catch (Exception ex) {
            logger.error("Subscription failed with " + ex.getMessage() + " for queueURI " + queueURI, ex);
        }
    }
    
    @Override
    public void publish(String subject, byte[] data) throws Exception {
        ensureConnected();
        conn.publish(subject, data);
    }
    
    @Override
    public void closeSubs() {
        if (subs != null) {
            try {
                subs.close(true);
            } catch (Exception ex) {
                logger.error("closeSubs failed with " + ex.getMessage() + " for " + queueURI, ex);
            }
            subs = null;
        }
    }
    
    @Override
    public void closeConn() {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ex) {
                logger.error("closeConn failed with " + ex.getMessage() + " for " + queueURI, ex);
            }
            conn = null;
        }
    }
}
