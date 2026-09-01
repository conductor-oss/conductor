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
package org.conductoross.conductor.postgres.config;

import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

/**
 * Creates the schema registry's tables on PostgreSQL.
 *
 * <p>The registry migrates from a location of its own, tracked in a history table of its own, so
 * its version numbering cannot contend with the main Conductor migrations: a merge that takes the
 * next free number in {@code db/migration_postgres} cannot break an upgrade here.
 *
 * <p>A bean of its own with an {@code initMethod}, rather than a migration run from inside the
 * DAO's factory method, so the migration is visible to anyone reading the bean definitions instead
 * of hiding behind a constructor call.
 *
 * <p>Deliberately not typed as {@link Flyway}. Boot's {@code FlywayConfiguration} is
 * {@code @ConditionalOnMissingBean(Flyway.class)}, so on MySQL a second Flyway bean would take the
 * main migrations with it; on PostgreSQL and SQLite, which already declare {@code
 * flywayForPrimaryDb}, it would instead make an {@code @Autowired Flyway} ambiguous.
 */
public class PostgresSchemaRegistryMigration {

    private static final String LOCATION = "classpath:db/migration_postgres_schema_registry";
    private static final String HISTORY_TABLE = "flyway_schema_history_schema_registry";

    private final DataSource dataSource;

    private final String schema;

    public PostgresSchemaRegistryMigration(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.schema = schema;
    }

    public void migrate() {
        Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .locations(LOCATION)
                .schemas(schema)
                .dataSource(dataSource)
                .table(HISTORY_TABLE)
                .outOfOrder(true)
                .baselineOnMigrate(true)
                // baseline 0 rather than Flyway's default 1, which would skip V1 when another
                // feature's flyway (same schema, own history table) migrated first
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
