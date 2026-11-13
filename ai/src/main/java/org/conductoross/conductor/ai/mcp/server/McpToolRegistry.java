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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import tools.jackson.databind.node.ObjectNode;

/**
 * Merges the tool sources into one namespace, sorted by name.
 *
 * <p>Agents and workflows already share one namespace: a deployed agent is a workflow definition
 * with an agent marker, and definition names are unique. The workflow source skips agent
 * definitions, so two sources should not return the same name; if one ever does, it is listed once,
 * as the agent, because an agent is started with its prompt.
 */
public class McpToolRegistry {

    /**
     * The MCP tool name rule (*Tools › Tool Names*): 1 to 128 characters, ASCII letters, digits,
     * {@code _}, {@code -} and {@code .}. Conductor names can hold other characters (name
     * validation is off by default); such a definition is not offered, so that one name cannot make
     * a client reject the list.
     */
    private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    private final List<McpToolSource> sources;

    public McpToolRegistry(List<McpToolSource> sources) {
        this.sources = List.copyOf(sources);
    }

    /** Every exposed tool once, sorted by name (a stable order for a later cursor). */
    public List<McpTool> listTools() {
        Map<String, McpTool> byName = new HashMap<>();
        for (McpToolSource source : sources) {
            for (McpTool tool : source.listTools()) {
                if (isToolName(tool.name())) {
                    byName.merge(tool.name(), tool, McpToolRegistry::preferAgent);
                }
            }
        }
        return byName.values().stream().sorted(Comparator.comparing(McpTool::name)).toList();
    }

    /** Whether any source exposes executions of this name (see {@link McpToolSource#exposes}). */
    public boolean exposes(String name) {
        return isToolName(name) && sources.stream().anyMatch(source -> source.exposes(name));
    }

    public Optional<McpTool> findTool(String name) {
        return resolve(name).map(Resolved::tool);
    }

    /**
     * The exposed tool with this name and the source that provides it, found in one pass. An agent
     * wins a name clash, as in {@link #listTools()}.
     */
    public Optional<Resolved> resolve(String name) {
        if (!isToolName(name)) {
            return Optional.empty();
        }
        Resolved found = null;
        for (McpToolSource source : sources) {
            Optional<McpTool> tool = source.findTool(name);
            if (tool.isPresent()
                    && (found == null || preferAgent(found.tool(), tool.get()) == tool.get())) {
                found = new Resolved(tool.get(), source);
            }
        }
        return Optional.ofNullable(found);
    }

    static boolean isToolName(String name) {
        return name != null && TOOL_NAME.matcher(name).matches();
    }

    /** A tool and the source that provides it. */
    public record Resolved(McpTool tool, McpToolSource source) {

        /** The tool's MCP settings, such as {@code mcp.syncWaitSeconds}, or {@code null}. */
        public Map<String, Object> metadata() {
            return source.metadata(tool.name());
        }

        /** Starts one execution of the tool. */
        public String start(ObjectNode arguments) {
            return source.start(tool.name(), arguments);
        }
    }

    private static McpTool preferAgent(McpTool a, McpTool b) {
        return b.kind() == McpTool.Kind.AGENT ? b : a;
    }
}
