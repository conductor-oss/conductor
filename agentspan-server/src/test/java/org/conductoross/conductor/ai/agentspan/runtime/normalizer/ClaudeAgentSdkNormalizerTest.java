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

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClaudeAgentSdkNormalizerTest {

    private final ClaudeAgentSdkNormalizer normalizer = new ClaudeAgentSdkNormalizer();

    @Test
    void frameworkIdIsClaudeAgentSdk() {
        assertThat(normalizer.frameworkId()).isEqualTo("claude_agent_sdk");
    }

    @Test
    void normalizeProducesPassthroughConfig() {
        Map<String, Object> raw =
                Map.of(
                        "name", "my_agent",
                        "_worker_name", "my_agent");

        AgentConfig config = normalizer.normalize(raw);

        assertThat(config.getName()).isEqualTo("my_agent");
        assertThat(config.getModel()).isNull();
        assertThat(config.getMetadata()).containsEntry("_framework_passthrough", true);
        assertThat(config.getTools()).hasSize(1);
        assertThat(config.getTools().get(0).getName()).isEqualTo("my_agent");
        assertThat(config.getTools().get(0).getToolType()).isEqualTo("worker");
    }

    @Test
    void normalizeUsesDefaultNameWhenMissing() {
        AgentConfig config = normalizer.normalize(Map.of());

        assertThat(config.getName()).isEqualTo("claude_agent_sdk_agent");
        assertThat(config.getMetadata()).containsEntry("_framework_passthrough", true);
    }
}
