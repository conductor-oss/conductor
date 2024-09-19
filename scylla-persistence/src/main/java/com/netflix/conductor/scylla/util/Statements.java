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
package com.netflix.conductor.scylla.util;

import com.datastax.driver.core.querybuilder.QueryBuilder;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;
import static com.netflix.conductor.scylla.util.Constants.*;

//import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

/**
 * DML statements
 *
 * <p><em>MetadataDAO</em>
 *
 * <ul>
 *   <li>INSERT INTO conductor.workflow_definitions (workflow_def_name,version,workflow_definition)
 *       VALUES (?,?,?) IF NOT EXISTS;
 *   <li>INSERT INTO conductor.workflow_defs_index
 *       (workflow_def_version_index,workflow_def_name_version, workflow_def_index_value) VALUES
 *       ('workflow_def_version_index',?,?);
 *   <li>INSERT INTO conductor.task_definitions (task_defs,task_def_name,task_definition) VALUES
 *       ('task_defs',?,?);
 *   <li>SELECT workflow_definition FROM conductor.workflow_definitions WHERE workflow_def_name=?
 *       AND version=?;
 *   <li>SELECT * FROM conductor.workflow_definitions WHERE workflow_def_name=?;
 *   <li>SELECT * FROM conductor.workflow_defs_index WHERE workflow_def_version_index=?;
 *   <li>SELECT task_definition FROM conductor.task_definitions WHERE task_defs='task_defs' AND
 *       task_def_name=?;
 *   <li>SELECT * FROM conductor.task_definitions WHERE task_defs=?;
 *   <li>UPDATE conductor.workflow_definitions SET workflow_definition=? WHERE workflow_def_name=?
 *       AND version=?;
 *   <li>DELETE FROM conductor.workflow_definitions WHERE workflow_def_name=? AND version=?;
 *   <li>DELETE FROM conductor.workflow_defs_index WHERE workflow_def_version_index=? AND
 *       workflow_def_name_version=?;
 *   <li>DELETE FROM conductor.task_definitions WHERE task_defs='task_defs' AND task_def_name=?;
 * </ul>
 *
 * <em>ExecutionDAO</em>
 *
 * <ul>
 *   <li>INSERT INTO conductor.workflows
 *       (workflow_id,shard_id,task_id,entity,payload,total_tasks,total_partitions) VALUES
 *       (?,?,?,'workflow',?,?,?);
 *   <li>INSERT INTO conductor.workflows (workflow_id,shard_id,task_id,entity,payload) VALUES
 *       (?,?,?,'task',?);
 *   <li>INSERT INTO conductor.event_executions
 *       (message_id,event_handler_name,event_execution_id,payload) VALUES (?,?,?,?) IF NOT EXISTS;
 *   <li>SELECT total_tasks,total_partitions FROM conductor.workflows WHERE workflow_id=? AND
 *       shard_id=1;
 *   <li>SELECT payload FROM conductor.workflows WHERE workflow_id=? AND shard_id=? AND
 *       entity='task' AND task_id=?;
 *   <li>SELECT payload FROM conductor.workflows WHERE workflow_id=? AND shard_id=1 AND
 *       entity='workflow';
 *   <li>SELECT * FROM conductor.workflows WHERE workflow_id=? AND shard_id=?;
 *   <li>SELECT workflow_id FROM conductor.task_lookup WHERE task_id=?;
 *   <li>SELECT * FROM conductor.task_def_limit WHERE task_def_name=?;
 *   <li>SELECT * FROM conductor.event_executions WHERE message_id=? AND event_handler_name=?;
 *   <li>UPDATE conductor.workflows SET payload=? WHERE workflow_id=? AND shard_id=1 AND
 *       entity='workflow' AND task_id='';
 *   <li>UPDATE conductor.workflows SET total_tasks=? WHERE workflow_id=? AND shard_id=?;
 *   <li>UPDATE conductor.workflows SET total_partitions=?,total_tasks=? WHERE workflow_id=? AND
 *       shard_id=1;
 *   <li>UPDATE conductor.task_lookup SET workflow_id=? WHERE task_id=?;
 *   <li>UPDATE conductor.task_def_limit SET workflow_id=? WHERE task_def_name=? AND task_id=?;
 *   <li>UPDATE conductor.event_executions USING TTL ? SET payload=? WHERE message_id=? AND
 *       event_handler_name=? AND event_execution_id=?;
 *   <li>DELETE FROM conductor.workflows WHERE workflow_id=? AND shard_id=?;
 *   <li>DELETE FROM conductor.workflows WHERE workflow_id=? AND shard_id=? AND entity='task' AND
 *       task_id=?;
 *   <li>DELETE FROM conductor.task_lookup WHERE task_id=?;
 *   <li>DELETE FROM conductor.task_def_limit WHERE task_def_name=? AND task_id=?;
 *   <li>DELETE FROM conductor.event_executions WHERE message_id=? AND event_handler_name=? AND
 *       event_execution_id=?;
 * </ul>
 *
 * <em>EventHandlerDAO</em>
 *
 * <ul>
 *   <li>INSERT INTO conductor.event_handlers (handlers,event_handler_name,event_handler) VALUES
 *       ('handlers',?,?);
 *   <li>SELECT * FROM conductor.event_handlers WHERE handlers=?;
 *   <li>DELETE FROM conductor.event_handlers WHERE handlers='handlers' AND event_handler_name=?;
 * </ul>
 */
