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

import java.net.HttpURLConnection;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_NOT_ACCEPTABLE;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNSUPPORTED_TYPE;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.HEADER_MISMATCH;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INTERNAL_ERROR;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INVALID_PARAMS;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.INVALID_REQUEST;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.METHOD_NOT_FOUND;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.PARSE_ERROR;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.RESOURCE_NOT_FOUND;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.UNSUPPORTED_PROTOCOL_VERSION;
import static org.conductoross.conductor.ai.mcp.McpJsonRpc.URL_ELICITATION_REQUIRED;

/**
 * An HTTP or JSON-RPC error on a request in the MCP {@code 2026-07-28} form (calls that select
 * {@code mcpOptions.protocolVersion}). It keeps the HTTP status and the parsed JSON-RPC error so
 * the caller can choose the task outcome.
 */
public class McpRequestException extends RuntimeException {

    /** Too Many Requests (RFC 6585); {@link HttpURLConnection} has no constant for it. */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** The last status of the {@code 5xx} (server error) class. */
    private static final int HTTP_LAST_SERVER_ERROR = 599;

    private static final Set<Integer> PROTOCOL_ERROR_CODES =
            Set.of(
                    HEADER_MISMATCH,
                    MISSING_REQUIRED_CLIENT_CAPABILITY,
                    UNSUPPORTED_PROTOCOL_VERSION);
    private static final int MAX_BODY_IN_MESSAGE = 500;

    private final McpMethod method;
    private final int httpStatus;
    private final Integer code;
    private final String rpcMessage;
    private final JsonNode data;
    private final String body;

    McpRequestException(
            McpMethod method,
            int httpStatus,
            Integer code,
            String rpcMessage,
            JsonNode data,
            String body) {
        super(message(method, httpStatus, code, rpcMessage, data, body));
        this.method = method;
        this.httpStatus = httpStatus;
        this.code = code;
        this.rpcMessage = rpcMessage;
        this.data = data;
        this.body = body;
    }

    /**
     * Builds the exception for a response with a non-2xx status. The body is parsed as a JSON-RPC
     * response when possible; an empty or non-JSON body gives an exception without a code.
     */
    static McpRequestException fromHttpError(
            McpMethod method, int httpStatus, String body, ObjectMapper objectMapper) {
        JsonNode error = null;
        try {
            if (body != null && !body.isBlank()) {
                error = objectMapper.readTree(body).get("error");
            }
        } catch (Exception ignored) {
            // Not JSON: keep the HTTP status and the body only.
        }
        if (error == null || !error.isObject()) {
            return new McpRequestException(method, httpStatus, null, null, null, body);
        }
        return withError(method, httpStatus, error, body);
    }

    /** Builds the exception for a JSON-RPC {@code error} object. */
    static McpRequestException fromRpcError(McpMethod method, int httpStatus, JsonNode error) {
        return withError(method, httpStatus, error, error.toString());
    }

    private static McpRequestException withError(
            McpMethod method, int httpStatus, JsonNode error, String body) {
        McpRpcError rpcError = McpRpcError.from(error);
        return new McpRequestException(
                method, httpStatus, rpcError.code(), rpcError.message(), rpcError.data(), body);
    }

    /**
     * True if this is an error that a {@code 2026-07-28} server sends: code {@code -32020}, {@code
     * -32021} or {@code -32022}; {@code -32601} with HTTP {@code 404}; or {@code -32602} with HTTP
     * {@code 400}.
     */
    public boolean isRecognizedProtocolError() {
        if (code == null) {
            return false;
        }
        if (PROTOCOL_ERROR_CODES.contains(code)) {
            return true;
        }
        return (code == METHOD_NOT_FOUND && httpStatus == HTTP_NOT_FOUND)
                || (code == INVALID_PARAMS && httpStatus == HTTP_BAD_REQUEST);
    }

    /** The JSON-RPC 2.0 codes with a meaning the specification defines. */
    private static final Set<Integer> JSON_RPC_DEFINED_CODES =
            Set.of(PARSE_ERROR, INVALID_REQUEST, METHOD_NOT_FOUND, INVALID_PARAMS, INTERNAL_ERROR);

    /** The codes MCP defines. */
    private static final Set<Integer> MCP_DEFINED_CODES =
            Set.of(
                    RESOURCE_NOT_FOUND,
                    HEADER_MISMATCH,
                    MISSING_REQUIRED_CLIENT_CAPABILITY,
                    UNSUPPORTED_PROTOCOL_VERSION,
                    URL_ELICITATION_REQUIRED);

    /** True if the server reports an internal error: JSON-RPC {@code -32603}. */
    public boolean isInternalError() {
        return code != null && code == INTERNAL_ERROR;
    }

