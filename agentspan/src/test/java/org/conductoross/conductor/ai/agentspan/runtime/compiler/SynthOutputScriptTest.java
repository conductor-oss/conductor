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

import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the post-loop output synthesizer that ensures the agent's workflow ``result`` is
 * non-empty even when the loop terminated on a TOOL_CALLS turn (the {@code stop_when} fired right
 * after the model called a writer tool, leaving the LLM's text result empty).
 *
 * <p>Without this synthesis, the explorer agent's output is {@code "[]"} and the downstream stage
 * sees nothing — the bug the user reported on workflow {@code 420d4c2f-...}. With it, the workflow
 * output carries a JSON dump of the last turn's tool-call inputs, surfacing the {@code content} arg
 * of {@code write_coder_plan} et al.
 */
class SynthOutputScriptTest {

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

    /** Mirror exactly what AgentCompiler.buildSynthesizeOutputTask emits. */
    private static final String SCRIPT =
            "(function(){"
                    + "  var txt = $.llm_result;"
                    + "  if (txt !== null && txt !== undefined && String(txt).trim() !== '' && String(txt).trim() !== '[]') {"
                    + "    return txt;"
                    + "  }"
                    + "  var tcs = $.tool_calls;"
                    + "  if (Array.isArray(tcs) && tcs.length > 0) {"
                    + "    var summary = [];"
                    + "    for (var i = 0; i < tcs.length; i++) {"
                    + "      var tc = tcs[i] || {};"
                    + "      summary.push({name: tc.name, inputs: tc.inputParameters || tc.inputs || {}});"
                    + "    }"
                    + "    try { return JSON.stringify(summary); } catch (e) { return String(summary); }"
                    + "  }"
                    + "  return txt || '';"
                    + "})()";

    private String run(String inputJson) {
        String wrapped =
                "var $ = "
                        + inputJson
                        + "; var __r = "
                        + SCRIPT
                        + "; JSON.stringify({result: __r});";
        Value v = graalCtx.eval("js", wrapped);
        try {
            Map<?, ?> m = MAPPER.readValue(v.asString(), Map.class);
            Object r = m.get("result");
            return r == null ? null : String.valueOf(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void prefersLlmTextWhenPresent() {
        String r = run("{\"llm_result\": \"hello world\", \"tool_calls\": null}");
        assertThat(r).isEqualTo("hello world");
    }

    @Test
    void fallsBackToToolCallsWhenLlmResultIsEmptyString() {
        String input =
                "{\"llm_result\": \"\", \"tool_calls\": ["
                        + "{\"name\": \"write_coder_plan\", \"inputParameters\": {\"content\": \"# plan\\n## step 1\"}}"
                        + "]}";
        String r = run(input);
        assertThat(r).contains("write_coder_plan");
        assertThat(r).contains("# plan");
    }

    @Test
    void fallsBackToToolCallsWhenLlmResultIsEmptyArray() {
        // The bug surface: AgentCompiler binds result to ${llm.output.result}
        // which can come back as the literal string "[]" when no text was
        // emitted. Treat that as empty.
        String input =
                "{\"llm_result\": \"[]\", \"tool_calls\": ["
                        + "{\"name\": \"write_coder_plan\", \"inputParameters\": {\"content\": \"plan body\"}}"
                        + "]}";
        String r = run(input);
        assertThat(r).contains("write_coder_plan");
        assertThat(r).contains("plan body");
    }

    @Test
    void summarizesMultipleToolCallsInOneTurn() {
        String input =
                "{\"llm_result\": null, \"tool_calls\": ["
                        + "{\"name\": \"write_coder_plan\", \"inputParameters\": {\"content\": \"plan\"}},"
                        + "{\"name\": \"contextbook_write\", \"inputParameters\": {\"section\": \"x\", \"content\": \"y\"}}"
                        + "]}";
        String r = run(input);
        assertThat(r).contains("write_coder_plan");
        assertThat(r).contains("contextbook_write");
        assertThat(r).contains("plan");
    }

    @Test
    void returnsEmptyStringWhenNothingToSynthesize() {
        String r = run("{\"llm_result\": null, \"tool_calls\": null}");
        assertThat(r).isIn("", null);
    }

    @Test
    void honorsAlternateInputsKey() {
        // The compiler may surface tool-call inputs under either
        // ``inputParameters`` (Conductor TaskDef shape) or ``inputs``
        // (LLM_CHAT_COMPLETE pre-enrich shape). Cover both.
        String input =
                "{\"llm_result\": \"\", \"tool_calls\": ["
                        + "{\"name\": \"write_coder_plan\", \"inputs\": {\"content\": \"alt\"}}"
                        + "]}";
        String r = run(input);
        assertThat(r).contains("alt");
    }
}
