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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nats.client.Connection;
import io.nats.client.ConnectionFactory;
import io.nats.client.Subscription;
import rx.Scheduler;

/** @author Oleksiy Lysak */
public class NATSObservableQueue extends NATSAbstractQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(NATSObservableQueue.class);
    private final ConnectionFactory fact;
    private Subscription subs;
    private Connection conn;

    public NATSObservableQueue(ConnectionFactory factory, String queueURI, Scheduler scheduler) {
        super(queueURI, "nats", scheduler);
        this.fact = factory;
        open();
    }

    @Override
    public boolean isConnected() {
        return (conn != null && conn.isConnected());
    }

    @Override
    public void connect() {
        try {
            Connection temp = fact.createConnection();
            LOGGER.info("Successfully connected for " + queueURI);
            temp.setReconnectedCallback(
                    (event) -> LOGGER.warn("onReconnect. Reconnected back for " + queueURI));
            temp.setDisconnectedCallback(
                    (event -> LOGGER.warn("onDisconnect. Disconnected for " + queueURI)));
            conn = temp;
        } catch (Exception e) {
            LOGGER.error("Unable to establish nats connection for " + queueURI, e);
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
            // Create subject/queue subscription if the queue has been provided
            if (StringUtils.isNotEmpty(queue)) {
                LOGGER.info(
                        "No subscription. Creating a queue subscription. subject={}, queue={}",
                        subject,
                        queue);
                subs =
                        conn.subscribe(
                                subject, queue, msg -> onMessage(msg.getSubject(), msg.getData()));
            } else {
                LOGGER.info(
                        "No subscription. Creating a pub/sub subscription. subject={}", subject);
                subs = conn.subscribe(subject, msg -> onMessage(msg.getSubject(), msg.getData()));
            }
        } catch (Exception ex) {
            LOGGER.error(
                    "Subscription failed with " + ex.getMessage() + " for queueURI " + queueURI,
                    ex);
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
                subs.close();
            } catch (Exception ex) {
                LOGGER.error("closeSubs failed with " + ex.getMessage() + " for " + queueURI, ex);
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
                LOGGER.error("closeConn failed with " + ex.getMessage() + " for " + queueURI, ex);
            }
            conn = null;
        }
    }
}
