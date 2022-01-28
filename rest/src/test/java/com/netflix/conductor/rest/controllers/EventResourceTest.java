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
package com.netflix.conductor.rest.controllers;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.service.EventService;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventResourceTest {

    private EventResource eventResource;

    @Mock private EventService mockEventService;

    @Before
    public void setUp() {
        this.mockEventService = mock(EventService.class);
        this.eventResource = new EventResource(this.mockEventService);
    }

    @Test
    public void testAddEventHandler() {
        EventHandler eventHandler = new EventHandler();
        eventResource.addEventHandler(eventHandler);
        verify(mockEventService, times(1)).addEventHandler(any(EventHandler.class));
    }

    @Test
    public void testUpdateEventHandler() {
        EventHandler eventHandler = new EventHandler();
        eventResource.updateEventHandler(eventHandler);
        verify(mockEventService, times(1)).updateEventHandler(any(EventHandler.class));
    }

    @Test
    public void testRemoveEventHandlerStatus() {
        eventResource.removeEventHandlerStatus("testEvent");
        verify(mockEventService, times(1)).removeEventHandlerStatus(anyString());
    }

    @Test
    public void testGetEventHandlersForEvent() {
        EventHandler eventHandler = new EventHandler();
        eventResource.addEventHandler(eventHandler);
        List<EventHandler> listOfEventHandler = new ArrayList<>();
        listOfEventHandler.add(eventHandler);
        when(mockEventService.getEventHandlersForEvent(anyString(), anyBoolean()))
                .thenReturn(listOfEventHandler);
        assertEquals(listOfEventHandler, eventResource.getEventHandlersForEvent("testEvent", true));
    }

    @Test
    public void testGetEventHandlers() {
        EventHandler eventHandler = new EventHandler();
        eventResource.addEventHandler(eventHandler);
        List<EventHandler> listOfEventHandler = new ArrayList<>();
        listOfEventHandler.add(eventHandler);
        when(mockEventService.getEventHandlers()).thenReturn(listOfEventHandler);
        assertEquals(listOfEventHandler, eventResource.getEventHandlers());
    }
}
