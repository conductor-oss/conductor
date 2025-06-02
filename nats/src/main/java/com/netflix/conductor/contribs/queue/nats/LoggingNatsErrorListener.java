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
package com.netflix.conductor.contribs.queue.nats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nats.client.Connection;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;

public class LoggingNatsErrorListener implements ErrorListener {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingNatsErrorListener.class);

    @Override
    public void errorOccurred(Connection conn, String error) {
        LOG.error("Nats connection error occurred: {}", error);
    }

    @Override
    public void exceptionOccurred(Connection conn, Exception exp) {
        LOG.error("Nats connection exception occurred", exp);
    }

    @Override
    public void messageDiscarded(Connection conn, Message msg) {
        LOG.error("Nats message discarded, SID={}, ", msg.getSID());
    }

    @Override
    public void heartbeatAlarm(
            Connection conn,
            JetStreamSubscription sub,
            long lastStreamSequence,
            long lastConsumerSequence) {
        LOG.warn("Heartbit missed, subject={}", sub.getSubject());
    }
}
