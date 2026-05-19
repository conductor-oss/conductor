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

import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi;
import org.conductoross.conductor.ai.video.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Gemini Veo video model implementation using the GeminiApi OkHttp client.
 *
 * <p>Implements {@link AsyncVideoModel} for Google's Veo video generation models (veo-2.0, veo-3.0,
 * veo-3.1). Supports text-to-video and image-to-video generation via AI Studio or Vertex AI.
 *
 * <p>The async flow uses long-running operations:
 *
 * <ol>
 *   <li>{@link #call(VideoPrompt)} submits via {@code api.generateVideos()} returning an
 *       operation name
 *   <li>{@link #checkStatus(String)} polls via {@code api.getVideosOperation()}
 *   <li>When {@code operation.done()} is true, video bytes are extracted from the response
 * </ol>
 */
@Slf4j
public class GeminiVideoModel implements AsyncVideoModel {

    private final GeminiApi api;
    private final okhttp3.OkHttpClient httpClient;

    public GeminiVideoModel(GeminiApi api, okhttp3.OkHttpClient httpClient) {
        this.api = api;
        this.httpClient = httpClient;
    }

    @Override
    public VideoResponse call(VideoPrompt prompt) {
        try {
            VideoOptions opts = prompt.getOptions();
            String text = prompt.getInstructions().getFirst().getText();

            // Build GenerateVideosConfig from VideoOptions
            GeminiApi.GenerateVideosConfig config = new GeminiApi.GenerateVideosConfig(
                    opts.getN() != null ? opts.getN() : 1,
                    opts.getDuration(),
                    opts.getAspectRatio(),
                    opts.getSeed(),
                    opts.getNegativePrompt(),
                    opts.getPersonGeneration(),
                    opts.getResolution(),
                    opts.getGenerateAudio(),
                    opts.getFps());

            // Resolve input image for image-to-video
            byte[] inputBytes = null;
            String inputMime = null;
            if (opts.getInputImage() != null && !opts.getInputImage().isBlank()) {
                String inputImage = opts.getInputImage();
                if (inputImage.startsWith("data:")) {
                    // data:image/png;base64,xxx
                    inputMime = inputImage.substring(5, inputImage.indexOf(";"));
                    String base64Part = inputImage.substring(inputImage.indexOf(",") + 1);
                    inputBytes = Base64.getDecoder().decode(base64Part);
                } else if (inputImage.startsWith("http://") || inputImage.startsWith("https://")) {
                    inputBytes = downloadFromUrl(inputImage);
                    inputMime = "image/png";
                } else {
                    // Assume raw base64 encoded image
                    inputBytes = Base64.getDecoder().decode(inputImage);
                    inputMime = "image/png";
                }
            }

            // Submit the video generation operation (async)
            GeminiApi.GenerateVideosOperation operation =
                    api.generateVideos(opts.getModel(), text, inputBytes, inputMime, config);

            String operationName = operation.name();

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
            GeminiApi.GenerateVideosOperation operation = api.getVideosOperation(jobId);

            VideoResponseMetadata metadata = new VideoResponseMetadata();
            metadata.setJobId(jobId);

            if (Boolean.TRUE.equals(operation.done())) {
                // Check for error
                if (operation.error() != null) {
                    metadata.setStatus("FAILED");
                    metadata.setErrorMessage(operation.error().message());
                    log.error("Gemini Veo video failed: operation={}", jobId);
                    return new VideoResponse(List.of(), metadata);
                }

                // Extract generated videos from the response
                List<VideoGeneration> generations = new ArrayList<>();

                GeminiApi.GenerateVideosResult response = operation.response();
                if (response != null) {
                    List<GeminiApi.GeneratedVideo> generatedVideos =
                            response.videos() != null ? response.videos() : List.of();

                    for (GeminiApi.GeneratedVideo gv : generatedVideos) {
                        String mime = gv.mimeType() != null ? gv.mimeType() : "video/mp4";
                        Video videoObj;
                        if (gv.bytesBase64Encoded() != null) {
                            byte[] bytes = Base64.getDecoder().decode(gv.bytesBase64Encoded());
                            videoObj = Video.fromBytes(bytes, mime);
                        } else {
                            // Fallback to URL if bytes not available
                            videoObj = new Video(gv.uri(), null, null, mime);
                        }
                        generations.add(new VideoGeneration(videoObj));
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

    private byte[] downloadFromUrl(String url) {
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get().build();
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
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
