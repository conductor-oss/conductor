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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.ai.providers.openai.api.OpenAIImageGenApi;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

/**
 * Spring AI {@link ImageModel} implementation backed by OkHttp calls to OpenAI's image generation
 * API.
 */
public class OpenAIHttpImageModel implements ImageModel {

    private final OpenAIImageGenApi imageGenApi;

    public OpenAIHttpImageModel(OpenAIImageGenApi imageGenApi) {
        this.imageGenApi = imageGenApi;
    }

    @Override
    public ImageResponse call(ImagePrompt prompt) {
        try {
            ImageOptions options = prompt.getOptions();
            String promptText =
                    prompt.getInstructions().stream()
                            .map(msg -> msg.getText())
                            .reduce((a, b) -> a + " " + b)
                            .orElse("");

            String size = null;
            if (options != null && options.getWidth() != null && options.getHeight() != null) {
                size = options.getWidth() + "x" + options.getHeight();
            }

            var request =
                    OpenAIImageGenApi.ImageRequest.builder()
                            .model(options != null ? options.getModel() : null)
                            .prompt(promptText)
                            .n(options != null && options.getN() != null ? options.getN() : 1)
                            .size(size)
                            .style(options != null ? options.getStyle() : null)
                            .responseFormat(
                                    options != null && options.getResponseFormat() != null
                                            ? options.getResponseFormat()
                                            : "b64_json")
                            .build();

            var result = imageGenApi.createImage(request);

            List<ImageGeneration> generations = new ArrayList<>();
            if (result.data() != null) {
                for (var imageData : result.data()) {
                    Image image = new Image(imageData.url(), imageData.b64Json());
                    generations.add(new ImageGeneration(image));
                }
            }

            return new ImageResponse(generations);
        } catch (IOException e) {
            throw new RuntimeException("Image generation API call failed: " + e.getMessage(), e);
        }
    }
}
