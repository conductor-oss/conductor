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

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.http.ExternalDataLimits;
import org.conductoross.conductor.ai.http.RetryInterceptor;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import io.modelcontextprotocol.spec.McpSchema;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Service for interacting with MCP (Model Context Protocol) servers.
 *
 * <p>Supports remote (HTTP/HTTPS) MCP servers.
 */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class MCPService {

    private static final Logger log = LoggerFactory.getLogger(MCPService.class);
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final JsonTextParser jsonTextParser = new JsonTextParser(objectMapper);
    private final OkHttpClient httpClient;
    private final OkHttpClient taskGetClient;
    private final OkHttpClient taskCancelClient;

    /** Upper bound on one {@code tasks/get} call, so a stalled server cannot hold a worker. */
    static final Duration TASK_GET_TIMEOUT = Duration.ofSeconds(30);

    /** Upper bound on one best-effort {@code tasks/cancel} call. */
    static final Duration TASK_CANCEL_TIMEOUT = Duration.ofSeconds(5);

    // Spring needs the marker: with a second constructor it cannot pick one on its own.
    @Autowired
    public MCPService(OkHttpClient conductorAiHttpClient) {
        this(conductorAiHttpClient, TASK_GET_TIMEOUT, TASK_CANCEL_TIMEOUT);
    }

    MCPService(
            OkHttpClient conductorAiHttpClient,
            Duration taskGetTimeout,
            Duration taskCancelTimeout) {
        // Redirects are followed explicitly below so credentials can be withheld across origins.
        this.httpClient =
                conductorAiHttpClient
                        .newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
        // tasks/get and tasks/cancel are short status requests: unlike tool calls, they get a hard
        // per-call bound instead of the shared client's long read timeout.
        this.taskGetClient = withoutRetries(httpClient).callTimeout(taskGetTimeout).build();
        this.taskCancelClient = withoutRetries(httpClient).callTimeout(taskCancelTimeout).build();
    }

    /**
     * A builder from {@code client} without its {@link RetryInterceptor}. The polling logic in
     * {@code McpTasksCall} owns retries for {@code tasks/get} (with its failure limit) and {@code
     * tasks/cancel} is best effort, so a second, hidden retry layer would only stretch their time
     * limits. {@code tools/call} keeps the interceptor, as legacy calls do.
     */
    private static OkHttpClient.Builder withoutRetries(OkHttpClient client) {
        OkHttpClient.Builder builder = client.newBuilder();
        builder.interceptors().removeIf(RetryInterceptor.class::isInstance);
        return builder;
    }

    /**
     * Lists all tools available from an MCP server.
     *
     * @param serverUrl MCP server URL (http:// or https://)
     * @param headers HTTP headers for the request
     * @return List of available tools
     */
    public List<McpSchema.Tool> listTools(String serverUrl, Map<String, String> headers) {
        return listToolsHttp(serverUrl, headers);
    }

    /**
     * Calls a tool on an MCP server.
     *
     * @param serverUrl MCP server URL (http:// or https://)
     * @param toolName Name of the tool to call
     * @param arguments Tool arguments
     * @param headers HTTP headers for the request
     * @return Tool call result as Map (preserves parsed JSON fields)
     */
    public Map<String, Object> callTool(
            String serverUrl,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers) {
        return callToolHttp(serverUrl, toolName, arguments, headers);
    }

    /** Upper bound on the {@code tools/list} pages read to find one tool. */
    static final int MAX_TOOLS_LIST_PAGES = 100;

    /**
     * Finds a tool's definition with {@code tools/list} in the MCP {@code 2026-07-28} form,
     * following {@code nextCursor} until the tool is found or the list ends. Used only for calls
     * that select {@code mcpOptions.protocolVersion}; {@link #listTools} is unchanged.
     *
     * <p>The definition is returned as raw JSON, so that schema extensions such as {@code
     * x-mcp-header} are kept.
     *
     * @return the tool definition, or empty if no page lists the tool
     * @throws McpRequestException on an HTTP or JSON-RPC error
     */
    public Optional<JsonNode> listToolsPaged(
            String serverUrl, String toolName, Map<String, String> headers) {
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > MAX_TOOLS_LIST_PAGES) {
                throw new RuntimeException(
                        "MCP server "
                                + serverUrl
                                + " returned more than "
                                + MAX_TOOLS_LIST_PAGES
                                + " tools/list pages without listing tool '"
                                + toolName
                                + "'");
            }
            ObjectNode params = objectMapper.createObjectNode();
            if (cursor != null) {
                params.put("cursor", cursor);
            }
            JsonNode result =
                    sendProtocolRequest(
                            serverUrl,
                            "tools/list",
                            null,
                            params,
                            false,
                            false,
                            headers,
                            Map.of(),
                            httpClient);
            ExternalDataLimits.validateStructure(objectMapper.convertValue(result, Object.class));

            JsonNode tools = result.get("tools");
            if (tools == null || !tools.isArray()) {
                throw new RuntimeException(
                        "Invalid tools/list response from "
                                + serverUrl
                                + ": 'tools' field is missing or not an array");
            }
            for (JsonNode tool : tools) {
                if (toolName.equals(tool.path("name").asString(null))) {
                    return Optional.of(tool);
                }
            }

            JsonNode next = result.get("nextCursor");
            cursor =
                    next != null && next.isString() && !next.asString().isEmpty()
                            ? next.asString()
                            : null;
            if (cursor != null && !seenCursors.add(cursor)) {
                throw new RuntimeException(
                        "MCP server " + serverUrl + " repeated tools/list cursor '" + cursor + "'");
            }
        } while (cursor != null);
        return Optional.empty();
    }

    /**
     * Sends a {@code tools/call} in the MCP {@code 2026-07-28} form, for a call that selects {@code
     * mcpOptions.protocolVersion}, with the Tasks extension declared. {@link #callTool} is
     * unchanged.
     *
     * @param paramHeaders the {@code Mcp-Param-*} headers for the tool's {@code x-mcp-header}
     *     parameters
     * @return an immediate result, or the remote task that the server created
     * @throws McpRequestException on a non-2xx status or a JSON-RPC {@code error}
     * @throws TransportException when the request could not be sent or the response not read
     */
    public ToolCallOutcome callToolTasks(
            String serverUrl,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers,
            Map<String, String> paramHeaders) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments));
        JsonNode result =
                sendProtocolRequest(
                        serverUrl,
                        "tools/call",
                        toolName,
                        params,
                        true,
                        true,
                        headers,
                        paramHeaders,
                        httpClient);
        if (RESULT_TYPE_TASK.equals(resultType(result))) {
            ExternalDataLimits.validateStructure(objectMapper.convertValue(result, Object.class));
            return new ToolCallOutcome(
                    null, parseTask(result, null, "tools/call", serverUrl, false));
        }
        return new ToolCallOutcome(toResultMap(result), null);
    }

    /**
     * Gets the status of a remote task with {@code tasks/get}. For a {@code completed} task, the
     * embedded tool result is returned as raw JSON; the caller maps it with {@link #mapTaskResult},
     * so that a result that cannot be mapped is not a failure of the request itself.
     *
     * @throws McpRequestException on a non-2xx status or a JSON-RPC {@code error}
     * @throws TransportException when the request could not be sent or the response not read
     */
    public McpTask getTask(String serverUrl, String taskId, Map<String, String> headers) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("taskId", taskId);
        JsonNode result =
                sendProtocolRequest(
                        serverUrl,
                        "tasks/get",
                        taskId,
                        params,
                        true,
                        false,
                        headers,
                        Map.of(),
                        taskGetClient);
        validateTaskStructure(result);
        return parseTask(result, taskId, "tasks/get", serverUrl, true);
    }

    /**
     * Asks the server to cancel a remote task with {@code tasks/cancel}. The server acknowledges
     * with an empty result; cancellation itself is cooperative.
     *
     * @throws McpRequestException on a non-2xx status or a JSON-RPC {@code error}
     * @throws TransportException when the request could not be sent or the response not read
     */
    public void cancelTask(String serverUrl, String taskId, Map<String, String> headers) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("taskId", taskId);
        sendProtocolRequest(
                serverUrl,
                "tasks/cancel",
                taskId,
                params,
                true,
                false,
                headers,
                Map.of(),
                taskCancelClient);
    }

    /**
     * The outcome of a {@code tools/call} in the {@code 2026-07-28} form: exactly one of {@code
     * result} (an immediate {@code CallToolResult}, mapped as on the synchronous path) and {@code
     * task} (a {@code CreateTaskResult}) is set.
     */
    public record ToolCallOutcome(Map<String, Object> result, McpTask task) {
        public boolean isTask() {
            return task != null;
        }
    }

    /**
     * A remote task as reported by {@code CreateTaskResult} or {@code tasks/get}.
     *
     * @param pollIntervalMs the server's suggested poll interval, or {@code null} if it gave none
     * @param result for a {@code completed} {@code tasks/get} response, the raw tool result;
     *     otherwise {@code null}. It is mapped with {@link #mapTaskResult} by the caller, so that a
     *     result that cannot be mapped is not a failure of the {@code tasks/get} request itself
     * @param error for a {@code failed} task, the JSON-RPC {@code error} object; otherwise {@code
     *     null}
     */
    public record McpTask(
            String taskId,
            String status,
            String statusMessage,
            Long pollIntervalMs,
            JsonNode result,
            JsonNode error) {}

    /**
     * A request in the MCP {@code 2026-07-28} form that failed with an I/O error while it was sent
     * or its response was read (for example, a refused connection or a timeout). The cause is the
     * original failure. Deterministic failures, such as the payload size limit or a refused
     * redirect, are not transport failures and keep their own exception.
     */
    public static class TransportException extends RuntimeException {
        TransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final String RESULT_TYPE_COMPLETE = "complete";
    private static final String TASK_STATUS_COMPLETED = "completed";
    private static final String RESULT_TYPE_TASK = "task";
    private static final String RESULT_TYPE_INPUT_REQUIRED = "input_required";

    /** The {@code resultType} of a result; an absent value means {@code complete}. */
    private static String resultType(JsonNode result) {
        JsonNode type = result.get("resultType");
        return type == null || type.isNull() ? RESULT_TYPE_COMPLETE : type.asString();
    }

    /**
     * Applies the {@code resultType} rules: an absent value or {@code complete} is a final result,
     * {@code task} is accepted only where a {@code CreateTaskResult} may be returned, and any other
     * value, including {@code input_required}, is invalid.
     */
    private static void checkResultType(
            JsonNode result, String method, String serverUrl, boolean allowTaskResult) {
        JsonNode typeNode = result.get("resultType");
        if (typeNode != null && !typeNode.isNull() && !typeNode.isString()) {
            throw new McpCallException(
                    "MCP server "
                            + serverUrl
                            + " returned a non-text resultType to "
                            + method
                            + ": "
                            + typeNode);
        }
        String type = resultType(result);
        if (RESULT_TYPE_COMPLETE.equals(type)
                || (allowTaskResult && RESULT_TYPE_TASK.equals(type))) {
            return;
        }
        if (RESULT_TYPE_INPUT_REQUIRED.equals(type)) {
            throw new McpCallException(
                    "MCP server "
                            + serverUrl
                            + " returned resultType 'input_required' to "
                            + method
                            + ": server input requests are not supported");
        }
        throw new McpCallException(
                "MCP server "
                        + serverUrl
                        + " returned unsupported resultType '"
                        + type
                        + "' to "
                        + method);
    }

    /**
     * Maps the {@code result} of a completed task as the synchronous path maps a {@code
     * CallToolResult}. The node is modified in place: a {@code parsed} field is added to each text
     * content item that holds JSON.
     *
     * @throws RuntimeException if the result cannot be mapped, for example a non-string {@code
     *     text} content item
     */
    public Map<String, Object> mapTaskResult(JsonNode result) {
        return toResultMap(result);
    }

    /** Maps a {@code CallToolResult} as the synchronous path does: adds {@code parsed} fields. */
    private Map<String, Object> toResultMap(JsonNode result) {
        processResultJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.convertValue(result, Map.class);
        ExternalDataLimits.validateStructure(map);
        return map;
    }

    /**
     * @param readResult whether {@code result} may be read: {@code false} for a {@code
     *     CreateTaskResult}, which the Tasks extension defines without {@code result} for every
     *     status
     */
    /**
     * Checks the nesting depth of a {@code tasks/get} result. The embedded tool {@code result} is
     * checked on its own, from its own root, so that it gets the same depth limit as a tool result
     * returned at once.
     */
    private void validateTaskStructure(JsonNode result) {
        JsonNode toolResult = result.get("result");
        if (toolResult == null || !(result instanceof ObjectNode object)) {
            ExternalDataLimits.validateStructure(objectMapper.convertValue(result, Object.class));
            return;
        }
        ObjectNode fields = object.deepCopy();
        fields.remove("result");
        ExternalDataLimits.validateStructure(objectMapper.convertValue(fields, Object.class));
        ExternalDataLimits.validateStructure(objectMapper.convertValue(toolResult, Object.class));
    }

    private McpTask parseTask(
            JsonNode node, String requestedTaskId, String method, String url, boolean readResult) {
        JsonNode idNode = node.get("taskId");
        String taskId =
                idNode != null && idNode.isString() && !idNode.asString().isBlank()
                        ? idNode.asString()
                        : requestedTaskId;
        if (taskId == null) {
            throw new McpCallException(
                    "Invalid " + method + " response from " + url + ": missing 'taskId'");
        }
        JsonNode statusNode = node.get("status");
        if (statusNode == null || !statusNode.isString()) {
            throw new McpCallException(
                    "Invalid " + method + " response from " + url + ": missing 'status'");
        }
        JsonNode messageNode = node.get("statusMessage");
        JsonNode intervalNode = node.get("pollIntervalMs");
        // A hint only: a value that is not a whole number that fits a long (1e30, 1500.5, a
        // larger integer) is ignored, as an absent one is. 5000.0 is used as 5000.
        Long pollIntervalMs =
                intervalNode != null && intervalNode.isNumber() && intervalNode.canConvertToLong()
                        ? intervalNode.asLong()
                        : null;
        // The Tasks extension defines result only on a completed tasks/get response
        // (CompletedTask in the DetailedTask union); CreateTaskResult has none, even when its
        // seed status is completed. Anywhere else it is an extra field and is ignored, so a
        // stray result can never fail a call.
        JsonNode resultNode =
                readResult && TASK_STATUS_COMPLETED.equals(statusNode.asString())
                        ? node.get("result")
                        : null;
        JsonNode result = resultNode != null && resultNode.isObject() ? resultNode : null;
        JsonNode errorNode = node.get("error");
        return new McpTask(
                taskId,
                statusNode.asString(),
                messageNode != null && messageNode.isString() ? messageNode.asString() : null,
                pollIntervalMs,
                result,
                errorNode != null && errorNode.isObject() ? errorNode : null);
    }

    /**
     * Sends one JSON-RPC request in the MCP {@code 2026-07-28} form and returns its {@code result}.
     * User headers are added first, then the protocol headers and {@code extraHeaders}, which
     * replace user headers with the same name.
     *
     * @param mcpName the {@code Mcp-Name} header value, or {@code null} to omit it
     * @param declareTasks whether {@code _meta} declares the Tasks extension
     * @param allowTaskResult whether {@code resultType: "task"} is a valid result
     * @throws McpRequestException on a non-2xx status or a JSON-RPC {@code error}
     * @throws TransportException when the request could not be sent or the response not read
     */
    private JsonNode sendProtocolRequest(
            String serverUrl,
            String method,
            String mcpName,
            ObjectNode params,
            boolean declareTasks,
            boolean allowTaskResult,
            Map<String, String> userHeaders,
            Map<String, String> extraHeaders,
            OkHttpClient client) {
        ResponsePayload response;
        try {
            params.set("_meta", McpProtocolHeaders.meta(objectMapper, declareTasks));
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", method);
            request.put("id", 1);
            request.set("params", params);

            Request.Builder requestBuilder =
                    new Request.Builder()
                            .url(serverUrl)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(request),
                                            MediaType.get("application/json")))
                            .header("Content-Type", "application/json");
            addHeaders(requestBuilder, withoutReservedHeaders(userHeaders));
            McpProtocolHeaders.apply(requestBuilder, method, mcpName);
            extraHeaders.forEach(requestBuilder::header);

            response = execute(requestBuilder.build(), client);
        } catch (RuntimeException e) {
            // Deterministic failures (invalid request, payload limit, refused or broken redirect)
            // keep their own type and message; they are not transport failures and must not be
            // retried as if they were.
            throw e;
        } catch (Exception e) {
            // I/O failures from OkHttp: refused connection, timeout, dropped connection.
            throw new TransportException(
                    "MCP " + method + " to " + serverUrl + " failed: " + e.getMessage(), e);
        }

        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw McpRequestException.fromHttpError(
                    method, response.statusCode, response.body, objectMapper);
        }

        JsonNode responseJson =
                response.contentType != null && response.contentType.contains("text/event-stream")
                        ? parseSseResponse(response.body)
                        : objectMapper.readTree(response.body);
        if (responseJson.hasNonNull("error")) {
            throw McpRequestException.fromRpcError(
                    method, response.statusCode, responseJson.get("error"));
        }
        if (!responseJson.has("result")) {
            throw new RuntimeException(
                    "Invalid JSON-RPC response to MCP "
                            + method
                            + " from "
                            + serverUrl
                            + ": missing 'result' field");
        }
        JsonNode result = responseJson.get("result");
        if (!result.isObject()) {
            throw new RuntimeException(
                    "Invalid JSON-RPC response to MCP "
                            + method
                            + " from "
                            + serverUrl
                            + ": 'result' is not an object");
        }
        checkResultType(result, method, serverUrl, allowTaskResult);
        return result;
    }

    /** Lists tools from an HTTP/HTTPS MCP server. */
    private List<McpSchema.Tool> listToolsHttp(String serverUrl, Map<String, String> headers) {
        // Use direct JSON-RPC since many MCP servers don't support full SDK
        // initialization
        log.debug("Listing tools from MCP server via direct JSON-RPC: {}", serverUrl);
        return listToolsDirectHttp(serverUrl, headers);
    }

    /**
     * Lists tools using direct JSON-RPC HTTP call (fallback for servers that don't support SDK).
     */
    private List<McpSchema.Tool> listToolsDirectHttp(
            String serverUrl, Map<String, String> headers) {
        try {
            log.debug("Making direct JSON-RPC call to list tools from: {}", serverUrl);

            // Build JSON-RPC request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", "tools/list");
            request.put("id", 1);

            Request.Builder requestBuilder =
                    new Request.Builder()
                            .url(serverUrl)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(request),
                                            MediaType.get("application/json")))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream");

            // Add custom headers
            addHeaders(requestBuilder, headers);

            ResponsePayload response = execute(requestBuilder.build());
            // Check response status
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw new RuntimeException(
                        String.format(
                                "HTTP %d error from MCP server: %s",
                                response.statusCode, response.body));
            }

            // Get response body and content type
            String responseBody = response.body;
            String contentType = response.contentType;

            // Parse response based on content type
            JsonNode responseJson;
            if (contentType != null && contentType.contains("text/event-stream")) {
                // Parse SSE format
                responseJson = parseSseResponse(responseBody);
            } else {
                // Parse as JSON directly
                responseJson = objectMapper.readTree(responseBody);
            }

            if (responseJson.has("error")) {
                throw new RuntimeException(
                        "JSON-RPC error: " + responseJson.get("error").toString());
            }

            if (!responseJson.has("result")) {
                throw new RuntimeException("Invalid JSON-RPC response: missing 'result' field");
            }

            JsonNode result = responseJson.get("result");
            JsonNode toolsNode = result.get("tools");

            if (toolsNode == null || !toolsNode.isArray()) {
                throw new RuntimeException(
                        "Invalid response: 'tools' field is missing or not an array");
            }
            ExternalDataLimits.validateStructure(objectMapper.convertValue(result, Object.class));

            // Convert to McpSchema.Tool list
            // Use Jackson to deserialize tools directly (same pattern as stdio implementation)
            List<McpSchema.Tool> tools =
                    objectMapper.convertValue(
                            toolsNode,
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, McpSchema.Tool.class));

            log.debug(
                    "Successfully listed {} tools via direct JSON-RPC from {}",
                    tools.size(),
                    serverUrl);
            return tools;

        } catch (Exception e) {
            log.error(
                    "Failed to list tools via direct JSON-RPC from {}: {}",
                    serverUrl,
                    e.getMessage());
            throw new RuntimeException(
                    "Failed to list MCP tools from " + serverUrl + ": " + e.getMessage(), e);
        }
    }

    /** Calls a tool on an HTTP/HTTPS MCP server. */
    private Map<String, Object> callToolHttp(
            String serverUrl,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers) {

        // Use direct JSON-RPC since many MCP servers don't support full SDK
        // initialization
        log.debug("Calling tool '{}' on MCP server via direct JSON-RPC: {}", toolName, serverUrl);
        return callToolDirectHttp(serverUrl, toolName, arguments, headers);
    }

    /**
     * Calls a tool using direct JSON-RPC HTTP call (fallback for servers that don't support SDK).
     */
    private Map<String, Object> callToolDirectHttp(
            String serverUrl,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers) {
        try {
            log.debug("Making direct JSON-RPC call to tool '{}' on: {}", toolName, serverUrl);

            // Build JSON-RPC request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", "tools/call");
            request.put("id", 1);

            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", objectMapper.valueToTree(arguments));
            request.set("params", params);

            Request.Builder requestBuilder =
                    new Request.Builder()
                            .url(serverUrl)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(request),
                                            MediaType.get("application/json")))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream");

            // Add custom headers
            addHeaders(requestBuilder, headers);

            ResponsePayload response = execute(requestBuilder.build());
            // Check response status
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw new RuntimeException(
                        String.format(
                                "HTTP %d error from MCP server: %s",
                                response.statusCode, response.body));
            }

            // Get response body and content type
            String responseBody = response.body;
            String contentType = response.contentType;

            // Parse response based on content type
            JsonNode responseJson;
            if (contentType != null && contentType.contains("text/event-stream")) {
                // Parse SSE format
                responseJson = parseSseResponse(responseBody);
            } else {
                // Parse as JSON directly
                responseJson = objectMapper.readTree(responseBody);
            }

            if (responseJson.has("error")) {
                throw new RuntimeException(responseJson.get("error").toString());
            }

            if (!responseJson.has("result")) {
                throw new RuntimeException("Invalid JSON-RPC response: missing 'result' field");
            }

            JsonNode resultNode = responseJson.get("result");

            // Process the result JSON to parse text content as JSON where applicable
            processResultJson(resultNode);

            // Return as Map to preserve parsed field
            Map<String, Object> result = objectMapper.convertValue(resultNode, Map.class);
            ExternalDataLimits.validateStructure(result);

            log.debug(
                    "Successfully called tool '{}' via direct JSON-RPC on {}", toolName, serverUrl);
            return result;

        } catch (Exception e) {
            log.error(
                    "Failed to call tool '{}' via direct JSON-RPC on {}: {}",
                    toolName,
                    serverUrl,
                    e.getMessage());
            throw new RuntimeException(
                    "Failed to call MCP tool '"
                            + toolName
                            + "' on "
                            + serverUrl
                            + ": "
                            + e.getMessage(),
                    e);
        }
    }

    /**
     * Processes a CallToolResult JSON node to parse JSON strings in text content.
     *
     * <p>Modifies the JSON response before deserialization to convert JSON strings to objects.
     */
    private void processResultJson(JsonNode resultNode) {
        if (resultNode == null || !resultNode.has("content")) {
            return;
        }

        JsonNode contentArray = resultNode.get("content");
        if (!contentArray.isArray()) {
            return;
        }

        for (JsonNode contentItem : contentArray) {
            if (contentItem.isObject()
                    && "text".equals(contentItem.path("type").asString())
                    && contentItem.has("text")) {

                String textValue = contentItem.get("text").asString();
                Object parsed = jsonTextParser.parseTextOrJsonAsObject(textValue);
                // If it parsed as JSON object/array, add a 'parsed' field
                try {
                    JsonNode parsedNode = objectMapper.valueToTree(parsed);
                    ((ObjectNode) contentItem).set("parsed", parsedNode);
                } catch (Exception e) {
                    log.warn("Failed to add parsed JSON field: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Parses an SSE (Server-Sent Events) response to extract JSON data.
     *
     * <p>SSE format:
     *
     * <pre>
     * event: message
     * data: {"jsonrpc": "2.0", ...}
     * </pre>
     *
     * @param sseBody the raw SSE response body
     * @return parsed JSON node from the data field
     */
    private JsonNode parseSseResponse(String sseBody) {
        log.debug("Parsing SSE response: {}", sseBody);

        // Find all "data:" lines and concatenate their content
        StringBuilder jsonData = new StringBuilder();
        String[] lines = sseBody.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                // Skip empty data or "[DONE]" markers
                if (!data.isEmpty() && !data.equals("[DONE]")) {
                    jsonData.append(data);
                }
            }
        }

        if (jsonData.length() == 0) {
            throw new RuntimeException("No data found in SSE response: " + sseBody);
        }

        try {
            return objectMapper.readTree(jsonData.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SSE data as JSON: " + jsonData, e);
        }
    }

    /** Executes one MCP request and follows redirects while protecting sensitive headers. */
    private ResponsePayload execute(Request initialRequest) throws Exception {
        return execute(initialRequest, httpClient);
    }

    /**
     * Executes one MCP request with {@code client}, following redirects. When the client has a
     * {@code callTimeout} (the bounded {@code tasks/get} and {@code tasks/cancel} clients), that
     * limit applies to the whole request, across redirects, not to each redirect. The shared client
     * has none, so the legacy path is unchanged.
     */
    private ResponsePayload execute(Request initialRequest, OkHttpClient client) throws Exception {
        long budgetMillis = client.callTimeoutMillis();
        long deadlineNanos =
                budgetMillis > 0
                        ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis)
                        : 0;
        Request request = initialRequest;
        for (int redirects = 0; redirects <= 5; redirects++) {
            Call call = client.newCall(request);
            if (budgetMillis > 0) {
                long remainingMillis =
                        TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
                if (remainingMillis <= 0) {
                    throw new InterruptedIOException(
                            "MCP request exceeded its time limit of "
                                    + budgetMillis
                                    + " ms across redirects");
                }
                call.timeout().timeout(remainingMillis, TimeUnit.MILLISECONDS);
            }
            try (Response response = call.execute()) {
                if (!response.isRedirect()) {
                    return new ResponsePayload(
                            response.code(),
                            response.header("Content-Type", "application/json"),
                            readBoundedBody(response.body()));
                }
                String location = response.header("Location");
                if (location == null || request.url().resolve(location) == null) {
                    throw new RuntimeException(
                            "MCP server returned a redirect without a valid Location");
                }
                String target = request.url().resolve(location).toString();
                if (hasSensitiveHeaders(request)
                        && !isSameOrigin(request.url().toString(), target)) {
                    throw new RuntimeException(
                            "Refusing to forward credentials across an MCP redirect");
                }
                request = request.newBuilder().url(target).build();
            }
        }
        throw new RuntimeException("MCP server exceeded the redirect limit");
    }

    /**
     * Removes user headers that Conductor owns on a request in the {@code 2026-07-28} form: {@code
     * Mcp-Name} and every {@code Mcp-Param-*} (ignoring case). Conductor sets them from the request
     * itself, and a stale user value would be sent where the protocol requires the header to be
     * absent. Other protocol headers are replaced, not removed, because Conductor always sets them.
     */
    private static Map<String, String> withoutReservedHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }
        Map<String, String> kept = new LinkedHashMap<>();
        headers.forEach(
                (name, value) -> {
                    String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
                    if (!lower.equals("mcp-name") && !lower.startsWith("mcp-param-")) {
                        kept.put(name, value);
                    }
                });
        return kept;
    }

    private void addHeaders(Request.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach(
                (name, value) -> {
                    if (name == null
                            || value == null
                            || name.indexOf('\r') >= 0
                            || name.indexOf('\n') >= 0
                            || value.indexOf('\r') >= 0
                            || value.indexOf('\n') >= 0) {
                        throw new IllegalArgumentException(
                                "MCP headers must not contain CR or LF characters");
                    }
                    builder.header(name, value);
                });
    }

    private boolean hasSensitiveHeaders(Request request) {
        return request.header("Authorization") != null
                || request.header("Cookie") != null
                || request.header("Proxy-Authorization") != null;
    }

    private boolean isSameOrigin(String firstUrl, String secondUrl) {
        okhttp3.HttpUrl first = okhttp3.HttpUrl.parse(firstUrl);
        okhttp3.HttpUrl second = okhttp3.HttpUrl.parse(secondUrl);
        return first != null
                && second != null
                && first.scheme().equalsIgnoreCase(second.scheme())
                && first.host().equalsIgnoreCase(second.host())
                && first.port() == second.port();
    }

    private String readBoundedBody(ResponseBody body) throws Exception {
        if (body == null) {
            return "";
        }
        if (body.contentLength() > ExternalDataLimits.MAX_PAYLOAD_BYTES) {
            throw new RuntimeException("MCP response exceeds the 10 MiB payload limit");
        }
        try (body) {
            okio.Buffer buffer = new okio.Buffer();
            long total = 0;
            long read;
            while ((read = body.source().read(buffer, 8192)) != -1) {
                total += read;
                if (total > ExternalDataLimits.MAX_PAYLOAD_BYTES) {
                    buffer.clear();
                    throw new RuntimeException("MCP response exceeds the 10 MiB payload limit");
                }
            }
            return buffer.readUtf8();
        }
    }

    private record ResponsePayload(int statusCode, String contentType, String body) {}

    /** Transport type enum. */
    private enum TransportType {
        STREAMABLE_HTTP,
        SSE
    }
}
