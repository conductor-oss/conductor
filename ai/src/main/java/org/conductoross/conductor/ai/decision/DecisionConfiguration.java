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
package org.conductoross.conductor.ai.decision;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

import static org.conductoross.conductor.ai.decision.DecisionValidation.requireResponse;

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
        String selectedProvider = resolveProvider(requestedProvider);
        Provider providerSettings = providers.getOrDefault(selectedProvider, new Provider());
        Model modelSettings = providerSettings.getModels().getOrDefault(model, new Model());

        String shape = resolveApiShape(providerSettings, modelSettings);
        String url = resolveEndpoint(selectedProvider, providerSettings, modelSettings, shape);
        String key = resolveApiKey(selectedProvider, providerSettings);
        return new Route(selectedProvider, url, key, shape);
    }

    private String resolveProvider(String requestedProvider) {
        String selectedProvider = StringUtils.defaultIfBlank(requestedProvider, provider);
        requireResponse(StringUtils.isNotBlank(selectedProvider), "Decision provider is required");
        return selectedProvider;
    }

    private String resolveApiShape(Provider providerSettings, Model modelSettings) {
        // A model setting is most specific, followed by its provider and then the global default.
        String shape =
                StringUtils.firstNonBlank(
                        modelSettings.getApiShape(), providerSettings.getApiShape(), apiShape);
        requireResponse(StringUtils.isNotBlank(shape), "Decision API shape is required");
        return shape;
    }

    private String resolveEndpoint(
            String selectedProvider, Provider providerSettings, Model modelSettings, String shape) {
        String resolvedEndpoint =
                StringUtils.firstNonBlank(
                        modelSettings.getEndpoint(),
                        providerSettings.getEndpoint(),
                        isDefaultProvider(selectedProvider) ? endpoint : null);
        if (StringUtils.isBlank(resolvedEndpoint)) {
            resolvedEndpoint = defaultEndpoint(selectedProvider, shape);
        }
        requireResponse(
                StringUtils.isNotBlank(resolvedEndpoint),
                "No endpoint configured for decision provider: " + selectedProvider);
        return resolvedEndpoint;
    }

    private String defaultEndpoint(String selectedProvider, String shape) {
        if (!"system-one".equals(shape)) {
            return null;
        }
        return switch (selectedProvider) {
            case "openrouter" -> "https://openrouter.ai/api/v1/systemone";
            case "typesafe" -> "https://api.typesafe.ai/v1/systemone";
            default -> null;
        };
    }

    private String resolveApiKey(String selectedProvider, Provider providerSettings) {
        // Global credentials belong only to the default provider.
        return StringUtils.firstNonBlank(
                providerSettings.getApiKey(), isDefaultProvider(selectedProvider) ? apiKey : null);
    }

    private boolean isDefaultProvider(String selectedProvider) {
        return selectedProvider.equals(provider);
    }
}
