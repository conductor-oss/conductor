/*
 * Copyright 2020 Netflix, Inc.
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

import static org.junit.Assert.assertEquals;

import com.netflix.conductor.config.TestConfiguration;
import org.junit.Before;
import org.junit.Test;

public class StatementsTest {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private Statements statements;

    @Before
    public void setUp() {
        statements = new Statements(testConfiguration);
    }

    @Test
    public void testGetInsertWorkflowDefStatement() {
        String statement = "INSERT INTO junit.workflow_definitions (workflow_def_name,version,workflow_definition) VALUES (?,?,?) IF NOT EXISTS;";
        assertEquals(statement, statements.getInsertWorkflowDefStatement());
    }

    @Test
    public void testGetInsertWorkflowDefVersionStatement() {
        String statement = "INSERT INTO junit.workflow_defs_index (workflow_def_version_index,workflow_def_name_version,workflow_def_index_value) VALUES ('workflow_def_version_index',?,?);";
        assertEquals(statement, statements.getInsertWorkflowDefVersionIndexStatement());
    }

    @Test
    public void testGetInsertTaskDefStatement() {
        String statement = "INSERT INTO junit.task_definitions (task_defs,task_def_name,task_definition) VALUES ('task_defs',?,?);";
        assertEquals(statement, statements.getInsertTaskDefStatement());
    }

    @Test
    public void testGetSelectWorkflowDefStatement() {
        String statement = "SELECT workflow_definition FROM junit.workflow_definitions WHERE workflow_def_name=? AND "
            + "version=?;";
        assertEquals(statement, statements.getSelectWorkflowDefStatement());
    }

    @Test
    public void testGetSelectAllWorkflowDefVersionsByNameStatement() {
        String statement = "SELECT * FROM junit.workflow_definitions WHERE workflow_def_name=?;";
        assertEquals(statement, statements.getSelectAllWorkflowDefVersionsByNameStatement());
    }

    @Test
    public void testGetSelectAllWorkflowDefsStatement() {
        String statement = "SELECT * FROM junit.workflow_defs_index WHERE workflow_def_version_index=?;";
        assertEquals(statement, statements.getSelectAllWorkflowDefsStatement());
    }

    @Test
    public void testGetSelectTaskDefStatement() {
        String statement = "SELECT task_definition FROM junit.task_definitions WHERE task_defs='task_defs' AND task_def_name=?;";
        assertEquals(statement, statements.getSelectTaskDefStatement());
    }

    @Test
    public void testGetSelectAllTaskDefsStatement() {
        String statement = "SELECT * FROM junit.task_definitions WHERE task_defs=?;";
        assertEquals(statement, statements.getSelectAllTaskDefsStatement());
    }

    @Test
    public void testGetUpdateWorkflowDefStatement() {
        String statement = "UPDATE junit.workflow_definitions SET workflow_definition=? WHERE workflow_def_name=? AND version=?;";
        assertEquals(statement, statements.getUpdateWorkflowDefStatement());
    }

    @Test
    public void testGetDeleteWorkflowDefStatement() {
        String statement = "DELETE FROM junit.workflow_definitions WHERE workflow_def_name=? AND version=?;";
        assertEquals(statement, statements.getDeleteWorkflowDefStatement());
    }

    @Test
    public void testGetDeleteWorkflowDefIndexStatement() {
        String statement = "DELETE FROM junit.workflow_defs_index WHERE workflow_def_version_index=? AND workflow_def_name_version=?;";
        assertEquals(statement, statements.getDeleteWorkflowDefIndexStatement());
    }

    @Test
    public void testGetDeleteTaskDefStatement() {
        String statement = "DELETE FROM junit.task_definitions WHERE task_defs='task_defs' AND task_def_name=?;";
        assertEquals(statement, statements.getDeleteTaskDefStatement());
    }

    @Test
    public void testGetInsertWorkflowStatement() {
        String statement = "INSERT INTO junit.workflows (workflow_id,shard_id,task_id,entity,payload,total_tasks,total_partitions) VALUES (?,?,?,'workflow',?,?,?);";
        assertEquals(statement, statements.getInsertWorkflowStatement());
    }

    @Test
    public void testGetInsertTaskStatement() {
        String statement = "INSERT INTO junit.workflows (workflow_id,shard_id,task_id,entity,payload) VALUES (?,?,?,'task',?);";
        assertEquals(statement, statements.getInsertTaskStatement());
    }

    @Test
    public void testGetInsertEventExecutionStatement() {
        String statement = "INSERT INTO junit.event_executions (message_id,event_handler_name,event_execution_id,payload) VALUES (?,?,?,?) IF NOT EXISTS;";
        assertEquals(statement, statements.getInsertEventExecutionStatement());
    }

    @Test
    public void testGetSelectTotalStatement() {
        String statement = "SELECT total_tasks,total_partitions FROM junit.workflows WHERE workflow_id=? AND shard_id=1;";
        assertEquals(statement, statements.getSelectTotalStatement());
    }

    @Test
    public void testGetSelectTaskStatement() {
        String statement = "SELECT payload FROM junit.workflows WHERE workflow_id=? AND shard_id=? AND entity='task' AND task_id=?;";
        assertEquals(statement, statements.getSelectTaskStatement());
    }

    @Test
    public void testGetSelectWorkflowStatement() {
        String statement = "SELECT payload FROM junit.workflows WHERE workflow_id=? AND shard_id=1 AND entity='workflow';";
        assertEquals(statement, statements.getSelectWorkflowStatement());
    }

    @Test
    public void testGetSelectWorkflowWithTasksStatement() {
        String statement = "SELECT * FROM junit.workflows WHERE workflow_id=? AND shard_id=?;";
        assertEquals(statement, statements.getSelectWorkflowWithTasksStatement());
    }

    @Test
    public void testGetSelectTaskFromLookupTableStatement() {
        String statement = "SELECT workflow_id FROM junit.task_lookup WHERE task_id=?;";
        assertEquals(statement, statements.getSelectTaskFromLookupTableStatement());
    }

    @Test
    public void testGetSelectTasksFromTaskDefLimitStatement() {
        String statement = "SELECT * FROM junit.task_def_limit WHERE task_def_name=?;";
        assertEquals(statement, statements.getSelectTasksFromTaskDefLimitStatement());
    }

    @Test
    public void testGetSelectAllEventExecutionsForMessageFromEventExecutionsStatement() {
        String statement = "SELECT * FROM junit.event_executions WHERE message_id=? AND event_handler_name=?;";
        assertEquals(statement, statements.getSelectAllEventExecutionsForMessageFromEventExecutionsStatement());
    }

    @Test
    public void testGetUpdateWorkflowStatement() {
        String statement = "UPDATE junit.workflows SET payload=? WHERE workflow_id=? AND shard_id=1 AND entity='workflow' AND task_id='';";
        assertEquals(statement, statements.getUpdateWorkflowStatement());
    }

    @Test
    public void testGetUpdateTotalTasksStatement() {
        String statement = "UPDATE junit.workflows SET total_tasks=? WHERE workflow_id=? AND shard_id=?;";
        assertEquals(statement, statements.getUpdateTotalTasksStatement());
    }

    @Test
    public void testGetUpdateTotalPartitionsStatement() {
        String statement = "UPDATE junit.workflows SET total_partitions=?,total_tasks=? WHERE workflow_id=? AND shard_id=1;";
        assertEquals(statement, statements.getUpdateTotalPartitionsStatement());
    }

    @Test
    public void testGetUpdateTaskLookupStatement() {
        String statement = "UPDATE junit.task_lookup SET workflow_id=? WHERE task_id=?;";
        assertEquals(statement, statements.getUpdateTaskLookupStatement());
    }

    @Test
    public void testGetUpdateTaskDefLimitStatement() {
        String statement = "UPDATE junit.task_def_limit SET workflow_id=? WHERE task_def_name=? AND task_id=?;";
        assertEquals(statement, statements.getUpdateTaskDefLimitStatement());
    }

    @Test
    public void testGetUpdateEventExecutionStatement() {
        String statement = "UPDATE junit.event_executions USING TTL ? SET payload=? WHERE message_id=? AND event_handler_name=? AND event_execution_id=?;";
        assertEquals(statement, statements.getUpdateEventExecutionStatement());
    }

    @Test
    public void testGetDeleteWorkflowStatement() {
        String statement = "DELETE FROM junit.workflows WHERE workflow_id=? AND shard_id=?;";
        assertEquals(statement, statements.getDeleteWorkflowStatement());
    }

    @Test
    public void testGetDeleteTaskLookupStatement() {
        String statement = "DELETE FROM junit.task_lookup WHERE task_id=?;";
        assertEquals(statement, statements.getDeleteTaskLookupStatement());
    }

    @Test
    public void testGetDeleteTaskStatement() {
        String statement = "DELETE FROM junit.workflows WHERE workflow_id=? AND shard_id=? AND entity='task' AND task_id=?;";
        assertEquals(statement, statements.getDeleteTaskStatement());
    }

    @Test
    public void testGetDeleteTaskDefLimitStatement() {
        String statement = "DELETE FROM junit.task_def_limit WHERE task_def_name=? AND task_id=?;";
        assertEquals(statement, statements.getDeleteTaskDefLimitStatement());
    }

    @Test
    public void testGetDeleteEventExecutionStatement() {
        String statement = "DELETE FROM junit.event_executions WHERE message_id=? AND event_handler_name=? AND event_execution_id=?;";
        assertEquals(statement, statements.getDeleteEventExecutionsStatement());
    }

    @Test
    public void testGetInsertEventHandlerStatement() {
        String statement = "INSERT INTO junit.event_handlers (handlers,event_handler_name,event_handler) VALUES ('handlers',?,?);";
        assertEquals(statement, statements.getInsertEventHandlerStatement());
    }

    @Test
    public void testGetSelectAllEventHandlersStatement() {
        String statement = "SELECT * FROM junit.event_handlers WHERE handlers=?;";
        assertEquals(statement, statements.getSelectAllEventHandlersStatement());
    }

    @Test
    public void testDeleteEventHandlerStatement() {
        String statement = "DELETE FROM junit.event_handlers WHERE handlers='handlers' AND event_handler_name=?;";
        assertEquals(statement, statements.getDeleteEventHandlerStatement());
    }
}