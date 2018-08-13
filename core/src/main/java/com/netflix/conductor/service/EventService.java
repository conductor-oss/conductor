package com.netflix.conductor.service;

import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.service.utils.ServiceUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 * @author fjhaveri
 */
@Singleton
@Trace
public class EventService {

    private final MetadataService metadataService;

    private final EventProcessor eventProcessor;

    @Inject
    public EventService(MetadataService metadataService, EventProcessor eventProcessor) {
        this.metadataService = metadataService;
        this.eventProcessor = eventProcessor;
    }

    /**
     * Add a new event handler.
     */
    public void addEventHandler(EventHandler eventHandler) {
        metadataService.addEventHandler(eventHandler);
    }

    /**
     * Update an existing event handler.
     */
    public void updateEventHandler(EventHandler eventHandler) {
        metadataService.updateEventHandler(eventHandler);
    }

    /**
     * Remove an event handler.
     */
    public void removeEventHandlerStatus(String name) {
        ServiceUtils.isValid(name, "Name cannot be null or empty.");
        metadataService.removeEventHandlerStatus(name);
    }

    /**
     * Get all the event handlers.
     */
    public List<EventHandler> getEventHandlers() {
        return metadataService.getEventHandlers();
    }

    /**
     * Get event handlers for a given event.
     */
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        ServiceUtils.isValid(event, "Event cannot be null or empty.");
        return metadataService.getEventHandlersForEvent(event, activeOnly);
    }

    /**
     * Get registered queues.
     */
    public Map<String, ?> getEventQueues(boolean verbose) {
        return (verbose ? eventProcessor.getQueueSizes() : eventProcessor.getQueues());
    }

    /**
     * Get registered queue providers.
     */
    public List<String> getEventQueueProviders() {
        return EventQueues.providers();
    }
}
