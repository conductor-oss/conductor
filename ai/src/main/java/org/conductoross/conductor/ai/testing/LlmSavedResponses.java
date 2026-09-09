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
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.conductoross.conductor.ai.model.FinishReason;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Portable request/response pairs with the model's history policy for playback. Credentials,
 * runtime IDs, and usage are deliberately absent.
 */
public record LlmSavedResponses(
        int schemaVersion, String scenario, List<Entry> entries, ModelSettings modelSettings) {
    public LlmSavedResponses(int schemaVersion, String scenario, List<Entry> entries) {
        this(schemaVersion, scenario, entries, null);
    }

    public record ModelSettings(String model, boolean supportsAssistantPrefill) {}

    public static final int SCHEMA_VERSION = 1;

    public LlmSavedResponses {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported LLM saved responses schema version: " + schemaVersion);
        }
        if (StringUtils.isBlank(scenario) || !scenario.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("Invalid LLM saved responses scenario name");
        }
        entries = List.copyOf(entries);
    }

    public record Entry(Request request, Response response) {
        public Entry {
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
                throw new IllegalArgumentException("Unsupported recorded message role");
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

    public record Completion(Message message, FinishReason finishReason) {
        public Completion {
            if (message == null || !"assistant".equals(message.role())) {
                throw new IllegalArgumentException("Completion must contain an assistant message");
            }
            Objects.requireNonNull(finishReason, "finishReason");
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
