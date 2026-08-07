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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that JOIN tool envelopes remain complete observations for the next LLM turn. */
class AgentToolJoinStateMergeScriptTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void appendsHttpAndMcpJoinEnvelopesWhilePreservingPriorResultsAndShallowMergingState()
            throws Exception {
        Map<String, Object> httpOutput =
                map(
                        "response",
                        map(
                                "statusCode",
                                200,
                                "body",
                                map("temperature", 64, "conditions", "clear")),
                        "_state_updates",
                        map("weatherLoaded", true, "region", "us-east"));
        Map<String, Object> mcpOutput =
                map(
                        "content",
                        List.of(map("type", "text", "text", "Forecast available")),
                        "isError",
                        false,
                        "_state_updates",
                        map("nextPage", "forecast-2", "region", "eu-west"));

        Map<String, Object> joinOutput = new LinkedHashMap<>();
        joinOutput.put(
                "weather_0__1",
                map("_agent_tool_name", "weather", "_agent_tool_output", httpOutput));
        joinOutput.put(
                "forecast_1__1",
                map("_agent_tool_name", "forecast", "_agent_tool_output", mcpOutput));

        Map<String, Object> result =
                evaluate(
                        map(
                                "currentState",
                                map("requestId", "request-1", "region", "us-west"),
                                "previousToolResults",
                                List.of(map("name", "catalog", "output", map("count", 3))),
                                "joinOutput",
                                joinOutput));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolResults =
                (List<Map<String, Object>>) result.get("toolResults");
        assertThat(toolResults)
                .containsExactly(
                        map("name", "catalog", "output", map("count", 3)),
                        map("name", "weather", "output", httpOutput),
                        map("name", "forecast", "output", mcpOutput));

        assertThat(result.get("mergedState"))
                .isEqualTo(
                        map(
                                "requestId",
                                "request-1",
                                "region",
                                "eu-west",
                                "weatherLoaded",
                                true,
                                "nextPage",
                                "forecast-2"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluate(Map<String, Object> inputs) throws Exception {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("inputsJson", MAPPER.writeValueAsString(inputs));
            String result =
                    context.eval(
                                    "js",
                                    "(function($) { return JSON.stringify("
                                            + JavaScriptBuilder.stateMergeScript()
                                            + "); })(JSON.parse(inputsJson));")
                            .asString();
            return MAPPER.readValue(result, Map.class);
        }
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }
}
