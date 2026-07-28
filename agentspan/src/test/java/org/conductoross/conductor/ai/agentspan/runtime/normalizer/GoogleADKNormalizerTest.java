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
package org.conductoross.conductor.ai.agentspan.runtime.normalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleADKNormalizerTest {

    private final GoogleADKNormalizer normalizer = new GoogleADKNormalizer();

    @Test
    void googleSearchToolUsesProviderNativeSearchInsteadOfAnEmptyHttpTask() {
        AgentConfig config =
                normalizer.normalize(
                        Map.of(
                                "name", "researcher",
                                "model", "openai/gpt-4o-mini",
                                "instruction", "Search before answering.",
                                "tools", List.of(Map.of("_type", "GoogleSearchTool"))));

        assertThat(config.getTools()).isNullOrEmpty();
        assertThat(config.getMetadata()).containsEntry("_builtin_web_search", true);

        WorkflowDef workflow = new AgentCompiler().compile(config);
        List<WorkflowTask> tasks = flatten(workflow.getTasks());
        WorkflowTask llm =
                tasks.stream()
                        .filter(task -> "LLM_CHAT_COMPLETE".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();

        assertThat(llm.getInputParameters()).containsEntry("webSearch", true);
        assertThat(tasks)
                .noneMatch(
                        task ->
                                "HTTP".equals(task.getType())
                                        && task.getInputParameters() != null
                                        && String.valueOf(task.getInputParameters())
                                                .contains("google_search"));
    }

    @Test
    void googleSearchToolRejectsProvidersWithoutNativeWebSearch() {
        AgentConfig config =
                normalizer.normalize(
                        Map.of(
                                "name", "researcher",
                                "model", "ollama/llama3",
                                "tools", List.of(Map.of("_type", "GoogleSearchTool"))));

        assertThatThrownBy(() -> new AgentCompiler().compile(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Provider-native web search requires an OpenAI or Anthropic model");
    }

    private static List<WorkflowTask> flatten(List<WorkflowTask> tasks) {
        List<WorkflowTask> result = new ArrayList<>();
        for (WorkflowTask task : tasks) {
            result.add(task);
            if (task.getLoopOver() != null) {
                result.addAll(flatten(task.getLoopOver()));
            }
            if (task.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : task.getDecisionCases().values()) {
                    result.addAll(flatten(branch));
                }
            }
            if (task.getDefaultCase() != null) {
                result.addAll(flatten(task.getDefaultCase()));
            }
        }
        return result;
    }
}
