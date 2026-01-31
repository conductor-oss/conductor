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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.Media;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.vertexai.VertexAI;
import com.google.common.primitives.Floats;
import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.VoiceConfig;
import lombok.SneakyThrows;

public class GeminiVertex implements AIModel {

    public static final String NAME = "vertex_ai";

    private final GeminiVertexConfiguration config;

    public GeminiVertex(GeminiVertexConfiguration config) {
        this.config = config;
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        VertexAiTextEmbeddingOptions options =
                VertexAiTextEmbeddingOptions.builder()
                        .model(embeddingGenRequest.getModel())
                        .dimensions(embeddingGenRequest.getDimensions())
                        .build();

        VertexAiEmbeddingConnectionDetails connectionDetails =
                getVertexAiEmbeddingConnectionDetails();
        VertexAiTextEmbeddingModel model =
                new VertexAiTextEmbeddingModel(connectionDetails, options);

        float[] embeddingsResponse =
                model.embedForResponse(List.of(embeddingGenRequest.getText()))
                        .getResult()
                        .getOutput();
        return Floats.asList(embeddingsResponse);
    }

    @Override
    public ChatModel getChatModel() {
        VertexAI vertextAI = getVertexAI();
        return VertexAiGeminiChatModel.builder().vertexAI(vertextAI).build();
    }

    @SneakyThrows
    private VertexAI getVertexAI() {
        VertexAI.Builder builder = new VertexAI.Builder();
        if (config.getGoogleCredentials() != null) {
            // Scope credentials for Vertex AI
            var scopedCredentials =
                    config.getGoogleCredentials()
                            .createScoped("https://www.googleapis.com/auth/cloud-platform");
            builder = builder.setCredentials(scopedCredentials);
        }

        return builder.setProjectId(config.getProjectId())
                .setLocation(config.getLocation())
                .build();
    }

    @SneakyThrows
    private VertexAiEmbeddingConnectionDetails getVertexAiEmbeddingConnectionDetails() {
        var builder =
                VertexAiEmbeddingConnectionDetails.builder()
                        .projectId(config.getProjectId())
                        .location(config.getLocation())
                        .apiEndpoint(config.getBaseURL());

        // Only configure custom credentials if available
        if (config.getGoogleCredentials() != null) {
            // Scope credentials for Vertex AI
            var scopedCredentials =
                    config.getGoogleCredentials()
                            .createScoped("https://www.googleapis.com/auth/cloud-platform");
            builder.predictionServiceSettings(
                    PredictionServiceSettings.newBuilder()
                            .setEndpoint(config.getBaseURL())
                            .setCredentialsProvider(
                                    FixedCredentialsProvider.create(scopedCredentials))
                            .build());
        }

        return builder.build();
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        List<ToolCallback> toolCallbacks = getToolCallback(input);
        Set<String> toolNames =
                toolCallbacks.stream()
                        .map(tc -> tc.getToolDefinition().name())
                        .collect(Collectors.toSet());

        return VertexAiGeminiChatOptions.builder()
                .model(input.getModel())
                .temperature(input.getTemperature())
                .maxOutputTokens(input.getMaxTokens())
                .frequencyPenalty(input.getFrequencyPenalty())
                .internalToolExecutionEnabled(false)
                .presencePenalty(input.getPresencePenalty())
                .stopSequences(input.getStopWords())
                .toolCallbacks(toolCallbacks)
                .toolNames(toolNames)
                .topK(input.getTopK())
                .topP(input.getTopP())
                .googleSearchRetrieval(input.isGoogleSearchRetrieval())
                .build();
    }

    @Override
    public ImageModel getImageModel() {
        return new GeminiGenAI(
                Client.builder()
                        .vertexAI(true)
                        .credentials(config.getGoogleCredentials())
                        .location(config.getLocation())
                        .project(config.getProjectId())
                        .build());
    }

    @Override
    public LLMResponse generateAudio(AudioGenRequest request) {
        var client =
                Client.builder()
                        .vertexAI(true)
                        .credentials(config.getGoogleCredentials())
                        .location(config.getLocation())
                        .project(config.getProjectId())
                        .build();
        GenerateContentConfig config =
                GenerateContentConfig.builder()
                        .speechConfig(
                                SpeechConfig.builder()
                                        .voiceConfig(
                                                VoiceConfig.builder()
                                                        .prebuiltVoiceConfig(
                                                                PrebuiltVoiceConfig.builder()
                                                                        .voiceName(
                                                                                request.getVoice())
                                                                        .build())
                                                        .build())
                                        .build())
                        .responseModalities(List.of("AUDIO"))
                        .frequencyPenalty(
                                request.getFrequencyPenalty() != null
                                        ? request.getFrequencyPenalty().floatValue()
                                        : null)
                        .maxOutputTokens(request.getMaxTokens())
                        .stopSequences(request.getStopWords())
                        .build();
        GenerateContentResponse response =
                client.models.generateContent(request.getModel(), request.getText(), config);
        List<Candidate> candiates = response.candidates().orElse(new ArrayList<>());
        List<Media> media = new ArrayList<>();
        for (Candidate candiate : candiates) {
            candiate.content()
                    .flatMap(Content::parts)
                    .ifPresent(
                            parts -> {
                                parts.forEach(
                                        part ->
                                                part.inlineData()
                                                        .flatMap(Blob::data)
                                                        .ifPresent(
                                                                bytes -> {
                                                                    media.add(
                                                                            Media.builder()
                                                                                    .data(bytes)
                                                                                    .mimeType(
                                                                                            "audio/"
                                                                                                    + request
                                                                                                            .getResponseFormat())
                                                                                    .build());
                                                                }));
                            });
        }
        return LLMResponse.builder().media(media).build();
    }
}
