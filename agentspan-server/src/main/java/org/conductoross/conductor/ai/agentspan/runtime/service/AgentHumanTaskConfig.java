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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HUMAN;

/**
 * Registers {@link AgentHumanTask} as the {@code HUMAN} system task when running in embedded mode
 * ({@code agentspan.embedded=true}).
 *
 * <p>The {@code @Bean("HUMAN")} definition <em>overrides</em> Conductor's default {@code Human}
 * component (enabled via {@code spring.main.allow-bean-definition-overriding=true}), so exactly one
 * bean named {@code HUMAN} exists. This avoids the component-scan bean-name collision and the
 * duplicate taskType key in {@code SystemTaskRegistry} that would otherwise result from having two
 * {@code WorkflowSystemTask}s both reporting the {@code HUMAN} type.
 *
 * <p>When {@code agentspan.embedded} is unset (the standalone OSS server), this configuration is
 * skipped and Conductor's default {@code Human} task remains in effect.
 */
@Configuration
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
public class AgentHumanTaskConfig {

    @Bean(TASK_TYPE_HUMAN)
    @Primary
    public AgentHumanTask agentHumanTask(AgentStreamRegistry streamRegistry) {
        return new AgentHumanTask(streamRegistry);
    }
}
