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
import java.util.Optional;
import java.util.Set;

import org.conductoross.conductor.ai.mcp.MCPFields;
import org.conductoross.conductor.ai.mcp.MCPJsonRpc;
import org.conductoross.conductor.ai.mcp.MCPMethod;
import org.conductoross.conductor.ai.mcp.MCPProtocolHeaders;
import org.conductoross.conductor.ai.mcp.MCPProtocolVersion;
import org.conductoross.conductor.ai.mcp.MCPResultType;
import org.conductoross.conductor.ai.mcp.server.model.MCPCapabilities;
import org.conductoross.conductor.ai.mcp.server.model.MCPDiscoverResult;
import org.conductoross.conductor.ai.mcp.server.model.MCPError;
import org.conductoross.conductor.ai.mcp.server.model.MCPInitializeResult;
import org.conductoross.conductor.ai.mcp.server.model.MCPJsonRpcResponse;
import org.conductoross.conductor.ai.mcp.server.model.MCPServerInfo;
import org.conductoross.conductor.ai.mcp.server.model.MCPUnsupportedVersion;
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
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.HEADER_MISMATCH;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.INTERNAL_ERROR;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.INVALID_PARAMS;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.INVALID_REQUEST;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.METHOD_NOT_FOUND;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.PARSE_ERROR;
import static org.conductoross.conductor.ai.mcp.MCPJsonRpc.UNSUPPORTED_PROTOCOL_VERSION;

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
public final class MCPServerProtocol {

    private static final Logger log = LoggerFactory.getLogger(MCPServerProtocol.class);

    static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    private static final String CACHE_SCOPE = "cacheScope";

    /** Methods a legacy request may call; the tool methods use the shared handlers. */
    private static final Set<MCPMethod> LEGACY_TOOL_METHODS =
            EnumSet.of(MCPMethod.TOOLS_LIST, MCPMethod.TOOLS_CALL);

    /** Methods whose {@code Mcp-Name} header carries {@code params.taskId}. */
    private static final Set<MCPMethod> TASK_METHODS =
            EnumSet.of(MCPMethod.TASKS_GET, MCPMethod.TASKS_UPDATE, MCPMethod.TASKS_CANCEL);

    /** Every version served, newest first, as {@code server/discover} lists them. */
    private static final List<String> SUPPORTED_VERSIONS =
            MCPProtocolVersion.newestFirst().stream().map(MCPProtocolVersion::value).toList();

    /** The versions of the stateless request form, as an unsupported-version error lists them. */
    private static final List<String> STATELESS_VERSIONS =
            MCPProtocolVersion.stateless().stream().map(MCPProtocolVersion::value).toList();

    private static final MCPServerInfo SERVER_INFO =
            new MCPServerInfo(
                    MCPProtocolHeaders.IMPLEMENTATION_NAME,
                    MCPProtocolHeaders.IMPLEMENTATION_VERSION);

    /** The HTTP response for one request; {@code body} is {@code null} for HTTP 202. */
    public record MCPHttpResponse(int status, JsonNode body) {}

    /**
     * One validated request, handed to a method handler.
     *
     * @param params the request {@code params}, including {@code _meta}
     * @param declaresTasks whether {@code _meta} declares the Tasks extension
     * @param headers the request headers (case-insensitive keys)
     * @param version the protocol version of the request
     */
    public record MCPCall(
            JsonNode id,
            MCPMethod method,
            ObjectNode params,
            ObjectNode meta,
            boolean declaresTasks,
            Map<String, String> headers,
            MCPProtocolVersion version) {

        /** A call on the newest version. */
        public MCPCall(
                JsonNode id,
                MCPMethod method,
                ObjectNode params,
                ObjectNode meta,
                boolean declaresTasks,
                Map<String, String> headers) {
            this(id, method, params, meta, declaresTasks, headers, MCPProtocolVersion.latest());
        }
    }

    /**
     * Runs one MCP method. Returns the JSON-RPC {@code result}, or fails with {@link
     * MCPServerException} for a protocol error.
     */
    @FunctionalInterface
    public interface MethodHandler {
        Mono<JsonNode> handle(MCPCall call);
    }

