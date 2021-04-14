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
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PostgresDAOTestUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresDAOTestUtil.class);
    private final HikariDataSource dataSource;
    private final PostgresProperties properties = mock(PostgresProperties.class);
    private final ObjectMapper objectMapper;
    private final DataSource initializationDataSource;

    public PostgresDAOTestUtil(PostgreSQLContainer postgreSQLContainer, ObjectMapper objectMapper, String dbName) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(postgreSQLContainer.getJdbcUrl());
        dataSource.setDatabaseName(dbName);
        dataSource.setUser(postgreSQLContainer.getUsername());
        dataSource.setPassword(postgreSQLContainer.getPassword());
        this.initializationDataSource = dataSource;

        this.objectMapper = objectMapper;

        when(properties.getJdbcUrl()).thenReturn(postgreSQLContainer.getJdbcUrl());
        when(properties.getJdbcUsername()).thenReturn(postgreSQLContainer.getUsername());
        when(properties.getJdbcPassword()).thenReturn(postgreSQLContainer.getPassword());
        when(properties.isFlywayEnabled()).thenReturn(true);
        when(properties.getTaskDefCacheRefreshInterval()).thenReturn(Duration.ofSeconds(60));

        this.dataSource = getDataSource(properties);
    }

    private HikariDataSource getDataSource(PostgresProperties properties) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getJdbcUsername());
        dataSource.setPassword(properties.getJdbcPassword());
        dataSource.setAutoCommit(false);

        // Prevent DB from getting exhausted during rapid testing
        dataSource.setMaximumPoolSize(8);

        flywayMigrate(dataSource);

        return dataSource;
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

    public static void dropDb(DataSource ds, String dbName) {
        exec(ds, dbName, "DROP", "IF EXISTS");
    }

    private static void exec(DataSource ds, String dbName, String prefix, String suffix) {

        try (Connection connection = ds.getConnection()) {
            String stmt = String.format("%s DATABASE %s %s", prefix, suffix, dbName);
            try (PreparedStatement ps = connection.prepareStatement(stmt)) {
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    public void resetAllData() {
        LOGGER.info("Resetting data for test");
        dropDb(initializationDataSource, "conductor");
        flywayMigrate(dataSource);
    }
}
