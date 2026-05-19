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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.conductoross.conductor.service.webhook.WebhookTaskHashing;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.sqlite.dao.SqliteBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/** SQLite-backed {@link WebhookTaskService}. Same schema/behavior as the postgres impl. */
@Slf4j
public class SqliteWebhookTaskService extends SqliteBaseDAO implements WebhookTaskService {

    private static final String INSERT =
            "INSERT INTO webhook_hash_to_taskid (hash, task_id) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING";
    private static final String SELECT =
            "SELECT task_id FROM webhook_hash_to_taskid WHERE hash = ?";
    private static final String DELETE =
            "DELETE FROM webhook_hash_to_taskid WHERE hash = ? AND task_id = ?";

    public SqliteWebhookTaskService(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void put(TaskModel task, int workflowVersion) {
        String hash = WebhookTaskHashing.computeHash(task, workflowVersion);
        queryWithTransaction(
                INSERT, q -> q.addParameter(hash).addParameter(task.getTaskId()).executeUpdate());
    }

    @Override
    public Set<String> get(String hash) {
        List<String> taskIds =
                queryWithTransaction(
                        SELECT, q -> q.addParameter(hash).executeAndFetch(String.class));
        return new HashSet<>(taskIds);
    }

    @Override
    public void remove(String hash, String taskId) {
        queryWithTransaction(
                DELETE, q -> q.addParameter(hash).addParameter(taskId).executeUpdate());
    }
}
