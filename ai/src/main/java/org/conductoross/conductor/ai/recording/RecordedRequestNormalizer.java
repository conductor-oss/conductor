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
package org.conductoross.conductor.ai.recording;

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
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Normalizes only declared transport fields, never arbitrary user payload keys or prompt text.
 * Create one instance per request to normalize IDs from its full history.
 */
public final class RecordedRequestNormalizer {
    public static final String FUNCTION_TOOL_TYPE = "function";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final Map<String, CallIdentity> callIdentities = new HashMap<>();

    private record CallIdentity(String reference, String name) {}

    public LLMRecording.Request normalize(Prompt prompt, ChatCompletion input) {
        return normalize(prompt, options(input));
    }

    public record RequestOptions(
            boolean jsonOutput,
            JsonNode outputSchema,
            List<LLMRecording.Tool> tools,
            LLMRecording.GenerationOptions generationOptions) {}

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
        List<LLMRecording.Tool> tools = new ArrayList<>();
        if (input.getTools() != null) {
            for (ToolSpec tool : input.getTools()) {
                tools.add(
                        new LLMRecording.Tool(
                                tool.getName(),
                                tool.getDescription(),
                                MAPPER.valueToTree(tool.getInputSchema())));
            }
        }
        return new RequestOptions(
                input.isJsonOutput(),
                MAPPER.valueToTree(input.getOutputSchema()),
                List.copyOf(tools),
                new LLMRecording.GenerationOptions(
                        input.getTemperature(),
                        input.getTopP(),
                        input.getTopK(),
                        input.getFrequencyPenalty(),
                        input.getPresencePenalty(),
                        input.getStopWords(),
                        input.getMaxTokens(),
                        input.getThinkingTokenLimit(),
                        input.getReasoningEffort(),
                        input.getReasoningSummary()));
    }

    public LLMRecording.Request normalize(Prompt prompt, RequestOptions input) {
        List<LLMRecording.Message> messages =
                prompt.getInstructions().stream().map(this::toSavedMessage).toList();
        List<LLMRecording.Tool> tools = input.tools();
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
                            new LLMRecording.Tool(
                                    definition.name(),
                                    definition.description(),
                                    parseObject(definition.inputSchema(), "tool input schema")));
                }
            }
        }
        return new LLMRecording.Request(
                messages,
                tools,
                input.jsonOutput(),
                input.outputSchema(),
                input.generationOptions());
    }

    private LLMRecording.Message toSavedMessage(Message message) {
        if (message instanceof MediaContent media && ObjectUtils.isNotEmpty(media.getMedia())) {
            throw new IllegalArgumentException("Media is unsupported in LLM recordings");
        }
        List<LLMRecording.ToolCall> calls = new ArrayList<>();
        List<LLMRecording.ToolResult> results = new ArrayList<>();
        if (message instanceof AssistantMessage assistant) {
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                Validate.isTrue(
                        FUNCTION_TOOL_TYPE.equals(call.type()),
                        "Only function tool calls are supported in LLM recordings");
                calls.add(
                        new LLMRecording.ToolCall(
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
                        new LLMRecording.ToolResult(
                                call.reference(),
                                result.name(),
                                parseResult(result.responseData())));
            }
        }
        return new LLMRecording.Message(
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
