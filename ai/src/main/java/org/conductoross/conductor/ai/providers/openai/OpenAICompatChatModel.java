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
package org.conductoross.conductor.ai.providers.openai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI {@link ChatModel} backed by the OpenAI-compatible Chat Completions API via OkHttp.
 *
 * <p>Works with any provider that implements the standard Chat Completions format (Perplexity,
 * Grok, Together AI, etc.).
 */
@Slf4j
public class OpenAICompatChatModel implements ChatModel {

    private final OpenAIChatCompletionsApi api;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAICompatChatModel(OpenAIChatCompletionsApi api) {
        this.api = api;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            ChatCompletionRequest request = buildRequest(prompt);
            ChatCompletionResult result = api.createChatCompletion(request);
            return toSpringChatResponse(result);
        } catch (IOException e) {
            throw new RuntimeException("Chat Completions API call failed: " + e.getMessage(), e);
        }
    }

    private ChatCompletionRequest buildRequest(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        ChatOptions options = prompt.getOptions();

        List<MessageItem> items = new ArrayList<>();
        for (Message msg : messages) {
            items.addAll(convertMessage(msg));
        }

        String model = options != null ? options.getModel() : null;
        Double temperature = options != null ? options.getTemperature() : null;
        Double topP = options != null ? options.getTopP() : null;
        Integer maxTokens = options != null ? options.getMaxTokens() : null;
        List<String> stop = options != null ? options.getStopSequences() : null;
        Double frequencyPenalty = options != null ? options.getFrequencyPenalty() : null;
        Double presencePenalty = options != null ? options.getPresencePenalty() : null;
        Integer topK = options != null ? options.getTopK() : null;

        List<ToolDef> tools = null;
        if (options instanceof ToolCallingChatOptions tcOpts
                && tcOpts.getToolCallbacks() != null
                && !tcOpts.getToolCallbacks().isEmpty()) {
            tools = new ArrayList<>();
            for (var cb : tcOpts.getToolCallbacks()) {
                var def = cb.getToolDefinition();
                // inputSchema() returns a JSON string — parse it to an object so Jackson
                // serializes it as a nested JSON object, not a quoted string
                Object parameters = parseSchemaToObject(def.inputSchema());
                tools.add(ToolDef.function(def.name(), def.description(), parameters));
            }
        }

        return ChatCompletionRequest.builder()
                .model(model)
                .messages(items)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .stop(stop != null && !stop.isEmpty() ? stop : null)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .topK(topK)
                .tools(tools != null && !tools.isEmpty() ? tools : null)
                .build();
    }

    private List<MessageItem> convertMessage(Message msg) {
        List<MessageItem> items = new ArrayList<>();
        switch (msg.getMessageType()) {
            case SYSTEM -> items.add(MessageItem.system(((SystemMessage) msg).getText()));
            case USER -> items.add(MessageItem.user(((UserMessage) msg).getText()));
            case ASSISTANT -> {
                AssistantMessage assistantMsg = (AssistantMessage) msg;
                if (assistantMsg.hasToolCalls()) {
                    List<ToolCallItem> toolCalls = new ArrayList<>();
                    for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                        toolCalls.add(
                                new ToolCallItem(
                                        tc.id(),
                                        "function",
                                        new FunctionCall(tc.name(), tc.arguments())));
                    }
                    items.add(MessageItem.assistant(assistantMsg.getText(), toolCalls));
                } else {
                    items.add(MessageItem.assistant(assistantMsg.getText()));
                }
            }
            case TOOL -> {
                ToolResponseMessage toolMsg = (ToolResponseMessage) msg;
                for (ToolResponseMessage.ToolResponse tr : toolMsg.getResponses()) {
                    items.add(MessageItem.tool(tr.id(), tr.responseData()));
                }
            }
            default -> log.warn("Unsupported message type: {}", msg.getMessageType());
        }
        return items;
    }

    private ChatResponse toSpringChatResponse(ChatCompletionResult result) {
        List<Generation> generations = new ArrayList<>();

        if (result.choices() != null) {
            for (Choice choice : result.choices()) {
                MessageItem msg = choice.message();
                String text = msg != null && msg.content() instanceof String s ? s : "";
                String finishReason = choice.finishReason();

                AssistantMessage assistantMessage;
                if (msg != null && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
                    for (ToolCallItem tc : msg.toolCalls()) {
                        toolCalls.add(
                                new AssistantMessage.ToolCall(
                                        tc.id(),
                                        "function",
                                        tc.function().name(),
                                        tc.function().arguments()));
                    }
                    assistantMessage =
                            AssistantMessage.builder().content(text).toolCalls(toolCalls).build();
                    finishReason = "TOOL_CALLS";
                } else {
                    assistantMessage = new AssistantMessage(text);
                }

                String mappedReason = mapFinishReason(finishReason);
                ChatGenerationMetadata genMeta =
                        ChatGenerationMetadata.builder().finishReason(mappedReason).build();
                generations.add(new Generation(assistantMessage, genMeta));
            }
        }

        Usage usage = result.usage();
        int inputTok = usage != null && usage.promptTokens() != null ? usage.promptTokens() : 0;
        int outputTok =
                usage != null && usage.completionTokens() != null ? usage.completionTokens() : 0;
        int totalTok = usage != null && usage.totalTokens() != null ? usage.totalTokens() : 0;
        DefaultUsage springUsage = new DefaultUsage(inputTok, outputTok, totalTok);

        ChatResponseMetadata metadata =
                ChatResponseMetadata.builder()
                        .id(result.id())
                        .model(result.model())
                        .usage(springUsage)
                        .build();

        return new ChatResponse(generations, metadata);
    }

    @SuppressWarnings("unchecked")
    private Object parseSchemaToObject(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Map.of("type", "object");
        }
        try {
            return objectMapper.readValue(schemaJson, Map.class);
        } catch (Exception e) {
            return Map.of("type", "object");
        }
    }

    private String mapFinishReason(String reason) {
        if (reason == null) return "STOP";
        return switch (reason) {
            case "stop" -> "STOP";
            case "length" -> "MAX_TOKENS";
            case "tool_calls" -> "TOOL_CALLS";
            case "content_filter" -> "CONTENT_FILTER";
            default -> reason.toUpperCase();
        };
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