public class Statements {

    private final String keyspace;

    public Statements(String keyspace) {
        this.keyspace = keyspace;
    }

    // MetadataDAO
    // Insert Statements

    /**
     * @return cql query statement to insert a new workflow definition into the
     *     "workflow_definitions" table
     */
    public String getInsertWorkflowDefStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_WORKFLOW_DEFS)
                .value(WORKFLOW_DEF_NAME_KEY, bindMarker())
                .value(WORKFLOW_VERSION_KEY, bindMarker())
                .value(WORKFLOW_DEFINITION_KEY, bindMarker())
                .ifNotExists()
                .getQueryString();
    }

    /**
     * @return cql query statement to insert a workflow def name version index into the
     *     "workflow_defs_index" table
     */
    public String getInsertWorkflowDefVersionIndexStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_WORKFLOW_DEFS_INDEX)
                .value(WORKFLOW_DEF_INDEX_KEY, WORKFLOW_DEF_INDEX_KEY)
                .value(WORKFLOW_DEF_NAME_VERSION_KEY, bindMarker())
                .value(WORKFLOW_DEF_INDEX_VALUE, bindMarker())
                .getQueryString();
    }

    /**
     * @return cql query statement to insert a new task definition into the "task_definitions" table
     */
    public String getInsertTaskDefStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_TASK_DEFS)
                .value(TASK_DEFS_KEY, TASK_DEFS_KEY)
                .value(TASK_DEF_NAME_KEY, bindMarker())
                .value(TASK_DEFINITION_KEY, bindMarker())
                .getQueryString();
    }

    // Select Statements

    /**
     * @return cql query statement to fetch a workflow definition by name and version from the
     *     "workflow_definitions" table
     */
    public String getSelectWorkflowDefStatement() {
        return QueryBuilder.select(WORKFLOW_DEFINITION_KEY)
                .from(keyspace, TABLE_WORKFLOW_DEFS)
                .where(eq(WORKFLOW_DEF_NAME_KEY, bindMarker()))
                .and(eq(WORKFLOW_VERSION_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve all versions of a workflow definition by name from
     *     the "workflow_definitions" table
     */
    public String getSelectAllWorkflowDefVersionsByNameStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_WORKFLOW_DEFS)
                .where(eq(WORKFLOW_DEF_NAME_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to fetch all workflow def names and version from the
     *     "workflow_defs_index" table
     */
    public String getSelectAllWorkflowDefsStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_WORKFLOW_DEFS_INDEX)
                .where(eq(WORKFLOW_DEF_INDEX_KEY, bindMarker()))
                .getQueryString();
    }

    public String getSelectAllWorkflowDefsLatestVersionsStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_WORKFLOW_DEFS_INDEX)
                .where(eq(WORKFLOW_DEF_INDEX_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to fetch a task definition by name from the "task_definitions"
     *     table
     */
    public String getSelectTaskDefStatement() {
        return QueryBuilder.select(TASK_DEFINITION_KEY)
                .from(keyspace, TABLE_TASK_DEFS)
                .where(eq(TASK_DEFS_KEY, TASK_DEFS_KEY))
                .and(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve all task definitions from the "task_definitions"
     *     table
     */
    public String getSelectAllTaskDefsStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_TASK_DEFS)
                .where(eq(TASK_DEFS_KEY, bindMarker()))
                .getQueryString();
    }

    // Update Statement

    /**
     * @return cql query statement to update a workflow definitinos in the "workflow_definitions"
     *     table
     */
    public String getUpdateWorkflowDefStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOW_DEFS)
                .with(set(WORKFLOW_DEFINITION_KEY, bindMarker()))
                .where(eq(WORKFLOW_DEF_NAME_KEY, bindMarker()))
                .and(eq(WORKFLOW_VERSION_KEY, bindMarker()))
                .getQueryString();
    }

    // Delete Statements

    /**
     * @return cql query statement to delete a workflow definition by name and version from the
     *     "workflow_definitions" table
     */
    public String getDeleteWorkflowDefStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_WORKFLOW_DEFS)
                .where(eq(WORKFLOW_DEF_NAME_KEY, bindMarker()))
                .and(eq(WORKFLOW_VERSION_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to delete a workflow def name/version from the
     *     "workflow_defs_index" table
     */
    public String getDeleteWorkflowDefIndexStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_WORKFLOW_DEFS_INDEX)
                .where(eq(WORKFLOW_DEF_INDEX_KEY, bindMarker()))
                .and(eq(WORKFLOW_DEF_NAME_VERSION_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to delete a task definition by name from the "task_definitions"
     *     table
     */
    public String getDeleteTaskDefStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_TASK_DEFS)
                .where(eq(TASK_DEFS_KEY, TASK_DEFS_KEY))
                .and(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .getQueryString();
    }

    // ExecutionDAO
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
                .value(VERSION, bindMarker())
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
    /**
     * @return cql query statement to insert a new event execution into the "event_executions" table
     */
    public String getInsertEventExecutionStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_EVENT_EXECUTIONS)
                .value(MESSAGE_ID_KEY, bindMarker())
                .value(EVENT_HANDLER_NAME_KEY, bindMarker())
                .value(EVENT_EXECUTION_ID_KEY, bindMarker())
                .value(PAYLOAD_KEY, bindMarker())
                .ifNotExists()
                .getQueryString();
    }

    // Select Statements

    /**
     * @return cql query statement to retrieve all workflows executions from the "workflows"
     *     table by coorelationId
     */
    public String getSelectWorkflowsByCorrelationIdStatement() {
        return QueryBuilder.select(PAYLOAD_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(SHARD_ID_KEY, bindMarker()))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_WORKFLOW))
                .allowFiltering()
                .getQueryString();
    }

    /**
     * @return cql query statement to insert tasks to task_in_progress table
     */
    public String getInsertTaskInProgressStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_TASK_IN_PROGRESS)
                .value(TASK_DEF_NAME_KEY, bindMarker())
                .value(TASK_ID_KEY, bindMarker())
                .value(WORKFLOW_ID_KEY, bindMarker())
                .value(TASK_IN_PROG_STATUS_KEY, bindMarker())
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve all the tasks count from task_in_progress table
     * per taskDefName and task_id
     */
    public String getSelectTaskInProgressStatement() {
        return QueryBuilder.select()
                .countAll()
                .from(keyspace, TABLE_TASK_IN_PROGRESS)
                .where(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .and(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve all the tasks count from task_in_progress table per taskDefName
     */
    public String getSelectCountTaskInProgressPerTskDefStatement() {
        return QueryBuilder.select()
                .countAll()
                .from(keyspace, TABLE_TASK_IN_PROGRESS)
                .where(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to update the task in task_in_progress table per taskDefName
     * and task_id
     */
    public String getUpdateTaskInProgressStatement() {
        return QueryBuilder.update(keyspace, TABLE_TASK_IN_PROGRESS)
                .with(set(TASK_IN_PROG_STATUS_KEY, bindMarker()))
                .where(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .and(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to delete the task in task_in_progress table per taskDefName
     * and task_id
     */
    public String getDeleteTaskInProgressStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_TASK_IN_PROGRESS)
                .where(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .and(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve the total_tasks and total_partitions for a workflow
     *     from the "workflows" table
     */
    public String getSelectTotalStatement() {
        return QueryBuilder.select(TOTAL_TASKS_KEY, TOTAL_PARTITIONS_KEY)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
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
     * @return cql query statement to retrieve a workflow (without its tasks) from the "workflows"
     *     table
     */
    public String getSelectWorkflowStatement() {
        return QueryBuilder.select(PAYLOAD_KEY,VERSION)
                .from(keyspace, TABLE_WORKFLOWS)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
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
     * @return cql query statement to retrieve the workflow_id for a particular task_id from the
     *     "task_lookup" table
     */
    public String getSelectTaskFromLookupTableStatement() {
        return QueryBuilder.select(WORKFLOW_ID_KEY)
                .from(keyspace, TABLE_TASK_LOOKUP)
                .where(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve the shard_id for a particular task_id from the
     *     "task_lookup" table
     */
    public String getSelectShardFromTaskLookupTableStatement() {
        return QueryBuilder.select(SHARD_ID_KEY)
                .from(keyspace, TABLE_TASK_LOOKUP)
                .where(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve the shard_id for a particular workflow_id  from the
     *     "workflow_lookup" table
     */
    public String getSelectShardFromWorkflowLookupTableStatement() {
        return QueryBuilder.select(SHARD_ID_KEY)
                .from(keyspace, TABLE_WORKFLOW_LOOKUP)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve all task ids for a given taskDefName with concurrent
     *     execution limit configured from the "task_def_limit" table
     */
    public String getSelectTasksFromTaskDefLimitStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_TASK_DEF_LIMIT)
                .where(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to retrieve all event executions for a given message and event
     *     handler from the "event_executions" table
     */
    public String getSelectAllEventExecutionsForMessageFromEventExecutionsStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_EVENT_EXECUTIONS)
                .where(eq(MESSAGE_ID_KEY, bindMarker()))
                .and(eq(EVENT_HANDLER_NAME_KEY, bindMarker()))
                .getQueryString();
    }

    // Update Statements

    /**
     * @return cql query statement to update a workflow in the "workflows" table
     */
    public String getUpdateWorkflowStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(PAYLOAD_KEY, bindMarker()))
                .and(set(VERSION, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .and(eq(ENTITY_KEY, ENTITY_TYPE_WORKFLOW))
                .and(eq(TASK_ID_KEY, ""))
                .onlyIf(eq(VERSION, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to update the total_tasks in a shard for a workflow in the
     *     "workflows" table
     */
    public String getUpdateTotalTasksStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(TOTAL_TASKS_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to update the total_partitions for a workflow in the "workflows"
     *     table
     */
    public String getUpdateTotalPartitionsStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOWS)
                .with(set(TOTAL_PARTITIONS_KEY, bindMarker()))
                .and(set(TOTAL_TASKS_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .and(eq(SHARD_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to add a new task_id to workflow_id mapping to the "task_lookup"
     *     table
     */
    public String getUpdateTaskLookupStatement() {
        return QueryBuilder.update(keyspace, TABLE_TASK_LOOKUP)
                .with(set(WORKFLOW_ID_KEY, bindMarker()))
                .and(set(SHARD_ID_KEY, bindMarker()))
                .where(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to update shard_id to workflow_id mapping to the "workflow_lookup"
     *     table
     */
    public String getUpdateWorkflowLookupStatement() {
        return QueryBuilder.update(keyspace, TABLE_WORKFLOW_LOOKUP)
                .with(set(SHARD_ID_KEY, bindMarker()))
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to add a new task_id to the "task_def_limit" table
     */
    public String getUpdateTaskDefLimitStatement() {
        return QueryBuilder.update(keyspace, TABLE_TASK_DEF_LIMIT)
                .with(set(WORKFLOW_ID_KEY, bindMarker()))
                .where(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .and(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to update an event execution in the "event_executions" table
     */
    public String getUpdateEventExecutionStatement() {
        return QueryBuilder.update(keyspace, TABLE_EVENT_EXECUTIONS)
                .using(QueryBuilder.ttl(bindMarker()))
                .with(set(PAYLOAD_KEY, bindMarker()))
                .where(eq(MESSAGE_ID_KEY, bindMarker()))
                .and(eq(EVENT_HANDLER_NAME_KEY, bindMarker()))
                .and(eq(EVENT_EXECUTION_ID_KEY, bindMarker()))
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
     * @return cql query statement to delete a task_id to workflow_id mapping from the "task_lookup"
     *     table
     */
    public String getDeleteTaskLookupStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_TASK_LOOKUP)
                .where(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to delete a workflow_lookup entry from the "workflow_lookup"
     *     table
     */
    public String getDeleteWorkflowLookupStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_WORKFLOW_LOOKUP)
                .where(eq(WORKFLOW_ID_KEY, bindMarker()))
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

    /**
     * @return cql query statement to delete a task_id from the "task_def_limit" table
     */
    public String getDeleteTaskDefLimitStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_TASK_DEF_LIMIT)
                .where(eq(TASK_DEF_NAME_KEY, bindMarker()))
                .and(eq(TASK_ID_KEY, bindMarker()))
                .getQueryString();
    }

    /**
     * @return cql query statement to delete an event execution from the "event_execution" table
     */
    public String getDeleteEventExecutionsStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_EVENT_EXECUTIONS)
                .where(eq(MESSAGE_ID_KEY, bindMarker()))
                .and(eq(EVENT_HANDLER_NAME_KEY, bindMarker()))
                .and(eq(EVENT_EXECUTION_ID_KEY, bindMarker()))
                .getQueryString();
    }

    // EventHandlerDAO
    // Insert Statements

    /**
     * @return cql query statement to insert an event handler into the "event_handlers" table
     */
    public String getInsertEventHandlerStatement() {
        return QueryBuilder.insertInto(keyspace, TABLE_EVENT_HANDLERS)
                .value(HANDLERS_KEY, HANDLERS_KEY)
                .value(EVENT_HANDLER_NAME_KEY, bindMarker())
                .value(EVENT_HANDLER_KEY, bindMarker())
                .getQueryString();
    }

    // Select Statements

    /**
     * @return cql query statement to retrieve all event handlers from the "event_handlers" table
     */
    public String getSelectAllEventHandlersStatement() {
        return QueryBuilder.select()
                .all()
                .from(keyspace, TABLE_EVENT_HANDLERS)
                .where(eq(HANDLERS_KEY, bindMarker()))
                .getQueryString();
    }

    // Delete Statements

    /**
     * @return cql query statement to delete an event handler by name from the "event_handlers"
     *     table
     */
    public String getDeleteEventHandlerStatement() {
        return QueryBuilder.delete()
                .from(keyspace, TABLE_EVENT_HANDLERS)
                .where(eq(HANDLERS_KEY, HANDLERS_KEY))
                .and(eq(EVENT_HANDLER_NAME_KEY, bindMarker()))
                .getQueryString();
    }
}
