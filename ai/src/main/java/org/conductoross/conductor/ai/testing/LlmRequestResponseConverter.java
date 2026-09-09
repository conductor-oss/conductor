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
import java.util.TreeMap;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.FinishReason;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Normalizes only declared transport fields, never arbitrary user payload keys or prompt text.
 * Create one instance per request/response pair to normalize IDs from its full history.
 */
public final class LlmRequestResponseConverter {
    private static final String PREVIOUS_RESPONSE_UNSUPPORTED =
            "LLM recordings require full history; previousResponseId is unsupported";
    private static final String NATIVE_TOOLS_UNSUPPORTED =
            "Provider-native tools are unsupported in LLM recordings";
    private static final String UNRESOLVED_TOOLS =
            "LLM recordings require resolved tool definitions";
    private static final String INTERNAL_TOOL_EXECUTION_UNSUPPORTED =
            "LLM recordings require external tool execution";
    private static final String TOOL_INPUT_SCHEMA_FIELD = "tool input schema";
    private static final String MISSING_MODEL_RESPONSE = "Cannot record an absent model response";
    private static final String MISSING_REPLAY_ID_PREFIX =
            "Replay tool-call ID prefix must not be blank";
    private static final String TOOL_ID_SEPARATOR = "_";
    private static final String MISMATCHED_TOOL_REFERENCE =
            "Recorded tool-call reference does not match request history";
    public static final String FUNCTION_TOOL_TYPE = "function";
    private static final String MEDIA_UNSUPPORTED = "Media is unsupported in LLM recordings";
    private static final String UNSUPPORTED_TOOL_TYPE =
            "Only function tool calls are supported in LLM recordings";
    private static final String TOOL_ARGUMENTS_FIELD = "tool arguments";
    private static final String UNMATCHED_TOOL_RESULT =
            "Tool result has no matching call in the recorded history";
    private static final String MISSING_TOOL_IDENTITY = "Recorded tool calls require a name and ID";
    private static final String TOOL_REFERENCE_PREFIX = "call_";
    private static final String REUSED_TOOL_ID = "Tool-call ID was reused for a different tool";
    private static final String EXPECTED_JSON_OBJECT = "Expected a JSON object for ";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final Map<String, CallIdentity> callIdentities = new HashMap<>();

    private record CallIdentity(String reference, String name) {}

    public LlmRequestResponseConverter() {}

    public LlmSavedResponses.Request toSavedRequest(Prompt prompt, ChatCompletion input) {
        return toSavedRequest(prompt, options(input));
    }

    public record RequestOptions(boolean jsonOutput, JsonNode outputSchema) {}

    public static RequestOptions options(ChatCompletion input) {
        if (StringUtils.isNotBlank(input.getPreviousResponseId())) {
            throw new IllegalArgumentException(PREVIOUS_RESPONSE_UNSUPPORTED);
        }
        if (input.isWebSearch()
                || input.isCodeInterpreter()
                || input.isGoogleSearchRetrieval()
                || ObjectUtils.isNotEmpty(input.getFileSearchVectorStoreIds())) {
            throw new IllegalArgumentException(NATIVE_TOOLS_UNSUPPORTED);
        }
        // Capture only recording constraints; messages and tools come from the effective prompt.
        return new RequestOptions(
                input.isJsonOutput(), canonical(MAPPER.valueToTree(input.getOutputSchema())));
    }

