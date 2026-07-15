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
package org.conductoross.conductor.ai.agentspan;

import org.conductoross.conductor.ai.agent.ConductorAgentRuntime;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.ai.agentspan.runtime.spi.SkillMetadataDAO;
import org.conductoross.conductor.ai.agentspan.runtime.spi.SkillPackageStore;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.dao.SkillPackageDAO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Conditional(AIIntegrationEnabledCondition.class)
public class ConductorAgentSpanConfiguration {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    /**
     * Bridges AgentSpan's skill-metadata SPI to Conductor's per-backend {@code SkillMetadataDAO}.
     */
    @Bean
    public SkillMetadataDAO agentSpanSkillMetadataDAO(
            org.conductoross.conductor.dao.SkillMetadataDAO skillMetadataDAO) {
        return new SkillMetadataDaoAdapter(skillMetadataDAO, objectMapper);
    }

    /** Bridges AgentSpan's skill-package SPI to Conductor's per-backend {@code SkillPackageDAO}. */
    @Bean
    public SkillPackageStore agentSpanSkillPackageStore(SkillPackageDAO skillPackageDAO) {
        return new SkillPackageStoreAdapter(skillPackageDAO);
    }

    /**
     * Exposes the embedded AgentSpan runtime to the {@code ai} module's {@code AGENT} (conductor)
     * task as a {@link ConductorAgentRuntime}. Only registered when the embedded runtime is enabled
     * ({@code agentspan.embedded=true}) — the same guard {@code AgentController}/{@code
     * AgentService} use — so the {@code AGENT} task receives a non-empty {@code
     * Optional<ConductorAgentRuntime>} exactly when the runtime is available.
     */
    @Bean
    @ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
    public ConductorAgentRuntime conductorAgentRuntime(AgentService agentService) {
        return new ConductorAgentRuntimeAdapter(agentService);
    }
}
