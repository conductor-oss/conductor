/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.test.integration.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.validation.ValidationError;
import com.netflix.conductor.test.integration.AbstractEndToEndTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-integrationtest.properties")
public abstract class AbstractHttpEndToEndTest extends AbstractEndToEndTest {

    @LocalServerPort protected int port;

    protected static String apiRoot;

    protected static TaskClient taskClient;
    protected static WorkflowClient workflowClient;
    protected static MetadataClient metadataClient;
    protected static EventClient eventClient;

    @Override
    protected String startWorkflow(String workflowExecutionName, WorkflowDef workflowDefinition) {
        StartWorkflowRequest workflowRequest =
                new StartWorkflowRequest()
                        .withName(workflowExecutionName)
                        .withWorkflowDef(workflowDefinition);

        return workflowClient.startWorkflow(workflowRequest);
    }

    @Override
    protected Workflow getWorkflow(String workflowId, boolean includeTasks) {
        return workflowClient.getWorkflow(workflowId, includeTasks);
    }

    @Override
    protected TaskDef getTaskDefinition(String taskName) {
        return metadataClient.getTaskDef(taskName);
    }

    @Override
    protected void registerTaskDefinitions(List<TaskDef> taskDefinitionList) {
        metadataClient.registerTaskDefs(taskDefinitionList);
    }

    @Override
    protected void registerWorkflowDefinition(WorkflowDef workflowDefinition) {
        metadataClient.registerWorkflowDef(workflowDefinition);
    }

    @Override
    protected void registerEventHandler(EventHandler eventHandler) {
        eventClient.registerEventHandler(eventHandler);
    }

    @Override
    protected Iterator<EventHandler> getEventHandlers(String event, boolean activeOnly) {
        return eventClient.getEventHandlers(event, activeOnly).iterator();
    }

    @Test
    public void testAll() throws Exception {
        createAndRegisterTaskDefinitions("t", 5);

        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        def.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);
        WorkflowTask t0 = new WorkflowTask();
        t0.setName("t0");
        t0.setWorkflowTaskType(TaskType.SIMPLE);
        t0.setTaskReferenceName("t0");

        WorkflowTask t1 = new WorkflowTask();
        t1.setName("t1");
        t1.setWorkflowTaskType(TaskType.SIMPLE);
        t1.setTaskReferenceName("t1");

        def.getTasks().add(t0);
        def.getTasks().add(t1);

        metadataClient.registerWorkflowDef(def);
        WorkflowDef workflowDefinitionFromSystem =
                metadataClient.getWorkflowDef(def.getName(), null);
        assertNotNull(workflowDefinitionFromSystem);
        assertEquals(def, workflowDefinitionFromSystem);

        String correlationId = "test_corr_id";
        StartWorkflowRequest startWorkflowRequest =
                new StartWorkflowRequest()
                        .withName(def.getName())
                        .withCorrelationId(correlationId)
                        .withPriority(50)
                        .withInput(new HashMap<>());
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        assertNotNull(workflowId);

        Workflow workflow = workflowClient.getWorkflow(workflowId, false);
        assertEquals(0, workflow.getTasks().size());
        assertEquals(workflowId, workflow.getWorkflowId());

        workflow = workflowClient.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(t0.getTaskReferenceName(), workflow.getTasks().get(0).getReferenceTaskName());
        assertEquals(workflowId, workflow.getWorkflowId());

        int queueSize = taskClient.getQueueSizeForTask(workflow.getTasks().get(0).getTaskType());
        assertEquals(1, queueSize);

        List<String> runningIds =
                workflowClient.getRunningWorkflow(def.getName(), def.getVersion());
        assertNotNull(runningIds);
        assertEquals(1, runningIds.size());
        assertEquals(workflowId, runningIds.get(0));

        List<Task> polled =
                taskClient.batchPollTasksByTaskType("non existing task", "test", 1, 100);
        assertNotNull(polled);
        assertEquals(0, polled.size());

        polled = taskClient.batchPollTasksByTaskType(t0.getName(), "test", 1, 100);
        assertNotNull(polled);
        assertEquals(1, polled.size());
        assertEquals(t0.getName(), polled.get(0).getTaskDefName());
        Task task = polled.get(0);

        task.getOutputData().put("key1", "value1");
        task.setStatus(Status.COMPLETED);
        taskClient.updateTask(new TaskResult(task));

