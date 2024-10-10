/*
 * Copyright 2022 Conductor Authors.
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

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.scylla.config.ScyllaProperties;
import com.netflix.conductor.scylla.util.Statements;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.DriverException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.scylla.util.Constants.DOMAIN_KEY;
import static com.netflix.conductor.scylla.util.Constants.LAST_POLL_TIME_KEY;
import static com.netflix.conductor.scylla.util.Constants.TASK_DEF_NAME_KEY;
import static com.netflix.conductor.scylla.util.Constants.WORKER_ID_KEY;

/**
 * This is a dummy implementation and this feature is not implemented for Cassandra backed
 * Conductor.
 */
public class ScyllaPollDataDAO extends ScyllaBaseDAO implements PollDataDAO {

    protected final PreparedStatement updateLastPollDataStatement;

    private static final String CLASS_NAME = ScyllaPollDataDAO.class.getSimpleName();

    private final PreparedStatement selectPollDataStatement;
    private final PreparedStatement selectAllPollDataStatement;

    public ScyllaPollDataDAO(
            Session session,
            ObjectMapper objectMapper,
            ScyllaProperties properties,
            Statements statements) {
        super(session, objectMapper, properties);
        this.updateLastPollDataStatement =
                session.prepare(statements.getInsertPollDataStatement())
                        .setConsistencyLevel(properties.getWriteConsistencyLevel());
        this.selectPollDataStatement =
                session.prepare(statements.getSelectPollDataByTaskDefNameAndDomainStatement())
                        .setConsistencyLevel(properties.getReadConsistencyLevel());
        this.selectAllPollDataStatement =
                session.prepare(statements.getSelectAllPollDataByTaskDefNameStatement())
                        .setConsistencyLevel(properties.getReadConsistencyLevel());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ScyllaPollDataDAO.class);

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        try {
            session.execute(
                    updateLastPollDataStatement.bind(
                            taskDefName, domain, workerId, System.currentTimeMillis()));
            recordCassandraDaoRequests("storeEventHandler");
        } catch (DriverException e) {
            Monitors.error(CLASS_NAME, "insertOrUpdateEventHandler");
            String errorMsg =
                    String.format(
                            "Error updating last poll data: %s/%s/%s",
                            taskDefName, domain, workerId);
            LOGGER.error(errorMsg, e);
        }
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        try {
            ResultSet resultSet = session.execute(selectPollDataStatement.bind(taskDefName));
            List<Row> rows = resultSet.all();
            if (rows.size() == 0) {
                LOGGER.info(String.format("No last poll data for : %s/%s", taskDefName, domain));
                return null;
            }
            return rows.stream().map(this::mapToPollData).collect(Collectors.toList()).get(0);

        } catch (DriverException e) {
            Monitors.error(CLASS_NAME, "getPollData");
            String errorMsg = "Failed to get all last poll data";
            LOGGER.error(errorMsg, e);
            return null;
        }
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        try {
            ResultSet resultSet = session.execute(selectAllPollDataStatement.bind(taskDefName));
            List<Row> rows = resultSet.all();
            if (rows.size() == 0) {
                LOGGER.info(String.format("No last poll data for : %s", taskDefName));
                return Collections.EMPTY_LIST;
            }
            return rows.stream().map(this::mapToPollData).collect(Collectors.toList());

        } catch (DriverException e) {
            Monitors.error(CLASS_NAME, "getPollData");
            String errorMsg = String.format("Failed to get last poll data for : %s", taskDefName);
            LOGGER.error(errorMsg, e);
            return null;
        }
    }

    private PollData mapToPollData(Row row) {
        return new PollData(
                row.getString(TASK_DEF_NAME_KEY),
                row.getString(DOMAIN_KEY),
                row.getString(WORKER_ID_KEY),
                row.getLong(LAST_POLL_TIME_KEY));
    }
}
