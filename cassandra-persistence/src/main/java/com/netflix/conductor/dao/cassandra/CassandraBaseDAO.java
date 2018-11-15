/*
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.cassandra;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.cassandra.CassandraConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.netflix.conductor.util.Constants.CREATE_KEYSPACE_STATEMENT;
import static com.netflix.conductor.util.Constants.CREATE_TASK_LOOKUP_TABLE_STATEMENT;
import static com.netflix.conductor.util.Constants.CREATE_WORKFLOWS_TABLE_STATEMENT;

public class CassandraBaseDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraBaseDAO.class);

    protected final Session session;
    protected final ObjectMapper objectMapper;
    protected final CassandraConfiguration config;

    public CassandraBaseDAO(Session session, ObjectMapper objectMapper, CassandraConfiguration config) {
        this.session = session;
        this.objectMapper = objectMapper;
        this.config = config;

        init();
    }

    private void init() {
        try {
            session.execute(String.format(CREATE_KEYSPACE_STATEMENT, config.getKeyspace()));
            session.execute(String.format(CREATE_WORKFLOWS_TABLE_STATEMENT, config.getKeyspace()));
            session.execute(String.format(CREATE_TASK_LOOKUP_TABLE_STATEMENT, config.getKeyspace()));
            LOGGER.info("CassandraDAO initialization complete! Tables created!");
        } catch (Exception e) {
            LOGGER.error("Error initializing and setting up keyspace and table in cassandra", e);
            throw e;
        }
    }

    String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    <T> T readValue(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class WorkflowMetadata {
        int totalTasks;
        int totalPartitions;

        public int getTotalTasks() {
            return totalTasks;
        }

        public void setTotalTasks(int totalTasks) {
            this.totalTasks = totalTasks;
        }

        public int getTotalPartitions() {
            return totalPartitions;
        }

        public void setTotalPartitions(int totalPartitions) {
            this.totalPartitions = totalPartitions;
        }
    }
}
