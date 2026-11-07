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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Schema lookup for opted-in calls: {@code tools/list} in the 2026-07-28 form (task 2.6). */
class MCPServiceListToolsPagedTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

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

    @Test
    void toolFoundOnALaterPageKeepsSchemaExtensions() throws Exception {
        enqueue("{\"tools\":[{\"name\":\"other\"}],\"nextCursor\":\"page-2\"}");
        enqueue(
                "{\"tools\":[{\"name\":\"execute_sql\",\"inputSchema\":{\"type\":\"object\","
                        + "\"properties\":{\"region\":{\"type\":\"string\","
                        + "\"x-mcp-header\":\"Region\"}}}}]}");

        Optional<JsonNode> tool = service.listToolsPaged(url, "execute_sql", Map.of());

        assertTrue(tool.isPresent());
        assertEquals(
                "Region",
                tool.get()
                        .path("inputSchema")
                        .path("properties")
                        .path("region")
                        .path("x-mcp-header")
                        .asString());

        JsonNode first = body(take());
        assertFalse(first.get("params").has("cursor"));
        JsonNode second = body(take());
        assertEquals("page-2", second.get("params").get("cursor").asString());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void schemaLookupRequestUsesThe20260728FormWithoutCapabilities() throws Exception {
        enqueue("{\"tools\":[{\"name\":\"echo\"}]}");

        service.listToolsPaged(url, "echo", Map.of());

        RecordedRequest request = take();
        assertEquals(
                MAPPER.readTree(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1,\"params\":{"
                                + "\"_meta\":{"
                                + "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                                + "\"io.modelcontextprotocol/clientInfo\":"
                                + "{\"name\":\"conductor\",\"version\":\"unknown\"},"
                                + "\"io.modelcontextprotocol/clientCapabilities\":{}}}}"),
                body(request));
        assertEquals("2026-07-28", request.getHeader("MCP-Protocol-Version"));
        assertEquals("tools/list", request.getHeader("Mcp-Method"));
        assertNull(request.getHeader("Mcp-Name"));
        assertEquals("application/json, text/event-stream", request.getHeader("Accept"));
    }

    @Test
    void toolNotListedOnAnyPage() {
        enqueue("{\"tools\":[{\"name\":\"a\"}],\"nextCursor\":\"2\"}");
        enqueue("{\"tools\":[{\"name\":\"b\"}],\"nextCursor\":\"\"}");

        assertEquals(Optional.empty(), service.listToolsPaged(url, "echo", Map.of()));
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void userSuppliedProtocolHeaderIsOverriddenAndOthersAreKept() throws Exception {
        enqueue("{\"tools\":[{\"name\":\"echo\"}]}");

        service.listToolsPaged(
                url,
                "echo",
                Map.of("MCP-Protocol-Version", "2025-06-18", "Authorization", "Bearer t"));

        RecordedRequest request = take();
        assertEquals(1, request.getHeaders().values("MCP-Protocol-Version").size());
        assertEquals("2026-07-28", request.getHeader("MCP-Protocol-Version"));
        assertEquals("Bearer t", request.getHeader("Authorization"));
    }

    @Test
    void userHeaderWithCrLfIsStillRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listToolsPaged(url, "echo", Map.of("X-Bad", "a\r\nb")));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void repeatedCursorStopsPaging() {
        enqueue("{\"tools\":[],\"nextCursor\":\"same\"}");
        enqueue("{\"tools\":[],\"nextCursor\":\"same\"}");

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> service.listToolsPaged(url, "echo", Map.of()));
        assertTrue(e.getMessage().contains("repeated tools/list cursor"), e.getMessage());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void singleEventSseResponseIsParsed() {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(
                                "event: message\ndata: "
                                        + result("{\"tools\":[{\"name\":\"echo\"}]}")
                                        + "\n\n"));

        assertTrue(service.listToolsPaged(url, "echo", Map.of()).isPresent());
    }

    @Test
    void httpErrorWithRecognizedCodeRaisesAClassifiedException() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(400)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32022,"
                                        + "\"message\":\"Unsupported protocol version\","
                                        + "\"data\":{\"supported\":[\"2025-11-25\"]}}}"));

        McpRequestException e =
                assertThrows(
                        McpRequestException.class,
                        () -> service.listToolsPaged(url, "echo", Map.of()));
        assertEquals("tools/list", e.getMethod());
        assertEquals(400, e.getHttpStatus());
        assertEquals(-32022, e.getCode());
        assertTrue(e.isRecognizedProtocolError());
    }

    @Test
    void jsonRpcErrorInA200ResponseRaisesAClassifiedException() {
        enqueueRaw(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32021,"
                        + "\"message\":\"Missing capability\","
                        + "\"data\":{\"requiredCapabilities\":[\"elicitation\"]}}}");

        McpRequestException e =
                assertThrows(
                        McpRequestException.class,
                        () -> service.listToolsPaged(url, "echo", Map.of()));
        assertEquals(-32021, e.getCode());
        assertEquals(200, e.getHttpStatus());
        assertTrue(e.getMessage().contains("elicitation"), e.getMessage());
    }

    @Test
    void responseWithoutToolsArrayFails() {
        enqueue("{}");

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> service.listToolsPaged(url, "echo", Map.of()));
        assertTrue(e.getMessage().contains("'tools'"), e.getMessage());
    }

    private void enqueue(String resultJson) {
        enqueueRaw(result(resultJson));
    }

    private void enqueueRaw(String body) {
        server.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
    }

    private static String result(String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}";
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