        polled = taskClient.batchPollTasksByTaskType(t0.getName(), "test", 1, 100);
        assertNotNull(polled);
        assertTrue(polled.toString(), polled.isEmpty());

        workflow = workflowClient.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(t0.getTaskReferenceName(), workflow.getTasks().get(0).getReferenceTaskName());
        assertEquals(t1.getTaskReferenceName(), workflow.getTasks().get(1).getReferenceTaskName());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        Task taskById = taskClient.getTaskDetails(task.getTaskId());
        assertNotNull(taskById);
        assertEquals(task.getTaskId(), taskById.getTaskId());

        queueSize = taskClient.getQueueSizeForTask(workflow.getTasks().get(1).getTaskType());
        assertEquals(1, queueSize);

        Thread.sleep(1000);
        SearchResult<WorkflowSummary> searchResult =
                workflowClient.search("workflowType='" + def.getName() + "'");
        assertNotNull(searchResult);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(workflow.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());

        SearchResult<Workflow> searchResultV2 =
                workflowClient.searchV2("workflowType='" + def.getName() + "'");
        assertNotNull(searchResultV2);
        assertEquals(1, searchResultV2.getTotalHits());
        assertEquals(workflow.getWorkflowId(), searchResultV2.getResults().get(0).getWorkflowId());

        SearchResult<WorkflowSummary> searchResultAdvanced =
                workflowClient.search(0, 1, null, null, "workflowType='" + def.getName() + "'");
        assertNotNull(searchResultAdvanced);
        assertEquals(1, searchResultAdvanced.getTotalHits());
        assertEquals(
                workflow.getWorkflowId(), searchResultAdvanced.getResults().get(0).getWorkflowId());

        SearchResult<Workflow> searchResultV2Advanced =
                workflowClient.searchV2(0, 1, null, null, "workflowType='" + def.getName() + "'");
        assertNotNull(searchResultV2Advanced);
        assertEquals(1, searchResultV2Advanced.getTotalHits());
        assertEquals(
                workflow.getWorkflowId(),
                searchResultV2Advanced.getResults().get(0).getWorkflowId());

        SearchResult<TaskSummary> taskSearchResult =
                taskClient.search("taskType='" + t0.getName() + "'");
        assertNotNull(taskSearchResult);
        assertEquals(1, searchResultV2Advanced.getTotalHits());
        assertEquals(t0.getName(), taskSearchResult.getResults().get(0).getTaskDefName());

        SearchResult<TaskSummary> taskSearchResultAdvanced =
                taskClient.search(0, 1, null, null, "taskType='" + t0.getName() + "'");
        assertNotNull(taskSearchResultAdvanced);
        assertEquals(1, taskSearchResultAdvanced.getTotalHits());
        assertEquals(t0.getName(), taskSearchResultAdvanced.getResults().get(0).getTaskDefName());

        SearchResult<Task> taskSearchResultV2 =
                taskClient.searchV2("taskType='" + t0.getName() + "'");
        assertNotNull(taskSearchResultV2);
        assertEquals(1, searchResultV2Advanced.getTotalHits());
        assertEquals(
                t0.getTaskReferenceName(),
                taskSearchResultV2.getResults().get(0).getReferenceTaskName());

        SearchResult<Task> taskSearchResultV2Advanced =
                taskClient.searchV2(0, 1, null, null, "taskType='" + t0.getName() + "'");
        assertNotNull(taskSearchResultV2Advanced);
        assertEquals(1, taskSearchResultV2Advanced.getTotalHits());
        assertEquals(
                t0.getTaskReferenceName(),
                taskSearchResultV2Advanced.getResults().get(0).getReferenceTaskName());

