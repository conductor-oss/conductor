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
package org.conductoross.conductor.ai.providers.bedrock;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Slf4j
public class Bedrock implements AIModel {
    private static final ObjectMapper om = new ObjectMapperProvider().getObjectMapper();

    public static final String NAME = "bedrock";
    private final BedrockConfiguration config;

    public Bedrock(BedrockConfiguration config) {
        this.config = config;
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @SneakyThrows
    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        String modelId = embeddingGenRequest.getModel();
        if (!modelId.startsWith("cohere.")) {
            throw new RuntimeException("Unsupported model " + modelId);
        }
        var client =
                BedrockRuntimeClient.builder()
                        .credentialsProvider(config.getAwsCredentialsProvider())
                        .region(Region.of(config.getRegion()))
                        .build();
        Map<String, Object> requestMap =
                getEmbeddingRequest(embeddingGenRequest.getModel(), embeddingGenRequest.getText());
        byte[] body = om.writeValueAsBytes(requestMap);
        InvokeModelRequest request =
                InvokeModelRequest.builder()
                        .modelId(modelId)
                        .body(SdkBytes.fromByteArray(body))
                        .build();
        InvokeModelResponse response = client.invokeModel(request);
        byte[] byteArray = response.body().asByteArray();
        Map<String, Map<String, Object>> ressMap = om.readValue(byteArray, Map.class);
        List<List<Float>> floats = (List<List<Float>>) ressMap.get("embeddings").get("float");
        return floats.get(0);
    }

    @Override
    public ChatModel getChatModel() {
        var clientBuilder =
                BedrockRuntimeClient.builder()
                        .credentialsProvider(config.getAwsCredentialsProvider())
                        .region(Region.of(config.getRegion()));

        // Add bearer token interceptor if configured
        if (config.isBearerTokenConfigured()) {
            clientBuilder.overrideConfiguration(
                    c ->
                            c.addExecutionInterceptor(
                                    new BearerTokenInterceptor(config.getBearerToken())));
        }

        var client = clientBuilder.build();
        return BedrockProxyChatModel.builder()
                .credentialsProvider(config.getAwsCredentialsProvider())
                .bedrockRuntimeClient(client)
                .build();
    }

    /** Execution interceptor to add bearer token authorization header. */
    private static class BearerTokenInterceptor implements ExecutionInterceptor {
        private final String bearerToken;

        BearerTokenInterceptor(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        @Override
        public SdkHttpRequest modifyHttpRequest(
                Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
            return context.httpRequest().toBuilder()
                    .putHeader("Authorization", "Bearer " + bearerToken)
                    .build();
        }
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        String model = input.getModel();
        log.info("\n\nusing bedrock model: {}", model);
        return BedrockChatOptions.builder()
                .model(model)
                .maxTokens(input.getMaxTokens())
                .topP(input.getTopP())
                .temperature(input.getTemperature())
                .toolCallbacks(getToolCallback(input))
                .stopSequences(input.getStopWords())
                .frequencyPenalty(input.getFrequencyPenalty())
                .topK(input.getTopK())
                .internalToolExecutionEnabled(false)
                .presencePenalty(input.getPresencePenalty())
                .build();
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }

    private Map<String, Object> getEmbeddingRequest(String modelId, String text) {
        return Map.of(
                "input_type", "search_document",
                "embedding_types", List.of("float"),
                "texts", List.of(text));
    }

    @SneakyThrows
    private List<Float> extractEmbeddings(String modelId, InvokeModelResponse response) {
        if (!modelId.startsWith("cohere.")) {
            throw new RuntimeException("Unsupported model " + modelId);
        }
        byte[] byteArray = response.body().asByteArray();
        Map<String, Map<String, Object>> ressMap = om.readValue(byteArray, Map.class);
        List<List<Float>> floats = (List<List<Float>>) ressMap.get("embeddings").get("float");
        return floats.get(0);
    }
}
