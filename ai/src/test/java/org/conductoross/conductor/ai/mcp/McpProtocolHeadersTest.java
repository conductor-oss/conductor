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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import okhttp3.Request;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec: "Opted-in requests carry the 2026-07-28 per-request protocol fields" and "… headers". */
class McpProtocolHeadersTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    // ---- _meta (task 2.3) -----------------------------------------------------------------------

    @Test
    void toolsCallAndTaskRequestMetaDeclaresTheTasksExtension() {
        JsonNode meta = McpProtocolHeaders.meta(MAPPER, true);

        assertEquals("2026-07-28", meta.get("io.modelcontextprotocol/protocolVersion").asString());
        JsonNode clientInfo = meta.get("io.modelcontextprotocol/clientInfo");
        assertEquals("conductor", clientInfo.get("name").asString());
        assertFalse(clientInfo.get("version").asString().isBlank());

        JsonNode capabilities = meta.get("io.modelcontextprotocol/clientCapabilities");
        assertEquals(
                MAPPER.readTree("{\"extensions\":{\"io.modelcontextprotocol/tasks\":{}}}"),
                capabilities);
        assertFalse(capabilities.has("elicitation"));
        assertFalse(capabilities.has("sampling"));
        assertFalse(capabilities.has("roots"));
        assertFalse(meta.has("task"));
        assertEquals(3, meta.size());
    }

    @Test
    void schemaLookupMetaDeclaresNoCapability() {
        JsonNode meta = McpProtocolHeaders.meta(MAPPER, false);

        assertEquals("2026-07-28", meta.get("io.modelcontextprotocol/protocolVersion").asString());
        assertEquals(
                "conductor", meta.get("io.modelcontextprotocol/clientInfo").get("name").asString());
        assertEquals(
                MAPPER.createObjectNode(), meta.get("io.modelcontextprotocol/clientCapabilities"));
    }

    @Test
    void clientVersionFallsBackToUnknownWithoutAJarManifest() {
        // Test classes run from a directory, so there is no Implementation-Version.
        assertEquals("unknown", McpProtocolHeaders.clientVersion());
    }

    // ---- headers (task 2.4) ---------------------------------------------------------------------

    @Test
    void asciiToolName() {
        Request request = build("tools/call", "get_weather");

        assertEquals("2026-07-28", request.header("MCP-Protocol-Version"));
        assertEquals("tools/call", request.header("Mcp-Method"));
        assertEquals("get_weather", request.header("Mcp-Name"));
        assertEquals("application/json, text/event-stream", request.header("Accept"));
    }

    @Test
    void nonAsciiToolName() {
        assertEquals("=?base64?bcOpdMOpbw==?=", build("tools/call", "météo").header("Mcp-Name"));
    }

    @Test
    void valueMatchingTheSentinelPatternIsEncoded() {
        String name = "=?base64?x?=";
        assertEquals("=?base64?PT9iYXNlNjQ/eD89?=", McpProtocolHeaders.encodeHeaderValue(name));
        assertEquals(
                McpProtocolHeaders.encodeHeaderValue(name),
                build("tools/call", name).header("Mcp-Name"));
    }

    @Test
    void valueWithLeadingOrTrailingWhitespaceIsEncoded() {
        assertEquals("=?base64?IGE=?=", McpProtocolHeaders.encodeHeaderValue(" a"));
        assertEquals("=?base64?YSA=?=", McpProtocolHeaders.encodeHeaderValue("a "));
        assertEquals("=?base64?CWE=?=", McpProtocolHeaders.encodeHeaderValue("\ta"));
    }

    @Test
    void printableAsciiAndInnerTabAreSentUnchanged() {
        assertEquals("a b\tc!~", McpProtocolHeaders.encodeHeaderValue("a b\tc!~"));
        assertEquals("", McpProtocolHeaders.encodeHeaderValue(""));
    }

    @Test
    void controlCharactersAndNonAsciiAreEncoded() {
        assertEquals(
                "=?base64?SGVsbG8sIOS4lueVjA==?=",
                McpProtocolHeaders.encodeHeaderValue("Hello, 世界"));
        assertEquals("=?base64?YQFi?=", McpProtocolHeaders.encodeHeaderValue("a\u0001b"));
    }

    @Test
    void taskRequestsUseTheTaskIdAsMcpName() {
        String taskId = "786512e2-9e0d-44bd-8f29-789f320fe840";
        for (String method : new String[] {"tasks/get", "tasks/cancel"}) {
            Request request = build(method, taskId);
            assertEquals(method, request.header("Mcp-Method"));
            assertEquals(taskId, request.header("Mcp-Name"));
        }
    }

    @Test
    void mcpNameIsOmittedWhenNull() {
        assertNull(build("tools/list", null).header("Mcp-Name"));
    }

    @Test
    void protocolHeadersReplaceUserHeadersIgnoringCaseAndKeepOtherHeaders() {
        Request.Builder builder = new Request.Builder().url("http://localhost/mcp");
        builder.header("mcp-protocol-version", "2025-06-18");
        builder.header("Authorization", "Bearer t");
        builder.header("Accept", "text/plain");

        McpProtocolHeaders.apply(builder, "tools/call", "get_weather");
        Request request = builder.build();

        assertEquals(1, request.headers("MCP-Protocol-Version").size());
        assertEquals("2026-07-28", request.header("MCP-Protocol-Version"));
        assertEquals("Bearer t", request.header("Authorization"));
        assertEquals("application/json, text/event-stream", request.header("Accept"));
        assertTrue(request.headers("Accept").size() == 1);
    }

    private static Request build(String method, String name) {
        Request.Builder builder = new Request.Builder().url("http://localhost/mcp");
        McpProtocolHeaders.apply(builder, method, name);
        return builder.build();
    }
}
