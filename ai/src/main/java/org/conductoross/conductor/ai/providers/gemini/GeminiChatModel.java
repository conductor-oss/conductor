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
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi;
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
 * Spring AI {@link ChatModel} implementation backed by the OkHttp-based {@link GeminiApi} client.
 *
 * <p>Converts Spring AI {@link Prompt} messages to {@link GeminiApi.Content} objects, calls {@code
 * GeminiApi.generateContent()}, and converts the response back to Spring AI's {@link ChatResponse}.
 */
@Slf4j
public class GeminiChatModel implements ChatModel {

    private final GeminiApi api;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiChatModel(GeminiApi api) {
        this.api = api;
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

        // Convert messages to GeminiApi Content
        List<GeminiApi.Content> contents = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message msg : nonSystemMessages) {
            contents.addAll(convertMessage(msg));
        }

        GeminiChatOptions opts = options instanceof GeminiChatOptions gco ? gco : null;

        // Build system instruction content (if any)
        GeminiApi.Content systemInstruction = null;
        if (!systemBuilder.isEmpty()) {
            systemInstruction =
                    new GeminiApi.Content(
                            null, List.of(GeminiApi.Part.text(systemBuilder.toString())));
        }

        // Build tools
        List<GeminiApi.Tool> toolList = buildTools(opts);

        // Build GenerationConfig
        Double temperature = null;
        Double topP = null;
        Integer topK = null;
        Integer maxOutputTokens = null;
        List<String> stopSequences = null;
        Double frequencyPenalty = null;
        Double presencePenalty = null;
        GeminiApi.ThinkingConfig thinkingConfig = null;

        if (opts != null) {
            temperature = opts.getTemperature();
            topP = opts.getTopP();
            topK = opts.getTopK();
            maxOutputTokens = opts.getMaxTokens();
            stopSequences = opts.getStopSequences();
            frequencyPenalty = opts.getFrequencyPenalty();
            presencePenalty = opts.getPresencePenalty();

            // Thinking config
            boolean hasBudget =
                    opts.getThinkingBudgetTokens() != null && opts.getThinkingBudgetTokens() > 0;
            boolean includeThoughts =
                    opts.getIncludeThoughts() != null && opts.getIncludeThoughts();
            if (hasBudget || includeThoughts) {
                thinkingConfig =
                        new GeminiApi.ThinkingConfig(
                                hasBudget ? opts.getThinkingBudgetTokens() : null,
                                includeThoughts ? true : null);
            }
        } else if (options != null) {
            temperature = options.getTemperature();
            topP = options.getTopP();
            topK = options.getTopK();
            maxOutputTokens = options.getMaxTokens();
            stopSequences = options.getStopSequences();
            frequencyPenalty = options.getFrequencyPenalty();
            presencePenalty = options.getPresencePenalty();
        }

        GeminiApi.GenerationConfig config =
                new GeminiApi.GenerationConfig(
                        temperature,
                        topP,
                        topK,
                        maxOutputTokens,
                        stopSequences,
                        frequencyPenalty,
                        presencePenalty,
                        null, // responseMimeType
                        null, // responseModalities
                        thinkingConfig,
                        null // speechConfig
                        );

        String model =
                opts != null ? opts.getModel() : (options != null ? options.getModel() : null);
        if (model == null) {
            model = "gemini-2.5-flash";
        }

        GeminiApi.GenerateContentResponse result;
        try {
            result =
                    api.generateContent(
                            model,
                            contents,
                            systemInstruction,
                            toolList.isEmpty() ? null : toolList,
                            config);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Gemini generateContent failed: " + e.getMessage(), e);
        }

