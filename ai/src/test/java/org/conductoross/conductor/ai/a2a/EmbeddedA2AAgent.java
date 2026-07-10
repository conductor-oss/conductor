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
package org.conductoross.conductor.ai.a2a;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A real, embedded HTTP server that speaks the A2A protocol (JSON-RPC 2.0 + SSE + agent-card
 * discovery) over loopback, for hermetic end-to-end tests of the A2A client and {@code AGENT}.
 *
 * <p>Not a mock: it parses real JSON-RPC requests and emits real responses. Behavior is configured
 * per test via {@link SendMode} and {@link #completeAfterPolls(int)}.
 */
final class EmbeddedA2AAgent implements AutoCloseable {

    enum SendMode {
        TASK_COMPLETED,
        TASK_WORKING,
        MESSAGE,
        INPUT_REQUIRED,
        STREAM
    }

    static final String AGENT_TASK_ID = "agent-task-xyz";
    static final String CONTEXT_ID = "ctx-xyz";

    private final HttpServer server;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final AtomicInteger getCalls = new AtomicInteger();
    private final AtomicInteger cancelCalls = new AtomicInteger();
    private final AtomicReference<JsonNode> lastSendParams = new AtomicReference<>();

    private volatile SendMode sendMode = SendMode.TASK_COMPLETED;
    private volatile int completeAfterPolls = 0;
    private volatile String text = "42";
    private volatile String question = "Which currency?";
    private volatile boolean failGetTask = false;

    EmbeddedA2AAgent() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    EmbeddedA2AAgent sendMode(SendMode mode) {
        this.sendMode = mode;
        return this;
    }

    EmbeddedA2AAgent completeAfterPolls(int polls) {
        this.completeAfterPolls = polls;
        return this;
    }

    EmbeddedA2AAgent text(String value) {
        this.text = value;
        return this;
    }

    /** Simulates an agent that goes unreachable while a task is being polled. */
    EmbeddedA2AAgent failGetTask(boolean fail) {
        this.failGetTask = fail;
        return this;
    }

    /** The {@code messageId} sent on the most recent message/send (for idempotency assertions). */
    String lastMessageId() {
        JsonNode params = lastSendParams.get();
        return params == null ? null : params.path("message").path("messageId").asText(null);
    }

    int getCalls() {
        return getCalls.get();
    }

    int cancelCalls() {
        return cancelCalls.get();
    }

    JsonNode lastSendParams() {
        return lastSendParams.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && path.endsWith("/.well-known/agent-card.json")) {
                writeJson(exchange, agentCardJson());
                return;
            }
            if ("GET".equals(method)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            JsonNode request = objectMapper.readTree(bodyBytes);
            String rpcMethod = request.path("method").asText("");
            JsonNode params = request.get("params");
            switch (rpcMethod) {
                case "message/send":
                    lastSendParams.set(params);
                    writeJson(exchange, onSend());
                    break;
                case "message/stream":
                    lastSendParams.set(params);
                    writeStream(exchange);
                    break;
                case "tasks/get":
                    if (failGetTask) {
                        // 500 -> A2AService raises a retryable A2AException (agent unreachable).
                        byte[] err = "agent down".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(500, err.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(err);
                        }
                        break;
                    }
                    writeJson(exchange, onGet());
                    break;
                case "tasks/cancel":
                    cancelCalls.incrementAndGet();
                    writeJson(exchange, resultEnvelope(taskJson("canceled", null, null)));
                    break;
                default:
                    writeJson(
                            exchange,
                            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"method not found\"}}");
            }
        } catch (Exception e) {
            byte[] err =
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32603,\"message\":\"internal\"}}"
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        }
    }

    private String onSend() {
        switch (sendMode) {
            case MESSAGE:
                return resultEnvelope(messageJson(text));
            case INPUT_REQUIRED:
                return resultEnvelope(taskJson("input-required", null, question));
            case TASK_WORKING:
                return resultEnvelope(taskJson("working", null, null));
            case TASK_COMPLETED:
            default:
                return resultEnvelope(taskJson("completed", text, null));
        }
    }

    private String onGet() {
        int call = getCalls.incrementAndGet();
        boolean done = call > completeAfterPolls;
        String state = done ? "completed" : "working";
        return resultEnvelope(taskJson(state, done ? text : null, null));
    }

    private void writeStream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            writeEvent(os, resultEnvelope(taskJson("working", null, null)));
            writeEvent(
                    os,
                    resultEnvelope(
                            artifactUpdateJson("art1", "Hello", /* append= */ false, false)));
            writeEvent(
                    os,
                    resultEnvelope(artifactUpdateJson("art1", "world", /* append= */ true, true)));
            writeEvent(os, resultEnvelope(statusUpdateJson("completed", true)));
        }
    }

    private void writeEvent(OutputStream os, String json) throws IOException {
        os.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String resultEnvelope(String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}";
    }

    private String taskJson(String state, String artifactText, String questionText) {
        StringBuilder status = new StringBuilder("{\"state\":\"" + state + "\"");
        if (questionText != null) {
            status.append(
                    ",\"message\":{\"role\":\"agent\",\"kind\":\"message\",\"parts\":[{\"kind\":\"text\",\"text\":\""
                            + questionText
                            + "\"}]}");
        }
        status.append("}");
        StringBuilder task =
                new StringBuilder(
                        "{\"kind\":\"task\",\"id\":\""
                                + AGENT_TASK_ID
                                + "\",\"contextId\":\""
                                + CONTEXT_ID
                                + "\",\"status\":"
                                + status);
        if (artifactText != null) {
            task.append(
                    ",\"artifacts\":[{\"artifactId\":\"a1\",\"parts\":[{\"kind\":\"text\",\"text\":\""
                            + artifactText
                            + "\"}]}]");
        }
        task.append("}");
        return task.toString();
    }

    private String messageJson(String value) {
        return "{\"kind\":\"message\",\"role\":\"agent\",\"messageId\":\"m1\",\"contextId\":\""
                + CONTEXT_ID
                + "\",\"parts\":[{\"kind\":\"text\",\"text\":\""
                + value
                + "\"}]}";
    }

    private String artifactUpdateJson(
            String artifactId, String partText, boolean append, boolean lastChunk) {
        return "{\"kind\":\"artifact-update\",\"taskId\":\""
                + AGENT_TASK_ID
                + "\",\"contextId\":\""
                + CONTEXT_ID
                + "\",\"append\":"
                + append
                + ",\"lastChunk\":"
                + lastChunk
                + ",\"artifact\":{\"artifactId\":\""
                + artifactId
                + "\",\"parts\":[{\"kind\":\"text\",\"text\":\""
                + partText
                + "\"}]}}";
    }

    private String statusUpdateJson(String state, boolean isFinal) {
        return "{\"kind\":\"status-update\",\"taskId\":\""
                + AGENT_TASK_ID
                + "\",\"contextId\":\""
                + CONTEXT_ID
                + "\",\"status\":{\"state\":\""
                + state
                + "\"},\"final\":"
                + isFinal
                + "}";
    }

    private String agentCardJson() {
        return "{\"name\":\"Embedded Agent\",\"description\":\"A2A test agent\",\"url\":\""
                + url()
                + "\",\"version\":\"1.0.0\",\"protocolVersion\":\"0.3.0\","
                + "\"capabilities\":{\"streaming\":true,\"pushNotifications\":true},"
                + "\"defaultInputModes\":[\"text/plain\"],\"defaultOutputModes\":[\"text/plain\"],"
                + "\"skills\":[{\"id\":\"echo\",\"name\":\"Echo\",\"description\":\"Echoes input\",\"tags\":[\"test\"]}]}";
    }
}
