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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises planner-context fetching against a real HTTP server. The request count and
 * If-None-Match header are observed by the server itself; no HTTP-client test double is involved.
 */
class PlannerContextFetchTaskTest {

    private HttpServer server;
    private String endpoint;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger conditionalRequests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/context", this::respond);
        server.start();
        endpoint = "http://localhost:" + server.getAddress().getPort() + "/context";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void cacheHitWithinTtlAvoidsASecondNetworkRequest() {
        PlannerContextFetchTask task = new PlannerContextFetchTask(HttpClient.newHttpClient());
        Map<String, Object> input = Map.of("url", endpoint, "ttl_seconds", 60, "required", true);

        TaskModel first = start(task, input);
        TaskModel second = start(task, input);

        assertThat(responseBody(first)).isEqualTo("body-for-anonymous");
        assertThat(first.getOutputData().get("cache_hit")).isEqualTo(false);
        assertThat(responseBody(second)).isEqualTo("body-for-anonymous");
        assertThat(second.getOutputData().get("cache_hit")).isEqualTo(true);
        assertThat(requests).hasValue(1);
    }

    @Test
    void staleEtagIsRevalidatedByTheRealServerAndKeepsTheCachedBody() throws InterruptedException {
        PlannerContextFetchTask task = new PlannerContextFetchTask(HttpClient.newHttpClient());
        Map<String, Object> input = Map.of("url", endpoint, "ttl_seconds", 1, "required", true);

        TaskModel first = start(task, input);
        Thread.sleep(1_100);
        TaskModel revalidated = start(task, input);

        assertThat(first.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(responseBody(revalidated)).isEqualTo("body-for-anonymous");
        assertThat(revalidated.getOutputData().get("cache_hit")).isEqualTo(true);
        assertThat(requests).hasValue(2);
        assertThat(conditionalRequests).hasValue(1);
    }

    @Test
    void requiredFlagControlsWhetherAnActualServerFailureFailsTheTask() {
        PlannerContextFetchTask task = new PlannerContextFetchTask(HttpClient.newHttpClient());
        String failingEndpoint = endpoint + "?status=503";

        TaskModel optional = start(task, Map.of("url", failingEndpoint, "required", false));
        TaskModel required = start(task, Map.of("url", failingEndpoint, "required", true));

        assertThat(optional.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(responseStatus(optional)).isEqualTo(503);
        assertThat(required.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        assertThat(required.getReasonForIncompletion()).contains("503");
    }

    @Test
    void authorizationHeadersArePartOfTheCacheIdentity() {
        PlannerContextFetchTask task = new PlannerContextFetchTask(HttpClient.newHttpClient());

        TaskModel alice =
                start(
                        task,
                        Map.of(
                                "url",
                                endpoint,
                                "headers",
                                Map.of("Authorization", "Bearer alice"),
                                "required",
                                true));
        TaskModel bob =
                start(
                        task,
                        Map.of(
                                "url",
                                endpoint,
                                "headers",
                                Map.of("Authorization", "Bearer bob"),
                                "required",
                                true));

        assertThat(responseBody(alice)).isEqualTo("body-for-Bearer alice");
        assertThat(responseBody(bob)).isEqualTo("body-for-Bearer bob");
        assertThat(requests).hasValue(2);
    }

    private TaskModel start(PlannerContextFetchTask task, Map<String, Object> input) {
        TaskModel model = new TaskModel();
        model.setInputData(input);
        task.start(null, model, null);
        return model;
    }

    @SuppressWarnings("unchecked")
    private static String responseBody(TaskModel task) {
        return (String) ((Map<String, Object>) task.getOutputData().get("response")).get("body");
    }

    @SuppressWarnings("unchecked")
    private static int responseStatus(TaskModel task) {
        return (int) ((Map<String, Object>) task.getOutputData().get("response")).get("statusCode");
    }

    private void respond(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        if (URI.create(exchange.getRequestURI().toString()).getQuery() != null) {
            send(exchange, 503, "service unavailable");
            return;
        }
        if ("v1".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            conditionalRequests.incrementAndGet();
            exchange.getResponseHeaders().add("ETag", "v1");
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().add("ETag", "v1");
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        send(exchange, 200, "body-for-" + (authorization == null ? "anonymous" : authorization));
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
