/*
 * Copyright 2023 Orkes, Inc.
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
package io.conductor.e2e.event;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Slf4j
@Disabled("requires external webhook endpoint and enterprise webhook registration API")
public class WebhookTests {

    private static ObjectMapper om = new ObjectMapperProvider().getObjectMapper();

    private static String startWorkflowWebhookId;

    private static String receiveWebhookId;

    private static String webhookUrl;

    private static String receiveWebhookUrl;

    private static String webhookHeaderKey = UUID.randomUUID().toString();

    private static String webhookHeaderValue = UUID.randomUUID().toString();

    private static final String WORKFLOW_NAME = "e2e-webhook-wf";

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private String correlationId = UUID.randomUUID().toString();

    @BeforeAll
    public static void setup() {
        // Webhook registration requires enterprise API - skipped
    }

    @SneakyThrows
    @Test
    @Disabled("requires external webhook endpoint and enterprise webhook registration API")
    public void testWebHook() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        int count = 20;
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            String key = UUID.randomUUID().toString();
            keys[i] = key;
        }

        terminateExistingRunningWorkflows(WORKFLOW_NAME, workflowClient);

        sendWebhook(keys, webhookUrl);
        List<String> workflowIds = new ArrayList<>();
        await().pollInterval(10, TimeUnit.SECONDS).atMost(60, TimeUnit.SECONDS).untilAsserted(() ->{
            SearchResult<WorkflowSummary> workflows = workflowClient.search(0, count, "", correlationId, "status = 'RUNNING'");
            assertNotNull(workflows);
            assertNotNull(workflows.getResults());
            assertEquals(count, workflows.getResults().size());
            workflowIds.addAll(workflows.getResults().stream().map(result -> result.getWorkflowId()).collect(Collectors.toList()));
        });
        assertNotNull(workflowIds);
        assertEquals(count, workflowIds.size());

        for (int i = 0; i < count; i++) {
            String key = keys[i];
            Map<String, Object> input = new HashMap<>();
            input.put("event", Map.of("id", key));
            sendWebhook(input, receiveWebhookUrl);
        }

        await().pollInterval(10, TimeUnit.SECONDS).atMost(60, TimeUnit.SECONDS).untilAsserted(() ->{
            try {
                for (String wfId : workflowIds) {
                    Workflow workflow = workflowClient.getWorkflow(wfId, true);
                    assertNotNull(workflow);
                    assertEquals(2, workflow.getTasks().size());
                    assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
                    assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(1).getStatus());
                    Map<String, Object> event = (Map<String, Object>) workflow.getTasks().get(0).getOutputData().get("event");
                    assertEquals(workflow.getInput().get("key"), event.get("id"));
                }
            }catch (Exception e){

            }
        });

        for (int i = 0; i < count; i++) {
            String key = keys[i];
            Map<String, Object> input = new HashMap<>();
            input.put("key", 12);
            sendWebhook(input, receiveWebhookUrl + "?id=" + key);
        }

        await().pollInterval(1, TimeUnit.SECONDS).atMost(60, TimeUnit.SECONDS).untilAsserted(() ->{
            for (String wfId : workflowIds) {
                Workflow workflow = workflowClient.getWorkflow(wfId, true);
                assertNotNull(workflow);
                assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
                assertEquals(2, workflow.getTasks().size());
                assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
                assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
                assertEquals(workflow.getInput().get("key"), workflow.getTasks().get(1).getOutputData().get("id"));
            }
        });

    }

    private void sendWebhook(String[] keys, String url) {
        for (int i = 0; i < keys.length; i++) {
            Map<String, Object> input = new HashMap<>();
            input.put("key", keys[i]);
            input.put("correlationId", correlationId);
            sendWebhook(input, url);
        }
    }

    @SneakyThrows
    private void sendWebhook(Map<String, Object> input, String url) {
        String json = om.writeValueAsString(input);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header(webhookHeaderKey, webhookHeaderValue)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @SneakyThrows
    @BeforeAll
    @Disabled("requires external webhook endpoint and enterprise webhook registration API")
    public static void registerWebhook() {
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;

        WorkflowDef def = new WorkflowDef();
        def.setName(WORKFLOW_NAME);
        def.setVersion(1);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTimeoutSeconds(120);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setType("WAIT_FOR_WEBHOOK");
        workflowTask.setName("wait_for_webhook");
        workflowTask.setTaskReferenceName("wait_for_webhook");
        workflowTask.getInputParameters().put("matches", Map.of("$['event']['id']", "${workflow.input.key}"));

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setType("WAIT_FOR_WEBHOOK");
        workflowTask2.setName("wait_for_webhook2");
        workflowTask2.setTaskReferenceName("wait_for_webhook2");
        workflowTask2.getInputParameters().put("matches", Map.of("$['id']", "${workflow.input.key}"));

        def.getTasks().add(workflowTask);
        def.getTasks().add(workflowTask2);

        metadataClient.updateWorkflowDefs(List.of(def));

        // Webhook config registration requires enterprise API - not ported
        log.info("Webhook registration skipped - requires enterprise API");
    }

    @SneakyThrows
    @AfterAll
    @Disabled("requires external webhook endpoint and enterprise webhook registration API")
    public static void cleanUp() {
        // Webhook cleanup requires enterprise API - skipped
        log.info("Webhook cleanup skipped - requires enterprise API");
    }

    private void terminateExistingRunningWorkflows(String workflowName, WorkflowClient workflowClient) {
        //clean up first
        try {
            SearchResult<WorkflowSummary> found = workflowClient.search("workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
            System.out.println("Found " + found.getResults().size() + " running workflows to be cleaned up");
            found.getResults().forEach(workflowSummary -> {
                System.out.println("Going to terminate " + workflowSummary.getWorkflowId() + " with status " + workflowSummary.getStatus());
                workflowClient.terminateWorkflow(workflowSummary.getWorkflowId(), "terminate - webhook test");
            });
        } catch(Exception e){
            // Ignore
        }
    }
}
