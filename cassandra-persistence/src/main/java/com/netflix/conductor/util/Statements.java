/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.util;

import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.netflix.conductor.cassandra.CassandraConfiguration;

import javax.inject.Inject;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.netflix.conductor.util.Constants.ENTITY_KEY;
import static com.netflix.conductor.util.Constants.ENTITY_TYPE_TASK;
import static com.netflix.conductor.util.Constants.ENTITY_TYPE_WORKFLOW;
import static com.netflix.conductor.util.Constants.PAYLOAD_KEY;
import static com.netflix.conductor.util.Constants.SHARD_ID_KEY;
import static com.netflix.conductor.util.Constants.TABLE_TASK_LOOKUP;
import static com.netflix.conductor.util.Constants.TABLE_WORKFLOWS;
import static com.netflix.conductor.util.Constants.TASK_ID_KEY;
import static com.netflix.conductor.util.Constants.TOTAL_PARTITIONS_KEY;
import static com.netflix.conductor.util.Constants.TOTAL_TASKS_KEY;
import static com.netflix.conductor.util.Constants.WORKFLOW_ID_KEY;

public class Statements {
    private final String keyspace;

    @Inject
    public Statements(CassandraConfiguration config) {
        this.keyspace = config.getKeyspace();
    }

    // Insert Statements
    public Statement getInsertWorkflowStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_WORKFLOWS)
                .value(WORKFLOW_ID_KEY, bindMarker())
                .value(SHARD_ID_KEY, bindMarker())
                .value(TASK_ID_KEY, bindMarker())
                .value(ENTITY_KEY, ENTITY_TYPE_WORKFLOW)
                .value(PAYLOAD_KEY, bindMarker())
                .value(TOTAL_TASKS_KEY, bindMarker())
                .value(TOTAL_PARTITIONS_KEY, bindMarker());
    }

    public Statement getInsertTaskStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_WORKFLOWS)
                .value(WORKFLOW_ID_KEY, bindMarker())
                .value(SHARD_ID_KEY, bindMarker())
                .value(TASK_ID_KEY, bindMarker())
                .value(ENTITY_KEY, ENTITY_TYPE_TASK)
                .value(PAYLOAD_KEY, bindMarker());
    }

    // Select Statements
    public Statement getSelectTotalStatement() {
        return QueryBuilder.select(TOTAL_TASKS_KEY, TOTAL_PARTITIONS_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1));
    }

    public Statement getSelectTaskStatement() {
        return QueryBuilder.select(PAYLOAD_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_TASK))
                .and(eq(TASK_ID_KEY, bindMarker()));
    }

    public Statement getSelectWorkflowStatement() {
        return QueryBuilder.select(PAYLOAD_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_WORKFLOW));
    }

    public Statement getSelectWorkflowWithTasksStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()));
    }

    public Statement getSelectTaskFromLookupTableStatement() {
        return QueryBuilder.select(WORKFLOW_ID_KEY)
                .from(keyspace, TABLE_TASK_LOOKUP)
                .where(eq(TASK_ID_KEY, bindMarker()));
    }

    // Update Statements
    public Statement getUpdateWorkflowStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(PAYLOAD_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_WORKFLOW))
                .and(eq(TASK_ID_KEY, ""));
    }

    public Statement getUpdateTotalTasksStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(TOTAL_TASKS_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()));
    }

    public Statement getUpdateTotalPartitionsStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(TOTAL_PARTITIONS_KEY, bindMarker()))
                .and(set(TOTAL_TASKS_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1));
    }

    public Statement getUpdateTaskLookupStatement() {
        return QueryBuilder.update(keyspace, TABLE_TASK_LOOKUP)
                .with(set(WORKFLOW_ID_KEY, bindMarker()))
                .where(eq(TASK_ID_KEY, bindMarker()));
    }

    // Delete statements
    public Statement getDeleteWorkflowStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()));
    }

    public Statement getDeleteTaskLookupStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_TASK_LOOKUP)
                .where(eq(TASK_ID_KEY, bindMarker()));
    }

    public Statement getDeleteTaskStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_TASK))
                .and(eq(TASK_ID_KEY, bindMarker()));
    }
}
