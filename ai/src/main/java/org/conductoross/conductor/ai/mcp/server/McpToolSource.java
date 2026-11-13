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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.node.ObjectNode;

/**
 * A source of MCP tools. Sources read their definitions on every call, so a workflow that is opted
 * in or deployed is listed at once, on every node, with no sync step.
 */
public interface McpToolSource {

    /** The tools this source exposes now. */
    List<McpTool> listTools();

    /** The exposed tool with this name, or empty. */
    Optional<McpTool> findTool(String name);

    /**
     * Whether executions of this name belong to an exposed tool, so that {@code tasks/*} may read
     * and cancel them. A source may expose a name that it does not offer for new calls, for example
     * a workflow whose schema became unusable while executions were running.
     */
    default boolean exposes(String name) {
        return findTool(name).isPresent();
    }

    /**
     * The metadata that holds the tool's MCP settings, such as {@code mcp.syncWaitSeconds}, or
     * {@code null} for the defaults.
     */
    default Map<String, Object> metadata(String name) {
        return null;
    }

    /**
     * Starts one execution of the named tool.
     *
     * @param arguments the {@code tools/call} arguments (an object, possibly empty)
     * @return the execution id (a workflow id)
     * @throws McpServerException for invalid arguments ({@code -32602})
     */
    String start(String name, ObjectNode arguments);
}
