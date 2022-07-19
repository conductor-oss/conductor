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
package com.netflix.conductor.cassandra.config.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.cassandra.dao.CassandraEventHandlerDAO;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.metrics.Monitors;

@Trace
public class CacheableEventHandlerDAO implements EventHandlerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheableEventHandlerDAO.class);

    private static final String CLASS_NAME = CacheableEventHandlerDAO.class.getSimpleName();

    private final CassandraEventHandlerDAO cassandraEventHandlerDAO;
    private final CassandraProperties properties;

    private final Map<String, EventHandler> eventHandlerCache = new ConcurrentHashMap<>();

    public CacheableEventHandlerDAO(
            CassandraEventHandlerDAO cassandraEventHandlerDAO, CassandraProperties properties) {
        this.cassandraEventHandlerDAO = cassandraEventHandlerDAO;
        this.properties = properties;
    }

    @PostConstruct
    public void scheduleEventHandlerRefresh() {
        long cacheRefreshTime = properties.getEventHandlerCacheRefreshInterval().getSeconds();
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(
                        this::refreshEventHandlersCache, 0, cacheRefreshTime, TimeUnit.SECONDS);
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        try {
            cassandraEventHandlerDAO.addEventHandler(eventHandler);
        } finally {
            evictEventHandler(eventHandler);
        }
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        try {
            cassandraEventHandlerDAO.updateEventHandler(eventHandler);
        } finally {
            evictEventHandler(eventHandler);
        }
    }

    @Override
    public void removeEventHandler(String name) {
        try {
            cassandraEventHandlerDAO.removeEventHandler(name);
        } finally {
            eventHandlerCache.remove(name);
        }
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        if (eventHandlerCache.size() == 0) {
            refreshEventHandlersCache();
        }
        return new ArrayList<>(eventHandlerCache.values());
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        if (activeOnly) {
            return getAllEventHandlers().stream()
                    .filter(eventHandler -> eventHandler.getEvent().equals(event))
                    .filter(EventHandler::isActive)
                    .collect(Collectors.toList());
        } else {
            return getAllEventHandlers().stream()
                    .filter(eventHandler -> eventHandler.getEvent().equals(event))
                    .collect(Collectors.toList());
        }
    }

    private void refreshEventHandlersCache() {
        try {
            Map<String, EventHandler> map = new HashMap<>();
            cassandraEventHandlerDAO
                    .getAllEventHandlers()
                    .forEach(eventHandler -> map.put(eventHandler.getName(), eventHandler));
            this.eventHandlerCache.putAll(map);
            LOGGER.debug("Refreshed event handlers, total num: " + this.eventHandlerCache.size());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "refreshEventHandlersCache");
            LOGGER.error("refresh EventHandlers failed", e);
        }
    }

    private void evictEventHandler(EventHandler eventHandler) {
        if (eventHandler != null && eventHandler.getName() != null) {
            eventHandlerCache.remove(eventHandler.getName());
        }
    }
}
