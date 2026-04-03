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
package org.conductoross.conductor.ai.providers.grok;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;

public class Grok implements AIModel {

    public static final String NAME = "Grok";
    private final GrokAIConfiguration config;

    public Grok(GrokAIConfiguration config) {
        this.config = config;
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
        List<ToolCallback> toolCallbacks = getToolCallback(input);
        Set<String> toolNames =
                toolCallbacks.stream()
                        .map(tc -> tc.getToolDefinition().name())
                        .collect(Collectors.toSet());

        OpenAiChatOptions.Builder builder =
                OpenAiChatOptions.builder()
                        .model(input.getModel())
                        .temperature(input.getTemperature())
                        .topP(input.getTopP())
                        .maxTokens(input.getMaxTokens())
                        .stop(input.getStopWords())
                        .frequencyPenalty(input.getFrequencyPenalty())
                        .presencePenalty(input.getPresencePenalty())
                        .toolCallbacks(toolCallbacks)
                        .toolNames(toolNames)
                        .internalToolExecutionEnabled(false);

        return builder.build();
    }

    @Override
    public ChatModel getChatModel() {
        OpenAiApi grokApi =
                OpenAiApi.builder()
                        .baseUrl(config.getBaseURL())
                        .apiKey(config.getApiKey())
                        .completionsPath("/v1/chat/completions")
                        .build();
        return OpenAiChatModel.builder().openAiApi(grokApi).build();
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }
}
