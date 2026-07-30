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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.assertThat;

class LongTermMemoryCompilerTest {

    private final AgentCompiler compiler = new AgentCompiler();

    @Test
    @SuppressWarnings("unchecked")
    void registersOcgMcpRecallWithSecretReferenceAndBestEffortDiscovery() {
        WorkflowDef workflow = compiler.compile(agent());

        WorkflowTask listTools =
                workflow.getTasks().stream()
                        .filter(task -> "LIST_MCP_TOOLS".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(listTools.getInputParameters().get("mcpServer"))
                .isEqualTo("https://ocg.example/mcp/");
        assertThat(listTools.isOptional()).isTrue();
        Map<String, Object> headers =
                (Map<String, Object>) listTools.getInputParameters().get("headers");
        assertThat(headers).containsEntry("X-API-Key", "${workflow.secrets.OCG_KEY}");

        String definition = workflow.toString();
        assertThat(workflow.getMetadata().get("agentDef").toString())
                .contains("cg.search_memories");
        assertThat(definition)
                .doesNotContain("_ltm_distill")
                .doesNotContain("_ltm_save")
                .doesNotContain("feedback-links")
                .doesNotContain("MEMORY_SUMMARIZER");
    }

    @Test
    void doesNotAddOcgMcpWhenMemoryIsNotConfigured() {
        AgentConfig withoutMemory = agent().toBuilder().longTermMemory(null).build();
        WorkflowDef workflow = compiler.compile(withoutMemory);

        assertThat(workflow.getTasks()).noneMatch(task -> "LIST_MCP_TOOLS".equals(task.getType()));
    }

    private static AgentConfig agent() {
        ToolConfig worker =
                ToolConfig.builder()
                        .name("lookup")
                        .description("Lookup")
                        .inputSchema(Map.of("type", "object", "properties", Map.of()))
                        .toolType("worker")
                        .build();
        return AgentConfig.builder()
                .name("memory_agent")
                .model("openai/gpt-4o")
                .instructions("Help")
                .tools(List.of(worker))
                .longTermMemory(
                        LongTermMemoryConfig.builder()
                                .ocgUrl("https://ocg.example/")
                                .credential("OCG_KEY")
                                .agent("agentspan")
                                .user("user:alice")
                                .build())
                .build();
    }
}