    private final ObjectMapper mapper;
    private final Map<MCPMethod, MethodHandler> handlers;

    /**
     * @param handlers method handlers by JSON-RPC method; {@code server/discover} is built in
     */
    public MCPServerProtocol(ObjectMapper mapper, Map<MCPMethod, MethodHandler> handlers) {
        this.mapper = mapper;
        this.handlers = new EnumMap<>(MCPMethod.class);
        this.handlers.putAll(handlers);
        this.handlers.put(MCPMethod.SERVER_DISCOVER, call -> Mono.just(discover()));
    }

    /**
     * Handles one HTTP POST: the JSON-RPC envelope first, then the request form ({@code 2026-07-28}
     * or legacy).
     *
     * @param headers request headers; looked up case-insensitively by the caller's map
     * @param rawBody the request body as sent
     */
    public Mono<MCPHttpResponse> handle(Map<String, String> headers, String rawBody) {
        JsonNode body;
        try {
            body = rawBody == null || rawBody.isBlank() ? null : mapper.readTree(rawBody);
        } catch (JacksonException e) {
            return Mono.just(error(HTTP_BAD_REQUEST, null, PARSE_ERROR, "Parse error", null));
        }
        Optional<MCPHttpResponse> invalid = invalidEnvelope(body);
        if (invalid.isPresent()) {
            return Mono.just(invalid.get());
        }
        JsonNode id = body.get(MCPFields.JsonRpc.ID);
        String method = body.get(MCPFields.JsonRpc.METHOD).asString();
        if (id == null) {
            // A notification: nothing to answer. The server handles none today.
            return Mono.just(new MCPHttpResponse(HTTP_ACCEPTED, null));
        }
        JsonNode params = body.get(MCPFields.JsonRpc.PARAMS);
        return isLegacy(params, headers)
                ? handleLegacy(id, method, params, headers)
                : handleStateless(id, method, params, headers);
    }

    /**
     * The error for a body that is not a JSON-RPC request: not an object, {@code jsonrpc} not
     * {@code "2.0"}, no string {@code method}, or an {@code id} that is not a string or an integer.
     */
    private Optional<MCPHttpResponse> invalidEnvelope(JsonNode body) {
        if (body == null || !body.isObject()) {
            return Optional.of(
                    error(
                            HTTP_BAD_REQUEST,
                            null,
                            INVALID_REQUEST,
                            "Invalid Request: not a JSON object",
                            null));
        }
        JsonNode id = body.get(MCPFields.JsonRpc.ID);
        boolean validId = id == null || id.isString() || id.isIntegralNumber();
        JsonNode methodNode = body.get(MCPFields.JsonRpc.METHOD);
        if (!MCPJsonRpc.VERSION.equals(text(body.get(MCPFields.JsonRpc.JSONRPC)))
                || methodNode == null
                || !methodNode.isString()) {
            return Optional.of(
                    error(
                            HTTP_BAD_REQUEST,
                            // An id of the wrong type cannot be echoed: JSON-RPC answers null.
                            validId ? id : null,
                            INVALID_REQUEST,
                            "Invalid Request: 'jsonrpc' must be \"2.0\" and 'method' a string",
                            null));
        }
        if (!validId) {
            // MCP request ids are strings or integers, never null: a reply to "id": null cannot
            // be matched to its request, so the call is refused before anything starts.
            return Optional.of(
                    error(
                            HTTP_BAD_REQUEST,
                            null,
                            INVALID_REQUEST,
                            "Invalid Request: 'id' must be a string or an integer",
                            null));
        }
        return Optional.empty();
    }

