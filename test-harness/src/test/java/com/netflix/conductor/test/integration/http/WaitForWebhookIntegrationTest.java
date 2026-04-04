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

import org.conductoross.conductor.tasks.webhook.IncomingWebhookResource;
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
import com.netflix.conductor.common.metadata.tasks.Task;
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
 * End-to-end integration test for the WAIT_FOR_WEBHOOK system task.
 *
 * <p>Boots the full Conductor server in-memory (no Redis, no Elasticsearch). Exercises the complete
 * flow:
 *
 * <ol>
 *   <li>Register a workflow definition containing a WAIT_FOR_WEBHOOK task.
 *   <li>Create a webhook config that maps that workflow (via {@code POST /api/metadata/webhook}).
 *   <li>Start a workflow instance and confirm the WAIT_FOR_WEBHOOK task enters IN_PROGRESS.
 *   <li>POST an inbound event to {@code /webhook/{id}} and confirm the workflow completes.
 *   <li>Assert the task output contains the inbound payload.
 * </ol>
 *
 * <p>Uses {@code application-integrationtest.properties} which sets {@code
 * conductor.db.type=memory} and disables indexing/Redis, so no external infrastructure is required.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = "conductor.enable.ui.serving=false")
public class WaitForWebhookIntegrationTest {

    private static final String WORKFLOW_NAME = "wait_for_webhook_e2e_test";
    private static final int WORKFLOW_VERSION = 1;
    private static final String TASK_REF = "waitForEvent";

    @LocalServerPort private int port;

    @Autowired private TestRestTemplate restTemplate;

    // Existence check: if this fails to wire, IncomingWebhookResource bean was not created
    @Autowired private IncomingWebhookResource incomingWebhookResource;

    private MetadataClient metadataClient;
    private WorkflowClient workflowClient;

