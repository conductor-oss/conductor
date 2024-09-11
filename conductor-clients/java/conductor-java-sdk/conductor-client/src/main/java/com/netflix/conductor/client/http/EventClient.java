/*
 * Copyright 2022 Orkes, Inc.
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

import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.common.metadata.events.EventHandler;

import com.fasterxml.jackson.core.type.TypeReference;



// Client class for all Event Handler operations
public final class EventClient {

    private ConductorClient client;

    /** Creates a default metadata client */
    public EventClient() {
    }

    public EventClient(ConductorClient client) {
        this.client = client;
    }

    /**
     * Kept only for backwards compatibility
     *
     * @param rootUri basePath for the ApiClient
     */
    @Deprecated
    public void setRootURI(String rootUri) {
        if (client != null) {
            client.shutdown();
        }
        client = new ConductorClient(rootUri);
    }

    /**
     * Register an event handler with the server.
     *
     * @param eventHandler the eventHandler definition.
     */
    public void registerEventHandler(EventHandler eventHandler) {
        Validate.notNull(eventHandler, "Event Handler definition cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/event")
                .body(eventHandler)
                .build();

        client.execute(request);
    }

    /**
     * Updates an event handler with the server.
     *
     * @param eventHandler the eventHandler definition.
     */
    public void updateEventHandler(EventHandler eventHandler) {
        Validate.notNull(eventHandler, "Event Handler definition cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/event")
                .body(eventHandler)
                .build();

        client.execute(request);
    }

    /**
     * @param event name of the event.
     * @param activeOnly if true, returns only the active handlers.
     * @return Returns the list of all the event handlers for a given event.
     */
    public List<EventHandler> getEventHandlers(String event, boolean activeOnly) {
        Validate.notBlank(event, "Event cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/event/{name}")
                .addPathParam("name", event)
                .addQueryParam("activeOnly", activeOnly)
                .build();

        ConductorClientResponse<List<EventHandler>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    /**
     * Removes the event handler definition from the conductor server
     *
     * @param name the name of the event handler to be unregistered
     */
    public void unregisterEventHandler(String name) {
        Validate.notBlank(name, "Event handler name cannot be blank");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/event/{name}")
                .addPathParam("name", name)
                .build();
        client.execute(request);
    }
}
