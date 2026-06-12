/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.redis.dao;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.Action.Type;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisEventHandlerDAOTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisEventHandlerDAO redisEventHandlerDAO;
    private JedisPool jedisPool;

    @BeforeAll
    void setUp() {
        redis.start();

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);

        jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        ConductorProperties conductorProperties = new ConductorProperties();
        RedisProperties redisProperties = new RedisProperties(conductorProperties);

        redisEventHandlerDAO =
                new RedisEventHandlerDAO(
                        jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @AfterAll
    void tearDown() {
        redis.stop();
    }

    @BeforeEach
    void cleanUp() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    @Test
    void testAddAndGetEventHandler() {
        EventHandler eventHandler = createEventHandler("handler1", "SQS::arn:test:queue1");

        redisEventHandlerDAO.addEventHandler(eventHandler);

        List<EventHandler> allHandlers = redisEventHandlerDAO.getAllEventHandlers();
        assertNotNull(allHandlers);
        assertEquals(1, allHandlers.size());
        assertEquals(eventHandler.getName(), allHandlers.get(0).getName());
        assertEquals(eventHandler.getEvent(), allHandlers.get(0).getEvent());
    }

    @Test
    void testEventHandlers() {
        String event1 = "SQS::arn:account090:sqstest1";
        String event2 = "SQS::arn:account090:sqstest2";

        EventHandler eventHandler = new EventHandler();
        eventHandler.setName(UUID.randomUUID().toString());
        eventHandler.setActive(false);
        Action action = new Action();
        action.setAction(Type.start_workflow);
        action.setStart_workflow(new StartWorkflow());
        action.getStart_workflow().setName("test_workflow");
        eventHandler.getActions().add(action);
        eventHandler.setEvent(event1);

        redisEventHandlerDAO.addEventHandler(eventHandler);
        List<EventHandler> allEventHandlers = redisEventHandlerDAO.getAllEventHandlers();
        assertNotNull(allEventHandlers);
        assertEquals(1, allEventHandlers.size());
        assertEquals(eventHandler.getName(), allEventHandlers.get(0).getName());
        assertEquals(eventHandler.getEvent(), allEventHandlers.get(0).getEvent());

        List<EventHandler> byEvents = redisEventHandlerDAO.getEventHandlersForEvent(event1, true);
        assertNotNull(byEvents);
        assertEquals(0, byEvents.size()); // event is marked as in-active

        eventHandler.setActive(true);
        eventHandler.setEvent(event2);
        redisEventHandlerDAO.updateEventHandler(eventHandler);

        allEventHandlers = redisEventHandlerDAO.getAllEventHandlers();
        assertNotNull(allEventHandlers);
        assertEquals(1, allEventHandlers.size());

        byEvents = redisEventHandlerDAO.getEventHandlersForEvent(event1, true);
        assertNotNull(byEvents);
        assertEquals(0, byEvents.size());

        byEvents = redisEventHandlerDAO.getEventHandlersForEvent(event2, true);
        assertNotNull(byEvents);
        assertEquals(1, byEvents.size());
    }

    @Test
    void testUpdateEventHandler() {
        EventHandler eventHandler = createEventHandler("handler2", "SQS::arn:test:queue2");
        redisEventHandlerDAO.addEventHandler(eventHandler);

        eventHandler.setActive(true);
        redisEventHandlerDAO.updateEventHandler(eventHandler);

        List<EventHandler> handlers =
                redisEventHandlerDAO.getEventHandlersForEvent("SQS::arn:test:queue2", true);
        assertEquals(1, handlers.size());
        assertTrue(handlers.get(0).isActive());
    }

    @Test
    void testUpdateEventHandlerChangeEvent() {
        String oldEvent = "SQS::arn:test:old_queue";
        String newEvent = "SQS::arn:test:new_queue";

        EventHandler eventHandler = createEventHandler("handler3", oldEvent);
        eventHandler.setActive(true);
        redisEventHandlerDAO.addEventHandler(eventHandler);

        assertEquals(1, redisEventHandlerDAO.getEventHandlersForEvent(oldEvent, true).size());

        eventHandler.setEvent(newEvent);
        redisEventHandlerDAO.updateEventHandler(eventHandler);

        assertEquals(0, redisEventHandlerDAO.getEventHandlersForEvent(oldEvent, true).size());
        assertEquals(1, redisEventHandlerDAO.getEventHandlersForEvent(newEvent, true).size());
    }

    @Test
    void testRemoveEventHandler() {
        EventHandler eventHandler = createEventHandler("handler4", "SQS::arn:test:queue4");
        redisEventHandlerDAO.addEventHandler(eventHandler);

        assertEquals(1, redisEventHandlerDAO.getAllEventHandlers().size());

        redisEventHandlerDAO.removeEventHandler(eventHandler.getName());

        assertEquals(0, redisEventHandlerDAO.getAllEventHandlers().size());
    }

    @Test
    void testAddDuplicateEventHandler() {
        EventHandler eventHandler = createEventHandler("duplicate_handler", "SQS::arn:test:q");
        redisEventHandlerDAO.addEventHandler(eventHandler);

        assertThrows(
                ConflictException.class, () -> redisEventHandlerDAO.addEventHandler(eventHandler));
    }

    @Test
    void testUpdateNonExistentEventHandler() {
        EventHandler eventHandler = createEventHandler("nonexistent", "SQS::arn:test:q");

        assertThrows(
                NotFoundException.class,
                () -> redisEventHandlerDAO.updateEventHandler(eventHandler));
    }

    @Test
    void testRemoveNonExistentEventHandler() {
        assertThrows(
                NotFoundException.class,
                () -> redisEventHandlerDAO.removeEventHandler("nonexistent"));
    }

    @Test
    void testGetEventHandlersForEventActiveOnly() {
        String event = "SQS::arn:test:active_test";

        EventHandler active = createEventHandler("active_handler", event);
        active.setActive(true);
        redisEventHandlerDAO.addEventHandler(active);

        EventHandler inactive = createEventHandler("inactive_handler", event);
        inactive.setActive(false);
        redisEventHandlerDAO.addEventHandler(inactive);

        List<EventHandler> activeHandlers =
                redisEventHandlerDAO.getEventHandlersForEvent(event, true);
        assertEquals(1, activeHandlers.size());
        assertEquals("active_handler", activeHandlers.get(0).getName());

        List<EventHandler> allHandlers =
                redisEventHandlerDAO.getEventHandlersForEvent(event, false);
        assertEquals(2, allHandlers.size());
    }

    @Test
    void testGetAllEventHandlersMultiple() {
        for (int i = 0; i < 5; i++) {
            EventHandler handler = createEventHandler("handler_" + i, "SQS::arn:test:queue_" + i);
            redisEventHandlerDAO.addEventHandler(handler);
        }

        List<EventHandler> allHandlers = redisEventHandlerDAO.getAllEventHandlers();
        assertEquals(5, allHandlers.size());
    }

    private EventHandler createEventHandler(String name, String event) {
        EventHandler handler = new EventHandler();
        handler.setName(name);
        handler.setEvent(event);
        handler.setActive(false);
        Action action = new Action();
        action.setAction(Type.start_workflow);
        action.setStart_workflow(new StartWorkflow());
        action.getStart_workflow().setName("test_workflow");
        handler.getActions().add(action);
        return handler;
    }
}
