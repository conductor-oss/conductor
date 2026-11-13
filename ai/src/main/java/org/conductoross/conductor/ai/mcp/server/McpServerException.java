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

import tools.jackson.databind.JsonNode;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INVALID_PARAMS;

/**
 * A JSON-RPC error that the MCP server returns for a request, with the HTTP status to send it with.
 * Method handlers raise it for protocol problems; tool failures are {@code isError} results, not
 * exceptions.
 */
public class McpServerException extends RuntimeException {

    private final int httpStatus;
    private final int code;
    private final transient JsonNode data;

    /**
     * @param httpStatus the HTTP status of the response
     * @param code the JSON-RPC error code
     * @param message the JSON-RPC error message, sent to the client
     * @param data the JSON-RPC error {@code data}, or {@code null}
     */
    public McpServerException(int httpStatus, int code, String message, JsonNode data) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.data = data;
    }

    /** Invalid params inside a valid request ({@code -32602}, HTTP 200). */
    public static McpServerException invalidParams(String message) {
        return new McpServerException(HTTP_OK, INVALID_PARAMS, message, null);
    }

    public static McpServerException unknownTool(String name) {
        return invalidParams("Unknown tool: " + name);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }

    public JsonNode getData() {
        return data;
    }
}
