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

import java.util.List;

import org.springframework.context.Lifecycle;

import rx.Observable;

public interface ObservableQueue extends Lifecycle {

    /**
     * @return An observable for the given queue
     */
    Observable<Message> observe();

    /**
     * @return Type of the queue
     */
    String getType();

    /**
     * @return Name of the queue
     */
    String getName();

    /**
     * @return URI identifier for the queue.
     */
    String getURI();

    /**
     * @param messages to be ack'ed
     * @return the id of the ones which could not be ack'ed
     */
    List<String> ack(List<Message> messages);

    /**
     * @param messages to be Nack'ed
     */
    default void nack(List<Message> messages) {}

    /**
     * @param messages Messages to be published
     */
    void publish(List<Message> messages);

    /**
     * Used to determine if the queue supports unack/visibility timeout such that the messages will
     * re-appear on the queue after a specific period and are available to be picked up again and
     * retried.
     *
     * @return - false if the queue message need not be re-published to the queue for retriability -
     *     true if the message must be re-published to the queue for retriability
     */
    default boolean rePublishIfNoAck() {
        return false;
    }

    /**
     * Extend the lease of the unacknowledged message for longer period.
     *
     * @param message Message for which the timeout has to be changed
     * @param unackTimeout timeout in milliseconds for which the unack lease should be extended.
     *     (replaces the current value with this value)
     */
    void setUnackTimeout(Message message, long unackTimeout);

    /**
     * @return Size of the queue - no. messages pending. Note: Depending upon the implementation,
     *     this can be an approximation
     */
    long size();

    /** Used to close queue instance prior to remove from queues */
    default void close() {}
}
