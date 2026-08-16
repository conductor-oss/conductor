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
package org.conductoross.conductor.ai;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.conductoross.conductor.ai.model.AudioGenRequest;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.model.ImageGenRequest;
import org.conductoross.conductor.ai.model.LLMResponse;
import org.conductoross.conductor.ai.model.ToolSpec;
import org.conductoross.conductor.ai.model.VideoGenRequest;
import org.conductoross.conductor.ai.video.VideoModel;
import org.conductoross.conductor.ai.video.VideoOptions;
import org.conductoross.conductor.ai.video.VideoOptionsBuilder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Interface for LLM implementations. */
public interface AIModel {

    enum ConductorTask {
        CHAT_COMPLETE,
        GENERATE_IMAGE,
        GENERATE_VIDEO,
        TEXT_TO_SPEECH
    }

    ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    /**
     * @return name of the foundation model provider. e.g. openai, anthropic etc.
     */
    String getModelProvider();

    /**
     * @return alternative provider names that resolve to this same provider
     */
    default List<String> getProviderAliases() {
        return List.of();
    }

    /**
     * Whether this provider accepts a chat-completion request whose last message has the {@code
     * assistant} role — i.e. assistant-message prefill, used to nudge the model's response to start
     * a certain way (and, in this codebase, the format in which {@link
     * org.conductoross.conductor.ai.tasks.mapper.ChatCompleteTaskMapper}'s loop-history injection
     * carries prior DO_WHILE iteration outputs back into the next iteration).
     *
     * <p>When this returns {@code false}, the mapper suppresses the same-refName loop-iteration
     * assistant injection: those auto-attached assistant messages would either be rejected outright
     * by the provider's API (e.g. Anthropic Claude Sonnet 4.6+ returns {@code 400 "This model does
     * not support assistant message prefill. The conversation must end with a user message."}) or
     * be silently mis-handled. Participants, tool calls, and sub-workflow context are unaffected by
     * this flag.
     *
     * <p>Default is {@code true} to preserve historical behavior for providers (OpenAI Responses
     * API with {@code previousResponseId}, Gemini, etc.) that accept trailing assistant messages.
     */
    default boolean supportsAssistantPrefill() {
        return true;
    }

    /**
     * Embedding generation
     *
     * @param embeddingGenRequest request
     * @return embeddings
     */
    List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest);

    /**
     * @return Chat Completion model
     */
    ChatModel getChatModel();

    /**
     * @param input request to do chat completion
     * @return Options
     */
    default ChatOptions getChatOptions(ChatCompletion input) {
        return ToolCallingChatOptions.builder()
                .model(input.getModel())
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

    /**
     * @param input Image gen request
     * @return Options
     */
    default ImageOptions getImageOptions(ImageGenRequest input) {
        return ImageOptionsBuilder.builder()
                .model(input.getModel())
                .height(input.getHeight())
                .width(input.getWidth())
                .N(input.getN())
                .responseFormat("b64_json")
                .style(input.getStyle())
                .build();
    }

    /**
     * @return Model to generate images
     */
    ImageModel getImageModel();

    /**
     * @param input Video gen request
     * @return Options
     */
    default VideoOptions getVideoOptions(VideoGenRequest input) {
        return VideoOptionsBuilder.builder()
                .model(input.getModel())
                .duration(input.getDuration())
                .width(input.getWidth())
                .height(input.getHeight())
                .fps(input.getFps())
                .outputFormat(input.getOutputFormat())
                .n(input.getN())
                .style(input.getStyle())
                .motion(input.getMotion())
                .seed(input.getSeed())
                .guidanceScale(input.getGuidanceScale())
                .aspectRatio(input.getAspectRatio())
                .generateThumbnail(input.getGenerateThumbnail())
                .thumbnailTimestamp(input.getThumbnailTimestamp())
                .inputImage(input.getInputImage())
                .negativePrompt(input.getNegativePrompt())
                .personGeneration(input.getPersonGeneration())
                .resolution(input.getResolution())
                .generateAudio(input.getGenerateAudio())
                .size(input.getSize())
                .build();
    }

    /**
     * @return Model to generate videos
     */
    default VideoModel getVideoModel() {
        return null; // Default: video generation not supported
    }

    /**
     * Generate video (async job submission)
     *
     * @param request Video generation request
     * @return Response with job ID
     */
    default LLMResponse generateVideo(VideoGenRequest request) {
        throw new UnsupportedOperationException("Video generation not supported by this provider");
    }

    /**
     * Check video generation job status (polling)
     *
     * @param request Video generation request with jobId
     * @return Response with current status or completed video
     */
    default LLMResponse checkVideoStatus(VideoGenRequest request) {
        throw new UnsupportedOperationException("Video generation not supported by this provider");
    }

    default LLMResponse generateAudio(AudioGenRequest request) {
        throw new UnsupportedOperationException();
    }

    default List<ToolCallback> getToolCallback(ChatCompletion input) {
        if (input.getTools() == null || input.getTools().isEmpty()) {
            return List.of();
        }
        List<ToolCallback> functions = new ArrayList<>();
        try {
            for (ToolSpec tool : input.getTools()) {
                FunctionToolCallback<Object, Object> function =
                        FunctionToolCallback.builder(tool.getName(), Function.identity())
                                .description(tool.getDescription())
                                .inputSchema(objectMapper.writeValueAsString(tool.getInputSchema()))
                                .inputType(Map.class) // does not matter, we are not doing
                                // internal tool calling!
                                .build();
                functions.add(function);
            }
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
        return functions;
    }

    static URI getURI(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return new URI(input);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
