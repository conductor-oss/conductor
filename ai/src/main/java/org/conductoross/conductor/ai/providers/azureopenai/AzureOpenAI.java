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

import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.azure.openai.AzureOpenAiImageOptions;
import org.springframework.ai.azure.openai.AzureOpenAiResponseFormat;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.image.ImageModel;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.google.common.primitives.Floats;

public class AzureOpenAI implements AIModel {

    public static final String NAME = "azure_openai";
    private final AzureOpenAIConfiguration config;

    public AzureOpenAI(AzureOpenAIConfiguration config) {
        this.config = config;
    }

    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        AzureOpenAiEmbeddingOptions options =
                AzureOpenAiEmbeddingOptions.builder()
                        .deploymentName(embeddingGenRequest.getModel())
                        .dimensions(embeddingGenRequest.getDimensions())
                        .build();

        OpenAIClient client = getOpenAIClientBuilder().buildClient();
        AzureOpenAiEmbeddingModel model = new AzureOpenAiEmbeddingModel(client);

        EmbeddingRequest embeddingRequest =
                new EmbeddingRequest(List.of(embeddingGenRequest.getText()), options);

        float[] embeddingsResponse = model.call(embeddingRequest).getResult().getOutput();
        return Floats.asList(embeddingsResponse);
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        AzureOpenAiChatOptions.Builder builder =
                AzureOpenAiChatOptions.builder()
                        .deploymentName(input.getModel())
                        .topP(input.getTopP())
                        .stop(input.getStopWords())
                        .frequencyPenalty(input.getFrequencyPenalty())
                        .maxTokens(input.getMaxTokens())
                        .toolCallbacks(getToolCallback(input))
                        .internalToolExecutionEnabled(false)
                        .presencePenalty(input.getPresencePenalty());

        if (input.isJsonOutput()) {
            builder.responseFormat(
                    AzureOpenAiResponseFormat.builder()
                            .type(AzureOpenAiResponseFormat.Type.JSON_OBJECT)
                            .build());
        }

        // remove temperature, stop and topP for reasoning models
        if (isReasoningModel(input.getModel())) {
            builder.temperature(null);
            builder.topP(null);
            builder.stop(null);
        }

        return builder.build();
    }

    @Override
    public ChatModel getChatModel() {
        return AzureOpenAiChatModel.builder().openAIClientBuilder(getOpenAIClientBuilder()).build();
    }

    private OpenAIClientBuilder getOpenAIClientBuilder() {
        return new OpenAIClientBuilder()
                .endpoint(config.getBaseURL())
                .credential(new AzureKeyCredential(config.getApiKey()));
    }

    private boolean isReasoningModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        String model = modelName.toLowerCase();
        return model.startsWith("o1") || model.startsWith("o3") || model.startsWith("gpt-5");
    }

    @Override
    public ImageModel getImageModel() {
        OpenAIClient client = getOpenAIClientBuilder().buildClient();
        AzureOpenAiImageOptions options =
                AzureOpenAiImageOptions.builder()
                        .deploymentName(config.getDeploymentName())
                        .user(config.getUser())
                        .build();
        return new AzureOpenAiImageModel(client, options);
    }
}
