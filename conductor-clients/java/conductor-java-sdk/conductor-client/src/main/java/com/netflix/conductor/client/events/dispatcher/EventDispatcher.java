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
package com.netflix.conductor.client.events.dispatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.netflix.conductor.client.events.ConductorClientEvent;

public class EventDispatcher<T extends ConductorClientEvent> {

    private final Map<Class<? extends T>, List<Consumer<? extends T>>> listeners;

    public EventDispatcher() {
        this.listeners = new ConcurrentHashMap<>();
    }

    public <U extends T> void register(Class<U> clazz, Consumer<U> listener) {
        //  CopyOnWriteArrayList is thread-safe. It's particularly useful in scenarios where
        //  reads are far more frequent than writes (typical for event listeners).
        listeners.computeIfAbsent(clazz, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public <U extends T> void unregister(Class<U> clazz, Consumer<U> listener) {
        List<Consumer<? extends T>> consumers = listeners.get(clazz);
        if (consumers != null) {
            consumers.remove(listener);
            if (consumers.isEmpty()) {
                listeners.remove(clazz);
            }
        }
    }

    public void publish(T event) {
        if (noListeners(event)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            List<Consumer<? extends T>> eventListeners = getEventListeners(event);
            for (Consumer<? extends T> listener : eventListeners) {
                ((Consumer<T>) listener).accept(event);
            }
        });
    }

    private boolean noListeners(T event) {
        if (listeners.isEmpty()) {
            return true;
        }

        var specificEventListeners = listeners.get(event.getClass());
        var promiscuousListeners = listeners.get(ConductorClientEvent.class);

        return (specificEventListeners == null || specificEventListeners.isEmpty())
                && (promiscuousListeners == null || promiscuousListeners.isEmpty());
    }

    private List<Consumer<? extends T>> getEventListeners(T event) {
        var specificEventListeners = listeners.get(event.getClass());
        var promiscuousListeners = listeners.get(ConductorClientEvent.class);
        if (promiscuousListeners == null || promiscuousListeners.isEmpty()) {
            return specificEventListeners;
        }

        if (specificEventListeners == null || specificEventListeners.isEmpty()) {
            return promiscuousListeners;
        }

        return Stream.concat(specificEventListeners.stream(), promiscuousListeners.stream())
                .collect(Collectors.toList());
    }

}
