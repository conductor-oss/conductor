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
package org.conductoross.conductor.ai.providers.openai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.Media;
import org.conductoross.conductor.ai.models.ToolSpec;
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIEmbeddingsApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIImageGenApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.Tool;
import org.conductoross.conductor.ai.providers.openai.api.OpenAISpeechApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIVideoApi;
import org.conductoross.conductor.ai.video.VideoModel;
import org.conductoross.conductor.ai.video.VideoOptions;
import org.conductoross.conductor.ai.video.VideoPrompt;
import org.conductoross.conductor.ai.video.VideoResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAI implements AIModel {

    public static final String NAME = "openai";
    private final OpenAIConfiguration config;

    // OkHttp-based API clients
    private final OpenAIResponsesApi responsesApi;
    private final OpenAIEmbeddingsApi embeddingsApi;
    private final OpenAIImageGenApi imageGenApi;
    private final OpenAISpeechApi speechApi;
    private final OpenAIVideoApi videoApi;

    // Spring AI adapter
    private final OpenAIResponsesChatModel chatModel;
    private final OpenAIHttpImageModel imageModel;
    private final OpenAIVideoModel videoModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAI(OpenAIConfiguration config) {
        this.config = config;
        long timeoutSecs = config.getTimeout() != null ? config.getTimeout().getSeconds() : 600;
        String baseUrl = config.getBaseURL();

        this.responsesApi = new OpenAIResponsesApi(config.getApiKey(), baseUrl, false, timeoutSecs);
        this.embeddingsApi =
                new OpenAIEmbeddingsApi(config.getApiKey(), baseUrl, false, timeoutSecs);
        this.imageGenApi = new OpenAIImageGenApi(config.getApiKey(), baseUrl, false, timeoutSecs);

        // Audio and Video APIs need base URL without /v1 suffix
        String baseUrlNoV1 = stripV1(baseUrl);
        this.speechApi = new OpenAISpeechApi(config.getApiKey(), baseUrl, false, timeoutSecs);
        this.videoApi = new OpenAIVideoApi(config.getApiKey(), baseUrlNoV1);

        this.chatModel = new OpenAIResponsesChatModel(responsesApi);
        this.imageModel = new OpenAIHttpImageModel(imageGenApi);
        this.videoModel = new OpenAIVideoModel(videoApi);
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

        OpenAIResponsesChatOptions.OpenAIResponsesChatOptionsBuilder builder =
                OpenAIResponsesChatOptions.builder()
                        .model(input.getModel())
                        .topP(input.getTopP())
                        .frequencyPenalty(input.getFrequencyPenalty())
                        .presencePenalty(input.getPresencePenalty())
                        .maxTokens(input.getMaxTokens())
                        .stopSequences(input.getStopWords())
                        .previousResponseId(input.getPreviousResponseId())
                        .reasoningEffort(input.getReasoningEffort())
                        .jsonOutput(input.isJsonOutput())
                        .responsesApiTools(tools.isEmpty() ? null : tools);

        // Reasoning models don't support temperature/topP/stop
        if (isReasoningModel(input.getModel())) {
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

    @Override
    public LLMResponse generateAudio(AudioGenRequest request) {
        try {
            String responseFormat =
                    request.getResponseFormat() != null
                            ? request.getResponseFormat().toLowerCase()
                            : "mp3";

            var speechRequest =
                    new OpenAISpeechApi.SpeechRequest(
                            request.getModel(),
                            request.getText(),
                            request.getVoice(),
                            responseFormat,
                            request.getSpeed());
            byte[] audioData = speechApi.createSpeech(speechRequest);

            List<Media> media = new ArrayList<>();
            media.add(Media.builder().data(audioData).mimeType("audio/*").build());
            return LLMResponse.builder().media(media).build();
        } catch (IOException e) {
            throw new RuntimeException("Speech API call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoModel getVideoModel() {
        return this.videoModel;
    }

    @Override
    public LLMResponse generateVideo(VideoGenRequest request) {
        VideoOptions options = getVideoOptions(request);
        VideoPrompt videoPrompt = new VideoPrompt(request.getPrompt(), options);
        VideoResponse response = videoModel.call(videoPrompt);

        return LLMResponse.builder()
                .jobId(response.getMetadata().getJobId())
                .finishReason(response.getMetadata().getStatus())
                .build();
    }

    @Override
    public LLMResponse checkVideoStatus(VideoGenRequest request) {
        VideoResponse response = videoModel.checkStatus(request.getJobId());
        String status = response.getMetadata().getStatus();

        LLMResponse.LLMResponseBuilder builder = LLMResponse.builder().finishReason(status);

        if ("COMPLETED".equals(status)) {
            List<Media> mediaList = new ArrayList<>();
            for (var gen : response.getResults()) {
                var video = gen.getOutput();
                String mimeType = video.getMimeType() != null ? video.getMimeType() : "video/mp4";

                if (video.getData() != null) {
                    mediaList.add(Media.builder().data(video.getData()).mimeType(mimeType).build());
                } else if (video.getB64Json() != null) {
                    mediaList.add(
                            Media.builder()
                                    .data(java.util.Base64.getDecoder().decode(video.getB64Json()))
                                    .mimeType(mimeType)
                                    .build());
                }
            }
            builder.media(mediaList);
        }

        return builder.build();
    }

    // -- Helpers --

    private List<Tool> convertTools(ChatCompletion input) {
        List<Tool> tools = new ArrayList<>();

        // OpenAI Responses API built-in tools
        if (input.isWebSearch()) {
            tools.add(Tool.webSearch());
        }
        if (input.isCodeInterpreter()) {
            tools.add(Tool.codeInterpreter());
        }
        if (input.getFileSearchVectorStoreIds() != null
                && !input.getFileSearchVectorStoreIds().isEmpty()) {
            tools.add(Tool.fileSearch(input.getFileSearchVectorStoreIds()));
        }

        // Convert Conductor ToolSpecs to Responses API function tools
        if (input.getTools() != null) {
            for (ToolSpec toolSpec : input.getTools()) {
                try {
                    Object params = toolSpec.getInputSchema();
                    tools.add(Tool.function(toolSpec.getName(), toolSpec.getDescription(), params));
                } catch (Exception e) {
                    log.warn("Failed to convert tool spec: {}", toolSpec.getName(), e);
                }
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

    private static String stripV1(String baseUrl) {
        if (baseUrl != null && baseUrl.endsWith("/v1")) {
            return baseUrl.substring(0, baseUrl.length() - 3);
        }
        return baseUrl;
    }
}
