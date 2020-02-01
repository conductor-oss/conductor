/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.Action.Type;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.redis.JedisMock;
import com.netflix.conductor.dyno.DynoProxy;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.commands.JedisCommands;

public class RedisEventHandlerDAOTest {

    private RedisEventHandlerDAO redisEventHandlerDAO;

    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    @Before
    public void init() {
        Configuration config = new TestConfiguration();
        JedisCommands jedisMock = new JedisMock();
        DynoProxy dynoClient = new DynoProxy(jedisMock);

        redisEventHandlerDAO = new RedisEventHandlerDAO(dynoClient, objectMapper, config);
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
        assertEquals(0, byEvents.size());        //event is marked as in-active

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
