/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.providers.cohere;

import java.util.List;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.providers.cohere.api.CohereApi;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi.ChatCompletionRequest;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi.ChatCompletionResponse;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi.ChatMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * Cohere Chat Model implementation following Spring AI patterns. Potential contribution to
 * spring-ai project.
 */
public class CohereChatModel implements ChatModel {

    private final CohereApi cohereApi;
    private final CohereChatOptions defaultOptions;

    public CohereChatModel(CohereApi cohereApi) {
        this(cohereApi, CohereChatOptions.builder().build());
    }

    public CohereChatModel(CohereApi cohereApi, CohereChatOptions defaultOptions) {
        Assert.notNull(cohereApi, "CohereApi must not be null");
        this.cohereApi = cohereApi;
        this.defaultOptions =
                defaultOptions != null ? defaultOptions : CohereChatOptions.builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatCompletionRequest request = createRequest(prompt);
        ResponseEntity<ChatCompletionResponse> response = this.cohereApi.chat(request);
        return toChatResponse(response.getBody());
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return this.defaultOptions;
    }

    private ChatCompletionRequest createRequest(Prompt prompt) {
        // Merge options: prompt options override defaults
        CohereChatOptions options = mergeOptions(prompt.getOptions());

        // Convert Spring AI messages to Cohere format
        List<ChatMessage> messages =
                prompt.getInstructions().stream()
                        .map(this::toCohereMessage)
                        .collect(Collectors.toList());

        return ChatCompletionRequest.builder()
                .model(options.getModel())
                .messages(messages)
                .temperature(options.getTemperature())
                .maxTokens(options.getMaxTokens())
                .topP(options.getTopP())
                .topK(options.getTopK())
                .frequencyPenalty(options.getFrequencyPenalty())
                .presencePenalty(options.getPresencePenalty())
                .stopSequences(options.getStopSequences())
                .stream(false)
                .build();
    }

    private CohereChatOptions mergeOptions(ChatOptions promptOptions) {
        if (promptOptions == null) {
            return this.defaultOptions;
        }

        CohereChatOptions.Builder builder = CohereChatOptions.builder();

        // Use prompt options if available, otherwise default
        builder.model(
                promptOptions.getModel() != null
                        ? promptOptions.getModel()
                        : defaultOptions.getModel());
        builder.temperature(
                promptOptions.getTemperature() != null
                        ? promptOptions.getTemperature()
                        : defaultOptions.getTemperature());
        builder.maxTokens(
                promptOptions.getMaxTokens() != null
                        ? promptOptions.getMaxTokens()
                        : defaultOptions.getMaxTokens());
        builder.topP(
                promptOptions.getTopP() != null
                        ? promptOptions.getTopP()
                        : defaultOptions.getTopP());
        builder.topK(
                promptOptions.getTopK() != null
                        ? promptOptions.getTopK()
                        : defaultOptions.getTopK());
        builder.frequencyPenalty(
                promptOptions.getFrequencyPenalty() != null
                        ? promptOptions.getFrequencyPenalty()
                        : defaultOptions.getFrequencyPenalty());
        builder.presencePenalty(
                promptOptions.getPresencePenalty() != null
                        ? promptOptions.getPresencePenalty()
                        : defaultOptions.getPresencePenalty());

        // Handle Cohere-specific options
        if (promptOptions instanceof CohereChatOptions cohereOptions) {
            builder.stopSequences(
                    cohereOptions.getStopSequences() != null
                            ? cohereOptions.getStopSequences()
                            : defaultOptions.getStopSequences());
        } else {
            builder.stopSequences(defaultOptions.getStopSequences());
        }

        return builder.build();
    }

    private ChatMessage toCohereMessage(Message message) {
        String role =
                switch (message.getMessageType()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                    case TOOL -> "tool";
                };
        return new ChatMessage(role, message.getText());
    }

    private ChatResponse toChatResponse(ChatCompletionResponse response) {
        if (response == null) {
            return new ChatResponse(List.of());
        }

        // Extract text from content blocks
        String text = "";
        if (response.message() != null && response.message().content() != null) {
            text =
                    response.message().content().stream()
                            .filter(block -> "text".equals(block.type()))
                            .map(ChatCompletionResponse.ContentBlock::text)
                            .collect(Collectors.joining());
        }

        AssistantMessage assistantMessage = new AssistantMessage(text);

        // Build generation with metadata
        ChatGenerationMetadata.Builder metadataBuilder = ChatGenerationMetadata.builder();
        if (response.finishReason() != null) {
            metadataBuilder.finishReason(response.finishReason());
        }

        Generation generation = new Generation(assistantMessage, metadataBuilder.build());

        // Build response metadata with usage
        ChatResponseMetadata.Builder responseMetaBuilder = ChatResponseMetadata.builder();
        if (response.id() != null) {
            responseMetaBuilder.id(response.id());
        }
        if (response.usage() != null) {
            responseMetaBuilder.usage(new CohereUsage(response.usage()));
        }

        return new ChatResponse(List.of(generation), responseMetaBuilder.build());
    }

    /** Usage implementation for Cohere. */
    private static class CohereUsage implements Usage {
        private final ChatCompletionResponse.Usage usage;

        CohereUsage(ChatCompletionResponse.Usage usage) {
            this.usage = usage;
        }

        @Override
        public Integer getPromptTokens() {
            return usage.inputTokens() != null ? usage.inputTokens() : 0;
        }

        @Override
        public Integer getCompletionTokens() {
            return usage.outputTokens() != null ? usage.outputTokens() : 0;
        }

        @Override
        public Integer getTotalTokens() {
            return getPromptTokens() + getCompletionTokens();
        }

        @Override
        public Object getNativeUsage() {
            return usage;
        }
    }

    public static class Builder {
        private CohereApi cohereApi;
        private CohereChatOptions defaultOptions;

        public Builder cohereApi(CohereApi cohereApi) {
            this.cohereApi = cohereApi;
            return this;
        }

        public Builder defaultOptions(CohereChatOptions defaultOptions) {
            this.defaultOptions = defaultOptions;
            return this;
        }

        public CohereChatModel build() {
            Assert.notNull(this.cohereApi, "CohereApi must not be null");
            return new CohereChatModel(this.cohereApi, this.defaultOptions);
        }
    }
}
