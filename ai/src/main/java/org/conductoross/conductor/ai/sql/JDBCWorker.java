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
package org.conductoross.conductor.ai.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JDBCWorker implements AnnotatedSystemTaskWorker {

    public static final String NAME = "JDBC";
    private final JDBCProvider jdbcProvider;

    public JDBCWorker(JDBCProvider jdbcProvider) {
        this.jdbcProvider = jdbcProvider;
        log.info("JDBCWorker initialized");
    }

    @WorkerTask(NAME)
    public TaskResult execute(JDBCInput input) {
        Task task = TaskContext.get().getTask();
        if (input.getStatement() == null) {
            task.setStatus(Task.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion("Missing JDBC statement");
            return new TaskResult(task);
        }

        DataSource ds = jdbcProvider.get(input);
        if (ds == null) {
            task.setStatus(Task.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion("No such datasource configured.  input: " + input);
            return new TaskResult(task);
        }

        switch (input.getType()) {
            case SELECT:
                return executeSelect(ds, input, task);
            case UPDATE:
                return executeUpdate(ds, input, task);
            default:
                task.setStatus(Task.Status.FAILED);
                task.setReasonForIncompletion("Unsupported Operation " + input.getType());
                return new TaskResult(task);
        }
    }

    private TaskResult executeSelect(DataSource ds, JDBCInput input, Task task) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = ds.getConnection();
            pstmt = conn.prepareStatement(input.getStatement());

            if (input.getParameters() != null) {
                for (int i = 0; i < input.getParameters().size(); i++) {
                    pstmt.setObject(i + 1, input.getParameters().get(i));
                }
            }

            rs = pstmt.executeQuery();
            ResultSetMetaData metadata = rs.getMetaData();
            int colCount = metadata.getColumnCount();
            List<Map<String, Object>> result = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String key = metadata.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(key, value);
                }
                result.add(row);
            }

            log.debug("Executed SELECT, found {} rows", result.size());
            task.getOutputData().put("result", result);
            task.setStatus(Task.Status.COMPLETED);
            return new TaskResult(task);

        } catch (SQLException sqlException) {
            log.error(sqlException.getMessage(), sqlException);
            task.setStatus(Task.Status.FAILED);
            task.setReasonForIncompletion(sqlException.getMessage());
            return new TaskResult(task);

        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    log.error("Failed to close ResultSet", e);
                }
            }
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException e) {
                    log.error("Failed to close PreparedStatement", e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close Connection", e);
                }
            }
        }
    }

    private TaskResult executeUpdate(DataSource ds, JDBCInput input, Task task) {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(input.getStatement());

            if (input.getParameters() != null) {
                for (int i = 0; i < input.getParameters().size(); i++) {
                    pstmt.setObject(i + 1, input.getParameters().get(i));
                }
            }

            int count = pstmt.executeUpdate();
            log.debug("updated {} rows", count);

            if (input.getExpectedUpdateCount() > 0 && count != input.getExpectedUpdateCount()) {
                log.debug(
                        "row update count {} does not match with expected update {}.  Going to rollback",
                        count,
                        input.getExpectedUpdateCount());

                conn.rollback();

                task.getOutputData().put("update_count", count);
                task.setStatus(Task.Status.FAILED);
                task.setReasonForIncompletion(
                        "Update count "
                                + count
                                + " does not match with expected update count "
                                + input.getExpectedUpdateCount());
                return new TaskResult(task);
            }

            conn.commit();
            task.getOutputData().put("update_count", count);
            task.setStatus(Task.Status.COMPLETED);
            return new TaskResult(task);

        } catch (SQLException sqlException) {
            log.error(sqlException.getMessage(), sqlException);

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    log.error("Failed to rollback transaction", e);
                }
            }

            task.setStatus(Task.Status.FAILED);
            task.setReasonForIncompletion(sqlException.getMessage());
            return new TaskResult(task);

        } finally {
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException e) {
                    log.error("Failed to close PreparedStatement", e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close Connection", e);
                }
            }
        }
    }
}
