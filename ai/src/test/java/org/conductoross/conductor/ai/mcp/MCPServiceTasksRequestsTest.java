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
package org.conductoross.conductor.ai.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.http.RetryInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opted-in {@code tools/call}, {@code tasks/get} and {@code tasks/cancel} in the MCP 2026-07-28
 * form: request shapes, {@code resultType} routing and result parsing (tasks 3.1, 3.2 and 3.4).
 */
class MCPServiceTasksRequestsTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();
    private static final String TASK_ID = "786512e2-9e0d-44bd-8f29-789f320fe840";

    private MockWebServer server;
    private MCPService service;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        url = server.url("/mcp").toString();
        service = new MCPService(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ---- 3.1 tools/call request and results -----------------------------------------------------

    @Test
    void toolsCallSendsTheTasksRequestForm() throws Exception {
        enqueue("{\"resultType\":\"complete\",\"content\":[]}");

        service.callToolTasks(
                url,
                "execute_sql",
                Map.of("region", "us-west1", "query", "SELECT 1"),
                Map.of("Authorization", "Bearer t", "MCP-Protocol-Version", "2025-06-18"),
                Map.of("Mcp-Param-Region", "us-west1"));

        RecordedRequest request = take();
        JsonNode body = body(request);
        assertEquals("2.0", body.get("jsonrpc").asString());
        assertEquals("tools/call", body.get("method").asString());
        assertTrue(body.has("id"));
        JsonNode params = body.get("params");
        assertEquals("execute_sql", params.get("name").asString());
        assertEquals(
                MAPPER.valueToTree(Map.of("region", "us-west1", "query", "SELECT 1")),
                params.get("arguments"));
        assertFalse(params.has("task"), "no 2025-11-25 task augmentation");
        assertTasksMeta(params.get("_meta"));

        assertEquals("2026-07-28", request.getHeader("MCP-Protocol-Version"));
        assertEquals("tools/call", request.getHeader("Mcp-Method"));
        assertEquals("execute_sql", request.getHeader("Mcp-Name"));
        assertEquals("application/json, text/event-stream", request.getHeader("Accept"));
        assertEquals("us-west1", request.getHeader("Mcp-Param-Region"));
        assertEquals("Bearer t", request.getHeader("Authorization"));
        assertEquals(1, request.getHeaders().values("MCP-Protocol-Version").size());
    }

    @Test
    void completeResultIsMappedLikeTheSynchronousPath() {
        enqueue(
                "{\"resultType\":\"complete\",\"isError\":false,\"content\":["
                        + "{\"type\":\"text\",\"text\":\"{\\\"a\\\":1}\"}]}");

        MCPService.ToolCallOutcome outcome = callEcho();

        assertFalse(outcome.isTask());
        assertNull(outcome.task());
        JsonNode result = MAPPER.valueToTree(outcome.result());
        assertEquals(false, result.get("isError").asBoolean());
        assertEquals(1, result.get("content").get(0).get("parsed").get("a").asInt());
    }

    @Test
    void absentResultTypeIsAnImmediateResult() {
        enqueue("{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}");

        MCPService.ToolCallOutcome outcome = callEcho();

        assertFalse(outcome.isTask());
        assertEquals("hi", MAPPER.valueToTree(outcome.result()).at("/content/0/text").asString());
    }

    @Test
    void createTaskResultIsParsed() {
        enqueue(
                "{\"resultType\":\"task\",\"taskId\":\""
                        + TASK_ID
                        + "\",\"status\":\"working\",\"statusMessage\":\"started\","
                        + "\"createdAt\":\"2026-07-28T10:30:00Z\",\"ttlMs\":60000,"
                        + "\"pollIntervalMs\":5000}");

        MCPService.ToolCallOutcome outcome = callEcho();

        assertTrue(outcome.isTask());
        assertNull(outcome.result());
        MCPService.McpTask task = outcome.task();
        assertEquals(TASK_ID, task.taskId());
        assertEquals("working", task.status());
        assertEquals("started", task.statusMessage());
        assertEquals(5000L, task.pollIntervalMs());
    }

    @Test
    void createTaskResultWithoutPollIntervalHasNoInterval() {
        enqueue("{\"resultType\":\"task\",\"taskId\":\"t-1\",\"status\":\"working\"}");

        MCPService.McpTask task = callEcho().task();

        assertEquals("t-1", task.taskId());
        assertNull(task.pollIntervalMs());
    }

    @Test
    void createTaskResultWithoutTaskIdIsInvalid() {
        enqueue("{\"resultType\":\"task\",\"status\":\"working\"}");

        RuntimeException e = assertThrows(RuntimeException.class, this::callEcho);

        assertTrue(e.getMessage().contains("missing 'taskId'"), e.getMessage());
    }

    @Test
    void networkFailureIsATransportException() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        MCPService.TransportException e =
                assertThrows(MCPService.TransportException.class, this::callEcho);

        assertNotNull(e.getCause());
    }

    @Test
    void staleUserMcpNameAndParamHeadersAreNotSent() throws Exception {
        enqueue("{\"tools\":[{\"name\":\"echo\"}]}");
        enqueue("{\"resultType\":\"complete\",\"content\":[]}");
        Map<String, String> userHeaders =
                Map.of(
                        "mcp-param-region", "stale",
                        "MCP-NAME", "stale",
                        "Authorization", "Bearer t");

        service.listToolsPaged(url, "echo", userHeaders);
        service.callToolTasks(url, "echo", Map.of("q", "x"), userHeaders, Map.of());

        RecordedRequest list = take();
        assertNull(list.getHeader("Mcp-Name"));
        assertNull(list.getHeader("Mcp-Param-Region"));
        assertEquals("Bearer t", list.getHeader("Authorization"));
        RecordedRequest call = take();
        assertEquals("echo", call.getHeader("Mcp-Name"));
        assertEquals(1, call.getHeaders().values("Mcp-Name").size());
        assertNull(call.getHeader("Mcp-Param-Region"));
        assertEquals("Bearer t", call.getHeader("Authorization"));
    }

    @Test
    void schemaLookupStopsAtThePageLimit() {
        server.setDispatcher(
                new okhttp3.mockwebserver.Dispatcher() {
                    private int page;

                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        page++;
                        return new MockResponse()
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[],"
                                                + "\"nextCursor\":\"c-"
                                                + page
                                                + "\"}}");
                    }
                });

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> service.listToolsPaged(url, "echo", Map.of()));

        assertTrue(
                e.getMessage().contains("more than " + MCPService.MAX_TOOLS_LIST_PAGES),
                e.getMessage());
        assertTrue(e.getMessage().contains("'echo'"), e.getMessage());
        assertEquals(MCPService.MAX_TOOLS_LIST_PAGES, server.getRequestCount());
    }

    @Test
    void tasksGetAndTasksCancelAreBoundedByTheirCallTimeout() {
        MCPService bounded =
                new MCPService(new OkHttpClient(), Duration.ofSeconds(1), Duration.ofSeconds(1));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        long started = System.nanoTime();
        assertThrows(
                MCPService.TransportException.class, () -> bounded.getTask(url, "t-1", Map.of()));
        assertThrows(
                MCPService.TransportException.class,
                () -> bounded.cancelTask(url, "t-1", Map.of()));
        long elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000L;

        assertTrue(elapsedSeconds < 10, "took " + elapsedSeconds + " s");
    }

    @Test
    void timeLimitCoversTheWholeRequestAcrossRedirects() {
        MCPService bounded =
                new MCPService(new OkHttpClient(), Duration.ofSeconds(1), Duration.ofSeconds(1));
        // Each hop is quick on its own (400 ms), but six of them exceed the 1 s limit.
        for (int i = 0; i < 6; i++) {
            server.enqueue(
                    new MockResponse()
                            .setResponseCode(307)
                            .setHeader("Location", url)
                            .setHeadersDelay(400, TimeUnit.MILLISECONDS));
        }

        long started = System.nanoTime();
        assertThrows(
                MCPService.TransportException.class, () -> bounded.getTask(url, "t-1", Map.of()));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertTrue(elapsedMillis < 2000, "took " + elapsedMillis + " ms");
        assertTrue(server.getRequestCount() < 6, "requests: " + server.getRequestCount());
    }

    @Test
    void taskRequestsAreNotRetriedByTheSharedClient() {
        MCPService withSharedRetries =
                new MCPService(
                        new OkHttpClient.Builder().addInterceptor(new RetryInterceptor(2)).build());
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        McpRequestException get =
                assertThrows(
                        McpRequestException.class,
                        () -> withSharedRetries.getTask(url, "t-1", Map.of()));
        McpRequestException cancel =
                assertThrows(
                        McpRequestException.class,
                        () -> withSharedRetries.cancelTask(url, "t-1", Map.of()));

        assertEquals(503, get.getHttpStatus());
        assertEquals(503, cancel.getHttpStatus());
        assertEquals(2, server.getRequestCount(), "one request each, no hidden retry");
    }

    @Test
    void toolsCallKeepsTheSharedClientRetriesLikeALegacyCall() {
        MCPService withSharedRetries =
                new MCPService(
                        new OkHttpClient.Builder().addInterceptor(new RetryInterceptor(1)).build());
        server.enqueue(new MockResponse().setResponseCode(503));
        enqueue("{\"resultType\":\"complete\",\"content\":[]}");

        MCPService.ToolCallOutcome outcome =
                withSharedRetries.callToolTasks(url, "echo", Map.of(), Map.of(), Map.of());

        assertFalse(outcome.isTask());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void defaultTaskRequestTimeoutsAreShort() {
        assertEquals(Duration.ofSeconds(30), MCPService.TASK_GET_TIMEOUT);
        assertEquals(Duration.ofSeconds(5), MCPService.TASK_CANCEL_TIMEOUT);
    }

    @Test
    void redirectWithoutLocationIsNotATransportFailure() {
        server.enqueue(new MockResponse().setResponseCode(302));

        RuntimeException e = assertThrows(RuntimeException.class, this::callEcho);

        assertFalse(e instanceof MCPService.TransportException, e.toString());
        assertTrue(e.getMessage().contains("redirect without a valid Location"), e.getMessage());
    }

    @Test
    void refusedCrossOriginRedirectIsNotATransportFailure() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(307)
                        .setHeader("Location", "http://other.example.invalid/mcp"));

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.callToolTasks(
                                        url,
                                        "echo",
                                        Map.of("q", "x"),
                                        Map.of("Authorization", "Bearer t"),
                                        Map.of()));

        assertFalse(e instanceof MCPService.TransportException, e.toString());
        assertTrue(e.getMessage().contains("Refusing to forward credentials"), e.getMessage());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void httpErrorKeepsTheSynchronousDetail() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        McpRequestException e = assertThrows(McpRequestException.class, this::callEcho);

        assertEquals(500, e.getHttpStatus());
        assertEquals("HTTP 500 error from MCP server: boom", e.synchronousDetail());
    }

    // ---- 3.2 resultType routing -----------------------------------------------------------------

    @Test
    void unknownResultTypeIsInvalid() {
        enqueue("{\"resultType\":\"something_else\",\"content\":[]}");

        RuntimeException e = assertThrows(RuntimeException.class, this::callEcho);

        assertTrue(e.getMessage().contains("'something_else'"), e.getMessage());
    }

    @Test
    void inputRequiredResultTypeIsNotSupported() {
        enqueue("{\"resultType\":\"input_required\",\"inputRequests\":{}}");

        RuntimeException e = assertThrows(RuntimeException.class, this::callEcho);

        assertTrue(e.getMessage().contains("'input_required'"), e.getMessage());
        assertTrue(
                e.getMessage().contains("server input requests are not supported"), e.getMessage());
    }

    @Test
    void taskResultTypeIsInvalidOutsideToolsCall() {
        enqueue("{\"resultType\":\"task\",\"taskId\":\"t-1\",\"status\":\"working\"}");

        RuntimeException e =
                assertThrows(RuntimeException.class, () -> service.getTask(url, "t-1", Map.of()));

        assertTrue(e.getMessage().contains("'task'"), e.getMessage());
    }

    @Test
    void schemaLookupRejectsAnInvalidResultType() {
        enqueue("{\"resultType\":\"input_required\",\"tools\":[]}");

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> service.listToolsPaged(url, "echo", Map.of()));

        assertTrue(e.getMessage().contains("'input_required'"), e.getMessage());
    }

    // ---- 3.4 tasks/get and tasks/cancel
    // ----------------------------------------------------------

    @Test
    void tasksGetSendsTheTasksRequestForm() throws Exception {
        enqueue(
                "{\"resultType\":\"complete\",\"taskId\":\""
                        + TASK_ID
                        + "\",\"status\":\"working\",\"pollIntervalMs\":3000}");

        service.getTask(url, TASK_ID, Map.of("Authorization", "Bearer t"));

        RecordedRequest request = take();
        JsonNode body = body(request);
        assertEquals("tasks/get", body.get("method").asString());
        assertEquals(TASK_ID, body.at("/params/taskId").asString());
        assertTasksMeta(body.at("/params/_meta"));
        assertEquals("2026-07-28", request.getHeader("MCP-Protocol-Version"));
        assertEquals("tasks/get", request.getHeader("Mcp-Method"));
        assertEquals(TASK_ID, request.getHeader("Mcp-Name"));
        assertEquals("application/json, text/event-stream", request.getHeader("Accept"));
        assertEquals("Bearer t", request.getHeader("Authorization"));
    }

    @Test
    void tasksCancelSendsTheTasksRequestForm() throws Exception {
        enqueue("{\"resultType\":\"complete\"}");

        service.cancelTask(url, TASK_ID, Map.of());

        RecordedRequest request = take();
        JsonNode body = body(request);
        assertEquals("tasks/cancel", body.get("method").asString());
        assertEquals(TASK_ID, body.at("/params/taskId").asString());
        assertTasksMeta(body.at("/params/_meta"));
        assertEquals("tasks/cancel", request.getHeader("Mcp-Method"));
        assertEquals(TASK_ID, request.getHeader("Mcp-Name"));
    }

    @Test
    void workingStatusIsParsed() {
        enqueue(
                "{\"resultType\":\"complete\",\"taskId\":\"t-1\",\"status\":\"working\","
                        + "\"pollIntervalMs\":3000}");

        MCPService.McpTask task = service.getTask(url, "t-1", Map.of());

        assertEquals("t-1", task.taskId());
        assertEquals("working", task.status());
        assertEquals(3000L, task.pollIntervalMs());
        assertNull(task.result());
        assertNull(task.error());
    }

    @Test
    void completedStatusCarriesTheMappedResult() {
        enqueue(
                "{\"resultType\":\"complete\",\"taskId\":\"t-1\",\"status\":\"completed\","
                        + "\"result\":{\"isError\":true,\"content\":["
                        + "{\"type\":\"text\",\"text\":\"[1,2]\"}]}}");

        MCPService.McpTask task = service.getTask(url, "t-1", Map.of());

        assertEquals("completed", task.status());
        JsonNode result = MAPPER.valueToTree(service.mapTaskResult(task.result()));
        assertTrue(result.get("isError").asBoolean());
        assertEquals(2, result.at("/content/0/parsed").size());
    }

    @Test
    void failedStatusCarriesTheJsonRpcError() {
        enqueue(
                "{\"resultType\":\"complete\",\"taskId\":\"t-1\",\"status\":\"failed\","
                        + "\"error\":{\"code\":-32603,\"message\":\"boom\"}}");

        MCPService.McpTask task = service.getTask(url, "t-1", Map.of());

        assertEquals("failed", task.status());
        assertEquals(-32603, task.error().get("code").asInt());
        assertEquals("boom", task.error().get("message").asString());
        assertNull(task.result());
    }

    @Test
    void otherStatusesAreParsedAsGiven() {
        for (String status : List.of("cancelled", "input_required", "paused")) {
            enqueue(
                    "{\"resultType\":\"complete\",\"taskId\":\"t-1\",\"status\":\""
                            + status
                            + "\"}");

            assertEquals(status, service.getTask(url, "t-1", Map.of()).status());
        }
    }

    @Test
    void missingTaskIdInTasksGetFallsBackToTheRequestedId() {
        enqueue("{\"resultType\":\"complete\",\"status\":\"working\"}");

        assertEquals("t-9", service.getTask(url, "t-9", Map.of()).taskId());
    }

    @Test
    void tasksGetWithoutStatusIsInvalid() {
        enqueue("{\"resultType\":\"complete\",\"taskId\":\"t-1\"}");

        RuntimeException e =
                assertThrows(RuntimeException.class, () -> service.getTask(url, "t-1", Map.of()));

        assertTrue(e.getMessage().contains("missing 'status'"), e.getMessage());
    }

    @Test
    void tasksGetErrorIsAnMcpRequestException() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(400)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,"
                                        + "\"message\":\"Task not found\"}}"));

        McpRequestException e =
                assertThrows(
                        McpRequestException.class, () -> service.getTask(url, "t-1", Map.of()));

        assertEquals(-32602, e.getCode());
        assertEquals("tasks/get", e.getMethod());
    }

    // ---- helpers --------------------------------------------------------------------------------

    private MCPService.ToolCallOutcome callEcho() {
        return service.callToolTasks(url, "echo", Map.of("q", "x"), Map.of(), Map.of());
    }

    private static void assertTasksMeta(JsonNode meta) {
        assertNotNull(meta);
        assertEquals("2026-07-28", meta.get("io.modelcontextprotocol/protocolVersion").asString());
        assertEquals("conductor", meta.at("/io.modelcontextprotocol~1clientInfo/name").asString());
        JsonNode capabilities = meta.get("io.modelcontextprotocol/clientCapabilities");
        assertNotNull(capabilities);
        assertTrue(capabilities.at("/extensions/io.modelcontextprotocol~1tasks").isObject());
        assertEquals(0, capabilities.at("/extensions/io.modelcontextprotocol~1tasks").size());
        assertFalse(capabilities.has("elicitation"));
        assertFalse(capabilities.has("sampling"));
        assertFalse(capabilities.has("roots"));
    }

    private void enqueue(String resultJson) {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}"));
    }

    private RecordedRequest take() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "expected an HTTP request");
        return request;
    }

    private static JsonNode body(RecordedRequest request) {
        return MAPPER.readTree(request.getBody().readUtf8());
    }
}
