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
package org.conductoross.conductor.postgres.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.postgres.config.PostgresConfiguration;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            PostgresConfiguration.class,
            FlywayAutoConfiguration.class
        })
@TestPropertySource(
        properties = {
            "conductor.db.type=postgres",
            "spring.flyway.clean-disabled=false",
            "conductor.app.workflow.name-validation.enabled=true",
            // PostgresConfiguration builds Flyway via Flyway.configure() rather than
            // Spring auto-config, so spring.flyway.ignore-migration-patterns has no
            // effect. Other tests in this module (PostgresQueueListenerTest,
            // PostgresGrpcEndToEndTest) set experimentalQueueNotify=true and apply
            // V10.1 from migration_postgres_notify to the shared testcontainer DB.
            // Match that location set here so Flyway validation passes when this
            // test runs after them.
            "conductor.postgres.experimentalQueueNotify=true"
        })
@SpringBootTest
public class PostgresWebhookCleanupJobTest {

    @Autowired private PostgresWebhookCleanupJob job;
    @Autowired private DataSource dataSource;
    @Autowired private Flyway flyway;

    @Before
    public void before() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    public void deletes_rows_older_than_retention_keeps_recent() throws SQLException {
        // 1-day retention; insert one row from 5 days ago, one from 5 minutes ago.
        Timestamp old = Timestamp.from(Instant.now().minus(Duration.ofDays(5)));
        Timestamp recent = Timestamp.from(Instant.now().minus(Duration.ofMinutes(5)));
        insertEvent("ev-old", "{\"x\":1}", old);
        insertEvent("ev-recent", "{\"x\":2}", recent);
        assertEquals(2, rowCount());

        job.setRetentionDuration(Duration.ofDays(1));
        job.setBatchSize(100);
        job.setMaxRuntime(Duration.ofSeconds(5));
        job.run();

        assertEquals(1, rowCount());
        assertEquals("ev-recent", firstRowEventId());
    }

    @Test
    public void empty_table_noop() {
        job.setRetentionDuration(Duration.ofDays(1));
        job.run();
        // No exception, no rows.
        try {
            assertEquals(0, rowCount());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void all_recent_rows_kept() throws SQLException {
        Timestamp recent = Timestamp.from(Instant.now().minus(Duration.ofMinutes(5)));
        insertEvent("ev-1", "{}", recent);
        insertEvent("ev-2", "{}", recent);
        assertEquals(2, rowCount());

        job.setRetentionDuration(Duration.ofDays(1));
        job.run();

        assertEquals(2, rowCount());
    }

    @Test
    public void batched_delete_respects_batch_size_across_multiple_passes() throws SQLException {
        Timestamp old = Timestamp.from(Instant.now().minus(Duration.ofDays(5)));
        for (int i = 0; i < 7; i++) {
            insertEvent("ev-old-" + i, "{}", old);
        }
        assertEquals(7, rowCount());

        // batch=2 forces 4 passes (2,2,2,1)
        job.setRetentionDuration(Duration.ofDays(1));
        job.setBatchSize(2);
        job.setMaxRuntime(Duration.ofSeconds(5));
        job.run();

        assertEquals(0, rowCount());
    }

    private void insertEvent(String id, String json, Timestamp createdOn) throws SQLException {
        // The test datasource is configured with hikari auto-commit=false (see
        // src/test/resources/application.properties) so an explicit commit is
        // required before the connection returns to the pool, otherwise the
        // insert is rolled back.
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO incoming_webhook_event (event_id, json_data, created_on)"
                                        + " VALUES (?, ?::text, ?)")) {
            ps.setString(1, id);
            ps.setString(2, json);
            ps.setTimestamp(3, createdOn);
            ps.executeUpdate();
            conn.commit();
        }
    }

    private int rowCount() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(1) FROM incoming_webhook_event");
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String firstRowEventId() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT event_id FROM incoming_webhook_event");
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
