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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class VercelAINormalizerTest {

    private final VercelAINormalizer normalizer = new VercelAINormalizer();

    @Test
    void testFrameworkId() {
        assertThat(normalizer.frameworkId()).isEqualTo("vercel_ai");
    }

    // ── Basic agent normalization ───────────────────────────────────

    @Test
    void testNormalizeWithDefaults() {
        AgentConfig config = normalizer.normalize(Map.of());

        assertThat(config.getName()).isEqualTo("vercel_ai_agent");
        assertThat(config.getModel()).isEqualTo("openai/gpt-4o");
        // No passthrough metadata
        assertThat(config.getMetadata()).isNull();
    }

    @Test
    void testNormalizeWithCustomName() {
        AgentConfig config = normalizer.normalize(Map.of("name", "my_vercel_agent"));

        assertThat(config.getName()).isEqualTo("my_vercel_agent");
    }

    @Test
    void testNormalizeWithIdFallback() {
        AgentConfig config = normalizer.normalize(Map.of("id", "my_agent_id"));

        assertThat(config.getName()).isEqualTo("my_agent_id");
    }

    @Test
    void testPassthroughNotInMetadata() {
        AgentConfig config = normalizer.normalize(Map.of("name", "test_agent"));

        // _framework_passthrough must NOT be in metadata
        assertThat(config.getMetadata()).isNull();
    }

    // ── Model extraction ────────────────────────────────────────────

    @Test
    void testModelStringBare() {
        AgentConfig config = normalizer.normalize(Map.of("model", "gpt-4o-mini"));

        assertThat(config.getModel()).isEqualTo("openai/gpt-4o-mini");
    }

    @Test
    void testModelStringWithProvider() {
        AgentConfig config =
                normalizer.normalize(Map.of("model", "anthropic/claude-sonnet-4-20250514"));

        assertThat(config.getModel()).isEqualTo("anthropic/claude-sonnet-4-20250514");
    }

    @Test
    void testModelNestedObject() {
        Map<String, Object> modelObj = new LinkedHashMap<>();
        modelObj.put("modelId", "gpt-4o");
        modelObj.put("provider", "openai");

        AgentConfig config = normalizer.normalize(Map.of("model", modelObj));

        assertThat(config.getModel()).isEqualTo("openai/gpt-4o");
    }

    @Test
    void testModelDefaultWhenMissing() {
        AgentConfig config = normalizer.normalize(Map.of("name", "test"));

        assertThat(config.getModel()).isEqualTo("openai/gpt-4o");
    }

    // ── Instructions extraction ─────────────────────────────────────

    @Test
    void testInstructionsFromSystem() {
        AgentConfig config = normalizer.normalize(Map.of("system", "You are a helpful assistant."));

        assertThat(config.getInstructions()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void testInstructionsFromInstructions() {
        AgentConfig config = normalizer.normalize(Map.of("instructions", "Be helpful."));

        assertThat(config.getInstructions()).isEqualTo("Be helpful.");
    }

    @Test
    void testSystemPrioritizedOverInstructions() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("system", "System wins.");
        raw.put("instructions", "Instructions loses.");

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getInstructions()).isEqualTo("System wins.");
    }

    // ── Tool extraction (array style) ───────────────────────────────

    @Test
    void testWorkerRefTools() {
        Map<String, Object> tool1 = new LinkedHashMap<>();
        tool1.put("_worker_ref", "get_weather");
        tool1.put("description", "Get current weather");
        tool1.put(
                "parameters",
                Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));

        Map<String, Object> tool2 = new LinkedHashMap<>();
        tool2.put("_worker_ref", "calculate");
        tool2.put("description", "Do math");
        tool2.put(
                "parameters",
                Map.of("type", "object", "properties", Map.of("expr", Map.of("type", "string"))));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "tool_agent");
        raw.put("model", "gpt-4o");
        raw.put("tools", List.of(tool1, tool2));

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getTools()).hasSize(2);
        assertThat(config.getTools().get(0).getName()).isEqualTo("get_weather");
        assertThat(config.getTools().get(0).getToolType()).isEqualTo("worker");
        assertThat(config.getTools().get(0).getDescription()).isEqualTo("Get current weather");
        assertThat(config.getTools().get(1).getName()).isEqualTo("calculate");
        assertThat(config.getTools().get(1).getToolType()).isEqualTo("worker");
    }

    @Test
    void testNamedTools() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "search");
        tool.put("description", "Search the web");
        tool.put(
                "parameters",
                Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))));

        AgentConfig config = normalizer.normalize(Map.of("name", "test", "tools", List.of(tool)));

        assertThat(config.getTools()).hasSize(1);
        assertThat(config.getTools().get(0).getName()).isEqualTo("search");
        assertThat(config.getTools().get(0).getToolType()).isEqualTo("worker");
    }

    // ── Tool extraction (map style) ─────────────────────────────────

    @Test
    void testToolMapStyle() {
        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("description", "Search the web");
        toolDef.put(
                "parameters",
                Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))));

        Map<String, Object> toolMap = new LinkedHashMap<>();
        toolMap.put("search", toolDef);

        AgentConfig config = normalizer.normalize(Map.of("name", "test", "tools", toolMap));

        assertThat(config.getTools()).hasSize(1);
        assertThat(config.getTools().get(0).getName()).isEqualTo("search");
        assertThat(config.getTools().get(0).getDescription()).isEqualTo("Search the web");
        assertThat(config.getTools().get(0).getToolType()).isEqualTo("worker");
    }

    @Test
    void testToolMapStyleWithWorkerRef() {
        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("_worker_ref", "my_custom_tool");
        toolDef.put("description", "Custom tool");
        toolDef.put("parameters", Map.of("type", "object"));

        Map<String, Object> toolMap = new LinkedHashMap<>();
        toolMap.put("custom", toolDef);

        AgentConfig config = normalizer.normalize(Map.of("name", "test", "tools", toolMap));

        assertThat(config.getTools()).hasSize(1);
        // Worker ref name takes precedence over map key
        assertThat(config.getTools().get(0).getName()).isEqualTo("my_custom_tool");
        assertThat(config.getTools().get(0).getToolType()).isEqualTo("worker");
    }

    // ── Model settings ──────────────────────────────────────────────

    @Test
    void testTemperatureAndMaxTokensTopLevel() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "test");
        raw.put("temperature", 0.7);
        raw.put("maxTokens", 500);

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getTemperature()).isEqualTo(0.7);
        assertThat(config.getMaxTokens()).isEqualTo(500);
    }

    @Test
    void testModelSettingsNested() {
        Map<String, Object> settings = Map.of("temperature", 0.5, "max_tokens", 1000);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "test");
        raw.put("model_settings", settings);

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getTemperature()).isEqualTo(0.5);
        assertThat(config.getMaxTokens()).isEqualTo(1000);
    }

    // ── Output type ─────────────────────────────────────────────────

    @Test
    void testOutputType() {
        Map<String, Object> schema =
                Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "test");
        raw.put("output_type", schema);

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getOutputType()).isNotNull();
        assertThat(config.getOutputType().getSchema()).containsKey("type");
    }

    // ── Max steps ───────────────────────────────────────────────────

    @Test
    void testMaxStepsMappedToMaxTurns() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "test");
        raw.put("maxSteps", 10);

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getMaxTurns()).isEqualTo(10);
    }

    // ── No tools produces no tool list ──────────────────────────────

    @Test
    void testNoToolsProducesNullTools() {
        AgentConfig config = normalizer.normalize(Map.of("name", "test", "model", "gpt-4o"));

        assertThat(config.getTools()).isNull();
    }
}
