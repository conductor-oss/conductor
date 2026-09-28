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

import static org.assertj.core.api.Assertions.*;

class LangGraphNormalizerTest {

    private final LangGraphNormalizer normalizer = new LangGraphNormalizer();

    @Test
    void frameworkIdIsLanggraph() {
        assertThat(normalizer.frameworkId()).isEqualTo("langgraph");
    }

    @Test
    void normalizeProducesPassthroughConfig() {
        Map<String, Object> raw =
                Map.of(
                        "name", "my_graph",
                        "_worker_name", "my_graph");

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getName()).isEqualTo("my_graph");
        assertThat(config.getModel()).isNull();
        assertThat(config.getMetadata()).containsEntry("_framework_passthrough", true);
        assertThat(config.getTools()).hasSize(1);
        assertThat(config.getTools().get(0).getName()).isEqualTo("my_graph");
        assertThat(config.getTools().get(0).getToolType()).isEqualTo("worker");
    }

    @Test
    void normalizeUsesDefaultNameWhenMissing() {
        AgentConfig config = normalizer.normalize(Map.of());

        assertThat(config.getName()).isEqualTo("langgraph_agent");
        assertThat(config.getTools().get(0).getName()).isEqualTo("langgraph_agent");
        assertThat(config.getMetadata()).containsEntry("_framework_passthrough", true);
    }

    // ── Full extraction path ──────────────────────────────────────────

    @Test
    void normalizeFullExtractionWithModelAndTools() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "extracted_agent");
        raw.put("model", "openai/gpt-4o");
        raw.put("instructions", "You are helpful.");
        raw.put(
                "tools",
                List.of(
                        Map.of(
                                "_worker_ref",
                                "search_tool",
                                "description",
                                "Search the web",
                                "parameters",
                                Map.of("type", "object")),
                        Map.of(
                                "_worker_ref",
                                "calc_tool",
                                "description",
                                "Calculate",
                                "parameters",
                                Map.of("type", "object"))));

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getName()).isEqualTo("extracted_agent");
        assertThat(config.getModel()).isEqualTo("openai/gpt-4o");
        assertThat(config.getTools()).hasSize(2);
        assertThat(config.getTools().get(0).getName()).isEqualTo("search_tool");
        assertThat(config.getTools().get(1).getName()).isEqualTo("calc_tool");
        // Full extraction should NOT have _framework_passthrough
        assertThat(config.getMetadata()).isNull();
    }

    // ── Graph-structure path ──────────────────────────────────────────

    @Test
    void normalizeGraphStructureWithNodesAndEdges() {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put(
                "nodes",
                List.of(
                        Map.of("name", "fetch", "_worker_ref", "graph_fetch"),
                        Map.of("name", "process", "_worker_ref", "graph_process")));
        graph.put(
                "edges",
                List.of(
                        Map.of("source", "__start__", "target", "fetch"),
                        Map.of("source", "fetch", "target", "process"),
                        Map.of("source", "process", "target", "__end__")));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "my_structured_graph");
        raw.put("_graph", graph);

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getName()).isEqualTo("my_structured_graph");
        // Tools should be created for each node's worker ref
        assertThat(config.getTools()).hasSize(2);
        assertThat(config.getTools().stream().map(ToolConfig::getName).toList())
                .containsExactly("graph_fetch", "graph_process");
        // Metadata should contain _graph_structure (not _framework_passthrough)
        assertThat(config.getMetadata()).containsKey("_graph_structure");
        assertThat(config.getMetadata()).doesNotContainKey("_framework_passthrough");
    }

    @Test
    void normalizeGraphStructureWithLlmNode() {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put(
                "nodes",
                List.of(
                        Map.of("name", "prepare", "_worker_ref", "prep_worker"),
                        Map.of(
                                "name",
                                "analyze",
                                "_worker_ref",
                                "analyze_prep",
                                "_llm_node",
                                true,
                                "_llm_prep_ref",
                                "analyze_prep",
                                "_llm_finish_ref",
                                "analyze_finish")));
        graph.put(
                "edges",
                List.of(
                        Map.of("source", "__start__", "target", "prepare"),
                        Map.of("source", "prepare", "target", "analyze"),
                        Map.of("source", "analyze", "target", "__end__")));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "llm_graph");
        raw.put("model", "openai/gpt-4o");
        raw.put("_graph", graph);

        AgentConfig config = normalizer.normalize(raw);

        // Tools should include prep worker, LLM prep, and LLM finish
        assertThat(config.getTools()).hasSize(3);
        List<String> toolNames = config.getTools().stream().map(ToolConfig::getName).toList();
        assertThat(toolNames).contains("prep_worker", "analyze_prep", "analyze_finish");
        assertThat(config.getMetadata()).containsKey("_graph_structure");
    }

    @Test
    void normalizeGraphStructureWithSubgraphNode() {
        // Build a sub-graph raw config (will be recursively normalized)
        Map<String, Object> subRaw = new LinkedHashMap<>();
        subRaw.put("name", "child_graph");
        subRaw.put("_worker_name", "child_graph");

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put(
                "nodes",
                List.of(
                        Map.of(
                                "name",
                                "sub",
                                "_worker_ref",
                                "sub_prep",
                                "_subgraph_node",
                                true,
                                "_subgraph_prep_ref",
                                "sub_prep",
                                "_subgraph_finish_ref",
                                "sub_finish",
                                "_subgraph_config",
                                subRaw)));
        graph.put(
                "edges",
                List.of(
                        Map.of("source", "__start__", "target", "sub"),
                        Map.of("source", "sub", "target", "__end__")));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "parent_graph");
        raw.put("_graph", graph);

        AgentConfig config = normalizer.normalize(raw);

        // Metadata should contain _subgraph_configs
        assertThat(config.getMetadata()).containsKey("_subgraph_configs");
        @SuppressWarnings("unchecked")
        Map<String, AgentConfig> subgraphConfigs =
                (Map<String, AgentConfig>) config.getMetadata().get("_subgraph_configs");
        assertThat(subgraphConfigs).containsKey("sub");
        assertThat(subgraphConfigs.get("sub").getName()).isEqualTo("child_graph");

        // Tools should include prep, finish, and the sub-agent's worker
        List<String> toolNames = config.getTools().stream().map(ToolConfig::getName).toList();
        assertThat(toolNames).contains("sub_prep", "sub_finish", "child_graph");
    }

    @Test
    void normalizeGraphStructureConditionalEdgeMerging() {
        // Two conditional_edges from the same source — should merge without error
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put(
                "nodes",
                List.of(
                        Map.of("name", "classify", "_worker_ref", "classify_worker"),
                        Map.of("name", "a", "_worker_ref", "a_worker"),
                        Map.of("name", "b", "_worker_ref", "b_worker")));
        graph.put("edges", List.of(Map.of("source", "__start__", "target", "classify")));
        graph.put(
                "conditional_edges",
                List.of(
                        Map.of(
                                "source",
                                "classify",
                                "_router_ref",
                                "router_v1",
                                "targets",
                                Map.of("a", "a")),
                        Map.of(
                                "source",
                                "classify",
                                "_router_ref",
                                "router_v2",
                                "targets",
                                Map.of("b", "b"))));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "merge_test");
        raw.put("_graph", graph);

        // Should normalize without error
        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getName()).isEqualTo("merge_test");
        assertThat(config.getMetadata()).containsKey("_graph_structure");
        // Tools should include router workers
        List<String> toolNames = config.getTools().stream().map(ToolConfig::getName).toList();
        assertThat(toolNames).contains("classify_worker", "a_worker", "b_worker");
    }
}
