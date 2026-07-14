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

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.OutputTypeConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.ToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Normalizes Vercel AI SDK rawConfig into the canonical {@link AgentConfig}.
 *
 * <p>Handles the generic-serialized form of Vercel AI agent objects, where:
 *
 * <ul>
 *   <li>Callables are replaced with {@code {"_worker_ref": "name", ...}} markers
 *   <li>Non-callable objects are serialized with {@code {"_type": "ClassName", ...}}
 *   <li>Model names may be provider-prefixed (e.g. "openai/gpt-4o") or bare
 *   <li>Tools may be a map (Vercel AI style) or an array
 * </ul>
 */
@Component
public class VercelAINormalizer implements AgentConfigNormalizer {

    private static final Logger log = LoggerFactory.getLogger(VercelAINormalizer.class);
    private static final String DEFAULT_NAME = "vercel_ai_agent";
    private static final String DEFAULT_PROVIDER = "openai";

    @Override
    public String frameworkId() {
        return "vercel_ai";
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentConfig normalize(Map<String, Object> raw) {
        String name = getString(raw, "name", getString(raw, "id", DEFAULT_NAME));
        log.info("Normalizing Vercel AI SDK agent: {}", name);

        AgentConfig config = new AgentConfig();
        config.setName(name);

        // Model: Vercel AI model may be a string like "gpt-4o" or provider-prefixed "openai/gpt-4o"
        // It can also be in a nested object with a "modelId" field
        String model = extractModel(raw);
        config.setModel(model);

        // Instructions: check "system", "instructions", "systemPrompt"
        Object instructions = raw.get("system");
        if (instructions == null) instructions = raw.get("instructions");
        if (instructions == null) instructions = raw.get("systemPrompt");
        config.setInstructions(instructions);

        // Tools: Vercel AI tools can be a map { toolName: toolDef } or an array
        List<ToolConfig> tools = extractTools(raw);
        if (!tools.isEmpty()) {
            config.setTools(tools);
        }

        // Output type / structured output
        Object outputType = raw.get("output_type");
        if (outputType == null) outputType = raw.get("outputType");
        if (outputType == null) outputType = raw.get("schema");
        if (outputType instanceof Map) {
            Map<String, Object> otMap = (Map<String, Object>) outputType;
            config.setOutputType(OutputTypeConfig.builder().schema(otMap).build());
        }

        // Model settings: temperature, max_tokens, maxTokens
        extractModelSettings(raw, config);

        // Max turns / max steps
        if (raw.containsKey("maxSteps")) {
            Integer maxSteps = getInt(raw, "maxSteps");
            if (maxSteps != null) {
                config.setMaxTurns(maxSteps);
            }
        }

        return config;
    }

    // ── Model extraction ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractModel(Map<String, Object> raw) {
        Object modelObj = raw.get("model");

        // String model: "gpt-4o" or "openai/gpt-4o"
        if (modelObj instanceof String) {
            return ensureProvider((String) modelObj, DEFAULT_PROVIDER);
        }

        // Nested model object: { modelId: "gpt-4o", provider: "openai", ... }
        if (modelObj instanceof Map) {
            Map<String, Object> modelMap = (Map<String, Object>) modelObj;
            String modelId =
                    getString(
                            modelMap,
                            "modelId",
                            getString(modelMap, "model", getString(modelMap, "name", null)));
            String provider =
                    getString(
                            modelMap,
                            "provider",
                            getString(modelMap, "providerId", DEFAULT_PROVIDER));
            if (modelId != null) {
                return ensureProvider(modelId, provider);
            }
        }

        // Default
        return DEFAULT_PROVIDER + "/gpt-4o";
    }

    // ── Tool extraction ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ToolConfig> extractTools(Map<String, Object> raw) {
        List<ToolConfig> tools = new ArrayList<>();
        Object rawTools = raw.get("tools");

        if (rawTools instanceof Map) {
            // Vercel AI style: { toolName: { description, parameters, execute, ... } }
            Map<String, Object> toolMap = (Map<String, Object>) rawTools;
            for (Map.Entry<String, Object> entry : toolMap.entrySet()) {
                String toolName = entry.getKey();
                Object toolDef = entry.getValue();
                ToolConfig tool = normalizeToolEntry(toolName, toolDef);
                if (tool != null) {
                    tools.add(tool);
                }
            }
        } else if (rawTools instanceof List) {
            // Array style: [ { name: "...", ... }, ... ]
            List<?> toolList = (List<?>) rawTools;
            for (Object item : toolList) {
                if (item instanceof Map) {
                    ToolConfig tool = normalizeTool((Map<String, Object>) item);
                    if (tool != null) {
                        tools.add(tool);
                    }
                }
            }
        }

        return tools;
    }

    /** Normalize a tool from a map entry (Vercel AI style: key = tool name, value = tool def). */
    @SuppressWarnings("unchecked")
    private ToolConfig normalizeToolEntry(String toolName, Object toolDef) {
        if (toolDef instanceof Map) {
            Map<String, Object> t = (Map<String, Object>) toolDef;

            // Worker ref (callable extracted by SDK)
            if (t.containsKey("_worker_ref")) {
                return ToolConfig.builder()
                        .name(getString(t, "_worker_ref", toolName))
                        .description(getString(t, "description", ""))
                        .inputSchema((Map<String, Object>) t.get("parameters"))
                        .toolType("worker")
                        .build();
            }

            // Regular tool definition
            return ToolConfig.builder()
                    .name(toolName)
                    .description(getString(t, "description", ""))
                    .inputSchema(extractInputSchema(t))
                    .toolType("worker")
                    .build();
        }

        // Scalar or unexpected type — skip
        return null;
    }

    /** Normalize a tool from a list entry (array style). */
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

        // Named tool with possible _type
        String name = getString(raw, "name", null);
        if (name != null) {
            return ToolConfig.builder()
                    .name(name)
                    .description(getString(raw, "description", ""))
                    .inputSchema(extractInputSchema(raw))
                    .toolType("worker")
                    .build();
        }

        log.warn("Skipping unrecognized Vercel AI tool: {}", raw);
        return null;
    }

    /**
     * Extract input schema from a tool definition. Vercel AI uses "parameters" (Zod-converted JSON
     * schema).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractInputSchema(Map<String, Object> toolDef) {
        Object schema = toolDef.get("parameters");
        if (schema == null) schema = toolDef.get("inputSchema");
        if (schema == null) schema = toolDef.get("schema");
        if (schema instanceof Map) {
            return (Map<String, Object>) schema;
        }
        return null;
    }

    // ── Model settings extraction ───────────────────────────────────

    private void extractModelSettings(Map<String, Object> raw, AgentConfig config) {
        // Check nested model_settings
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
    }

    // ── Utility methods ─────────────────────────────────────────────

    private String ensureProvider(String model, String defaultProvider) {
        if (model == null || model.isEmpty()) {
            return defaultProvider + "/gpt-4o";
        }
        return model.contains("/") ? model : defaultProvider + "/" + model;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : defaultValue;
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
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return null;
    }
}
