/*
 * Copyright 2026 Conductor Authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = "conductor.external-payload-storage.type=dummy")
public class ExternalPayloadStorageWorkflowStartHttpIntegrationTest {

    @LocalServerPort private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    public void testExternalInputPayloadPathWithoutConfiguredStorageReturnsBadRequest() {
        String workflowName = "external_payload_storage_http_repro";
        String apiRoot = String.format("http://localhost:%d/api/", port);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setSchemaVersion(2);
        workflowDef.setOwnerEmail("debug@example.com");

        WorkflowTask task = new WorkflowTask();
        task.setName("echo");
        task.setTaskReferenceName("echo_ref");
        task.setType(TaskType.JSON_JQ_TRANSFORM.name());
        task.setInputParameters(
                Map.of(
                        "in", "${workflow.input}",
                        "queryExpression", "{got: .in}"));
        workflowDef.setTasks(List.of(task));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> registrationResponse =
                restTemplate.exchange(
                        apiRoot + "metadata/workflow",
                        HttpMethod.PUT,
                        new HttpEntity<>(List.of(workflowDef), headers),
                        Void.class);
        assertTrue(registrationResponse.getStatusCode().is2xxSuccessful());

        StartWorkflowRequest startRequest =
                new StartWorkflowRequest()
                        .withName(workflowName)
                        .withVersion(1)
                        .withExternalInputPayloadStoragePath("workflow/input/nonexistent.json");

        try {
            restTemplate.postForEntity(apiRoot + "workflow", new HttpEntity<>(startRequest, headers), String.class);
            fail("Expected HTTP 400 when external payload storage is not configured");
        } catch (HttpStatusCodeException exception) {
            assertEquals(400, exception.getStatusCode().value());
            assertTrue(
                    exception
                            .getResponseBodyAsString()
                            .contains("External payload storage is not configured"));
        }
    }
}
