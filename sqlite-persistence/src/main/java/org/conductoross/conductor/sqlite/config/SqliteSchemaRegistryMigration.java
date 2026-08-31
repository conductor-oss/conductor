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
package org.conductoross.conductor.sqlite.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

/**
 * Creates the schema registry's tables on SQLite.
 *
 * <p>The registry migrates from a location of its own, tracked in a history table of its own, so
 * its version numbering cannot contend with the main Conductor migrations — a merge that takes the
 * next free number in {@code db/migration_sqlite} cannot break an upgrade here.
 *
 * <p>Deliberately not a {@link Flyway} Spring bean: a second bean of that type makes an injected
 * {@code Flyway} ambiguous, and this module's tests inject one.
 *
 * <p>Called from the bean method that builds the DAO, so the tables exist before anything can reach
 * them. A DAO bean later made conditional or lazy would take this with it.
 */
public final class SqliteSchemaRegistryMigration {

    private static final String LOCATION = "classpath:db/migration_sqlite_schema_registry";
    private static final String HISTORY_TABLE = "flyway_schema_history_schema_registry";

    private SqliteSchemaRegistryMigration() {}

    public static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .table(HISTORY_TABLE)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .mixed(true)
                .outOfOrder(true)
                .baselineOnMigrate(true)
                // baseline 0 rather than Flyway's default 1, which would skip V1 when another
                // feature's flyway (same db, own history table) migrated first
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
