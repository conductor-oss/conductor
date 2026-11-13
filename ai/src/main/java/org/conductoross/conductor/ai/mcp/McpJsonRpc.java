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

/**
 * JSON-RPC 2.0 as MCP uses it: the protocol version and the error codes. Shared by the MCP client
 * and the MCP server.
 */
public final class McpJsonRpc {

    /** The {@code jsonrpc} member of every request and response. */
    public static final String VERSION = "2.0";

    // Error codes, sorted by number.
    // Defined by MCP:
    public static final int RESOURCE_NOT_FOUND = -32002;
    public static final int HEADER_MISMATCH = -32020;
    public static final int MISSING_REQUIRED_CLIENT_CAPABILITY = -32021;
    public static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;
    public static final int URL_ELICITATION_REQUIRED = -32042;
    // Defined by JSON-RPC 2.0:
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int PARSE_ERROR = -32700;

    private McpJsonRpc() {}
}
