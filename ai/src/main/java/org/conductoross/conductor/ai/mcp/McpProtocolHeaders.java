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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import okhttp3.Request;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Request fields and HTTP headers for MCP requests in the {@code 2026-07-28} form. Used only for
 * calls that select {@code mcpOptions.protocolVersion}; the legacy request is untouched.
 *
 * <p>All protocol constants for this revision are kept here, so a change to the specification text
 * touches only this class and {@code McpTasksCall}.
 */
public final class McpProtocolHeaders {

    public static final String PROTOCOL_VERSION = "2026-07-28";
    public static final String TASKS_EXTENSION = "io.modelcontextprotocol/tasks";

    public static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String META_CLIENT_INFO = "io.modelcontextprotocol/clientInfo";
    public static final String META_CLIENT_CAPABILITIES =
            "io.modelcontextprotocol/clientCapabilities";

    public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";
    public static final String HEADER_METHOD = "Mcp-Method";
    public static final String HEADER_NAME = "Mcp-Name";
    public static final String ACCEPT = "application/json, text/event-stream";

    static final String CLIENT_NAME = "conductor";

    private static final String SENTINEL_PREFIX = "=?base64?";
    private static final String SENTINEL_SUFFIX = "?=";

    private static final String CLIENT_VERSION = resolveClientVersion();

    private McpProtocolHeaders() {}

    /**
     * Builds the {@code params._meta} object.
     *
     * @param declareTasks {@code true} for {@code tools/call}, {@code tasks/get} and {@code
     *     tasks/cancel}, which declare the Tasks extension; {@code false} for the schema-lookup
     *     {@code tools/list}, which declares no capability
     */
    public static ObjectNode meta(ObjectMapper objectMapper, boolean declareTasks) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(META_PROTOCOL_VERSION, PROTOCOL_VERSION);

        ObjectNode clientInfo = meta.putObject(META_CLIENT_INFO);
        clientInfo.put("name", CLIENT_NAME);
        clientInfo.put("version", CLIENT_VERSION);

        ObjectNode capabilities = meta.putObject(META_CLIENT_CAPABILITIES);
        if (declareTasks) {
            capabilities.putObject("extensions").putObject(TASKS_EXTENSION);
        }
        return meta;
    }

    /**
     * Sets the protocol headers on a request. Call this after the user-supplied headers have been
     * added: {@link Request.Builder#header} replaces any existing value with the same name,
     * ignoring case, so the protocol headers take precedence and other user headers are kept.
     *
     * @param method the JSON-RPC method, sent as {@code Mcp-Method}
     * @param name the tool name or task ID, sent as {@code Mcp-Name}; {@code null} to omit it
     */
    public static void apply(Request.Builder builder, String method, String name) {
        builder.header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION);
        builder.header(HEADER_METHOD, encodeHeaderValue(method));
        if (name != null) {
            builder.header(HEADER_NAME, encodeHeaderValue(name));
        }
        builder.header("Accept", ACCEPT);
    }

    /**
     * Returns the value to send in a header: unchanged when it is safe as a plain header value,
     * otherwise {@code =?base64?{Base64 of UTF-8}?=}. A value is encoded when it has a character
     * outside {@code 0x20-0x7E} other than tab, has leading or trailing whitespace, or matches the
     * sentinel pattern itself.
     */
    public static String encodeHeaderValue(String value) {
        if (!needsEncoding(value)) {
            return value;
        }
        return SENTINEL_PREFIX
                + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                + SENTINEL_SUFFIX;
    }

    static String clientVersion() {
        return CLIENT_VERSION;
    }

    private static boolean needsEncoding(String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (value.startsWith(SENTINEL_PREFIX) && value.endsWith(SENTINEL_SUFFIX)) {
            return true;
        }
        if (isWhitespace(value.charAt(0)) || isWhitespace(value.charAt(value.length() - 1))) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\t' && (c < 0x20 || c > 0x7E)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
    }

    private static String resolveClientVersion() {
        String version = McpProtocolHeaders.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }
}
