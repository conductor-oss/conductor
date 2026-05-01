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
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.Media;
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.ai.video.Video;
import org.conductoross.conductor.ai.video.VideoGeneration;
import org.conductoross.conductor.ai.video.VideoModel;
import org.conductoross.conductor.ai.video.VideoOptions;
import org.conductoross.conductor.ai.video.VideoPrompt;
import org.conductoross.conductor.ai.video.VideoResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.retry.support.RetryTemplate;

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
import io.micrometer.observation.ObservationRegistry;
import lombok.SneakyThrows;

public class GeminiVertex implements AIModel {

    public static final String NAME = "vertex_ai";

    public static final String ALIAS = "google_gemini";

    private final GeminiVertexConfiguration config;

    public GeminiVertex(GeminiVertexConfiguration config) {
        this.config = config;
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<String> getProviderAliases() {
        return List.of(ALIAS);
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
        // API key path: use GoogleGenAiChatModel (REST, works with AI Studio keys)
        if (config.getApiKey() != null
                && !config.getApiKey().isBlank()
                && config.getGoogleCredentials() == null) {
            Client genAiClient = createGenAIClient();
            return new GoogleGenAiChatModel(
                    genAiClient,
                    GoogleGenAiChatOptions.builder()
                            .model("gemini-2.5-flash") // default, overridden per-call by
                            // getChatOptions()
                            .build(),
                    DefaultToolCallingManager.builder().build(),
                    RetryTemplate.defaultInstance(),
                    ObservationRegistry.NOOP);
        }
        // Vertex AI path: use gRPC with GCP IAM credentials
        VertexAI vertexAI = getVertexAI();
        return VertexAiGeminiChatModel.builder().vertexAI(vertexAI).build();
    }

    @SneakyThrows
    private VertexAI getVertexAI() {
        VertexAI.Builder builder = new VertexAI.Builder();
        if (config.getGoogleCredentials() != null) {
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

        // For API key path: use GoogleGenAiChatOptions
        // Build tool callbacks with proper schemas from ChatCompletion.getTools()
        if (config.getApiKey() != null
                && !config.getApiKey().isBlank()
                && config.getGoogleCredentials() == null) {

            List<ToolCallback> genaiCallbacks = buildGenAiToolCallbacks(input);
            Set<String> genaiToolNames =
                    genaiCallbacks.stream()
                            .map(tc -> tc.getToolDefinition().name())
                            .collect(Collectors.toSet());

            return GoogleGenAiChatOptions.builder()
                    .model(input.getModel())
                    .temperature(input.getTemperature())
                    .maxOutputTokens(input.getMaxTokens())
                    .frequencyPenalty(input.getFrequencyPenalty())
                    .internalToolExecutionEnabled(false)
                    .presencePenalty(input.getPresencePenalty())
                    .stopSequences(input.getStopWords())
                    .toolCallbacks(genaiCallbacks)
                    .toolNames(genaiToolNames)
                    .topK(input.getTopK())
                    .topP(input.getTopP())
                    .googleSearchRetrieval(input.isGoogleSearchRetrieval())
                    .build();
        }

        // Vertex AI path: use VertexAiGeminiChatOptions
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

    /**
     * Build ToolCallbacks with proper inputSchema from ChatCompletion.getTools(). Conductor's
     * default getToolCallback() creates callbacks with null inputSchema, which causes
     * GoogleGenAiToolCallingManager to crash.
     */
    private List<ToolCallback> buildGenAiToolCallbacks(ChatCompletion input) {
        if (input.getTools() == null || input.getTools().isEmpty()) {
            return List.of();
        }

        List<ToolCallback> callbacks = new ArrayList<>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (var toolSpec : input.getTools()) {
            String schemaJson = "{}";
            if (toolSpec.getInputSchema() != null && !toolSpec.getInputSchema().isEmpty()) {
                try {
                    schemaJson = mapper.writeValueAsString(toolSpec.getInputSchema());
                } catch (Exception e) {
                    // fallback
                }
            }

            var toolDef =
                    ToolDefinition.builder()
                            .name(toolSpec.getName())
                            .description(
                                    toolSpec.getDescription() != null
                                            ? toolSpec.getDescription()
                                            : "")
                            .inputSchema(schemaJson)
                            .build();

            // Create a no-op callback — Conductor manages tool execution externally
            callbacks.add(
                    new ToolCallback() {
                        @Override
                        public ToolDefinition getToolDefinition() {
                            return toolDef;
                        }

                        @Override
                        public String call(String toolInput) {
                            return "{}";
                        }
                    });
        }

        return callbacks;
    }

    @Override
    public ImageModel getImageModel() {
        return new GeminiGenAI(createGenAIClient());
    }

    @Override
    public VideoModel getVideoModel() {
        return new GeminiVideoModel(createGenAIClient());
    }

    @Override
    public LLMResponse generateVideo(VideoGenRequest request) {
        VideoOptions options = getVideoOptions(request);
        VideoPrompt videoPrompt = new VideoPrompt(request.getPrompt(), options);
        GeminiVideoModel videoModel = new GeminiVideoModel(createGenAIClient());
        VideoResponse response = videoModel.call(videoPrompt);

        return LLMResponse.builder()
                .result(response.getMetadata().getJobId())
                .finishReason(response.getMetadata().getStatus())
                .build();
    }

    @Override
    public LLMResponse checkVideoStatus(VideoGenRequest request) {
        GeminiVideoModel videoModel = new GeminiVideoModel(createGenAIClient());
        VideoResponse response = videoModel.checkStatus(request.getJobId());
        String status = response.getMetadata().getStatus();

        LLMResponse.LLMResponseBuilder builder = LLMResponse.builder().finishReason(status);

        if ("COMPLETED".equals(status)) {
            List<Media> mediaList = new ArrayList<>();
            for (VideoGeneration gen : response.getResults()) {
                Video video = gen.getOutput();
                // Use the mime type from the Video if set, default to video/mp4
                String mimeType = video.getMimeType() != null ? video.getMimeType() : "video/mp4";

                // Prefer direct byte data to avoid redundant operations
                if (video.getData() != null) {
                    mediaList.add(Media.builder().data(video.getData()).mimeType(mimeType).build());
                } else if (video.getB64Json() != null) {
                    // Fallback to base64 decoding if data field not populated
                    mediaList.add(
                            Media.builder()
                                    .data(Base64.getDecoder().decode(video.getB64Json()))
                                    .mimeType(mimeType)
                                    .build());
                } else if (video.getUrl() != null) {
                    // Last resort: Download from URL (e.g., GCS URI) to get bytes
                    byte[] bytes = downloadFromUrl(video.getUrl());
                    mediaList.add(Media.builder().data(bytes).mimeType(mimeType).build());
                }
            }
            builder.media(mediaList);
        }

        return builder.build();
    }

    /** Creates a Google GenAI Client, using API key when available, otherwise Vertex AI. */
    private Client createGenAIClient() {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return Client.builder().apiKey(config.getApiKey()).build();
        }
        return Client.builder()
                .vertexAI(true)
                .credentials(config.getGoogleCredentials())
                .location(config.getLocation())
                .project(config.getProjectId())
                .build();
    }

    private byte[] downloadFromUrl(String url) {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get().build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("Empty response downloading from " + url);
            }
            return response.body().bytes();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download from " + url, e);
        }
    }

    @Override
    public LLMResponse generateAudio(AudioGenRequest request) {
        var client = createGenAIClient();
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
