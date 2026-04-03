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
package org.conductoross.conductor.ai.providers.stabilityai;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * REST client for the Stability AI v2beta Image Generation API.
 *
 * <p>This client calls the v2beta endpoints directly using OkHttp with multipart/form-data
 * requests. It replaces the Spring AI {@code StabilityAiImageModel} which targets the retired v1
 * API.
 *
 * <p>Supported endpoints:
 *
 * <ul>
 *   <li>{@code POST /v2beta/stable-image/generate/sd3} - Stable Diffusion 3.x models
 *   <li>{@code POST /v2beta/stable-image/generate/core} - Stable Image Core (fast, affordable)
 *   <li>{@code POST /v2beta/stable-image/generate/ultra} - Stable Image Ultra (highest quality)
 * </ul>
 *
 * <p>All endpoints accept multipart/form-data and return either raw image bytes (when {@code
 * Accept: image/*}) or JSON with base64 data (when {@code Accept: application/json}).
 *
 * @see <a href="https://platform.stability.ai/docs/api-reference">Stability AI API Reference</a>
 */
@Slf4j
public class StabilityAiApi {

    public static final String DEFAULT_BASE_URL = "https://api.stability.ai";

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient httpClient;

    public StabilityAiApi(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public StabilityAiApi(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build();
    }

    /**
     * Generate an image using the specified endpoint and parameters.
     *
     * <p>The endpoint is selected based on the model name:
     *
     * <ul>
     *   <li>Models starting with "sd3" use the {@code /sd3} endpoint
     *   <li>"core" model uses the {@code /core} endpoint
     *   <li>"ultra" model uses the {@code /ultra} endpoint
     *   <li>All others default to the {@code /ultra} endpoint
     * </ul>
     *
     * @param params Image generation parameters
     * @return Raw image bytes (PNG format by default)
     */
    public ImageResult generateImage(ImageCreateParams params) throws IOException {
        String endpoint = resolveEndpoint(params.model());

        // Build the multipart request body
        MultipartBody.Builder bodyBuilder =
                new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("prompt", params.prompt());

        // The sd3 endpoint accepts a "model" field to select the specific SD3 variant.
        // The core and ultra endpoints do not need a model field.
        if (endpoint.endsWith("/sd3")) {
            bodyBuilder.addFormDataPart("model", params.model());
        }

        // Output format: png, jpeg, or webp
        String outputFormat = params.outputFormat() != null ? params.outputFormat() : "png";
        bodyBuilder.addFormDataPart("output_format", outputFormat);

        if (params.negativePrompt() != null && !params.negativePrompt().isBlank()) {
            bodyBuilder.addFormDataPart("negative_prompt", params.negativePrompt());
        }

        if (params.aspectRatio() != null && !params.aspectRatio().isBlank()) {
            bodyBuilder.addFormDataPart("aspect_ratio", params.aspectRatio());
        }

        if (params.seed() != null) {
            bodyBuilder.addFormDataPart("seed", String.valueOf(params.seed()));
        }

        if (params.stylePreset() != null && !params.stylePreset().isBlank()) {
            bodyBuilder.addFormDataPart("style_preset", params.stylePreset());
        }

        // Request raw image bytes with Accept: image/*
        Request request =
                new Request.Builder()
                        .url(baseUrl + endpoint)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "image/*")
                        .post(bodyBuilder.build())
                        .build();

        log.info(
                "Stability AI image generation request: endpoint={}, model={}, outputFormat={}",
                endpoint,
                params.model(),
                outputFormat);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = readResponseBody(response);
                throw new IOException(
                        "Stability AI API failed with status %d: %s"
                                .formatted(response.code(), errorBody));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Stability AI API returned empty response body");
            }

            // Determine the actual content type returned
            String contentType = response.header("Content-Type", "image/" + outputFormat);
            // Read the finish-reason header (e.g., SUCCESS, CONTENT_FILTERED)
            String finishReason = response.header("finish-reason", "SUCCESS");
            // Read the seed header
            String seedHeader = response.header("seed");

            byte[] imageBytes = body.bytes();
            log.info(
                    "Stability AI image generated: {} bytes, contentType={}, finishReason={}",
                    imageBytes.length,
                    contentType,
                    finishReason);

            return new ImageResult(imageBytes, contentType, finishReason, seedHeader);
        }
    }

    /**
     * Resolve the v2beta endpoint path based on the model name.
     *
     * <p>Model to endpoint mapping:
     *
     * <ul>
     *   <li>"sd3", "sd3-large", "sd3-large-turbo", "sd3-medium", "sd3.5-large",
     *       "sd3.5-large-turbo", "sd3.5-medium" -> /v2beta/stable-image/generate/sd3
     *   <li>"core", "stable-image-core" -> /v2beta/stable-image/generate/core
     *   <li>"ultra", "stable-image-ultra" -> /v2beta/stable-image/generate/ultra
     * </ul>
     */
    private String resolveEndpoint(String model) {
        if (model == null) {
            return "/v2beta/stable-image/generate/ultra";
        }
        String m = model.toLowerCase();
        if (m.startsWith("sd3")) {
            return "/v2beta/stable-image/generate/sd3";
        } else if (m.contains("core")) {
            return "/v2beta/stable-image/generate/core";
        } else if (m.contains("ultra")) {
            return "/v2beta/stable-image/generate/ultra";
        }
        // Default to ultra for unknown models
        return "/v2beta/stable-image/generate/ultra";
    }

    private String readResponseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body != null ? body.string() : "";
    }

    // -- DTOs --

    /**
     * Parameters for image generation.
     *
     * @param prompt Text description of the image to generate (required)
     * @param model Model name (e.g., "sd3.5-large", "core", "ultra")
     * @param outputFormat Output format: "png", "jpeg", or "webp" (default: "png")
     * @param aspectRatio Aspect ratio (e.g., "1:1", "16:9", "9:16", "3:2", "2:3")
     * @param negativePrompt What to exclude from the image
     * @param seed Random seed for reproducibility (0-4294967294)
     * @param stylePreset Style preset (e.g., "cinematic", "anime", "digital-art")
     */
    public record ImageCreateParams(
            String prompt,
            String model,
            String outputFormat,
            String aspectRatio,
            String negativePrompt,
            Long seed,
            String stylePreset) {}

    /**
     * Result of image generation.
     *
     * @param imageBytes Raw image binary data
     * @param contentType MIME type of the image (e.g., "image/png")
     * @param finishReason Reason generation finished (e.g., "SUCCESS", "CONTENT_FILTERED")
     * @param seed The seed used for generation
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageResult(
            byte[] imageBytes,
            @JsonProperty("content_type") String contentType,
            @JsonProperty("finish_reason") String finishReason,
            String seed) {}
}
