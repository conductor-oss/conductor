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

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
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
 * <p>Supports remote (HTTP/HTTPS) MCP servers.
 */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class MCPService {

    private static final Logger log = LoggerFactory.getLogger(MCPService.class);
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final JsonTextParser jsonTextParser = new JsonTextParser(objectMapper);

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
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream");

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

                // Get response body and content type
                String responseBody = response.body().string();
                String contentType = response.header("Content-Type", "application/json");

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
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream");

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

                // Get response body and content type
                String responseBody = response.body().string();
                String contentType = response.header("Content-Type", "application/json");

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

    /** Closes an HTTP MCP client. */
    private void closeClient(McpSyncClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Error closing MCP client: {}", e.getMessage());
        }
    }

    /** Transport type enum. */
    private enum TransportType {
        STREAMABLE_HTTP,
        SSE
    }
}
