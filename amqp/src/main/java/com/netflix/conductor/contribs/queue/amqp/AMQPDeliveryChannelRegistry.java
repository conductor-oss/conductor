/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.contribs.queue.amqp;

import java.lang.ref.Cleaner;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.netflix.conductor.core.events.queue.Message;

import com.rabbitmq.client.Channel;

final class AMQPDeliveryChannelRegistry {

    private static final Cleaner CLEANER = Cleaner.create();

    private final ReferenceQueue<Message> collectedMessages = new ReferenceQueue<>();
    private final ConcurrentMap<IdentityReference, Delivery> deliveries = new ConcurrentHashMap<>();

    void register(Message message, Channel channel) {
        reapCollectedMessages();
        IdentityReference reference = new IdentityReference(message, collectedMessages);
        Delivery previous = deliveries.putIfAbsent(reference, new Delivery(channel));
        if (previous != null) {
            throw new IllegalStateException("Message already has AMQP delivery metadata");
        }
        CLEANER.register(message, () -> deliveries.remove(reference));
    }

    Delivery get(Message message) {
        reapCollectedMessages();
        return deliveries.get(new IdentityReference(message, null));
    }

    int pendingCount() {
        reapCollectedMessages();
        return (int) deliveries.values().stream().filter(Delivery::isPending).count();
    }

    void reapCollectedMessages() {
        IdentityReference reference;
        while ((reference = (IdentityReference) collectedMessages.poll()) != null) {
            deliveries.remove(reference);
        }
    }

    static final class Delivery {

        private Channel channel;
        private boolean settled;

        private Delivery(Channel channel) {
            this.channel = channel;
        }

        synchronized Channel channel() {
            return channel;
        }

        synchronized boolean isSettled() {
            return settled;
        }

        synchronized boolean isPending() {
            return channel != null;
        }

        synchronized void markSettled() {
            channel = null;
            settled = true;
        }
    }

    private static final class IdentityReference extends WeakReference<Message> {

        private final int identityHashCode;

        private IdentityReference(Message message, ReferenceQueue<Message> referenceQueue) {
            super(message, referenceQueue);
            identityHashCode = System.identityHashCode(message);
        }

        @Override
        public int hashCode() {
            return identityHashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityReference reference)) {
                return false;
            }
            Message message = get();
            return message != null && message == reference.get();
        }
    }
}
