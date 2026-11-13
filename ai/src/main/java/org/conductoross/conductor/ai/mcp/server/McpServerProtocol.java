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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.ai.mcp.McpJsonRpc;
import org.conductoross.conductor.ai.mcp.McpMethod;
import org.conductoross.conductor.ai.mcp.McpProtocolHeaders;
import org.conductoross.conductor.ai.mcp.McpResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.HEADER_MISMATCH;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INTERNAL_ERROR;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INVALID_PARAMS;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INVALID_REQUEST;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.METHOD_NOT_FOUND;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.PARSE_ERROR;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.UNSUPPORTED_PROTOCOL_VERSION;

/**
 * The MCP {@code 2026-07-28} protocol layer of the Conductor MCP server: validates one JSON-RPC
 * request, dispatches it to a method handler, and renders the HTTP status and body.
 *
 * <p>It depends on Jackson, Reactor and the client's protocol constants only, never on engine
 * types, so it can be tested on its own. Checks run in a fixed order, and the first failure wins:
 *
 * <ol>
 *   <li>JSON-RPC shape: HTTP 400, {@code -32700} or {@code -32600}.
 *   <li>{@code params._meta} with {@code protocolVersion} and {@code clientCapabilities}: HTTP 400,
 *       {@code -32602}. {@code clientInfo} is optional.
 *   <li>A {@code MCP-Protocol-Version} header that differs from the {@code _meta} version: HTTP
 *       400, {@code -32020}, even when the {@code _meta} version is unsupported.
 *   <li>Protocol version {@code 2026-07-28}: HTTP 400, {@code -32022} with {@code supported} and
 *       {@code requested}.
 *   <li>Headers match the body ({@code MCP-Protocol-Version} present, {@code Mcp-Method}, {@code
 *       Mcp-Name}): HTTP 400, {@code -32020}. Only {@code Mcp-Name} is Base64 sentinel decoded.
 *   <li>The method is implemented: HTTP 404, {@code -32601}.
 * </ol>
 *
 * A notification (no {@code id}) is accepted with HTTP 202 and no body. Every {@code 2026-07-28}
 * result carries a {@code resultType} ({@code "complete"} unless the handler set one).
 */
public final class McpServerProtocol {

    private static final Logger log = LoggerFactory.getLogger(McpServerProtocol.class);

    static final String SERVER_NAME = "conductor";
    static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    private static final String TTL_MS = "ttlMs";
    private static final String CACHE_SCOPE = "cacheScope";

    /** Protocol versions served with the legacy ({@code initialize}) rules, oldest first. */
    static final List<String> LEGACY_VERSIONS = List.of("2025-03-26", "2025-06-18", "2025-11-25");

    /** Methods a legacy request may call; the tool methods use the shared handlers. */
    private static final Set<McpMethod> LEGACY_TOOL_METHODS =
            EnumSet.of(McpMethod.TOOLS_LIST, McpMethod.TOOLS_CALL);

    /** Methods whose {@code Mcp-Name} header carries {@code params.taskId}. */
    private static final Set<McpMethod> TASK_METHODS =
            EnumSet.of(McpMethod.TASKS_GET, McpMethod.TASKS_UPDATE, McpMethod.TASKS_CANCEL);

    private static final String SERVER_VERSION = resolveServerVersion();

    /** The HTTP response for one request; {@code body} is {@code null} for HTTP 202. */
    public record McpHttpResponse(int status, JsonNode body) {}

    /**
     * One validated request, handed to a method handler.
     *
     * @param params the request {@code params}, including {@code _meta}
     * @param declaresTasks whether {@code _meta} declares the Tasks extension
     * @param headers the request headers (case-insensitive keys)
     */
    public record McpCall(
            JsonNode id,
            McpMethod method,
            ObjectNode params,
            ObjectNode meta,
            boolean declaresTasks,
            Map<String, String> headers,
            boolean legacy) {

        /** A {@code 2026-07-28} call. */
        public McpCall(
                JsonNode id,
                McpMethod method,
                ObjectNode params,
                ObjectNode meta,
                boolean declaresTasks,
                Map<String, String> headers) {
            this(id, method, params, meta, declaresTasks, headers, false);
        }
    }

    /**
     * Runs one MCP method. Returns the JSON-RPC {@code result}, or fails with {@link
     * McpServerException} for a protocol error.
     */
    @FunctionalInterface
    public interface MethodHandler {
        Mono<JsonNode> handle(McpCall call);
    }

    private final ObjectMapper mapper;
    private final Map<McpMethod, MethodHandler> handlers;

