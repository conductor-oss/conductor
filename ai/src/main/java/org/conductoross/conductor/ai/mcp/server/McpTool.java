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

/**
 * One tool the MCP server lists in {@code tools/list}.
 *
 * @param inputSchema a JSON Schema object
 * @param kind what runs when the tool is called
 */
public record McpTool(String name, String description, JsonNode inputSchema, Kind kind) {

    /**
     * The description of a tool whose definition has none. It does not say whether the tool is a
     * workflow or an agent: an MCP client sees tools only, and {@link Kind} stays internal.
     */
    public static String defaultDescription(String name) {
        return "Conductor tool '" + name + "'";
    }

    /** How the server starts the tool. Internal: never sent to MCP clients. */
    public enum Kind {
        /** An opted-in Conductor workflow, run with the call's {@code arguments} as input. */
        WORKFLOW,
        /** A deployed AgentSpan agent, run with {@code arguments.prompt}. */
        AGENT
    }
}
