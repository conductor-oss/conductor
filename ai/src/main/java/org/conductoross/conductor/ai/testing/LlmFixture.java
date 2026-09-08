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
package org.conductoross.conductor.ai.testing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import com.fasterxml.jackson.databind.JsonNode;

/** Portable LLM turns. Runtime IDs, provider configuration, and usage are deliberately absent. */
public record LlmFixture(int schemaVersion, String scenario, Map<String, List<Turn>> streams) {
    public static final int SCHEMA_VERSION = 1;

    public LlmFixture {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported LLM fixture schema version: " + schemaVersion);
        }
        if (StringUtils.isBlank(scenario) || !scenario.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("Invalid LLM fixture scenario name");
        }
        var copy = new TreeMap<String, List<Turn>>();
        streams.forEach(
                (name, turns) -> {
                    if (StringUtils.isBlank(name)) {
                        throw new IllegalArgumentException("Fixture stream name must not be blank");
                    }
                    copy.put(name, List.copyOf(turns));
                });
        streams = java.util.Collections.unmodifiableMap(copy);
    }

    public record Turn(Request request, Response response) {
        public Turn {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(response, "response");
        }
    }

    public record Request(
            List<Message> messages, List<Tool> tools, boolean jsonOutput, JsonNode outputSchema) {
        public Request {
            messages = List.copyOf(messages);
            tools = List.copyOf(tools);
        }
    }

    public record Tool(String name, String description, JsonNode inputSchema) {
        public Tool {
            requireName(name);
            if (inputSchema == null || !inputSchema.isObject()) {
                throw new IllegalArgumentException("Tool input schema must be an object");
            }
        }
    }

    public record Message(
            String role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        public Message {
            toolCalls = List.copyOf(toolCalls);
            toolResults = List.copyOf(toolResults);
            if (!Strings.CS.equalsAny(role, "system", "user", "assistant", "tool")) {
                throw new IllegalArgumentException("Unsupported fixture message role");
            }
            if ((!toolCalls.isEmpty() && !"assistant".equals(role))
                    || (!toolResults.isEmpty() && !"tool".equals(role))) {
                throw new IllegalArgumentException("Tool calls/results do not match message role");
            }
        }
    }

    public record ToolCall(String reference, String name, JsonNode arguments) {
        public ToolCall {
            requireReference(reference);
            requireName(name);
            if (arguments == null || !arguments.isObject()) {
                throw new IllegalArgumentException("Tool arguments must be an object");
            }
        }
    }

    public record ToolResult(String reference, String name, JsonNode value) {
        public ToolResult {
            requireReference(reference);
            requireName(name);
        }
    }

    public record Completion(Message message, String finishReason) {
        public Completion {
            if (message == null || !"assistant".equals(message.role())) {
                throw new IllegalArgumentException("Completion must contain an assistant message");
            }
            if (!Strings.CS.equalsAny(
                    finishReason, "STOP", "TOOL_CALLS", "MAX_TOKENS", "CONTENT_FILTER")) {
                throw new IllegalArgumentException("Unsupported fixture finish reason");
            }
        }
    }

    public record Response(List<Completion> completions) {
        public Response {
            completions = List.copyOf(completions);
            if (completions.isEmpty()) {
                throw new IllegalArgumentException("A recorded response must contain a completion");
            }
        }
    }

    private static void requireName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
    }

    private static void requireReference(String reference) {
        if (StringUtils.isBlank(reference) || !reference.matches("call_(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("Invalid logical tool-call reference");
        }
    }
}
