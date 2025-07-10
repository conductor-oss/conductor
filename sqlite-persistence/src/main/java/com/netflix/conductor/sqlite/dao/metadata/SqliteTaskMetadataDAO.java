/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao.metadata;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.sqlite.dao.SqliteBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class SqliteTaskMetadataDAO extends SqliteBaseDAO {

    public SqliteTaskMetadataDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    public TaskDef createTaskDef(TaskDef taskDef) {
        validate(taskDef);
        insertOrUpdateTaskDef(taskDef);
        return taskDef;
    }

    public TaskDef updateTaskDef(TaskDef taskDef) {
        validate(taskDef);
        insertOrUpdateTaskDef(taskDef);
        return taskDef;
    }

    public TaskDef getTaskDef(String name) {
        Preconditions.checkNotNull(name, "TaskDef name cannot be null");
        return getTaskDefFromDB(name);
    }

    public List<TaskDef> getAllTaskDefs() {
        return getWithRetriedTransactions(this::findAllTaskDefs);
    }

    public void removeTaskDef(String name) {
        final String DELETE_TASKDEF_QUERY = "DELETE FROM meta_task_def WHERE name = ?";

        executeWithTransaction(
                DELETE_TASKDEF_QUERY,
                q -> {
                    if (!q.addParameter(name).executeDelete()) {
                        throw new NotFoundException("No such task definition");
                    }
                });
    }

    private void validate(TaskDef taskDef) {
        Preconditions.checkNotNull(taskDef, "TaskDef object cannot be null");
        Preconditions.checkNotNull(taskDef.getName(), "TaskDef name cannot be null");
    }

    private TaskDef getTaskDefFromDB(String name) {
        final String READ_ONE_TASKDEF_QUERY = "SELECT json_data FROM meta_task_def WHERE name = ?";

        return queryWithTransaction(
                READ_ONE_TASKDEF_QUERY,
                q -> q.addParameter(name).executeAndFetchFirst(TaskDef.class));
    }

    private String insertOrUpdateTaskDef(TaskDef taskDef) {
        final String UPDATE_TASKDEF_QUERY =
                "UPDATE meta_task_def SET json_data = ?, modified_on = CURRENT_TIMESTAMP WHERE name = ?";

        final String INSERT_TASKDEF_QUERY =
                "INSERT INTO meta_task_def (name, json_data) VALUES (?, ?)";

        return getWithRetriedTransactions(
                tx -> {
                    execute(
                            tx,
                            UPDATE_TASKDEF_QUERY,
                            update -> {
                                int result =
                                        update.addJsonParameter(taskDef)
                                                .addParameter(taskDef.getName())
                                                .executeUpdate();
                                if (result == 0) {
                                    execute(
                                            tx,
                                            INSERT_TASKDEF_QUERY,
                                            insert ->
                                                    insert.addParameter(taskDef.getName())
                                                            .addJsonParameter(taskDef)
                                                            .executeUpdate());
                                }
                            });
                    return taskDef.getName();
                });
    }

    private List<TaskDef> findAllTaskDefs(Connection tx) {
        final String READ_ALL_TASKDEF_QUERY = "SELECT json_data FROM meta_task_def";

        return query(tx, READ_ALL_TASKDEF_QUERY, q -> q.executeAndFetch(TaskDef.class));
    }
}
