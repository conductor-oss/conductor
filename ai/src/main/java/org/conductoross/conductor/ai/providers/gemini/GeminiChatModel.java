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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.models.ToolSpec;
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
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolCodeExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI {@link ChatModel} implementation backed by the Google GenAI SDK.
 *
 * <p>Converts Spring AI {@link Prompt} messages to GenAI {@link Content} objects, calls {@code
 * Client.models.generateContent()}, and converts the response back to Spring AI's {@link
 * ChatResponse}.
 */
@Slf4j
public class GeminiChatModel implements ChatModel {

    private final Client client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiChatModel(Client client) {
        this.client = client;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<org.springframework.ai.chat.messages.Message> springMessages =
                prompt.getInstructions();
        ChatOptions options = prompt.getOptions();

        // Extract system messages
        StringBuilder systemBuilder = new StringBuilder();
        List<org.springframework.ai.chat.messages.Message> nonSystemMessages = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message msg : springMessages) {
            if (msg.getMessageType() == MessageType.SYSTEM) {
                String sysText = ((SystemMessage) msg).getText();
                if (!systemBuilder.isEmpty()) {
                    systemBuilder.append("\n");
                }
                systemBuilder.append(sysText);
            } else {
                nonSystemMessages.add(msg);
            }
        }

        // Convert messages to GenAI Content
        List<Content> contents = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message msg : nonSystemMessages) {
            contents.addAll(convertMessage(msg));
        }

        // Build config
        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

        if (!systemBuilder.isEmpty()) {
            configBuilder.systemInstruction(
                    Content.builder()
                            .parts(List.of(Part.fromText(systemBuilder.toString())))
                            .build());
        }

        GeminiChatOptions opts = options instanceof GeminiChatOptions gco ? gco : null;

        if (opts != null) {
            if (opts.getMaxTokens() != null) configBuilder.maxOutputTokens(opts.getMaxTokens());
            if (opts.getTemperature() != null)
                configBuilder.temperature(opts.getTemperature().floatValue());
            if (opts.getTopP() != null) configBuilder.topP(opts.getTopP().floatValue());
            if (opts.getTopK() != null) configBuilder.topK(opts.getTopK().floatValue());
            if (opts.getStopSequences() != null)
                configBuilder.stopSequences(opts.getStopSequences());
            if (opts.getFrequencyPenalty() != null)
                configBuilder.frequencyPenalty(opts.getFrequencyPenalty().floatValue());
            if (opts.getPresencePenalty() != null)
                configBuilder.presencePenalty(opts.getPresencePenalty().floatValue());

            // Tools
            List<Tool> tools = buildTools(opts);
            if (!tools.isEmpty()) {
                configBuilder.tools(tools);
            }

            // Thinking
            if (opts.getThinkingBudgetTokens() != null && opts.getThinkingBudgetTokens() > 0) {
                configBuilder.thinkingConfig(
                        ThinkingConfig.builder()
                                .thinkingBudget(opts.getThinkingBudgetTokens())
                                .build());
            }
        } else if (options != null) {
            if (options.getMaxTokens() != null)
                configBuilder.maxOutputTokens(options.getMaxTokens());
            if (options.getTemperature() != null)
                configBuilder.temperature(options.getTemperature().floatValue());
            if (options.getTopP() != null) configBuilder.topP(options.getTopP().floatValue());
            if (options.getTopK() != null) configBuilder.topK(options.getTopK().floatValue());
            if (options.getStopSequences() != null)
                configBuilder.stopSequences(options.getStopSequences());
            if (options.getFrequencyPenalty() != null)
                configBuilder.frequencyPenalty(options.getFrequencyPenalty().floatValue());
            if (options.getPresencePenalty() != null)
                configBuilder.presencePenalty(options.getPresencePenalty().floatValue());
        }

        String model =
                opts != null ? opts.getModel() : (options != null ? options.getModel() : null);
        if (model == null) {
            model = "gemini-2.5-flash";
        }

        GenerateContentResponse result =
                client.models.generateContent(model, contents, configBuilder.build());

        return toSpringChatResponse(result, model);
    }

    private List<Tool> buildTools(GeminiChatOptions opts) {
        List<Tool> tools = new ArrayList<>();

        // Google Search
        if (opts.isGoogleSearchRetrieval()) {
            tools.add(Tool.builder().googleSearch(GoogleSearch.builder().build()).build());
        }

        // Code execution
        if (opts.isCodeExecution()) {
            tools.add(Tool.builder().codeExecution(ToolCodeExecution.builder().build()).build());
        }

        // Function tools
        if (opts.getTools() != null && !opts.getTools().isEmpty()) {
            List<FunctionDeclaration> declarations = new ArrayList<>();
            for (ToolSpec toolSpec : opts.getTools()) {
                FunctionDeclaration.Builder declBuilder =
                        FunctionDeclaration.builder()
                                .name(toolSpec.getName())
                                .description(
                                        toolSpec.getDescription() != null
                                                ? toolSpec.getDescription()
                                                : "");
                if (toolSpec.getInputSchema() != null && !toolSpec.getInputSchema().isEmpty()) {
                    declBuilder.parametersJsonSchema(toolSpec.getInputSchema());
                }
                declarations.add(declBuilder.build());
            }
            tools.add(Tool.builder().functionDeclarations(declarations).build());
        }

        return tools;
    }

    @SuppressWarnings("unchecked")
    private List<Content> convertMessage(org.springframework.ai.chat.messages.Message msg) {
        List<Content> contents = new ArrayList<>();

        switch (msg.getMessageType()) {
            case USER -> {
                UserMessage userMsg = (UserMessage) msg;
                contents.add(
                        Content.builder()
                                .role("user")
                                .parts(List.of(Part.fromText(userMsg.getText())))
                                .build());
            }

            case ASSISTANT -> {
                AssistantMessage assistantMsg = (AssistantMessage) msg;
                List<Part> parts = new ArrayList<>();
                if (assistantMsg.getText() != null && !assistantMsg.getText().isBlank()) {
                    parts.add(Part.fromText(assistantMsg.getText()));
                }
                if (assistantMsg.hasToolCalls()) {
                    for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                        Map<String, Object> args;
                        try {
                            args = objectMapper.readValue(tc.arguments(), Map.class);
                        } catch (Exception e) {
                            args = Map.of();
                        }
                        parts.add(Part.fromFunctionCall(tc.name(), args));
                    }
                }
                if (!parts.isEmpty()) {
                    contents.add(Content.builder().role("model").parts(parts).build());
                }
            }

            case TOOL -> {
                ToolResponseMessage toolMsg = (ToolResponseMessage) msg;
                List<Part> parts = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : toolMsg.getResponses()) {
                    Map<String, Object> responseMap;
                    try {
                        responseMap = objectMapper.readValue(tr.responseData(), Map.class);
                    } catch (Exception e) {
                        responseMap = Map.of("result", tr.responseData());
                    }
                    parts.add(Part.fromFunctionResponse(tr.name(), responseMap));
                }
                contents.add(Content.builder().role("user").parts(parts).build());
            }

            default -> log.warn("Unsupported message type: {}", msg.getMessageType());
        }

        return contents;
    }

    private ChatResponse toSpringChatResponse(GenerateContentResponse result, String model) {
        List<Generation> generations = new ArrayList<>();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        // Extract finish reason
        String finishReason = "STOP";
        var geminiFinishReason = result.finishReason();
        if (geminiFinishReason != null) {
            finishReason = geminiFinishReason.toString();
        }

        // Extract text from response
        try {
            String text = result.text();
            if (text != null && !text.isEmpty()) {
                textBuilder.append(text);
            }
        } catch (Exception e) {
            // text() throws if no text content
        }

        // Extract function calls
        var functionCalls = result.functionCalls();
        if (functionCalls != null && !functionCalls.isEmpty()) {
            for (FunctionCall fc : functionCalls) {
                String name = fc.name().orElse("");
                String id = fc.id().orElse(name);
                String argsJson;
                try {
                    argsJson = objectMapper.writeValueAsString(fc.args().orElse(Map.of()));
                } catch (Exception e) {
                    argsJson = "{}";
                }
                toolCalls.add(new AssistantMessage.ToolCall(id, "function", name, argsJson));
            }
            finishReason = "TOOL_CALLS";
        }

        // Build AssistantMessage
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

        // Usage metadata
        int inputTok = 0;
        int outputTok = 0;
        var usageMeta = result.usageMetadata();
        if (usageMeta.isPresent()) {
            GenerateContentResponseUsageMetadata usage = usageMeta.get();
            inputTok = usage.promptTokenCount().orElse(0);
            outputTok = usage.candidatesTokenCount().orElse(0);
        }
        DefaultUsage springUsage = new DefaultUsage(inputTok, outputTok, inputTok + outputTok);

        String responseId = result.responseId().orElse(null);
        ChatResponseMetadata.Builder metaBuilder =
                ChatResponseMetadata.builder().model(model).usage(springUsage);
        if (responseId != null) {
            metaBuilder.id(responseId);
        }

        return new ChatResponse(generations, metaBuilder.build());
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