    @Before
    public void setUp() {
        String apiRoot = "http://localhost:" + port + "/api/";
        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);
        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);
        registerWorkflowDef();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * A matching inbound webhook event completes the waiting task and terminates the workflow.
     *
     * <p>This is the primary happy-path assertion: the task registers itself under the correct
     * routing hash at start, and the inbound event dispatch computes the same hash and completes
     * the task inline.
     */
    @Test
    public void matchingWebhookEvent_completesWorkflow() {
        String webhookId = createWebhookConfig();
        String workflowId = startWorkflow();

        waitForWebhookTaskInProgress(workflowId);

        org.springframework.http.ResponseEntity<String> webhookResponse =
                postWebhookEventWithResponse(
                        webhookId, "{\"event\":{\"type\":\"order_fulfilled\"}}");

        // Diagnostic: check immediate post-dispatch state so failures are informative
        Workflow immediate = workflowClient.getWorkflow(workflowId, true);
        Task immediateTask =
                immediate.getTasks().stream()
                        .filter(t -> "WAIT_FOR_WEBHOOK".equals(t.getTaskType()))
                        .findFirst()
                        .orElse(null);
        assertEquals(
                "Webhook POST must return HTTP 200; body=" + webhookResponse.getBody(),
                200,
                webhookResponse.getStatusCodeValue());
        assertNotNull("WAIT_FOR_WEBHOOK task must exist after event dispatch", immediateTask);
        assertEquals(
                "WAIT_FOR_WEBHOOK task must be COMPLETED after matching event; "
                        + "workflow status="
                        + immediate.getStatus()
                        + ", task output="
                        + immediateTask.getOutputData(),
                Status.COMPLETED,
                immediateTask.getStatus());

        // After task completion the decider runs synchronously; workflow should be COMPLETED now
        assertEquals(
                "Workflow must be COMPLETED after the only task completes",
                WorkflowStatus.COMPLETED,
                immediate.getStatus());

        // Payload keys are spread directly into task output (not wrapped) — Orkes Enterprise format
        @SuppressWarnings("unchecked")
        Map<String, Object> eventOutput =
                (Map<String, Object>) immediateTask.getOutputData().get("event");
        assertNotNull("'event' key must be present in WAIT_FOR_WEBHOOK task output", eventOutput);
        assertEquals("order_fulfilled", eventOutput.get("type"));
    }

    /**
     * An event whose payload does not match the task's criteria is silently ignored: the workflow
     * keeps running with the WAIT_FOR_WEBHOOK task still IN_PROGRESS.
     */
    @Test
    public void nonMatchingWebhookEvent_leavesWorkflowRunning() throws InterruptedException {
        String webhookId = createWebhookConfig();
        String workflowId = startWorkflow();

        waitForWebhookTaskInProgress(workflowId);

        postWebhookEvent(webhookId, "{\"event\":{\"type\":\"something_else\"}}");

        // Give the server time to process the event (it should do nothing)
        Thread.sleep(500);

        Workflow wf = workflowClient.getWorkflow(workflowId, false);
        assertEquals(
                "Workflow must still be RUNNING after a non-matching webhook event",
                WorkflowStatus.RUNNING,
                wf.getStatus());

        workflowClient.terminateWorkflow(workflowId, "test cleanup");
    }

    /**
     * An event sent to an unknown webhook id is silently discarded (HTTP 200, no error). This
     * prevents retry storms when a webhook config is deleted while the provider still has the URL
     * registered.
     */
    @Test
    public void unknownWebhookId_returnsOkAndDiscards() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request =
                new HttpEntity<>("{\"event\":{\"type\":\"anything\"}}", headers);

        org.springframework.http.ResponseEntity<String> response =
                restTemplate.postForEntity("/webhook/" + UUID.randomUUID(), request, String.class);

        assertEquals(
                "Unknown webhook id must return HTTP 200 (silent discard)",
                200,
                response.getStatusCodeValue());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void registerWorkflowDef() {
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setName("WAIT_FOR_WEBHOOK");
        waitTask.setTaskReferenceName(TASK_REF);
        waitTask.setType("WAIT_FOR_WEBHOOK");
        waitTask.setInputParameters(
                Map.of("matches", Map.of("$['event']['type']", "order_fulfilled")));

        WorkflowDef def = new WorkflowDef();
        def.setName(WORKFLOW_NAME);
        def.setVersion(WORKFLOW_VERSION);
        def.setOwnerEmail("test@webhook.test");
        def.setSchemaVersion(2);
        def.setTasks(List.of(waitTask));

        try {
            metadataClient.registerWorkflowDef(def);
        } catch (Exception e) {
            // Definition already registered from a previous test run — update it
            metadataClient.updateWorkflowDefs(List.of(def));
        }
    }

    /**
     * Creates a webhook config pointing at {@link #WORKFLOW_NAME}/{@link #WORKFLOW_VERSION} with
     * the NONE verifier (no auth — suitable for tests).
     *
     * @return the generated webhook id
     */
    private String createWebhookConfig() {
        // Build the request body as a plain map to avoid a hard class dependency on WebhookConfig
        // from the test layer. The JSON shape is what matters.
        Map<String, Object> config =
                Map.of(
                        "name",
                        "e2e-test-" + UUID.randomUUID(),
                        "verifier",
                        "NONE",
                        "receiverWorkflowNamesToVersions",
                        Map.of(WORKFLOW_NAME, WORKFLOW_VERSION));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(config, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response =
                restTemplate.postForEntity("/api/metadata/webhook", request, Map.class).getBody();

        assertNotNull("Webhook config creation must return a body", response);
        String id = (String) response.get("id");
        assertNotNull("Created webhook config must have an id", id);
        return id;
    }

    private String startWorkflow() {
        StartWorkflowRequest req =
                new StartWorkflowRequest()
                        .withName(WORKFLOW_NAME)
                        .withVersion(WORKFLOW_VERSION)
                        .withInput(Map.of("orderId", "test-order-" + UUID.randomUUID()));
        return workflowClient.startWorkflow(req);
    }

    private void waitForWebhookTaskInProgress(String workflowId) {
        await("WAIT_FOR_WEBHOOK task should reach IN_PROGRESS")
                .atMost(10, SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            return wf.getTasks().stream()
                                    .anyMatch(
                                            t ->
                                                    "WAIT_FOR_WEBHOOK".equals(t.getTaskType())
                                                            && Status.IN_PROGRESS == t.getStatus());
                        });
    }

    private void postWebhookEvent(String webhookId, String body) {
        postWebhookEventWithResponse(webhookId, body);
    }

    private org.springframework.http.ResponseEntity<String> postWebhookEventWithResponse(
            String webhookId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity("/webhook/" + webhookId, request, String.class);
    }
}
