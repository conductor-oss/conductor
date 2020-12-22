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
package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.EventQueues;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    private final MetadataService metadataService;
    private final EventProcessor eventProcessor;
    private final EventQueues eventQueues;

    public EventServiceImpl(MetadataService metadataService, Optional<EventProcessor> optionalEventProcessor, EventQueues eventQueues) {
        this.metadataService = metadataService;
        // EventProcessor is optional and may not be enabled
        this.eventProcessor = optionalEventProcessor.orElse(null);
        this.eventQueues = eventQueues;
    }

    /**
     * Add a new event handler.
     *
     * @param eventHandler Instance of {@link EventHandler}
     */
    public void addEventHandler(EventHandler eventHandler) {
        metadataService.addEventHandler(eventHandler);
    }

    /**
     * Update an existing event handler.
     *
     * @param eventHandler Instance of {@link EventHandler}
     */
    public void updateEventHandler(EventHandler eventHandler) {
        metadataService.updateEventHandler(eventHandler);
    }

    /**
     * Remove an event handler.
     *
     * @param name Event name
     */
    public void removeEventHandlerStatus(String name) {
        metadataService.removeEventHandlerStatus(name);
    }

    /**
     * Get all the event handlers.
     *
     * @return list of {@link EventHandler}
     */
    public List<EventHandler> getEventHandlers() {
        return metadataService.getAllEventHandlers();
    }

    /**
     * Get event handlers for a given event.
     *
     * @param event      Event Name
     * @param activeOnly `true|false` for active only events
     * @return list of {@link EventHandler}
     */
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        return metadataService.getEventHandlersForEvent(event, activeOnly);
    }

    /**
     * Get registered queues.
     *
     * @param verbose `true|false` for verbose logs
     * @return map of event queues
     */
    public Map<String, ?> getEventQueues(boolean verbose) {
        if(eventProcessor == null) {
            throw new IllegalStateException("Event processing is DISABLED");
        }
        return (verbose ? eventProcessor.getQueueSizes() : eventProcessor.getQueues());
    }

    /**
     * Get registered queue providers.
     *
     * @return list of registered queue providers.
     */
    public List<String> getEventQueueProviders() {
        return eventQueues.getProviders();
    }
}
