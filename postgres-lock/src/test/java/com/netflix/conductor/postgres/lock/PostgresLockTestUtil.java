/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.postgres.lock;

import java.nio.file.Paths;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import com.netflix.conductor.postgreslock.config.PostgresLockProperties;

public class PostgresLockTestUtil {

    private final DataSource dataSource;
    private final PostgresLockProperties properties = new PostgresLockProperties();

    public PostgresLockTestUtil(PostgreSQLContainer<?> postgreSQLContainer) {
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
                        .schemas("lock")
                        .locations(Paths.get("db/migration_lock_postgres").toString())
                        .dataSource(dataSource);

        Flyway flyway = fluentConfiguration.load();
        flyway.migrate();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public PostgresLockProperties getTestProperties() {
        return properties;
    }
}
