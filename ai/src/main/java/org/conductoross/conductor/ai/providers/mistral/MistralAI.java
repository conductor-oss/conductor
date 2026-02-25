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
package org.conductoross.conductor.ai.providers.mistral;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MistralAI implements AIModel {

    public static final String NAME = "mistral";
    private final MistralAIConfiguration config;

    // Cached instances
    private final MistralAiApi mistralAiApi;
    private final MistralAiChatModel chatModel;
    private final MistralAiEmbeddingModel embeddingModel;

    public MistralAI(MistralAIConfiguration config) {
        this.config = config;
        this.mistralAiApi = createMistralAiApi();
        this.chatModel = createChatModel();
        this.embeddingModel =
                MistralAiEmbeddingModel.builder().mistralAiApi(this.mistralAiApi).build();
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        // Note, mistral does not support passing embedding dimensions
        EmbeddingOptions options =
                EmbeddingOptions.builder().model(embeddingGenRequest.getModel()).build();

        EmbeddingRequest request =
                new EmbeddingRequest(List.of(embeddingGenRequest.getText()), options);
        EmbeddingResponse response = embeddingModel.call(request);
        return List.of(ArrayUtils.toObject(response.getResult().getOutput()));
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        List<ToolCallback> toolCallbacks = getToolCallback(input);
        Set<String> toolNames =
                toolCallbacks.stream()
                        .map(tc -> tc.getToolDefinition().name())
                        .collect(Collectors.toSet());

        MistralAiChatOptions.Builder builder =
                MistralAiChatOptions.builder()
                        .model(input.getModel())
                        .temperature(input.getTemperature())
                        .topP(input.getTopP())
                        .maxTokens(input.getMaxTokens())
                        .stop(input.getStopWords())
                        .toolCallbacks(toolCallbacks)
                        .toolNames(toolNames)
                        .internalToolExecutionEnabled(false);

        if (input.isJsonOutput()) {
            builder.responseFormat(
                    new MistralAiApi.ChatCompletionRequest.ResponseFormat("json_object"));
        }

        return builder.build();
    }

    @Override
    public ChatModel getChatModel() {
        return this.chatModel;
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }

    // Initialization helpers

    private MistralAiApi createMistralAiApi() {
        String apiKey = config.getApiKey();
        String baseURL = config.getBaseURL();
        // Needs accept-encoding headers
        // https://github.com/spring-projects/spring-ai/issues/372
        return MistralAiApi.builder()
                .baseUrl(baseURL)
                .apiKey(apiKey)
                .restClientBuilder(
                        RestClient.builder().defaultHeader("Accept-Encoding", "gzip, deflate"))
                .build();
    }

    private MistralAiChatModel createChatModel() {
        MistralAiChatOptions chatOptions =
                MistralAiChatOptions.builder().temperature(null).topP(null).build();
        return MistralAiChatModel.builder()
                .defaultOptions(chatOptions)
                .mistralAiApi(this.mistralAiApi)
                .build();
    }
}
