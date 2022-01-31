/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core.events.queue;

import java.util.Objects;

public class Message {

    private String payload;
    private String id;
    private String receipt;
    private int priority;

    public Message() {}

    public Message(String id, String payload, String receipt) {
        this.payload = payload;
        this.id = id;
        this.receipt = receipt;
    }

    public Message(String id, String payload, String receipt, int priority) {
        this.payload = payload;
        this.id = id;
        this.receipt = receipt;
        this.priority = priority;
    }

    /** @return the payload */
    public String getPayload() {
        return payload;
    }

    /** @param payload the payload to set */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /** @return the id */
    public String getId() {
        return id;
    }

    /** @param id the id to set */
    public void setId(String id) {
        this.id = id;
    }

    /** @return Receipt attached to the message */
    public String getReceipt() {
        return receipt;
    }

    /** @param receipt Receipt attached to the message */
    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    /**
     * Gets the message priority
     *
     * @return priority of message.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the message priority (between 0 and 99). Higher priority message is retrieved ahead of
     * lower priority ones.
     *
     * @param priority the priority of message (between 0 and 99)
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Message message = (Message) o;
        return Objects.equals(payload, message.payload)
                && Objects.equals(id, message.id)
                && Objects.equals(priority, message.priority)
                && Objects.equals(receipt, message.receipt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payload, id, receipt, priority);
    }
}
