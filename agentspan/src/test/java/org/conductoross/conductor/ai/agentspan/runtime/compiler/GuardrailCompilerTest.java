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

import org.conductoross.conductor.common.metadata.agent.GuardrailConfig;
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

    // ── Issue #1323: output guardrails must see the LLM's toolCalls ──────
    //
    // Output-position guardrails are evaluated every loop iteration. On a
    // tool-call turn the LLM output.result is [] (only non-tool turns produce a
    // result Map), so a non-empty-content guardrail fails on every tool round
    // and injects a correction mid-tool-use. The fix binds a `toolCalls` input
    // referencing the agent LLM task's output.toolCalls so the generated script
    // can short-circuit on tool-call turns.
    @Test
    void testOutputRegexGuardrailBindsToolCalls() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("non_empty")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("retry")
                        .patterns(List.of("\\w"))
                        .mode("allow")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results = gc.compileGuardrailTasks(List.of(g), "agent", "${agent_llm.output.result}");

        WorkflowTask inline = results.get(0).getTasks().get(0);
        assertThat(inline.getType()).isEqualTo("INLINE");
        // The INLINE guardrail task must bind toolCalls to the agent LLM task's
        // output.toolCalls so the script can detect tool-call turns.
        assertThat(inline.getInputParameters())
                .containsEntry("toolCalls", "${agent_llm.output.toolCalls}");
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
    void testCustomGuardrailNormalizeWiresLiveIterationAndMaxRetriesForEscalation() {
        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("custom_check")
                        .guardrailType("custom")
                        .position("output")
                        .taskName("my_guardrail_worker")
                        .onFail("retry")
                        .maxRetries(3)
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results = gc.compileGuardrailTasks(List.of(g), "agent", "${ref}");

        WorkflowTask normalize = results.get(0).getTasks().get(1);
        assertThat(normalize.getType()).isEqualTo("INLINE");
        // - must read from DO_WHILE task's OUTPUT: ${<loop>.output.iteration}
        // - bare ${<loop>.iteration} = null mid-loop -> escalation silently disabled
        assertThat((String) normalize.getInputParameters().get("iteration"))
                .isEqualTo("${agent_loop.output.iteration}");
        assertThat(normalize.getInputParameters().get("max_retries")).isEqualTo(3);
        // normalize script: same retry->raise coercion as regex/llm scripts
        assertThat((String) normalize.getInputParameters().get("expression"))
                .contains("iteration >= max_retries")
                .contains("'raise'")
                // fix -> raise only if no fixedOutput
                // else: fix guardrail w/ output wrongly terminated, not applied
                .contains("fixedOutput === null");
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

    @Test
    void testToolGuardrailIterationUsesLiveLoopCounterForAllTypes() {
        // - compileToolGuardrailTasks used to pass constant "1", not live DoWhile counter
        // - silently disabled retry->raise escalation for every tool-guardrail type
        // - must resolve to ${<agent>_loop.output.iteration} (agent-level expression)
        // - never literal "1", never bare (pre-cc025ed0) ${<agent>_loop.iteration}
        GuardrailConfig customGuard =
                GuardrailConfig.builder()
                        .name("custom_check")
                        .guardrailType("custom")
                        .taskName("my_guardrail_worker")
                        .onFail("retry")
                        .maxRetries(2)
                        .build();
        GuardrailConfig regexGuard =
                GuardrailConfig.builder()
                        .name("regex_check")
                        .guardrailType("regex")
                        .onFail("retry")
                        .maxRetries(2)
                        .patterns(List.of("dangerous"))
                        .mode("block")
                        .build();
        GuardrailConfig llmGuard =
                GuardrailConfig.builder()
                        .name("llm_check")
                        .guardrailType("llm")
                        .onFail("retry")
                        .maxRetries(2)
                        .model("openai/gpt-4o")
                        .policy("no secrets")
                        .build();

        GuardrailCompiler gc = new GuardrailCompiler();
        var results =
                gc.compileToolGuardrailTasks(
                        List.of(customGuard, regexGuard, llmGuard), "agent", "${ref}");
        assertThat(results).hasSize(3);

        // custom: iteration lands on the SIMPLE worker task (index 0)
        assertThat((String) results.get(0).getTasks().get(0).getInputParameters().get("iteration"))
                .isEqualTo("${agent_loop.output.iteration}");
        // regex: single INLINE task
        assertThat((String) results.get(1).getTasks().get(0).getInputParameters().get("iteration"))
                .isEqualTo("${agent_loop.output.iteration}");
        // llm: iteration lands on the parser INLINE task (index 1)
        assertThat((String) results.get(2).getTasks().get(1).getInputParameters().get("iteration"))
                .isEqualTo("${agent_loop.output.iteration}");
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
