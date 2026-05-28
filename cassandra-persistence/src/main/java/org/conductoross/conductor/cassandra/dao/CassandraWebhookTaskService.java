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
package org.conductoross.conductor.cassandra.dao;

import java.util.HashSet;
import java.util.Set;

import org.conductoross.conductor.service.webhook.WebhookTaskHashing;
import org.conductoross.conductor.service.webhook.WebhookTaskService;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.cassandra.dao.CassandraBaseDAO;
import com.netflix.conductor.model.TaskModel;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Cassandra-backed {@link WebhookTaskService}. Stores (hash, task_id) rows in {@code
 * webhook_hash_to_taskid} with hash as partition key — efficient {@code get(hash)} via a
 * single-partition scan. Same {@link WebhookTaskHashing} as every other backing so the hash a task
 * is stored under is identical regardless of impl.
 */
@Slf4j
public class CassandraWebhookTaskService extends CassandraBaseDAO implements WebhookTaskService {

    private static final String TABLE = "webhook_hash_to_taskid";

    private final Session session;
    private final PreparedStatement insertStmt;
    private final PreparedStatement selectByHashStmt;
    private final PreparedStatement deleteStmt;

    public CassandraWebhookTaskService(
            Session session, ObjectMapper objectMapper, CassandraProperties properties) {
        super(session, objectMapper, properties);
        this.session = session;
        ensureTables();
        ConsistencyLevel readConsistency = properties.getReadConsistencyLevel();
        ConsistencyLevel writeConsistency = properties.getWriteConsistencyLevel();
        String table = properties.getKeyspace() + "." + TABLE;
        insertStmt =
                session.prepare("INSERT INTO " + table + " (hash, task_id) VALUES (?, ?)")
                        .setConsistencyLevel(writeConsistency);
        selectByHashStmt =
                session.prepare("SELECT task_id FROM " + table + " WHERE hash = ?")
                        .setConsistencyLevel(readConsistency);
        deleteStmt =
                session.prepare("DELETE FROM " + table + " WHERE hash = ? AND task_id = ?")
                        .setConsistencyLevel(writeConsistency);
    }

    private void ensureTables() {
        String ks = properties.getKeyspace();
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
                        + "."
                        + TABLE
                        + " ("
                        + "hash text,"
                        + "task_id text,"
                        + "PRIMARY KEY ((hash), task_id)"
                        + ")");
    }

    @Override
    public void put(TaskModel task, int workflowVersion) {
        String hash = WebhookTaskHashing.computeHash(task, workflowVersion);
        session.execute(insertStmt.bind(hash, task.getTaskId()));
    }

    @Override
    public Set<String> get(String hash) {
        ResultSet rs = session.execute(selectByHashStmt.bind(hash));
        Set<String> taskIds = new HashSet<>();
        for (Row row : rs) {
            taskIds.add(row.getString("task_id"));
        }
        return taskIds;
    }

    @Override
    public void remove(String hash, String taskId) {
        session.execute(deleteStmt.bind(hash, taskId));
    }
}
