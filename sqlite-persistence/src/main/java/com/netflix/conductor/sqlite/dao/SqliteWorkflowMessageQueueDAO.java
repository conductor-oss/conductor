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
package com.netflix.conductor.sqlite.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.sql.DataSource;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;

import tools.jackson.databind.ObjectMapper;

/**
 * The durable workflow message queue on SQLite.
 *
 * <p>SQLite serializes writers itself, so no row is locked. Push and pop are each one write
 * statement ({@code INSERT ... SELECT ... WHERE} and {@code DELETE ... RETURNING}): a transaction
 * that reads and then writes would have to upgrade its lock, and SQLite fails such an upgrade at
 * once with SQLITE_BUSY instead of waiting out busy_timeout, so concurrent pushes and pulls would
 * fail under load.
 */
public class SqliteWorkflowMessageQueueDAO extends JdbcWorkflowMessageQueueDAO {

    public SqliteWorkflowMessageQueueDAO(
            DataSource dataSource,
            ObjectMapper objectMapper,
            WorkflowMessageQueueProperties properties) {
        super(dataSource, objectMapper, properties);
    }

    @Override
    protected String forUpdate() {
        return "";
    }

    @Override
    protected String insertFenceIfAbsent() {
        return "INSERT INTO workflow_message_claim (workflow_id, claimant, ordinal) VALUES (?, ?, ?)"
                + " ON CONFLICT (workflow_id, claimant) DO NOTHING";
    }

    @Override
    public void push(String workflowId, WorkflowMessage message) {
        int inserted =
                inTransaction(
                        c -> {
                            try (PreparedStatement ps =
                                    c.prepareStatement(
                                            "INSERT INTO workflow_message_queue (message_id, workflow_id, json_data)"
                                                    + " SELECT ?, ?, ? WHERE (SELECT COUNT(*) FROM workflow_message_queue"
                                                    + " WHERE workflow_id = ? AND claimed_by IS NULL) < ?")) {
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

    @Override
    public List<WorkflowMessage> pop(String workflowId, int maxCount) {
        return inTransaction(
                c -> {
                    TreeMap<Long, WorkflowMessage> bySeq = new TreeMap<>();
                    try (PreparedStatement ps =
                            c.prepareStatement(
                                    "DELETE FROM workflow_message_queue WHERE seq IN (SELECT seq FROM"
                                            + " workflow_message_queue WHERE workflow_id = ? AND claimed_by IS NULL"
                                            + " ORDER BY seq LIMIT ?)"
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
}
