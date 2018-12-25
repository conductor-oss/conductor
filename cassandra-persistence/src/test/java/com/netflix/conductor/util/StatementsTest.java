package com.netflix.conductor.util;

import com.netflix.conductor.config.TestConfiguration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StatementsTest {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private Statements statements;

    @Before
    public void setUp() {
        statements = new Statements(testConfiguration);
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
}