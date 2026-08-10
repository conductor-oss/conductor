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

import org.conductoross.conductor.common.metadata.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Normalizes OpenAI Agent SDK raw configs into the canonical {@link AgentConfig}.
 *
 * <p>Handles the generic-serialized form of OpenAI Agent objects, where:
 *
 * <ul>
 *   <li>Callables are replaced with {@code {"_worker_ref": "name", ...}} markers
 *   <li>Non-callable objects are serialized with {@code {"_type": "ClassName", ...}}
 *   <li>Model names may lack the provider prefix (e.g. "gpt-4o" → "openai/gpt-4o")
 * </ul>
 */
@Component
public class OpenAINormalizer implements AgentConfigNormalizer {

    private static final Logger log = LoggerFactory.getLogger(OpenAINormalizer.class);

    @Override
    public String frameworkId() {
        return "openai";
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentConfig normalize(Map<String, Object> raw) {
        log.info("Normalizing OpenAI agent config: {}", raw.get("name"));

        AgentConfig config = new AgentConfig();
        config.setName(getString(raw, "name", "openai_agent"));

        // Model: prefix with "openai/" if no provider specified
        String model = getString(raw, "model", "gpt-4o");
        config.setModel(ensureProvider(model, "openai"));

        // Instructions
        config.setInstructions(raw.get("instructions"));

        // Tools
        List<Map<String, Object>> rawTools = getList(raw, "tools");
        if (rawTools != null) {
            List<ToolConfig> tools = new ArrayList<>();
            for (Map<String, Object> t : rawTools) {
                ToolConfig tool = normalizeTool(t);
                if (tool != null) {
                    tools.add(tool);
                }
            }
            if (!tools.isEmpty()) {
                config.setTools(tools);
            }
        }

        // Code execution — check if any tool is a CodeInterpreterTool
        if (hasToolType(rawTools, "CodeInterpreterTool")) {
            config.setCodeExecution(CodeExecutionConfig.builder().enabled(true).build());
        }

        // Handoffs → agents + strategy="handoff"
        List<Map<String, Object>> handoffs = getList(raw, "handoffs");
        if (handoffs != null && !handoffs.isEmpty()) {
            config.setStrategy(AgentConfig.Strategy.HANDOFF);
            List<AgentConfig> agents = new ArrayList<>();
            for (Map<String, Object> h : handoffs) {
                agents.add(normalize(h));
            }
            config.setAgents(agents);
        }

        // Output type
        Object outputType = raw.get("output_type");
        if (outputType == null) outputType = raw.get("outputType");
        if (outputType instanceof Map) {
            Map<String, Object> otMap = (Map<String, Object>) outputType;
            config.setOutputType(OutputTypeConfig.builder().schema(otMap).build());
        }

        // Guardrails
        List<GuardrailConfig> guardrails = new ArrayList<>();
        addGuardrails(guardrails, getList(raw, "guardrails"), null);
        addGuardrails(guardrails, getList(raw, "input_guardrails"), "input");
        addGuardrails(guardrails, getList(raw, "output_guardrails"), "output");
        addGuardrails(guardrails, getList(raw, "inputGuardrails"), "input");
        addGuardrails(guardrails, getList(raw, "outputGuardrails"), "output");
        if (!guardrails.isEmpty()) {
            config.setGuardrails(guardrails);
        }

        // Model settings (may be nested under "model_settings" or "modelSettings")
        Map<String, Object> modelSettings = getMap(raw, "model_settings");
        if (modelSettings == null) modelSettings = getMap(raw, "modelSettings");
        if (modelSettings != null) {
            if (modelSettings.containsKey("temperature")) {
                config.setTemperature(getDouble(modelSettings, "temperature"));
            }
            if (modelSettings.containsKey("max_tokens")) {
                config.setMaxTokens(getInt(modelSettings, "max_tokens"));
            }
            if (modelSettings.containsKey("maxTokens")) {
                config.setMaxTokens(getInt(modelSettings, "maxTokens"));
            }
        }
        // Also check top-level temperature/max_tokens/maxTokens
        if (raw.containsKey("temperature")) {
            config.setTemperature(getDouble(raw, "temperature"));
        }
        if (raw.containsKey("max_tokens")) {
            config.setMaxTokens(getInt(raw, "max_tokens"));
        }
        if (raw.containsKey("maxTokens")) {
            config.setMaxTokens(getInt(raw, "maxTokens"));
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private ToolConfig normalizeTool(Map<String, Object> raw) {
        // Worker ref (callable extracted by SDK)
        if (raw.containsKey("_worker_ref")) {
            return ToolConfig.builder()
                    .name(getString(raw, "_worker_ref", "unknown_tool"))
                    .description(getString(raw, "description", ""))
                    .inputSchema((Map<String, Object>) raw.get("parameters"))
                    .toolType("worker")
                    .build();
        }

        // Typed object (serialized non-callable)
        String type = getString(raw, "_type", getString(raw, "type", ""));

        switch (type) {
            case "WebSearchTool":
            case "web_search":
                return ToolConfig.builder()
                        .name("web_search")
                        .description("Search the web for information")
                        .toolType("http")
                        .config(Map.of("builtin", "web_search"))
                        .build();

            case "FileSearchTool":
            case "file_search":
                return ToolConfig.builder()
                        .name("file_search")
                        .description("Search through files")
                        .toolType("worker")
                        .build();

            case "CodeInterpreterTool":
            case "code_interpreter":
                // Handled separately via codeExecution config
                return null;

            case "FunctionTool":
            case "function":
            case "function_tool":
                // Function tool with inline schema (not a _worker_ref)
                return ToolConfig.builder()
                        .name(getString(raw, "name", "unknown_tool"))
                        .description(getString(raw, "description", ""))
                        .inputSchema((Map<String, Object>) raw.get("parameters"))
                        .toolType("worker")
                        .build();

            case "AgentTool":
                {
                    // Agent-as-tool: wrap a child agent as a callable tool (SUB_WORKFLOW)
                    Map<String, Object> agentRaw = getMap(raw, "agent");
                    if (agentRaw == null) {
                        log.warn(
                                "AgentTool '{}' has no embedded agent, skipping",
                                getString(raw, "name", "unknown"));
                        return null;
                    }
                    AgentConfig childAgent = normalize(agentRaw);
                    String toolName = getString(raw, "name", childAgent.getName());
                    String toolDesc =
                            getString(raw, "description", "Invoke agent: " + childAgent.getName());

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

            default:
                // Unknown tool type — pass through as worker
                if (raw.containsKey("name")) {
                    return ToolConfig.builder()
                            .name(getString(raw, "name", "unknown_tool"))
                            .description(getString(raw, "description", ""))
                            .inputSchema((Map<String, Object>) raw.get("parameters"))
                            .toolType("worker")
                            .build();
                }
                log.warn("Skipping unrecognized OpenAI tool: {}", raw);
                return null;
        }
    }

    private void addGuardrails(
            List<GuardrailConfig> guardrails,
            List<Map<String, Object>> rawGuardrails,
            String defaultPosition) {
        if (rawGuardrails == null || rawGuardrails.isEmpty()) {
            return;
        }
        for (Map<String, Object> g : rawGuardrails) {
            GuardrailConfig gc = normalizeGuardrail(g, defaultPosition);
            if (gc != null) {
                guardrails.add(gc);
            }
        }
    }

    private GuardrailConfig normalizeGuardrail(Map<String, Object> raw, String defaultPosition) {
        GuardrailConfig gc = new GuardrailConfig();
        gc.setName(getString(raw, "name", "guardrail"));

        // OpenAI guardrails have a "position" that's either "input" or "output"
        // The generic serializer may have it as a field or as _type
        String position = getString(raw, "position", defaultPosition);
        String type = getString(raw, "_type", "");
        if (position == null) {
            if (type.toLowerCase().contains("input")) {
                position = "input";
            } else if (type.toLowerCase().contains("output")) {
                position = "output";
            } else {
                position = "output";
            }
        }
        gc.setPosition(position);

        String workerRef = getString(raw, "_worker_ref", null);
        if (workerRef == null) workerRef = extractNestedWorkerRef(raw, "execute");
        if (workerRef == null) workerRef = extractNestedWorkerRef(raw, "guardrail_function");
        if (workerRef == null) workerRef = extractNestedWorkerRef(raw, "guardrailFunction");

        // Guardrails with worker refs are custom (callable-based)
        if (workerRef != null && !workerRef.isEmpty()) {
            gc.setGuardrailType("custom");
            gc.setTaskName(workerRef);
        } else {
            gc.setGuardrailType("custom");
            gc.setTaskName(getString(raw, "name", "guardrail"));
        }

        return gc;
    }

    private String extractNestedWorkerRef(Map<String, Object> raw, String key) {
        Map<String, Object> nested = getMap(raw, key);
        if (nested == null) {
            return null;
        }
        return getString(nested, "_worker_ref", null);
    }

    // ── Utility methods ─────────────────────────────────────────────

    private String ensureProvider(String model, String defaultProvider) {
        if (model == null || model.isEmpty()) {
            return defaultProvider + "/gpt-4o";
        }
        return model.contains("/") ? model : defaultProvider + "/" + model;
    }

    @SuppressWarnings("unchecked")
    private boolean hasToolType(List<Map<String, Object>> tools, String typeName) {
        if (tools == null) return false;
        for (Map<String, Object> t : tools) {
            String type = getString(t, "_type", getString(t, "type", ""));
            if (typeName.equals(type)) return true;
        }
        return false;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : defaultValue;
    }

    private String getString(Map<String, Object> map, String key) {
        return getString(map, key, null);
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return null;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return null;
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
