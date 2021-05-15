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
package com.netflix.conductor.postgres.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.postgres.config.PostgresProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PostgresDAOTestUtil {

    private final HikariDataSource dataSource;
    private final PostgresProperties properties = mock(PostgresProperties.class);
    private final ObjectMapper objectMapper;

    public PostgresDAOTestUtil(PostgreSQLContainer<?> postgreSQLContainer, ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;

        this.dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgreSQLContainer.getJdbcUrl());
        dataSource.setUsername(postgreSQLContainer.getUsername());
        dataSource.setPassword(postgreSQLContainer.getPassword());
        dataSource.setAutoCommit(false);
        // Prevent DB from getting exhausted during rapid testing
        dataSource.setMaximumPoolSize(8);

        when(properties.getTaskDefCacheRefreshInterval()).thenReturn(Duration.ofSeconds(60));

        flywayMigrate(dataSource);
    }

    private void flywayMigrate(DataSource dataSource) {
        FluentConfiguration fluentConfiguration = Flyway.configure()
                .table("schema_version")
                .locations(Paths.get("db", "migration_postgres").toString())
                .dataSource(dataSource)
                .placeholderReplacement(false);

        Flyway flyway = fluentConfiguration.load();
        flyway.migrate();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public PostgresProperties getTestProperties() {
        return properties;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }


}
