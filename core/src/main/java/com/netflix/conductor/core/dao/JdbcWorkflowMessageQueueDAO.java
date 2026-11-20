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
 * The durable workflow message queue on a SQL database, in the table {@code workflow_message_queue}
 * that each persistence module creates with its migrations. This class has the rules that are the
 * same on every database. Each persistence module has its own subclass for what differs: how a row
 * is locked, how a fence row is inserted, and on SQLite how a message is pushed and popped.
 *
 * <p>Messages pushed to a running workflow survive a server restart. Messages are ordered by an
 * auto-increment sequence. A pop reads and deletes in one transaction. A claim marks the messages
 * with the pull that took them ({@code claimed_by}, {@code claim_ordinal}) and keeps them, so a
 * second run of that pull gets them again. Claimed messages are deleted when the same pull claims
 * in a later loop iteration, or when the workflow ends. They do not count in {@link #size} or
 * toward the maximum queue size.
 */
public abstract class JdbcWorkflowMessageQueueDAO implements WorkflowMessageQueueDAO {

    private final DataSource dataSource;
    protected final ObjectMapper objectMapper;
    protected final int maxQueueSize;

    protected JdbcWorkflowMessageQueueDAO(
            DataSource dataSource,
            ObjectMapper objectMapper,
            WorkflowMessageQueueProperties properties) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.maxQueueSize = properties.getMaxQueueSize();
    }

    /**
     * What a SELECT ends with to lock the rows it reads until the transaction ends, such as {@code
     * " FOR UPDATE"}; empty on a database that serializes writers itself.
     */
    protected abstract String forUpdate();

    /**
     * An INSERT into {@code workflow_message_claim (workflow_id, claimant, ordinal)} that does
     * nothing when the row is there, and holds the row until the transaction ends.
     */
    protected abstract String insertFenceIfAbsent();

    @Override
    public void push(String workflowId, WorkflowMessage message) {
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
        return inTransaction(
                c -> {
                    List<Long> seqs = new ArrayList<>();
                    List<WorkflowMessage> messages = new ArrayList<>();
                    String select =
                            "SELECT seq, json_data FROM workflow_message_queue WHERE workflow_id = ?"
                                    + " AND claimed_by IS NULL ORDER BY seq LIMIT ?"
                                    + forUpdate();
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

    @Override
    public List<WorkflowMessage> claim(
            String workflowId, int maxCount, String claimant, long ordinal) {
        String lock = forUpdate();
        String mine =
                "SELECT seq, json_data FROM workflow_message_queue WHERE workflow_id = ?"
                        + " AND claimed_by = ? AND claim_ordinal = ? ORDER BY seq";
        return inTransaction(
                c -> {
                    if (!advanceFence(c, workflowId, claimant, ordinal)) {
                        // a later run of this pull has claimed: this run is stale, takes nothing
                        // new
                        return select(c, mine, workflowId, claimant, ordinal);
                    }
                    if (!lock.isEmpty()) {
                        // Serialize claims of one workflow: a second run of the same pull waits
                        // here, then sees the first run's claims instead of claiming other rows.
                        try (PreparedStatement ps =
                                c.prepareStatement(
                                        "SELECT seq FROM workflow_message_queue WHERE workflow_id = ? FOR UPDATE")) {
                            ps.setString(1, workflowId);
                            ps.executeQuery().close();
                        }
                    }
                    // an earlier iteration of this pull has recorded its result: the loop only
                    // came here after it. Claims of other pulls stay.
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "DELETE FROM workflow_message_queue WHERE workflow_id = ?"
                                            + " AND claimed_by = ? AND claim_ordinal < ?")) {
                        ps.setString(1, workflowId);
                        ps.setString(2, claimant);
                        ps.setLong(3, ordinal);
                        ps.executeUpdate();
                    }
                    List<WorkflowMessage> again =
                            select(c, mine + lock, workflowId, claimant, ordinal);
                    if (!again.isEmpty()) {
                        return again;
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
                                        "UPDATE workflow_message_queue SET claimed_by = ?, claim_ordinal = ?"
                                                + " WHERE seq = ?")) {
                            for (Long seq : seqs) {
                                ps.setString(1, claimant);
                                ps.setLong(2, ordinal);
                                ps.setLong(3, seq);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }
                    return claimed;
                });
    }

    /**
     * Raises the fence of this pull to {@code ordinal}, or reports that a higher ordinal of the
     * same pull has already claimed. Each pull of a workflow has its own fence: the ordinal is a
     * loop iteration, which means nothing across pulls. Holds the fence row for the rest of the
     * transaction.
     */
    private boolean advanceFence(Connection c, String workflowId, String claimant, long ordinal)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(insertFenceIfAbsent())) {
            ps.setString(1, workflowId);
            ps.setString(2, claimant);
            ps.setLong(3, ordinal);
            ps.executeUpdate();
        }
        long current;
        try (PreparedStatement ps =
                c.prepareStatement(
                        "SELECT ordinal FROM workflow_message_claim WHERE workflow_id = ? AND claimant = ?"
                                + forUpdate())) {
            ps.setString(1, workflowId);
            ps.setString(2, claimant);
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
                            "UPDATE workflow_message_claim SET ordinal = ?"
                                    + " WHERE workflow_id = ? AND claimant = ?")) {
                ps.setLong(1, ordinal);
                ps.setString(2, workflowId);
                ps.setString(3, claimant);
                ps.executeUpdate();
            }
        }
        return true;
    }

    private List<WorkflowMessage> select(
            Connection c, String sql, String workflowId, String claimant, long ordinal)
            throws Exception {
        List<WorkflowMessage> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setString(2, claimant);
            ps.setLong(3, ordinal);
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
                    // the fences go too, so a restarted execution's pulls start from iteration 1
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
                        "SELECT COUNT(*) FROM workflow_message_queue WHERE workflow_id = ?"
                                + " AND claimed_by IS NULL")) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    @FunctionalInterface
    protected interface TxFunction<R> {
        R apply(Connection connection) throws Exception;
    }

    private static final int MAX_ATTEMPTS = 5;

    /**
     * Runs {@code function} in one transaction. Lock conflicts between concurrent writers are
     * retried: SQLite in WAL mode reports SQLITE_BUSY_SNAPSHOT when a read transaction tries to
     * write after another connection committed, which busy_timeout does not cover, and Postgres and
     * MySQL report serialization failures and deadlocks the same way.
     */
    protected <R> R inTransaction(TxFunction<R> function) {
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
