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
package org.conductoross.conductor.mysql.config;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.SkillMetadataDAO;
import org.conductoross.conductor.dao.SkillPackageDAO;
import org.conductoross.conductor.mysql.dao.MySQLSkillMetadataDAO;
import org.conductoross.conductor.mysql.dao.MySQLSkillPackageDAO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MySQL skill storage for the AgentSpan runtime, kept deliberately separate from {@link
 * com.netflix.conductor.mysql.config.MySQLConfiguration}.
 *
 * <p><b>Why its own class.</b> An embedding host that brings its own MySQL persistence layer
 * excludes {@code MySQLConfiguration} wholesale from its component scan to avoid duplicate DAO
 * beans (orkes-conductor does exactly this, by an exact-name filter). Skill storage then became
 * unreachable as collateral, leaving such a host with no MySQL implementation of {@link
 * SkillMetadataDAO} / {@link SkillPackageDAO} — and if it substitutes a Postgres one, its DDL fails
 * against a MySQL {@link DataSource}. Registering these two DAOs from a class of their own lets any
 * host pick them up while still excluding the monolithic backend config.
 *
 * <p>Both beans are {@link ConditionalOnMissingBean} so a host may still supply its own, and are
 * gated on {@code conductor.db.type=mysql} so they are mutually exclusive with the Postgres pair.
 * The tables come from {@code db/migration/V10__agentspan_skills.sql}, hence the {@link DependsOn}
 * on Flyway.
 *
 * <p>Retries use core's {@code onTransientErrorRetryTemplate} rather than {@code
 * mysqlRetryTemplate}: the latter is declared by {@code MySQLConfiguration} and so does not exist
 * in a host that excludes it, whereas the core template is always present (and a host may tune it
 * for MySQL deadlock semantics — orkes reconfigures it in place via a {@code BeanPostProcessor}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "mysql")
public class MySQLSkillDAOConfiguration {

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    @ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
    @ConditionalOnMissingBean(SkillMetadataDAO.class)
    public SkillMetadataDAO mySqlSkillMetadataDAO(
            @Qualifier("onTransientErrorRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        return new MySQLSkillMetadataDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    @ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
    @ConditionalOnMissingBean(SkillPackageDAO.class)
    public SkillPackageDAO mySqlSkillPackageDAO(
            @Qualifier("onTransientErrorRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        return new MySQLSkillPackageDAO(retryTemplate, objectMapper, dataSource);
    }
}
