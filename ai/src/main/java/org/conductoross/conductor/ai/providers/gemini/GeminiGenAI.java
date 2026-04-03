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

import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import com.google.genai.Client;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;

public class GeminiGenAI implements ImageModel {

    private final Client client;

    public GeminiGenAI(Client client) {
        this.client = client;
    }

    @Override
    public ImageResponse call(ImagePrompt request) {
        var options = request.getOptions();
        GenerateImagesConfig config =
                GenerateImagesConfig.builder()
                        .numberOfImages(options.getN())
                        .outputMimeType("image/png")
                        .includeSafetyAttributes(true)
                        .build();

        GenerateImagesResponse response =
                client.models.generateImages(
                        options.getModel(), request.getInstructions().getFirst().getText(), config);

        List<GeneratedImage> generatedImages = response.generatedImages().orElse(new ArrayList<>());
        List<ImageGeneration> generations = new ArrayList<>();
        for (GeneratedImage generatedImage : generatedImages) {
            generatedImage
                    .image()
                    .ifPresent(
                            image -> {
                                byte[] data = image.imageBytes().orElse(new byte[0]);
                                org.springframework.ai.image.Image img =
                                        new org.springframework.ai.image.Image(
                                                null, Base64.getEncoder().encodeToString(data));
                                ImageGeneration imageGeneration = new ImageGeneration(img);
                                generations.add(imageGeneration);
                            });
        }
        return new ImageResponse(generations);
    }
}
