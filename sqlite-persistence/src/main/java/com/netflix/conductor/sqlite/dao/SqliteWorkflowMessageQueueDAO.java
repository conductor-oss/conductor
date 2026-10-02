/*
 * Copyright 2024 Conductor Authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.sqlite.util.Query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SQLite-backed implementation of {@link WorkflowMessageQueueDAO}.
 *
 * <p>Each workflow's messages are stored in the {@code workflow_message_queue} table, ordered by an
 * auto-increment {@code seq} column for reliable FIFO. Atomic pop is guaranteed by SQLite's
 * SERIALIZABLE transactions (already enforced by {@link SqliteBaseDAO}).
 *
 * <p>Unlike the Redis implementation, this DAO does not implement TTL-based expiration. Cleanup
 * relies on the workflow termination hook in {@code WorkflowExecutorOps}, which calls {@link
 * #delete(String)} when a workflow reaches a terminal state.
 */
public class SqliteWorkflowMessageQueueDAO extends SqliteBaseDAO
        implements WorkflowMessageQueueDAO {

    private static final String PUSH_SQL =
            "INSERT INTO workflow_message_queue (workflow_id, message_id, payload, received_at) VALUES (?, ?, ?, ?)";

    private static final String POP_SELECT_SQL =
            "SELECT seq, message_id, payload, received_at FROM workflow_message_queue WHERE workflow_id = ? ORDER BY seq LIMIT ?";

    private static final String POP_DELETE_SQL =
            "DELETE FROM workflow_message_queue WHERE seq IN (%s)";

    private static final String SIZE_SQL =
            "SELECT COUNT(*) FROM workflow_message_queue WHERE workflow_id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM workflow_message_queue WHERE workflow_id = ?";

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final WorkflowMessageQueueProperties wmqProperties;

    public SqliteWorkflowMessageQueueDAO(
            RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            WorkflowMessageQueueProperties wmqProperties) {
        super(retryTemplate, objectMapper, dataSource);
        this.wmqProperties = wmqProperties;
    }

    @Override
    public void push(String workflowId, WorkflowMessage message) {
        getWithRetriedTransactions(
                tx -> {
                    long currentSize =
                            query(tx, SIZE_SQL, q -> q.addParameter(workflowId).executeCount());
                    if (currentSize >= wmqProperties.getMaxQueueSize()) {
                        throw new IllegalStateException(
                                "Workflow message queue for workflowId="
                                        + workflowId
                                        + " has reached the maximum size of "
                                        + wmqProperties.getMaxQueueSize());
                    }
                    execute(
                            tx,
                            PUSH_SQL,
                            q ->
                                    q.addParameter(workflowId)
                                            .addParameter(message.getId())
                                            .addParameter(toJson(message.getPayload()))
                                            .addParameter(message.getReceivedAt())
                                            .executeUpdate());
                    return null;
                });
    }

    @Override
    public List<WorkflowMessage> pop(String workflowId, int maxCount) {
        return getWithRetriedTransactions(
                tx -> {
                    List<Long> seqs = new ArrayList<>();
                    List<WorkflowMessage> messages =
                            query(
                                    tx,
                                    POP_SELECT_SQL,
                                    q -> {
                                        q.addParameter(workflowId).addParameter(maxCount);
                                        return q.executeAndFetch(
                                                rs -> {
                                                    List<WorkflowMessage> result =
                                                            new ArrayList<>();
                                                    while (rs.next()) {
                                                        seqs.add(rs.getLong("seq"));
                                                        WorkflowMessage msg = new WorkflowMessage();
                                                        msg.setId(rs.getString("message_id"));
                                                        msg.setWorkflowId(workflowId);
                                                        msg.setPayload(
                                                                readValue(
                                                                        rs.getString("payload"),
                                                                        MAP_TYPE_REF));
                                                        msg.setReceivedAt(
                                                                rs.getString("received_at"));
                                                        result.add(msg);
                                                    }
                                                    return result;
                                                });
                                    });

                    if (!seqs.isEmpty()) {
                        String placeholders = Query.generateInBindings(seqs.size());
                        String deleteSql = String.format(POP_DELETE_SQL, placeholders);
                        execute(
                                tx,
                                deleteSql,
                                q -> {
                                    for (Long seq : seqs) {
                                        q.addParameter(seq);
                                    }
                                    q.executeUpdate();
                                });
                    }

                    return messages == null ? Collections.emptyList() : messages;
                });
    }

    @Override
    public long size(String workflowId) {
        return queryWithTransaction(SIZE_SQL, q -> q.addParameter(workflowId).executeCount());
    }

    @Override
    public void delete(String workflowId) {
        executeWithTransaction(DELETE_SQL, q -> q.addParameter(workflowId).executeUpdate());
    }
}
