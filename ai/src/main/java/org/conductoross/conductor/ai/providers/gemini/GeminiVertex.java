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
import org.conductoross.conductor.ai.video.Video;
import org.conductoross.conductor.ai.video.VideoGeneration;
import org.conductoross.conductor.ai.video.VideoModel;
import org.conductoross.conductor.ai.video.VideoOptions;
import org.conductoross.conductor.ai.video.VideoPrompt;
import org.conductoross.conductor.ai.video.VideoResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.VoiceConfig;

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
        Client client = createGenAIClient();
        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
        if (embeddingGenRequest.getDimensions() != null) {
            configBuilder.outputDimensionality(embeddingGenRequest.getDimensions());
        }
        EmbedContentResponse response =
                client.models.embedContent(
                        embeddingGenRequest.getModel(),
                        embeddingGenRequest.getText(),
                        configBuilder.build());
        return response.embeddings()
                .flatMap(
                        embeddings ->
                                embeddings.isEmpty()
                                        ? java.util.Optional.empty()
                                        : java.util.Optional.of(embeddings.getFirst()))
                .flatMap(ContentEmbedding::values)
                .orElseThrow(() -> new RuntimeException("No embeddings returned from Gemini API"));
    }

    @Override
    public ChatModel getChatModel() {
        return new GeminiChatModel(createGenAIClient());
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
                .build();
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

    /** Creates a Google GenAI Client, using API key when available, otherwise Vertex AI. */
    private Client createGenAIClient() {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return Client.builder().apiKey(config.getApiKey()).build();
        }
        Client.Builder builder =
                Client.builder()
                        .vertexAI(true)
                        .location(config.getLocation())
                        .project(config.getProjectId());
        if (config.getGoogleCredentials() != null) {
            builder.credentials(config.getGoogleCredentials());
        }
        return builder.build();
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
