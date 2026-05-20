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
package org.conductoross.conductor.ai.providers.perplexity;

import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.providers.openai.OpenAICompatChatModel;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIChatCompletionsApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

public class PerplexityAI implements AIModel {

    public static final String NAME = "perplexity";
    private final PerplexityAIConfiguration config;
    private final OpenAICompatChatModel chatModel;

    public PerplexityAI(PerplexityAIConfiguration config) {
        this.config = config;
        long timeoutSecs = config.getTimeout() != null ? config.getTimeout().getSeconds() : 600;
        OpenAIChatCompletionsApi api =
                new OpenAIChatCompletionsApi(
                        config.getApiKey(), config.getBaseURL(), "/chat/completions", timeoutSecs);
        this.chatModel = new OpenAICompatChatModel(api);
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        return ToolCallingChatOptions.builder()
                .model(input.getModel())
                .maxTokens(input.getMaxTokens())
                .topP(input.getTopP())
                .temperature(input.getTemperature())
                .toolCallbacks(getToolCallback(input))
                .internalToolExecutionEnabled(false)
                .frequencyPenalty(input.getFrequencyPenalty())
                .topK(input.getTopK())
                .presencePenalty(input.getPresencePenalty())
                .build();
    }

    @Override
    public ChatModel getChatModel() {
        return this.chatModel;
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }
}
