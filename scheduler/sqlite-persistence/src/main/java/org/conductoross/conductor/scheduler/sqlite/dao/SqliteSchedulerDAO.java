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
package org.conductoross.conductor.scheduler.sqlite.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NonTransientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * SQLite implementation of {@link SchedulerDAO}.
 *
 * <p>Mirrors the Orkes Conductor schema: schedules and execution records are stored as JSON blobs
 * in {@code scheduler} and {@code scheduler_execution} tables respectively. The {@code
 * scheduler_execution} table additionally carries {@code schedule_name} and {@code state} columns
 * to support efficient queries (OSS has no queue infrastructure to offload this work).
 *
 * <p>Managed by Flyway ({@code db/migration_scheduler_sqlite}).
 */
public class SqliteSchedulerDAO implements SchedulerDAO {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SqliteSchedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel schedule) {
        jdbc.update(
                "INSERT OR REPLACE INTO scheduler (scheduler_name, workflow_name, json_data, next_run_time) "
                        + "VALUES (?, ?, ?, ?)",
                schedule.getName(),
                schedule.getStartWorkflowRequest() != null
                        ? schedule.getStartWorkflowRequest().getName()
                        : null,
                toJson(schedule),
                schedule.getNextRunTime());
        // Reset the cached next-run-time so SchedulerService can set a fresh value
        // via setNextRunTimeInEpoch after recomputing the schedule.
        jdbc.update("DELETE FROM scheduler_next_run WHERE key = ?", schedule.getName());
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String name) {
        String sql = "SELECT json_data FROM scheduler WHERE scheduler_name = ?";
        List<WorkflowScheduleModel> results = jdbc.query(sql, scheduleRowMapper(), name);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules() {
        return jdbc.query("SELECT json_data FROM scheduler", scheduleRowMapper());
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String workflowName) {
        String sql = "SELECT json_data FROM scheduler WHERE workflow_name = ?";
        return jdbc.query(sql, scheduleRowMapper(), workflowName);
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        String placeholders = names.stream().map(n -> "?").collect(Collectors.joining(", "));
        String sql =
                "SELECT json_data FROM scheduler WHERE scheduler_name IN (" + placeholders + ")";
        List<WorkflowScheduleModel> schedules =
                jdbc.query(sql, scheduleRowMapper(), names.toArray(new Object[0]));
        Map<String, WorkflowScheduleModel> result = new HashMap<>();
        for (WorkflowScheduleModel s : schedules) {
            result.put(s.getName(), s);
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        jdbc.update("DELETE FROM scheduler_execution WHERE schedule_name = ?", name);
        jdbc.update("DELETE FROM scheduler_next_run WHERE key = ?", name);
        jdbc.update("DELETE FROM scheduler WHERE scheduler_name = ?", name);
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel execution) {
        String sql =
                "INSERT OR REPLACE INTO scheduler_execution (execution_id, schedule_name, state, json_data) "
                        + "VALUES (?, ?, ?, ?)";
        jdbc.update(
                sql,
                execution.getExecutionId(),
                execution.getScheduleName(),
                execution.getState() != null ? execution.getState().name() : null,
                toJson(execution));
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String executionId) {
        String sql = "SELECT json_data FROM scheduler_execution WHERE execution_id = ?";
        List<WorkflowScheduleExecutionModel> results =
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
    public long getNextRunTimeInEpoch(String scheduleName) {
        List<Long> results =
                jdbc.queryForList(
                        "SELECT epoch_millis FROM scheduler_next_run WHERE key = ?",
                        Long.class,
                        scheduleName);
        if (results.isEmpty() || results.get(0) == null) {
            return -1L;
        }
        return results.get(0);
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        // INSERT OR REPLACE accepts any key: schedule names (single-cron) and JSON payload
        // strings like {"name":"s","cron":"...","id":0} (multi-cron) both work.
        jdbc.update(
                "INSERT OR REPLACE INTO scheduler_next_run (key, epoch_millis) VALUES (?, ?)",
                scheduleName,
                epochMillis);
    }

    @Override
    public SearchResult<WorkflowScheduleModel> searchSchedules(
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        StringBuilder sql = new StringBuilder("SELECT json_data FROM scheduler WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM scheduler WHERE 1=1");
        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        if (workflowName != null && !workflowName.isEmpty()) {
            sql.append(" AND workflow_name = ?");
            countSql.append(" AND workflow_name = ?");
            params.add(workflowName);
            countParams.add(workflowName);
        }
        if (scheduleName != null && !scheduleName.isEmpty()) {
            sql.append(" AND scheduler_name LIKE ?");
            countSql.append(" AND scheduler_name LIKE ?");
            params.add("%" + scheduleName + "%");
            countParams.add("%" + scheduleName + "%");
        }
        if (paused != null) {
            sql.append(" AND json_extract(json_data, '$.paused') = ?");
            countSql.append(" AND json_extract(json_data, '$.paused') = ?");
            // SQLite json_extract returns 1/0 for booleans, not true/false
            params.add(paused ? 1 : 0);
            countParams.add(paused ? 1 : 0);
        }

        long totalHits =
                jdbc.queryForObject(
                        countSql.toString(), Long.class, countParams.toArray(new Object[0]));

        sql.append(" ORDER BY scheduler_name LIMIT ? OFFSET ?");
        params.add(size);
        params.add(start);

        List<WorkflowScheduleModel> results =
                jdbc.query(sql.toString(), scheduleRowMapper(), params.toArray(new Object[0]));

        return new SearchResult<>(totalHits, results);
    }

    private RowMapper<WorkflowScheduleModel> scheduleRowMapper() {
        return (rs, rowNum) -> {
            try {
                return objectMapper.readValue(
                        rs.getString("json_data"), WorkflowScheduleModel.class);
            } catch (Exception e) {
                throw new NonTransientException("Failed to deserialize WorkflowScheduleModel", e);
            }
        };
    }

    private RowMapper<WorkflowScheduleExecutionModel> executionRowMapper() {
        return (rs, rowNum) -> {
            try {
                return objectMapper.readValue(
                        rs.getString("json_data"), WorkflowScheduleExecutionModel.class);
            } catch (Exception e) {
                throw new NonTransientException(
                        "Failed to deserialize WorkflowScheduleExecutionModel", e);
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
