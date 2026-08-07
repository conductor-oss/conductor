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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Issue #1497: the V6 migration rewrites the index {@code start_time}/{@code update_time} columns
 * in UTC, reading the instant from {@code json_data}.
 *
 * <p>Because no timezone is involved, every expectation here is a constant and these tests pass in
 * any host zone. The SQL is read off the classpath so the test covers the file that ships.
 */
public class SqliteIndexTimestampMigrationTest {

    private static final String MIGRATION_RESOURCE =
            "db/migration_sqlite/V6__index_timestamps_to_utc.sql";

    /** The authoritative instant, as SqliteIndexDAO writes it into json_data. */
    private static final String TRUTH_ISO = "2026-08-07T03:17:36.285Z";

    /** The canonical UTC text the migration must produce for {@link #TRUTH_ISO}. */
    private static final String TRUTH_CANONICAL = "2026-08-07 03:17:36.285";

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

    private void insertWorkflow(String id, String storedTime, String json) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO workflow_index (workflow_id, workflow_type, start_time, update_time, status, json_data) VALUES ('"
                            + id
                            + "', 'wf-type', '"
                            + storedTime
                            + "', '"
                            + storedTime
                            + "', 'COMPLETED', '"
                            + json
                            + "')");
        }
    }

    private String[] readTimes(String table, String idColumn, String id) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT start_time, update_time FROM "
                                        + table
                                        + " WHERE "
                                        + idColumn
                                        + " = '"
                                        + id
                                        + "'")) {
            assertTrue("Expected exactly one row", rs.next());
            return new String[] {rs.getString("start_time"), rs.getString("update_time")};
        }
    }

    private static String json(String startTime, String updateTime) {
        return "{\"startTime\":\"" + startTime + "\",\"updateTime\":\"" + updateTime + "\"}";
    }

    @Test
    public void rewritesWorkflowIndexLocalTimeToUtc() throws Exception {
        // '2026-08-06 23:17:36.285' is the same instant rendered at -04:00, i.e. what the pre-fix
        // write path stored on a JVM whose default zone is four hours behind UTC.
        insertWorkflow("wf-1", "2026-08-06 23:17:36.285", json(TRUTH_ISO, TRUTH_ISO));

        runMigration();

        String[] times = readTimes("workflow_index", "workflow_id", "wf-1");
        assertEquals("start_time should be rebuilt from json_data", TRUTH_CANONICAL, times[0]);
        assertEquals("update_time should be rebuilt from json_data", TRUTH_CANONICAL, times[1]);
    }

    @Test
    public void rewritesTaskIndexLocalTimeToUtc() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO task_index (task_id, task_type, task_def_name, status, start_time, update_time, workflow_type, json_data) VALUES "
                            + "('task-1', 'task-type', 'task-def', 'COMPLETED', '2026-08-06 23:17:36.285', '2026-08-06 23:17:36.285', 'wf-type', '"
                            + json(TRUTH_ISO, TRUTH_ISO)
                            + "')");
        }

        runMigration();

        String[] times = readTimes("task_index", "task_id", "task-1");
        assertEquals("start_time should be rebuilt from json_data", TRUTH_CANONICAL, times[0]);
        assertEquals("update_time should be rebuilt from json_data", TRUTH_CANONICAL, times[1]);
    }

    /**
     * The stored text is ignored, so a row lands on the right instant whatever offset it was
     * written with and whatever a previous migration left behind.
     */
    @Test
    public void repairsRowsWhateverTheStoredTextSays() throws Exception {
        insertWorkflow("wf-skew", "2026-08-06 23:17:36.285", json(TRUTH_ISO, TRUTH_ISO));
        insertWorkflow("wf-half-fixed", "2026-08-07 02:17:36.285", json(TRUTH_ISO, TRUTH_ISO));

        runMigration();

        assertEquals(TRUTH_CANONICAL, readTimes("workflow_index", "workflow_id", "wf-skew")[0]);
        assertEquals(
                "a row left wrong by an earlier migration attempt should be repaired, not shifted"
                        + " again",
                TRUTH_CANONICAL,
                readTimes("workflow_index", "workflow_id", "wf-half-fixed")[0]);
    }

    /** Running the migration twice must not move a row that is already correct. */
    @Test
    public void isIdempotent() throws Exception {
        insertWorkflow("wf-twice", "2026-08-06 23:17:36.285", json(TRUTH_ISO, TRUTH_ISO));

        runMigration();
        runMigration();

        assertEquals(TRUTH_CANONICAL, readTimes("workflow_index", "workflow_id", "wf-twice")[0]);
    }

    /** ISO_INSTANT omits the fraction when the instant lands on a whole second. */
    @Test
    public void handlesInstantsWithoutAFractionalPart() throws Exception {
        insertWorkflow(
                "wf-no-millis",
                "2026-08-06 23:17:36.0",
                json("2026-08-07T03:17:36Z", "2026-08-07T03:17:36Z"));

        runMigration();

        assertEquals(
                "a whole-second instant should still be padded to three fractional digits",
                "2026-08-07 03:17:36.000",
                readTimes("workflow_index", "workflow_id", "wf-no-millis")[0]);
    }

    /**
     * json_extract() raises "malformed JSON" and aborts the whole statement, so without the
     * json_valid() guard one bad row would fail the migration and stop the server booting.
     */
    @Test
    public void malformedJsonDoesNotAbortTheMigration() throws Exception {
        insertWorkflow("wf-bad-json", "2026-08-06 23:17:36.285", "not json at all");
        insertWorkflow("wf-good", "2026-08-06 23:17:36.285", json(TRUTH_ISO, TRUTH_ISO));

        runMigration();

        assertEquals(
                "the row with unreadable json_data should keep its existing value",
                "2026-08-06 23:17:36.285",
                readTimes("workflow_index", "workflow_id", "wf-bad-json")[0]);
        assertEquals(
                "a valid row alongside it should still be migrated",
                TRUTH_CANONICAL,
                readTimes("workflow_index", "workflow_id", "wf-good")[0]);
    }

    /** Valid JSON that simply lacks the field yields NULL, which COALESCE must absorb. */
    @Test
    public void missingJsonFieldLeavesTheColumnUntouched() throws Exception {
        insertWorkflow(
                "wf-no-field", "2026-08-06 23:17:36.285", "{\"workflowId\":\"wf-no-field\"}");

        runMigration();

        String[] times = readTimes("workflow_index", "workflow_id", "wf-no-field");
        assertEquals(
                "COALESCE guard should leave the value untouched rather than nulling a NOT NULL"
                        + " column",
                "2026-08-06 23:17:36.285",
                times[0]);
        assertEquals("2026-08-06 23:17:36.285", times[1]);
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
