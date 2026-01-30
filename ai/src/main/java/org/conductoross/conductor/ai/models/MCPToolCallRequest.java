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
package org.conductoross.conductor.ai.models;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Request model for calling a tool on an MCP server.
 *
 * <p>All fields except mcpServer, toolName, and headers are treated as tool arguments and passed to
 * the MCP tool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class MCPToolCallRequest extends LLMWorkerInput {

    /**
     * MCP server URL or stdio command.
     *
     * <p>Examples: - HTTP/SSE: "http://localhost:3000/sse" - HTTPS: "https://api.example.com/mcp" -
     * stdio: "stdio://npx -y @modelcontextprotocol/server-everything"
     */
    private String mcpServer;

    /** Name of the tool to call on the MCP server. */
    private String method;

    /** HTTP headers for remote MCP servers (optional). Only applicable for HTTP/HTTPS transport. */
    private Map<String, String> headers;

    /**
     * Additional arguments to pass to the MCP tool. These are collected via @JsonAnySetter and
     * passed as tool arguments.
     */
    @JsonIgnore @Builder.Default private Map<String, Object> args = new HashMap<>();

    /** Captures any additional properties as tool arguments. */
    @JsonAnySetter
    public void setArgs(String key, Object value) {
        // Skip known fields
        if ("mcpServer".equals(key) || "toolName".equals(key) || "headers".equals(key)) {
            return;
        }
        args.put(key, value);
    }

    /** Exposes tool arguments for serialization. */
    @JsonAnyGetter
    public Map<String, Object> getArgs() {
        return args;
    }
}