    /**
     * @param handlers method handlers by JSON-RPC method; {@code server/discover} is built in
     */
    public McpServerProtocol(ObjectMapper mapper, Map<McpMethod, MethodHandler> handlers) {
        this.mapper = mapper;
        this.handlers = new EnumMap<>(McpMethod.class);
        this.handlers.putAll(handlers);
        this.handlers.put(McpMethod.SERVER_DISCOVER, call -> Mono.just(discover()));
    }

    /**
     * Handles one HTTP POST.
     *
     * @param headers request headers; looked up case-insensitively by the caller's map
     * @param rawBody the request body as sent
     */
    public Mono<McpHttpResponse> handle(Map<String, String> headers, String rawBody) {
        JsonNode body;
        try {
            body = rawBody == null || rawBody.isBlank() ? null : mapper.readTree(rawBody);
        } catch (JacksonException e) {
            return Mono.just(error(HTTP_BAD_REQUEST, null, PARSE_ERROR, "Parse error", null));
        }
        if (body == null || !body.isObject()) {
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            null,
                            INVALID_REQUEST,
                            "Invalid Request: not a JSON object",
                            null));
        }
        JsonNode id = body.get("id");
        JsonNode methodNode = body.get("method");
        if (!McpJsonRpc.VERSION.equals(text(body.get("jsonrpc")))
                || methodNode == null
                || !methodNode.isString()) {
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_REQUEST,
                            "Invalid Request: 'jsonrpc' must be \"2.0\" and 'method' a string",
                            null));
        }
        if (id != null && !id.isString() && !id.isIntegralNumber()) {
            // MCP request ids are strings or integers, never null: a reply to "id": null cannot
            // be matched to its request, so the call is refused before anything starts.
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            null,
                            INVALID_REQUEST,
                            "Invalid Request: 'id' must be a string or an integer",
                            null));
        }
        String method = methodNode.asString();
        if (id == null) {
            // A notification: nothing to answer. The server handles none today.
            return Mono.just(new McpHttpResponse(HTTP_ACCEPTED, null));
        }

        JsonNode paramsNode = body.get("params");
        if (isLegacy(paramsNode, headers)) {
            return handleLegacy(id, method, paramsNode, headers);
        }
        if (paramsNode != null && !paramsNode.isObject()) {
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_PARAMS,
                            "'params' must be an object",
                            null));
        }
        ObjectNode params =
                paramsNode == null ? mapper.createObjectNode() : (ObjectNode) paramsNode;
        JsonNode metaNode = params.get("_meta");
        if (metaNode == null || !metaNode.isObject()) {
            return Mono.just(
                    error(HTTP_BAD_REQUEST, id, INVALID_PARAMS, "Missing params._meta", null));
        }
        ObjectNode meta = (ObjectNode) metaNode;
        JsonNode versionNode = meta.get(McpProtocolHeaders.META_PROTOCOL_VERSION);
        JsonNode capabilities = meta.get(McpProtocolHeaders.META_CLIENT_CAPABILITIES);
        if (versionNode == null || !versionNode.isString()) {
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_PARAMS,
                            "Missing _meta." + McpProtocolHeaders.META_PROTOCOL_VERSION,
                            null));
        }
        if (capabilities == null || !capabilities.isObject()) {
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_PARAMS,
                            "Missing _meta." + McpProtocolHeaders.META_CLIENT_CAPABILITIES,
                            null));
        }

        String version = versionNode.asString();
        // A version header that contradicts _meta is a mismatch even for an unsupported version;
        // a missing one is not, so such a client still learns the supported versions.
        // Standard headers are compared as sent: only Mcp-Name and Mcp-Param-* may use the Base64
        // sentinel, and an intermediary routes on the raw value.
        String versionHeader = headers.get(McpProtocolHeaders.HEADER_PROTOCOL_VERSION);
        if (versionHeader != null && !version.equals(versionHeader)) {
            return Mono.just(
                    error(HTTP_BAD_REQUEST, id, HEADER_MISMATCH, versionMismatch(version), null));
        }

        if (!McpProtocolHeaders.PROTOCOL_VERSION.equals(version)) {
            ObjectNode data = mapper.createObjectNode();
            addSupportedVersions(data.putArray("supported"));
            data.put("requested", version);
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            UNSUPPORTED_PROTOCOL_VERSION,
                            "Unsupported protocol version: " + version,
                            data));
        }

        String mismatch = headerMismatch(headers, version, method, params);
        if (mismatch != null) {
            return Mono.just(error(HTTP_BAD_REQUEST, id, HEADER_MISMATCH, mismatch, null));
        }

        McpMethod mcpMethod = McpMethod.of(method);
        MethodHandler handler = handlers.get(mcpMethod);
        if (handler == null) {
            return Mono.just(
                    error(
                            HTTP_NOT_FOUND,
                            id,
                            METHOD_NOT_FOUND,
                            "Method not found: " + method,
                            null));
        }

        boolean declaresTasks =
                capabilities.path("extensions").has(McpProtocolHeaders.TASKS_EXTENSION);
        McpCall call = new McpCall(id, mcpMethod, params, meta, declaresTasks, headers);
        Mono<JsonNode> result;
        try {
            result = handler.handle(call);
        } catch (RuntimeException e) {
            result = Mono.error(e);
        }
        return result.map(value -> success(id, withResultType(value)))
                .onErrorResume(e -> Mono.just(failure(id, method, e)));
    }

    // ---- legacy (2025-xx) clients -----------------------------------------------------------

    /**
     * A request is legacy when {@code params._meta} has no protocol version and the {@code
     * MCP-Protocol-Version} header is not {@code 2026-07-28}. A {@code 2026-07-28} header with no
     * {@code _meta} stays on the {@code 2026-07-28} path, which rejects it with {@code -32602}.
     */
    private static boolean isLegacy(JsonNode params, Map<String, String> headers) {
        if (params != null
                && params.isObject()
                && params.path("_meta").has(McpProtocolHeaders.META_PROTOCOL_VERSION)) {
            return false;
        }
        return !McpProtocolHeaders.PROTOCOL_VERSION.equals(
                headers.get(McpProtocolHeaders.HEADER_PROTOCOL_VERSION));
    }

    /**
     * Serves a client on {@code 2025-03-26}, {@code 2025-06-18} or {@code 2025-11-25}: no session,
     * no {@code _meta}, synchronous tool calls only, and no {@code resultType} in results.
     */
    private Mono<McpHttpResponse> handleLegacy(
            JsonNode id, String method, JsonNode paramsNode, Map<String, String> headers) {
        String versionHeader = headers.get(McpProtocolHeaders.HEADER_PROTOCOL_VERSION);
        if (versionHeader != null && !LEGACY_VERSIONS.contains(versionHeader)) {
            return Mono.just(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_REQUEST,
                            "Unsupported MCP-Protocol-Version: " + versionHeader,
                            null));
        }
        if (paramsNode != null && !paramsNode.isNull() && !paramsNode.isObject()) {
            return Mono.just(
                    error(HTTP_OK, id, INVALID_PARAMS, "'params' must be an object", null));
        }
        ObjectNode params =
                paramsNode == null || paramsNode.isNull()
                        ? mapper.createObjectNode()
                        : (ObjectNode) paramsNode;
        McpMethod mcpMethod = McpMethod.of(method);
        switch (mcpMethod) {
            case INITIALIZE:
                return Mono.just(success(id, initialize(params)));
            case PING:
                return Mono.just(success(id, mapper.createObjectNode()));
            default:
                break;
        }
        MethodHandler handler =
                LEGACY_TOOL_METHODS.contains(mcpMethod) ? handlers.get(mcpMethod) : null;
        if (handler == null) {
            return Mono.just(
                    error(HTTP_OK, id, METHOD_NOT_FOUND, "Method not found: " + method, null));
        }
        McpCall call =
                new McpCall(id, mcpMethod, params, mapper.createObjectNode(), false, headers, true);
        Mono<JsonNode> result;
        try {
            result = handler.handle(call);
        } catch (RuntimeException e) {
            result = Mono.error(e);
        }
        return result.map(value -> success(id, withoutResultType(value)))
                .onErrorResume(e -> Mono.just(legacyFailure(id, method, e)));
    }

    /** The {@code initialize} result: the requested version when supported, else the newest. */
    private JsonNode initialize(ObjectNode params) {
        String requested = text(params.get("protocolVersion"));
        String version =
                requested != null && LEGACY_VERSIONS.contains(requested)
                        ? requested
                        : LEGACY_VERSIONS.get(LEGACY_VERSIONS.size() - 1);
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", version);
        result.putObject("capabilities").putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return result;
    }

    /** A {@code 2026-07-28} result needs a {@code resultType}; {@code complete} by default. */
    private static JsonNode withResultType(JsonNode result) {
        if (result instanceof ObjectNode object && !object.has(McpResultType.FIELD)) {
            object.put(McpResultType.FIELD, McpResultType.COMPLETE.value());
        }
        return result;
    }

    /** A legacy result has no {@code 2026-07-28} fields: no result type, no caching hints. */
    private static JsonNode withoutResultType(JsonNode result) {
        if (result instanceof ObjectNode object) {
            object.remove(McpResultType.FIELD);
            object.remove(TTL_MS);
            object.remove(CACHE_SCOPE);
        }
        return result;
    }

    /**
     * Adds the caching hints a {@code 2026-07-28} list or discovery result must carry. The tool
     * list changes whenever a definition is opted in or an agent is deployed, so clients are told
     * not to cache it ({@code ttlMs} 0); it is the same for every caller ({@code public}).
     */
    static ObjectNode withCachingHints(ObjectNode result) {
        result.put(TTL_MS, 0);
        result.put(CACHE_SCOPE, "public");
        return result;
    }

    /** Legacy method errors are JSON-RPC errors with HTTP 200. */
    private McpHttpResponse legacyFailure(JsonNode id, String method, Throwable e) {
        McpHttpResponse response = failure(id, method, e);
        return response.status() == HTTP_INTERNAL_ERROR
                ? response
                : new McpHttpResponse(HTTP_OK, response.body());
    }

    // ---- checks ---------------------------------------------------------------------------------

    /** Returns a message for the first header that does not match the body, or {@code null}. */
    private static String headerMismatch(
            Map<String, String> headers, String version, String method, ObjectNode params) {
        String versionHeader = headers.get(McpProtocolHeaders.HEADER_PROTOCOL_VERSION);
        if (!version.equals(versionHeader)) {
            return versionMismatch(version);
        }
        if (!method.equals(headers.get(McpProtocolHeaders.HEADER_METHOD))) {
            return McpProtocolHeaders.HEADER_METHOD
                    + " header must equal the method '"
                    + method
                    + "'";
        }
        String expectedName = null;
        McpMethod mcpMethod = McpMethod.of(method);
        if (mcpMethod == McpMethod.TOOLS_CALL) {
            expectedName = text(params.get("name"));
        } else if (TASK_METHODS.contains(mcpMethod)) {
            expectedName = text(params.get("taskId"));
        }
        // A missing body value is the method handler's invalid-params error, not a header one.
        if (expectedName != null
                && !expectedName.equals(
                        McpProtocolHeaders.decodeHeaderValue(
                                headers.get(McpProtocolHeaders.HEADER_NAME)))) {
            return McpProtocolHeaders.HEADER_NAME + " header must equal '" + expectedName + "'";
        }
        return null;
    }

    private static String versionMismatch(String version) {
        return McpProtocolHeaders.HEADER_PROTOCOL_VERSION
                + " header must equal the _meta protocol version '"
                + version
                + "'";
    }

    // ---- results --------------------------------------------------------------------------------

    /**
     * Every version the server serves, newest first: {@code 2026-07-28}, then the older versions,
     * which a client reaches with {@code initialize}.
     */
    private static void addSupportedVersions(ArrayNode versions) {
        versions.add(McpProtocolHeaders.PROTOCOL_VERSION);
        for (int i = LEGACY_VERSIONS.size() - 1; i >= 0; i--) {
            versions.add(LEGACY_VERSIONS.get(i));
        }
    }

    private JsonNode discover() {
        ObjectNode result = mapper.createObjectNode();
        addSupportedVersions(result.putArray("supportedVersions"));
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        capabilities.putObject("extensions").putObject(McpProtocolHeaders.TASKS_EXTENSION);
        ObjectNode serverInfo = result.putObject("_meta").putObject(META_SERVER_INFO);
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return withCachingHints(result);
    }

    private McpHttpResponse success(JsonNode id, JsonNode result) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", McpJsonRpc.VERSION);
        body.set("id", id);
        body.set("result", result);
        return new McpHttpResponse(HTTP_OK, body);
    }

    private McpHttpResponse failure(JsonNode id, String method, Throwable e) {
        if (e instanceof McpServerException mcp) {
            return error(mcp.getHttpStatus(), id, mcp.getCode(), mcp.getMessage(), mcp.getData());
        }
        log.warn("MCP server: {} failed", method, e);
        return error(HTTP_INTERNAL_ERROR, id, INTERNAL_ERROR, "Internal error", null);
    }

    private McpHttpResponse error(
            int status, JsonNode id, int code, String message, JsonNode data) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", McpJsonRpc.VERSION);
        if (id == null) {
            body.putNull("id");
        } else {
            body.set("id", id);
        }
        ObjectNode error = body.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.set("data", data);
        }
        return new McpHttpResponse(status, body);
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    private static String resolveServerVersion() {
        String version = McpServerProtocol.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }
}
