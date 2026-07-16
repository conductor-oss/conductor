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

import org.conductoross.conductor.ai.agentspan.runtime.util.JavaScriptBuilder;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #1323 — output-position guardrails misfire on tool-call turns.
 *
 * <p>Output guardrails are evaluated every loop iteration. On a tool-call turn the LLM task's
 * {@code output.result} is {@code []} (only non-tool turns produce a result Map), so a
 * non-empty-content guardrail (e.g. allow pattern {@code \w}) fails on every tool round and its
 * retry branch injects a correction message mid-tool-use.
 *
 * <p>The fix short-circuits the generated guardrail script BEFORE pattern evaluation: if {@code
 * $.toolCalls} is a non-empty array, it returns {@code passed: true}. Genuine final turns
 * (toolCalls empty/absent) evaluate the content as before.
 *
 * <p>This test evaluates the real script produced by {@link JavaScriptBuilder} through GraalJS,
 * mirroring {@code SynthOutputScriptTest}'s harness.
 */
class GuardrailToolCallShortCircuitScriptTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Context graalCtx;

    @BeforeEach
    void setUp() {
        graalCtx = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        graalCtx.close();
    }

    /** A non-empty-content regex guardrail: allow-mode, pattern {@code \w}, retry on fail. */
    private static String regexGuardrailScript() {
        return JavaScriptBuilder.regexGuardrailScript(
                JavaScriptBuilder.toJson(List.of("\\w")),
                "allow",
                "retry",
                "Content did not match any allowed pattern.",
                3,
                "non_empty");
    }

    /** Evaluate the guardrail script with the given {@code $} input JSON, return the result Map. */
    private Map<?, ?> evaluate(String inputJson) {
        String wrapped =
                "var $ = " + inputJson + "; var __r = " + regexGuardrailScript() + "; JSON.stringify(__r);";
        Value v = graalCtx.eval("js", wrapped);
        try {
            return MAPPER.readValue(v.asString(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shortCircuitsToPassedWhenToolCallsPresentAndContentEmpty() {
        // Tool-call turn: result is [] but the LLM emitted tool calls.
        Map<?, ?> result =
                evaluate(
                        "{\"content\": [], \"iteration\": 1, \"toolCalls\": ["
                                + "{\"name\": \"search\", \"inputParameters\": {\"q\": \"x\"}}"
                                + "]}");
        assertThat(result.get("passed"))
                .as("tool-call turn (non-empty toolCalls, empty content) must pass the guardrail")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void stillFailsOnGenuineEmptyFinalReplyWhenNoToolCalls() {
        // Genuine final turn with no tool calls and empty content -> guardrail fails as before.
        Map<?, ?> result = evaluate("{\"content\": \"\", \"iteration\": 1, \"toolCalls\": []}");
        assertThat(result.get("passed"))
                .as("empty final reply with no tool calls must still fail the allow-pattern guardrail")
                .isEqualTo(Boolean.FALSE);
    }
}
