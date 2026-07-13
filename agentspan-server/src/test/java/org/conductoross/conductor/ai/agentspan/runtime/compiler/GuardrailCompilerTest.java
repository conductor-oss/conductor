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

import org.conductoross.conductor.ai.agentspan.runtime.model.GuardrailConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.*;

class GuardrailCompilerTest {

    @Test
    void testRegexBlock() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("no_ssn")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("retry")
                        .patterns(List.of("\\d{3}-\\d{2}-\\d{4}"))
                        .mode("block")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results = gc.compileGuardrailTasks(List.of(g), "agent", "${agent_llm.output.result}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isInline()).isTrue();
        assertThat(results.get(0).getTasks()).hasSize(1);
        assertThat(results.get(0).getTasks().get(0).getType()).isEqualTo("INLINE");
        assertThat(results.get(0).getRefName()).isEqualTo("agent_regex_guardrail_no_ssn");
    }

    @Test
    void testLLMGuardrail() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("safety")
                        .guardrailType("llm")
                        .position("output")
                        .model("openai/gpt-4o")
                        .policy("No harmful content")
                        .onFail("retry")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results = gc.compileGuardrailTasks(List.of(g), "agent", "${ref}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isInline()).isTrue();
        // LLM guardrail produces 2 tasks (LLM + parser)
        assertThat(results.get(0).getTasks()).hasSize(2);
        assertThat(results.get(0).getTasks().get(0).getType()).isEqualTo("LLM_CHAT_COMPLETE");
        assertThat(results.get(0).getTasks().get(1).getType()).isEqualTo("INLINE");
    }

    @Test
    void testCustomGuardrail() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("custom_check")
                        .guardrailType("custom")
                        .position("output")
                        .taskName("my_guardrail_worker")
                        .onFail("raise")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results = gc.compileGuardrailTasks(List.of(g), "agent", "${ref}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isInline()).isTrue();
        assertThat(results.get(0).getTasks()).hasSize(2);
        assertThat(results.get(0).getTasks().get(0).getType()).isEqualTo("SIMPLE");
        assertThat(results.get(0).getTasks().get(1).getType()).isEqualTo("INLINE");
    }

    @Test
    void testCustomGuardrailIncludesOpenAICompatibleInputAliases() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("custom_check")
                        .guardrailType("custom")
                        .position("output")
                        .taskName("my_guardrail_worker")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results = gc.compileGuardrailTasks(List.of(g), "agent", "${ref}");

