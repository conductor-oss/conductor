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

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.Media;
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi;
import org.conductoross.conductor.ai.video.Video;
import org.conductoross.conductor.ai.video.VideoGeneration;
import org.conductoross.conductor.ai.video.VideoModel;
import org.conductoross.conductor.ai.video.VideoOptions;
import org.conductoross.conductor.ai.video.VideoPrompt;
import org.conductoross.conductor.ai.video.VideoResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import okhttp3.OkHttpClient;

public class GeminiVertex implements AIModel {

    public static final String NAME = "vertex_ai";

    public static final String ALIAS = "google_gemini";

    private final GeminiVertexConfiguration config;
    private final OkHttpClient httpClient;
    private final GeminiApi geminiApi;

    public GeminiVertex(GeminiVertexConfiguration config, OkHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.geminiApi = createApi();
    }

    private GeminiApi createApi() {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return GeminiApi.forApiKey(httpClient, config.getApiKey(), config.getBaseURL());
        }
        return GeminiApi.forVertex(httpClient, config.getProjectId(),
                config.getLocation(), config.getGoogleCredentials());
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
        try {
            GeminiApi.EmbedContentResponse resp = geminiApi.embedContent(
                    embeddingGenRequest.getModel(),
                    embeddingGenRequest.getText(),
                    embeddingGenRequest.getDimensions());
            if (resp.embedding() == null || resp.embedding().values() == null) {
                throw new RuntimeException("No embeddings returned from Gemini API");
            }
            return resp.embedding().values();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gemini embedContent failed", e);
        }
    }

    @Override
    public ChatModel getChatModel() {
        return new GeminiChatModel(geminiApi);
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        return GeminiChatOptions.builder()
                .model(input.getModel())
                .temperature(input.getTemperature())
                .maxTokens(input.getMaxTokens())
                .frequencyPenalty(input.getFrequencyPenalty())
                .presencePenalty(input.getPresencePenalty())
                .stopSequences(input.getStopWords())
                .topK(input.getTopK())
                .topP(input.getTopP())
                .googleSearchRetrieval(input.isGoogleSearchRetrieval() || input.isWebSearch())
                .codeExecution(input.isCodeInterpreter())
                .tools(
                        input.getTools() != null && !input.getTools().isEmpty()
                                ? input.getTools()
                                : null)
                .thinkingBudgetTokens(
                        input.getThinkingTokenLimit() > 0 ? input.getThinkingTokenLimit() : null)
                // Any non-blank reasoningSummary opts the request into emitting
                // thought summaries. Gemini 2.5 will not return thought text
                // without ``includeThoughts=true`` even when a budget is set.
                .includeThoughts(
                        input.getReasoningSummary() != null
                                        && !input.getReasoningSummary().isBlank()
                                ? Boolean.TRUE
                                : null)
                .build();
    }

    @Override
    public ImageModel getImageModel() {
        return new GeminiGenAI(geminiApi);
    }

    @Override
    public VideoModel getVideoModel() {
        return new GeminiVideoModel(geminiApi, httpClient);
    }

    @Override
    public LLMResponse generateVideo(VideoGenRequest request) {
        VideoOptions options = getVideoOptions(request);
        VideoPrompt videoPrompt = new VideoPrompt(request.getPrompt(), options);
        GeminiVideoModel videoModel = new GeminiVideoModel(geminiApi, httpClient);
        VideoResponse response = videoModel.call(videoPrompt);

        return LLMResponse.builder()
                .result(response.getMetadata().getJobId())
                .finishReason(response.getMetadata().getStatus())
                .build();
    }

    @Override
    public LLMResponse checkVideoStatus(VideoGenRequest request) {
        GeminiVideoModel videoModel = new GeminiVideoModel(geminiApi, httpClient);
        VideoResponse response = videoModel.checkStatus(request.getJobId());
        String status = response.getMetadata().getStatus();

        LLMResponse.LLMResponseBuilder builder = LLMResponse.builder().finishReason(status);

        if ("COMPLETED".equals(status)) {
            List<Media> mediaList = new ArrayList<>();
            for (VideoGeneration gen : response.getResults()) {
                Video video = gen.getOutput();
                String mimeType = video.getMimeType() != null ? video.getMimeType() : "video/mp4";

                if (video.getData() != null) {
                    mediaList.add(Media.builder().data(video.getData()).mimeType(mimeType).build());
                } else if (video.getB64Json() != null) {
                    mediaList.add(
                            Media.builder()
                                    .data(Base64.getDecoder().decode(video.getB64Json()))
                                    .mimeType(mimeType)
                                    .build());
                } else if (video.getUrl() != null) {
                    byte[] bytes = downloadFromUrl(video.getUrl());
                    mediaList.add(Media.builder().data(bytes).mimeType(mimeType).build());
                }
            }
            builder.media(mediaList);
        }

        return builder.build();
    }

    private byte[] downloadFromUrl(String url) {
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get().build();
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
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
        GeminiApi.SpeechConfig speechConfig = new GeminiApi.SpeechConfig(
                new GeminiApi.VoiceConfig(new GeminiApi.PrebuiltVoiceConfig(request.getVoice())));
        GeminiApi.GenerationConfig genConfig = new GeminiApi.GenerationConfig(
                null, null, null, request.getMaxTokens(), request.getStopWords(),
                request.getFrequencyPenalty() != null ? request.getFrequencyPenalty() : null,
                null, null, List.of("AUDIO"), null, speechConfig);

        try {
            GeminiApi.Content userContent = new GeminiApi.Content("user",
                    List.of(GeminiApi.Part.text(request.getText())));
            GeminiApi.GenerateContentResponse result = geminiApi.generateContent(
                    request.getModel(), List.of(userContent), null, null, genConfig);

            List<Media> media = new ArrayList<>();
            if (result.candidates() != null) {
                for (GeminiApi.Candidate c : result.candidates()) {
                    if (c.content() == null || c.content().parts() == null) continue;
                    for (GeminiApi.Part p : c.content().parts()) {
                        if (p.inlineData() != null && p.inlineData().data() != null) {
                            byte[] bytes = java.util.Base64.getDecoder()
                                    .decode(p.inlineData().data());
                            media.add(Media.builder()
                                    .data(bytes)
                                    .mimeType("audio/" + request.getResponseFormat())
                                    .build());
                        }
                    }
                }
            }
            return LLMResponse.builder().media(media).build();
        } catch (Exception e) {
            throw new RuntimeException("Gemini generateAudio failed", e);
        }
    }
}