    /**
     * True if the code has a meaning that JSON-RPC 2.0 or MCP defines. Every other code has no
     * defined meaning: {@code -32000} to {@code -32099} are left to the server, and codes outside
     * the reserved range to the application. Servers use such codes both for server failures and
     * for rejected requests (the MCP TypeScript SDK sends {@code -32000} with HTTP 400, 403, 406,
     * 409, 413, 415 and 500, and {@code -32001} with 404), so only the HTTP status can tell them
     * apart.
     */
    public boolean hasDefinedMeaning() {
        return code != null
                && (JSON_RPC_DEFINED_CODES.contains(code) || MCP_DEFINED_CODES.contains(code));
    }

    /**
     * True if the schema-lookup {@code tools/list} or the {@code tools/call} was rejected with a
     * status that signals a request the server does not understand ({@code 400}, {@code 404},
     * {@code 405}, {@code 406} or {@code 415}) and no recognized {@code 2026-07-28} error, which
     * means the server may not accept this request form. Both requests apply because the schema
     * lookup is the first request of a call that selects {@code mcpOptions.protocolVersion}, so an
     * older server rejects it before {@code tools/call} is sent. {@code tasks/get} and {@code
     * tasks/cancel} are excluded: they follow a created task, so the server already accepted the
     * form. Other {@code 4xx} statuses, such as {@code 401}, {@code 403} or {@code 429}, are about
     * credentials or limits, not the request form.
     */
    public boolean isOlderServerRejection() {
        return (method == McpMethod.TOOLS_LIST || method == McpMethod.TOOLS_CALL)
                && REQUEST_FORM_REJECTION_STATUSES.contains(httpStatus)
                && !isRecognizedProtocolError();
    }

    /** HTTP statuses that a server uses for a request whose form it does not accept. */
    private static final Set<Integer> REQUEST_FORM_REJECTION_STATUSES =
            Set.of(
                    HTTP_BAD_REQUEST,
                    HTTP_NOT_FOUND,
                    HTTP_BAD_METHOD,
                    HTTP_NOT_ACCEPTABLE,
                    HTTP_UNSUPPORTED_TYPE);

    /**
     * True if the HTTP status rejects the credentials: {@code 401} or {@code 403}. Looks at the
     * HTTP status only, so check {@link #hasDefinedMeaning()} first: a JSON-RPC code with a defined
     * meaning decides on its own, whatever the status.
     */
    public boolean isAuthRejection() {
        return AUTH_REJECTION_STATUSES.contains(httpStatus);
    }

    /**
     * True if the HTTP status reports a condition that can pass: {@code 5xx}, {@code 408} or {@code
     * 429}. Looks at the HTTP status only, so check {@link #hasDefinedMeaning()} first.
     */
    public boolean isTransientHttpError() {
        return (httpStatus >= HTTP_INTERNAL_ERROR && httpStatus <= HTTP_LAST_SERVER_ERROR)
                || TRANSIENT_STATUSES.contains(httpStatus);
    }

    /** True if the HTTP status is a success: the {@code 2xx} class. */
    public static boolean isSuccessStatus(int status) {
        return status >= HTTP_OK && status < HTTP_MULT_CHOICE;
    }

    private static final Set<Integer> AUTH_REJECTION_STATUSES =
            Set.of(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN);

    /** Request timeout and too many requests; every {@code 5xx} is transient as well. */
    private static final Set<Integer> TRANSIENT_STATUSES =
            Set.of(HTTP_CLIENT_TIMEOUT, HTTP_TOO_MANY_REQUESTS);

    public McpMethod getMethod() {
        return method;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** The JSON-RPC error code, or {@code null} if the response had none. */
    public Integer getCode() {
        return code;
    }

    /** The JSON-RPC error message, or {@code null}. */
    public String getRpcMessage() {
        return rpcMessage;
    }

    /** The JSON-RPC error {@code data}, or {@code null}. */
    public JsonNode getData() {
        return data;
    }

    /**
     * The failure detail in the words of the legacy {@code tools/call} path, so that a call with
     * {@code mcpOptions.protocolVersion} can fail with the same reason as a legacy call: {@code
     * HTTP <status> error from MCP server: <body>} for a non-2xx response, otherwise the JSON-RPC
     * {@code error} object.
     */
    public String synchronousDetail() {
        if (!isSuccessStatus(httpStatus)) {
            return String.format("HTTP %d error from MCP server: %s", httpStatus, body);
        }
        return body;
    }

    private static String message(
            McpMethod method,
            int httpStatus,
            Integer code,
            String rpcMessage,
            JsonNode data,
            String body) {
        StringBuilder sb = new StringBuilder("MCP ").append(method).append(" failed");
        if (!isSuccessStatus(httpStatus)) {
            sb.append(" with HTTP ").append(httpStatus);
        }
        if (code != null) {
            sb.append(": JSON-RPC error ").append(code);
            if (rpcMessage != null) {
                sb.append(" ").append(rpcMessage);
            }
            if (data != null && !data.isNull()) {
                sb.append(" ").append(data);
            }
        } else if (body != null && !body.isBlank()) {
            sb.append(": ")
                    .append(
                            body.length() > MAX_BODY_IN_MESSAGE
                                    ? body.substring(0, MAX_BODY_IN_MESSAGE) + "..."
                                    : body);
        }
        return sb.toString();
    }
}
