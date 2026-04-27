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
package org.conductoross.conductor.ai.providers.azureopenai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.ToolSpec;
import org.conductoross.conductor.ai.providers.openai.OpenAIHttpImageModel;
import org.conductoross.conductor.ai.providers.openai.OpenAIResponsesChatModel;
import org.conductoross.conductor.ai.providers.openai.OpenAIResponsesChatOptions;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIEmbeddingsApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIImageGenApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.Tool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Azure OpenAI provider backed by OkHttp calls to the Azure OpenAI Responses API.
 *
 * <p>Uses the same API classes as the OpenAI provider but with Azure-specific authentication
 * (api-key header) and base URL format.
 */
@Slf4j
public class AzureOpenAI implements AIModel {

    public static final String NAME = "azure_openai";
    private final AzureOpenAIConfiguration config;

    private final OpenAIResponsesApi responsesApi;
    private final OpenAIEmbeddingsApi embeddingsApi;
    private final OpenAIImageGenApi imageGenApi;
    private final OpenAIResponsesChatModel chatModel;
    private final OpenAIHttpImageModel imageModel;

    public AzureOpenAI(AzureOpenAIConfiguration config) {
        this.config = config;
        long timeoutSecs = config.getTimeout() != null ? config.getTimeout().getSeconds() : 600;

        // Azure base URL format: https://RESOURCE.openai.azure.com/openai/v1
        String baseUrl = toAzureV1Url(config.getBaseURL());

        this.responsesApi = new OpenAIResponsesApi(config.getApiKey(), baseUrl, true, timeoutSecs);
        this.embeddingsApi =
                new OpenAIEmbeddingsApi(config.getApiKey(), baseUrl, true, timeoutSecs);
        this.imageGenApi = new OpenAIImageGenApi(config.getApiKey(), baseUrl, true, timeoutSecs);
        this.chatModel = new OpenAIResponsesChatModel(responsesApi);
        this.imageModel = new OpenAIHttpImageModel(imageGenApi);
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        try {
            var request =
                    new OpenAIEmbeddingsApi.EmbeddingRequest(
                            embeddingGenRequest.getModel(),
                            embeddingGenRequest.getText(),
                            embeddingGenRequest.getDimensions());
            var result = embeddingsApi.createEmbeddings(request);
            if (result.data() != null && !result.data().isEmpty()) {
                return result.data().getFirst().embedding();
            }
            return List.of();
        } catch (IOException e) {
            throw new RuntimeException("Embeddings API call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        List<Tool> tools = convertTools(input);

        // Azure uses deployment name as the model
        String model = input.getModel();

        OpenAIResponsesChatOptions.OpenAIResponsesChatOptionsBuilder builder =
                OpenAIResponsesChatOptions.builder()
                        .model(model)
                        .topP(input.getTopP())
                        .frequencyPenalty(input.getFrequencyPenalty())
                        .presencePenalty(input.getPresencePenalty())
                        .maxTokens(input.getMaxTokens())
                        .stopSequences(input.getStopWords())
                        .previousResponseId(input.getPreviousResponseId())
                        .reasoningEffort(input.getReasoningEffort())
                        .jsonOutput(input.isJsonOutput())
                        .responsesApiTools(tools.isEmpty() ? null : tools);

        if (isReasoningModel(model)) {
            builder.temperature(null);
            builder.topP(null);
            builder.stopSequences(null);
        }

        return builder.build();
    }

    @Override
    public ChatModel getChatModel() {
        return this.chatModel;
    }

    @Override
    public ImageModel getImageModel() {
        return this.imageModel;
    }

    // -- Helpers --

    private List<Tool> convertTools(ChatCompletion input) {
        List<Tool> tools = new ArrayList<>();
        if (input.getTools() != null) {
            for (ToolSpec toolSpec : input.getTools()) {
                tools.add(
                        Tool.function(
                                toolSpec.getName(),
                                toolSpec.getDescription(),
                                toolSpec.getInputSchema()));
            }
        }
        return tools;
    }

    private boolean isReasoningModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        String model = modelName.toLowerCase();
        return model.startsWith("o1")
                || model.startsWith("o3")
                || model.startsWith("o4")
                || model.startsWith("gpt-5");
    }

    /**
     * Converts an Azure OpenAI endpoint to the v1 URL format expected by the Responses API. Input:
     * "https://resource.openai.azure.com" → Output: "https://resource.openai.azure.com/openai/v1"
     */
    private static String toAzureV1Url(String baseUrl) {
        if (baseUrl == null) return null;
        // If already has /openai/v1, return as-is
        if (baseUrl.endsWith("/openai/v1")) return baseUrl;
        // Strip trailing slash
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return url + "/openai/v1";
    }
}
