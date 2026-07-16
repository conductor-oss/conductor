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
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REPRODUCES ISSUE #1323 (compiler half) - AgentSpan output guardrails misfire on tool-call turns.
 *
 * <p>The runtime misfire (see {@code Issue1323GuardrailToolCallMisfireTest}) is only fixable if the
 * compiled INLINE guardrail task is actually fed the LLM turn's tool calls. Today {@link
 * GuardrailCompiler} binds only {@code content} (the LLM {@code output.result}) and {@code
 * iteration} into the guardrail's inputs - there is no {@code toolCalls} binding - so the generated
 * script has no way to know a turn was a tool-call turn.
 *
 * <p>Fix contract asserted here: the compiled regex output guardrail must bind a {@code toolCalls}
 * input referencing the LLM task's {@code output.toolCalls}, and the generated guardrail script
 * must reference {@code toolCalls}.
 *
 * <p>Status: FAILS on current code (no {@code toolCalls} binding is emitted) until the fix is
 * applied.
 */
class Issue1323GuardrailToolCallBindingTest {

    @Test
    void compiledOutputGuardrailBindsToolCallsFromLlmOutput() {
        // The exact guardrail from the incident: non-empty-content ("allow" + \w) with onFail retry.
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("non_empty_output")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("retry")
                        .patterns(List.of("\\w"))
                        .mode("allow")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        String contentRef = "${myagent_llm.output.result}";
        var results = gc.compileGuardrailTasks(List.of(g), "myagent", contentRef);

        assertThat(results).hasSize(1);
        WorkflowTask inlineTask = results.get(0).getTasks().get(0);
        assertThat(inlineTask.getType()).isEqualTo("INLINE");

        Map<String, Object> inputs = inlineTask.getInputParameters();

        // Sanity: the existing content/iteration bindings are present.
        assertThat(inputs).containsEntry("content", contentRef);
        assertThat(inputs).containsKey("iteration");

        // REPRODUCES #1323 (compiler half): the guardrail must also receive the turn's tool calls
        // so the script can distinguish a tool-call turn from a genuine final answer.
        assertThat(inputs)
                .as("compiled output guardrail must bind toolCalls from the LLM output")
                .containsKey("toolCalls");
        assertThat(String.valueOf(inputs.get("toolCalls")))
                .as("toolCalls binding must reference the LLM task's output.toolCalls")
                .contains("toolCalls");

        // And the generated script must actually consult toolCalls to short-circuit.
        assertThat(String.valueOf(inputs.get("expression")))
                .as("generated guardrail script must reference toolCalls")
                .contains("toolCalls");
    }
}
