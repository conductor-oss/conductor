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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Parses "provider/model" strings into provider and model components. Mirrors
 * python/src/conductor/agents/_internal/model_parser.py.
 */
public class ModelParser {

    @Data
    @AllArgsConstructor
    public static class ParsedModel {
        private String provider;
        private String model;
    }

    /**
     * Parse a model string like "openai/gpt-4o" into provider and model.
     *
     * @param modelString The model string in "provider/model" format.
     * @return A ParsedModel with provider and model components.
     * @throws IllegalArgumentException if the format is invalid.
     */
    public static ParsedModel parse(String modelString) {
        if (modelString == null || modelString.isBlank()) {
            throw new IllegalArgumentException("Model string cannot be null or empty");
        }

        int slashIdx = modelString.indexOf('/');
        if (slashIdx <= 0 || slashIdx >= modelString.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid model format: '" + modelString + "'. Expected 'provider/model'.");
        }

        String provider = modelString.substring(0, slashIdx);
        String model = modelString.substring(slashIdx + 1);
        return new ParsedModel(provider, model);
    }
}
