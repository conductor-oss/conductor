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
package com.netflix.conductor.mysql.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.mysql.config.MySQLProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySQLDAOTestUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLDAOTestUtil.class);
    private final HikariDataSource dataSource;
    private final MySQLProperties properties;
    private final ObjectMapper objectMapper;

    public MySQLDAOTestUtil(ObjectMapper objectMapper, String dbName) {
        properties = mock(MySQLProperties.class);
        when(properties.getJdbcUrl()).thenReturn("jdbc:mysql://localhost:33307/" + dbName
            + "?useSSL=false&useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC");
        when(properties.getJdbcUserName()).thenReturn("root");
        when(properties.getJdbcPassword()).thenReturn("root");
        when(properties.getTaskDefRefreshTimeSecs()).thenReturn(60);
        createDatabase(dbName);
        this.objectMapper = objectMapper;
        this.dataSource = getDataSource(properties);
    }

    private void createDatabase(String dbName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://localhost:33307/conductor");
        dataSource.setUsername("root");
        dataSource.setPassword("root");
        dataSource.setAutoCommit(false);
        dataSource.setMaximumPoolSize(2);

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE IF NOT EXISTS " + dbName);
            }
        } catch (SQLException sqlException) {
            LOGGER.error("Unable to create default connection for docker mysql db", sqlException);
            throw new RuntimeException(sqlException);
        } finally {
            dataSource.close();
        }
    }

    private HikariDataSource getDataSource(MySQLProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getJdbcUserName());
        dataSource.setPassword(properties.getJdbcPassword());
        dataSource.setAutoCommit(false);

        // Prevent DB from getting exhausted during rapid testing
        dataSource.setMaximumPoolSize(8);

        flywayMigrate(dataSource);

        return dataSource;
    }

    private void flywayMigrate(DataSource dataSource) {
        FluentConfiguration fluentConfiguration = Flyway.configure()
            .dataSource(dataSource)
            .placeholderReplacement(false);

        Flyway flyway = new Flyway(fluentConfiguration);
        flyway.migrate();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public MySQLProperties getTestProperties() {
        return properties;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void resetAllData() {
        LOGGER.info("Resetting data for test");
        try (Connection connection = dataSource.getConnection()) {
            try (ResultSet rs = connection.prepareStatement("SHOW TABLES").executeQuery();
                PreparedStatement keysOn = connection.prepareStatement("SET FOREIGN_KEY_CHECKS=1")) {
                try (PreparedStatement keysOff = connection.prepareStatement("SET FOREIGN_KEY_CHECKS=0")) {
                    keysOff.execute();
                    while (rs.next()) {
                        String table = rs.getString(1);
                        try (PreparedStatement ps = connection.prepareStatement("TRUNCATE TABLE " + table)) {
                            ps.execute();
                        }
                    }
                } finally {
                    keysOn.execute();
                }
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
