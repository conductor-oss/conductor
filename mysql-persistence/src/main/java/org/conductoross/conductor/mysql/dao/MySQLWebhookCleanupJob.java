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
package org.conductoross.conductor.mysql.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;

/**
 * MySQL-flavored sibling of {@code PostgresWebhookCleanupJob}. MySQL doesn't support {@code DELETE
 * ... RETURNING} the way postgres does, so we use {@code DELETE ... LIMIT N} and rely on the JDBC
 * {@code executeUpdate()} return value to know when to stop.
 */
@Slf4j
public class MySQLWebhookCleanupJob {

    private static final String DELETE_BATCH =
            "DELETE FROM incoming_webhook_event WHERE created_on < ? LIMIT ?";

    private final DataSource dataSource;

    private Duration retentionDuration = Duration.ofDays(7);
    private int batchSize = 1000;
    private Duration maxRuntime = Duration.ofSeconds(60);

    public MySQLWebhookCleanupJob(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setRetentionDuration(Duration retentionDuration) {
        this.retentionDuration = retentionDuration;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setMaxRuntime(Duration maxRuntime) {
        this.maxRuntime = maxRuntime;
    }

    @Scheduled(cron = "${conductor.webhooks.cleanup.cron:0 0 * * * *}")
    public void run() {
        long deadline = System.currentTimeMillis() + maxRuntime.toMillis();
        int totalDeleted = 0;

        while (System.currentTimeMillis() < deadline) {
            Timestamp threshold = Timestamp.from(Instant.now().minus(retentionDuration));
            int deletedThisBatch;
            try {
                deletedThisBatch = deleteBatch(threshold, batchSize);
            } catch (SQLException e) {
                log.error("webhook event cleanup batch failed", e);
                return;
            }
            if (deletedThisBatch == 0) {
                break;
            }
            totalDeleted += deletedThisBatch;
        }

        if (totalDeleted > 0) {
            log.info(
                    "webhook event cleanup: deleted {} rows older than {}",
                    totalDeleted,
                    retentionDuration);
        }
    }

    private int deleteBatch(Timestamp threshold, int batch) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(DELETE_BATCH)) {
            conn.setAutoCommit(true);
            ps.setTimestamp(1, threshold);
            ps.setInt(2, batch);
            return ps.executeUpdate();
        }
    }
}
