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

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.providers.cohere.api.CohereApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Cohere AI provider using native SDK (not OpenAI-compatible). Uses CohereApi, CohereChatModel, and
 * CohereEmbeddingModel for proper v2 API support.
 */
@Slf4j
public class CohereAI implements AIModel {

    public static final String NAME = "cohere";
    private final CohereAIConfiguration config;

    // Cached instances
    private final CohereApi cohereApi;
    private final CohereChatModel chatModel;

    public CohereAI(CohereAIConfiguration config) {
        this.config = config;
        this.cohereApi = createCohereApi();
        this.chatModel = createChatModel();
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        CohereEmbeddingModel embeddingModel =
                CohereEmbeddingModel.builder()
                        .cohereApi(this.cohereApi)
                        .defaultModel(embeddingGenRequest.getModel())
                        .defaultDimensions(embeddingGenRequest.getDimensions())
                        .build();

        org.springframework.ai.embedding.EmbeddingRequest request =
                new org.springframework.ai.embedding.EmbeddingRequest(
                        List.of(embeddingGenRequest.getText()), null);

        org.springframework.ai.embedding.EmbeddingResponse response = embeddingModel.call(request);

        if (response.getResults() != null && !response.getResults().isEmpty()) {
            float[] output = response.getResults().get(0).getOutput();
            List<Float> result = new java.util.ArrayList<>(output.length);
            for (float f : output) {
                result.add(f);
            }
            return result;
        }
        return List.of();
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        return CohereChatOptions.builder()
                .model(input.getModel())
                .temperature(input.getTemperature())
                .topP(input.getTopP())
                .maxTokens(input.getMaxTokens())
                .stopSequences(input.getStopWords())
                .frequencyPenalty(input.getFrequencyPenalty())
                .presencePenalty(input.getPresencePenalty())
                .build();
    }

    @Override
    public ChatModel getChatModel() {
        return this.chatModel;
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by Cohere");
    }

    // Initialization helpers

    private CohereApi createCohereApi() {
        CohereApi.Builder builder = CohereApi.builder().apiKey(config.getApiKey());

        if (config.getBaseURL() != null && !config.getBaseURL().isEmpty()) {
            builder.baseUrl(config.getBaseURL());
        }

        return builder.build();
    }

    private CohereChatModel createChatModel() {
        return CohereChatModel.builder().cohereApi(this.cohereApi).build();
    }
}
