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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the swarm transcript scripts. The critical contracts: a turn that ended on tool calls
 * (result {@code []}) must NOT pollute the conversation with {@code [agent]: []}, a handoff must be
 * annotated with the transfer message so the receiving agent sees the delegation intent, and the
 * transfer-message extraction must handle Java-Map-backed tool calls (the shape GraalJS sees at
 * runtime), not just JS-native objects.
 */
class SwarmScriptsTest {

    private Context graalCtx;

    @BeforeEach
    void setUp() {
        graalCtx = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        graalCtx.close();
    }

    private String evalConcat(String dollarJson) {
        String script = "var $ = " + dollarJson + "; " + JavaScriptBuilder.swarmConcatScript("ceo");
        return graalCtx.eval("js", script).asString();
    }

    // ── swarmConcatScript ───────────────────────────────────────────

    @Test
    void concatAppendsNormalResponse() {
        String out =
                evalConcat(
                        "{prev: 'start', response: 'built the API', is_transfer: false,"
                                + " transfer_to: '', transfer_message: ''}");
        assertThat(out).isEqualTo("start\n\n[ceo]: built the API");
    }

    @Test
    void concatSkipsToolCallOnlyTurnAndAnnotatesHandoff() {
        // finishReason=TOOL_CALLS turns produce result [] — must not become "[ceo]: []"
        String out =
                evalConcat(
                        "{prev: 'start', response: [], is_transfer: true,"
                                + " transfer_to: 'engineering_lead',"
                                + " transfer_message: 'Design the REST API'}");
        assertThat(out).doesNotContain("[ceo]: []");
        assertThat(out).isEqualTo("start\n\n[ceo -> engineering_lead]: Design the REST API");
    }

    @Test
    void concatSkipsEmptyTurnWithoutTransfer() {
        String out =
                evalConcat(
                        "{prev: 'start', response: [], is_transfer: false, transfer_to: '',"
                                + " transfer_message: ''}");
        assertThat(out).isEqualTo("start");
    }

    @Test
    void concatAppendsResponseAndBareHandoffMarkerWhenNoMessage() {
        String out =
                evalConcat(
                        "{prev: 'start', response: 'done my part', is_transfer: true,"
                                + " transfer_to: 'marketing_lead', transfer_message: null}");
        assertThat(out).isEqualTo("start\n\n[ceo]: done my part\n\n[ceo -> marketing_lead]");
    }

    @Test
    void concatHandlesStringifiedTransferFlag() {
        // SET_VARIABLE round-trips can stringify booleans
        String out =
                evalConcat(
                        "{prev: 'start', response: [], is_transfer: 'true',"
                                + " transfer_to: 'engineering_lead', transfer_message: ''}");
        assertThat(out).isEqualTo("start\n\n[ceo -> engineering_lead]");
    }

    // ── extractTransferMessageScript ────────────────────────────────

    private String evalExtract(Object toolCalls) {
        graalCtx.getBindings("js").putMember("javaCalls", toolCalls);
        String script =
                "var $ = {tool_calls: javaCalls}; "
                        + JavaScriptBuilder.extractTransferMessageScript();
        Value v = graalCtx.eval("js", script);
        return v.asString();
    }

    @Test
    void extractReturnsMessageFromJavaMapToolCall() {
        // Runtime shape: Java List of Java Maps (Conductor task output interop)
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("name", "ceo_transfer_to_engineering_lead");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("method", "ceo_transfer_to_engineering_lead");
        params.put("message", "Design the REST API");
        call.put("inputParameters", params);
        assertThat(evalExtract(List.of(call))).isEqualTo("Design the REST API");
    }

    @Test
    void extractTakesFirstTransferCallMatchingWorkerSelection() {
        // The SDK check_transfer worker is first-wins; the message must belong to that call
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "ceo_transfer_to_engineering_lead");
        first.put("inputParameters", Map.of("message", "eng task"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "ceo_transfer_to_marketing_lead");
        second.put("inputParameters", Map.of("message", "mkt task"));
        assertThat(evalExtract(List.of(first, second))).isEqualTo("eng task");
    }

    @Test
    void extractIgnoresNonTransferCalls() {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("name", "search_web");
        call.put("inputParameters", Map.of("query", "conductor"));
        assertThat(evalExtract(List.of(call))).isEmpty();
    }

    @Test
    void extractAllowlistDoesNotTreatTransferLookingUserToolAsAControl() {
        Map<String, Object> userTool = new LinkedHashMap<>();
        userTool.put("name", "search_transfer_to_engineering_notes");
        userTool.put("inputParameters", Map.of("message", "ordinary tool output"));
        graalCtx.getBindings("js").putMember("javaCalls", List.of(userTool));

        String script =
                "var $ = {tool_calls: javaCalls}; "
                        + JavaScriptBuilder.extractTransferMessageScript(
                                Map.of("ceo_transfer_to_engineering", "engineering"));

        assertThat(graalCtx.eval("js", script).asString()).isEmpty();
    }

    @Test
    void extractHandlesMissingMessageAndNullCalls() {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("name", "ceo_transfer_to_engineering_lead");
        call.put("inputParameters", Map.of("method", "ceo_transfer_to_engineering_lead"));
        assertThat(evalExtract(List.of(call))).isEmpty();

        String nullScript =
                "var $ = {tool_calls: null}; " + JavaScriptBuilder.extractTransferMessageScript();
        assertThat(graalCtx.eval("js", nullScript).asString()).isEmpty();
    }
}
