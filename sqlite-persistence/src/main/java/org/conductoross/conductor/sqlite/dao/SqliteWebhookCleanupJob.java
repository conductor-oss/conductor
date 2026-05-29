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

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;

/**
 * SQLite-flavored sibling of {@code PostgresWebhookCleanupJob}. SQLite supports {@code DELETE ...
 * LIMIT} only when compiled with SQLITE_ENABLE_UPDATE_DELETE_LIMIT (not the default in many
 * distributions), so we use a rowid subquery instead.
 */
@Slf4j
public class SqliteWebhookCleanupJob {

    private static final String DELETE_BATCH =
            "DELETE FROM incoming_webhook_event WHERE rowid IN ("
                    + "  SELECT rowid FROM incoming_webhook_event WHERE created_on < ? LIMIT ?"
                    + ")";

    private static final String LEASE_TASK = "webhook-event-cleanup";
    private static final String ACQUIRE_LEASE =
            "UPDATE webhook_cleanup_lease "
                    + "SET holder = ?, acquired_at = ?, expires_at = ? "
                    + "WHERE task = ? AND expires_at < ?";

    private final DataSource dataSource;
    private final String holderId = computeHolderId();

    private Duration retentionDuration = Duration.ofDays(7);
    private int batchSize = 1000;
    private Duration maxRuntime = Duration.ofSeconds(60);
    private Duration leaseTtl = Duration.ofMinutes(5);

    public SqliteWebhookCleanupJob(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setLeaseTtl(Duration leaseTtl) {
        this.leaseTtl = leaseTtl;
    }

    private static String computeHolderId() {
        return ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID();
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
        if (!tryAcquireLease()) {
            log.debug("webhook event cleanup: lease held elsewhere, skipping tick");
            return;
        }
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

    /**
     * Atomically claim the cleanup lease for this tick. Returns true iff the prior holder's lease
     * expired and this instance took it. SQLite deployments are typically single-instance, but the
     * lease is consulted unconditionally for symmetry with postgres/mysql.
     */
    private boolean tryAcquireLease() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(ACQUIRE_LEASE)) {
            conn.setAutoCommit(true);
            Instant now = Instant.now();
            ps.setString(1, holderId);
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now.plus(leaseTtl)));
            ps.setString(4, LEASE_TASK);
            ps.setTimestamp(5, Timestamp.from(now));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            log.warn("webhook cleanup lease acquisition failed", e);
            return false;
        }
    }
}
