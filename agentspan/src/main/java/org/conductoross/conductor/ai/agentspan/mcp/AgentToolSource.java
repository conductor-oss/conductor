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

import org.conductoross.conductor.ai.agentspan.AgentIntroduction;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.ai.agentspan.runtime.util.WorkflowClassifiers;
import org.conductoross.conductor.ai.mcp.server.MCPTool;
import org.conductoross.conductor.ai.mcp.server.MCPToolArgumentsException;
import org.conductoross.conductor.ai.mcp.server.MCPToolSource;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
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
public class AgentToolSource implements MCPToolSource {

    /** Tells the LLM what to pass: the agent's only argument. */
    static final String PROMPT_DESCRIPTION = "The request for the agent, in plain language.";

    private final AgentService agentService;
    private final JsonNode promptSchema;

    public AgentToolSource(AgentService agentService) {
        this.agentService = agentService;
        ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties")
                .putObject("prompt")
                .put("type", "string")
                .put("description", PROMPT_DESCRIPTION);
        schema.putArray("required").add("prompt");
        this.promptSchema = schema;
    }

    @Override
    public List<MCPTool> listTools() {
        return agentService.listAgents().stream()
                .map(
                        agent ->
                                toTool(
                                        agent.getName(),
                                        introduction(agent.getName(), agent.getVersion()),
                                        agent.getDescription()))
                .toList();
    }

    /** Reads only this name's latest definition: it runs on every {@code tools/call}. */
    @Override
    public Optional<MCPTool> findTool(String name) {
        return latestAgent(name)
                .map(def -> toTool(def.getName(), AgentIntroduction.of(def), def.getDescription()));
    }

    /**
     * A deployed agent of this name. Reads only this name's latest definition, with no description
     * lookup, as it runs on every {@code tasks/*} poll.
     */
    @Override
    public boolean exposes(String name) {
        return latestAgent(name).isPresent();
    }

    /** The latest definition of {@code name} when it is an agent, as {@code listAgents} selects. */
    private Optional<WorkflowDef> latestAgent(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        WorkflowDef def;
        try {
            def = agentService.getAgentDefinition(name, null);
        } catch (NotFoundException e) {
            return Optional.empty();
        }
        return def != null && WorkflowClassifiers.isAgent(def.getMetadata())
                ? Optional.of(def)
                : Optional.empty();
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
            // An input error the caller can fix: it becomes an error result, as a schema
            // rejection does, not a protocol error.
            throw new MCPToolArgumentsException(
                    "Tool '" + name + "' needs a non-empty string argument 'prompt'");
        }
        AgentStartRequest request = new AgentStartRequest();
        request.setName(name);
        request.setPrompt(prompt.asString());
        return agentService.start(request).getExecutionId();
    }

    /** The agent's own introduction, from that version's definition; null when there is none. */
    private String introduction(String name, int version) {
        try {
            return AgentIntroduction.of(agentService.getAgentDefinition(name, version));
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * The tool for an agent. Its description is the agent's own {@code introduction}, as the A2A
     * agent server shows it, so both servers describe an agent the same way; without one, the
     * agent's workflow definition description.
     */
    private MCPTool toTool(String name, String introduction, String workflowDescription) {
        String description = introduction != null ? introduction : workflowDescription;
        return new MCPTool(
                name,
                description == null || description.isBlank()
                        ? MCPTool.defaultDescription(name)
                        : description,
                promptSchema,
                null,
                MCPTool.Kind.AGENT);
    }
}
