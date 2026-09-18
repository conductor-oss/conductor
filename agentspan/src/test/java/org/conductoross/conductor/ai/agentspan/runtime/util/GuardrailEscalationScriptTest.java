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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Engine-level validation of {@link JavaScriptBuilder#customGuardrailNormalizeScript()}.
 *
 * <ul>
 *   <li>executes the real script via GraalJS -- no LLM, no workflow executor
 *   <li>same evaluator as production ({@code evaluatorType: graaljs})
 *   <li>proves escalation logic itself, independent of compiler wiring ({@code
 *       GuardrailCompilerTest})
 * </ul>
 *
 * <p>Regression coverage:
 *
 * <ul>
 *   <li>tool-guardrail retry never escalated (compiler fed constant iteration "1")
 *   <li>upstream pair -- agent-level iteration ref + fix->raise-only-when-no-fixed-output
 *   <li>compiler now wires live per-turn iteration -- this script turns {@code iteration >=
 *       max_retries} into {@code on_fail: 'raise'}
 * </ul>
 */
class GuardrailEscalationScriptTest {

    private Context graalCtx;

    @BeforeEach
    void setUp() {
        graalCtx = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        graalCtx.close();
    }

    private Value normalize(String dollarJs) {
        String script =
                "var $ = " + dollarJs + "; " + JavaScriptBuilder.customGuardrailNormalizeScript();
        return graalCtx.eval("js", script);
    }

    // ── retry -> raise escalation (the G12 fix this validates) ────────

    @Test
    void retryBelowMaxRetriesStaysRetryAndContinues() {
        Value result =
                normalize(
                        "{worker_output: {on_fail: 'retry'}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 2, max_retries: 3}");

        assertThat(result.getMember("on_fail").asString()).isEqualTo("retry");
        assertThat(result.getMember("should_continue").asBoolean()).isTrue();
    }

    @Test
    void retryAtMaxRetriesEscalatesToRaiseAndStops() {
        Value result =
                normalize(
                        "{worker_output: {on_fail: 'retry'}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 3, max_retries: 3}");

        assertThat(result.getMember("on_fail").asString()).isEqualTo("raise");
        assertThat(result.getMember("should_continue").asBoolean()).isFalse();
    }

    @Test
    void retryPastMaxRetriesAlsoEscalates() {
        Value result =
                normalize(
                        "{worker_output: {on_fail: 'retry'}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 25, max_retries: 3}");

        assertThat(result.getMember("on_fail").asString()).isEqualTo("raise");
    }

    // ── fix -> raise only when there is no fixed_output (the 190c727f guard) ──

    @Test
    void fixWithFixedOutputNeverEscalatesRegardlessOfIteration() {
        Value result =
                normalize(
                        "{worker_output: {on_fail: 'fix', fixed_output: 'REDACTED'}, "
                                + "guardrail_name: 'g', default_on_fail: 'retry', "
                                + "iteration: 99, max_retries: 1}");

        assertThat(result.getMember("on_fail").asString()).isEqualTo("fix");
        assertThat(result.getMember("fixed_output").asString()).isEqualTo("REDACTED");
    }

    @Test
    void fixWithoutFixedOutputEscalatesToRaise() {
        Value result =
                normalize(
                        "{worker_output: {on_fail: 'fix'}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 1, max_retries: 3}");

        assertThat(result.getMember("on_fail").asString()).isEqualTo("raise");
    }

    // ── tripwire-style (e.g. OpenAI-guardrails) raw output takes the same coercion ──

    @Test
    void tripwireTriggeredRetryEscalatesAtMaxRetries() {
        Value result =
                normalize(
                        "{worker_output: {tripwire_triggered: true, output_info: 'blocked'}, "
                                + "guardrail_name: 'g', default_on_fail: 'retry', "
                                + "iteration: 2, max_retries: 2}");

        assertThat(result.getMember("on_fail").asString()).isEqualTo("raise");
        assertThat(result.getMember("should_continue").asBoolean()).isFalse();
    }

    @Test
    void tripwireNotTriggeredPassesRegardlessOfIteration() {
        Value result =
                normalize(
                        "{worker_output: {tripwire_triggered: false}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 99, max_retries: 1}");

        assertThat(result.getMember("passed").asBoolean()).isTrue();
        assertThat(result.getMember("on_fail").isNull()).isTrue();
    }

    // ── pass-through cases unaffected by iteration/max_retries ────────

    @Test
    void explicitPassIsNeverEscalated() {
        Value result =
                normalize(
                        "{worker_output: {passed: true, on_fail: 'pass'}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 99, max_retries: 0}");

        assertThat(result.getMember("passed").asBoolean()).isTrue();
        assertThat(result.getMember("should_continue").asBoolean()).isFalse();
    }

    @Test
    void nullWorkerOutputPassesWithoutEscalation() {
        Value result =
                normalize(
                        "{worker_output: null, guardrail_name: 'g', default_on_fail: 'retry', "
                                + "iteration: 99, max_retries: 0}");

        assertThat(result.getMember("passed").asBoolean()).isTrue();
    }

    // ── composition with the tool-call-turn short-circuit (Issue #1323 / PR #1352) ──
    //
    // compileGuardrailTasks binds both toolCalls (short-circuit, #1352) and
    // iteration/max_retries (escalation, this file) into the same normalize INLINE.
    // - short-circuit must win: tool-call turn = mid-tool-use, never raise
    // - even if iteration has already crossed max_retries
    // - matches script order: toolCalls check runs before escalate() is consulted

    @Test
    void toolCallTurnShortCircuitsPassEvenWhenIterationExceedsMaxRetries() {
        Value result =
                normalize(
                        "{worker_output: {on_fail: 'retry'}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 5, max_retries: 1, "
                                + "toolCalls: [{name: 'search'}]}");

        assertThat(result.getMember("passed").asBoolean())
                .as("a tool-call turn must pass regardless of retry escalation state")
                .isTrue();
        assertThat(result.getMember("on_fail").isNull()).isTrue();
        assertThat(result.getMember("should_continue").asBoolean()).isFalse();
    }

    @Test
    void emptyToolCallsArrayDoesNotShortCircuitAndEscalationStillFires() {
        Value result =
                normalize(
                        "{worker_output: {on_fail: 'retry'}, guardrail_name: 'g', "
                                + "default_on_fail: 'retry', iteration: 3, max_retries: 3, "
                                + "toolCalls: []}");

        assertThat(result.getMember("on_fail").asString())
                .as(
                        "an empty toolCalls array is a genuine final turn -- escalation must still fire")
                .isEqualTo("raise");
    }
}