    /**
     * Serves a {@code 2026-07-28} request: {@code _meta}, the protocol version, the headers, then
     * the method handler.
     */
    private Mono<MCPHttpResponse> handleStateless(
            JsonNode id, String method, JsonNode paramsNode, Map<String, String> headers) {
        Optional<MCPHttpResponse> invalid = invalidMeta(id, paramsNode);
        if (invalid.isPresent()) {
            return Mono.just(invalid.get());
        }
        ObjectNode params = (ObjectNode) paramsNode;
        ObjectNode meta = (ObjectNode) params.get(MCPFields.Session.META);
        String version = meta.get(MCPProtocolHeaders.META_PROTOCOL_VERSION).asString();
        invalid = invalidVersion(id, version, headers);
        if (invalid.isPresent()) {
            return Mono.just(invalid.get());
        }
        String mismatch = headerMismatch(headers, version, method, params);
        if (mismatch != null) {
            return Mono.just(error(HTTP_BAD_REQUEST, id, HEADER_MISMATCH, mismatch, null));
        }
        MCPMethod mcpMethod = MCPMethod.of(method);
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
                meta.get(MCPProtocolHeaders.META_CLIENT_CAPABILITIES)
                        .path(MCPFields.Session.EXTENSIONS)
                        .has(MCPProtocolHeaders.TASKS_EXTENSION);
        MCPCall call =
                new MCPCall(
                        id,
                        mcpMethod,
                        params,
                        meta,
                        declaresTasks,
                        headers,
                        MCPProtocolVersion.stateless(version).orElseThrow());
        // A handler that throws instead of returning an error signal fails the same way.
        Mono<JsonNode> result = Mono.defer(() -> handler.handle(call));
        return result.map(value -> success(id, withResultType(value)))
                .onErrorResume(e -> Mono.just(failure(call, e)));
    }

