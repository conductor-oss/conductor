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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.netflix.conductor.client.grpc.MetadataClient;
import com.netflix.conductor.client.grpc.TaskClient;
import com.netflix.conductor.client.grpc.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.test.integration.AbstractEndToEndTest;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
    "conductor.grpc.server.enabled=true",
    "conductor.grpc.server.port=8092"
})
@TestPropertySource(locations = "classpath:application-integrationtest.properties")
public abstract class AbstractGrpcEndToEndTest extends AbstractEndToEndTest {

    protected static TaskClient taskClient;
    protected static WorkflowClient workflowClient;
    protected static MetadataClient metadataClient;

    @Override
    protected String startWorkflow(String workflowExecutionName, WorkflowDef workflowDefinition) {
        StartWorkflowRequest workflowRequest = new StartWorkflowRequest()
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

        List<String> runningIds = workflowClient.getRunningWorkflow(def.getName(), def.getVersion());
        assertNotNull(runningIds);
        assertEquals(1, runningIds.size());
        assertEquals(workflowId, runningIds.get(0));

        List<Task> polled = taskClient.batchPollTasksByTaskType("non existing task", "test", 1, 100);
        assertNotNull(polled);
        assertEquals(0, polled.size());

        polled = taskClient.batchPollTasksByTaskType(t0.getName(), "test", 1, 100);
        assertNotNull(polled);
        assertEquals(1, polled.size());
        assertEquals(t0.getName(), polled.get(0).getTaskDefName());
        Task task = polled.get(0);

        boolean acked = taskClient.ack(task.getTaskId(), "test");
        assertTrue(acked);

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

        List<Task> getTasks = taskClient.getPendingTasksByType(t0.getName(), null, 1);
        assertNotNull(getTasks);
        assertEquals(0, getTasks.size());        //getTasks only gives pending tasks

        getTasks = taskClient.getPendingTasksByType(t1.getName(), null, 1);
        assertNotNull(getTasks);
        assertEquals(1, getTasks.size());

        Task pending = taskClient.getPendingTaskForWorkflow(workflowId, t1.getTaskReferenceName());
        assertNotNull(pending);
        assertEquals(t1.getTaskReferenceName(), pending.getReferenceTaskName());
        assertEquals(workflowId, pending.getWorkflowInstanceId());

        Thread.sleep(1000);
        SearchResult<WorkflowSummary> searchResult = workflowClient.search("workflowType='" + def.getName() + "'");
        assertNotNull(searchResult);
        assertEquals(1, searchResult.getTotalHits());

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
