/*
 * Copyright 2024 Conductor Authors.
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Advanced end-to-end integration tests for the WAIT_FOR_WEBHOOK system task.
 *
 * <p>Each test exercises a scenario that the basic test does not cover:
 *
 * <ul>
 *   <li><b>Fan-out</b> — two workflow instances waiting under the same hash; one event completes
 *       both.
 *   <li><b>Sequential tasks</b> — two WAIT_FOR_WEBHOOK tasks in a single workflow, each triggered
 *       by a different event.
 *   <li><b>Multi-criteria matching</b> — all JSONPath criteria must match; partial matches are
 *       silently ignored.
 *   <li><b>Stale hash cleanup</b> — a terminated workflow's task is deregistered; a subsequent
 *       webhook event for the same hash returns HTTP 200 without error, and a freshly-started
 *       workflow is still correctly completed.
 * </ul>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = "conductor.enable.ui.serving=false")
public class WaitForWebhookAdvancedIntegrationTest {

    // -----------------------------------------------------------------------
    // Workflow names — unique per scenario to avoid cross-test interference
    // -----------------------------------------------------------------------

    private static final String WF_FANOUT = "wfwh_fanout_e2e";
    private static final String WF_SEQUENTIAL = "wfwh_sequential_e2e";
    private static final String WF_MULTI_CRITERIA = "wfwh_multi_criteria_e2e";
    private static final String WF_STALE = "wfwh_stale_e2e";
    private static final int VERSION = 1;

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;

    private MetadataClient metadataClient;
    private WorkflowClient workflowClient;

    @Before
    public void setUp() {
        String apiRoot = "http://localhost:" + port + "/api/";
        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);
        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);

        registerAll();
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Two workflow instances with identical match criteria register under the same routing hash.
     * One inbound event must complete both — demonstrating fan-out (broadcast) semantics.
     */
    @Test
    public void fanOut_twoInstancesOneEvent_bothComplete() {
        String webhookId = createWebhookConfig(WF_FANOUT);

        String wfId1 = startWorkflow(WF_FANOUT);
        String wfId2 = startWorkflow(WF_FANOUT);

        waitForTaskInProgress(wfId1, "WAIT_FOR_WEBHOOK");
        waitForTaskInProgress(wfId2, "WAIT_FOR_WEBHOOK");

        // Single event — must unblock both instances
        org.springframework.http.ResponseEntity<String> resp =
                post("/webhook/" + webhookId, "{\"event\":{\"type\":\"fanout_trigger\"}}");
        assertEquals(
                "Webhook POST must return HTTP 200; body=" + resp.getBody(),
                200,
                resp.getStatusCodeValue());

        Workflow wf1 = workflowClient.getWorkflow(wfId1, true);
        Workflow wf2 = workflowClient.getWorkflow(wfId2, true);

        assertEquals(
                "First workflow must be COMPLETED after fan-out event",
                WorkflowStatus.COMPLETED,
                wf1.getStatus());
        assertEquals(
                "Second workflow must be COMPLETED after fan-out event",
                WorkflowStatus.COMPLETED,
                wf2.getStatus());
    }

    /**
     * A workflow with two sequential WAIT_FOR_WEBHOOK tasks (different criteria each). The first
     * event completes task 1; the workflow engine immediately starts task 2 (synchronous decide);
     * the second event completes task 2 and finishes the workflow.
     */
    @Test
    public void sequential_twoTasksInOrder_bothComplete() {
        String webhookId = createWebhookConfig(WF_SEQUENTIAL);
        String wfId = startWorkflow(WF_SEQUENTIAL);

        // First task parks immediately
        waitForTaskStatus(wfId, "waitForApproval", Status.IN_PROGRESS);

        // Trigger first task
        post("/webhook/" + webhookId, "{\"phase\":\"approved\"}");

        // First task must be COMPLETED; second task must have started (inline decide is sync)
        waitForTaskStatus(wfId, "waitForApproval", Status.COMPLETED);
        waitForTaskStatus(wfId, "waitForShipment", Status.IN_PROGRESS);

        // Trigger second task
        post("/webhook/" + webhookId, "{\"phase\":\"shipped\"}");

        waitForTaskStatus(wfId, "waitForShipment", Status.COMPLETED);

        Workflow wf = workflowClient.getWorkflow(wfId, false);
        assertEquals(
                "Workflow must be COMPLETED after both sequential tasks are triggered",
                WorkflowStatus.COMPLETED,
                wf.getStatus());
    }

    /**
     * A task with two match criteria requires BOTH to match the inbound payload. An event that
     * satisfies only one criterion must leave the workflow running; an event satisfying both must
     * complete it.
     */
    @Test
    public void multiCriteria_partialMatchIgnored_fullMatchCompletes() throws InterruptedException {
        String webhookId = createWebhookConfig(WF_MULTI_CRITERIA);
        String wfId = startWorkflow(WF_MULTI_CRITERIA);

        waitForTaskInProgress(wfId, "WAIT_FOR_WEBHOOK");

        // Partial match: type correct, region wrong
        post("/webhook/" + webhookId, "{\"event\":{\"type\":\"order\",\"region\":\"us-east\"}}");
        Thread.sleep(300);

        Workflow afterPartial = workflowClient.getWorkflow(wfId, false);
        assertEquals(
                "Workflow must still be RUNNING after a partial match",
                WorkflowStatus.RUNNING,
                afterPartial.getStatus());

        // Full match: both criteria satisfied
        post("/webhook/" + webhookId, "{\"event\":{\"type\":\"order\",\"region\":\"us-west\"}}");

        Workflow afterFull = workflowClient.getWorkflow(wfId, true);
        assertEquals(
                "Workflow must be COMPLETED after full multi-criteria match",
                WorkflowStatus.COMPLETED,
                afterFull.getStatus());
    }

    /**
     * When a workflow is terminated while a WAIT_FOR_WEBHOOK task is IN_PROGRESS, the task's {@code
     * cancel()} method deregisters it from the DAO. A subsequent webhook event for the same hash
     * must:
     *
     * <ol>
     *   <li>Return HTTP 200 (no error — stale hash is silently a no-op).
     *   <li>Not interfere with a new workflow instance started afterward, which must still be
     *       completed by a matching event.
     * </ol>
     */
    @Test
    public void staleHash_terminatedWorkflow_doesNotBlockSubsequentInstance()
            throws InterruptedException {
        String webhookId = createWebhookConfig(WF_STALE);

        // Start first instance and wait for it to park
        String staleWfId = startWorkflow(WF_STALE);
        waitForTaskInProgress(staleWfId, "WAIT_FOR_WEBHOOK");

        // Terminate — WaitForWebhookTask.cancel() deregisters the hash
        workflowClient.terminateWorkflow(staleWfId, "test: stale hash scenario");
        Thread.sleep(200); // allow cancel() to propagate in-memory

        // Fire webhook for the now-gone task — must not throw
        org.springframework.http.ResponseEntity<String> staleResp =
                post("/webhook/" + webhookId, "{\"event\":{\"type\":\"stale_trigger\"}}");
        assertEquals(
                "Webhook POST against stale hash must return HTTP 200",
                200,
                staleResp.getStatusCodeValue());

        // New instance must still work correctly
        String freshWfId = startWorkflow(WF_STALE);
        waitForTaskInProgress(freshWfId, "WAIT_FOR_WEBHOOK");

        post("/webhook/" + webhookId, "{\"event\":{\"type\":\"stale_trigger\"}}");

        Workflow freshWf = workflowClient.getWorkflow(freshWfId, false);
        assertEquals(
                "Fresh workflow instance must be COMPLETED after matching event",
                WorkflowStatus.COMPLETED,
                freshWf.getStatus());
    }

    // -----------------------------------------------------------------------
    // Workflow definitions
    // -----------------------------------------------------------------------

    private void registerAll() {
        register(fanoutDef());
        register(sequentialDef());
        register(multiCriteriaDef());
        register(staleDef());
    }

    private WorkflowDef fanoutDef() {
        WorkflowTask wait =
                waitTask("waitForFanout", Map.of("$['event']['type']", "fanout_trigger"));
        return def(WF_FANOUT, List.of(wait));
    }

    private WorkflowDef sequentialDef() {
        WorkflowTask approval = waitTask("waitForApproval", Map.of("$['phase']", "approved"));
        WorkflowTask shipment = waitTask("waitForShipment", Map.of("$['phase']", "shipped"));
        return def(WF_SEQUENTIAL, List.of(approval, shipment));
    }

    private WorkflowDef multiCriteriaDef() {
        WorkflowTask wait =
                waitTask(
                        "waitForOrder",
                        Map.of("$['event']['type']", "order", "$['event']['region']", "us-west"));
        return def(WF_MULTI_CRITERIA, List.of(wait));
    }

    private WorkflowDef staleDef() {
        WorkflowTask wait = waitTask("waitForStale", Map.of("$['event']['type']", "stale_trigger"));
        return def(WF_STALE, List.of(wait));
    }

    private WorkflowTask waitTask(String refName, Map<String, Object> matches) {
        WorkflowTask t = new WorkflowTask();
        t.setName("WAIT_FOR_WEBHOOK");
        t.setTaskReferenceName(refName);
        t.setType("WAIT_FOR_WEBHOOK");
        t.setInputParameters(Map.of("matches", matches));
        return t;
    }

    private WorkflowDef def(String name, List<WorkflowTask> tasks) {
        WorkflowDef d = new WorkflowDef();
        d.setName(name);
        d.setVersion(VERSION);
        d.setOwnerEmail("test@webhook.test");
        d.setSchemaVersion(2);
        d.setTasks(tasks);
        return d;
    }

    private void register(WorkflowDef def) {
        try {
            metadataClient.registerWorkflowDef(def);
        } catch (Exception e) {
            metadataClient.updateWorkflowDefs(List.of(def));
        }
    }

    // -----------------------------------------------------------------------
    // Webhook config helpers
    // -----------------------------------------------------------------------

    private String createWebhookConfig(String workflowName) {
        Map<String, Object> config =
                Map.of(
                        "name",
                        "e2e-adv-" + UUID.randomUUID(),
                        "verifier",
                        "NONE",
                        "receiverWorkflowNamesToVersions",
                        Map.of(workflowName, VERSION));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        @SuppressWarnings("unchecked")
        Map<String, Object> response =
                restTemplate
                        .postForEntity(
                                "/api/metadata/webhook",
                                new HttpEntity<>(config, headers),
                                Map.class)
                        .getBody();

        assertNotNull("Webhook config creation must return a body", response);
        String id = (String) response.get("id");
        assertNotNull("Created webhook config must have an id", id);
        return id;
    }

    // -----------------------------------------------------------------------
    // Workflow lifecycle helpers
    // -----------------------------------------------------------------------

    private String startWorkflow(String name) {
        StartWorkflowRequest req =
                new StartWorkflowRequest()
                        .withName(name)
                        .withVersion(VERSION)
                        .withInput(Map.of("run", UUID.randomUUID().toString()));
        return workflowClient.startWorkflow(req);
    }

    private void waitForTaskInProgress(String workflowId, String taskType) {
        await("Task " + taskType + " should reach IN_PROGRESS in workflow " + workflowId)
                .atMost(10, SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            return wf.getTasks().stream()
                                    .anyMatch(
                                            t ->
                                                    taskType.equals(t.getTaskType())
                                                            && Status.IN_PROGRESS == t.getStatus());
                        });
    }

    private void waitForTaskStatus(String workflowId, String taskRefName, Status expected) {
        await("Task " + taskRefName + " should reach " + expected)
                .atMost(10, SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            return wf.getTasks().stream()
                                    .filter(t -> taskRefName.equals(t.getReferenceTaskName()))
                                    .anyMatch(t -> expected == t.getStatus());
                        });
    }

    private org.springframework.http.ResponseEntity<String> post(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }
}
