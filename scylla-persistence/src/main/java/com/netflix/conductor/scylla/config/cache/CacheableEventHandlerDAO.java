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
package com.netflix.conductor.scylla.config.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.netflix.conductor.scylla.config.ScyllaProperties;
import com.netflix.conductor.scylla.dao.ScyllaEventHandlerDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.metrics.Monitors;

import static com.netflix.conductor.scylla.config.cache.CachingConfig.EVENT_HANDLER_CACHE;

@Trace
public class CacheableEventHandlerDAO implements EventHandlerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheableEventHandlerDAO.class);

    private static final String CLASS_NAME = CacheableEventHandlerDAO.class.getSimpleName();

    private final ScyllaEventHandlerDAO scyllaEventHandlerDAO;
    private final ScyllaProperties properties;

    private final CacheManager cacheManager;

    public CacheableEventHandlerDAO(
            ScyllaEventHandlerDAO scyllaEventHandlerDAO,
            ScyllaProperties properties,
            CacheManager cacheManager) {
        this.scyllaEventHandlerDAO = scyllaEventHandlerDAO;
        this.properties = properties;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    public void scheduleEventHandlerRefresh() {
        long cacheRefreshTime = properties.getEventHandlerCacheRefreshInterval().getSeconds();
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(
                        this::refreshEventHandlersCache, 0, cacheRefreshTime, TimeUnit.SECONDS);
    }

    @Override
    @CachePut(value = EVENT_HANDLER_CACHE, key = "#eventHandler.name")
    public void addEventHandler(EventHandler eventHandler) {
        scyllaEventHandlerDAO.addEventHandler(eventHandler);
    }

    @Override
    @CachePut(value = EVENT_HANDLER_CACHE, key = "#eventHandler.name")
    public void updateEventHandler(EventHandler eventHandler) {
        scyllaEventHandlerDAO.updateEventHandler(eventHandler);
    }

    @Override
    @CacheEvict(EVENT_HANDLER_CACHE)
    public void removeEventHandler(String name) {
        scyllaEventHandlerDAO.removeEventHandler(name);
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        Object nativeCache = cacheManager.getCache(EVENT_HANDLER_CACHE).getNativeCache();
        if (nativeCache != null && nativeCache instanceof ConcurrentHashMap) {
            ConcurrentHashMap cacheMap = (ConcurrentHashMap) nativeCache;
            if (!cacheMap.isEmpty()) {
                List<EventHandler> eventHandlers = new ArrayList<>();
                cacheMap.values().stream()
                        .filter(element -> element != null && element instanceof EventHandler)
                        .forEach(element -> eventHandlers.add((EventHandler) element));
                return eventHandlers;
            }
        }

        return refreshEventHandlersCache();
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

    private List<EventHandler> refreshEventHandlersCache() {
        try {
            Cache eventHandlersCache = cacheManager.getCache(EVENT_HANDLER_CACHE);
            eventHandlersCache.clear();
            List<EventHandler> eventHandlers = scyllaEventHandlerDAO.getAllEventHandlers();
            eventHandlers.forEach(
                    eventHandler -> eventHandlersCache.put(eventHandler.getName(), eventHandler));
            LOGGER.debug("Refreshed event handlers, total num: " + eventHandlers.size());
            return eventHandlers;
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "refreshEventHandlersCache");
            LOGGER.error("refresh EventHandlers failed", e);
        }
        return Collections.emptyList();
    }
}
