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
import org.springframework.ai.chat.messages.MessageType;

import com.fasterxml.jackson.databind.JsonNode;

/** Normalized requests and complete model responses with the history policy for playback. */
public record LlmSavedResponses(
        int schemaVersion, String scenario, List<Entry> entries, ModelSettings modelSettings) {
    private static final String MISMATCHED_MESSAGE_ROLE =
            "Tool calls/results do not match message role";

    public LlmSavedResponses(int schemaVersion, String scenario, List<Entry> entries) {
        this(schemaVersion, scenario, entries, null);
    }

    public record ModelSettings(String model, boolean supportsAssistantPrefill) {}

    public static final int SCHEMA_VERSION = 2;

    public LlmSavedResponses {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported LLM saved responses schema version: " + schemaVersion);
        }
        Validate.isTrue(
                StringUtils.isNotBlank(scenario)
                        && scenario.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}"),
                "Invalid LLM saved responses scenario name");
        entries = List.copyOf(entries);
    }

    public record Entry(Request request, JsonNode response) {
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
            Validate.isTrue(
                    inputSchema != null && inputSchema.isObject(),
                    "Tool input schema must be an object");
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
                    "Unsupported recorded message role");
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
            Validate.isTrue(
                    arguments != null && arguments.isObject(), "Tool arguments must be an object");
        }
    }

    public record ToolResult(String reference, String name, JsonNode value) {
        public ToolResult {
            requireReference(reference);
            requireName(name);
        }
    }

    private static void requireName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
    }

    private static void requireReference(String reference) {
        Validate.isTrue(
                StringUtils.isNotBlank(reference) && reference.matches("call_(0|[1-9][0-9]*)"),
                "Invalid logical tool-call reference");
    }
}
