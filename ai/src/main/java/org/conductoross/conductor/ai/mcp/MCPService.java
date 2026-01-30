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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for interacting with MCP (Model Context Protocol) servers.
 *
 * <p>Supports both remote (HTTP/HTTPS) and local (stdio) MCP servers.
 */
@Component
public class MCPService {

    private static final Logger log = LoggerFactory.getLogger(MCPService.class);
    private static final String STDIO_PREFIX = "stdio://";
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final JsonTextParser jsonTextParser = new JsonTextParser(objectMapper);

    // Cache for stdio processes to avoid recreating them for each request
    private final Map<String, StdioMCPClient> stdioClients = new ConcurrentHashMap<>();

    /**
     * Lists all tools available from an MCP server.
     *
     * @param serverUrl MCP server URL (http://, https://, or stdio://)
     * @param headers HTTP headers for remote servers (ignored for stdio)
     * @return List of available tools
     */
    public List<McpSchema.Tool> listTools(String serverUrl, Map<String, String> headers) {
        if (isStdioServer(serverUrl)) {
            return listToolsStdio(serverUrl);
        } else {
            return listToolsHttp(serverUrl, headers);
        }
    }

    /**
     * Calls a tool on an MCP server.
     *
     * @param serverUrl MCP server URL (http://, https://, or stdio://)
     * @param toolName Name of the tool to call
     * @param arguments Tool arguments
     * @param headers HTTP headers for remote servers (ignored for stdio)
     * @return Tool call result as Map (preserves parsed JSON fields)
     */
    public Map<String, Object> callTool(
            String serverUrl,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers) {

        if (isStdioServer(serverUrl)) {
            McpSchema.CallToolResult result = callToolStdio(serverUrl, toolName, arguments);
            return objectMapper.convertValue(result, Map.class);
        } else {
            return callToolHttp(serverUrl, toolName, arguments, headers);
        }
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

            // Make HTTP POST request with OkHttp
            OkHttpClient httpClient =
                    new OkHttpClient.Builder()
                            .connectTimeout(Duration.ofSeconds(30))
                            .readTimeout(Duration.ofSeconds(30))
                            .build();

            Request.Builder requestBuilder =
                    new Request.Builder()
                            .url(serverUrl)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(request),
                                            MediaType.get("application/json")))
                            .header("Content-Type", "application/json");

            // Add custom headers
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(requestBuilder::header);
            }

            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                // Check response status
                if (!response.isSuccessful()) {
                    throw new RuntimeException(
                            String.format(
                                    "HTTP %d error from MCP server: %s",
                                    response.code(),
                                    response.body() != null ? response.body().string() : ""));
                }

                // Parse JSON-RPC response
                String responseBody = response.body().string();
                JsonNode responseJson = objectMapper.readTree(responseBody);

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

                // Convert to McpSchema.Tool list
                // Use Jackson to deserialize tools directly (same pattern as stdio
                // implementation)
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
            }
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

            // Make HTTP POST request with OkHttp
            OkHttpClient httpClient =
                    new OkHttpClient.Builder()
                            .connectTimeout(Duration.ofSeconds(30))
                            .readTimeout(Duration.ofSeconds(30))
                            .build();

            Request.Builder requestBuilder =
                    new Request.Builder()
                            .url(serverUrl)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(request),
                                            MediaType.get("application/json")))
                            .header("Content-Type", "application/json");

            // Add custom headers
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(requestBuilder::header);
            }

            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                // Check response status
                if (!response.isSuccessful()) {
                    throw new RuntimeException(
                            String.format(
                                    "HTTP %d error from MCP server: %s",
                                    response.code(),
                                    response.body() != null ? response.body().string() : ""));
                }

                // Parse JSON-RPC response
                String responseBody = response.body().string();
                JsonNode responseJson = objectMapper.readTree(responseBody);

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

                log.debug(
                        "Successfully called tool '{}' via direct JSON-RPC on {}",
                        toolName,
                        serverUrl);
                return result;
            }
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
                    && "text".equals(contentItem.path("type").asText())
                    && contentItem.has("text")) {

                String textValue = contentItem.get("text").asText();
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

    /** Checks if server URL is a stdio command. */
    private boolean isStdioServer(String serverUrl) {
        return serverUrl != null && serverUrl.startsWith(STDIO_PREFIX);
    }

    /** Lists tools from a stdio MCP server. */
    private List<McpSchema.Tool> listToolsStdio(String serverUrl) {
        StdioMCPClient client = getOrCreateStdioClient(serverUrl);
        return client.listTools();
    }

    /** Calls a tool on a stdio MCP server. */
    private McpSchema.CallToolResult callToolStdio(
            String serverUrl, String toolName, Map<String, Object> arguments) {
        StdioMCPClient client = getOrCreateStdioClient(serverUrl);
        return client.callTool(toolName, arguments);
    }

    /** Gets or creates a stdio MCP client for the given command. */
    private StdioMCPClient getOrCreateStdioClient(String serverUrl) {
        return stdioClients.computeIfAbsent(
                serverUrl,
                url -> {
                    String command = url.substring(STDIO_PREFIX.length()).trim();
                    log.debug("Creating stdio MCP client for command: {}", command);
                    return new StdioMCPClient(command, objectMapper);
                });
    }

    /** Closes an HTTP MCP client. */
    private void closeClient(McpSyncClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Error closing MCP client: {}", e.getMessage());
        }
    }

    /** Stdio MCP client that communicates via stdin/stdout. */
    private static class StdioMCPClient {
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private final ObjectMapper objectMapper;
        private final AtomicInteger requestId = new AtomicInteger(1);

        StdioMCPClient(String command, ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            try {
                // Parse command into parts
                String[] commandParts = command.split("\\s+");

                ProcessBuilder pb = new ProcessBuilder(commandParts);
                pb.redirectErrorStream(true);

                this.process = pb.start();
                this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                // Initialize the connection
                initialize();

                log.debug("Stdio MCP client started successfully for command: {}", command);
            } catch (IOException e) {
                log.error("Failed to start stdio MCP server: {}", e.getMessage(), e);
                throw new RuntimeException(
                        "Failed to start stdio MCP server: " + e.getMessage(), e);
            }
        }

        private void initialize() throws IOException {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId.getAndIncrement());
            request.put("method", "initialize");
            request.set(
                    "params",
                    objectMapper
                            .createObjectNode()
                            .put("protocolVersion", "2024-11-05")
                            .put(
                                    "clientInfo",
                                    objectMapper
                                            .createObjectNode()
                                            .put("name", "conductor")
                                            .put("version", "1.0.0")));

            sendRequest(request);
            JsonNode response = readResponse();

            if (response.has("error")) {
                throw new IOException("Failed to initialize: " + response.get("error"));
            }
        }

        List<McpSchema.Tool> listTools() {
            try {
                ObjectNode request = objectMapper.createObjectNode();
                request.put("jsonrpc", "2.0");
                request.put("id", requestId.getAndIncrement());
                request.put("method", "tools/list");

                sendRequest(request);
                JsonNode response = readResponse();

                if (response.has("error")) {
                    throw new RuntimeException("MCP error: " + response.get("error"));
                }

                JsonNode tools = response.path("result").path("tools");
                return objectMapper.convertValue(
                        tools,
                        objectMapper
                                .getTypeFactory()
                                .constructCollectionType(List.class, McpSchema.Tool.class));
            } catch (IOException e) {
                throw new RuntimeException("Failed to list tools: " + e.getMessage(), e);
            }
        }

        McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            try {
                ObjectNode request = objectMapper.createObjectNode();
                request.put("jsonrpc", "2.0");
                request.put("id", requestId.getAndIncrement());
                request.put("method", "tools/call");

                ObjectNode params = objectMapper.createObjectNode();
                params.put("name", toolName);
                params.set("arguments", objectMapper.valueToTree(arguments));
                request.set("params", params);

                sendRequest(request);
                JsonNode response = readResponse();

                if (response.has("error")) {
                    throw new RuntimeException("MCP error: " + response.get("error"));
                }

                return objectMapper.convertValue(
                        response.get("result"), McpSchema.CallToolResult.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to call tool: " + e.getMessage(), e);
            }
        }

        private void sendRequest(ObjectNode request) throws IOException {
            String json = objectMapper.writeValueAsString(request);
            log.debug("Sending MCP request: {}", json);
            writer.write(json);
            writer.newLine();
            writer.flush();
        }

        private JsonNode readResponse() throws IOException {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("MCP server closed connection");
            }
            log.debug("Received MCP response: {}", line);
            return objectMapper.readTree(line);
        }

        void close() {
            try {
                writer.close();
                reader.close();
                process.destroy();
                process.waitFor(5, TimeUnit.SECONDS);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (Exception e) {
                log.warn("Error closing stdio MCP client: {}", e.getMessage());
            }
        }
    }

    /** Transport type enum. */
    private enum TransportType {
        STREAMABLE_HTTP,
        SSE
    }
}
