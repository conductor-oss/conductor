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
package org.conductoross.conductor.ai.providers.huggingface;

import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.http.AIHttpClients;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.model.ToolSpec;
import org.conductoross.conductor.ai.providers.openai.OpenAIResponsesChatModel;
import org.conductoross.conductor.ai.providers.openai.OpenAIResponsesChatOptions;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.Tool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import okhttp3.OkHttpClient;

/**
 * HuggingFace provider, backed by HuggingFace's OpenAI-compatible <b>router</b> endpoint
 * ({@code https://router.huggingface.co/v1}). This reuses {@link OpenAIResponsesChatModel} (the same
 * Responses-API model used by OpenAI/Azure), which converts multimodal input — text plus
 * {@code input_image} content parts — so vision-capable HuggingFace models receive images.
 *
 * <p>Auth is a Bearer token (the OpenAI, non-Azure, header). Image support is model-dependent.
 */
public class HuggingFace implements AIModel {

    public static final String NAME = "huggingface";
    private final HuggingFaceConfiguration config;
    private final OpenAIResponsesChatModel chatModel;

    public HuggingFace(HuggingFaceConfiguration config) {
        this(config, AIHttpClients.defaultClient());
    }

    public HuggingFace(HuggingFaceConfiguration config, OkHttpClient httpClient) {
        this.config = config;
        // Bearer auth (azureAuth=false via the 3-arg constructor). baseURL is the
        // router /v1 root; the client appends /responses.
        OpenAIResponsesApi responsesApi =
                new OpenAIResponsesApi(httpClient, config.getApiKey(), config.getBaseURL());
        this.chatModel = new OpenAIResponsesChatModel(responsesApi);
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
        List<Tool> tools = convertTools(input);
        return OpenAIResponsesChatOptions.builder()
                .model(input.getModel())
                .temperature(input.getTemperature())
                .topP(input.getTopP())
                .frequencyPenalty(input.getFrequencyPenalty())
                .presencePenalty(input.getPresencePenalty())
                .maxTokens(input.getMaxTokens())
                .stopSequences(input.getStopWords())
                .jsonOutput(input.isJsonOutput())
                .responsesApiTools(tools.isEmpty() ? null : tools)
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
}
