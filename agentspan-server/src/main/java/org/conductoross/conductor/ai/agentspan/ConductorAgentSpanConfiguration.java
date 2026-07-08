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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.dao.SkillPackageDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentspan.runtime.spi.CredentialStoreProvider;
import dev.agentspan.runtime.spi.SecretOutputMasker;

/**
 * Supplies the host SPI beans the embedded {@code conductor-agentspan} library requires, when
 * {@code conductor.integrations.ai.enabled=true}.
 *
 * <p>AgentSpan itself is activated in <b>embedded mode</b> by {@link
 * AgentSpanEmbeddedEnvironmentPostProcessor} (which sets {@code agentspan.embedded=true}), so the
 * library's own auto-configuration component-scans and registers the agent runtime, services, REST
 * controllers and the agent-aware {@code LLM_CHAT_COMPLETE} mapper. Crucially, in embedded mode the
 * library does <b>not</b> override Conductor's {@code HTTP}/{@code HUMAN}/{@code JOIN} system-task
 * beans; those native tasks are extended in place via the {@code __agentspan_ctx__} task input.
 * This class therefore only contributes the host-provided SPI implementations:
 *
 * <ul>
 *   <li>{@link CredentialStoreProvider} — environment-seeded, read-only ({@link
 *       EnvBackedCredentialStore}).
 *   <li>{@link SecretOutputMasker} — no-op (OSS parity).
 *   <li>{@code credentialMasterKey} — HMAC signing key for worker execution tokens.
 *   <li>Skill metadata/package SPI adapters onto Conductor's per-backend DAOs.
 *   <li>A request-principal filter populating AgentSpan's {@code RequestContextHolder}.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Conditional(AIIntegrationEnabledCondition.class)
public class ConductorAgentSpanConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(ConductorAgentSpanConfiguration.class);

    private static final int MASTER_KEY_BYTES = 32;

    /** OSS parity: no per-execution disclosure tracking, so nothing to redact. */
    @Bean
    public SecretOutputMasker agentSpanSecretOutputMasker() {
        return (executionId, userId, payload) -> payload;
    }

    /**
     * HMAC signing key for AgentSpan worker execution tokens. Sourced from {@code
     * AGENTSPAN_MASTER_KEY} (base64 or raw), else a random key generated at boot — tokens are
     * short-lived and per-run, so a fresh key on restart is acceptable.
     */
    @Bean("credentialMasterKey")
    public byte[] agentSpanCredentialMasterKey(
            @Value("${AGENTSPAN_MASTER_KEY:}") String configured) {
        if (configured != null && !configured.isBlank()) {
            try {
                return Base64.getDecoder().decode(configured.trim());
            } catch (IllegalArgumentException notBase64) {
                return configured.getBytes(StandardCharsets.UTF_8);
            }
        }
        byte[] key = new byte[MASTER_KEY_BYTES];
        new SecureRandom().nextBytes(key);
        log.info(
                "AGENTSPAN_MASTER_KEY not set — generated an ephemeral worker-token signing key for this run");
        return key;
    }

    /**
     * Read-only, environment-seeded credential store. Secrets must be supplied as environment
     * variables and injected at runtime; the API cannot write them (see {@link
     * EnvBackedCredentialStore}).
     */
    @Bean
    public CredentialStoreProvider agentSpanCredentialStoreProvider(
            @Value("${conductor.integrations.ai.secrets.env-names:}") String extraNamesCsv) {
        List<String> extraNames =
                Arrays.stream(extraNamesCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
        return new EnvBackedCredentialStore(extraNames, System::getenv);
    }

    /**
     * Bridges AgentSpan's skill-metadata SPI to Conductor's per-backend {@code SkillMetadataDAO}.
     */
    @Bean
    public dev.agentspan.runtime.spi.SkillMetadataDAO agentSpanSkillMetadataDAO(
            org.conductoross.conductor.dao.SkillMetadataDAO skillMetadataDAO,
            ObjectMapper objectMapper) {
        return new SkillMetadataDaoAdapter(skillMetadataDAO, objectMapper);
    }

    /** Bridges AgentSpan's skill-package SPI to Conductor's per-backend {@code SkillPackageDAO}. */
    @Bean
    public dev.agentspan.runtime.spi.SkillPackageStore agentSpanSkillPackageStore(
            SkillPackageDAO skillPackageDAO) {
        return new SkillPackageStoreAdapter(skillPackageDAO);
    }

    /**
     * Populates AgentSpan's request principal for {@code /api/*}. Runs late so a host security
     * adapter (if any) can set the real principal first.
     */
    @Bean
    public FilterRegistrationBean<AgentSpanPrincipalFilter> agentSpanPrincipalFilter(
            @Value("${conductor.integrations.ai.agentspan.default-user-id:agentspan-system}")
                    String defaultUserId) {
        FilterRegistrationBean<AgentSpanPrincipalFilter> registration =
                new FilterRegistrationBean<>(new AgentSpanPrincipalFilter(defaultUserId));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.setName("agentSpanPrincipalFilter");
        return registration;
    }
}
