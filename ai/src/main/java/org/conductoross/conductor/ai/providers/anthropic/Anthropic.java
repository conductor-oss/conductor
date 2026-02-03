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
package org.conductoross.conductor.ai.providers.anthropic;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.tool.ToolCallback;

public class Anthropic implements AIModel {

    public static final String NAME = "anthropic";
    private final AnthropicConfiguration config;

    public Anthropic(AnthropicConfiguration config) {
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
    public ChatModel getChatModel() {
        var builder =
                AnthropicApi.builder().baseUrl(config.getBaseURL()).apiKey(config.getApiKey());

        if (StringUtils.isNotBlank(config.getVersion())) {
            builder.anthropicVersion(config.getVersion());
        }
        if (StringUtils.isNotBlank(config.getBetaVersion())) {
            builder.anthropicBetaFeatures(config.getBetaVersion());
        }
        if (StringUtils.isNotBlank(config.getCompletionsPath())) {
            builder.completionsPath(config.getCompletionsPath());
        }
        AnthropicApi anthropicApi = builder.build();
        return AnthropicChatModel.builder().anthropicApi(anthropicApi).build();
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        List<ToolCallback> toolCallbacks = getToolCallback(input);
        Set<String> toolNames =
                toolCallbacks.stream()
                        .map(tc -> tc.getToolDefinition().name())
                        .collect(Collectors.toSet());
        Double temperature = input.getTemperature();
        AnthropicApi.ChatCompletionRequest.ThinkingConfig thinkingConfig = null;
        if (input.getThinkingTokenLimit() > 0) {
            thinkingConfig =
                    new AnthropicApi.ChatCompletionRequest.ThinkingConfig(
                            AnthropicApi.ThinkingType.ENABLED, input.getThinkingTokenLimit());
            temperature = 1.0;
        }
        AnthropicChatOptions.Builder builder =
                AnthropicChatOptions.builder()
                        .model(input.getModel())
                        .thinking(thinkingConfig)
                        .topP(input.getTopP())
                        .toolCallbacks(toolCallbacks)
                        .toolNames(toolNames)
                        .internalToolExecutionEnabled(false)
                        .temperature(temperature)
                        .maxTokens(input.getMaxTokens());

        return builder.build();
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }
}
