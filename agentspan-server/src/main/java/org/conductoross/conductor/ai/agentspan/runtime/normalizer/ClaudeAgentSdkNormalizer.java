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

import java.util.*;

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.ToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Normalizes Claude Agent SDK rawConfig into a passthrough AgentConfig. */
@Component
public class ClaudeAgentSdkNormalizer implements AgentConfigNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAgentSdkNormalizer.class);
    private static final String DEFAULT_NAME = "claude_agent_sdk_agent";

    @Override
    public String frameworkId() {
        return "claude_agent_sdk";
    }

    @Override
    public AgentConfig normalize(Map<String, Object> raw) {
        String name = getString(raw, "name", DEFAULT_NAME);
        String workerName = getString(raw, "_worker_name", name);
        log.info("Normalizing Claude Agent SDK agent: {}", name);

        AgentConfig config = new AgentConfig();
        config.setName(name);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_framework_passthrough", true);
        config.setMetadata(metadata);

        ToolConfig worker =
                ToolConfig.builder()
                        .name(workerName)
                        .description("Claude Agent SDK passthrough worker")
                        .toolType("worker")
                        .build();
        config.setTools(List.of(worker));

        return config;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : defaultValue;
    }
}
