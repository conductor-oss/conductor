/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.normalizer;

import java.util.*;
import java.util.LinkedHashMap;

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Normalizes LangGraph rawConfig into an AgentConfig.
 *
 * <p>Three serialization paths:
 *
 * <ul>
 *   <li><b>Full extraction</b> — rawConfig has {@code model} and {@code tools} with {@code
 *       _worker_ref} markers (produced by {@code agentspan.agents.langchain.create_agent}).
 *       Normalizes identically to OpenAI: AI_MODEL task + SIMPLE tasks per tool.
 *   <li><b>Graph-structure</b> — rawConfig has {@code _graph} with nodes, edges, and
 *       conditional_edges. Each node becomes a SIMPLE task worker, edges define the workflow
 *       structure. Model noted for observability.
 *   <li><b>Passthrough</b> — rawConfig has {@code _worker_name} (custom StateGraph). The entire
 *       graph runs in a single SIMPLE task.
 * </ul>
 */
@Component
public class LangGraphNormalizer implements AgentConfigNormalizer {

    private static final Logger log = LoggerFactory.getLogger(LangGraphNormalizer.class);
    private static final String DEFAULT_NAME = "langgraph_agent";

    @Override
    public String frameworkId() {
        return "langgraph";
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentConfig normalize(Map<String, Object> raw) {
        String name = getString(raw, "name", DEFAULT_NAME);
        log.info("Normalizing LangGraph agent: {}", name);

        // Full extraction path: model + tools with _worker_ref
        if (raw.containsKey("model")
                && !raw.containsKey("_worker_name")
                && !raw.containsKey("_graph")) {
            return normalizeFullExtraction(name, raw);
        }

        // Graph-structure path: nodes + edges workflow
        if (raw.containsKey("_graph")) {
            return normalizeGraphStructure(name, raw);
        }

        // Passthrough path: custom StateGraph running in a single worker
        return normalizePassthrough(name, raw);
    }

    private AgentConfig normalizeFullExtraction(String name, Map<String, Object> raw) {
        log.info("LangGraph agent '{}' using full extraction (server-side LLM)", name);

        AgentConfig config = new AgentConfig();
        config.setName(name);
        config.setModel(getString(raw, "model", "openai/gpt-4o-mini"));
        config.setInstructions(raw.get("instructions"));

        if (raw.containsKey("temperature")) {
            Object t = raw.get("temperature");
            if (t instanceof Number) config.setTemperature(((Number) t).doubleValue());
        }
        if (raw.containsKey("max_tokens")) {
            Object mt = raw.get("max_tokens");
            if (mt instanceof Number) config.setMaxTokens(((Number) mt).intValue());
        }

        List<Map<String, Object>> rawTools = getList(raw, "tools");
        if (rawTools != null) {
            List<ToolConfig> tools = new ArrayList<>();
            for (Map<String, Object> t : rawTools) {
                // Agent-as-tool: compile child agent as SUB_WORKFLOW
                if ("AgentTool".equals(getString(t, "_type", ""))) {
                    ToolConfig agentTool = normalizeAgentTool(t);
                    if (agentTool != null) tools.add(agentTool);
                    continue;
                }
                if (t.containsKey("_worker_ref")) {
                    tools.add(
                            ToolConfig.builder()
                                    .name(getString(t, "_worker_ref", "unknown_tool"))
                                    .description(getString(t, "description", ""))
                                    .inputSchema((Map<String, Object>) t.get("parameters"))
                                    .toolType("worker")
                                    .build());
                }
            }
            if (!tools.isEmpty()) {
                config.setTools(tools);
            }
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private AgentConfig normalizeGraphStructure(String name, Map<String, Object> raw) {
        log.info("LangGraph agent '{}' using graph-structure (node/edge workflow)", name);

        Map<String, Object> graph = getMap(raw, "_graph");
        if (graph == null) {
            log.warn(
                    "LangGraph agent '{}' has _graph key but no graph data, falling back to passthrough",
                    name);
            return normalizePassthrough(name, raw);
        }

        AgentConfig config = new AgentConfig();
        config.setName(name);

        // Model for observability (actual LLM calls happen inside node workers)
        String model = getString(raw, "model", null);
        if (model != null) {
            config.setModel(model);
        }

        // Create worker tool for each node
        Map<String, AgentConfig> subgraphConfigs = null;
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        if (nodes != null) {
            List<ToolConfig> tools = new ArrayList<>();
            for (Map<String, Object> node : nodes) {
                // Human node: Conductor HUMAN system task, no workers needed
                boolean isHumanNode = Boolean.TRUE.equals(node.get("_human_node"));
                if (isHumanNode) {
                    // No workers to register — HUMAN is a Conductor system task
                    continue;
                }

                boolean isLlmNode = Boolean.TRUE.equals(node.get("_llm_node"));
                boolean isSubgraphNode = Boolean.TRUE.equals(node.get("_subgraph_node"));

                if (isSubgraphNode) {
                    // Subgraph node: register prep and finish workers, normalize subgraph config
                    String prepRef = getString(node, "_subgraph_prep_ref", "");
                    String finishRef = getString(node, "_subgraph_finish_ref", "");
                    if (!prepRef.isEmpty()) {
                        tools.add(
                                ToolConfig.builder()
                                        .name(prepRef)
                                        .description(
                                                "Subgraph prep for " + getString(node, "name", ""))
                                        .toolType("worker")
                                        .build());
                    }
                    if (!finishRef.isEmpty()) {
                        tools.add(
                                ToolConfig.builder()
                                        .name(finishRef)
                                        .description(
                                                "Subgraph finish for "
                                                        + getString(node, "name", ""))
                                        .toolType("worker")
                                        .build());
                    }
                    // Recursively normalize the subgraph config and store for the compiler
                    Map<String, Object> subConfig = getMap(node, "_subgraph_config");
                    if (subConfig != null) {
                        AgentConfig subAgent = normalize(subConfig);
                        if (subgraphConfigs == null) {
                            subgraphConfigs = new LinkedHashMap<>();
                        }
                        subgraphConfigs.put(getString(node, "name", ""), subAgent);
                        log.info(
                                "LangGraph '{}': subgraph node '{}' → sub-agent '{}'",
                                name,
                                getString(node, "name", ""),
                                subAgent.getName());
                        // Also register the subgraph's workers as parent tools
                        if (subAgent.getTools() != null) {
                            tools.addAll(subAgent.getTools());
                        }
                    }
                } else if (isLlmNode) {
                    // LLM node: register prep and finish workers (the LLM task is built by
                    // compiler)
                    String prepRef = getString(node, "_llm_prep_ref", "");
                    String finishRef = getString(node, "_llm_finish_ref", "");
                    if (!prepRef.isEmpty()) {
                        tools.add(
                                ToolConfig.builder()
                                        .name(prepRef)
                                        .description("LLM prep for " + getString(node, "name", ""))
                                        .toolType("worker")
                                        .build());
                    }
                    if (!finishRef.isEmpty()) {
                        tools.add(
                                ToolConfig.builder()
                                        .name(finishRef)
                                        .description(
                                                "LLM finish for " + getString(node, "name", ""))
                                        .toolType("worker")
                                        .build());
                    }
                } else {
                    String workerRef = getString(node, "_worker_ref", "");
                    if (!workerRef.isEmpty()) {
                        tools.add(
                                ToolConfig.builder()
                                        .name(workerRef)
                                        .description(getString(node, "name", workerRef))
                                        .toolType("worker")
                                        .build());
                    }
                }
            }
            // Also add router workers from conditional edges
            List<Map<String, Object>> conditionalEdges =
                    (List<Map<String, Object>>) graph.get("conditional_edges");
            if (conditionalEdges != null) {
                for (Map<String, Object> ce : conditionalEdges) {
                    String routerRef = getString(ce, "_router_ref", "");
                    if (!routerRef.isEmpty()) {
                        tools.add(
                                ToolConfig.builder()
                                        .name(routerRef)
                                        .description(
                                                "Router for " + getString(ce, "source", "unknown"))
                                        .toolType("worker")
                                        .build());
                    }
                }
            }
            config.setTools(tools);
        }

        // Store graph structure in metadata for the compiler
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_graph_structure", graph);

        // Extract reducer metadata for compiler (affects FORK_JOIN state merge)
        Map<String, Object> reducers = getMap(graph, "_reducers");
        if (reducers != null && !reducers.isEmpty()) {
            metadata.put("_reducers", reducers);
            log.info("LangGraph '{}': reducers: {}", name, reducers.keySet());
        }

        // Extract retry policies for compiler (maps to Conductor task retry settings)
        Map<String, Object> retryPolicies = getMap(graph, "_retry_policies");
        if (retryPolicies != null && !retryPolicies.isEmpty()) {
            metadata.put("_retry_policies", retryPolicies);
            log.info("LangGraph '{}': retry policies for nodes: {}", name, retryPolicies.keySet());
        }

        // Extract recursion limit (maps to DO_WHILE iteration cap)
        Object recursionLimit = graph.get("_recursion_limit");
        if (recursionLimit instanceof Number) {
            metadata.put("_recursion_limit", ((Number) recursionLimit).intValue());
        }

        // Store normalized subgraph configs for the compiler
        if (subgraphConfigs != null && !subgraphConfigs.isEmpty()) {
            metadata.put("_subgraph_configs", subgraphConfigs);
            log.info(
                    "LangGraph '{}': subgraph configs for nodes: {}",
                    name,
                    subgraphConfigs.keySet());
        }

        config.setMetadata(metadata);

        return config;
    }

    private AgentConfig normalizePassthrough(String name, Map<String, Object> raw) {
        log.info("LangGraph agent '{}' using passthrough (local graph execution)", name);
        String workerName = getString(raw, "_worker_name", name);

        AgentConfig config = new AgentConfig();
        config.setName(name);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_framework_passthrough", true);
        config.setMetadata(metadata);

        ToolConfig worker =
                ToolConfig.builder()
                        .name(workerName)
                        .description("LangGraph passthrough worker")
                        .toolType("worker")
                        .build();
        config.setTools(List.of(worker));

        return config;
    }

    @SuppressWarnings("unchecked")
    private ToolConfig normalizeAgentTool(Map<String, Object> raw) {
        Map<String, Object> agentRaw = getMap(raw, "agent");
        if (agentRaw == null) {
            log.warn(
                    "AgentTool '{}' has no embedded agent config, skipping",
                    getString(raw, "name", "unknown"));
            return null;
        }

        AgentConfig childAgent = normalize(agentRaw);
        String toolName = getString(raw, "name", childAgent.getName());
        String toolDesc = getString(raw, "description", "Invoke agent: " + childAgent.getName());

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put(
                "properties",
                Map.of(
                        "request",
                        Map.of(
                                "type",
                                "string",
                                "description",
                                "The request or question to send to this agent")));
        inputSchema.put("required", List.of("request"));
        inputSchema.put("additionalProperties", false);

        Map<String, Object> toolConfig = new LinkedHashMap<>();
        toolConfig.put("agentConfig", childAgent);

        return ToolConfig.builder()
                .name(toolName)
                .description(toolDesc)
                .inputSchema(inputSchema)
                .toolType("agent_tool")
                .config(toolConfig)
                .build();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List) return (List<Map<String, Object>>) v;
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return null;
    }
}
