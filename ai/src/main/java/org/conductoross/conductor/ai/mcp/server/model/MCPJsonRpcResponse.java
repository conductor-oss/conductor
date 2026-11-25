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
package org.conductoross.conductor.ai.mcp.server.model;

import org.conductoross.conductor.ai.mcp.MCPFields;
import org.conductoross.conductor.ai.mcp.MCPJsonRpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.JsonNode;

/**
 * A JSON-RPC response: a {@code result} or an {@code error}. {@code id} is always written, as
 * {@code null} when the request's id is unknown or unusable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    MCPFields.JsonRpc.JSONRPC,
    MCPFields.JsonRpc.ID,
    MCPFields.JsonRpc.RESULT,
    MCPFields.JsonRpc.ERROR
})
public record MCPJsonRpcResponse(
        String jsonrpc,
        @JsonInclude(JsonInclude.Include.ALWAYS) JsonNode id,
        JsonNode result,
        MCPError error) {

    public static MCPJsonRpcResponse success(JsonNode id, JsonNode result) {
        return new MCPJsonRpcResponse(MCPJsonRpc.VERSION, id, result, null);
    }

    public static MCPJsonRpcResponse error(JsonNode id, MCPError error) {
        return new MCPJsonRpcResponse(MCPJsonRpc.VERSION, id, null, error);
    }
}
