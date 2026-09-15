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
package org.conductoross.conductor.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts a respond body into the two shapes agent runtimes ask for.
 *
 * <p>Exists because every client needs this and calling {@code Map.toString()} on the body — which
 * several did — puts {@code {result=the user's text}} on the wire instead of the text or its JSON.
 */
public final class AgentBodies {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentBodies() {}

    /** The body as JSON, for a tool-result payload. */
    public static String toJson(ConductorAgentRespondRequest request) {
        return request.getBody() != null ? MAPPER.valueToTree(request.getBody()).toString() : "{}";
    }

    /**
     * Tool results keyed by {@code tool_call_id}, ready to submit.
     *
     * <p>Two ways to answer a turn. {@code toolResults} names a result per call and is the only way
     * to answer a turn that asked for several tools. {@code body} answers a turn that asked for
     * exactly one — the shape the agent delegate produces from a resumed prompt.
     *
     * <p>A turn with several outstanding calls and only a {@code body} is rejected rather than
     * having that one result replayed against every call: the provider would accept it and the
     * model would reason from an answer to a question no tool was asked.
     *
     * @param outstandingToolCallIds the calls the run is actually blocked on, in provider order
     */
    public static Map<String, String> toolResults(
            ConductorAgentRespondRequest request, List<String> outstandingToolCallIds) {
        Map<String, String> results = new LinkedHashMap<>();
        Map<String, Object> keyed = request.getToolResults();
        if (keyed != null && !keyed.isEmpty()) {
            keyed.forEach((toolCallId, result) -> results.put(toolCallId, asJson(result)));
            return results;
        }
        if (outstandingToolCallIds.size() == 1) {
            results.put(outstandingToolCallIds.get(0), toJson(request));
            return results;
        }
        throw new IllegalArgumentException(
                "This turn is waiting on "
                        + outstandingToolCallIds.size()
                        + " tool calls "
                        + outstandingToolCallIds
                        + ", so a single result is ambiguous. Set toolResults keyed by tool_call_id.");
    }

    private static String asJson(Object value) {
        if (value == null) {
            return "{}";
        }
        return value instanceof CharSequence text
                ? text.toString()
                : MAPPER.valueToTree(value).toString();
    }

    /**
     * The body as conversational text, for continuing a thread. A resumed prompt arrives as {@code
     * {"result": "<text>"}} from the agent delegate, so that text is unwrapped; anything else goes
     * across as JSON.
     */
    public static String toMessage(ConductorAgentRespondRequest request) {
        if (request.getBody() == null) {
            return "";
        }
        Object result = request.getBody().get("result");
        if (result instanceof CharSequence text) {
            return text.toString();
        }
        return MAPPER.valueToTree(request.getBody()).toString();
    }
}
