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
package org.conductoross.conductor.ai.mcp.server;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.conductoross.conductor.ai.mcp.McpMethod;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerProtocolTest {

    private static final String VERSION = "2026-07-28";
    private static final String TASKS = "io.modelcontextprotocol/tasks";

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
    private final AtomicReference<McpServerProtocol.McpCall> seen = new AtomicReference<>();

    private McpServerProtocol protocol() {
        McpServerProtocol.MethodHandler echo =
                call -> {
                    seen.set(call);
                    return Mono.just(mapper.createObjectNode().put("ok", true));
                };
        return new McpServerProtocol(
                mapper,
                Map.of(
                        McpMethod.TOOLS_LIST,
                        echo,
                        McpMethod.TOOLS_CALL,
                        echo,
                        McpMethod.TASKS_GET,
                        echo));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private ObjectNode meta(String version, boolean tasks) {
        ObjectNode meta = mapper.createObjectNode();
        meta.put("io.modelcontextprotocol/protocolVersion", version);
        ObjectNode caps = meta.putObject("io.modelcontextprotocol/clientCapabilities");
        if (tasks) {
            caps.putObject("extensions").putObject(TASKS);
        }
        return meta;
    }

    private ObjectNode request(String method, ObjectNode params) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 7);
        body.put("method", method);
        if (params != null) {
            body.set("params", params);
        }
        return body;
    }

    private ObjectNode params(ObjectNode meta) {
        ObjectNode params = mapper.createObjectNode();
        params.set("_meta", meta);
        return params;
    }

    private Map<String, String> headers(String method, String name) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("MCP-Protocol-Version", VERSION);
        headers.put("Mcp-Method", method);
        if (name != null) {
            headers.put("Mcp-Name", name);
        }
        return headers;
    }

    private McpServerProtocol.McpHttpResponse send(Map<String, String> headers, JsonNode body) {
        return send(headers, body.toString());
    }

    private McpServerProtocol.McpHttpResponse send(Map<String, String> headers, String body) {
        return protocol().handle(headers, body).block();
    }

    private static int code(McpServerProtocol.McpHttpResponse response) {
        return response.body().get("error").get("code").asInt();
    }

    // ---- 1. JSON-RPC shape ----------------------------------------------------------------------

    @Test
    void notJson_isParseError400() {
        McpServerProtocol.McpHttpResponse response = send(headers("tools/list", null), "{not json");
        assertEquals(400, response.status());
        assertEquals(-32700, code(response));
    }

    @Test
    void missingMethod_isInvalidRequest400_keepingId() {
        ObjectNode body = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", 7);
        McpServerProtocol.McpHttpResponse response = send(headers("tools/list", null), body);
        assertEquals(400, response.status());
        assertEquals(-32600, code(response));
        assertEquals(7, response.body().get("id").asInt());
    }

    @Test
    void notification_isAccepted202WithNoBody() {
        ObjectNode body = request("notifications/initialized", params(meta(VERSION, false)));
        body.remove("id");
        McpServerProtocol.McpHttpResponse response =
                send(headers("notifications/initialized", null), body);
        assertEquals(202, response.status());
        assertNull(response.body());
    }

    @Test
    void idThatIsNullOrNotStringOrInteger_isInvalidRequest400_andNothingRuns() {
        for (JsonNode id :
                List.of(
                        mapper.nullNode(),
                        mapper.getNodeFactory().booleanNode(true),
                        mapper.createObjectNode(),
                        mapper.getNodeFactory().numberNode(1.5))) {
            ObjectNode body = request("tools/call", params(meta(VERSION, false)));
            body.set("id", id);
            McpServerProtocol.McpHttpResponse response = send(headers("tools/call", null), body);
            assertEquals(400, response.status(), id.toString());
            assertEquals(-32600, code(response), id.toString());
            assertTrue(response.body().get("id").isNull(), id.toString());
        }
        assertNull(seen.get());
    }

    @Test
    void stringId_isARequest() {
        ObjectNode body = request("tools/list", params(meta(VERSION, false)));
        body.put("id", "abc");
        McpServerProtocol.McpHttpResponse response = send(headers("tools/list", null), body);
        assertEquals(200, response.status());
        assertEquals("abc", response.body().get("id").asString());
    }

    // ---- 2. _meta structure ---------------------------------------------------------------------

    @Test
    void missingMeta_is400InvalidParams() {
        McpServerProtocol.McpHttpResponse response =
                send(headers("tools/list", null), request("tools/list", mapper.createObjectNode()));
        assertEquals(400, response.status());
        assertEquals(-32602, code(response));
        assertEquals(7, response.body().get("id").asInt());
    }

    @Test
    void metaWithoutClientCapabilities_is400InvalidParams() {
        ObjectNode meta = mapper.createObjectNode();
        meta.put("io.modelcontextprotocol/protocolVersion", VERSION);
        McpServerProtocol.McpHttpResponse response =
                send(headers("tools/list", null), request("tools/list", params(meta)));
        assertEquals(400, response.status());
        assertEquals(-32602, code(response));
    }

    @Test
    void noClientInfo_isServed() {
        McpServerProtocol.McpHttpResponse response =
                send(
                        headers("tools/list", null),
                        request("tools/list", params(meta(VERSION, false))));
        assertEquals(200, response.status());
        assertTrue(response.body().get("result").get("ok").asBoolean());
    }

    // ---- 3. version -----------------------------------------------------------------------------

    @Test
    void unsupportedVersion_is400WithSupportedAndRequested() {
        Map<String, String> headers = headers("tools/list", null);
        headers.put("MCP-Protocol-Version", "v999.0.0");
        McpServerProtocol.McpHttpResponse response =
                send(headers, request("tools/list", params(meta("v999.0.0", false))));
        assertEquals(400, response.status());
        assertEquals(-32022, code(response));
        JsonNode data = response.body().get("error").get("data");
        assertEquals(
                List.of(VERSION, "2025-11-25", "2025-06-18", "2025-03-26"),
                data.get("supported").valueStream().map(JsonNode::asString).toList());
        assertEquals("v999.0.0", data.get("requested").asString());
    }

    @Test
    void unsupportedMetaVersionWithADifferentHeader_isAHeaderMismatchFirst() {
        // The conformance probe: _meta names an unknown version, the header the real one.
        McpServerProtocol.McpHttpResponse response =
                send(
                        headers("server/discover", null),
                        request("server/discover", params(meta("v999.0.0", false))));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));
    }

    // ---- 4. headers -----------------------------------------------------------------------------

    @Test
    void missingVersionHeader_is400HeaderMismatch() {
        Map<String, String> headers = headers("tools/list", null);
        headers.remove("MCP-Protocol-Version");
        McpServerProtocol.McpHttpResponse response =
                send(headers, request("tools/list", params(meta(VERSION, false))));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));
    }

    @Test
    void versionHeaderDifferentFromMeta_is400HeaderMismatch() {
        Map<String, String> headers = headers("tools/list", null);
        headers.put("MCP-Protocol-Version", "2025-11-25");
        McpServerProtocol.McpHttpResponse response =
                send(headers, request("tools/list", params(meta(VERSION, false))));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));
    }

    @Test
    void methodHeaderDifferentFromBody_is400HeaderMismatch() {
        McpServerProtocol.McpHttpResponse response =
                send(
                        headers("tools/call", null),
                        request("tools/list", params(meta(VERSION, false))));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));
    }

    @Test
    void headerAndBodyDisagree_nameMismatch_isRejectedBeforeTheHandler() {
        ObjectNode params = params(meta(VERSION, false));
        params.put("name", "refund");
        McpServerProtocol.McpHttpResponse response =
                send(headers("tools/call", "order_lookup"), request("tools/call", params));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));
        assertNull(seen.get());
    }

    @Test
    void missingNameHeaderOnToolsCall_is400HeaderMismatch() {
        ObjectNode params = params(meta(VERSION, false));
        params.put("name", "order_lookup");
        McpServerProtocol.McpHttpResponse response =
                send(headers("tools/call", null), request("tools/call", params));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));
    }

    @Test
    void base64SentinelName_isDecodedBeforeComparison() {
        String name = "été";
        String encoded =
                "=?base64?"
                        + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8))
                        + "?=";
        ObjectNode params = params(meta(VERSION, false));
        params.put("name", name);
        McpServerProtocol.McpHttpResponse response =
                send(headers("tools/call", encoded), request("tools/call", params));
        assertEquals(200, response.status());
        assertEquals(name, seen.get().params().get("name").asString());
    }

    @Test
    void base64SentinelOnMethodOrVersion_isAMismatch() {
        // Only Mcp-Name and Mcp-Param-* may be encoded; an intermediary routes on the raw value.
        String method =
                "=?base64?"
                        + Base64.getEncoder()
                                .encodeToString("tools/list".getBytes(StandardCharsets.UTF_8))
                        + "?=";
        Map<String, String> encodedMethod = headers("tools/list", null);
        encodedMethod.put("Mcp-Method", method);
        McpServerProtocol.McpHttpResponse response =
                send(encodedMethod, request("tools/list", params(meta(VERSION, false))));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));

        Map<String, String> encodedVersion = headers("tools/list", null);
        encodedVersion.put(
                "MCP-Protocol-Version",
                "=?base64?"
                        + Base64.getEncoder()
                                .encodeToString(VERSION.getBytes(StandardCharsets.UTF_8))
                        + "?=");
        response = send(encodedVersion, request("tools/list", params(meta(VERSION, false))));
        assertEquals(400, response.status());
        assertEquals(-32020, code(response));
    }

    @Test
    void taskMethodsCompareNameWithTaskId() {
        for (String method : new String[] {"tasks/get", "tasks/update", "tasks/cancel"}) {
            ObjectNode params = params(meta(VERSION, true));
            params.put("taskId", "wf-1");
            McpServerProtocol.McpHttpResponse mismatch =
                    send(headers(method, "wf-2"), request(method, params));
            assertEquals(400, mismatch.status(), method);
            assertEquals(-32020, code(mismatch), method);
            McpServerProtocol.McpHttpResponse missing =
                    send(headers(method, null), request(method, params));
            assertEquals(-32020, code(missing), method);
        }
        ObjectNode params = params(meta(VERSION, true));
        params.put("taskId", "wf-1");
        assertEquals(
                200, send(headers("tasks/get", "wf-1"), request("tasks/get", params)).status());
    }

    @Test
    void unsupportedVersionWithoutAVersionHeader_isUnsupportedVersion_withTheSupportedList() {
        Map<String, String> headers = headers("tools/list", null);
        headers.remove("MCP-Protocol-Version");
        McpServerProtocol.McpHttpResponse response =
                send(headers, request("tools/list", params(meta("v999.0.0", false))));
        assertEquals(400, response.status());
        assertEquals(-32022, code(response));
        assertEquals(
                VERSION,
                response.body().get("error").get("data").get("supported").get(0).asString());
    }

    // ---- 5. methods -----------------------------------------------------------------------------

    @Test
    void removedOrUnknownMethods_are404MethodNotFound() {
        for (String method : new String[] {"initialize", "ping", "resources/list"}) {
            McpServerProtocol.McpHttpResponse response =
                    send(headers(method, null), request(method, params(meta(VERSION, false))));
            assertEquals(404, response.status(), method);
            assertEquals(-32601, code(response), method);
            assertEquals(7, response.body().get("id").asInt(), method);
        }
    }

    // ---- dispatch -------------------------------------------------------------------------------

    @Test
    void otherExtensionsWithoutTasks_doNotDeclareTasks() {
        ObjectNode meta = meta(VERSION, false);
        ((ObjectNode) meta.get("io.modelcontextprotocol/clientCapabilities"))
                .putObject("extensions")
                .putObject("io.modelcontextprotocol/other");
        McpServerProtocol.McpHttpResponse response =
                send(headers("tools/list", null), request("tools/list", params(meta)));
        assertEquals(200, response.status());
        assertFalse(seen.get().declaresTasks());
    }

    @Test
    void handlerSeesDeclaredTasks() {
        protocolCall(true);
        assertTrue(seen.get().declaresTasks());
        protocolCall(false);
        assertFalse(seen.get().declaresTasks());
    }

    private void protocolCall(boolean tasks) {
        McpServerProtocol.McpHttpResponse response =
                send(
                        headers("tools/list", null),
                        request("tools/list", params(meta(VERSION, tasks))));
        assertEquals(200, response.status());
    }

    @Test
    void handlerError_isMappedToItsStatusAndCode() {
        McpServerProtocol failing =
                new McpServerProtocol(
                        mapper,
                        Map.of(
                                McpMethod.TOOLS_LIST,
                                call ->
                                        Mono.error(
                                                new McpServerException(
                                                        400, -32602, "bad tool", null))));
        McpServerProtocol.McpHttpResponse response =
                failing.handle(
                                headers("tools/list", null),
                                request("tools/list", params(meta(VERSION, false))).toString())
                        .block();
        assertEquals(400, response.status());
        assertEquals(-32602, code(response));
        assertEquals("bad tool", response.body().get("error").get("message").asString());
    }

    @Test
    void unexpectedHandlerFailure_isInternalErrorWithoutStackTrace() {
        McpServerProtocol failing =
                new McpServerProtocol(
                        mapper,
                        Map.of(
                                McpMethod.TOOLS_LIST,
                                call -> Mono.error(new IllegalStateException("boom"))));
        McpServerProtocol.McpHttpResponse response =
                failing.handle(
                                headers("tools/list", null),
                                request("tools/list", params(meta(VERSION, false))).toString())
                        .block();
        assertEquals(500, response.status());
        assertEquals(-32603, code(response));
        assertFalse(response.body().get("error").get("message").asString().contains("at "));
    }

    // ---- legacy (2025-xx) clients -----------------------------------------------------------

    private ObjectNode legacyRequest(String method, ObjectNode params) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 9);
        body.put("method", method);
        if (params != null) {
            body.set("params", params);
        }
        return body;
    }

    private static Map<String, String> noHeaders() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void legacyToolsCall_withNoMetaNoHeaderNoInitialize_isServed_withoutResultType() {
        McpServerProtocol protocol =
                new McpServerProtocol(
                        mapper,
                        Map.of(
                                McpMethod.TOOLS_CALL,
                                call -> {
                                    seen.set(call);
                                    return Mono.just(
                                            mapper.createObjectNode()
                                                    .put("resultType", "complete")
                                                    .put("isError", false));
                                }));
        ObjectNode params = mapper.createObjectNode().put("name", "order_lookup");
        McpServerProtocol.McpHttpResponse response =
                protocol.handle(noHeaders(), legacyRequest("tools/call", params).toString())
                        .block();
        assertEquals(200, response.status());
        JsonNode result = response.body().get("result");
        assertFalse(result.has("resultType"));
        assertFalse(result.get("isError").asBoolean());
        assertTrue(seen.get().legacy());
        assertFalse(seen.get().declaresTasks());
    }

    @Test
    void legacyInitialize_negotiatesTheVersion() {
        for (String[] pair :
                new String[][] {
                    {"2025-06-18", "2025-06-18"},
                    {"2025-03-26", "2025-03-26"},
                    {"2025-11-25", "2025-11-25"},
                    {"2024-11-05", "2025-11-25"},
                    {"1999-01-01", "2025-11-25"}
                }) {
            ObjectNode params = mapper.createObjectNode().put("protocolVersion", pair[0]);
            params.putObject("capabilities");
            params.putObject("clientInfo").put("name", "claude");
            McpServerProtocol.McpHttpResponse response =
                    send(noHeaders(), legacyRequest("initialize", params));
            assertEquals(200, response.status(), pair[0]);
            JsonNode result = response.body().get("result");
            assertEquals(pair[1], result.get("protocolVersion").asString(), pair[0]);
            assertTrue(result.get("capabilities").has("tools"));
            assertEquals("conductor", result.get("serverInfo").get("name").asString());
        }
    }

    @Test
    void legacyPing_andNotification() {
        assertEquals(0, send(noHeaders(), legacyRequest("ping", null)).body().get("result").size());
        ObjectNode notification = legacyRequest("notifications/initialized", null);
        notification.remove("id");
        assertEquals(202, send(noHeaders(), notification).status());
    }

    @Test
    void legacySupportedVersionHeader_isAccepted() {
        Map<String, String> headers = noHeaders();
        headers.put("MCP-Protocol-Version", "2025-06-18");
        McpServerProtocol.McpHttpResponse response =
                send(headers, legacyRequest("tools/list", mapper.createObjectNode()));
        assertEquals(200, response.status());
        assertTrue(seen.get().legacy());
    }

    @Test
    void legacyUnsupportedVersionHeader_is400InvalidRequest() {
        Map<String, String> headers = noHeaders();
        headers.put("MCP-Protocol-Version", "2024-01-01");
        McpServerProtocol.McpHttpResponse response =
                send(headers, legacyRequest("tools/list", mapper.createObjectNode()));
        assertEquals(400, response.status());
        assertEquals(-32600, code(response));
    }

    @Test
    void legacyTaskAndDiscoverMethods_areNotFoundWithHttp200() {
        for (String method : new String[] {"tasks/get", "tasks/cancel", "server/discover"}) {
            McpServerProtocol.McpHttpResponse response =
                    send(noHeaders(), legacyRequest(method, mapper.createObjectNode()));
            assertEquals(200, response.status(), method);
            assertEquals(-32601, code(response), method);
        }
    }

    @Test
    void newVersionHeaderWithoutMeta_staysOnTheNewPath() {
        McpServerProtocol.McpHttpResponse response =
                send(
                        headers("initialize", null),
                        legacyRequest("initialize", mapper.createObjectNode()));
        assertEquals(400, response.status());
        assertEquals(-32602, code(response));
    }

    @Test
    void legacyMethodErrors_useHttp200() {
        McpServerProtocol failing =
                new McpServerProtocol(
                        mapper,
                        Map.of(
                                McpMethod.TOOLS_CALL,
                                call -> Mono.error(McpServerException.unknownTool("nope"))));
        McpServerProtocol.McpHttpResponse response =
                failing.handle(
                                noHeaders(),
                                legacyRequest(
                                                "tools/call",
                                                mapper.createObjectNode().put("name", "nope"))
                                        .toString())
                        .block();
        assertEquals(200, response.status());
        assertEquals(-32602, code(response));
    }

    // ---- server/discover ------------------------------------------------------------------------

    @Test
    void discovery_advertisesVersionToolsAndTasks() {
        McpServerProtocol.McpHttpResponse response =
                send(
                        headers("server/discover", null),
                        request("server/discover", params(meta(VERSION, false))));
        assertEquals(200, response.status());
        JsonNode result = response.body().get("result");
        assertEquals(
                List.of(VERSION, "2025-11-25", "2025-06-18", "2025-03-26"),
                result.get("supportedVersions").valueStream().map(JsonNode::asString).toList());
        assertTrue(result.get("capabilities").has("tools"));
        assertTrue(result.get("capabilities").get("extensions").has(TASKS));
        assertFalse(result.get("capabilities").has("tasks"));
        assertFalse(result.get("capabilities").has("resources"));
        assertFalse(result.get("capabilities").has("prompts"));
        assertEquals(
                "conductor",
                result.get("_meta")
                        .get("io.modelcontextprotocol/serverInfo")
                        .get("name")
                        .asString());
    }

    @Test
    void discovery_carriesResultTypeAndCachingHints() {
        JsonNode result =
                send(
                                headers("server/discover", null),
                                request("server/discover", params(meta(VERSION, false))))
                        .body()
                        .get("result");
        assertEquals("complete", result.get("resultType").asString());
        assertEquals(0, result.get("ttlMs").asInt());
        assertEquals("public", result.get("cacheScope").asString());
    }

    @Test
    void everyNewVersionResultHasAResultType_handlersMayChooseIt() {
        McpServerProtocol protocol =
                new McpServerProtocol(
                        mapper,
                        Map.of(
                                McpMethod.TASKS_GET,
                                call ->
                                        Mono.just(
                                                mapper.createObjectNode().put("status", "working")),
                                McpMethod.TOOLS_CALL,
                                call ->
                                        Mono.just(
                                                mapper.createObjectNode()
                                                        .put("resultType", "task"))));
        JsonNode get =
                protocol.handle(
                                headers("tasks/get", "t1"),
                                request(
                                                "tasks/get",
                                                params(meta(VERSION, true)).put("taskId", "t1"))
                                        .toString())
                        .block()
                        .body()
                        .get("result");
        assertEquals("complete", get.get("resultType").asString());
        JsonNode call =
                protocol.handle(
                                headers("tools/call", "x"),
                                request("tools/call", params(meta(VERSION, true)).put("name", "x"))
                                        .toString())
                        .block()
                        .body()
                        .get("result");
        assertEquals("task", call.get("resultType").asString());
    }

    @Test
    void legacyResults_dropTheNewVersionFields() {
        McpServerProtocol protocol =
                new McpServerProtocol(
                        mapper,
                        Map.of(
                                McpMethod.TOOLS_LIST,
                                call ->
                                        Mono.just(
                                                McpServerProtocol.withCachingHints(
                                                        mapper.createObjectNode()
                                                                .put("resultType", "complete")))));
        JsonNode result =
                protocol.handle(
                                noHeaders(),
                                legacyRequest("tools/list", mapper.createObjectNode()).toString())
                        .block()
                        .body()
                        .get("result");
        assertFalse(result.has("resultType"));
        assertFalse(result.has("ttlMs"));
        assertFalse(result.has("cacheScope"));
    }
}
