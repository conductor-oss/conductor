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

import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.ContentPart;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.InputItem;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.OutputContent;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.OutputItem;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.ResponseRequest;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.ResponseResult;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.TextFormat;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.Tool;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.Usage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI {@link ChatModel} implementation backed by the OpenAI Responses API via OkHttp.
 *
 * <p>Converts Spring AI {@link Prompt} messages to Responses API input format, calls the API, and
 * converts the response back to Spring AI's {@link ChatResponse}.
 */
@Slf4j
public class OpenAIResponsesChatModel implements ChatModel {

    private final OpenAIResponsesApi responsesApi;

    public OpenAIResponsesChatModel(OpenAIResponsesApi responsesApi) {
        this.responsesApi = responsesApi;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            ResponseRequest request = buildRequest(prompt);
            ResponseResult result = responsesApi.createResponse(request);
            return toSpringChatResponse(result);
        } catch (IOException e) {
            throw new RuntimeException("OpenAI Responses API call failed: " + e.getMessage(), e);
        }
    }

    private ResponseRequest buildRequest(Prompt prompt) throws JsonProcessingException {
        List<Message> messages = prompt.getInstructions();
        ChatOptions options = prompt.getOptions();

        // Extract system messages → instructions field
        String instructions = null;
        List<Message> nonSystemMessages = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getMessageType() == MessageType.SYSTEM) {
                // Concatenate multiple system messages
                String sysText = ((SystemMessage) msg).getText();
                instructions = instructions == null ? sysText : instructions + "\n" + sysText;
            } else {
                nonSystemMessages.add(msg);
            }
        }

        // Convert messages → Responses API input items
        List<InputItem> inputItems = new ArrayList<>();
        for (Message msg : nonSystemMessages) {
            inputItems.addAll(convertMessage(msg));
        }

        // Extract options
        String model = options != null ? options.getModel() : null;
        Double temperature = options != null ? options.getTemperature() : null;
        Double topP = options != null ? options.getTopP() : null;
        Integer maxTokens = options != null ? options.getMaxTokens() : null;

        // Extract extended options from OpenAIResponsesChatOptions
        String previousResponseId = null;
        String reasoningEffort = null;
        Boolean jsonOutput = null;
        List<Tool> tools = null;

        if (options instanceof OpenAIResponsesChatOptions extOpts) {
            previousResponseId = extOpts.getPreviousResponseId();
            reasoningEffort = extOpts.getReasoningEffort();
            jsonOutput = extOpts.getJsonOutput();
            tools = extOpts.getResponsesApiTools();
        }

        // Build text format for JSON output
        TextFormat textFormat = null;
        if (Boolean.TRUE.equals(jsonOutput)) {
            textFormat = TextFormat.jsonObject();
        }

        ResponseRequest.Builder builder =
                ResponseRequest.builder()
                        .model(model)
                        .input(inputItems)
                        .instructions(instructions)
                        .tools(tools != null && !tools.isEmpty() ? tools : null)
                        .previousResponseId(previousResponseId)
                        .temperature(temperature)
                        .topP(topP)
                        .maxOutputTokens(maxTokens)
                        .reasoningEffort(reasoningEffort)
                        .text(textFormat);

        return builder.build();
    }

    private List<InputItem> convertMessage(Message msg) throws JsonProcessingException {
        List<InputItem> items = new ArrayList<>();

        switch (msg.getMessageType()) {
            case USER -> {
                UserMessage userMsg = (UserMessage) msg;
                List<Media> media = userMsg.getMedia();
                if (media != null && !media.isEmpty()) {
                    // Multi-modal: text + images
                    List<ContentPart> parts = new ArrayList<>();
                    if (userMsg.getText() != null && !userMsg.getText().isBlank()) {
                        parts.add(ContentPart.inputText(userMsg.getText()));
                    }
                    for (Media m : media) {
                        // Media can be URL or base64 data
                        String dataStr =
                                m.getData() instanceof byte[] bytes
                                        ? "data:"
                                                + m.getMimeType()
                                                + ";base64,"
                                                + java.util.Base64.getEncoder()
                                                        .encodeToString(bytes)
                                        : m.getData().toString();
                        parts.add(ContentPart.inputImage(dataStr));
                    }
                    items.add(InputItem.userMessage(parts));
                } else {
                    items.add(InputItem.userMessage(userMsg.getText()));
                }
            }

            case ASSISTANT -> {
                AssistantMessage assistantMsg = (AssistantMessage) msg;
                if (assistantMsg.hasToolCalls()) {
                    // Tool call messages: emit function_call items
                    for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                        items.add(InputItem.functionCall(tc.id(), tc.name(), tc.arguments()));
                    }
                } else {
                    String text = assistantMsg.getText();
                    if (text != null && !text.isBlank()) {
                        items.add(InputItem.assistantMessage(text));
                    }
                }
            }

            case TOOL -> {
                ToolResponseMessage toolMsg = (ToolResponseMessage) msg;
                for (ToolResponseMessage.ToolResponse tr : toolMsg.getResponses()) {
                    items.add(InputItem.functionCallOutput(tr.id(), tr.responseData()));
                }
            }

            default -> log.warn("Unsupported message type: {}", msg.getMessageType());
        }

        return items;
    }

    private ChatResponse toSpringChatResponse(ResponseResult result) {
        List<Generation> generations = new ArrayList<>();
        Usage usage = result.usage();

        if (result.output() != null) {
            // Collect text from message outputs and tool calls separately
            StringBuilder textBuilder = new StringBuilder();
            List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            String finishReason = result.status(); // "completed" typically

            for (OutputItem item : result.output()) {
                if ("message".equals(item.type())) {
                    if (item.content() != null) {
                        for (OutputContent content : item.content()) {
                            if ("output_text".equals(content.type()) && content.text() != null) {
                                if (!textBuilder.isEmpty()) {
                                    textBuilder.append("\n");
                                }
                                textBuilder.append(content.text());
                            }
                        }
                    }
                } else if ("function_call".equals(item.type())) {
                    toolCalls.add(
                            new AssistantMessage.ToolCall(
                                    item.callId(), "function", item.name(), item.arguments()));
                    finishReason = "TOOL_CALLS";
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

            // Map status to finish reason
            String mappedReason = mapFinishReason(finishReason);
            ChatGenerationMetadata genMeta =
                    ChatGenerationMetadata.builder().finishReason(mappedReason).build();
            generations.add(new Generation(assistantMessage, genMeta));
        }

        // Build usage metadata
        int inputTok = usage != null && usage.inputTokens() != null ? usage.inputTokens() : 0;
        int outputTok = usage != null && usage.outputTokens() != null ? usage.outputTokens() : 0;
        int totalTok = usage != null && usage.totalTokens() != null ? usage.totalTokens() : 0;
        org.springframework.ai.chat.metadata.DefaultUsage springUsage =
                new org.springframework.ai.chat.metadata.DefaultUsage(
                        inputTok, outputTok, totalTok);

        ChatResponseMetadata.Builder metaBuilder =
                ChatResponseMetadata.builder()
                        .id(result.id())
                        .model(result.model())
                        .usage(springUsage);

        // Store the response ID in metadata for previous_response_id chaining
        if (result.id() != null) {
            metaBuilder.keyValue("response_id", result.id());
        }

        return new ChatResponse(generations, metaBuilder.build());
    }

    private String mapFinishReason(String status) {
        if (status == null) return "STOP";
        return switch (status) {
            case "completed" -> "STOP";
            case "TOOL_CALLS" -> "TOOL_CALLS";
            case "incomplete" -> "MAX_TOKENS";
            case "failed" -> "ERROR";
            default -> status.toUpperCase();
        };
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
