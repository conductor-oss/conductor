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
package com.netflix.conductor.scylla.dao;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.scylla.config.ScyllaProperties;
import com.netflix.conductor.scylla.util.Statements;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.metrics.Monitors;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.DriverException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.scylla.util.Constants.EVENT_HANDLER_KEY;
import static com.netflix.conductor.scylla.util.Constants.HANDLERS_KEY;

@Trace
public class ScyllaEventHandlerDAO extends ScyllaBaseDAO implements EventHandlerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScyllaEventHandlerDAO.class);
    private static final String CLASS_NAME = ScyllaEventHandlerDAO.class.getSimpleName();

    private final PreparedStatement insertEventHandlerStatement;
    private final PreparedStatement selectAllEventHandlersStatement;
    private final PreparedStatement deleteEventHandlerStatement;

    public ScyllaEventHandlerDAO(
            Session session,
            ObjectMapper objectMapper,
            ScyllaProperties properties,
            Statements statements) {
        super(session, objectMapper, properties);

        insertEventHandlerStatement =
                session.prepare(statements.getInsertEventHandlerStatement())
                        .setConsistencyLevel(properties.getWriteConsistencyLevel());
        selectAllEventHandlersStatement =
                session.prepare(statements.getSelectAllEventHandlersStatement())
                        .setConsistencyLevel(properties.getReadConsistencyLevel());
        deleteEventHandlerStatement =
                session.prepare(statements.getDeleteEventHandlerStatement())
                        .setConsistencyLevel(properties.getWriteConsistencyLevel());
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
            throw new TransientException(errorMsg, e);
        }
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        return getAllEventHandlersFromDB();
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

    @SuppressWarnings("unchecked")
    private List<EventHandler> getAllEventHandlersFromDB() {
        try {
            ResultSet resultSet =
                    session.execute(selectAllEventHandlersStatement.bind(HANDLERS_KEY));
            List<Row> rows = resultSet.all();
            if (rows.size() == 0) {
                LOGGER.info("No event handlers were found.");
                return Collections.EMPTY_LIST;
            }
            return rows.stream()
                    .map(row -> readValue(row.getString(EVENT_HANDLER_KEY), EventHandler.class))
                    .collect(Collectors.toList());

        } catch (DriverException e) {
            Monitors.error(CLASS_NAME, "getAllEventHandlersFromDB");
            String errorMsg = "Failed to get all event handlers";
            LOGGER.error(errorMsg, e);
            throw new TransientException(errorMsg, e);
        }
    }

    private void insertOrUpdateEventHandler(EventHandler eventHandler) {
        try {
            String handler = toJson(eventHandler);
            session.execute(insertEventHandlerStatement.bind(eventHandler.getName(), handler));
            recordCassandraDaoRequests("storeEventHandler");
            recordCassandraDaoPayloadSize("storeEventHandler", handler.length(), "n/a", "n/a");
        } catch (DriverException e) {
            Monitors.error(CLASS_NAME, "insertOrUpdateEventHandler");
            String errorMsg =
                    String.format(
                            "Error creating/updating event handler: %s/%s",
                            eventHandler.getName(), eventHandler.getEvent());
            LOGGER.error(errorMsg, e);
            throw new TransientException(errorMsg, e);
        }
    }
}
