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
package org.conductoross.conductor.ai.providers.anthropic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ContentBlock;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.Message;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesRequest;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesResponse;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseContentBlock;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseUsage;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.Thinking;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
 * Spring AI {@link ChatModel} implementation backed by the Anthropic Messages API via OkHttp.
 *
 * <p>Converts Spring AI {@link Prompt} messages to Anthropic Messages API format, calls the API,
 * and converts the response back to Spring AI's {@link ChatResponse}.
 */
@Slf4j
public class AnthropicChatModel implements ChatModel {

    private final AnthropicMessagesApi messagesApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicChatModel(AnthropicMessagesApi messagesApi) {
        this.messagesApi = messagesApi;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            MessagesRequest request = buildRequest(prompt);
            MessagesResponse result = messagesApi.createMessage(request);
            return toSpringChatResponse(result);
        } catch (IOException e) {
            throw new RuntimeException("Anthropic Messages API call failed: " + e.getMessage(), e);
        }
    }

    private MessagesRequest buildRequest(Prompt prompt) {
        List<org.springframework.ai.chat.messages.Message> springMessages =
                prompt.getInstructions();
        ChatOptions options = prompt.getOptions();

        // Extract system messages
        String system = null;
        List<org.springframework.ai.chat.messages.Message> nonSystemMessages = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message msg : springMessages) {
            if (msg.getMessageType() == MessageType.SYSTEM) {
                String sysText = ((SystemMessage) msg).getText();
                system = system == null ? sysText : system + "\n" + sysText;
            } else {
                nonSystemMessages.add(msg);
            }
        }

        // Convert messages to Anthropic format
        List<Message> messages = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message msg : nonSystemMessages) {
            messages.addAll(convertMessage(msg));
        }

        // Extract options
        AnthropicChatOptions opts = options instanceof AnthropicChatOptions aco ? aco : null;

        MessagesRequest.Builder builder =
                MessagesRequest.builder().messages(messages).system(system);

        if (opts != null) {
            builder.model(opts.getModel())
                    .maxTokens(opts.getMaxTokens())
                    .temperature(opts.getTemperature())
                    .topP(opts.getTopP())
                    .topK(opts.getTopK())
                    .stopSequences(opts.getStopSequences())
                    .tools(opts.getTools());

            // Thinking mode
            if (opts.getThinkingBudgetTokens() != null && opts.getThinkingBudgetTokens() > 0) {
                builder.thinking(Thinking.enabled(opts.getThinkingBudgetTokens()));
            }

            // Check if code_execution tool is present — requires beta header
            if (opts.getTools() != null) {
                boolean hasCodeExec =
                        opts.getTools().stream()
                                .anyMatch(t -> "code_execution_20250825".equals(t.type()));
                if (hasCodeExec) {
                    builder.betaFeatures(List.of("code-execution-2025-08-25"));
                }
            }
        } else if (options != null) {
            builder.model(options.getModel())
                    .maxTokens(options.getMaxTokens())
                    .temperature(options.getTemperature())
                    .topP(options.getTopP())
                    .topK(options.getTopK())
                    .stopSequences(options.getStopSequences());
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private List<Message> convertMessage(org.springframework.ai.chat.messages.Message msg) {
        List<Message> messages = new ArrayList<>();

        switch (msg.getMessageType()) {
            case USER -> {
                UserMessage userMsg = (UserMessage) msg;
                messages.add(Message.user(userMsg.getText()));
            }

            case ASSISTANT -> {
                AssistantMessage assistantMsg = (AssistantMessage) msg;
                if (assistantMsg.hasToolCalls()) {
                    // Convert tool calls to content blocks
                    List<ContentBlock> blocks = new ArrayList<>();
                    if (assistantMsg.getText() != null && !assistantMsg.getText().isBlank()) {
                        blocks.add(ContentBlock.text(assistantMsg.getText()));
                    }
                    for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                        Object input;
                        try {
                            input = objectMapper.readValue(tc.arguments(), Map.class);
                        } catch (Exception e) {
                            input = Map.of();
                        }
                        blocks.add(ContentBlock.toolUse(tc.id(), tc.name(), input));
                    }
                    messages.add(Message.assistant(blocks));
                } else {
                    String text = assistantMsg.getText();
                    if (text != null && !text.isBlank()) {
                        messages.add(Message.assistant(text));
                    }
                }
            }

            case TOOL -> {
                ToolResponseMessage toolMsg = (ToolResponseMessage) msg;
                List<ContentBlock> blocks = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : toolMsg.getResponses()) {
                    blocks.add(ContentBlock.toolResult(tr.id(), tr.responseData()));
                }
                messages.add(new Message("user", blocks));
            }

            default -> log.warn("Unsupported message type: {}", msg.getMessageType());
        }

        return messages;
    }

    private ChatResponse toSpringChatResponse(MessagesResponse result) {
        List<Generation> generations = new ArrayList<>();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        String finishReason = mapStopReason(result.stopReason());

        if (result.content() != null) {
            for (ResponseContentBlock block : result.content()) {
                switch (block.type()) {
                    case "text" -> {
                        if (block.text() != null) {
                            if (!textBuilder.isEmpty()) {
                                textBuilder.append("\n");
                            }
                            textBuilder.append(block.text());
                        }
                    }
                    case "tool_use" -> {
                        String argsJson;
                        try {
                            argsJson = objectMapper.writeValueAsString(block.input());
                        } catch (Exception e) {
                            argsJson = "{}";
                        }
                        toolCalls.add(
                                new AssistantMessage.ToolCall(
                                        block.id(), "function", block.name(), argsJson));
                        finishReason = "TOOL_CALLS";
                    }
                    case "thinking" -> {
                        // Thinking blocks are internal reasoning — skip in main output
                    }
                    default -> {
                        // web_search_tool_result, code_execution_tool_result, etc.
                        // Server-side tool results — the text output is already in text blocks
                        log.debug("Ignoring content block type: {}", block.type());
                    }
                }
            }
        }

        // Build a single Generation with text + any tool calls
        AssistantMessage assistantMessage;
        if (!toolCalls.isEmpty()) {
            assistantMessage =
                    AssistantMessage.builder()
                            .content(textBuilder.toString())
                            .toolCalls(toolCalls)
                            .build();
        } else {
            assistantMessage = new AssistantMessage(textBuilder.toString());
        }

        ChatGenerationMetadata genMeta =
                ChatGenerationMetadata.builder().finishReason(finishReason).build();
        generations.add(new Generation(assistantMessage, genMeta));

        // Build usage metadata
        ResponseUsage usage = result.usage();
        int inputTok = usage != null && usage.inputTokens() != null ? usage.inputTokens() : 0;
        int outputTok = usage != null && usage.outputTokens() != null ? usage.outputTokens() : 0;
        DefaultUsage springUsage = new DefaultUsage(inputTok, outputTok, inputTok + outputTok);

        ChatResponseMetadata.Builder metaBuilder =
                ChatResponseMetadata.builder()
                        .id(result.id())
                        .model(result.model())
                        .usage(springUsage);

        return new ChatResponse(generations, metaBuilder.build());
    }

    private String mapStopReason(String stopReason) {
        if (stopReason == null) return "STOP";
        return switch (stopReason) {
            case "end_turn" -> "STOP";
            case "tool_use" -> "TOOL_CALLS";
            case "max_tokens" -> "MAX_TOKENS";
            case "stop_sequence" -> "STOP_SEQUENCE";
            default -> stopReason.toUpperCase();
        };
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
