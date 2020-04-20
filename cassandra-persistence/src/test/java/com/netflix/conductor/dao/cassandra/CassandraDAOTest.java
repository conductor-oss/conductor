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
package com.netflix.conductor.dao.cassandra;

import static com.netflix.conductor.common.metadata.events.EventExecution.Status.COMPLETED;
import static com.netflix.conductor.core.execution.ApplicationException.Code.INVALID_INPUT;
import static com.netflix.conductor.util.Constants.TABLE_EVENT_EXECUTIONS;
import static com.netflix.conductor.util.Constants.TABLE_WORKFLOW_DEFS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.cassandra.CassandraBaseDAO.WorkflowMetadata;
import com.netflix.conductor.util.EmbeddedCassandra;
import com.netflix.conductor.util.Statements;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CassandraDAOTest {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final ObjectMapper objectMapper = new JsonMapperProvider().get();

    private static EmbeddedCassandra embeddedCassandra;
    private static Session session;

    private CassandraMetadataDAO metadataDAO;
    private CassandraExecutionDAO executionDAO;
    private CassandraEventHandlerDAO eventHandlerDAO;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void init() throws Exception {
        embeddedCassandra = new EmbeddedCassandra();
        session = embeddedCassandra.getSession();
    }

    @Before
    public void setUp() {
        Statements statements = new Statements(testConfiguration);
        metadataDAO = new CassandraMetadataDAO(session, objectMapper, testConfiguration, statements);
        executionDAO = new CassandraExecutionDAO(session, objectMapper, testConfiguration, statements);
        eventHandlerDAO = new CassandraEventHandlerDAO(session, objectMapper, testConfiguration, statements);
    }

    @AfterClass
    public static void teardown() {
        embeddedCassandra.cleanupData();
        session.close();
    }

    @Test
    public void testWorkflowDefCRUD() throws Exception {
        String name = "workflow_def_1";
        int version = 1;

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(name);
        workflowDef.setVersion(version);
        workflowDef.setOwnerEmail("test@junit.com");

        // create workflow definition explicitly in test
        // since, embedded Cassandra server does not support LWT required for this API.
        addWorkflowDefinition(workflowDef);

        // fetch the workflow definition
        Optional<WorkflowDef> defOptional = metadataDAO.getWorkflowDef(name, version);
        assertTrue(defOptional.isPresent());
        assertEquals(workflowDef, defOptional.get());

        // register a higher version
        int higherVersion = 2;
        workflowDef.setVersion(higherVersion);
        workflowDef.setDescription("higher version");

        // register the higher version definition
        addWorkflowDefinition(workflowDef);

        // fetch the higher version
        defOptional = metadataDAO.getWorkflowDef(name, higherVersion);
        assertTrue(defOptional.isPresent());
        assertEquals(workflowDef, defOptional.get());

        // fetch latest version
        defOptional = metadataDAO.getLatestWorkflowDef(name);
        assertTrue(defOptional.isPresent());
        assertEquals(workflowDef, defOptional.get());

        // modify the definition
        workflowDef.setOwnerEmail("junit@test.com");
        metadataDAO.updateWorkflowDef(workflowDef);

        // fetch the workflow definition
        defOptional = metadataDAO.getWorkflowDef(name, higherVersion);
        assertTrue(defOptional.isPresent());
        assertEquals(workflowDef, defOptional.get());

        // delete workflow def
        metadataDAO.removeWorkflowDef(name, higherVersion);
        defOptional = metadataDAO.getWorkflowDef(name, higherVersion);
        assertFalse(defOptional.isPresent());
    }

    @Test
    public void testTaskDefCrud() {
        String task1Name = "task1";
        String task2Name = "task2";

        // fetch all task defs
        List<TaskDef> taskDefList = metadataDAO.getAllTaskDefs();
        assertNotNull(taskDefList);
        assertEquals(0, taskDefList.size());

        TaskDef taskDef = new TaskDef();
        taskDef.setName(task1Name);

        // register a task definition
        metadataDAO.createTaskDef(taskDef);

        // fetch all task defs
        taskDefList = metadataDAO.getAllTaskDefs();
        assertNotNull(taskDefList);
        assertEquals(1, taskDefList.size());

        // fetch the task def
        TaskDef def = metadataDAO.getTaskDef(task1Name);
        assertEquals(taskDef, def);

        // register another task definition
        TaskDef taskDef1 = new TaskDef();
        taskDef1.setName(task2Name);
        metadataDAO.createTaskDef(taskDef1);

        // fetch all task defs
        taskDefList = metadataDAO.getAllTaskDefs();
        assertNotNull(taskDefList);
        assertEquals(2, taskDefList.size());

        // update task def
        taskDef.setOwnerEmail("juni@test.com");
        metadataDAO.updateTaskDef(taskDef);
        def = metadataDAO.getTaskDef(task1Name);
        assertEquals(taskDef, def);

        // delete task def
        metadataDAO.removeTaskDef(task2Name);
        taskDefList = metadataDAO.getAllTaskDefs();
        assertNotNull(taskDefList);
        assertEquals(1, taskDefList.size());

        // fetch deleted task def
        def = metadataDAO.getTaskDef(task2Name);
        assertNull(def);
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
        workflow.setCreateTime(System.currentTimeMillis());

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
        workflow.setCreateTime(System.currentTimeMillis());

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

        // update tasks
        task1.setStatus(Task.Status.IN_PROGRESS);
        executionDAO.updateTask(task1);
        task = executionDAO.getTask(task1Id);
        assertEquals(task1, task);

        task2.setStatus(Task.Status.COMPLETED);
        executionDAO.updateTask(task2);
        task = executionDAO.getTask(task2Id);
        assertEquals(task2, task);

        task3.setStatus(Task.Status.FAILED);
        executionDAO.updateTask(task3);
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

    @Test
    public void testTaskDefLimitCRUD() {
        String taskDefName = "test_task_def";
        String taskId = IDGenerator.generate();

        TaskDef taskDef = new TaskDef();
        taskDef.setConcurrentExecLimit(1);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);

        Task task = new Task();
        task.setTaskDefName(taskDefName);
        task.setTaskId(taskId);
        task.setWorkflowInstanceId(IDGenerator.generate());
        task.setWorkflowTask(workflowTask);
        task.setTaskType("test_task");
        task.setWorkflowType("test_workflow");
        task.setStatus(Task.Status.SCHEDULED);

        Task newTask = new Task();
        newTask.setTaskDefName(taskDefName);
        newTask.setTaskId(IDGenerator.generate());
        newTask.setWorkflowInstanceId(IDGenerator.generate());
        newTask.setWorkflowTask(workflowTask);
        newTask.setTaskType("test_task");
        newTask.setWorkflowType("test_workflow");
        newTask.setStatus(Task.Status.SCHEDULED);

        // no tasks are IN_PROGRESS
        executionDAO.updateTaskDefLimit(task, false);
        assertFalse(executionDAO.exceedsInProgressLimit(task));

        // set a task to IN_PROGRESS
        task.setStatus(Status.IN_PROGRESS);
        executionDAO.updateTaskDefLimit(task, false);

        // when same task is checked
        assertFalse(executionDAO.exceedsInProgressLimit(task));

        // check if new task can be added
        assertTrue(executionDAO.exceedsInProgressLimit(newTask));

        // set IN_PROGRESS task to COMPLETED
        task.setStatus(Status.COMPLETED);
        executionDAO.updateTaskDefLimit(task, false);

        // check new task again
        assertFalse(executionDAO.exceedsInProgressLimit(newTask));

        // set new task to IN_PROGRESS
        newTask.setStatus(Status.IN_PROGRESS);
        executionDAO.updateTaskDefLimit(newTask, false);

        // check new task again
        assertFalse(executionDAO.exceedsInProgressLimit(newTask));

        // force remove from task def limit
        executionDAO.updateTaskDefLimit(newTask, true);
        assertFalse(executionDAO.exceedsInProgressLimit(task));
    }

    @Test
    public void testInvalid() {
        Task task = null;
        String id = "invalid_id";
        try {
            task = executionDAO.getTask(id);
        } catch (ApplicationException e) {
            assertEquals(INVALID_INPUT, e.getCode());
        }
        assertNull(task);

        Workflow workflow = null;
        try {
            workflow = executionDAO.getWorkflow(id, true);
        } catch (ApplicationException e) {
            assertEquals(INVALID_INPUT, e.getCode());
        }
        assertNull(workflow);

        id = IDGenerator.generate();
        task = executionDAO.getTask(id);
        assertNull(task);

        workflow = executionDAO.getWorkflow(id, true);
        assertNull(workflow);
    }

    @Test
    public void testEventHandlerCRUD() {
        String event = "event";
        String eventHandlerName1 = "event_handler1";
        String eventHandlerName2 = "event_handler2";

        EventHandler eventHandler = new EventHandler();
        eventHandler.setName(eventHandlerName1);
        eventHandler.setEvent(event);

        // create event handler
        eventHandlerDAO.addEventHandler(eventHandler);

        // fetch all event handlers for event
        List<EventHandler> handlers = eventHandlerDAO.getEventHandlersForEvent(event, false);
        assertNotNull(handlers);
        assertEquals(1, handlers.size());
        assertEquals(eventHandler.getName(), handlers.get(0).getName());
        assertEquals(eventHandler.getEvent(), handlers.get(0).getEvent());
        assertFalse(handlers.get(0).isActive());

        // add an active event handler for the same event
        EventHandler eventHandler1 = new EventHandler();
        eventHandler1.setName(eventHandlerName2);
        eventHandler1.setEvent(event);
        eventHandler1.setActive(true);
        eventHandlerDAO.addEventHandler(eventHandler1);

        // fetch all event handlers
        handlers = eventHandlerDAO.getAllEventHandlers();
        assertNotNull(handlers);
        assertEquals(2, handlers.size());

        // fetch all event handlers for event
        handlers = eventHandlerDAO.getEventHandlersForEvent(event, false);
        assertNotNull(handlers);
        assertEquals(2, handlers.size());

        // fetch only active handlers for event
        handlers = eventHandlerDAO.getEventHandlersForEvent(event, true);
        assertNotNull(handlers);
        assertEquals(1, handlers.size());
        assertEquals(eventHandler1.getName(), handlers.get(0).getName());
        assertEquals(eventHandler1.getEvent(), handlers.get(0).getEvent());
        assertTrue(handlers.get(0).isActive());

        // remove event handler
        eventHandlerDAO.removeEventHandler(eventHandlerName1);
        handlers = eventHandlerDAO.getAllEventHandlers();
        assertNotNull(handlers);
        assertEquals(1, handlers.size());
    }

    @Test
    public void testEventExecutionCRUD() throws Exception {
        String event = "test-event";
        String executionId1 = "id_1";
        String messageId1 = "message1";
        String eventHandler1 = "test_eh_1";
        EventExecution eventExecution1 = getEventExecution(executionId1, messageId1, eventHandler1, event);

        // create event execution explicitly in test
        // since, embedded Cassandra server does not support LWT required for this API.
        addEventExecution(eventExecution1);

        // fetch executions
        List<EventExecution> eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1);
        assertNotNull(eventExecutionList);
        assertEquals(1, eventExecutionList.size());
        assertEquals(eventExecution1, eventExecutionList.get(0));

        // add a different execution for same message
        String executionId2 = "id_2";
        EventExecution eventExecution2 = getEventExecution(executionId2, messageId1, eventHandler1, event);
        addEventExecution(eventExecution2);

        // fetch executions
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1);
        assertNotNull(eventExecutionList);
        assertEquals(2, eventExecutionList.size());
        assertEquals(eventExecution1, eventExecutionList.get(0));
        assertEquals(eventExecution2, eventExecutionList.get(1));

        // update the second execution
        eventExecution2.setStatus(COMPLETED);
        executionDAO.updateEventExecution(eventExecution2);

        // fetch executions
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1);
        assertNotNull(eventExecutionList);
        assertEquals(2, eventExecutionList.size());
        assertEquals(COMPLETED, eventExecutionList.get(1).getStatus());

        // sleep for 5 seconds (TTL)
        Thread.sleep(5000L);
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1);
        assertNotNull(eventExecutionList);
        assertEquals(1, eventExecutionList.size());

        // delete event execution
        executionDAO.removeEventExecution(eventExecution1);
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1);
        assertNotNull(eventExecutionList);
        assertEquals(0, eventExecutionList.size());
    }

    private void addWorkflowDefinition(WorkflowDef workflowDef) throws Exception {
        //INSERT INTO conductor.workflow_definitions (workflow_def_name,version,workflow_definition) VALUES (?,?,?);
        String table = testConfiguration.getCassandraKeyspace() + "." + TABLE_WORKFLOW_DEFS;
        String queryString = "UPDATE " + table
            + " SET workflow_definition='" + objectMapper.writeValueAsString(workflowDef)
            + "' WHERE workflow_def_name='" + workflowDef.getName()
            + "' AND version=" + workflowDef.getVersion()
            + ";";
        session.execute(queryString);
    }

    private void addEventExecution(EventExecution eventExecution) throws Exception {
        //INSERT INTO junit.event_executions (message_id,event_handler_name,event_execution_id,payload) VALUES (?,?,?,?)
        String table = testConfiguration.getCassandraKeyspace() + "." + TABLE_EVENT_EXECUTIONS;
        String queryString = "INSERT INTO " + table
            + " (message_id, event_handler_name, event_execution_id, payload) "
            + "VALUES ('" + eventExecution.getMessageId()
            + "', '" + eventExecution.getName()
            + "', '" + eventExecution.getId()
            + "', '" + objectMapper.writeValueAsString(eventExecution)
            + "');";
        session.execute(queryString);
    }

    private EventExecution getEventExecution(String id, String msgId, String name, String event) {
        EventExecution eventExecution = new EventExecution(id, msgId);
        eventExecution.setName(name);
        eventExecution.setEvent(event);
        eventExecution.setStatus(EventExecution.Status.IN_PROGRESS);
        return eventExecution;
    }
}
