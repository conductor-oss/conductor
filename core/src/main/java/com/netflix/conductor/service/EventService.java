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
package com.netflix.conductor.service;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

import com.netflix.conductor.common.metadata.events.EventHandler;

@Validated
public interface EventService {

    /**
     * Add a new event handler.
     *
     * @param eventHandler Instance of {@link EventHandler}
     */
    void addEventHandler(
            @NotNull(message = "EventHandler cannot be null.") @Valid EventHandler eventHandler);

    /**
     * Update an existing event handler.
     *
     * @param eventHandler Instance of {@link EventHandler}
     */
    void updateEventHandler(
            @NotNull(message = "EventHandler cannot be null.") @Valid EventHandler eventHandler);

    /**
     * Remove an event handler.
     *
     * @param name Event name
     */
    void removeEventHandlerStatus(
            @NotEmpty(message = "EventHandler name cannot be null or empty.") String name);

    /**
     * Get all the event handlers.
     *
     * @return list of {@link EventHandler}
     */
    List<EventHandler> getEventHandlers();

    /**
     * Get event handlers for a given event.
     *
     * @param event Event Name
     * @param activeOnly `true|false` for active only events
     * @return list of {@link EventHandler}
     */
    List<EventHandler> getEventHandlersForEvent(
            @NotEmpty(message = "Event cannot be null or empty.") String event, boolean activeOnly);
}
