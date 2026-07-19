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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.conductoross.conductor.ai.http.OutboundTargetPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises API discovery against a real local HTTP listener rather than a mocked client. */
class ListApiToolsTaskSecurityTest {

    private HttpServer server;
    private AtomicReference<String> responseBody;
    private AtomicReference<Integer> responseStatus;
    private AtomicReference<String> redirectLocation;
    private ListApiToolsTask task;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new AtomicReference<>();
        responseStatus = new AtomicReference<>(200);
        redirectLocation = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    String location = redirectLocation.get();
                    if (location != null) {
                        exchange.getResponseHeaders().set("Location", location);
                    }
                    byte[] body =
                            responseBody.get() == null
                                    ? new byte[0]
                                    : responseBody.get().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(responseStatus.get(), body.length);
                    if (body.length > 0) {
                        exchange.getResponseBody().write(body);
                    }
                    exchange.close();
                });
        server.start();
        OutboundTargetPolicy policy = new OutboundTargetPolicy();
        policy.setAllowedOrigins(List.of(serverUrl("/"), "https://api.example.test"));
        policy.setAllowPrivateNetworks(true);
        task = new ListApiToolsTask(policy);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop(0);
    }

    @Test
    void rejectsAnOversizedOpenApiDocumentBeforeItCanBecomeTaskOutput() {
        responseBody.set("x".repeat(1024 * 1024 + 1));

        TaskModel executionTask = start(serverUrl("/api-docs"));

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        assertThat(executionTask.getReasonForIncompletion()).contains("1 MiB payload limit");
        assertThat(executionTask.getOutputData()).doesNotContainKey("tools");
    }

    @Test
    void discoversAnAllowedOpenApiDocument() {
        responseBody.set(
                """
                {"openapi":"3.0.3","servers":[{"url":"https://api.example.test"}],"paths":{"/items/{id}":{"get":{"operationId":"getItem","parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"string"}}],"responses":{"200":{"description":"ok"}}}}}}
                """);

        TaskModel executionTask = start(serverUrl("/api-docs"));

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(executionTask.getOutputData()).containsEntry("format", "openapi3");
        assertThat(executionTask.getOutputData())
                .containsEntry("baseUrl", "https://api.example.test");
        assertThat((List<?>) executionTask.getOutputData().get("tools")).hasSize(1);
    }

    @Test
    void resolvesRelativeOpenApiServerAgainstTheSpecOrigin() {
        responseBody.set(
                """
                {"openapi":"3.0.3","servers":[{"url":"/v1"}],"paths":{"/items":{"get":{"operationId":"getItems","responses":{"200":{"description":"ok"}}}}}}
                """);

        TaskModel executionTask = start(serverUrl("/docs/openapi.json"));

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(executionTask.getOutputData()).containsEntry("baseUrl", serverUrl("/v1"));
        assertThat(executionTask.getOutputData()).containsEntry("format", "openapi3");
        assertThat((List<?>) executionTask.getOutputData().get("tools")).hasSize(1);
    }

    @Test
    void discoversSwagger2ToolsWithQueryAndBodyInputSchemas() {
        responseBody.set(
                """
                {"swagger":"2.0","host":"127.0.0.1:%d","schemes":["http"],"basePath":"/v1","paths":{"/widgets/{id}":{"post":{"operationId":"updateWidget","parameters":[{"name":"id","in":"path","required":true,"type":"string"},{"name":"dryRun","in":"query","type":"boolean"},{"name":"payload","in":"body","schema":{"type":"object","required":["count"],"properties":{"count":{"type":"integer"}}}}],"responses":{"200":{"description":"ok"}}}}}}
                """
                        .formatted(server.getAddress().getPort()));

        TaskModel executionTask = start(serverUrl("/swagger.json"));

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(executionTask.getOutputData()).containsEntry("format", "swagger2");
        assertThat(executionTask.getOutputData()).containsEntry("baseUrl", serverUrl("/v1"));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> tools =
                (List<java.util.Map<String, Object>>) executionTask.getOutputData().get("tools");
        assertThat(tools)
                .singleElement()
                .satisfies(
                        tool ->
                                assertThat(tool)
                                        .containsEntry("name", "updateWidget")
                                        .containsEntry("method", "POST")
                                        .containsEntry("path", "/widgets/{id}"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> schema =
                (java.util.Map<String, Object>) tools.get(0).get("inputSchema");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> properties =
                (java.util.Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("id", "dryRun", "count");
        assertThat(schema.get("required")).isEqualTo(List.of("id", "count"));
    }

    @Test
    void discoversNestedPostmanRequestsAndInfersJsonBodySchema() {
        String origin = serverUrl("");
        responseBody.set(
                """
                {"info":{"_postman_id":"test-collection"},"item":[{"name":"math","item":[{"name":"sum","request":{"method":"POST","url":"%s/api/sum","body":{"mode":"raw","raw":"{\\"a\\":1,\\"b\\":true}"}}}]}]}
                """
                        .formatted(origin));

        TaskModel executionTask = start(serverUrl("/collection.json"));

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(executionTask.getOutputData()).containsEntry("format", "postman");
        assertThat(executionTask.getOutputData()).containsEntry("baseUrl", origin);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> tools =
                (List<java.util.Map<String, Object>>) executionTask.getOutputData().get("tools");
        assertThat(tools)
                .singleElement()
                .satisfies(
                        tool ->
                                assertThat(tool)
                                        .containsEntry("name", "math_sum")
                                        .containsEntry("method", "POST")
                                        .containsEntry("path", "/api/sum"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> schema =
                (java.util.Map<String, Object>) tools.get(0).get("inputSchema");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> properties =
                (java.util.Map<String, Object>) schema.get("properties");
        assertThat(properties.get("a")).isEqualTo(java.util.Map.of("type", "integer"));
        assertThat(properties.get("b")).isEqualTo(java.util.Map.of("type", "boolean"));
    }

    @Test
    void rejectsAValidSpecThatAdvertisesAnUnapprovedApiServer() {
        responseBody.set(
                """
                {"openapi":"3.0.3","servers":[{"url":"https://unapproved.example.test"}],"paths":{}}
                """);

        TaskModel executionTask = start(serverUrl("/api-docs"));

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        assertThat(executionTask.getReasonForIncompletion()).contains("not allow-listed");
        assertThat(executionTask.getOutputData()).doesNotContainKey("tools");
    }

    @Test
    void refusesToForwardCredentialsAcrossAnApiSpecRedirect() {
        responseStatus.set(302);
        redirectLocation.set("https://api.example.test/openapi.json");

        TaskModel executionTask =
                start(serverUrl("/api-docs"), java.util.Map.of("Authorization", "Bearer secret"));

        assertThat(executionTask.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        assertThat(executionTask.getReasonForIncompletion())
                .contains("Refusing to forward credentials across an API spec redirect");
        assertThat(executionTask.getOutputData()).doesNotContainKey("tools");
    }

    private TaskModel start(String url) {
        return start(url, java.util.Map.of());
    }

    private TaskModel start(String url, java.util.Map<String, String> headers) {
        TaskModel executionTask = new TaskModel();
        executionTask.getInputData().put("specUrl", url);
        executionTask.getInputData().put("headers", headers);
        task.start(new WorkflowModel(), executionTask, null);
        return executionTask;
    }

    private String serverUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