        WorkflowTask workerTask = results.get(0).getTasks().get(0);
        assertThat(workerTask.getInputParameters())
                .containsEntry("content", "${ref}")
                .containsEntry("input", "${ref}")
                .containsEntry("input_text", "${ref}")
                .containsEntry("output", "${ref}")
                .containsEntry("agentOutput", "${ref}")
                .containsEntry("agent_output", "${ref}");
    }

    @Test
    void testMultipleCustomGuardrailsUseDistinctRefs() {
        GuardrailConfig first =
                GuardrailConfig.builder()
                        .name("first_check")
                        .guardrailType("custom")
                        .position("output")
                        .taskName("first_worker")
                        .build();

        GuardrailConfig second =
                GuardrailConfig.builder()
                        .name("second_check")
                        .guardrailType("custom")
                        .position("output")
                        .taskName("second_worker")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results = gc.compileGuardrailTasks(List.of(first, second), "agent", "${ref}");

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(GuardrailCompiler.GuardrailTaskResult::getRefName)
                .containsExactly(
                        "agent_output_guardrail_first_check",
                        "agent_output_guardrail_second_check");
        assertThat(results)
                .flatExtracting(
                        r -> r.getTasks().stream().map(WorkflowTask::getTaskReferenceName).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void testRoutingRetry() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("test")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("retry")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var routing = gc.compileGuardrailRouting(g, "guard_ref", "${content}", "agent", "", true);

        assertThat(routing.getSwitchTask().getType()).isEqualTo("SWITCH");
        assertThat(routing.getSwitchTask().getDecisionCases()).containsKey("retry");
        assertThat(routing.getRetryRef()).isEqualTo("agent_guardrail_retry");
    }

    @Test
    void testRoutingRaise() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("test")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("raise")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var routing = gc.compileGuardrailRouting(g, "guard_ref", "${content}", "agent", "", false);

        assertThat(routing.getSwitchTask().getDecisionCases()).containsKey("raise");
        List<WorkflowTask> raiseTasks = routing.getSwitchTask().getDecisionCases().get("raise");
        assertThat(raiseTasks.get(0).getType()).isEqualTo("TERMINATE");
    }

    @Test
    void testToolGuardrailCompilation_usesToolPrefix() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("no_rm_rf")
                        .guardrailType("regex")
                        .position("input") // position is not filtered for tool guardrails
                        .onFail("raise")
                        .patterns(List.of("rm\\s+-rf"))
                        .mode("block")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results =
                gc.compileToolGuardrailTasks(
                        List.of(g), "agent", "${agent_format_tool_calls.output.result.formatted}");

        assertThat(results).hasSize(1);
        // Ref name uses "_tool" prefix to avoid collision with agent-level guardrails
        assertThat(results.get(0).getRefName()).startsWith("agent_tool_");
        assertThat(results.get(0).getTasks()).hasSize(1);
        assertThat(results.get(0).getTasks().get(0).getType()).isEqualTo("INLINE");
    }

    @Test
    void testToolGuardrailCompilation_noPositionFiltering() {
        // Tool guardrails with position="input" should still compile
        // (unlike agent guardrails which filter to output only)
        GuardrailConfig inputGuard =
                GuardrailConfig.builder()
                        .name("check_input")
                        .guardrailType("regex")
                        .position("input")
                        .onFail("raise")
                        .patterns(List.of("dangerous"))
                        .mode("block")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();

        // compileGuardrailTasks filters out input guardrails
        var agentResults = gc.compileGuardrailTasks(List.of(inputGuard), "agent", "${ref}");
        assertThat(agentResults).isEmpty();

        // compileToolGuardrailTasks does NOT filter
        var toolResults = gc.compileToolGuardrailTasks(List.of(inputGuard), "agent", "${ref}");
        assertThat(toolResults).hasSize(1);
    }

    // ── Reachable-cases-only emission tests ───────────────────────────
    //
    // Previously every guardrail emitted retry+raise+fix unconditionally,
    // even when the configured ``on_fail`` could never trigger them. That
    // wasted Conductor TaskDefs and obscured intent in the workflow JSON.

    @Test
    void testRoutingRaise_emitsOnlyRaiseCase() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("test")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("raise")
                        .build();
        var routing =
                new GuardrailCompiler()
                        .compileGuardrailRouting(g, "guard_ref", "${content}", "agent", "", true);

        assertThat(routing.getSwitchTask().getDecisionCases())
                .as("on_fail=raise emits ONLY the raise case (no dead retry/fix branches)")
                .containsOnlyKeys("raise");
    }

    @Test
    void testRoutingRetry_emitsRetryAndRaiseFallback() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("test")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("retry")
                        .build();
        var routing =
                new GuardrailCompiler()
                        .compileGuardrailRouting(g, "guard_ref", "${content}", "agent", "", true);

        // retry needs raise too — the JS coerces retry to raise once
        // ``iteration >= max_retries``.
        assertThat(routing.getSwitchTask().getDecisionCases()).containsOnlyKeys("retry", "raise");
    }

    @Test
    void testRoutingFix_emitsFixAndRaiseFallback() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("test")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("fix")
                        .build();
        var routing =
                new GuardrailCompiler()
                        .compileGuardrailRouting(g, "guard_ref", "${content}", "agent", "", true);

        // Custom guardrails can return on_fail=fix directly; regex/llm scripts
        // coerce fix to raise. Both paths land on cases the SWITCH knows about.
        assertThat(routing.getSwitchTask().getDecisionCases()).containsOnlyKeys("fix", "raise");
    }

    @Test
    void testRoutingHuman_emitsHumanAndRaiseFallback() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("test")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("human")
                        .build();
        var routing =
                new GuardrailCompiler()
                        .compileGuardrailRouting(g, "guard_ref", "${content}", "agent", "", true);

        assertThat(routing.getSwitchTask().getDecisionCases()).containsOnlyKeys("human", "raise");
    }
}
