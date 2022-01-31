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
package com.netflix.conductor.dao;

import java.util.List;

import com.netflix.conductor.common.metadata.events.EventHandler;

/** An abstraction to enable different Event Handler store implementations */
public interface EventHandlerDAO {

    /**
     * @param eventHandler Event handler to be added.
     *     <p><em>NOTE:</em> Will throw an exception if an event handler already exists with the
     *     name
     */
    void addEventHandler(EventHandler eventHandler);

    /** @param eventHandler Event handler to be updated. */
    void updateEventHandler(EventHandler eventHandler);

    /** @param name Removes the event handler from the system */
    void removeEventHandler(String name);

    /** @return All the event handlers registered in the system */
    List<EventHandler> getAllEventHandlers();

    /**
     * @param event name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly);
}
