/*
 * Copyright 2026 Conductor Authors.
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

import org.conductoross.conductor.ai.video.*;

import com.google.genai.Client;
import com.google.genai.types.GenerateVideosConfig;
import com.google.genai.types.GenerateVideosOperation;
import com.google.genai.types.GenerateVideosResponse;
import com.google.genai.types.GeneratedVideo;
import com.google.genai.types.Image;
import lombok.extern.slf4j.Slf4j;

/**
 * Gemini Veo video model implementation using the Google GenAI SDK.
 *
 * <p>Implements {@link AsyncVideoModel} for Google's Veo video generation models (veo-2.0, veo-3.0,
 * veo-3.1). Supports text-to-video and image-to-video generation via Vertex AI.
 *
 * <p>The async flow uses long-running operations:
 *
 * <ol>
 *   <li>{@link #call(VideoPrompt)} submits via {@code client.models.generateVideos()} returning an
 *       operation name
 *   <li>{@link #checkStatus(String)} polls via {@code client.operations.getVideosOperation()}
 *   <li>When {@code operation.done()} is true, video bytes are extracted from the response
 * </ol>
 */
@Slf4j
public class GeminiVideoModel implements AsyncVideoModel {

    private final Client client;

    public GeminiVideoModel(Client client) {
        this.client = client;
    }

    @Override
    public VideoResponse call(VideoPrompt prompt) {
        try {
            VideoOptions opts = prompt.getOptions();
            String text = prompt.getInstructions().getFirst().getText();

            // Build GenerateVideosConfig from VideoOptions
            GenerateVideosConfig.Builder configBuilder =
                    GenerateVideosConfig.builder()
                            .numberOfVideos(opts.getN() != null ? opts.getN() : 1);

            if (opts.getDuration() != null) {
                configBuilder.durationSeconds(opts.getDuration());
            }
            if (opts.getAspectRatio() != null) {
                configBuilder.aspectRatio(opts.getAspectRatio());
            }
            if (opts.getSeed() != null) {
                configBuilder.seed(opts.getSeed());
            }
            if (opts.getNegativePrompt() != null) {
                configBuilder.negativePrompt(opts.getNegativePrompt());
            }
            if (opts.getPersonGeneration() != null) {
                configBuilder.personGeneration(opts.getPersonGeneration());
            }
            if (opts.getResolution() != null) {
                configBuilder.resolution(opts.getResolution());
            }
            if (opts.getGenerateAudio() != null) {
                configBuilder.generateAudio(opts.getGenerateAudio());
            }
            if (opts.getFps() != null) {
                configBuilder.fps(opts.getFps());
            }

            GenerateVideosConfig config = configBuilder.build();

            // Resolve input image for image-to-video
            Image inputImage = null;
            if (opts.getInputImage() != null && !opts.getInputImage().isBlank()) {
                inputImage = resolveGeminiImage(opts.getInputImage());
            }

            // Submit the video generation operation (async)
            GenerateVideosOperation operation =
                    client.models.generateVideos(opts.getModel(), text, inputImage, config);

            String operationName = operation.name().orElse(null);

            log.info(
                    "Gemini Veo video job submitted: operation={}, model={}",
                    operationName,
                    opts.getModel());

            VideoResponseMetadata metadata = new VideoResponseMetadata();
            metadata.setJobId(operationName);
            metadata.setStatus("PROCESSING");

            return new VideoResponse(List.of(), metadata);

        } catch (Exception e) {
            log.error("Failed to submit Gemini Veo video generation job", e);
            throw new RuntimeException("Failed to submit video generation: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoResponse checkStatus(String jobId) {
        try {
            // Build a minimal operation reference for polling
            GenerateVideosOperation opRef = GenerateVideosOperation.builder().name(jobId).build();

            GenerateVideosOperation operation = client.operations.getVideosOperation(opRef, null);

            boolean isDone = operation.done().orElse(false);

            VideoResponseMetadata metadata = new VideoResponseMetadata();
            metadata.setJobId(jobId);

            if (isDone) {
                // Check for error
                if (operation.error().isPresent()) {
                    metadata.setStatus("FAILED");
                    metadata.setErrorMessage(operation.error().get().toString());
                    log.error("Gemini Veo video failed: operation={}", jobId);
                    return new VideoResponse(List.of(), metadata);
                }

                // Extract generated videos from the response
                GenerateVideosResponse response = operation.response().orElse(null);
                List<VideoGeneration> generations = new ArrayList<>();

                if (response != null) {
                    List<GeneratedVideo> generatedVideos =
                            response.generatedVideos().orElse(List.of());

                    for (GeneratedVideo gv : generatedVideos) {
                        gv.video()
                                .ifPresent(
                                        video -> {
                                            byte[] bytes = video.videoBytes().orElse(null);
                                            String url = video.uri().orElse(null);

                                            // Use direct byte storage to avoid base64 encoding
                                            // overhead
                                            // Prefer bytes over URL to avoid potential
                                            // re-downloading
                                            org.conductoross.conductor.ai.video.Video videoObj;
                                            if (bytes != null) {
                                                videoObj =
                                                        org.conductoross.conductor.ai.video.Video
                                                                .fromBytes(bytes, "video/mp4");
                                            } else {
                                                // Fallback to URL if bytes not available
                                                videoObj =
                                                        new org.conductoross.conductor.ai.video
                                                                .Video(
                                                                url, null, null, "video/mp4");
                                            }

                                            generations.add(new VideoGeneration(videoObj));
                                        });
                    }
                }

                metadata.setStatus("COMPLETED");
                log.info(
                        "Gemini Veo video completed: operation={}, videos={}",
                        jobId,
                        generations.size());
                return new VideoResponse(generations, metadata);

            } else {
                metadata.setStatus("PROCESSING");
                log.debug("Gemini Veo video in progress: operation={}", jobId);
                return new VideoResponse(List.of(), metadata);
            }

        } catch (Exception e) {
            log.error("Failed to check Gemini Veo video status for operation {}", jobId, e);
            throw new RuntimeException("Failed to check video status: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves an input image specification to a Google GenAI Image object.
     *
     * <p>Supports data URIs, HTTP/HTTPS URLs, and raw base64 strings.
     */
    private Image resolveGeminiImage(String inputImage) {
        if (inputImage.startsWith("data:")) {
            // data:image/png;base64,xxx
            String base64Part = inputImage.substring(inputImage.indexOf(",") + 1);
            byte[] bytes = Base64.getDecoder().decode(base64Part);
            String mimeType = inputImage.substring(5, inputImage.indexOf(";"));
            return Image.builder().imageBytes(bytes).mimeType(mimeType).build();
        } else if (inputImage.startsWith("http://") || inputImage.startsWith("https://")) {
            byte[] bytes = downloadFromUrl(inputImage);
            return Image.builder().imageBytes(bytes).mimeType("image/png").build();
        } else {
            // Assume raw base64 encoded image
            byte[] bytes = Base64.getDecoder().decode(inputImage);
            return Image.builder().imageBytes(bytes).mimeType("image/png").build();
        }
    }

    private byte[] downloadFromUrl(String url) {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get().build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("Empty response downloading image from " + url);
            }
            return response.body().bytes();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image from " + url, e);
        }
    }
}