    public LlmSavedResponses.Request toSavedRequest(Prompt prompt, RequestOptions input) {
        List<LlmSavedResponses.Message> messages =
                prompt.getInstructions().stream().map(this::toSavedMessage).toList();
        List<LlmSavedResponses.Tool> tools = new ArrayList<LlmSavedResponses.Tool>();
        // Read the effective tool catalog passed to the model, not the original task definition.
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            if (ObjectUtils.isNotEmpty(options.getToolNames())) {
                throw new IllegalArgumentException(UNRESOLVED_TOOLS);
            }
            if (Boolean.TRUE.equals(options.getInternalToolExecutionEnabled())) {
                throw new IllegalArgumentException(INTERNAL_TOOL_EXECUTION_UNSUPPORTED);
            }
            if (ObjectUtils.isNotEmpty(options.getToolCallbacks())) {
                for (ToolCallback callback : options.getToolCallbacks()) {
                    ToolDefinition definition = callback.getToolDefinition();
                    tools.add(
                            new LlmSavedResponses.Tool(
                                    definition.name(),
                                    definition.description(),
                                    parseObject(
                                            definition.inputSchema(), TOOL_INPUT_SCHEMA_FIELD)));
                }
            }
        }
        return new LlmSavedResponses.Request(
                messages, tools, input.jsonOutput(), input.outputSchema());
    }

    public LlmSavedResponses.Response toSavedResponse(ChatResponse response) {
        if (response == null) {
            throw new IllegalArgumentException(MISSING_MODEL_RESPONSE);
        }
        return new LlmSavedResponses.Response(
                response.getResults().stream()
                        .map(
                                generation ->
                                        new LlmSavedResponses.Completion(
                                                toSavedMessage(generation.getOutput()),
                                                FinishReason.fromProvider(
                                                        generation
                                                                .getMetadata()
                                                                .getFinishReason())))
                        .toList());
    }

    /** Reconstruct a response before Conductor's normal tool conversion and JSON validation. */
    public ChatResponse toChatResponse(LlmSavedResponses.Response response, String idPrefix) {
        if (StringUtils.isBlank(idPrefix)) {
            throw new IllegalArgumentException(MISSING_REPLAY_ID_PREFIX);
        }
        List<Generation> generations = new ArrayList<Generation>();
        for (LlmSavedResponses.Completion completion : response.completions()) {
            LlmSavedResponses.Message message = completion.message();
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (LlmSavedResponses.ToolCall call : message.toolCalls()) {
                String id = idPrefix + TOOL_ID_SEPARATOR + call.reference();
                Validate.isTrue(
                        call.reference().equals(reference(id, call.name())),
                        MISMATCHED_TOOL_REFERENCE);
                calls.add(
                        new AssistantMessage.ToolCall(
                                id, FUNCTION_TOOL_TYPE, call.name(), call.arguments().toString()));
            }
            generations.add(
                    new Generation(
                            AssistantMessage.builder()
                                    .content(message.text())
                                    .toolCalls(calls)
                                    .build(),
                            ChatGenerationMetadata.builder()
                                    .finishReason(completion.finishReason().name())
                                    .build()));
        }
        // Default Spring AI metadata supplies empty/zero usage, with no provider response ID.
        return new ChatResponse(generations);
    }

    private LlmSavedResponses.Message toSavedMessage(Message message) {
        if (message instanceof MediaContent media && ObjectUtils.isNotEmpty(media.getMedia())) {
            throw new IllegalArgumentException(MEDIA_UNSUPPORTED);
        }
        List<LlmSavedResponses.ToolCall> calls = new ArrayList<>();
        List<LlmSavedResponses.ToolResult> results = new ArrayList<>();
        if (message instanceof AssistantMessage assistant) {
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                Validate.isTrue(FUNCTION_TOOL_TYPE.equals(call.type()), UNSUPPORTED_TOOL_TYPE);
                calls.add(
                        new LlmSavedResponses.ToolCall(
                                reference(call.id(), call.name()),
                                call.name(),
                                parseObject(call.arguments(), TOOL_ARGUMENTS_FIELD)));
            }
        } else if (message instanceof ToolResponseMessage tool) {
            for (ToolResponseMessage.ToolResponse result : tool.getResponses()) {
                CallIdentity call = callIdentities.get(result.id());
                Validate.isTrue(
                        call != null && result.name().equals(call.name()), UNMATCHED_TOOL_RESULT);
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
            throw new IllegalArgumentException(MISSING_TOOL_IDENTITY);
        }
        CallIdentity call =
                callIdentities.computeIfAbsent(
                        id,
                        ignored ->
                                new CallIdentity(
                                        TOOL_REFERENCE_PREFIX + callIdentities.size(), name));
        Validate.isTrue(call.name().equals(name), REUSED_TOOL_ID);
        return call.reference();
    }

    private static JsonNode parseObject(String json, String field) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node != null && node.isObject()) {
                return canonical(node);
            }
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            // Report the contract field, not a potentially sensitive payload or parser exception.
        }
        throw new IllegalArgumentException(EXPECTED_JSON_OBJECT + field);
    }

    private static JsonNode parseResult(String value) {
        if (value == null) return NullNode.instance;
        try {
            JsonNode parsed = MAPPER.readTree(value);
            if (parsed != null) return canonical(parsed);
        } catch (JsonProcessingException ignored) {
            // Tool results can also be plain text. Preserve their exact content.
        }
        return TextNode.valueOf(value);
    }

    private static JsonNode canonical(JsonNode node) {
        if (node == null || node.isNull()) return NullNode.instance;
        if (node.isObject()) {
            Map<String, JsonNode> fields = new TreeMap<String, JsonNode>();
            node.fields()
                    .forEachRemaining(
                            field -> fields.put(field.getKey(), canonical(field.getValue())));
            ObjectNode result = MAPPER.createObjectNode();
            fields.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            node.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return node.deepCopy();
    }
}
