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
package com.netflix.conductor.core.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;

import tools.jackson.databind.ObjectMapper;

/**
 * A durable workflow message queue on a SQL database (SQLite, Postgres, MySQL), in the table {@code
 * workflow_message_queue} created by each persistence module's migrations.
 *
 * <p>Messages pushed to a running workflow, including the signals, updates and queries of native
 * workflows, survive a server restart. Messages are ordered by an auto-increment sequence. A pop
 * reads and deletes in one transaction, so a message is delivered once; the PULL_WORKFLOW_MESSAGES
 * task that receives it records it in its output, which is durable from then on.
 *
 * <p>On SQLite, push and pop are each one write statement ({@code INSERT ... SELECT ... WHERE} and
 * {@code DELETE ... RETURNING}). A transaction that reads and then writes would have to upgrade its
 * lock, and SQLite fails such an upgrade at once with SQLITE_BUSY instead of waiting out
 * busy_timeout, so concurrent signals and pulls would fail under load.
 */
public class JdbcWorkflowMessageQueueDAO implements WorkflowMessageQueueDAO {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final int maxQueueSize;
    private final boolean selectForUpdate;

    /**
     * @param selectForUpdate lock the popped rows ({@code SELECT ... FOR UPDATE}); true for
     *     Postgres and MySQL, false for SQLite, which serializes writers itself
     */
    public JdbcWorkflowMessageQueueDAO(
            DataSource dataSource,
            ObjectMapper objectMapper,
            WorkflowMessageQueueProperties properties,
            boolean selectForUpdate) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.maxQueueSize = properties.getMaxQueueSize();
        this.selectForUpdate = selectForUpdate;
    }

    @Override
    public void push(String workflowId, WorkflowMessage message) {
        if (!selectForUpdate) {
            pushSingleStatement(workflowId, message);
            return;
        }
        inTransaction(
                c -> {
                    long size = count(c, workflowId);
                    if (size >= maxQueueSize) {
                        throw new IllegalStateException(
                                "Workflow message queue for workflowId="
                                        + workflowId
                                        + " has reached the maximum size of "
                                        + maxQueueSize);
                    }
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "INSERT INTO workflow_message_queue (message_id, workflow_id, json_data) VALUES (?, ?, ?)")) {
                        ps.setString(1, message.getId());
                        ps.setString(2, workflowId);
                        ps.setString(3, objectMapper.writeValueAsString(message));
                        ps.executeUpdate();
                    }
                    return null;
                });
    }

    @Override
    public List<WorkflowMessage> pop(String workflowId, int maxCount) {
        if (!selectForUpdate) {
            return popSingleStatement(workflowId, maxCount);
        }
        return inTransaction(
                c -> {
                    List<Long> seqs = new ArrayList<>();
                    List<WorkflowMessage> messages = new ArrayList<>();
                    String select =
                            "SELECT seq, json_data FROM workflow_message_queue WHERE workflow_id = ? ORDER BY seq LIMIT ?"
                                    + (selectForUpdate ? " FOR UPDATE" : "");
                    try (PreparedStatement ps = c.prepareStatement(select)) {
                        ps.setString(1, workflowId);
                        ps.setInt(2, maxCount);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                seqs.add(rs.getLong(1));
                                messages.add(
                                        objectMapper.readValue(
                                                rs.getString(2), WorkflowMessage.class));
                            }
                        }
                    }
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "DELETE FROM workflow_message_queue WHERE seq = ?")) {
                        for (Long seq : seqs) {
                            ps.setLong(1, seq);
                            ps.addBatch();
                        }
                        if (!seqs.isEmpty()) {
                            ps.executeBatch();
                        }
                    }
                    return messages;
                });
    }

    private void pushSingleStatement(String workflowId, WorkflowMessage message) {
        int inserted =
                inTransaction(
                        c -> {
                            try (PreparedStatement ps =
                                    c.prepareStatement(
                                            "INSERT INTO workflow_message_queue (message_id, workflow_id, json_data)"
                                                    + " SELECT ?, ?, ? WHERE (SELECT COUNT(*) FROM workflow_message_queue"
                                                    + " WHERE workflow_id = ?) < ?")) {
                                ps.setString(1, message.getId());
                                ps.setString(2, workflowId);
                                ps.setString(3, objectMapper.writeValueAsString(message));
                                ps.setString(4, workflowId);
                                ps.setInt(5, maxQueueSize);
                                return ps.executeUpdate();
                            }
                        });
        if (inserted == 0) {
            throw new IllegalStateException(
                    "Workflow message queue for workflowId="
                            + workflowId
                            + " has reached the maximum size of "
                            + maxQueueSize);
        }
    }

    private List<WorkflowMessage> popSingleStatement(String workflowId, int maxCount) {
        return inTransaction(
                c -> {
                    java.util.TreeMap<Long, WorkflowMessage> bySeq = new java.util.TreeMap<>();
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "DELETE FROM workflow_message_queue WHERE seq IN (SELECT seq FROM"
                                            + " workflow_message_queue WHERE workflow_id = ? ORDER BY seq LIMIT ?)"
                                            + " RETURNING seq, json_data")) {
                        ps.setString(1, workflowId);
                        ps.setInt(2, maxCount);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                bySeq.put(
                                        rs.getLong(1),
                                        objectMapper.readValue(
                                                rs.getString(2), WorkflowMessage.class));
                            }
                        }
                    }
                    return new ArrayList<>(bySeq.values());
                });
    }

    @Override
    public boolean supportsClaims() {
        return true;
    }

    @Override
    public List<WorkflowMessage> claim(
            String workflowId, int maxCount, String claimant, long ordinal) {
        String lock = selectForUpdate ? " FOR UPDATE" : "";
        return inTransaction(
                c -> {
                    if (!advanceFence(c, workflowId, ordinal)) {
                        // a newer pull has claimed: this run is stale and takes nothing new
                        return select(
                                c,
                                "SELECT seq, json_data FROM workflow_message_queue WHERE workflow_id = ?"
                                        + " AND claimed_by = ? ORDER BY seq",
                                workflowId,
                                claimant);
                    }
                    if (selectForUpdate) {
                        // Serialize claims of one workflow: a second run of the same pull waits
                        // here, then sees the first run's claims instead of claiming other rows.
                        try (PreparedStatement ps =
                                c.prepareStatement(
                                        "SELECT seq FROM workflow_message_queue WHERE workflow_id = ? FOR UPDATE")) {
                            ps.setString(1, workflowId);
                            ps.executeQuery().close();
                        }
                    }
                    // earlier pulls' results are recorded: their messages are delivered
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "DELETE FROM workflow_message_queue WHERE workflow_id = ?"
                                            + " AND claimed_by IS NOT NULL AND claimed_by <> ?")) {
                        ps.setString(1, workflowId);
                        ps.setString(2, claimant);
                        ps.executeUpdate();
                    }
                    List<WorkflowMessage> mine =
                            select(
                                    c,
                                    "SELECT seq, json_data FROM workflow_message_queue WHERE workflow_id = ?"
                                            + " AND claimed_by = ? ORDER BY seq"
                                            + lock,
                                    workflowId,
                                    claimant);
                    if (!mine.isEmpty()) {
                        return mine;
                    }
                    List<Long> seqs = new ArrayList<>();
                    List<WorkflowMessage> claimed = new ArrayList<>();
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "SELECT seq, json_data FROM workflow_message_queue WHERE workflow_id = ?"
                                            + " AND claimed_by IS NULL ORDER BY seq LIMIT ?"
                                            + lock)) {
                        ps.setString(1, workflowId);
                        ps.setInt(2, maxCount);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                seqs.add(rs.getLong(1));
                                claimed.add(
                                        objectMapper.readValue(
                                                rs.getString(2), WorkflowMessage.class));
                            }
                        }
                    }
                    if (!seqs.isEmpty()) {
                        try (PreparedStatement ps =
                                c.prepareStatement(
                                        "UPDATE workflow_message_queue SET claimed_by = ? WHERE seq = ?")) {
                            for (Long seq : seqs) {
                                ps.setString(1, claimant);
                                ps.setLong(2, seq);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }
                    return claimed;
                });
    }

    /**
     * Raises the workflow's claim fence to {@code ordinal}, or reports that a higher ordinal has
     * already claimed. Holds the fence row for the rest of the transaction.
     */
    private boolean advanceFence(Connection c, String workflowId, long ordinal) throws Exception {
        boolean mysql = c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        String insert =
                mysql
                        ? "INSERT IGNORE INTO workflow_message_claim (workflow_id, ordinal) VALUES (?, ?)"
                        : "INSERT INTO workflow_message_claim (workflow_id, ordinal) VALUES (?, ?) ON CONFLICT (workflow_id) DO NOTHING";
        try (PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setString(1, workflowId);
            ps.setLong(2, ordinal);
            ps.executeUpdate();
        }
        long current;
        try (PreparedStatement ps =
                c.prepareStatement(
                        "SELECT ordinal FROM workflow_message_claim WHERE workflow_id = ?"
                                + (selectForUpdate ? " FOR UPDATE" : ""))) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                current = rs.next() ? rs.getLong(1) : ordinal;
            }
        }
        if (ordinal < current) {
            return false;
        }
        if (ordinal > current) {
            try (PreparedStatement ps =
                    c.prepareStatement(
                            "UPDATE workflow_message_claim SET ordinal = ? WHERE workflow_id = ?")) {
                ps.setLong(1, ordinal);
                ps.setString(2, workflowId);
                ps.executeUpdate();
            }
        }
        return true;
    }

    private List<WorkflowMessage> select(
            Connection c, String sql, String workflowId, String claimant) throws Exception {
        List<WorkflowMessage> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setString(2, claimant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(objectMapper.readValue(rs.getString(2), WorkflowMessage.class));
                }
            }
        }
        return out;
    }

    @Override
    public long size(String workflowId) {
        return inTransaction(c -> count(c, workflowId));
    }

    @Override
    public void delete(String workflowId) {
        inTransaction(
                c -> {
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "DELETE FROM workflow_message_queue WHERE workflow_id = ?")) {
                        ps.setString(1, workflowId);
                        ps.executeUpdate();
                    }
                    // the fence goes too, so a restarted execution's pulls start from iteration 1
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "DELETE FROM workflow_message_claim WHERE workflow_id = ?")) {
                        ps.setString(1, workflowId);
                        ps.executeUpdate();
                    }
                    return null;
                });
    }

    private static long count(Connection c, String workflowId) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement(
                        "SELECT COUNT(*) FROM workflow_message_queue WHERE workflow_id = ?")) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    @FunctionalInterface
    private interface TxFunction<R> {
        R apply(Connection connection) throws Exception;
    }

    private static final int MAX_ATTEMPTS = 5;

    /**
     * Runs {@code function} in one transaction. Lock conflicts between concurrent writers are
     * retried: SQLite in WAL mode reports SQLITE_BUSY_SNAPSHOT when a read transaction tries to
     * write after another connection committed, which busy_timeout does not cover, and Postgres and
     * MySQL report serialization failures and deadlocks the same way.
     */
    private <R> R inTransaction(TxFunction<R> function) {
        for (int attempt = 1; ; attempt++) {
            try {
                return inTransactionOnce(function);
            } catch (NonTransientException e) {
                if (attempt >= MAX_ATTEMPTS || !isLockConflict(e.getCause())) {
                    throw e;
                }
                try {
                    Thread.sleep(10L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    static boolean isLockConflict(Throwable t) {
        for (; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                String state = sql.getSQLState();
                String message = String.valueOf(sql.getMessage());
                if ("40001".equals(state) // serialization failure
                        || "40P01".equals(state) // Postgres deadlock
                        || sql.getErrorCode() == 1213 // MySQL deadlock
                        || sql.getErrorCode() == 1205 // MySQL lock wait timeout
                        || message.contains("SQLITE_BUSY")
                        || message.contains("SQLITE_LOCKED")) {
                    return true;
                }
            }
        }
        return false;
    }

    private <R> R inTransactionOnce(TxFunction<R> function) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                R result = function.apply(c);
                c.commit();
                return result;
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            } catch (Exception e) {
                c.rollback();
                throw new NonTransientException("workflow message queue operation failed", e);
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new NonTransientException("workflow message queue operation failed", e);
        }
    }
}
