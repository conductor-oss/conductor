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

import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.ImageGenRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Stability AI provider for image generation using the v2beta REST API.
 *
 * <p>Supports the following models via different v2beta endpoints:
 *
 * <ul>
 *   <li><b>SD3 models</b> ({@code sd3.5-large}, {@code sd3.5-large-turbo}, {@code sd3.5-medium},
 *       {@code sd3-large}, {@code sd3-large-turbo}, {@code sd3-medium}) - via {@code
 *       /v2beta/stable-image/generate/sd3}
 *   <li><b>Core</b> ({@code core}) - Fast, affordable generation via {@code
 *       /v2beta/stable-image/generate/core}
 *   <li><b>Ultra</b> ({@code ultra}) - Highest quality via {@code
 *       /v2beta/stable-image/generate/ultra}
 * </ul>
 *
 * <p>This provider implements its own REST client ({@link StabilityAiApi}) that calls the v2beta
 * API directly, replacing Spring AI's built-in {@code StabilityAiImageModel} which targets the
 * retired v1 API.
 *
 * <p>Only image generation is supported. Chat, embeddings, audio, and video are not available.
 *
 * <p>Configure with {@code conductor.ai.stabilityai.apiKey} in application properties or set the
 * {@code STABILITY_API_KEY} environment variable.
 */
@Slf4j
public class StabilityAI implements AIModel {

    public static final String NAME = "stabilityai";

    private final StabilityAiApi api;

    /**
     * Custom ImageModel implementation that bridges Spring AI's ImageModel interface to the
     * Stability AI v2beta API.
     *
     * <p>This adapter receives Spring AI's ImagePrompt/ImageOptions and translates them into
     * StabilityAiApi.ImageCreateParams for the v2beta multipart request. The raw image bytes
     * returned by the API are base64-encoded and wrapped in Spring AI's ImageResponse.
     */
    private final ImageModel imageModel;

    public StabilityAI(StabilityAIConfiguration config) {
        this.api = new StabilityAiApi(config.getApiKey());

        // Create an ImageModel adapter that delegates to our v2beta API client.
        // The adapter translates Spring AI's ImagePrompt into StabilityAiApi calls,
        // and wraps the raw image bytes into Spring AI's ImageResponse format.
        this.imageModel =
                (ImagePrompt prompt) -> {
                    try {
                        // Extract the text prompt from the first message
                        String textPrompt =
                                prompt.getInstructions().stream()
                                        .findFirst()
                                        .map(msg -> msg.getText())
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "Image prompt must contain at least one message"));

                        // Extract options from the prompt
                        ImageOptions options = prompt.getOptions();
                        String model = options != null ? options.getModel() : "sd3.5-large";
                        String style = options != null ? options.getStyle() : null;

                        // Determine aspect ratio from width/height if provided
                        String aspectRatio = deriveAspectRatio(options);

                        // Build the API request
                        StabilityAiApi.ImageCreateParams params =
                                new StabilityAiApi.ImageCreateParams(
                                        textPrompt,
                                        model,
                                        "png",
                                        aspectRatio,
                                        null, // negativePrompt (not exposed via ImageOptions)
                                        null, // seed
                                        style);

                        // Call the v2beta API
                        StabilityAiApi.ImageResult result = api.generateImage(params);

                        // Wrap raw bytes as base64 in Spring AI's response format
                        String b64 = Base64.getEncoder().encodeToString(result.imageBytes());
                        Image image = new Image(null, b64);
                        ImageGeneration generation = new ImageGeneration(image);

                        return new ImageResponse(List.of(generation));
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Stability AI image generation failed: " + e.getMessage(), e);
                    }
                };
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public ImageModel getImageModel() {
        return this.imageModel;
    }

    @Override
    public ImageOptions getImageOptions(ImageGenRequest input) {
        // Build standard ImageOptions. The model field controls endpoint routing
        // in our StabilityAiApi (sd3 vs core vs ultra).
        return org.springframework.ai.image.ImageOptionsBuilder.builder()
                .model(input.getModel())
                .N(input.getN())
                .height(input.getHeight())
                .width(input.getWidth())
                .responseFormat("b64_json")
                .style(input.getStyle())
                .build();
    }

    @Override
    public ChatModel getChatModel() {
        throw new UnsupportedOperationException(
                "Chat completion is not supported by the Stability AI provider");
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        throw new UnsupportedOperationException(
                "Embeddings are not supported by the Stability AI provider");
    }

    /**
     * Derive an aspect ratio string from width/height in ImageOptions.
     *
     * <p>The v2beta API accepts aspect ratios like "1:1", "16:9", "9:16", "3:2", "2:3", "4:5",
     * "5:4", "21:9", "9:21". If width and height are both provided, we compute the closest matching
     * ratio. If not provided, defaults to "1:1".
     */
    private static String deriveAspectRatio(ImageOptions options) {
        if (options == null || options.getWidth() == null || options.getHeight() == null) {
            return "1:1";
        }
        int w = options.getWidth();
        int h = options.getHeight();
        if (w <= 0 || h <= 0) {
            return "1:1";
        }
        double ratio = (double) w / h;

        // Map to the closest supported aspect ratio
        // Supported: 1:1 (1.0), 16:9 (1.78), 9:16 (0.56), 3:2 (1.5), 2:3 (0.67),
        //            4:5 (0.8), 5:4 (1.25), 21:9 (2.33), 9:21 (0.43)
        double[][] ratios = {
            {1.0, 1, 1},
            {1.78, 16, 9},
            {0.56, 9, 16},
            {1.5, 3, 2},
            {0.67, 2, 3},
            {0.8, 4, 5},
            {1.25, 5, 4},
            {2.33, 21, 9},
            {0.43, 9, 21}
        };

        double bestDist = Double.MAX_VALUE;
        String bestRatio = "1:1";
        for (double[] r : ratios) {
            double dist = Math.abs(ratio - r[0]);
            if (dist < bestDist) {
                bestDist = dist;
                bestRatio = (int) r[1] + ":" + (int) r[2];
            }
        }
        return bestRatio;
    }
}
