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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ToolSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Normalizes only declared transport fields, never arbitrary user payload keys or prompt text.
 * Create one instance per request/response pair to normalize IDs from its full history.
 */
public final class LlmRequestResponseConverter {
    public static final String FUNCTION_TOOL_TYPE = "function";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final Map<String, CallIdentity> callIdentities = new HashMap<>();

    private record CallIdentity(String reference, String name) {}

    public LlmSavedResponses.Request toSavedRequest(Prompt prompt, ChatCompletion input) {
        return toSavedRequest(prompt, options(input));
    }

    public record RequestOptions(
            boolean jsonOutput, JsonNode outputSchema, List<LlmSavedResponses.Tool> tools) {}

    public static RequestOptions options(ChatCompletion input) {
        if (StringUtils.isNotBlank(input.getPreviousResponseId())) {
            throw new IllegalArgumentException(
                    "LLM recordings require full history; previousResponseId is unsupported");
        }
        if (input.isWebSearch()
                || input.isCodeInterpreter()
                || input.isGoogleSearchRetrieval()
                || ObjectUtils.isNotEmpty(input.getFileSearchVectorStoreIds())) {
            throw new IllegalArgumentException(
                    "Provider-native tools are unsupported in LLM recordings");
        }
        // Providers with custom ChatOptions carry these same Conductor tool definitions.
        // Snapshot them now so later task mutations cannot change this call's recording.
        List<LlmSavedResponses.Tool> tools = new ArrayList<>();
        if (input.getTools() != null) {
            for (ToolSpec tool : input.getTools()) {
                tools.add(
                        new LlmSavedResponses.Tool(
                                tool.getName(),
                                tool.getDescription(),
                                MAPPER.valueToTree(tool.getInputSchema())));
            }
        }
        return new RequestOptions(
                input.isJsonOutput(),
                MAPPER.valueToTree(input.getOutputSchema()),
                List.copyOf(tools));
    }

    public LlmSavedResponses.Request toSavedRequest(Prompt prompt, RequestOptions input) {
        List<LlmSavedResponses.Message> messages =
                prompt.getInstructions().stream().map(this::toSavedMessage).toList();
        List<LlmSavedResponses.Tool> tools = input.tools();
        // Prefer resolved callbacks when the provider exposes them through Spring AI options.
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            tools = new ArrayList<>();
            if (ObjectUtils.isNotEmpty(options.getToolNames())) {
                throw new IllegalArgumentException(
                        "LLM recordings require resolved tool definitions");
            }
            if (Boolean.TRUE.equals(options.getInternalToolExecutionEnabled())) {
                throw new IllegalArgumentException(
                        "LLM recordings require external tool execution");
            }
            if (ObjectUtils.isNotEmpty(options.getToolCallbacks())) {
                for (ToolCallback callback : options.getToolCallbacks()) {
                    ToolDefinition definition = callback.getToolDefinition();
                    tools.add(
                            new LlmSavedResponses.Tool(
                                    definition.name(),
                                    definition.description(),
                                    parseObject(definition.inputSchema(), "tool input schema")));
                }
            }
        }
        return new LlmSavedResponses.Request(
                messages, tools, input.jsonOutput(), input.outputSchema());
    }

    public JsonNode toSavedResponse(ChatResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Cannot record an absent model response");
        }
        return LlmChatResponseJson.write(response);
    }

    /** Restore all response data, replacing only tool-call IDs for this playback invocation. */
    public ChatResponse toChatResponse(JsonNode data, String idPrefix) {
        return LlmChatResponseJson.read(data, idPrefix);
    }

    /** Ignore per-call IDs and usage when checking repeated recordings for conflicting answers. */
    public static JsonNode responseContent(JsonNode response) {
        JsonNode results = response.get("results").deepCopy();
        int callIndex = 0;
        for (JsonNode result : results) {
            for (JsonNode call : result.get("output").get("toolCalls")) {
                ((ObjectNode) call).put("id", "call_" + callIndex++);
            }
        }
        return results;
    }

    private LlmSavedResponses.Message toSavedMessage(Message message) {
        if (message instanceof MediaContent media && ObjectUtils.isNotEmpty(media.getMedia())) {
            throw new IllegalArgumentException("Media is unsupported in LLM recordings");
        }
        List<LlmSavedResponses.ToolCall> calls = new ArrayList<>();
        List<LlmSavedResponses.ToolResult> results = new ArrayList<>();
        if (message instanceof AssistantMessage assistant) {
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                Validate.isTrue(
                        FUNCTION_TOOL_TYPE.equals(call.type()),
                        "Only function tool calls are supported in LLM recordings");
                calls.add(
                        new LlmSavedResponses.ToolCall(
                                reference(call.id(), call.name()),
                                call.name(),
                                parseObject(call.arguments(), "tool arguments")));
            }
        } else if (message instanceof ToolResponseMessage tool) {
            for (ToolResponseMessage.ToolResponse result : tool.getResponses()) {
                CallIdentity call = callIdentities.get(result.id());
                Validate.isTrue(
                        call != null && result.name().equals(call.name()),
                        "Tool result has no matching call in the recorded history");
                results.add(
                        new LlmSavedResponses.ToolResult(
                                call.reference(),
                                result.name(),
                                parseResult(result.responseData())));
            }
        }
        return new LlmSavedResponses.Message(
                message.getMessageType().getValue(), message.getText(), calls, results);
    }

    private String reference(String id, String name) {
        if (StringUtils.isAnyBlank(id, name)) {
            throw new IllegalArgumentException("Recorded tool calls require a name and ID");
        }
        CallIdentity call =
                callIdentities.computeIfAbsent(
                        id, ignored -> new CallIdentity("call_" + callIdentities.size(), name));
        Validate.isTrue(call.name().equals(name), "Tool-call ID was reused for a different tool");
        return call.reference();
    }

    private static JsonNode parseObject(String json, String field) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            // Report the contract field, not a potentially sensitive payload or parser exception.
        }
        throw new IllegalArgumentException("Expected a JSON object for " + field);
    }

    private static JsonNode parseResult(String value) {
        if (value == null) return NullNode.instance;
        try {
            JsonNode parsed = MAPPER.readTree(value);
            if (parsed != null) return parsed;
        } catch (JsonProcessingException ignored) {
            // Tool results can also be plain text. Preserve their exact content.
        }
        return TextNode.valueOf(value);
    }
}