    /**
     * The error for {@code params} that is not an object, or a {@code _meta} without a string
     * protocol version or a client capabilities object.
     */
    private Optional<MCPHttpResponse> invalidMeta(JsonNode id, JsonNode params) {
        if (params != null && !params.isObject()) {
            return Optional.of(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_PARAMS,
                            "'params' must be an object",
                            null));
        }
        JsonNode meta = params == null ? null : params.get(MCPFields.Session.META);
        if (meta == null || !meta.isObject()) {
            return Optional.of(
                    error(HTTP_BAD_REQUEST, id, INVALID_PARAMS, "Missing params._meta", null));
        }
        JsonNode version = meta.get(MCPProtocolHeaders.META_PROTOCOL_VERSION);
        if (version == null || !version.isString()) {
            return Optional.of(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_PARAMS,
                            "Missing _meta." + MCPProtocolHeaders.META_PROTOCOL_VERSION,
                            null));
        }
        JsonNode capabilities = meta.get(MCPProtocolHeaders.META_CLIENT_CAPABILITIES);
        if (capabilities == null || !capabilities.isObject()) {
            return Optional.of(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            INVALID_PARAMS,
                            "Missing _meta." + MCPProtocolHeaders.META_CLIENT_CAPABILITIES,
                            null));
        }
        return Optional.empty();
    }

    /**
     * The error for a version header that contradicts {@code _meta} (checked first, even for an
     * unsupported version), or a version this request form does not accept.
     */
    private Optional<MCPHttpResponse> invalidVersion(
            JsonNode id, String version, Map<String, String> headers) {
        String versionHeader = headers.get(MCPProtocolHeaders.HEADER_PROTOCOL_VERSION);
        if (versionHeader != null && !version.equals(versionHeader)) {
            return Optional.of(
                    error(HTTP_BAD_REQUEST, id, HEADER_MISMATCH, versionMismatch(version), null));
        }
        if (MCPProtocolVersion.stateless(version).isEmpty()) {
            // Only the versions this request form accepts: the older ones are reached with
            // initialize, so listing them here would invite a retry that fails the same way.
            JsonNode data =
                    mapper.valueToTree(new MCPUnsupportedVersion(STATELESS_VERSIONS, version));
            return Optional.of(
                    error(
                            HTTP_BAD_REQUEST,
                            id,
                            UNSUPPORTED_PROTOCOL_VERSION,
                            "Unsupported protocol version: "
                                    + version
                                    + ". Older versions are served through initialize.",
                            data));
        }
        return Optional.empty();
    }

    /**
     * A request is legacy when {@code params._meta} has no protocol version and the {@code
     * MCP-Protocol-Version} header is not {@code 2026-07-28}. A {@code 2026-07-28} header with no
     * {@code _meta} stays on the {@code 2026-07-28} path, which rejects it with {@code -32602}.
     */
    private static boolean isLegacy(JsonNode params, Map<String, String> headers) {
        if (params != null
                && params.isObject()
                && params.path(MCPFields.Session.META)
                        .has(MCPProtocolHeaders.META_PROTOCOL_VERSION)) {
            return false;
        }
        return MCPProtocolVersion.stateless(headers.get(MCPProtocolHeaders.HEADER_PROTOCOL_VERSION))
                .isEmpty();
    }

    /**
     * Serves a client on {@code 2025-03-26}, {@code 2025-06-18} or {@code 2025-11-25}: no session,
     * no {@code _meta}, synchronous tool calls only, and no {@code resultType} in results.
     */
    private Mono<MCPHttpResponse> handleLegacy(
            JsonNode id, String method, JsonNode paramsNode, Map<String, String> headers) {
        String versionHeader = headers.get(MCPProtocolHeaders.HEADER_PROTOCOL_VERSION);
        MCPProtocolVersion version =
                versionHeader == null
                        // A 2025-03-26 client sends no MCP-Protocol-Version header.
                        ? MCPProtocolVersion.legacyWithoutVersionHeader()
                        : MCPProtocolVersion.of(versionHeader)
                                .filter(MCPProtocolVersion::isLegacy)
                                .orElse(null);
        if (version == null) {
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
        MCPMethod mcpMethod = MCPMethod.of(method);
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
        MCPCall call =
                new MCPCall(
                        id, mcpMethod, params, mapper.createObjectNode(), false, headers, version);
        // A handler that throws instead of returning an error signal fails the same way.
        Mono<JsonNode> result = Mono.defer(() -> handler.handle(call));
        return result.map(value -> success(id, forLegacy(value, version)))
                .onErrorResume(e -> Mono.just(legacyFailure(call, e)));
    }

    /** The {@code initialize} result: the requested version when supported, else the newest. */
    private JsonNode initialize(ObjectNode params) {
        String requested = text(params.get(MCPFields.Session.PROTOCOL_VERSION));
        MCPProtocolVersion version =
                MCPProtocolVersion.of(requested)
                        .filter(MCPProtocolVersion::isLegacy)
                        .orElse(MCPProtocolVersion.latestLegacy());
        return mapper.valueToTree(
                new MCPInitializeResult(version.value(), MCPCapabilities.toolsOnly(), SERVER_INFO));
    }

    /** A {@code 2026-07-28} result needs a {@code resultType}; {@code complete} by default. */
    private static JsonNode withResultType(JsonNode result) {
        if (result instanceof ObjectNode object && !object.has(MCPResultType.FIELD)) {
            object.put(MCPResultType.FIELD, MCPResultType.COMPLETE.value());
        }
        return result;
    }

    /**
     * A legacy result has no {@code 2026-07-28} fields: no result type, no caching hints. For a
     * version without {@code structuredContent} it also has no {@code structuredContent} and no
     * tool {@code outputSchema}.
     */
    private static JsonNode forLegacy(JsonNode result, MCPProtocolVersion version) {
        if (result instanceof ObjectNode object) {
            object.remove(MCPResultType.FIELD);
            object.remove(MCPFields.Task.TTL_MS);
            object.remove(CACHE_SCOPE);
            if (!version.hasStructuredContent()) {
                object.remove(MCPFields.ToolResult.STRUCTURED_CONTENT);
                if (object.get(MCPFields.Tool.TOOLS) instanceof ArrayNode tools) {
                    tools.forEach(
                            tool -> {
                                if (tool instanceof ObjectNode listed) {
                                    listed.remove(MCPFields.Tool.OUTPUT_SCHEMA);
                                }
                            });
                }
            }
        }
        return result;
    }

    /**
     * Adds the caching hints a {@code 2026-07-28} list or discovery result must carry. The tool
     * list changes whenever a definition is opted in or an agent is deployed, so clients are told
     * not to cache it ({@code ttlMs} 0); it is the same for every caller ({@code public}).
     */
    static ObjectNode withCachingHints(ObjectNode result) {
        result.put(MCPFields.Task.TTL_MS, 0);
        result.put(CACHE_SCOPE, "public");
        return result;
    }

    /** Legacy method errors are JSON-RPC errors with HTTP 200. */
    private MCPHttpResponse legacyFailure(MCPCall call, Throwable e) {
        MCPHttpResponse response = failure(call, e);
        return response.status() == HTTP_INTERNAL_ERROR
                ? response
                : new MCPHttpResponse(HTTP_OK, response.body());
    }

    /** Returns a message for the first header that does not match the body, or {@code null}. */
    private static String headerMismatch(
            Map<String, String> headers, String version, String method, ObjectNode params) {
        String versionHeader = headers.get(MCPProtocolHeaders.HEADER_PROTOCOL_VERSION);
        if (!version.equals(versionHeader)) {
            return versionMismatch(version);
        }
        if (!method.equals(headers.get(MCPProtocolHeaders.HEADER_METHOD))) {
            return MCPProtocolHeaders.HEADER_METHOD
                    + " header must equal the method '"
                    + method
                    + "'";
        }
        String expectedName = null;
        MCPMethod mcpMethod = MCPMethod.of(method);
        if (mcpMethod == MCPMethod.TOOLS_CALL) {
            expectedName = text(params.get(MCPFields.Tool.NAME));
        } else if (TASK_METHODS.contains(mcpMethod)) {
            expectedName = text(params.get(MCPFields.Task.TASK_ID));
        }
        // A missing body value is the method handler's invalid-params error, not a header one.
        if (expectedName != null
                && !expectedName.equals(
                        MCPProtocolHeaders.decodeHeaderValue(
                                headers.get(MCPProtocolHeaders.HEADER_NAME)))) {
            return MCPProtocolHeaders.HEADER_NAME + " header must equal '" + expectedName + "'";
        }
        return null;
    }

    private static String versionMismatch(String version) {
        return MCPProtocolHeaders.HEADER_PROTOCOL_VERSION
                + " header must equal the _meta protocol version '"
                + version
                + "'";
    }

    private JsonNode discover() {
        MCPDiscoverResult result =
                new MCPDiscoverResult(
                        SUPPORTED_VERSIONS,
                        MCPCapabilities.toolsAndExtension(MCPProtocolHeaders.TASKS_EXTENSION),
                        Map.of(META_SERVER_INFO, SERVER_INFO));
        return withCachingHints(mapper.valueToTree(result));
    }

    /**
     * The body of an error response sent before a request is read, such as a request that is too
     * large: the id is unknown, so it is {@code null}.
     */
    public JsonNode errorBody(int code, String message) {
        return mapper.valueToTree(
                MCPJsonRpcResponse.error(null, new MCPError(code, message, null)));
    }

    private MCPHttpResponse success(JsonNode id, JsonNode result) {
        return new MCPHttpResponse(
                HTTP_OK, mapper.valueToTree(MCPJsonRpcResponse.success(id, result)));
    }

    private MCPHttpResponse failure(MCPCall call, Throwable e) {
        if (e instanceof MCPServerException mcp) {
            return error(
                    mcp.getHttpStatus(), call.id(), mcp.getCode(), mcp.getMessage(), mcp.getData());
        }
        log.warn(
                "MCP server: {} failed (request id {}{})",
                call.method(),
                call.id(),
                target(call),
                e);
        return error(
                HTTP_INTERNAL_ERROR,
                call.id(),
                INTERNAL_ERROR,
                MCPJsonRpc.INTERNAL_ERROR_MESSAGE,
                null);
    }

    /**
     * The tool or task a call names, for logs: {@code ", tool <name>"}, {@code ", task <id>"}, or
     * empty.
     */
    private static String target(MCPCall call) {
        if (call.method() == MCPMethod.TOOLS_CALL) {
            String name = text(call.params().get(MCPFields.Tool.NAME));
            return name == null ? "" : ", tool " + name;
        }
        if (TASK_METHODS.contains(call.method())) {
            String taskId = text(call.params().get(MCPFields.Task.TASK_ID));
            return taskId == null ? "" : ", task " + taskId;
        }
        return "";
    }

    private MCPHttpResponse error(
            int status, JsonNode id, int code, String message, JsonNode data) {
        return new MCPHttpResponse(
                status,
                mapper.valueToTree(
                        MCPJsonRpcResponse.error(id, new MCPError(code, message, data))));
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }
}
