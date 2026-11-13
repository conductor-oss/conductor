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
package org.conductoross.conductor.ai.agentspan.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.ai.mcp.server.McpServerException;
import org.conductoross.conductor.ai.mcp.server.McpTool;
import org.conductoross.conductor.ai.mcp.server.McpToolSource;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.exception.NotFoundException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Deployed AgentSpan agents as MCP tools. As in the A2A agent server, every deployed agent is
 * exposed once the MCP server is on; there is no opt-in. Gated on {@code
 * conductor.mcp.server.enabled=true} and {@code agentspan.embedded=true}.
 */
@Component
@ConditionalOnProperty(
        name = {"conductor.mcp.server.enabled", "agentspan.embedded"},
        havingValue = "true")
public class AgentToolSource implements McpToolSource {

    private final AgentService agentService;
    private final JsonNode promptSchema;

    public AgentToolSource(AgentService agentService) {
        this.agentService = agentService;
        ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("prompt").put("type", "string");
        schema.putArray("required").add("prompt");
        this.promptSchema = schema;
    }

    @Override
    public List<McpTool> listTools() {
        return agentService.listAgents().stream().map(this::toTool).toList();
    }

    @Override
    public Optional<McpTool> findTool(String name) {
        return agentService.listAgents().stream()
                .filter(agent -> agent.getName().equals(name))
                .findFirst()
                .map(this::toTool);
    }

    /** A deployed agent of this name; no description lookup, as on every {@code tasks/*} poll. */
    @Override
    public boolean exposes(String name) {
        return agentService.listAgents().stream().anyMatch(agent -> agent.getName().equals(name));
    }

    /**
     * The metadata the agent's author set. The compiled workflow definition keeps it in its stored
     * agent definition ({@code agentDef.metadata}), not in its own metadata. An agent without a
     * stored agent definition gets the defaults.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> metadata(String name) {
        Map<String, Object> agentDef;
        try {
            agentDef = agentService.getAgentDef(name, null);
        } catch (NotFoundException e) {
            return null;
        }
        return agentDef != null && agentDef.get("metadata") instanceof Map<?, ?> own
                ? (Map<String, Object>) own
                : null;
    }

    /** Starts the agent with {@code arguments.prompt}, which must be a non-blank string. */
    @Override
    public String start(String name, ObjectNode arguments) {
        JsonNode prompt = arguments.get("prompt");
        if (prompt == null || !prompt.isString() || prompt.asString().isBlank()) {
            throw McpServerException.invalidParams(
                    "Tool '" + name + "' needs a non-empty string argument 'prompt'");
        }
        AgentStartRequest request = new AgentStartRequest();
        request.setName(name);
        request.setPrompt(prompt.asString());
        return agentService.start(request).getExecutionId();
    }

    private McpTool toTool(AgentSummary agent) {
        String description = description(agent);
        return new McpTool(
                agent.getName(),
                description == null || description.isBlank()
                        ? McpTool.defaultDescription(agent.getName())
                        : description,
                promptSchema,
                McpTool.Kind.AGENT);
    }

    /**
     * The description the agent's author wrote. For a deployed agent that is the {@code
     * description} of its stored agent definition: the compiler sets the workflow definition's own
     * description to its own text ("Agent workflow for ..."), which would name the kind. A
     * definition tagged as an agent by hand has no stored agent definition, and its description is
     * the author's.
     */
    private String description(AgentSummary agent) {
        Map<String, Object> agentDef;
        try {
            agentDef = agentService.getAgentDef(agent.getName(), agent.getVersion());
        } catch (NotFoundException e) {
            return agent.getDescription();
        }
        return agentDef != null && agentDef.get("description") instanceof String text ? text : null;
    }
}
