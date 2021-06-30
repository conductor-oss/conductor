/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.postgres.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.postgres.dao.PostgresExecutionDAO;
import com.netflix.conductor.postgres.dao.PostgresMetadataDAO;
import com.netflix.conductor.postgres.dao.PostgresQueueDAO;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostgresProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "postgres")
// Import the DataSourceAutoConfiguration when postgres database is selected.
// By default the datasource configuration is excluded in the main module.
@Import(DataSourceAutoConfiguration.class)
public class PostgresConfiguration {

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        // override the default location.
        return configuration -> configuration.locations("classpath:db/migration_postgres");
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    public PostgresMetadataDAO postgresMetadataDAO(ObjectMapper objectMapper, DataSource dataSource,
        PostgresProperties properties) {
        return new PostgresMetadataDAO(objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    public PostgresExecutionDAO postgresExecutionDAO(ObjectMapper objectMapper, DataSource dataSource) {
        return new PostgresExecutionDAO(objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flyway", "flywayInitializer"})
    public PostgresQueueDAO postgresQueueDAO(ObjectMapper objectMapper, DataSource dataSource) {
        return new PostgresQueueDAO(objectMapper, dataSource);
    }
}
