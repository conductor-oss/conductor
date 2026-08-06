/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Issue #1497: the pre-fix {@code SqliteIndexDAO} wrote {@code start_time}/{@code update_time} as
 * local-time text (JVM default zone), while searches bound their range in UTC. The shipped V6
 * migration rewrites existing rows to the canonical UTC text so old and new rows compare correctly.
 *
 * <p>This test reads the real migration file off the classpath -- rather than pasting the SQL -- so
 * it exercises the artifact that actually ships. It requires {@code
 * db/migration_sqlite/V6__index_timestamps_to_utc.sql} to exist on the test classpath; if the
 * migration hasn't been added yet, {@link #readMigrationSql()} fails every test in this class with
 * an explicit "migration file not found" message.
 *
 * <p>Like {@link SqliteIndexDAOTest}, this is only a meaningful regression test when the JVM's
 * default zone is not UTC, which is why {@code sqlite-persistence/build.gradle} pins the {@code
 * test} task to {@code TZ=America/Asuncion}.
 */
public class SqliteIndexTimestampMigrationTest {

    private static final String MIGRATION_RESOURCE =
            "db/migration_sqlite/V6__index_timestamps_to_utc.sql";

    private static final DateTimeFormatter SQLITE_UTC_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_NAIVE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Path dbFile;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        dbFile = Files.createTempFile("sqlite-index-utc-migration-test", ".db");
        Files.deleteIfExists(dbFile);
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement statement = connection.createStatement()) {
            // Minimal shape of the tables from V1__initial_schema.sql -- only the columns the
            // V6 migration touches (plus the NOT NULL columns needed to satisfy the schema).
            statement.execute(
                    "CREATE TABLE workflow_index ("
                            + "workflow_id TEXT NOT NULL PRIMARY KEY,"
                            + "workflow_type TEXT NOT NULL,"
                            + "start_time DATETIME NOT NULL,"
                            + "update_time DATETIME NOT NULL,"
                            + "status TEXT NOT NULL,"
                            + "json_data TEXT NOT NULL)");
            statement.execute(
                    "CREATE TABLE task_index ("
                            + "task_id TEXT NOT NULL PRIMARY KEY,"
                            + "task_type TEXT NOT NULL,"
                            + "task_def_name TEXT NOT NULL,"
                            + "status TEXT NOT NULL,"
                            + "start_time DATETIME NOT NULL,"
                            + "update_time DATETIME NOT NULL,"
                            + "workflow_type TEXT NOT NULL,"
                            + "json_data TEXT NOT NULL)");
        }
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
        Files.deleteIfExists(dbFile);
    }

    /**
     * Reads the shipped migration off the classpath. Fails loudly (not an NPE downstream) if V6
     * hasn't been created yet.
     */
    private String readMigrationSql() throws IOException {
        try (InputStream in =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(MIGRATION_RESOURCE)) {
            if (in == null) {
                throw new AssertionError(
                        "Migration file not found on classpath: '"
                                + MIGRATION_RESOURCE
                                + "'. Expected to find it at "
                                + "sqlite-persistence/src/main/resources/"
                                + MIGRATION_RESOURCE
                                + " -- has the V6 migration been added yet? (issue #1497)");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void runMigration() throws Exception {
        String sql = readMigrationSql();
        // sqlite-jdbc's Statement.execute(String) only accepts a single statement, so split on
        // ';' the way Flyway would apply each statement of the migration in turn.
        try (Statement statement = connection.createStatement()) {
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    /** What the pre-fix SqliteIndexDAO wrote: Timestamp.toString() text in the JVM default zone. */
    private static String localNaive(LocalDateTime localDateTime) {
        return LOCAL_NAIVE.format(localDateTime);
    }

    /** The canonical UTC text the migration is expected to produce for a given local instant. */
    private static String expectedUtc(LocalDateTime localDateTime) {
        return SQLITE_UTC_TIMESTAMP.format(
                localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    public void rewritesWorkflowIndexLocalTimeToUtc() throws Exception {
        LocalDateTime local = LocalDateTime.of(2023, 2, 7, 8, 42, 45, 0);
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO workflow_index (workflow_id, workflow_type, start_time, update_time, status, json_data) VALUES "
                            + "('wf-1', 'wf-type', '"
                            + localNaive(local)
                            + "', '"
                            + localNaive(local)
                            + "', 'COMPLETED', '{}')");
        }

        runMigration();

        try (Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT start_time, update_time FROM workflow_index WHERE workflow_id = 'wf-1'")) {
            assertTrue("Expected exactly one row", rs.next());
            assertEquals(
                    "start_time should be rewritten to canonical UTC text",
                    expectedUtc(local),
                    rs.getString("start_time"));
            assertEquals(
                    "update_time should be rewritten to canonical UTC text",
                    expectedUtc(local),
                    rs.getString("update_time"));
        }
    }

    @Test
    public void rewritesTaskIndexLocalTimeToUtc() throws Exception {
        LocalDateTime local = LocalDateTime.of(2023, 2, 7, 9, 41, 45, 0);
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO task_index (task_id, task_type, task_def_name, status, start_time, update_time, workflow_type, json_data) VALUES "
                            + "('task-1', 'task-type', 'task-def', 'COMPLETED', '"
                            + localNaive(local)
                            + "', '"
                            + localNaive(local)
                            + "', 'wf-type', '{}')");
        }

        runMigration();

        try (Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT start_time, update_time FROM task_index WHERE task_id = 'task-1'")) {
            assertTrue("Expected exactly one row", rs.next());
            assertEquals(
                    "start_time should be rewritten to canonical UTC text",
                    expectedUtc(local),
                    rs.getString("start_time"));
            assertEquals(
                    "update_time should be rewritten to canonical UTC text",
                    expectedUtc(local),
                    rs.getString("update_time"));
        }
    }

    @Test
    public void malformedValueSurvivesUntouched() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO workflow_index (workflow_id, workflow_type, start_time, update_time, status, json_data) VALUES "
                            + "('wf-malformed', 'wf-type', 'not-a-timestamp', 'not-a-timestamp', 'COMPLETED', '{}')");
        }

        runMigration();

        try (Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT start_time, update_time FROM workflow_index WHERE workflow_id = 'wf-malformed'")) {
            assertTrue("Expected exactly one row", rs.next());
            assertEquals(
                    "COALESCE guard should leave an unparseable value untouched rather than"
                            + " nulling the NOT NULL column",
                    "not-a-timestamp",
                    rs.getString("start_time"));
            assertEquals(
                    "COALESCE guard should leave an unparseable value untouched rather than"
                            + " nulling the NOT NULL column",
                    "not-a-timestamp",
                    rs.getString("update_time"));
        }
    }

    @Test
    public void emptyTablesAreANoOp() throws Exception {
        runMigration();

        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS c FROM workflow_index")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c"));
        }
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS c FROM task_index")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c"));
        }
    }
}
