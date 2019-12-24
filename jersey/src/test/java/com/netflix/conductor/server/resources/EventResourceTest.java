package com.netflix.conductor.server.resources;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.service.EventService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventResourceTest {

    private EventResource eventResource;

    @Mock
    private EventService mockEventService;
    @Before
    public void setUp() throws Exception {
        this.mockEventService = Mockito.mock(EventService.class);
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
        when(mockEventService.getEventHandlersForEvent(anyString(), anyBoolean())).thenReturn(listOfEventHandler);
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

    @Test
    public void testGetEventQueues() {
        eventResource.getEventQueues(false);
        verify(mockEventService, times(1)).getEventQueues(anyBoolean());

    }

    @Test
    public void getEventQueueProviders() {
        List<String> queuesList = new ArrayList<>();
        when(mockEventService.getEventQueueProviders()).thenReturn(queuesList);
        assertEquals(queuesList, eventResource.getEventQueueProviders());
    }
}
