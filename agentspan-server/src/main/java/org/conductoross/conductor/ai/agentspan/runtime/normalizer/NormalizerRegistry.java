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
package org.conductoross.conductor.ai.agentspan.runtime.normalizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.springframework.stereotype.Component;

/**
 * Registry of {@link AgentConfigNormalizer} implementations, keyed by framework ID.
 *
 * <p>Spring auto-discovers all normalizer beans and registers them here. Use {@link
 * #normalize(String, Map)} to convert a framework-specific raw config into the canonical {@link
 * AgentConfig}.
 */
@Component
public class NormalizerRegistry {

    private final Map<String, AgentConfigNormalizer> normalizers = new HashMap<>();

    public NormalizerRegistry(List<AgentConfigNormalizer> allNormalizers) {
        for (AgentConfigNormalizer n : allNormalizers) {
            normalizers.put(n.frameworkId(), n);
        }
    }

    /**
     * Normalize a framework-specific raw config into the canonical AgentConfig.
     *
     * @param framework the framework identifier (e.g. "openai", "google_adk")
     * @param rawConfig the raw agent config as deserialized JSON
     * @return the normalized AgentConfig
     * @throws IllegalArgumentException if the framework is not supported
     */
    public AgentConfig normalize(String framework, Map<String, Object> rawConfig) {
        AgentConfigNormalizer normalizer = normalizers.get(framework);
        if (normalizer == null) {
            throw new IllegalArgumentException(
                    "Unsupported agent framework: '"
                            + framework
                            + "'. Supported frameworks: "
                            + normalizers.keySet());
        }
        return normalizer.normalize(rawConfig);
    }

    /** Check whether a given framework is supported. */
    public boolean supports(String framework) {
        return normalizers.containsKey(framework);
    }
}
