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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.List;

import org.conductoross.conductor.ai.http.OutboundTargetPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises API discovery against a real local HTTP listener rather than a mocked client. */
class ListApiToolsTaskSecurityTest {

    private MockWebServer server;
    private ListApiToolsTask task;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OutboundTargetPolicy policy = new OutboundTargetPolicy();
        policy.setAllowedOrigins(
                List.of(
                        server.url("/").toString().replaceAll("/$", ""),
                        "https://api.example.test"));
        policy.setAllowPrivateNetworks(true);
        task = new ListApiToolsTask(policy);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void rejectsAnOversizedOpenApiDocumentBeforeItCanBecomeTaskOutput() {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("x".repeat(1024 * 1024 + 1)));

        TaskModel executionTask = start(server.url("/api-docs").toString());

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        assertThat(executionTask.getReasonForIncompletion()).contains("1 MiB payload limit");
        assertThat(executionTask.getOutputData()).doesNotContainKey("tools");
    }

    @Test
    void discoversAnAllowedOpenApiDocument() {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                                """
                                {"openapi":"3.0.3","servers":[{"url":"https://api.example.test"}],"paths":{"/items/{id}":{"get":{"operationId":"getItem","parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"string"}}],"responses":{"200":{"description":"ok"}}}}}}
                                """));

        TaskModel executionTask = start(server.url("/api-docs").toString());

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(executionTask.getOutputData()).containsEntry("format", "openapi3");
        assertThat((List<?>) executionTask.getOutputData().get("tools")).hasSize(1);
    }

    private TaskModel start(String url) {
        TaskModel executionTask = new TaskModel();
        executionTask.getInputData().put("specUrl", url);
        task.start(new WorkflowModel(), executionTask, null);
        return executionTask;
    }
}
