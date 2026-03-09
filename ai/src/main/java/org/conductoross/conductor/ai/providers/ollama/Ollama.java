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
package org.conductoross.conductor.ai.providers.ollama;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.client.RestClient;

import com.google.common.primitives.Floats;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Ollama implements AIModel {

    public static final String NAME = "ollama";
    private final OllamaConfiguration config;

    public Ollama(OllamaConfiguration config) {
        this.config = config;
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        OllamaApi api = OllamaApi.builder().baseUrl(config.getBaseURL()).build();
        var options =
                OllamaEmbeddingOptions.builder().model(embeddingGenRequest.getModel()).build();
        var embeddingModel =
                OllamaEmbeddingModel.builder().ollamaApi(api).defaultOptions(options).build();
        float[] embeddingsResponse =
                embeddingModel
                        .embedForResponse(List.of(embeddingGenRequest.getText()))
                        .getResult()
                        .getOutput();
        return Floats.asList(embeddingsResponse);
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        List<ToolCallback> toolCallbacks = getToolCallback(input);
        Set<String> toolNames =
                toolCallbacks.stream()
                        .map(tc -> tc.getToolDefinition().name())
                        .collect(Collectors.toSet());

        OllamaChatOptions.Builder builder =
                OllamaChatOptions.builder()
                        .model(input.getModel())
                        .temperature(input.getTemperature())
                        .topP(input.getTopP())
                        .topK(input.getTopK())
                        .numPredict(input.getMaxTokens())
                        .stop(input.getStopWords())
                        .frequencyPenalty(input.getFrequencyPenalty())
                        .toolCallbacks(toolCallbacks)
                        .toolNames(toolNames)
                        .internalToolExecutionEnabled(false)
                        .presencePenalty(input.getPresencePenalty());

        if (input.isJsonOutput()) {
            builder.format("json");
        }

        return builder.build();
    }

    @Override
    public ChatModel getChatModel() {
        OllamaApi.Builder builder = OllamaApi.builder();
        builder.baseUrl(config.getBaseURL());
        if (StringUtils.isNotBlank(config.getAuthHeaderName())) {
            RestClient.Builder restClientBuilder =
                    RestClient.builder()
                            .defaultHeader(config.getAuthHeaderName(), config.getAuthHeader());
            builder.restClientBuilder(restClientBuilder);
        }
        OllamaApi api = builder.build();
        return OllamaChatModel.builder().ollamaApi(api).build();
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }
}
