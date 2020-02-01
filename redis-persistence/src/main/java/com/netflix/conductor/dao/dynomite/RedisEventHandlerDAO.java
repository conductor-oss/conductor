/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dyno.DynoProxy;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Trace
public class RedisEventHandlerDAO extends BaseDynoDAO implements EventHandlerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisEventHandlerDAO.class);

    private final static String EVENT_HANDLERS = "EVENT_HANDLERS";
    private final static String EVENT_HANDLERS_BY_EVENT = "EVENT_HANDLERS_BY_EVENT";

    @Inject
    protected RedisEventHandlerDAO(DynoProxy dynoClient, ObjectMapper objectMapper, Configuration config) {
        super(dynoClient, objectMapper, config);
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler.getName(), "Missing Name");
        if(getEventHandler(eventHandler.getName()) != null) {
            throw new ApplicationException(Code.CONFLICT, "EventHandler with name " + eventHandler.getName() + " already exists!");
        }
        index(eventHandler);
        dynoClient.hset(nsKey(EVENT_HANDLERS), eventHandler.getName(), toJson(eventHandler));
        recordRedisDaoRequests("addEventHandler");
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler.getName(), "Missing Name");
        EventHandler existing = getEventHandler(eventHandler.getName());
        if(existing == null) {
            throw new ApplicationException(Code.NOT_FOUND, "EventHandler with name " + eventHandler.getName() + " not found!");
        }
        index(eventHandler);
        dynoClient.hset(nsKey(EVENT_HANDLERS), eventHandler.getName(), toJson(eventHandler));
        recordRedisDaoRequests("updateEventHandler");
    }

    @Override
    public void removeEventHandler(String name) {
        EventHandler existing = getEventHandler(name);
        if(existing == null) {
            throw new ApplicationException(Code.NOT_FOUND, "EventHandler with name " + name + " not found!");
        }
        dynoClient.hdel(nsKey(EVENT_HANDLERS), name);
        recordRedisDaoRequests("removeEventHandler");
        removeIndex(existing);
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        Map<String, String> all = dynoClient.hgetAll(nsKey(EVENT_HANDLERS));
        List<EventHandler> handlers = new LinkedList<>();
        all.forEach((key, json) -> {
            EventHandler eventHandler = readValue(json, EventHandler.class);
            handlers.add(eventHandler);
        });
        recordRedisDaoRequests("getAllEventHandlers");
        return handlers;
    }

    private void index(EventHandler eventHandler) {
        String event = eventHandler.getEvent();
        String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
        dynoClient.sadd(key, eventHandler.getName());
    }

    private void removeIndex(EventHandler eventHandler) {
        String event = eventHandler.getEvent();
        String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
        dynoClient.srem(key, eventHandler.getName());
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        String key = nsKey(EVENT_HANDLERS_BY_EVENT, event);
        Set<String> names = dynoClient.smembers(key);
        List<EventHandler> handlers = new LinkedList<>();
        for(String name : names) {
            try {
                EventHandler eventHandler = getEventHandler(name);
                recordRedisDaoEventRequests("getEventHandler", event);
                if(eventHandler.getEvent().equals(event) && (!activeOnly || eventHandler.isActive())) {
                    handlers.add(eventHandler);
                }
            } catch (ApplicationException ae) {
                if(ae.getCode() == Code.NOT_FOUND) {
                    LOGGER.info("No matching event handler found for event: {}", event);
                }
                throw ae;
            }
        }
        return handlers;
    }

    private EventHandler getEventHandler(String name) {
        EventHandler eventHandler = null;
        String json = dynoClient.hget(nsKey(EVENT_HANDLERS), name);
        if (json != null) {
            eventHandler = readValue(json, EventHandler.class);
        }
        return eventHandler;
    }
}
