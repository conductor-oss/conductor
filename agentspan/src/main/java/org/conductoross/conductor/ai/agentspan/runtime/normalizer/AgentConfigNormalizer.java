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

import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;

/**
 * Normalizes a framework-specific raw agent config into the canonical {@link AgentConfig}.
 *
 * <p>Implementations are auto-discovered by Spring as {@code @Component} beans and registered in
 * {@link NormalizerRegistry} by their {@link #frameworkId()}.
 */
public interface AgentConfigNormalizer {

    /** The framework identifier this normalizer handles (e.g. "openai", "google_adk"). */
    String frameworkId();

    /**
     * Convert a framework-native agent config (as deserialized JSON) into the canonical {@link
     * AgentConfig} understood by {@link AgentCompiler}.
     *
     * @param rawConfig the framework-specific agent definition as a Map
     * @return the normalized AgentConfig
     */
    AgentConfig normalize(Map<String, Object> rawConfig);
}
