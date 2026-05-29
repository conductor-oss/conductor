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
package org.conductoross.conductor.sqlite.dao;

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
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.sqlite.config.SqliteConfiguration;

import static org.junit.Assert.assertEquals;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            SqliteConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public class SqliteWebhookCleanupJobTest {

    @Autowired private SqliteWebhookCleanupJob job;
    @Autowired private DataSource dataSource;
    @Autowired private Flyway flyway;

    @Before
    public void before() throws SQLException {
        flyway.clean();
        flyway.migrate();
        // flyway.clean() leaks rows across tests on the shared sqlite file in this
        // test runner — explicit DELETE guarantees a clean slate per method.
        // The cleanup-lease row also leaks: the V5 migration seeds it with
        // INSERT OR IGNORE so re-running migrate() after a partial-clean doesn't
        // reset expires_at. Without this reset, the first test acquires the
        // lease and sets expires_at = now + 5min, and subsequent tests'
        // tryAcquireLease() returns false so job.run() does nothing.
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps =
                    conn.prepareStatement("DELETE FROM incoming_webhook_event")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    conn.prepareStatement(
                            "UPDATE webhook_cleanup_lease SET holder = 'unclaimed',"
                                    + " expires_at = '1970-01-01T00:00:00'"
                                    + " WHERE task = 'webhook-event-cleanup'")) {
                ps.executeUpdate();
            }
        }
    }

    @Test
    public void deletes_rows_older_than_retention_keeps_recent() throws SQLException {
        insertEvent("ev-old", Timestamp.from(Instant.now().minus(Duration.ofDays(5))));
        insertEvent("ev-recent", Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))));
        assertEquals(2, rowCount());

        job.setRetentionDuration(Duration.ofDays(1));
        job.setBatchSize(100);
        job.setMaxRuntime(Duration.ofSeconds(5));
        job.run();

        assertEquals(1, rowCount());
        assertEquals("ev-recent", firstRowEventId());
    }

    @Test
    public void empty_table_noop() throws SQLException {
        job.setRetentionDuration(Duration.ofDays(1));
        job.run();
        assertEquals(0, rowCount());
    }

    @Test
    public void batched_delete_respects_batch_size_across_passes() throws SQLException {
        Timestamp old = Timestamp.from(Instant.now().minus(Duration.ofDays(5)));
        for (int i = 0; i < 7; i++) {
            insertEvent("ev-old-" + i, old);
        }
        assertEquals(7, rowCount());

        job.setRetentionDuration(Duration.ofDays(1));
        job.setBatchSize(2);
        job.setMaxRuntime(Duration.ofSeconds(5));
        job.run();

        assertEquals(0, rowCount());
    }

    private void insertEvent(String id, Timestamp createdOn) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO incoming_webhook_event (event_id, json_data, created_on)"
                                        + " VALUES (?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, "{}");
            ps.setTimestamp(3, createdOn);
            ps.executeUpdate();
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
