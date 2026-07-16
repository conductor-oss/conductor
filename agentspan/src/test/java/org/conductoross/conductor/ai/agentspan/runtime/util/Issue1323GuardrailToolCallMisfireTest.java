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
 * REPRODUCES ISSUE #1323 - AgentSpan output guardrails misfire on tool-call turns.
 *
 * <p>This test executes the ACTUAL guardrail script produced by {@link
 * JavaScriptBuilder#regexGuardrailScript} through the same GraalJS engine that the compiled INLINE
 * guardrail task uses at runtime, driving it with the exact inputs seen across a full agent loop:
 *
 * <ul>
 *   <li><b>Turn 1 (tool-call turn):</b> the LLM returns tool calls, so {@code
 *       ${<agent>_llm.output.result}} is {@code []} (empty) while {@code output.toolCalls} is a
 *       non-empty array. A non-empty-content regex guardrail (mode {@code allow}, pattern {@code
 *       \w}, onFail {@code retry}) must NOT fail here - the model is legitimately mid-tool-use.
 *   <li><b>Turn 2 (final turn):</b> the LLM returns real text and no tool calls; the guardrail must
 *       evaluate the content normally.
 * </ul>
 *
 * <p>Fix contract asserted here: output guardrails bind {@code toolCalls} from the LLM task output
 * and the generated script short-circuits {@code passed=true} when {@code toolCalls} is a non-empty
 * array; genuine final-turn behavior is unchanged.
 *
 * <p>Status: the tool-call-turn case FAILS on current code (the script has no {@code toolCalls}
 * awareness, so it returns {@code passed:false, should_continue:true} - injecting a spurious retry
 * message into the conversation) until the fix is applied. The final-turn cases already pass and
 * pin the "genuine behavior unchanged" half of the contract.
 */
class Issue1323GuardrailToolCallMisfireTest {

    // A non-empty-content "allow" guardrail: the output must contain at least one word char.
    // This is the exact guardrail shape from the incident.
    private static final String PATTERNS_JSON = "[\"\\\\w\"]";
    private static final String MODE = "allow";
    private static final String ON_FAIL = "retry";
    private static final String MESSAGE = "Content did not match any allowed pattern.";
    private static final int MAX_RETRIES = 3;
    private static final String NAME = "non_empty_output";

    private Context graalCtx;

    @BeforeEach
    void setUp() {
        graalCtx = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        graalCtx.close();
    }

    /**
     * Evaluate the real generated guardrail script with the given {@code $} input object literal.
     */
    private Value evaluate(String dollarObjectLiteralJs) {
        String script =
                "var $ = "
                        + dollarObjectLiteralJs
                        + "; "
                        + JavaScriptBuilder.regexGuardrailScript(
                                PATTERNS_JSON, MODE, ON_FAIL, MESSAGE, MAX_RETRIES, NAME);
        return graalCtx.eval("js", script);
    }

    @Test
    void toolCallTurn_guardrailMustPass_andMustNotInjectRetry() {
        // Turn 1: tool-call turn. output.result is [] (no assistant text yet); output.toolCalls is
        // a non-empty array. The guardrail must short-circuit to passed:true.
        Value result =
                evaluate(
                        "{content: [], iteration: 0,"
                                + " toolCalls: [{id: 'call_1', name: 'search', input: {q: 'x'}}]}");

        // REPRODUCES #1323: on current code the guardrail fails on the tool-call turn.
        assertThat(result.getMember("passed").asBoolean())
                .as("guardrail must PASS on a tool-call turn (content is legitimately empty)")
                .isTrue();

        // A passing guardrail must not drive a retry: no correction/user message gets injected
        // into the next turn's messages.
        assertThat(result.getMember("should_continue").asBoolean())
                .as("no retry message may be injected while the model is mid-tool-use")
                .isFalse();
    }

    @Test
    void finalTurn_withRealContent_guardrailEvaluatesContentNormally_andPasses() {
        // Turn 2: final answer, no tool calls. Content has word chars -> allow guardrail passes.
        Value result = evaluate("{content: 'The answer is 42.', iteration: 1, toolCalls: []}");

        assertThat(result.getMember("passed").asBoolean())
                .as("genuine non-empty final content still passes the allow guardrail")
                .isTrue();
        assertThat(result.getMember("should_continue").asBoolean()).isFalse();
    }

    @Test
    void finalTurn_withGenuinelyEmptyContent_guardrailStillFires() {
        // Control: an empty final answer with NO tool calls must still fail the allow guardrail,
        // proving the fix does not blanket-pass every turn - content is still evaluated.
        Value result = evaluate("{content: '', iteration: 0, toolCalls: []}");

        assertThat(result.getMember("passed").asBoolean())
                .as("genuinely empty final output must still fail the non-empty-content guardrail")
                .isFalse();
    }
}
