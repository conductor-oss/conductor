/*
 * Copyright 2021 Netflix, Inc.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.Action.Type;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.commands.JedisCommands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class RedisEventHandlerDAOTest {

    private RedisEventHandlerDAO redisEventHandlerDAO;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void init() {
        ConductorProperties conductorProperties = mock(ConductorProperties.class);
        RedisProperties properties = mock(RedisProperties.class);
        JedisCommands jedisMock = new JedisMock();
        JedisProxy jedisProxy = new JedisProxy(jedisMock);

        redisEventHandlerDAO =
                new RedisEventHandlerDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Test
    public void testEventHandlers() {
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
}
