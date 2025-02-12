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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.sqlite.config.SqliteProperties;
import com.netflix.conductor.sqlite.util.ExecutorsUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SqliteQueueDAO extends SqliteBaseDAO implements QueueDAO {

    private static final Long UNACK_SCHEDULE_MS = 60_000L;

    private final ScheduledExecutorService scheduledExecutorService;

    public SqliteQueueDAO(
            RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            SqliteProperties properties) {
        super(retryTemplate, objectMapper, dataSource);

        this.scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(
                        ExecutorsUtil.newNamedThreadFactory("postgres-queue-"));
        this.scheduledExecutorService.scheduleAtFixedRate(
                this::processAllUnacks,
                UNACK_SCHEDULE_MS,
                UNACK_SCHEDULE_MS,
                TimeUnit.MILLISECONDS);
        logger.debug("{} is ready to serve", SqliteBaseDAO.class.getName());

        //        if (properties.getExperimentalQueueNotify()) {
        //            this.queueListener = new PostgresQueueListener(dataSource, properties);
        //        }
    }

    private void processAllUnacks() {}

    @Override
    public void push(String queueName, String id, long offsetTimeInSecond) {}

    @Override
    public void push(String queueName, String id, int priority, long offsetTimeInSecond) {}

    @Override
    public void push(String queueName, List<Message> messages) {}

    @Override
    public boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
        return false;
    }

    @Override
    public boolean pushIfNotExists(
            String queueName, String id, int priority, long offsetTimeInSecond) {
        return false;
    }

    @Override
    public List<String> pop(String queueName, int count, int timeout) {
        return List.of();
    }

    @Override
    public List<Message> pollMessages(String queueName, int count, int timeout) {
        return List.of();
    }

    @Override
    public void remove(String queueName, String messageId) {}

    @Override
    public int getSize(String queueName) {
        return 0;
    }

    @Override
    public boolean ack(String queueName, String messageId) {
        return false;
    }

    @Override
    public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
        return false;
    }

    @Override
    public void flush(String queueName) {}

    @Override
    public Map<String, Long> queuesDetail() {
        return Map.of();
    }

    @Override
    public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        return Map.of();
    }

    @Override
    public boolean resetOffsetTime(String queueName, String id) {
        return false;
    }
}
