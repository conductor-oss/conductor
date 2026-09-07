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

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LangChainNormalizerTest {

    private final LangChainNormalizer normalizer = new LangChainNormalizer();

    @Test
    void frameworkIdIsLangchain() {
        assertThat(normalizer.frameworkId()).isEqualTo("langchain");
    }

    @Test
    void normalizeProducesPassthroughConfig() {
        Map<String, Object> raw =
                Map.of(
                        "name", "my_executor",
                        "_worker_name", "my_executor");

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getName()).isEqualTo("my_executor");
        assertThat(config.getModel()).isNull();
        assertThat(config.getMetadata()).containsEntry("_framework_passthrough", true);
        assertThat(config.getTools()).hasSize(1);
        assertThat(config.getTools().get(0).getName()).isEqualTo("my_executor");
        assertThat(config.getTools().get(0).getToolType()).isEqualTo("worker");
    }

    @Test
    void normalizeUsesDefaultNameWhenMissing() {
        AgentConfig config = normalizer.normalize(Map.of());

        assertThat(config.getName()).isEqualTo("langchain_agent");
        assertThat(config.getMetadata()).containsEntry("_framework_passthrough", true);
    }
}