        return toSpringChatResponse(result, model);
    }

    private List<GeminiApi.Tool> buildTools(GeminiChatOptions opts) {
        List<GeminiApi.Tool> tools = new ArrayList<>();
        if (opts == null) {
            return tools;
        }

        // Google Search
        if (opts.isGoogleSearchRetrieval()) {
            tools.add(GeminiApi.Tool.withGoogleSearch());
        }

        // Code execution
        if (opts.isCodeExecution()) {
            tools.add(GeminiApi.Tool.withCodeExecution());
        }

        // Function tools
        if (opts.getTools() != null && !opts.getTools().isEmpty()) {
            List<GeminiApi.FunctionDeclaration> declarations = new ArrayList<>();
            for (ToolSpec toolSpec : opts.getTools()) {
                Object schema =
                        (toolSpec.getInputSchema() != null && !toolSpec.getInputSchema().isEmpty())
                                ? toolSpec.getInputSchema()
                                : null;
                declarations.add(
                        new GeminiApi.FunctionDeclaration(
                                toolSpec.getName(),
                                toolSpec.getDescription() != null ? toolSpec.getDescription() : "",
                                schema));
            }
            tools.add(GeminiApi.Tool.withFunctionDeclarations(declarations));
        }

        return tools;
    }

    @SuppressWarnings("unchecked")
    private List<GeminiApi.Content> convertMessage(
            org.springframework.ai.chat.messages.Message msg) {
        List<GeminiApi.Content> contents = new ArrayList<>();

        switch (msg.getMessageType()) {
            case USER -> {
                UserMessage userMsg = (UserMessage) msg;
                contents.add(
                        new GeminiApi.Content(
                                "user", List.of(GeminiApi.Part.text(userMsg.getText()))));
            }

            case ASSISTANT -> {
                AssistantMessage assistantMsg = (AssistantMessage) msg;
                List<GeminiApi.Part> parts = new ArrayList<>();
                if (assistantMsg.getText() != null && !assistantMsg.getText().isBlank()) {
                    parts.add(GeminiApi.Part.text(assistantMsg.getText()));
                }
                if (assistantMsg.hasToolCalls()) {
                    for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                        Map<String, Object> args;
                        try {
                            args = objectMapper.readValue(tc.arguments(), Map.class);
                        } catch (Exception e) {
                            args = Map.of();
                        }
                        parts.add(GeminiApi.Part.functionCall(tc.name(), args));
                    }
                }
                if (!parts.isEmpty()) {
                    contents.add(new GeminiApi.Content("model", parts));
                }
            }

            case TOOL -> {
                ToolResponseMessage toolMsg = (ToolResponseMessage) msg;
                List<GeminiApi.Part> parts = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : toolMsg.getResponses()) {
                    Map<String, Object> responseMap;
                    try {
                        responseMap = objectMapper.readValue(tr.responseData(), Map.class);
                    } catch (Exception e) {
                        responseMap = Map.of("result", tr.responseData());
                    }
                    parts.add(GeminiApi.Part.functionResponse(tr.name(), responseMap));
                }
                contents.add(new GeminiApi.Content("user", parts));
            }

            default -> log.warn("Unsupported message type: {}", msg.getMessageType());
        }

        return contents;
    }

    ChatResponse toSpringChatResponse(GeminiApi.GenerateContentResponse result, String model) {
        List<Generation> generations = new ArrayList<>();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();

        // Extract finish reason
        String finishReason = "STOP";
        String geminiFinishReason = result.finishReason();
        if (geminiFinishReason != null) {
            finishReason = geminiFinishReason;
        }

        // Extract text from response. result.text() returns concatenated text from
        // non-thought parts only — it filters out parts whose thought flag is true.
        // We separately iterate the candidates to surface thought summaries as reasoning metadata.
        String text = result.text();
        if (text != null && !text.isEmpty()) {
            textBuilder.append(text);
        }

        // Collect thought summary text from thought parts
        List<GeminiApi.Candidate> candidates = result.candidates();
        if (candidates != null) {
            for (GeminiApi.Candidate candidate : candidates) {
                GeminiApi.Content content = candidate.content();
                if (content == null || content.parts() == null) continue;
                for (GeminiApi.Part part : content.parts()) {
                    if (Boolean.TRUE.equals(part.thought())) {
                        String thoughtText = part.text();
                        if (thoughtText != null && !thoughtText.isBlank()) {
                            if (!reasoningBuilder.isEmpty()) {
                                reasoningBuilder.append("\n\n");
                            }
                            reasoningBuilder.append(thoughtText);
                        }
                    }
                }
            }
        }

        // Extract function calls
        List<GeminiApi.FunctionCallPart> functionCalls = result.functionCalls();
        if (functionCalls != null && !functionCalls.isEmpty()) {
            for (GeminiApi.FunctionCallPart fc : functionCalls) {
                String name = fc.name();
                String id = fc.name(); // FunctionCallPart has no separate id field
                String argsJson;
                try {
                    argsJson =
                            objectMapper.writeValueAsString(
                                    fc.args() != null ? fc.args() : Map.of());
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
        Integer thoughtsTok = null;
        GeminiApi.UsageMetadata usageMeta = result.usageMetadata();
        if (usageMeta != null) {
            inputTok = usageMeta.promptTokenCount() != null ? usageMeta.promptTokenCount() : 0;
            outputTok =
                    usageMeta.candidatesTokenCount() != null ? usageMeta.candidatesTokenCount() : 0;
            thoughtsTok = usageMeta.thoughtsTokenCount();
        }
        DefaultUsage springUsage = new DefaultUsage(inputTok, outputTok, inputTok + outputTok);

        String responseId = result.responseId();
        ChatResponseMetadata.Builder metaBuilder =
                ChatResponseMetadata.builder().model(model).usage(springUsage);
        if (responseId != null) {
            metaBuilder.id(responseId);
        }
        if (!reasoningBuilder.isEmpty()) {
            metaBuilder.keyValue("reasoning", reasoningBuilder.toString());
        }
        if (thoughtsTok != null) {
            metaBuilder.keyValue("reasoning_tokens", thoughtsTok);
        }

        return new ChatResponse(generations, metaBuilder.build());
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
