/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dao.cassandra;

import static com.netflix.conductor.util.Constants.EVENT_HANDLER_KEY;
import static com.netflix.conductor.util.Constants.HANDLERS_KEY;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.cassandra.CassandraConfiguration;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.util.Statements;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Trace
public class CassandraEventHandlerDAO extends CassandraBaseDAO implements EventHandlerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraEventHandlerDAO.class);
    private static final String CLASS_NAME = CassandraEventHandlerDAO.class.getSimpleName();

    private volatile Map<String, EventHandler> eventHandlerCache = new HashMap<>();

    private final PreparedStatement insertEventHandlerStatement;
    private final PreparedStatement selectAllEventHandlersStatement;
    private final PreparedStatement deleteEventHandlerStatement;

    @Inject
    public CassandraEventHandlerDAO(Session session, ObjectMapper objectMapper, CassandraConfiguration config,
        Statements statements) {
        super(session, objectMapper, config);

        insertEventHandlerStatement = session.prepare(statements.getInsertEventHandlerStatement())
            .setConsistencyLevel(config.getWriteConsistencyLevel());
        selectAllEventHandlersStatement = session.prepare(statements.getSelectAllEventHandlersStatement())
            .setConsistencyLevel(config.getReadConsistencyLevel());
        deleteEventHandlerStatement = session.prepare(statements.getDeleteEventHandlerStatement())
            .setConsistencyLevel(config.getWriteConsistencyLevel());

        int cacheRefreshTime = config.getEventHandlerRefreshTimeSecsDefaultValue();
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::refreshEventHandlersCache, 0,
            cacheRefreshTime, TimeUnit.SECONDS);
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        insertOrUpdateEventHandler(eventHandler);
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        insertOrUpdateEventHandler(eventHandler);
    }

    @Override
    public void removeEventHandler(String name) {
        try {
            recordCassandraDaoRequests("removeEventHandler");
            session.execute(deleteEventHandlerStatement.bind(name));
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "removeEventHandler");
            String errorMsg = String.format("Failed to remove event handler: %s", name);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
        refreshEventHandlersCache();
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
        if (session.isClosed()) {
            LOGGER.warn("session is closed");
            return;
        }
        try {
            Map<String, EventHandler> map = new HashMap<>();
            getAllEventHandlersFromDB().forEach(eventHandler -> map.put(eventHandler.getName(), eventHandler));
            this.eventHandlerCache = map;
            LOGGER.debug("Refreshed event handlers, total num: " + this.eventHandlerCache.size());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "refreshEventHandlersCache");
            LOGGER.error("refresh EventHandlers failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EventHandler> getAllEventHandlersFromDB() {
        try {
            ResultSet resultSet = session.execute(selectAllEventHandlersStatement.bind(HANDLERS_KEY));
            List<Row> rows = resultSet.all();
            if (rows.size() == 0) {
                LOGGER.info("No event handlers were found.");
                return Collections.EMPTY_LIST;
            }
            return rows.stream()
                .map(row -> readValue(row.getString(EVENT_HANDLER_KEY), EventHandler.class))
                .collect(Collectors.toList());

        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getAllEventHandlersFromDB");
            String errorMsg = "Failed to get all event handlers";
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    private void insertOrUpdateEventHandler(EventHandler eventHandler) {
        try {
            String handler = toJson(eventHandler);
            session.execute(insertEventHandlerStatement.bind(eventHandler.getName(), handler));
            recordCassandraDaoRequests("storeEventHandler");
            recordCassandraDaoPayloadSize("storeEventHandler", handler.length(), "n/a", "n/a");
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "insertOrUpdateEventHandler");
            String errorMsg = String.format("Error creating/updating event handler: %s/%s",
                eventHandler.getName(), eventHandler.getEvent());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(Code.BACKEND_ERROR, errorMsg, e);
        }
        refreshEventHandlersCache();
    }
}
