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
package org.conductoross.conductor.ai.agent.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a tool call becomes a task: what it is called, and what it is given.
 *
 * <p>Shared by both dispatchers so a tool behaves the same whether it runs in the agent's own
 * workflow or a child of it - the naming is what lets results be matched back to the call that
 * asked, and a difference between the two would only show up as a mismatched result.
 */
public final class AgentToolNaming {

    private AgentToolNaming() {}

    /** Where a tool task records the call it answers, so its output can be routed back. */
    public static final String TOOL_CALL_ID = "_toolCallId";

    /**
     * Separates a turn and a call from the agent's own reference name.
     *
     * <p>The engine's own loop delimiter, so a second round reads as another round of the same
     * thing rather than as an unrelated task. Rounds must not collide: a repeated reference name is
     * dropped silently.
     */
    public static final String TURN_PREFIX = "__t";

    /** Separates the turn from the call within it. */
    public static final String CALL_PREFIX = "__";

    /** {@code agent_ref__t1__call_abc} - the agent, the turn, and the call it belongs to. */
    public static String referenceName(String taskRefName, int turn, String toolCallId) {
        return turnPrefix(taskRefName, turn) + sanitize(toolCallId);
    }

    /** Everything a turn's tasks share, which is how they are found again. */
    public static String turnPrefix(String taskRefName, int turn) {
        return taskRefName + TURN_PREFIX + turn + CALL_PREFIX;
    }

    /** The turn a reference name belongs to, or null when it is not one of this agent's. */
    public static Integer turnOf(String referenceName, String taskRefName) {
        String prefix = taskRefName + TURN_PREFIX;
        if (referenceName == null || !referenceName.startsWith(prefix)) {
            return null;
        }
        String rest = referenceName.substring(prefix.length());
        int end = rest.indexOf(CALL_PREFIX);
        if (end < 0) {
            return null;
        }
        try {
            return Integer.parseInt(rest.substring(0, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A tool runs as a task of its own name, so a worker already registered for {@code get_revenue}
     * serves it with no further configuration.
     */
    public static String taskNameFor(Map<String, String> overrides, String toolName) {
        return overrides == null ? toolName : overrides.getOrDefault(toolName, toolName);
    }

    /** The tool's arguments, plus what the result needs to find its way back. */
    public static Map<String, Object> toolInput(
            Map<String, Object> toolCall, String toolCallId, String toolName, String executionId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.putAll(AgentToolArguments.parse(toolCall.get("arguments")));
        input.put(TOOL_CALL_ID, toolCallId);
        input.put("_toolName", toolName);
        input.put("_agentExecutionId", executionId);
        return input;
    }

    /** Reference names are constrained; a provider's call id is not. */
    public static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
