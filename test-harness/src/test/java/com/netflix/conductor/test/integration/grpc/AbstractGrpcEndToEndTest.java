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
package com.netflix.conductor.test.integration.grpc;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.client.grpc.EventClient;
import com.netflix.conductor.client.grpc.MetadataClient;
import com.netflix.conductor.client.grpc.TaskClient;
import com.netflix.conductor.client.grpc.WorkflowClient;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.test.integration.AbstractEndToEndTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {"conductor.grpc-server.enabled=true", "conductor.grpc-server.port=8092"})
@TestPropertySource(locations = "classpath:application-integrationtest.properties")
public abstract class AbstractGrpcEndToEndTest extends AbstractEndToEndTest {

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
        return eventClient.getEventHandlers(event, activeOnly);
    }

    @Test
    public void testAll() throws Exception {
        assertNotNull(taskClient);
        List<TaskDef> defs = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            TaskDef def = new TaskDef("t" + i, "task " + i, DEFAULT_EMAIL_ADDRESS, 3, 60, 60);
            def.setTimeoutPolicy(TimeoutPolicy.RETRY);
            defs.add(def);
        }
        metadataClient.registerTaskDefs(defs);

        for (int i = 0; i < 5; i++) {
            final String taskName = "t" + i;
            TaskDef def = metadataClient.getTaskDef(taskName);
            assertNotNull(def);
            assertEquals(taskName, def.getName());
        }

        WorkflowDef def = createWorkflowDefinition("test");
        WorkflowTask t0 = createWorkflowTask("t0");
        WorkflowTask t1 = createWorkflowTask("t1");

        def.getTasks().add(t0);
        def.getTasks().add(t1);

        metadataClient.registerWorkflowDef(def);
        WorkflowDef found = metadataClient.getWorkflowDef(def.getName(), null);
        assertNotNull(found);
        assertEquals(def, found);

        String correlationId = "test_corr_id";
        StartWorkflowRequest startWf = new StartWorkflowRequest();
        startWf.setName(def.getName());
        startWf.setCorrelationId(correlationId);

        String workflowId = workflowClient.startWorkflow(startWf);
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
        assertEquals(Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        Task taskById = taskClient.getTaskDetails(task.getTaskId());
        assertNotNull(taskById);
        assertEquals(task.getTaskId(), taskById.getTaskId());

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
    }
}
