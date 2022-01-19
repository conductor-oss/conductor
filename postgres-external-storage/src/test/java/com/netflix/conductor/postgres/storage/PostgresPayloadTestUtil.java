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
package com.netflix.conductor.postgres.storage;

import java.nio.file.Paths;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import com.netflix.conductor.postgres.config.PostgresPayloadProperties;

public class PostgresPayloadTestUtil {

    private final DataSource dataSource;
    private final PostgresPayloadProperties properties = new PostgresPayloadProperties();

    public PostgresPayloadTestUtil(PostgreSQLContainer<?> postgreSQLContainer) {

        this.dataSource =
                DataSourceBuilder.create()
                        .url(postgreSQLContainer.getJdbcUrl())
                        .username(postgreSQLContainer.getUsername())
                        .password(postgreSQLContainer.getPassword())
                        .build();
        flywayMigrate(dataSource);
    }

    private void flywayMigrate(DataSource dataSource) {
        FluentConfiguration fluentConfiguration =
                Flyway.configure()
                        .schemas("external")
                        .locations(Paths.get("db/migration_external_postgres").toString())
                        .dataSource(dataSource)
                        .placeholderReplacement(true)
                        .placeholders(
                                Map.of(
                                        "tableName",
                                        "external.external_payload",
                                        "maxDataRows",
                                        "5",
                                        "maxDataDays",
                                        "'1'",
                                        "maxDataMonths",
                                        "'1'",
                                        "maxDataYears",
                                        "'1'"));

        Flyway flyway = fluentConfiguration.load();
        flyway.migrate();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public PostgresPayloadProperties getTestProperties() {
        return properties;
    }
}
