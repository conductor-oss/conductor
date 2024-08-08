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
package com.netflix.conductor.client.http;

import java.util.List;

import org.apache.commons.lang3.Validate;
import org.glassfish.jersey.client.ClientConfig;

import com.netflix.conductor.client.config.ConductorClientConfiguration;
import com.netflix.conductor.client.config.DefaultConductorClientConfiguration;
import com.netflix.conductor.common.metadata.events.EventHandler;

import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.GenericType;

// Client class for all Event Handler operations
public class EventClient extends ClientBase {
    private static final GenericType<List<EventHandler>> eventHandlerList = new GenericType<>() {};

    /** Creates a default metadata client */
    public EventClient() {
        this(new ClientConfig(), new DefaultConductorClientConfiguration());
    }

    /**
     * @param clientConfig REST Client configuration
     */
    public EventClient(ClientConfig clientConfig) {
        this(clientConfig, new DefaultConductorClientConfiguration());
    }

    /**
     * @param config config REST Client configuration
     * @param filters Chain of client side filters to be applied per request
     */
    public EventClient(ClientConfig config, ClientRequestFilter... filters) {
        this(config, new DefaultConductorClientConfiguration(), filters);
    }

    /**
     * @param config REST Client configuration
     * @param clientConfiguration Specific properties configured for the client, see {@link
     *     ConductorClientConfiguration}
     * @param filters Chain of client side filters to be applied per request
     */
    public EventClient(
            ClientConfig config,
            ConductorClientConfiguration clientConfiguration,
            ClientRequestFilter... filters) {
        super(new ClientRequestHandler(config, filters), clientConfiguration);
    }

    EventClient(ClientRequestHandler requestHandler) {
        super(requestHandler, null);
    }

    /**
     * Register an event handler with the server
     *
     * @param eventHandler the eventHandler definition
     */
    public void registerEventHandler(EventHandler eventHandler) {
        Validate.notNull(eventHandler, "Event Handler definition cannot be null");
        postForEntityWithRequestOnly("event", eventHandler);
    }

    /**
     * Updates an event handler with the server
     *
     * @param eventHandler the eventHandler definition
     */
    public void updateEventHandler(EventHandler eventHandler) {
        Validate.notNull(eventHandler, "Event Handler definition cannot be null");
        put("event", null, eventHandler);
    }

    /**
     * @param event name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    public List<EventHandler> getEventHandlers(String event, boolean activeOnly) {
        Validate.notBlank(event, "Event cannot be blank");

        return getForEntity(
                "event/{event}", new Object[] {"activeOnly", activeOnly}, eventHandlerList, event);
    }

    /**
     * Removes the event handler definition from the conductor server
     *
     * @param name the name of the event handler to be unregistered
     */
    public void unregisterEventHandler(String name) {
        Validate.notBlank(name, "Event handler name cannot be blank");
        delete("event/{name}", name);
    }
}
