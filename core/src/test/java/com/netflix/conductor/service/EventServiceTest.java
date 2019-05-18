package com.netflix.conductor.service;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import com.netflix.conductor.annotations.Service;
import com.netflix.conductor.core.config.ValidationModule;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.SimpleEventProcessor;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.interceptors.ServiceInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.Set;

import static com.netflix.conductor.utility.TestUtils.getConstraintViolationMessages;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventServiceTest {

    private MetadataService metadataService;
    private EventProcessor eventProcessor;
    private EventQueues eventQueues;
    private EventService eventService;

    @Before
    public void before() {
        metadataService = Mockito.mock(MetadataService.class);
        eventProcessor = Mockito.mock(SimpleEventProcessor.class);
        eventQueues = Mockito.mock(EventQueues.class);

        Injector injector =
                Guice.createInjector(
                        new AbstractModule() {
                            @Override
                            protected void configure() {

                                bind(MetadataService.class).toInstance(metadataService);
                                bind(EventProcessor.class).toInstance(eventProcessor);
                                bind(EventQueues.class).toInstance(eventQueues);

                                install(new ValidationModule());
                                bindInterceptor(Matchers.any(), Matchers.annotatedWith(Service.class), new ServiceInterceptor(getProvider(Validator.class)));
                            }
                        });
        eventService = injector.getInstance(EventServiceImpl.class);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testAddEventHandler(){
        try{
            eventService.addEventHandler(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("EventHandler cannot be null."));
            throw ex;
        }
        fail("eventService.addEventHandler did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateEventHandler(){
        try{
            eventService.updateEventHandler(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("EventHandler cannot be null."));
            throw ex;
        }
        fail("eventService.updateEventHandler did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRemoveEventHandlerStatus(){
        try{
            eventService.removeEventHandlerStatus(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("EventHandler name cannot be null or empty."));
            throw ex;
        }
        fail("eventService.removeEventHandlerStatus did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetEventHandlersForEvent(){
        try{
            eventService.getEventHandlersForEvent(null, false);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Event cannot be null or empty."));
            throw ex;
        }
        fail("eventService.getEventHandlersForEvent did not throw ConstraintViolationException !");
    }
}
