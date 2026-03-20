/*
 * Copyright 2023 Conductor Authors.
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
package io.conductor.e2e.task;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import io.conductor.e2e.util.ApiUtil;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class WaitTaskTest {

    private WorkflowExecutor executor;

    @Test
    public void testWaitTimeout()
            throws ExecutionException, InterruptedException, TimeoutException {
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        executor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 1000);

        ConductorWorkflow<Map<String, Object>> workflow = new ConductorWorkflow<>(executor);
        workflow.setName("wait_task_test");
        workflow.setVersion(1);
        workflow.setVariables(new HashMap<>());
        workflow.add(new Wait("wait_for_2_second", Duration.of(2, SECONDS)));
        CompletableFuture<Workflow> future = workflow.executeDynamic(new HashMap<>());
        assertNotNull(future);
        Workflow run = future.get(60, TimeUnit.SECONDS);
        assertNotNull(run);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, run.getStatus());
        assertEquals(1, run.getTasks().size());
        long timeToExecute =
                run.getTasks().get(0).getEndTime() - run.getTasks().get(0).getScheduledTime();

        // conductor-oss postgres queue may have up to ~10s overhead over the configured wait
        // duration
        assertTrue(
                timeToExecute < 15000,
                "Wait task did not complete in time, took " + timeToExecute + " millis");
    }

    @Test
    @DisplayName(
            "when a workflow is started with task '*' to domain mapping, WAIT task should be executed without a domain")
    public void startWorkflowWithDomain() {
        var workflowClient = ApiUtil.WORKFLOW_CLIENT;

        var workflowDef = new WorkflowDef();
        workflowDef.setName("wait_task__with_domain");
        workflowDef.setVersion(1);

        var waitTask = new WorkflowTask();
        waitTask.setType("WAIT");
        waitTask.setName("wait_task");
        waitTask.setTaskReferenceName("wait_task_ref");
        waitTask.getInputParameters().put("duration", "2 seconds");
        workflowDef.getTasks().add(waitTask);

        var request = new StartWorkflowRequest();
        request.setName(workflowDef.getName());
        request.setVersion(workflowDef.getVersion());
        request.setWorkflowDef(workflowDef);
        request.setTaskToDomain(Map.of("*", "my_domain"));

        var workflowId = workflowClient.startWorkflow(request);
        // conductor-oss postgres queue may have up to ~10s overhead over the configured wait
        // duration;
        // 2s WAIT + ~10s sweeper overhead + buffer = 30s to be safe
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(1, wf.getTasks().size());
                            var t0 = wf.getTasks().get(0);
                            assertEquals("WAIT", t0.getTaskType());
                            assertNull(t0.getDomain());
                            assertEquals(Task.Status.COMPLETED, t0.getStatus());
                        });
    }

    @Test
    @DisplayName("Wait task correctly set task def name")
    public void setVariableAndWaitTaskTest() {
        var workflowClient = ApiUtil.WORKFLOW_CLIENT;

        var workflowDef = new WorkflowDef();
        workflowDef.setName("set_variable_wait_workflow");
        workflowDef.setVersion(1);

        // SET_VARIABLE task
        var setVarTask = new WorkflowTask();
        setVarTask.setType("SET_VARIABLE");
        setVarTask.setName("set_var");
        setVarTask.setTaskReferenceName("set_var_ref");
        setVarTask.getInputParameters().put("var", "testValue");
        setVarTask.getInputParameters().put("value", "42");
        workflowDef.getTasks().add(setVarTask);

        // WAIT task
        var waitTask = new WorkflowTask();
        waitTask.setType("WAIT");
        waitTask.setName("wait_task");
        waitTask.setTaskReferenceName("wait_task_ref");
        waitTask.getInputParameters().put("duration", "1 second");
        workflowDef.getTasks().add(waitTask);

        var request = new StartWorkflowRequest();
        request.setName(workflowDef.getName());
        request.setVersion(workflowDef.getVersion());
        request.setWorkflowDef(workflowDef);

        var workflowId = workflowClient.startWorkflow(request);
        // 1s WAIT + ~10s postgres sweeper overhead + buffer
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(2, wf.getTasks().size());

                            var setVar = wf.getTasks().get(0);
                            assertEquals("SET_VARIABLE", setVar.getTaskType());
                            assertEquals(Task.Status.COMPLETED, setVar.getStatus());

                            var wait = wf.getTasks().get(1);
                            assertEquals("WAIT", wait.getTaskType());
                            assertEquals(Task.Status.COMPLETED, wait.getStatus());
                            assertNotNull(wait.getWorkflowTask());
                            assertNotNull(wait.getWorkflowTask().getTaskDefinition());
                            assertNotNull(wait.getWorkflowTask().getTaskDefinition().getName());
                        });
    }

    @AfterEach
    public void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
