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
package com.netflix.conductor.cassandra.dao;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.metrics.Monitors;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import static com.netflix.conductor.cassandra.util.Constants.DAO_NAME;
import static com.netflix.conductor.cassandra.util.Constants.ENTITY_KEY;
import static com.netflix.conductor.cassandra.util.Constants.EVENT_EXECUTION_ID_KEY;
import static com.netflix.conductor.cassandra.util.Constants.EVENT_HANDLER_KEY;
import static com.netflix.conductor.cassandra.util.Constants.EVENT_HANDLER_NAME_KEY;
import static com.netflix.conductor.cassandra.util.Constants.HANDLERS_KEY;
import static com.netflix.conductor.cassandra.util.Constants.MESSAGE_ID_KEY;
import static com.netflix.conductor.cassandra.util.Constants.PAYLOAD_KEY;
import static com.netflix.conductor.cassandra.util.Constants.SHARD_ID_KEY;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_EVENT_EXECUTIONS;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_EVENT_HANDLERS;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_TASK_DEFS;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_TASK_DEF_LIMIT;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_TASK_LOOKUP;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_WORKFLOWS;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_WORKFLOW_DEFS;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_WORKFLOW_DEFS_INDEX;
import static com.netflix.conductor.cassandra.util.Constants.TASK_DEFINITION_KEY;
import static com.netflix.conductor.cassandra.util.Constants.TASK_DEFS_KEY;
import static com.netflix.conductor.cassandra.util.Constants.TASK_DEF_NAME_KEY;
import static com.netflix.conductor.cassandra.util.Constants.TASK_ID_KEY;
import static com.netflix.conductor.cassandra.util.Constants.TOTAL_PARTITIONS_KEY;
import static com.netflix.conductor.cassandra.util.Constants.TOTAL_TASKS_KEY;
import static com.netflix.conductor.cassandra.util.Constants.WORKFLOW_DEFINITION_KEY;
import static com.netflix.conductor.cassandra.util.Constants.WORKFLOW_DEF_INDEX_KEY;
import static com.netflix.conductor.cassandra.util.Constants.WORKFLOW_DEF_INDEX_VALUE;
import static com.netflix.conductor.cassandra.util.Constants.WORKFLOW_DEF_NAME_KEY;
import static com.netflix.conductor.cassandra.util.Constants.WORKFLOW_DEF_NAME_VERSION_KEY;
import static com.netflix.conductor.cassandra.util.Constants.WORKFLOW_ID_KEY;
import static com.netflix.conductor.cassandra.util.Constants.WORKFLOW_VERSION_KEY;

/**
 * Creates the keyspace and tables.
 *
 * <p>CREATE KEYSPACE IF NOT EXISTS conductor WITH replication = { 'class' :
 * 'NetworkTopologyStrategy', 'us-east': '3'};
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.workflows ( workflow_id uuid, shard_id int, task_id text,
 * entity text, payload text, total_tasks int STATIC, total_partitions int STATIC, PRIMARY
 * KEY((workflow_id, shard_id), entity, task_id) );
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.task_lookup( task_id uuid, workflow_id uuid, PRIMARY KEY
 * (task_id) );
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.task_def_limit( task_def_name text, task_id uuid,
 * workflow_id uuid, PRIMARY KEY ((task_def_name), task_id_key) );
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.workflow_definitions( workflow_def_name text, version
 * int, workflow_definition text, PRIMARY KEY ((workflow_def_name), version) );
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.workflow_defs_index( workflow_def_version_index text,
 * workflow_def_name_version text, workflow_def_index_value text,PRIMARY KEY
 * ((workflow_def_version_index), workflow_def_name_version) );
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.task_definitions( task_defs text, task_def_name text,
 * task_definition text, PRIMARY KEY ((task_defs), task_def_name) );
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.event_handlers( handlers text, event_handler_name text,
 * event_handler text, PRIMARY KEY ((handlers), event_handler_name) );
 *
 * <p>CREATE TABLE IF NOT EXISTS conductor.event_executions( message_id text, event_handler_name
 * text, event_execution_id text, payload text, PRIMARY KEY ((message_id, event_handler_name),
 * event_execution_id) );
 */
