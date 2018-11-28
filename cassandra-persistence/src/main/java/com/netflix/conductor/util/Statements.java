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

/**
 * DML statements
 * <p>
 * INSERT INTO conductor.workflows (workflow_id,shard_id,task_id,entity,payload,total_tasks,total_partitions) VALUES (?,?,?,'workflow',?,?,?);
 * INSERT INTO conductor.workflows (workflow_id,shard_id,task_id,entity,payload) VALUES (?,?,?,'task',?);
 * <p>
 * SELECT total_tasks,total_partitions FROM conductor.workflows WHERE workflow_id=? AND shard_id=1;
 * SELECT payload FROM conductor.workflows WHERE workflow_id=? AND shard_id=? AND entity='task' AND task_id=?;
 * SELECT payload FROM conductor.workflows WHERE workflow_id=? AND shard_id=1 AND entity='workflow';
 * SELECT * FROM conductor.workflows WHERE workflow_id=? AND shard_id=?;
 * SELECT workflow_id FROM conductor.task_lookup WHERE task_id=?;
 * <p>
 * UPDATE conductor.workflows SET payload=? WHERE workflow_id=? AND shard_id=1 AND entity='workflow' AND task_id='';
 * UPDATE conductor.workflows SET total_tasks=? WHERE workflow_id=? AND shard_id=?;
 * UPDATE conductor.workflows SET total_partitions=?,total_tasks=? WHERE workflow_id=? AND shard_id=1;
 * UPDATE conductor.task_lookup SET workflow_id=? WHERE task_id=?;
 * <p>
 * DELETE FROM conductor.workflows WHERE workflow_id=? AND shard_id=?;
 * DELETE FROM conductor.workflows WHERE workflow_id=? AND shard_id=? AND entity='task' AND task_id=?;
 * DELETE FROM conductor.task_lookup WHERE task_id=?;
 */
public class Statements {
    private final String keyspace;

    @Inject
    public Statements(CassandraConfiguration config) {
        this.keyspace = config.getCassandraKeyspace();
    }

    // Insert Statements

    /**
     * @return cql query statement to insert a new workflow into the "workflows" table
     */
    public String getInsertWorkflowStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_WORKFLOWS)
                .value(WORKFLOW_ID_KEY, bindMarker())
                .value(SHARD_ID_KEY, bindMarker())
                .value(TASK_ID_KEY, bindMarker())
                .value(ENTITY_KEY, ENTITY_TYPE_WORKFLOW)
                .value(PAYLOAD_KEY, bindMarker())
                .value(TOTAL_TASKS_KEY, bindMarker())
                .value(TOTAL_PARTITIONS_KEY, bindMarker())
                .getQueryString();
    }

    /**
     * @return cql query statement to insert a new task into the "workflows" table
     */
    public String getInsertTaskStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_WORKFLOWS)
                .value(WORKFLOW_ID_KEY, bindMarker())
                .value(SHARD_ID_KEY, bindMarker())
                .value(TASK_ID_KEY, bindMarker())
                .value(ENTITY_KEY, ENTITY_TYPE_TASK)
                .value(PAYLOAD_KEY, bindMarker())
                .getQueryString();
    }

    // Select Statements

    /**
     * @return cql query statement to retrieve the total_tasks and total_partitions for a workflow from the "workflows" table
     */
    public String getSelectTotalStatement() {
        return QueryBuilder.select(TOTAL_TASKS_KEY, TOTAL_PARTITIONS_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve a task from the "workflows" table
     */
    public String getSelectTaskStatement() {
        return QueryBuilder.select(PAYLOAD_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_TASK))
                .and(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve a workflow (without its tasks) from the "workflows" table
     */
    public String getSelectWorkflowStatement() {
        return QueryBuilder.select(PAYLOAD_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_WORKFLOW))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve a workflow with its tasks from the "workflows" table
     */
    public String getSelectWorkflowWithTasksStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve the workflow_id for a particular task_id from the "task_lookup" table
     */
    public String getSelectTaskFromLookupTableStatement() {
        return QueryBuilder.select(WORKFLOW_ID_KEY)
                .from(keyspace, TABLE_TASK_LOOKUP)
                .where(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    // Update Statements

    /**
     * @return cql query statement to update a workflow in the "workflows" table
     */
    public String getUpdateWorkflowStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(PAYLOAD_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_WORKFLOW))
                .and(eq(TASK_ID_KEY, ""))
                .getQueryString();
    }

    /**
     * @return cql query statement to update the total_tasks in a shard for a workflow in the "workflows" table
     */
    public String getUpdateTotalTasksStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(TOTAL_TASKS_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to update the total_partitions for a workflow in the "workflows" table
     */
    public String getUpdateTotalPartitionsStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(TOTAL_PARTITIONS_KEY, bindMarker()))
                .and(set(TOTAL_TASKS_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, 1))
                .getQueryString();
    }

    /**
     * @return cql query statement to add a new task_id to workflow_id mapping to the "task_lookup" table
     */
    public String getUpdateTaskLookupStatement() {
        return QueryBuilder.update(keyspace, TABLE_TASK_LOOKUP)
                .with(set(WORKFLOW_ID_KEY, bindMarker()))
                .where(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    // Delete statements

    /**
     * @return cql query statement to delete a workflow from the "workflows" table
     */
    public String getDeleteWorkflowStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to delete a task_id to workflow_id mapping from the "task_lookup" table
     */
    public String getDeleteTaskLookupStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_TASK_LOOKUP)
                .where(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to delete a task from the "workflows" table
     */
    public String getDeleteTaskStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_TASK))
                .and(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }
}
