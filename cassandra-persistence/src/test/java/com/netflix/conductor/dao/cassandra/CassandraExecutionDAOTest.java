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
package com.netflix.conductor.dao.cassandra;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.util.EmbeddedCassandra;
import com.netflix.conductor.util.Statements;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.netflix.conductor.dao.cassandra.CassandraBaseDAO.WorkflowMetadata;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CassandraExecutionDAOTest {
    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final ObjectMapper objectMapper = new JsonMapperProvider().get();

    private EmbeddedCassandra embeddedCassandra;

    private CassandraExecutionDAO executionDAO;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        embeddedCassandra = new EmbeddedCassandra();
        Session session = embeddedCassandra.getSession();
        Statements statements = new Statements(testConfiguration);
        executionDAO = new CassandraExecutionDAO(session, objectMapper, testConfiguration, statements);
    }

    @After
    public void teardown() {
        embeddedCassandra.cleanupData();
    }

    @Test
    public void testValidateTasks() {
        List<Task> tasks = new ArrayList<>();

        // create tasks for a workflow and add to list
        Task task1 = new Task();
        task1.setWorkflowInstanceId("uuid");
        task1.setTaskId("task1id");
        task1.setReferenceTaskName("task1");
        tasks.add(task1);
        Task task2 = new Task();
        task2.setWorkflowInstanceId("uuid");
        task2.setTaskId("task2id");
        task2.setReferenceTaskName("task2");
        tasks.add(task2);
        executionDAO.validateTasks(tasks);

        // add a task from a different workflow to the list
        Task task3 = new Task();
        task3.setWorkflowInstanceId("other-uuid");
        task3.setTaskId("task3id");
        task3.setReferenceTaskName("task3");
        tasks.add(task3);
        expectedException.expect(ApplicationException.class);
        expectedException.expectMessage("Tasks of multiple workflows cannot be created/updated simultaneously");
        executionDAO.validateTasks(tasks);
    }

    @Test
    public void testWorkflowCRUD() {
        String workflowId = IDGenerator.generate();
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(workflowId);
        workflow.setInput(new HashMap<>());
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);

        // create a new workflow in the datastore
        String id = executionDAO.createWorkflow(workflow);
        assertEquals(workflowId, id);

        // read the workflow from the datastore
        Workflow found = executionDAO.getWorkflow(workflowId);
        assertEquals(workflow, found);

        // update the workflow
        workflow.setStatus(Workflow.WorkflowStatus.COMPLETED);
        executionDAO.updateWorkflow(workflow);
        found = executionDAO.getWorkflow(workflowId);
        assertEquals(workflow, found);

        // remove the workflow from datastore
        boolean removed = executionDAO.removeWorkflow(workflowId);
        assertTrue(removed);

        // read workflow again
        workflow = executionDAO.getWorkflow(workflowId, true);
        assertNull(workflow);
    }

    @Test
    public void testTasksCRUD() {
        String workflowId = IDGenerator.generate();
        // create a workflow
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(workflowId);
        workflow.setInput(new HashMap<>());
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);

        // add it to the datastore
        executionDAO.createWorkflow(workflow);

        // create tasks for this workflow
        Task task1 = new Task();
        task1.setWorkflowInstanceId(workflowId);
        task1.setTaskType("task1");
        task1.setReferenceTaskName("task1");
        task1.setStatus(Task.Status.SCHEDULED);
        String task1Id = IDGenerator.generate();
        task1.setTaskId(task1Id);
        Task task2 = new Task();
        task2.setWorkflowInstanceId(workflowId);
        task2.setTaskType("task2");
        task2.setReferenceTaskName("task2");
        task1.setStatus(Task.Status.SCHEDULED);
        String task2Id = IDGenerator.generate();
        task2.setTaskId(task2Id);
        Task task3 = new Task();
        task3.setWorkflowInstanceId(workflowId);
        task3.setTaskType("task3");
        task3.setReferenceTaskName("task3");
        task1.setStatus(Task.Status.SCHEDULED);
        String task3Id = IDGenerator.generate();
        task3.setTaskId(task3Id);
        List<Task> taskList = new ArrayList<>(Arrays.asList(task1, task2, task3));

        // add the tasks to the datastore
        List<Task> tasks = executionDAO.createTasks(taskList);
        assertNotNull(tasks);
        assertEquals(taskList, tasks);

        // read the tasks from the datastore
        Task task = executionDAO.getTask(task1Id);
        assertEquals(task1, task);
        task = executionDAO.getTask(task2Id);
        assertEquals(task2, task);
        task = executionDAO.getTask(task3Id);
        assertEquals(task3, task);

        // check the task lookup table
        String foundId = executionDAO.lookupWorkflowIdFromTaskId(task1Id);
        assertEquals(foundId, workflowId);
        foundId = executionDAO.lookupWorkflowIdFromTaskId(task2Id);
        assertEquals(foundId, workflowId);
        foundId = executionDAO.lookupWorkflowIdFromTaskId(task3Id);
        assertEquals(foundId, workflowId);

        WorkflowMetadata workflowMetadata = executionDAO.getWorkflowMetadata(workflowId);
        assertEquals(3, workflowMetadata.getTotalTasks());
        assertEquals(1, workflowMetadata.getTotalPartitions());

        List<Task> fetchedTasks = executionDAO.getTasks(Arrays.asList(task1Id, task2Id, task3Id));
        assertNotNull(fetchedTasks);
        assertEquals(3, fetchedTasks.size());

        fetchedTasks = executionDAO.getTasksForWorkflow(workflowId);
        assertNotNull(fetchedTasks);
        assertEquals(3, fetchedTasks.size());

        // read workflow with tasks
        Workflow found = executionDAO.getWorkflow(workflowId, true);
        assertNotNull(found);
        assertEquals(workflow.getWorkflowId(), found.getWorkflowId());
        assertEquals(3, found.getTasks().size());
        assertEquals(task1, found.getTaskByRefName("task1"));
        assertEquals(task2, found.getTaskByRefName("task2"));
        assertEquals(task3, found.getTaskByRefName("task3"));

        // update a task
        task1.setStatus(Task.Status.IN_PROGRESS);
        executionDAO.updateTask(task1);
        task = executionDAO.getTask(task1Id);
        assertEquals(task1, task);

        // update multiple tasks
        task2.setStatus(Task.Status.COMPLETED);
        task3.setStatus(Task.Status.FAILED);
        executionDAO.updateTasks(Arrays.asList(task2, task3));
        task = executionDAO.getTask(task2Id);
        assertEquals(task2, task);
        task = executionDAO.getTask(task3Id);
        assertEquals(task3, task);

        // get pending tasks for the workflow
        List<Task> pendingTasks = executionDAO.getPendingTasksByWorkflow(task1.getTaskType(), workflowId);
        assertNotNull(pendingTasks);
        assertEquals(1, pendingTasks.size());
        assertEquals(task1, pendingTasks.get(0));

        // remove a task
        boolean removed = executionDAO.removeTask(task3.getTaskId());
        assertTrue(removed);

        workflowMetadata = executionDAO.getWorkflowMetadata(workflowId);
        assertEquals(2, workflowMetadata.getTotalTasks());
        assertEquals(1, workflowMetadata.getTotalPartitions());

        // read workflow with tasks again
        found = executionDAO.getWorkflow(workflowId);
        assertNotNull(found);
        assertEquals(workflow.getWorkflowId(), found.getWorkflowId());
        assertEquals(2, found.getTasks().size());
        assertEquals(task1, found.getTaskByRefName("task1"));
        assertEquals(task2, found.getTaskByRefName("task2"));

        // check the task lookup table
        foundId = executionDAO.lookupWorkflowIdFromTaskId(task1Id);
        assertEquals(foundId, workflowId);
        foundId = executionDAO.lookupWorkflowIdFromTaskId(task2Id);
        assertEquals(foundId, workflowId);

        foundId = executionDAO.lookupWorkflowIdFromTaskId(task3Id);
        assertNull(foundId);

        // try to read removed task
        Task t = executionDAO.getTask(task3.getTaskId());
        assertNull(t);

        // remove the workflow
        removed = executionDAO.removeWorkflow(workflowId);
        assertTrue(removed);

        // check task_lookup table
        foundId = executionDAO.lookupWorkflowIdFromTaskId(task1Id);
        assertNull(foundId);
        foundId = executionDAO.lookupWorkflowIdFromTaskId(task2Id);
        assertNull(foundId);
    }
}
