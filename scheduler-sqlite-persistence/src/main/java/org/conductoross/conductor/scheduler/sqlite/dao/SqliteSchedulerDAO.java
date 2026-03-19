/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.conductoross.conductor.scheduler.sqlite.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.netflix.conductor.core.exception.NonTransientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SQLite implementation of {@link SchedulerDAO}.
 *
 * <p>Uses Spring {@link JdbcTemplate} and Flyway-managed migrations ({@code
 * db/migration_scheduler_sqlite}). Functionally equivalent to the MySQL implementation but uses
 * SQLite-compatible SQL syntax ({@code INSERT OR REPLACE INTO} for upserts; no {@code NULLS LAST}
 * in ORDER BY; manual {@code IN} placeholder expansion).
 *
 * <p><b>Pool size constraint:</b> The DataSource must be configured with {@code maximumPoolSize=1}.
 * SQLite in-memory databases are connection-scoped; a second connection creates an independent
 * database instance.
 */
public class SqliteSchedulerDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchedulerDAO.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    public SqliteSchedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.objectMapper = objectMapper;
    }

    @Override
    public void updateSchedule(WorkflowSchedule schedule) {
        String sql =
                "INSERT OR REPLACE INTO scheduler "
                        + "(scheduler_name, workflow_name, json_data, next_run_time) "
                        + "VALUES (?, ?, ?, ?)";
        jdbc.update(
                sql,
                schedule.getName(),
                schedule.getStartWorkflowRequest() != null
                        ? schedule.getStartWorkflowRequest().getName()
                        : null,
                toJson(schedule),
                schedule.getNextRunTime());
    }

    @Override
    public WorkflowSchedule findScheduleByName(String name) {
        String sql = "SELECT json_data FROM scheduler WHERE scheduler_name = ?";
        List<WorkflowSchedule> results = jdbc.query(sql, scheduleRowMapper(), name);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<WorkflowSchedule> getAllSchedules() {
        return jdbc.query("SELECT json_data FROM scheduler", scheduleRowMapper());
    }

    @Override
    public List<WorkflowSchedule> findAllSchedules(String workflowName) {
        String sql = "SELECT json_data FROM scheduler WHERE workflow_name = ?";
        return jdbc.query(sql, scheduleRowMapper(), workflowName);
    }

    @Override
    public Map<String, WorkflowSchedule> findAllByNames(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        String placeholders = names.stream().map(n -> "?").collect(Collectors.joining(","));
        String sql =
                "SELECT json_data FROM scheduler WHERE scheduler_name IN (" + placeholders + ")";
        List<WorkflowSchedule> schedules = jdbc.query(sql, scheduleRowMapper(), names.toArray());
        Map<String, WorkflowSchedule> result = new HashMap<>();
        for (WorkflowSchedule s : schedules) {
            result.put(s.getName(), s);
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        txTemplate.executeWithoutResult(
                status -> {
                    jdbc.update(
                            "DELETE FROM scheduler_execution WHERE schedule_name = ?", name);
                    jdbc.update("DELETE FROM scheduler WHERE scheduler_name = ?", name);
                });
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecution execution) {
        String sql =
                "INSERT OR REPLACE INTO scheduler_execution "
                        + "(execution_id, schedule_name, state, execution_time, json_data) "
                        + "VALUES (?, ?, ?, ?, ?)";
        jdbc.update(
                sql,
                execution.getExecutionId(),
                execution.getScheduleName(),
                execution.getState() != null ? execution.getState().name() : null,
                execution.getExecutionTime(),
                toJson(execution));
    }

    @Override
    public WorkflowScheduleExecution readExecutionRecord(String executionId) {
        String sql = "SELECT json_data FROM scheduler_execution WHERE execution_id = ?";
        List<WorkflowScheduleExecution> results =
                jdbc.query(sql, executionRowMapper(), executionId);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        jdbc.update("DELETE FROM scheduler_execution WHERE execution_id = ?", executionId);
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        return jdbc.queryForList(
                "SELECT execution_id FROM scheduler_execution WHERE state = 'POLLED'",
                String.class);
    }

    @Override
    public List<WorkflowScheduleExecution> getPendingExecutionRecords() {
        return jdbc.query(
                "SELECT json_data FROM scheduler_execution WHERE state = 'POLLED'",
                executionRowMapper());
    }

    @Override
    public List<WorkflowScheduleExecution> getExecutionRecords(String scheduleName, int limit) {
        String sql =
                "SELECT json_data FROM scheduler_execution "
                        + "WHERE schedule_name = ? "
                        + "ORDER BY execution_time DESC "
                        + "LIMIT ?";
        return jdbc.query(sql, executionRowMapper(), scheduleName, limit);
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        String sql = "SELECT next_run_time FROM scheduler WHERE scheduler_name = ?";
        List<Long> results = jdbc.queryForList(sql, Long.class, scheduleName);
        if (results.isEmpty() || results.get(0) == null) {
            return -1L;
        }
        return results.get(0);
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        jdbc.update(
                "UPDATE scheduler SET next_run_time = ? WHERE scheduler_name = ?",
                epochMillis,
                scheduleName);
    }

    private RowMapper<WorkflowSchedule> scheduleRowMapper() {
        return (rs, rowNum) -> {
            try {
                return objectMapper.readValue(rs.getString("json_data"), WorkflowSchedule.class);
            } catch (Exception e) {
                throw new NonTransientException("Failed to deserialize WorkflowSchedule", e);
            }
        };
    }

    private RowMapper<WorkflowScheduleExecution> executionRowMapper() {
        return (rs, rowNum) -> {
            try {
                return objectMapper.readValue(
                        rs.getString("json_data"), WorkflowScheduleExecution.class);
            } catch (Exception e) {
                throw new NonTransientException(
                        "Failed to deserialize WorkflowScheduleExecution", e);
            }
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NonTransientException("Failed to serialize to JSON", e);
        }
    }
}
