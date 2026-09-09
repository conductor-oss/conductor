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

import org.apache.commons.lang3.StringUtils;
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
import org.springframework.util.CollectionUtils;

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
            throw new IllegalArgumentException(
                    "LLM recordings require full history; previousResponseId is unsupported");
        }
        if (input.isWebSearch()
                || input.isCodeInterpreter()
                || input.isGoogleSearchRetrieval()
                || !CollectionUtils.isEmpty(input.getFileSearchVectorStoreIds())) {
            throw new IllegalArgumentException(
                    "Provider-native tools are unsupported in LLM recordings");
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
            if (!CollectionUtils.isEmpty(options.getToolNames())) {
                throw new IllegalArgumentException(
                        "LLM recordings require resolved tool definitions");
            }
            if (Boolean.TRUE.equals(options.getInternalToolExecutionEnabled())) {
                throw new IllegalArgumentException(
                        "LLM recordings require external tool execution");
            }
            if (!CollectionUtils.isEmpty(options.getToolCallbacks())) {
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

    public LlmSavedResponses.Response toSavedResponse(ChatResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Cannot record an absent model response");
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
            throw new IllegalArgumentException("Replay tool-call ID prefix must not be blank");
        }
        List<Generation> generations = new ArrayList<Generation>();
        for (LlmSavedResponses.Completion completion : response.completions()) {
            LlmSavedResponses.Message message = completion.message();
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (LlmSavedResponses.ToolCall call : message.toolCalls()) {
                String id = idPrefix + "_" + call.reference();
                if (!call.reference().equals(reference(id, call.name()))) {
                    throw new IllegalArgumentException(
                            "Recorded tool-call reference does not match request history");
                }
                calls.add(
                        new AssistantMessage.ToolCall(
                                id, "function", call.name(), call.arguments().toString()));
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
        if (message instanceof MediaContent media && !CollectionUtils.isEmpty(media.getMedia())) {
            throw new IllegalArgumentException("Media is unsupported in LLM recordings");
        }
        List<LlmSavedResponses.ToolCall> calls = new ArrayList<>();
        List<LlmSavedResponses.ToolResult> results = new ArrayList<>();
        if (message instanceof AssistantMessage assistant) {
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                if (!"function".equals(call.type())) {
                    throw new IllegalArgumentException(
                            "Only function tool calls are supported in LLM recordings");
                }
                calls.add(
                        new LlmSavedResponses.ToolCall(
                                reference(call.id(), call.name()),
                                call.name(),
                                parseObject(call.arguments(), "tool arguments")));
            }
        } else if (message instanceof ToolResponseMessage tool) {
            for (ToolResponseMessage.ToolResponse result : tool.getResponses()) {
                CallIdentity call = callIdentities.get(result.id());
                if (call == null || !result.name().equals(call.name())) {
                    throw new IllegalArgumentException(
                            "Tool result has no matching call in the recorded history");
                }
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
        if (!call.name().equals(name)) {
            throw new IllegalArgumentException("Tool-call ID was reused for a different tool");
        }
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
        throw new IllegalArgumentException("Expected a JSON object for " + field);
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