        workflowClient.terminateWorkflow(workflowId, "terminate reason");
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.TERMINATED, workflow.getStatus());

        workflowClient.restart(workflowId, false);
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        workflowClient.skipTaskFromWorkflow(workflowId, "t1");
    }

    @Test(expected = ConductorClientException.class)
    public void testMetadataWorkflowDefinition() {
        String workflowDefName = "testWorkflowDefMetadata";
        WorkflowDef def = new WorkflowDef();
        def.setName(workflowDefName);
        def.setVersion(1);
        WorkflowTask t0 = new WorkflowTask();
        t0.setName("t0");
        t0.setWorkflowTaskType(TaskType.SIMPLE);
        t0.setTaskReferenceName("t0");
        WorkflowTask t1 = new WorkflowTask();
        t1.setName("t1");
        t1.setWorkflowTaskType(TaskType.SIMPLE);
        t1.setTaskReferenceName("t1");
        def.getTasks().add(t0);
        def.getTasks().add(t1);

        metadataClient.registerWorkflowDef(def);
        metadataClient.unregisterWorkflowDef(workflowDefName, 1);

        try {
            metadataClient.getWorkflowDef(workflowDefName, 1);
        } catch (ConductorClientException e) {
            int statusCode = e.getStatus();
            String errorMessage = e.getMessage();
            boolean retryable = e.isRetryable();
            assertEquals(404, statusCode);
            assertEquals(
                    "No such workflow found by name: testWorkflowDefMetadata, version: 1",
                    errorMessage);
            assertFalse(retryable);
            throw e;
        }
    }

    @Test(expected = ConductorClientException.class)
    public void testInvalidResource() {
        MetadataClient metadataClient = new MetadataClient();
        metadataClient.setRootURI(String.format("%sinvalid", apiRoot));
        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflowDel");
        def.setVersion(1);
        try {
            metadataClient.registerWorkflowDef(def);
        } catch (ConductorClientException e) {
            int statusCode = e.getStatus();
            boolean retryable = e.isRetryable();
            assertEquals(404, statusCode);
            assertFalse(retryable);
            throw e;
        }
    }

    @Test(expected = ConductorClientException.class)
    public void testUpdateWorkflow() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("taskUpdate");
        ArrayList<TaskDef> tasks = new ArrayList<>();
        tasks.add(taskDef);
        metadataClient.registerTaskDefs(tasks);

        WorkflowDef def = new WorkflowDef();
        def.setName("testWorkflowDel");
        def.setVersion(1);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("taskUpdate");
        workflowTask.setTaskReferenceName("taskUpdate");
        List<WorkflowTask> workflowTaskList = new ArrayList<>();
        workflowTaskList.add(workflowTask);
        def.setTasks(workflowTaskList);
        List<WorkflowDef> workflowList = new ArrayList<>();
        workflowList.add(def);
        metadataClient.registerWorkflowDef(def);

        def.setVersion(2);
        metadataClient.updateWorkflowDefs(workflowList);
        WorkflowDef def1 = metadataClient.getWorkflowDef(def.getName(), 2);
        assertNotNull(def1);
        try {
            metadataClient.getTaskDef("test");
        } catch (ConductorClientException e) {
            int statuCode = e.getStatus();
            assertEquals(404, statuCode);
            assertEquals("No such taskType found by name: test", e.getMessage());
            assertFalse(e.isRetryable());
            throw e;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartWorkflow() {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        try {
            workflowClient.startWorkflow(startWorkflowRequest);
        } catch (IllegalArgumentException e) {
            assertEquals("Workflow name cannot be null or empty", e.getMessage());
            throw e;
        }
    }

    @Test(expected = ConductorClientException.class)
    public void testUpdateTask() {
        TaskResult taskResult = new TaskResult();
        try {
            taskClient.updateTask(taskResult);
        } catch (ConductorClientException e) {
            assertEquals(400, e.getStatus());
            assertEquals("Validation failed, check below errors for detail.", e.getMessage());
            assertFalse(e.isRetryable());
            List<ValidationError> errors = e.getValidationErrors();
            List<String> errorMessages =
                    errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
            assertEquals(2, errors.size());
            assertTrue(errorMessages.contains("Workflow Id cannot be null or empty"));
            throw e;
        }
    }

    @Test(expected = ConductorClientException.class)
    public void testGetWorfklowNotFound() {
        try {
            workflowClient.getWorkflow("w123", true);
        } catch (ConductorClientException e) {
            assertEquals(404, e.getStatus());
            assertEquals("No such workflow found by id: w123", e.getMessage());
            assertFalse(e.isRetryable());
            throw e;
        }
    }

    @Test(expected = ConductorClientException.class)
    public void testEmptyCreateWorkflowDef() {
        try {
            WorkflowDef workflowDef = new WorkflowDef();
            metadataClient.registerWorkflowDef(workflowDef);
        } catch (ConductorClientException e) {
            assertEquals(400, e.getStatus());
            assertEquals("Validation failed, check below errors for detail.", e.getMessage());
            assertFalse(e.isRetryable());
            List<ValidationError> errors = e.getValidationErrors();
            List<String> errorMessages =
                    errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
            assertTrue(errorMessages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(errorMessages.contains("WorkflowTask list cannot be empty"));
            throw e;
        }
    }

    @Test(expected = ConductorClientException.class)
    public void testUpdateWorkflowDef() {
        try {
            WorkflowDef workflowDef = new WorkflowDef();
            List<WorkflowDef> workflowDefList = new ArrayList<>();
            workflowDefList.add(workflowDef);
            metadataClient.updateWorkflowDefs(workflowDefList);
        } catch (ConductorClientException e) {
            assertEquals(400, e.getStatus());
            assertEquals("Validation failed, check below errors for detail.", e.getMessage());
            assertFalse(e.isRetryable());
            List<ValidationError> errors = e.getValidationErrors();
            List<String> errorMessages =
                    errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
            assertEquals(3, errors.size());
            assertTrue(errorMessages.contains("WorkflowTask list cannot be empty"));
            assertTrue(errorMessages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(errorMessages.contains("ownerEmail cannot be empty"));
            throw e;
        }
    }

    @Test
    public void testTaskByTaskId() {
        try {
            taskClient.getTaskDetails("test999");
        } catch (ConductorClientException e) {
            assertEquals(404, e.getStatus());
            assertEquals("No such task found by taskId: test999", e.getMessage());
        }
    }

    @Test
    public void testListworkflowsByCorrelationId() {
        workflowClient.getWorkflows("test", "test12", false, false);
    }

    @Test(expected = ConductorClientException.class)
    public void testCreateInvalidWorkflowDef() {
        try {
            WorkflowDef workflowDef = new WorkflowDef();
            List<WorkflowDef> workflowDefList = new ArrayList<>();
            workflowDefList.add(workflowDef);
            metadataClient.registerWorkflowDef(workflowDef);
        } catch (ConductorClientException e) {
            assertEquals(3, e.getValidationErrors().size());
            assertEquals(400, e.getStatus());
            assertEquals("Validation failed, check below errors for detail.", e.getMessage());
            assertFalse(e.isRetryable());
            List<ValidationError> errors = e.getValidationErrors();
            List<String> errorMessages =
                    errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
            assertTrue(errorMessages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(errorMessages.contains("WorkflowTask list cannot be empty"));
            assertTrue(errorMessages.contains("ownerEmail cannot be empty"));
            throw e;
        }
    }

    @Test(expected = ConductorClientException.class)
    public void testUpdateTaskDefNameNull() {
        TaskDef taskDef = new TaskDef();
        try {
            metadataClient.updateTaskDef(taskDef);
        } catch (ConductorClientException e) {
            assertEquals(2, e.getValidationErrors().size());
            assertEquals(400, e.getStatus());
            assertEquals("Validation failed, check below errors for detail.", e.getMessage());
            assertFalse(e.isRetryable());
            List<ValidationError> errors = e.getValidationErrors();
            List<String> errorMessages =
                    errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
            assertTrue(errorMessages.contains("TaskDef name cannot be null or empty"));
            assertTrue(errorMessages.contains("ownerEmail cannot be empty"));
            throw e;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetTaskDefNotExisting() {
        metadataClient.getTaskDef("");
    }

    @Test(expected = ConductorClientException.class)
    public void testUpdateWorkflowDefNameNull() {
        WorkflowDef workflowDef = new WorkflowDef();
        List<WorkflowDef> list = new ArrayList<>();
        list.add(workflowDef);
        try {
            metadataClient.updateWorkflowDefs(list);
        } catch (ConductorClientException e) {
            assertEquals(3, e.getValidationErrors().size());
            assertEquals(400, e.getStatus());
            assertEquals("Validation failed, check below errors for detail.", e.getMessage());
            assertFalse(e.isRetryable());
            List<ValidationError> errors = e.getValidationErrors();
            List<String> errorMessages =
                    errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
            assertTrue(errorMessages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(errorMessages.contains("WorkflowTask list cannot be empty"));
            assertTrue(errorMessages.contains("ownerEmail cannot be empty"));
            throw e;
        }
    }
}
