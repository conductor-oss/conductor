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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

import com.netflix.conductor.core.config.ConductorProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentspan.runtime.config.AgentSpanAutoConfiguration;
import dev.agentspan.runtime.spi.CredentialStoreProvider;
import dev.agentspan.runtime.spi.SecretOutputMasker;
import dev.agentspan.runtime.tasks.Join;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

/**
 * Embeds the {@code conductor-agentspan} library into OSS Conductor when {@code
 * conductor.integrations.ai.enabled=true}.
 *
 * <p>It component-scans the {@code dev.agentspan.runtime} namespace to register AgentSpan's agent
 * domain, compilers, services, REST controllers and system-task overrides. The library's own {@link
 * AgentSpanAutoConfiguration} stays dormant here (it only activates for the standalone server or
 * when {@code agentspan.embedded=true}), so we drive activation explicitly and leave {@code
 * agentspan.embedded} unset — which keeps AgentSpan's {@code HTTP}/{@code HUMAN}/{@code MCP}
 * overrides and the {@code AgentChatCompleteTaskMapper} active (the standalone "override" model).
 *
 * <p>Two exclusions from the scan:
 *
 * <ul>
 *   <li>{@link AgentSpanAutoConfiguration} — processed via the imports file, not our scan; excluded
 *       to avoid a redundant nested scan.
 *   <li>AgentSpan's {@link Join} ({@code @Component("JOIN")}) — excluded so it does not collide
 *       with Conductor core's scanned {@code @Component("JOIN")}; we instead re-declare it below as
 *       a primary {@code @Bean("JOIN")}, which cleanly overrides the core bean (the same mechanism
 *       AgentSpan's HTTP/HUMAN overrides use).
 * </ul>
 *
 * <p>This class also supplies the host SPI beans the library requires: a credential store, a
 * skill-metadata DAO adapter, a skill-package store adapter, a secret-output masker, the worker
 * token signing key, and a request-principal filter.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(AIIntegrationEnabledCondition.class)
@ComponentScan(
        basePackages = "dev.agentspan.runtime",
        excludeFilters = {
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {AgentSpanAutoConfiguration.class, Join.class})
        })
public class ConductorAgentSpanConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(ConductorAgentSpanConfiguration.class);

    private static final int MASTER_KEY_BYTES = 32;

    /**
     * Re-declares AgentSpan's JOIN task as a primary bean named {@code "JOIN"}, overriding
     * Conductor core's component-scanned {@code Join}. A manually-declared {@code @Bean} silently
     * overrides a scanned component of the same name, avoiding the {@code
     * ConflictingBeanDefinitionException} two scanned components would cause.
     */
    @Bean(TASK_TYPE_JOIN)
    @Primary
    public Join agentSpanJoin(ConductorProperties properties) {
        return new Join(properties);
    }

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
