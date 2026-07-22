/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.cassandra.dao;

import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.cassandra.util.Statements;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Rate limits tasks using a "task_rate_limit" bucket table: one row per task execution within the
 * rate limit frequency window, keyed by a timeuuid and expiring via TTL once the window elapses.
 */
@Trace
public class CassandraRateLimitingDAO extends CassandraBaseDAO implements RateLimitingDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraRateLimitingDAO.class);
    private static final String CLASS_NAME = CassandraRateLimitingDAO.class.getSimpleName();

    private final PreparedStatement selectRateLimitCountStatement;
    private final PreparedStatement insertRateLimitBucketStatement;

    public CassandraRateLimitingDAO(
            Session session,
            ObjectMapper objectMapper,
            CassandraProperties properties,
            Statements statements) {
        super(session, objectMapper, properties);
        this.selectRateLimitCountStatement =
                session.prepare(statements.getSelectRateLimitCountStatement());
        this.insertRateLimitBucketStatement =
                session.prepare(statements.getInsertRateLimitBucketStatement());
    }

    @Override
    public boolean exceedsRateLimitPerFrequency(TaskModel task, TaskDef taskDef) {
        ImmutablePair<Integer, Integer> rateLimitPair =
                Optional.ofNullable(taskDef)
                        .map(
                                definition ->
                                        new ImmutablePair<>(
                                                definition.getRateLimitPerFrequency(),
                                                definition.getRateLimitFrequencyInSeconds()))
                        .orElse(
                                new ImmutablePair<>(
                                        task.getRateLimitPerFrequency(),
                                        task.getRateLimitFrequencyInSeconds()));

        int rateLimitPerFrequency = rateLimitPair.getLeft();
        int rateLimitFrequencyInSeconds = rateLimitPair.getRight();
        if (rateLimitPerFrequency <= 0 || rateLimitFrequencyInSeconds <= 0) {
            LOGGER.debug(
                    "Rate limit not applied to the Task: {} either rateLimitPerFrequency: {} or rateLimitFrequencyInSeconds: {} is 0 or less",
                    task,
                    rateLimitPerFrequency,
                    rateLimitFrequencyInSeconds);
            return false;
        }

        try {
            recordCassandraDaoRequests("exceedsRateLimitPerFrequency");
            long nowMillis = System.currentTimeMillis();
            UUID windowStart = UUIDs.startOf(nowMillis - (rateLimitFrequencyInSeconds * 1000L));
            ResultSet resultSet =
                    session.execute(
                            selectRateLimitCountStatement.bind(task.getTaskDefName(), windowStart));
            long currentBucketCount = resultSet.one().getLong(0);

            if (currentBucketCount < rateLimitPerFrequency) {
                session.execute(
                        insertRateLimitBucketStatement.bind(
                                task.getTaskDefName(),
                                UUIDs.timeBased(),
                                rateLimitFrequencyInSeconds));
                LOGGER.info(
                        "TaskId: {} with TaskDefinition of: {} has rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} within the rate limit with current count {}",
                        task.getTaskId(),
                        task.getTaskDefName(),
                        rateLimitPerFrequency,
                        rateLimitFrequencyInSeconds,
                        currentBucketCount + 1);
                Monitors.recordTaskRateLimited(task.getTaskDefName(), rateLimitPerFrequency);
                return false;
            } else {
                LOGGER.info(
                        "TaskId: {} with TaskDefinition of: {} has rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} is out of bounds of rate limit with current count {}",
                        task.getTaskId(),
                        task.getTaskDefName(),
                        rateLimitPerFrequency,
                        rateLimitFrequencyInSeconds,
                        currentBucketCount);
                return true;
            }
        } catch (DriverException e) {
            Monitors.error(CLASS_NAME, "exceedsRateLimitPerFrequency");
            String errorMsg =
                    String.format(
                            "Failed to check rate limit for task: %s in taskDef: %s",
                            task.getTaskId(), task.getTaskDefName());
            LOGGER.error(errorMsg, e);
            throw new TransientException(errorMsg, e);
        }
    }
}
