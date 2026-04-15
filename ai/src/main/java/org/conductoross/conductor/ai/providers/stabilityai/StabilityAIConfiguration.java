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

import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the Stability AI image generation provider.
 *
 * <p>Activated by setting {@code conductor.ai.stabilityai.apiKey} in application properties. Uses
 * Spring AI's built-in {@code StabilityAiImageModel} for text-to-image generation with Stable
 * Diffusion models.
 */
@Data
@Component
@ConfigurationProperties(prefix = "conductor.ai.stabilityai")
@NoArgsConstructor
@AllArgsConstructor
public class StabilityAIConfiguration implements ModelConfiguration<StabilityAI> {

    private String apiKey;

    @Override
    public StabilityAI get() {
        return new StabilityAI(this);
    }
}
