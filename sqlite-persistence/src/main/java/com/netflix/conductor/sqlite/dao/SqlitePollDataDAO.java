/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.dao.PollDataDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class SqlitePollDataDAO extends SqliteBaseDAO implements PollDataDAO {

    public SqlitePollDataDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");

        String effectiveDomain = domain == null ? "DEFAULT" : domain;
        PollData pollData = new PollData(taskDefName, domain, workerId, System.currentTimeMillis());

        withTransaction(tx -> insertOrUpdatePollData(tx, pollData, effectiveDomain));
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
        String effectiveDomain = (domain == null) ? "DEFAULT" : domain;
        return getWithRetriedTransactions(tx -> readPollData(tx, taskDefName, effectiveDomain));
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
        return readAllPollData(taskDefName);
    }

    @Override
    public List<PollData> getAllPollData() {
        try (Connection tx = dataSource.getConnection()) {
            boolean previousAutoCommitMode = tx.getAutoCommit();
            tx.setAutoCommit(true);
            try {
                String GET_ALL_POLL_DATA = "SELECT json_data FROM poll_data ORDER BY queue_name";
                return query(tx, GET_ALL_POLL_DATA, q -> q.executeAndFetch(PollData.class));
            } catch (Throwable th) {
                throw new NonTransientException(th.getMessage(), th);
            } finally {
                tx.setAutoCommit(previousAutoCommitMode);
            }
        } catch (SQLException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        }
    }

    private void insertOrUpdatePollData(Connection connection, PollData pollData, String domain) {
        try {
            /*
             * Most times the row will be updated so let's try the update first. This used to be an 'INSERT/ON CONFLICT do update' sql statement. The problem with that
             * is that if we try the INSERT first, the sequence will be increased even if the ON CONFLICT happens. Since polling happens *a lot*, the sequence can increase
             * dramatically even though it won't be used.
             */
            String UPDATE_POLL_DATA =
                    "UPDATE poll_data SET json_data=?, modified_on=CURRENT_TIMESTAMP WHERE queue_name=? AND domain=?";
            int rowsUpdated =
                    query(
                            connection,
                            UPDATE_POLL_DATA,
                            q ->
                                    q.addJsonParameter(pollData)
                                            .addParameter(pollData.getQueueName())
                                            .addParameter(domain)
                                            .executeUpdate());

            if (rowsUpdated == 0) {
                String INSERT_POLL_DATA =
                        "INSERT INTO poll_data (queue_name, domain, json_data, modified_on) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (queue_name,domain) DO UPDATE SET json_data=excluded.json_data, modified_on=excluded.modified_on";
                execute(
                        connection,
                        INSERT_POLL_DATA,
                        q ->
                                q.addParameter(pollData.getQueueName())
                                        .addParameter(domain)
                                        .addJsonParameter(pollData)
                                        .executeUpdate());
            }
        } catch (NonTransientException e) {
            if (!e.getMessage().startsWith("ERROR: lastPollTime cannot be set to a lower value")) {
                throw e;
            }
        }
    }

    private PollData readPollData(Connection connection, String queueName, String domain) {
        String GET_POLL_DATA =
                "SELECT json_data FROM poll_data WHERE queue_name = ? AND domain = ?";
        return query(
                connection,
                GET_POLL_DATA,
                q ->
                        q.addParameter(queueName)
                                .addParameter(domain)
                                .executeAndFetchFirst(PollData.class));
    }

    private List<PollData> readAllPollData(String queueName) {
        String GET_ALL_POLL_DATA = "SELECT json_data FROM poll_data WHERE queue_name = ?";
        return queryWithTransaction(
                GET_ALL_POLL_DATA, q -> q.addParameter(queueName).executeAndFetch(PollData.class));
    }
}
