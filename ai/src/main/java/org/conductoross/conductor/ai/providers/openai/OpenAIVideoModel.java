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
package org.conductoross.conductor.ai.providers.openai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.ai.providers.openai.api.OpenAIVideoApi;
import org.conductoross.conductor.ai.video.*;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI Sora video model implementation.
 *
 * <p>Implements {@link AsyncVideoModel} for the OpenAI Video API (Sora). Supports both
 * text-to-video and image-to-video generation.
 *
 * <p>The async flow:
 *
 * <ol>
 *   <li>{@link #call(VideoPrompt)} submits a generation job via POST /v1/videos
 *   <li>{@link #checkStatus(String)} polls GET /v1/videos/{id} for progress
 *   <li>On completion, downloads the MP4 binary via GET /v1/videos/{id}/content
 * </ol>
 */
@Slf4j
public class OpenAIVideoModel implements AsyncVideoModel {

    private final OpenAIVideoApi api;

    public OpenAIVideoModel(OpenAIVideoApi api) {
        this.api = api;
    }

    @Override
    public VideoResponse call(VideoPrompt prompt) {
        try {
            VideoOptions opts = prompt.getOptions();
            String text = prompt.getInstructions().getFirst().getText();

            // Resolve input image if provided (for image-to-video)
            byte[] imageBytes = null;
            String imageMimeType = null;
            if (opts.getInputImage() != null && !opts.getInputImage().isBlank()) {
                imageBytes = resolveImageBytes(opts.getInputImage());
                imageMimeType = detectMimeType(opts.getInputImage());
            }

            // Build size string: use explicit size, or construct from width x height
            String size = opts.getSize();
            if (size == null && opts.getWidth() != null && opts.getHeight() != null) {
                size = opts.getWidth() + "x" + opts.getHeight();
            }

            // Duration as string (OpenAI API expects string, not integer)
            String seconds = opts.getDuration() != null ? String.valueOf(opts.getDuration()) : null;

            OpenAIVideoApi.VideoCreateParams params =
                    new OpenAIVideoApi.VideoCreateParams(
                            text, opts.getModel(), size, seconds, imageBytes, imageMimeType);

            OpenAIVideoApi.VideoStatusResponse status = api.submitVideoJob(params);

            log.info(
                    "OpenAI Sora video job submitted: id={}, status={}",
                    status.id(),
                    status.status());

            VideoResponseMetadata metadata = new VideoResponseMetadata();
            metadata.setJobId(status.id());
            metadata.setStatus(mapStatus(status.status()));

            return new VideoResponse(List.of(), metadata);

        } catch (Exception e) {
            log.error("Failed to submit OpenAI video generation job", e);
            throw new RuntimeException("Failed to submit video generation: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoResponse checkStatus(String jobId) {
        try {
            OpenAIVideoApi.VideoStatusResponse status = api.getVideoStatus(jobId);

            VideoResponseMetadata metadata = new VideoResponseMetadata();
            metadata.setJobId(status.id());
            metadata.setStatus(mapStatus(status.status()));
            metadata.put("progress", status.progress());

            if ("completed".equals(status.status())) {
                // Download the video MP4 as bytes
                // Use direct byte storage to avoid base64 encoding overhead (~33% memory savings)
                byte[] videoBytes = api.downloadVideo(jobId);

                Video video = Video.fromBytes(videoBytes, "video/mp4");
                VideoGeneration generation = new VideoGeneration(video);

                List<VideoGeneration> generations = new ArrayList<>();
                generations.add(generation);

                // Optionally download thumbnail (OpenAI returns webp thumbnails)
                try {
                    byte[] thumbnailBytes = api.downloadThumbnail(jobId);
                    Video thumbnail = Video.fromBytes(thumbnailBytes, "image/webp");
                    generations.add(new VideoGeneration(thumbnail));
                } catch (Exception e) {
                    log.debug(
                            "Could not download thumbnail for video {}: {}", jobId, e.getMessage());
                }

                metadata.setStatus("COMPLETED");
                log.info("OpenAI Sora video completed: id={}", jobId);
                return new VideoResponse(generations, metadata);

            } else if ("failed".equals(status.status())) {
                metadata.setStatus("FAILED");
                metadata.setErrorMessage(
                        "OpenAI video generation failed: %s".formatted(status.toString()));
                log.error("OpenAI Sora video failed: id={}, response = {}", jobId, status);
                return new VideoResponse(List.of(), metadata);

            } else {
                // queued or in_progress
                metadata.setStatus("PROCESSING");
                log.debug(
                        "OpenAI Sora video in progress: id={}, progress={}%",
                        jobId, status.progress());
                return new VideoResponse(List.of(), metadata);
            }

        } catch (Exception e) {
            log.error("Failed to check OpenAI video status for job {}", jobId, e);
            throw new RuntimeException("Failed to check video status: " + e.getMessage(), e);
        }
    }

    /**
     * Maps OpenAI status strings to our canonical status values.
     *
     * <p>OpenAI uses: queued, in_progress, completed, failed
     */
    private String mapStatus(String openaiStatus) {
        return switch (openaiStatus) {
            case "completed" -> "COMPLETED";
            case "failed" -> "FAILED";
            default -> "PROCESSING";
        };
    }

    /**
     * Resolves an input image specification to raw bytes.
     *
     * <p>Supports:
     *
     * <ul>
     *   <li>data: URI (e.g., data:image/png;base64,xxx)
     *   <li>HTTP/HTTPS URL (downloads the image)
     *   <li>Raw base64 string
     * </ul>
     */
    private byte[] resolveImageBytes(String inputImage) {
        if (inputImage.startsWith("data:")) {
            String base64Part = inputImage.substring(inputImage.indexOf(",") + 1);
            return Base64.getDecoder().decode(base64Part);
        } else if (inputImage.startsWith("http://") || inputImage.startsWith("https://")) {
            return downloadFromUrl(inputImage);
        } else {
            // Assume raw base64
            return Base64.getDecoder().decode(inputImage);
        }
    }

    /** Detects the MIME type from an input image specification. */
    private String detectMimeType(String inputImage) {
        if (inputImage.startsWith("data:")) {
            // Extract MIME type from data URI: data:image/png;base64,...
            return inputImage.substring(5, inputImage.indexOf(";"));
        } else if (inputImage.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (inputImage.toLowerCase().endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
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
