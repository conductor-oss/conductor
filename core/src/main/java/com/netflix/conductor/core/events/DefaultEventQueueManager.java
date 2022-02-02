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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.Lifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.events.queue.DefaultEventQueueProcessor;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel.Status;

/**
 * Manages the event queues registered in the system and sets up listeners for these.
 *
 * <p>Manages the lifecycle of -
 *
 * <ul>
 *   <li>Queues registered with event handlers
 *   <li>Default event queues that Conductor listens on
 * </ul>
 *
 * @see DefaultEventQueueProcessor
 */
@Component
@ConditionalOnProperty(
        name = "conductor.default-event-processor.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DefaultEventQueueManager extends LifecycleAwareComponent implements EventQueueManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEventQueueManager.class);

    private final EventHandlerDAO eventHandlerDAO;
    private final EventQueues eventQueues;
    private final DefaultEventProcessor defaultEventProcessor;
    private final Map<String, ObservableQueue> eventToQueueMap = new ConcurrentHashMap<>();
    private final Map<Status, ObservableQueue> defaultQueues;

    public DefaultEventQueueManager(
            Map<Status, ObservableQueue> defaultQueues,
            EventHandlerDAO eventHandlerDAO,
            EventQueues eventQueues,
            DefaultEventProcessor defaultEventProcessor) {
        this.defaultQueues = defaultQueues;
        this.eventHandlerDAO = eventHandlerDAO;
        this.eventQueues = eventQueues;
        this.defaultEventProcessor = defaultEventProcessor;
    }

    /**
     * @return Returns a map of queues which are active. Key is event name and value is queue URI
     */
    @Override
    public Map<String, String> getQueues() {
        Map<String, String> queues = new HashMap<>();
        eventToQueueMap.forEach((key, value) -> queues.put(key, value.getName()));
        return queues;
    }

    @Override
    public Map<String, Map<String, Long>> getQueueSizes() {
        Map<String, Map<String, Long>> queues = new HashMap<>();
        eventToQueueMap.forEach(
                (key, value) -> {
                    Map<String, Long> size = new HashMap<>();
                    size.put(value.getName(), value.size());
                    queues.put(key, size);
                });
        return queues;
    }

    @Override
    public void doStart() {
        eventToQueueMap.forEach(
                (event, queue) -> {
                    LOGGER.info("Start listening for events: {}", event);
                    queue.start();
                });
        defaultQueues.forEach(
                (status, queue) -> {
                    LOGGER.info(
                            "Start listening on default queue {} for status {}",
                            queue.getName(),
                            status);
                    queue.start();
                });
    }

    @Override
    public void doStop() {
        eventToQueueMap.forEach(
                (event, queue) -> {
                    LOGGER.info("Stop listening for events: {}", event);
                    queue.stop();
                });
        defaultQueues.forEach(
                (status, queue) -> {
                    LOGGER.info(
                            "Stop listening on default queue {} for status {}",
                            status,
                            queue.getName());
                    queue.stop();
                });
    }

    @Scheduled(fixedDelay = 60_000)
    public void refreshEventQueues() {
        try {
            Set<String> events =
                    eventHandlerDAO.getAllEventHandlers().stream()
                            .map(EventHandler::getEvent)
                            .collect(Collectors.toSet());

            List<ObservableQueue> createdQueues = new LinkedList<>();
            events.forEach(
                    event ->
                            eventToQueueMap.computeIfAbsent(
                                    event,
                                    s -> {
                                        ObservableQueue q = eventQueues.getQueue(event);
                                        createdQueues.add(q);
                                        return q;
                                    }));

            // start listening on all of the created queues
            createdQueues.stream()
                    .filter(Objects::nonNull)
                    .peek(Lifecycle::start)
                    .forEach(this::listen);

        } catch (Exception e) {
            Monitors.error(getClass().getSimpleName(), "refresh");
            LOGGER.error("refresh event queues failed", e);
        }
    }

    private void listen(ObservableQueue queue) {
        queue.observe().subscribe((Message msg) -> defaultEventProcessor.handle(queue, msg));
    }
}
