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

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAINormalizerTest {

    private final OpenAINormalizer normalizer = new OpenAINormalizer();

    @Test
    void frameworkIdIsOpenai() {
        assertThat(normalizer.frameworkId()).isEqualTo("openai");
    }

    @Test
    void normalizeSupportsCamelCaseFieldVariants() {
        Map<String, Object> schema =
                Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "camel_case_agent");
        raw.put("model", "gpt-4o");
        raw.put("instructions", "Answer carefully.");
        raw.put("outputType", schema);
        raw.put("modelSettings", Map.of("temperature", 0.7, "maxTokens", 321));
        raw.put("maxTokens", 654);

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getName()).isEqualTo("camel_case_agent");
        assertThat(config.getModel()).isEqualTo("openai/gpt-4o");
        assertThat(config.getOutputType()).isNotNull();
        assertThat(config.getOutputType().getSchema()).isEqualTo(schema);
        assertThat(config.getTemperature()).isEqualTo(0.7);
        assertThat(config.getMaxTokens()).isEqualTo(654);
    }

    @Test
    void normalizeExtractsNestedGuardrailWorkersFromPythonAndTypeScriptShapes() {
        Map<String, Object> inputGuardrail = new LinkedHashMap<>();
        inputGuardrail.put("name", "check_for_pii");
        inputGuardrail.put("execute", Map.of("_worker_ref", "check_for_pii"));

        Map<String, Object> outputGuardrail = new LinkedHashMap<>();
        outputGuardrail.put("name", "check_output_safety");
        outputGuardrail.put("guardrail_function", Map.of("_worker_ref", "check_output_safety"));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "guarded_agent");
        raw.put("inputGuardrails", List.of(inputGuardrail));
        raw.put("output_guardrails", List.of(outputGuardrail));

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getGuardrails()).hasSize(2);
        assertThat(config.getGuardrails())
                .anySatisfy(
                        g -> {
                            assertThat(g.getName()).isEqualTo("check_for_pii");
                            assertThat(g.getPosition()).isEqualTo("input");
                            assertThat(g.getTaskName()).isEqualTo("check_for_pii");
                        })
                .anySatisfy(
                        g -> {
                            assertThat(g.getName()).isEqualTo("check_output_safety");
                            assertThat(g.getPosition()).isEqualTo("output");
                            assertThat(g.getTaskName()).isEqualTo("check_output_safety");
                        });
    }

    @Test
    void normalizeAgentToolUsesChildAgentWorkflowConfig() {
        Map<String, Object> childAgent = new LinkedHashMap<>();
        childAgent.put("name", "sentiment_agent");
        childAgent.put("model", "gpt-4o");
        childAgent.put("instructions", "Analyze sentiment.");
        childAgent.put("tools", List.of());

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "manager");
        raw.put(
                "tools",
                List.of(
                        Map.of(
                                "_type", "AgentTool",
                                "name", "sentiment_analyzer",
                                "description", "Analyze sentiment with a specialist agent.",
                                "agent", childAgent)));

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getTools()).hasSize(1);
        ToolConfig tool = config.getTools().get(0);
        assertThat(tool.getName()).isEqualTo("sentiment_analyzer");
        assertThat(tool.getToolType()).isEqualTo("agent_tool");
        assertThat(tool.getInputSchema()).containsKey("properties");
        @SuppressWarnings("unchecked")
        AgentConfig nested = (AgentConfig) tool.getConfig().get("agentConfig");
        assertThat(nested.getName()).isEqualTo("sentiment_agent");
        assertThat(nested.getModel()).isEqualTo("openai/gpt-4o");
    }
}
