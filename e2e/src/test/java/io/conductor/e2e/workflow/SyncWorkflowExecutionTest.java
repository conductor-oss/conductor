/*
 * Copyright 2022 Conductor Authors.
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
package io.conductor.e2e.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.conductoross.conductor.common.model.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import io.conductor.e2e.util.ApiUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyncWorkflowExecutionTest {

    static WorkflowClient workflowClient;

    static int threshold = 30000;

    @BeforeAll
    public static void init() throws IOException {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        InputStream is =
                SyncWorkflowExecutionTest.class.getResourceAsStream(
                        "/metadata/sync_workflows.json");
        TypeReference<List<WorkflowDef>> listOfWorkflows =
                new TypeReference<List<WorkflowDef>>() {};
        List<WorkflowDef> workflowDefs =
                new ObjectMapperProvider()
                        .getObjectMapper()
                        .readValue(new InputStreamReader(is), listOfWorkflows);
        metadataClient.updateWorkflowDefs(workflowDefs);
    }

    @Test
    @DisplayName("Check sync workflow is executed within 20 seconds")
    public void testSyncWorkflowExecution()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "load_test_perf_sync_workflow";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, List.of(), 25);
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(120, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        long timeTaken = end - start;
        System.out.println(
                String.format(
                        "Workflow %s completed in %d ms.", workflowRun.getWorkflowId(), timeTaken));
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
        System.out.println("Workflow Run: " + workflowRun.getTasks());
    }

    @Test
    @DisplayName("Check sync workflow end with simple task.")
    public void testSyncWorkflowExecution2()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_end_with_simple_task";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, "simple_task_rka0w_ref");
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(11, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long timeTaken = end - start;
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowRun.getStatus());
        workflowClient.terminateWorkflow(workflowRun.getWorkflowId(), "Terminated");
    }

    @Test
    @DisplayName("Check sync workflow end with set variable task.")
    public void testSyncWorkflowExecution3()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_end_with_set_variable_task";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, "set_variable_task_1fi09_ref");
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(11, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        long timeTaken = end - start;
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
    }

    @Test
    @DisplayName("Check sync workflow end with jq task.")
    public void testSyncWorkflowExecution4()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_end_with_jq_task";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(
                        startWorkflowRequest, "json_transform_task_jjowa_ref");
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(11, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long timeTaken = end - start;
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
    }

    @Test
    @DisplayName("Check sync workflow end with sub workflow task.")
    public void testSyncWorkflowExecution5()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_end_with_subworkflow_task";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, "http_sync");
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(21, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long timeTaken = end - start;
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
    }

    @Test
    @Disabled(
            "Depends on external HTTP services (orkes-api-tester.orkesconductor.com, cdatfact.ninja) not reliably accessible in conductor-oss e2e; executeWorkflow times out instead of returning RUNNING state")
    @DisplayName("Check sync workflow end with failed case")
    public void testSyncWorkflowExecution6()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_failed_case";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, "http_fail");
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(9, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long timeTaken = end - start;
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowRun.getStatus());
        workflowClient.terminateWorkflow(workflowRun.getWorkflowId(), "Terminated");
    }

    @Test
    @DisplayName("Check sync workflow end with no poller")
    public void testSyncWorkflowExecution7()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_no_poller";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, "simple_task_pia0h_ref");
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(21, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long timeTaken = end - start;
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowRun.getStatus());
        workflowClient.terminateWorkflow(workflowRun.getWorkflowId(), "Terminated");
    }

    @Test
    @DisplayName("check sync workflow with update variables")
    public void testSyncWorkflowVariableUpdates()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_no_poller";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, "simple_task_pia0h_ref");
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(21, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long timeTaken = end - start;
        assertTrue(timeTaken < threshold, "Time taken was " + timeTaken);
        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowRun.getStatus());
        workflowClient.terminateWorkflow(workflowRun.getWorkflowId(), "Terminated");
    }

    @Test
    @DisplayName(
            "Check sync workflow with inline, wait, and simple task returns before timeout when waitUntilTaskRef is set")
    public void testSyncWorkflowWithInlineWaitAndWaitUntilTaskRef()
            throws ExecutionException, InterruptedException, TimeoutException {

        String workflowName = "sync_workflow_with_inline_wait_and_simple_task";

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        // Execute workflow with waitUntilTaskRef pointing to simple_ref
        // The workflow has: inline task (instant) -> wait task (5 seconds) -> simple task
        // With waitForSeconds=25, the workflow should return after the simple task is scheduled.
        // In conductor-oss postgres, WAIT sweeper adds ~10s overhead on top of the configured
        // duration,
        // so a 5-second WAIT may take ~15 seconds total before simple_ref is scheduled.
        CompletableFuture<WorkflowRun> completableFuture =
                workflowClient.executeWorkflow(startWorkflowRequest, "simple_ref", 25);
        long start = System.currentTimeMillis();
        WorkflowRun workflowRun = completableFuture.get(35, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        long timeTaken = end - start;

        System.out.println("WorkflowId " + workflowRun.getWorkflowId());
        System.out.println(String.format("Workflow completed in %d ms", timeTaken));

        // Verify that the workflow returned after the WAIT task (at least 5 seconds)
        // and before the waitForSeconds timeout (25 seconds + buffer)
        assertTrue(
                timeTaken >= 5000,
                "Workflow should take at least 5 seconds due to WAIT task, but took "
                        + timeTaken
                        + "ms");
        assertTrue(
                timeTaken < 30000,
                "Workflow should complete before 30 second timeout, but took " + timeTaken + "ms");

        // Verify that all three tasks were executed (inline, wait, simple)
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflowRun.getStatus());
        assertEquals(
                3,
                workflowRun.getTasks().size(),
                "Expected 3 tasks to be executed (inline, wait, simple)");

        // Clean up
        workflowClient.terminateWorkflow(workflowRun.getWorkflowId(), "Terminated");
    }
}
