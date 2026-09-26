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
package org.conductoross.conductor.ai.agentspan.runtime.decision;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

import static org.conductoross.conductor.ai.agentspan.runtime.decision.DecisionValidation.require;

/** Server-owned provider credentials and model-specific wire contracts. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "conductor.ai.decision")
public class DecisionConfiguration {
    private String apiKey;
    private String provider = "openrouter";
    private String endpoint;
    private String apiShape = "system-one";
    private Duration timeout = Duration.ofSeconds(20);
    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Provider {
        private String apiKey;
        private String endpoint;
        private String apiShape;
        private Map<String, Model> models = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Model {
        private String endpoint;
        private String apiShape;
    }

    public record Route(String provider, String endpoint, String apiKey, String apiShape) {}

    public Route resolve(String requestedProvider, String model) {
        String selected = StringUtils.defaultIfBlank(requestedProvider, provider);
        require(StringUtils.isNotBlank(selected), "Decision provider is required");
        Provider settings = providers.getOrDefault(selected, new Provider());
        Model modelSettings = settings.getModels().getOrDefault(model, new Model());
        // Model overrides provider; provider overrides the global API-shape default. Global
        // credentials and endpoint belong only to the default provider, never to another one.
        String shape =
                StringUtils.firstNonBlank(
                        modelSettings.getApiShape(), settings.getApiShape(), apiShape);
        String url =
                StringUtils.firstNonBlank(
                        modelSettings.getEndpoint(),
                        settings.getEndpoint(),
                        selected.equals(provider) ? endpoint : null);
        require(StringUtils.isNotBlank(shape), "Decision API shape is required");
        if (StringUtils.isBlank(url) && "system-one".equals(shape)) {
            url =
                    switch (selected) {
                        case "openrouter" -> "https://openrouter.ai/api/v1/systemone";
                        case "typesafe" -> "https://api.typesafe.ai/v1/systemone";
                        default -> null;
                    };
        }
        require(
                StringUtils.isNotBlank(url),
                "No endpoint configured for decision provider: " + selected);
        String key =
                StringUtils.firstNonBlank(
                        settings.getApiKey(), selected.equals(provider) ? apiKey : null);
        return new Route(selected, url, key, shape);
    }
}
