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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.Media;
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIVideoApi;
import org.conductoross.conductor.ai.video.Video;
import org.conductoross.conductor.ai.video.VideoGeneration;
import org.conductoross.conductor.ai.video.VideoModel;
import org.conductoross.conductor.ai.video.VideoOptions;
import org.conductoross.conductor.ai.video.VideoPrompt;
import org.conductoross.conductor.ai.video.VideoResponse;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.openaisdk.OpenAiSdkChatOptions;
import org.springframework.ai.openaisdk.OpenAiSdkImageModel;
import org.springframework.ai.openaisdk.OpenAiSdkImageOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.LinkedMultiValueMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAI implements AIModel {

    public static final String NAME = "openai";
    private final OpenAIConfiguration config;

    // Cached instances
    private final OpenAiApi openAiApi;
    private final OpenAiSdkChatModel chatModel;
    private final OpenAiEmbeddingModel embeddingModel;
    private final OpenAiSdkImageModel imageModel;
    private final OpenAiAudioApi audioApi;
    private final OpenAiAudioSpeechModel speechModel;
    private final OpenAIVideoModel videoModel;

    public OpenAI(OpenAIConfiguration config) {
        this.config = config;
        this.openAiApi = createOpenAiApi();
        this.chatModel = createChatModel();
        this.embeddingModel = new OpenAiEmbeddingModel(this.openAiApi);
        this.imageModel = createImageModel();
        this.audioApi = createAudioApi();
        this.speechModel = new OpenAiAudioSpeechModel(this.audioApi);
        this.videoModel = createVideoModel();
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        EmbeddingOptions options =
                EmbeddingOptions.builder()
                        .model(embeddingGenRequest.getModel())
                        .dimensions(embeddingGenRequest.getDimensions())
                        .build();
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

        List<String> outputModalities = null;
        OpenAiSdkChatOptions.AudioParameters audioParameters = null;
        if (input.getOutputMimeType() != null && input.getOutputMimeType().startsWith("audio/")) {
            outputModalities = new ArrayList<>();
            outputModalities.add("text");
            outputModalities.add("audio");
            OpenAiSdkChatOptions.AudioParameters.AudioResponseFormat responseFormat =
                    OpenAiSdkChatOptions.AudioParameters.AudioResponseFormat.MP3;
            OpenAiSdkChatOptions.AudioParameters.Voice voice =
                    OpenAiSdkChatOptions.AudioParameters.Voice.ALLOY;
            try {
                responseFormat =
                        OpenAiSdkChatOptions.AudioParameters.AudioResponseFormat.valueOf(
                                input.getOutputMimeType().replace("audio/", ""));
            } catch (Exception ignored) {
            }

            try {
                voice = OpenAiSdkChatOptions.AudioParameters.Voice.valueOf(input.getVoice());
            } catch (Exception ignored) {
            }

            audioParameters = new OpenAiSdkChatOptions.AudioParameters(voice, responseFormat);
        }

        OpenAiSdkChatOptions.Builder builder =
                OpenAiSdkChatOptions.builder()
                        .model(input.getModel())
                        .topP(input.getTopP())
                        .stop(input.getStopWords())
                        .outputModalities(outputModalities)
                        .outputAudio(audioParameters)
                        .frequencyPenalty(input.getFrequencyPenalty())
                        .maxCompletionTokens(input.getMaxTokens())
                        .toolCallbacks(toolCallbacks)
                        .toolNames(toolNames)
                        .model(input.getModel())
                        .internalToolExecutionEnabled(false)
                        .reasoningEffort(input.getReasoningEffort())
                        .presencePenalty(input.getPresencePenalty());

        if (input.isJsonOutput()) {
            builder.responseFormat(
                    OpenAiSdkChatModel.ResponseFormat.builder()
                            .type(OpenAiSdkChatModel.ResponseFormat.Type.JSON_OBJECT)
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
        return this.chatModel;
    }

    @Override
    public ImageModel getImageModel() {
        return this.imageModel;
    }

    @Override
    public LLMResponse generateAudio(AudioGenRequest request) {
        var options = getSpeechOptions(request);

        var prompt = new TextToSpeechPrompt(request.getText(), options);
        var response = speechModel.call(prompt);
        List<Media> media = new ArrayList<>();
        for (Speech result : response.getResults()) {
            byte[] data = result.getOutput();
            media.add(Media.builder().data(data).mimeType("audio/*").build());
        }
        return LLMResponse.builder().media(media).build();
    }

    public OpenAiAudioSpeechOptions getSpeechOptions(AudioGenRequest request) {
        OpenAiAudioApi.SpeechRequest.AudioResponseFormat responseFormat =
                OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3;
        try {
            if (request.getResponseFormat() != null) {
                responseFormat =
                        OpenAiAudioApi.SpeechRequest.AudioResponseFormat.valueOf(
                                request.getResponseFormat().toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {
        }
        return OpenAiAudioSpeechOptions.builder()
                .responseFormat(responseFormat)
                .speed(request.getSpeed())
                .model(request.getModel())
                .voice(request.getVoice())
                .build();
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
            for (VideoGeneration gen : response.getResults()) {
                Video video = gen.getOutput();
                // Use the mime type from the Video if set, default to video/mp4
                String mimeType =
                        video.getMimeType() != null ? video.getMimeType() : "video/mp4";

                // Prefer direct byte data to avoid redundant base64 decode
                if (video.getData() != null) {
                    mediaList.add(
                            Media.builder()
                                    .data(video.getData())
                                    .mimeType(mimeType)
                                    .build());
                } else if (video.getB64Json() != null) {
                    // Fallback to base64 decoding if data field not populated
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

    // Initialization helpers

    private OpenAiApi createOpenAiApi() {
        String apiKey = config.getApiKey();
        String baseURL = config.getBaseURL();
        // OpenAiApi appends /v1 automatically, so we must remove it if present to avoid
        // 404
        if (baseURL != null && baseURL.endsWith("/v1")) {
            baseURL = baseURL.substring(0, baseURL.length() - 3);
        }
        return OpenAiApi.builder().apiKey(apiKey).baseUrl(baseURL).build();
    }

    private OpenAiSdkChatModel createChatModel() {
        OpenAiSdkChatOptions opts =
                OpenAiSdkChatOptions.builder()
                        .temperature(null)
                        .topP(null)
                        .stop(null)
                        .organizationId(config.getOrganizationId())
                        .apiKey(config.getApiKey())
                        .baseUrl(config.getBaseURL())
                        .customHeaders(Map.of())
                        .build();
        return new OpenAiSdkChatModel(opts);
    }

    private OpenAiSdkImageModel createImageModel() {
        return new OpenAiSdkImageModel(
                OpenAiSdkImageOptions.builder()
                        .organizationId(config.getOrganizationId())
                        .apiKey(config.getApiKey())
                        .baseUrl(config.getBaseURL())
                        .build());
    }

    private OpenAiAudioApi createAudioApi() {
        LinkedMultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (StringUtils.isNotBlank(config.getOrganizationId())) {
            headers.put("OpenAI-Organization", List.of(config.getOrganizationId()));
        }

        // OpenAiAudioApi appends /v1 automatically, so we must remove it if present to
        // avoid 404
        String baseURL = config.getBaseURL();
        if (baseURL != null && baseURL.endsWith("/v1")) {
            baseURL = baseURL.substring(0, baseURL.length() - 3);
        }

        return OpenAiAudioApi.builder()
                .apiKey(config.getApiKey())
                .baseUrl(baseURL)
                .headers(headers)
                .build();
    }

    private OpenAIVideoModel createVideoModel() {
        // OpenAIVideoApi manages its own /v1/ path, so strip /v1 from base URL
        String baseUrl = config.getBaseURL();
        if (baseUrl != null && baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        OpenAIVideoApi videoApi = new OpenAIVideoApi(config.getApiKey(), baseUrl);
        return new OpenAIVideoModel(videoApi);
    }

    // Private methods
    private boolean isReasoningModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        String model = modelName.toLowerCase();
        return model.startsWith("o1") || model.startsWith("o3") || model.startsWith("gpt-5");
    }
}