public abstract class CassandraBaseDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraBaseDAO.class);

    private final ObjectMapper objectMapper;
    protected final Session session;
    protected final CassandraProperties properties;

    private boolean initialized = false;

    public CassandraBaseDAO(
            Session session, ObjectMapper objectMapper, CassandraProperties properties) {
        this.session = session;
        this.objectMapper = objectMapper;
        this.properties = properties;

        init();
    }

    private void init() {
        try {
            if (!initialized) {
                session.execute(getCreateKeyspaceStatement());
                session.execute(getCreateWorkflowsTableStatement());
                session.execute(getCreateTaskLookupTableStatement());
                session.execute(getCreateTaskDefLimitTableStatement());
                session.execute(getCreateWorkflowDefsTableStatement());
                session.execute(getCreateWorkflowDefsIndexTableStatement());
                session.execute(getCreateTaskDefsTableStatement());
                session.execute(getCreateEventHandlersTableStatement());
                session.execute(getCreateEventExecutionsTableStatement());
                LOGGER.info(
                        "{} initialization complete! Tables created!", getClass().getSimpleName());
                initialized = true;
            }
        } catch (Exception e) {
            LOGGER.error("Error initializing and setting up keyspace and table in cassandra", e);
            throw e;
        }
    }

    private String getCreateKeyspaceStatement() {
        return SchemaBuilder.createKeyspace(properties.getKeyspace())
                .ifNotExists()
                .with()
                .replication(
                        ImmutableMap.of(
                                "class",
                                properties.getReplicationStrategy(),
                                properties.getReplicationFactorKey(),
                                properties.getReplicationFactorValue()))
                .durableWrites(true)
                .getQueryString();
    }

    private String getCreateWorkflowsTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_WORKFLOWS)
                .ifNotExists()
                .addPartitionKey(WORKFLOW_ID_KEY, DataType.uuid())
                .addPartitionKey(SHARD_ID_KEY, DataType.cint())
                .addClusteringColumn(ENTITY_KEY, DataType.text())
                .addClusteringColumn(TASK_ID_KEY, DataType.text())
                .addColumn(PAYLOAD_KEY, DataType.text())
                .addStaticColumn(TOTAL_TASKS_KEY, DataType.cint())
                .addStaticColumn(TOTAL_PARTITIONS_KEY, DataType.cint())
                .getQueryString();
    }

    private String getCreateTaskLookupTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_TASK_LOOKUP)
                .ifNotExists()
                .addPartitionKey(TASK_ID_KEY, DataType.uuid())
                .addColumn(WORKFLOW_ID_KEY, DataType.uuid())
                .getQueryString();
    }

    private String getCreateTaskDefLimitTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_TASK_DEF_LIMIT)
                .ifNotExists()
                .addPartitionKey(TASK_DEF_NAME_KEY, DataType.text())
                .addClusteringColumn(TASK_ID_KEY, DataType.uuid())
                .addColumn(WORKFLOW_ID_KEY, DataType.uuid())
                .getQueryString();
    }

    private String getCreateWorkflowDefsTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_WORKFLOW_DEFS)
                .ifNotExists()
                .addPartitionKey(WORKFLOW_DEF_NAME_KEY, DataType.text())
                .addClusteringColumn(WORKFLOW_VERSION_KEY, DataType.cint())
                .addColumn(WORKFLOW_DEFINITION_KEY, DataType.text())
                .getQueryString();
    }

    private String getCreateWorkflowDefsIndexTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_WORKFLOW_DEFS_INDEX)
                .ifNotExists()
                .addPartitionKey(WORKFLOW_DEF_INDEX_KEY, DataType.text())
                .addClusteringColumn(WORKFLOW_DEF_NAME_VERSION_KEY, DataType.text())
                .addColumn(WORKFLOW_DEF_INDEX_VALUE, DataType.text())
                .getQueryString();
    }

    private String getCreateTaskDefsTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_TASK_DEFS)
                .ifNotExists()
                .addPartitionKey(TASK_DEFS_KEY, DataType.text())
                .addClusteringColumn(TASK_DEF_NAME_KEY, DataType.text())
                .addColumn(TASK_DEFINITION_KEY, DataType.text())
                .getQueryString();
    }

    private String getCreateEventHandlersTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_EVENT_HANDLERS)
                .ifNotExists()
                .addPartitionKey(HANDLERS_KEY, DataType.text())
                .addClusteringColumn(EVENT_HANDLER_NAME_KEY, DataType.text())
                .addColumn(EVENT_HANDLER_KEY, DataType.text())
                .getQueryString();
    }

    private String getCreateEventExecutionsTableStatement() {
        return SchemaBuilder.createTable(properties.getKeyspace(), TABLE_EVENT_EXECUTIONS)
                .ifNotExists()
                .addPartitionKey(MESSAGE_ID_KEY, DataType.text())
                .addPartitionKey(EVENT_HANDLER_NAME_KEY, DataType.text())
                .addClusteringColumn(EVENT_EXECUTION_ID_KEY, DataType.text())
                .addColumn(PAYLOAD_KEY, DataType.text())
                .getQueryString();
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

    void recordCassandraDaoRequests(String action) {
        recordCassandraDaoRequests(action, "n/a", "n/a");
    }

    void recordCassandraDaoRequests(String action, String taskType, String workflowType) {
        Monitors.recordDaoRequests(DAO_NAME, action, taskType, workflowType);
    }

    void recordCassandraDaoEventRequests(String action, String event) {
        Monitors.recordDaoEventRequests(DAO_NAME, action, event);
    }

    void recordCassandraDaoPayloadSize(
            String action, int size, String taskType, String workflowType) {
        Monitors.recordDaoPayloadSize(DAO_NAME, action, taskType, workflowType, size);
    }

    static class WorkflowMetadata {

        private int totalTasks;
        private int totalPartitions;

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
