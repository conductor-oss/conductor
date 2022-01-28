/*
 * Copyright 2022 Netflix, Inc.
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

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.postgres.storage.PostgresPayloadStorage;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostgresPayloadProperties.class)
@ConditionalOnProperty(name = "conductor.external-payload-storage.type", havingValue = "postgres")
public class PostgresPayloadConfiguration {

    PostgresPayloadProperties properties;

    public PostgresPayloadConfiguration(PostgresPayloadProperties properties) {
        this.properties = properties;
    }

    @Bean(initMethod = "migrate")
    @PostConstruct
    public Flyway flywayForExternalDb() {
        return Flyway.configure()
                .locations("classpath:db/migration_external_postgres")
                .schemas("external")
                .baselineOnMigrate(true)
                .placeholderReplacement(true)
                .placeholders(
                        Map.of(
                                "tableName",
                                properties.getTableName(),
                                "maxDataRows",
                                String.valueOf(properties.getMaxDataRows()),
                                "maxDataDays",
                                "'" + properties.getMaxDataDays() + "'",
                                "maxDataMonths",
                                "'" + properties.getMaxDataMonths() + "'",
                                "maxDataYears",
                                "'" + properties.getMaxDataYears() + "'"))
                .dataSource(
                        DataSourceBuilder.create()
                                .driverClassName("org.postgresql.Driver")
                                .url(properties.getUrl())
                                .username(properties.getUsername())
                                .password(properties.getPassword())
                                .build())
                .load();
    }

    @Bean
    public ExternalPayloadStorage postgresExternalPayloadStorage(
            PostgresPayloadProperties properties) {
        DataSource dataSource =
                DataSourceBuilder.create()
                        .driverClassName("org.postgresql.Driver")
                        .url(properties.getUrl())
                        .username(properties.getUsername())
                        .password(properties.getPassword())
                        .build();
        return new PostgresPayloadStorage(properties, dataSource);
    }
}
