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
package com.netflix.conductor.core.events;

import org.springframework.lang.NonNull;

import com.netflix.conductor.core.events.queue.ObservableQueue;

public interface EventQueueProvider {

    String getQueueType();

    /**
     * Creates or reads the {@link ObservableQueue} for the given <code>queueURI</code>.
     *
     * @param queueURI The URI of the queue.
     * @return The {@link ObservableQueue} implementation for the <code>queueURI</code>.
     * @throws IllegalArgumentException thrown when an {@link ObservableQueue} can not be created
     *     for the <code>queueURI</code>.
     */
    @NonNull
    ObservableQueue getQueue(String queueURI) throws IllegalArgumentException;
}
