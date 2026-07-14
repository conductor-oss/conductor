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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.List;

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.ToolConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: {@link AgentConfig#getMaskedFields()} must be applied to the top-level
 * (user-visible) compiled {@link WorkflowDef} so that field redaction in Conductor execution
 * history / UI actually happens.
 *
 * <p>Deterministic — no LLM, no live server. Verifies the masked fields survive compilation for the
 * single-agent (no tools), tools, and multi-agent shapes.
 */
class MaskedFieldsCompilerTest {

    private static final List<String> MASKED = List.of("ssn", "token");

    @Test
    void singleAgentNoTools_carriesMaskedFields() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("simple_agent")
                        .model("openai/gpt-4o")
                        .instructions("You are a helpful agent.")
                        .maskedFields(MASKED)
                        .build();

        WorkflowDef wf = new AgentCompiler().compile(config);

        assertThat(wf.getMaskedFields()).containsExactlyInAnyOrderElementsOf(MASKED);
    }

    @Test
    void toolsPath_carriesMaskedFields() {
        ToolConfig tool =
                ToolConfig.builder().name("get_weather").description("Get the weather.").build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("tools_agent")
                        .model("openai/gpt-4o")
                        .instructions("You are a helpful agent.")
                        .tools(List.of(tool))
                        .maskedFields(MASKED)
                        .build();

        WorkflowDef wf = new AgentCompiler().compile(config);

        assertThat(wf.getMaskedFields()).containsExactlyInAnyOrderElementsOf(MASKED);
    }

    @Test
    void multiAgent_topLevelCarriesMaskedFields() {
        AgentConfig subAgent =
                AgentConfig.builder()
                        .name("worker_agent")
                        .model("openai/gpt-4o")
                        .instructions("You are a worker.")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("coordinator_agent")
                        .model("openai/gpt-4o")
                        .instructions("You coordinate.")
                        .agents(List.of(subAgent))
                        .maskedFields(MASKED)
                        .build();

        WorkflowDef wf = new AgentCompiler().compile(config);

        // The user-visible top-level workflow must carry maskedFields.
        assertThat(wf.getMaskedFields()).containsExactlyInAnyOrderElementsOf(MASKED);
    }
}
