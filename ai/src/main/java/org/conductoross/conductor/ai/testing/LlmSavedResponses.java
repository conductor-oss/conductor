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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.Validate;
import org.conductoross.conductor.ai.model.FinishReason;
import org.springframework.ai.chat.messages.MessageType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Portable request/response pairs with the model's history policy for playback. Credentials,
 * runtime IDs, and usage are deliberately absent.
 */
public record LlmSavedResponses(
        int schemaVersion, String scenario, List<Entry> entries, ModelSettings modelSettings) {
    private static final String UNSUPPORTED_SCHEMA_VERSION =
            "Unsupported LLM saved responses schema version: ";
    private static final String SCENARIO_NAME_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}";
    private static final String INVALID_SCENARIO_NAME = "Invalid LLM saved responses scenario name";
    private static final String REQUEST_FIELD = "request";
    private static final String RESPONSE_FIELD = "response";
    private static final String INVALID_TOOL_SCHEMA = "Tool input schema must be an object";
    private static final String UNSUPPORTED_MESSAGE_ROLE = "Unsupported recorded message role";
    private static final String MISMATCHED_MESSAGE_ROLE =
            "Tool calls/results do not match message role";
    private static final String INVALID_TOOL_ARGUMENTS = "Tool arguments must be an object";
    private static final String INVALID_COMPLETION = "Completion must contain an assistant message";
    private static final String FINISH_REASON_FIELD = "finishReason";
    private static final String EMPTY_RESPONSE = "A recorded response must contain a completion";
    private static final String MISSING_TOOL_NAME = "Tool name must not be blank";
    private static final String TOOL_REFERENCE_PATTERN = "call_(0|[1-9][0-9]*)";
    private static final String INVALID_TOOL_REFERENCE = "Invalid logical tool-call reference";

    public LlmSavedResponses(int schemaVersion, String scenario, List<Entry> entries) {
        this(schemaVersion, scenario, entries, null);
    }

    public record ModelSettings(String model, boolean supportsAssistantPrefill) {}

    public static final int SCHEMA_VERSION = 1;

    public LlmSavedResponses {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(UNSUPPORTED_SCHEMA_VERSION + schemaVersion);
        }
        Validate.isTrue(
                StringUtils.isNotBlank(scenario) && scenario.matches(SCENARIO_NAME_PATTERN),
                INVALID_SCENARIO_NAME);
        entries = List.copyOf(entries);
    }

    public record Entry(Request request, Response response) {
        public Entry {
            Objects.requireNonNull(request, REQUEST_FIELD);
            Objects.requireNonNull(response, RESPONSE_FIELD);
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
            Validate.isTrue(inputSchema != null && inputSchema.isObject(), INVALID_TOOL_SCHEMA);
        }
    }

    public record Message(
            String role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        public Message {
            toolCalls = List.copyOf(toolCalls);
            toolResults = List.copyOf(toolResults);
            Validate.isTrue(
                    Strings.CS.equalsAny(
                            role,
                            MessageType.SYSTEM.getValue(),
                            MessageType.USER.getValue(),
                            MessageType.ASSISTANT.getValue(),
                            MessageType.TOOL.getValue()),
                    UNSUPPORTED_MESSAGE_ROLE);
            if (ObjectUtils.isNotEmpty(toolCalls)) {
                Validate.isTrue(
                        MessageType.ASSISTANT.getValue().equals(role), MISMATCHED_MESSAGE_ROLE);
            }
            if (ObjectUtils.isNotEmpty(toolResults)) {
                Validate.isTrue(MessageType.TOOL.getValue().equals(role), MISMATCHED_MESSAGE_ROLE);
            }
        }
    }

    public record ToolCall(String reference, String name, JsonNode arguments) {
        public ToolCall {
            requireReference(reference);
            requireName(name);
            Validate.isTrue(arguments != null && arguments.isObject(), INVALID_TOOL_ARGUMENTS);
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
            Validate.isTrue(
                    message != null && MessageType.ASSISTANT.getValue().equals(message.role()),
                    INVALID_COMPLETION);
            Objects.requireNonNull(finishReason, FINISH_REASON_FIELD);
        }
    }

    public record Response(List<Completion> completions) {
        public Response {
            completions = List.copyOf(completions);
            if (completions.isEmpty()) {
                throw new IllegalArgumentException(EMPTY_RESPONSE);
            }
        }
    }

    private static void requireName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException(MISSING_TOOL_NAME);
        }
    }

    private static void requireReference(String reference) {
        Validate.isTrue(
                StringUtils.isNotBlank(reference) && reference.matches(TOOL_REFERENCE_PATTERN),
                INVALID_TOOL_REFERENCE);
    }
}
