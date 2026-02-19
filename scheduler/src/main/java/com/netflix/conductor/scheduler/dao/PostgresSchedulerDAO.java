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
package com.netflix.conductor.scheduler.dao;

import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.scheduler.model.WorkflowSchedule;
import com.netflix.conductor.scheduler.model.WorkflowScheduleExecution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PostgreSQL implementation of {@link SchedulerDAO}.
 *
 * <p>Uses Spring {@link JdbcTemplate} and Flyway-managed migrations ({@code
 * db/migration_scheduler}). All queries include the {@code org_id} column so the schema is
 * compatible with Orkes Conductor; OSS always passes {@link WorkflowSchedule#DEFAULT_ORG_ID}.
 */
public class PostgresSchedulerDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(PostgresSchedulerDAO.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresSchedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Schedule CRUD
    // -------------------------------------------------------------------------

    @Override
    public void updateSchedule(WorkflowSchedule schedule) {
        String sql =
                """
                INSERT INTO workflow_schedule (org_id, schedule_name, workflow_name, json_data, next_run_time)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (org_id, schedule_name)
                DO UPDATE SET workflow_name = EXCLUDED.workflow_name,
                              json_data     = EXCLUDED.json_data,
                              next_run_time = EXCLUDED.next_run_time
                """;
        jdbc.update(
                sql,
                schedule.getOrgId(),
                schedule.getName(),
                schedule.getStartWorkflowRequest() != null
                        ? schedule.getStartWorkflowRequest().getName()
                        : null,
                toJson(schedule),
                schedule.getNextRunTime());
    }

    @Override
    public WorkflowSchedule findScheduleByName(String orgId, String name) {
        String sql =
                "SELECT json_data FROM workflow_schedule WHERE org_id = ? AND schedule_name = ?";
        List<WorkflowSchedule> results = jdbc.query(sql, scheduleRowMapper(), orgId, name);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<WorkflowSchedule> getAllSchedules(String orgId) {
        String sql = "SELECT json_data FROM workflow_schedule WHERE org_id = ?";
        return jdbc.query(sql, scheduleRowMapper(), orgId);
    }

    @Override
    public List<WorkflowSchedule> findAllSchedules(String orgId, String workflowName) {
        String sql =
                "SELECT json_data FROM workflow_schedule WHERE org_id = ? AND workflow_name = ?";
        return jdbc.query(sql, scheduleRowMapper(), orgId, workflowName);
    }

    @Override
    public void deleteWorkflowSchedule(String orgId, String name) {
        jdbc.update(
                "DELETE FROM workflow_schedule WHERE org_id = ? AND schedule_name = ?",
                orgId,
                name);
        jdbc.update(
                "DELETE FROM workflow_schedule_execution WHERE org_id = ? AND schedule_name = ?",
                orgId,
                name);
    }

    // -------------------------------------------------------------------------
    // Execution tracking
    // -------------------------------------------------------------------------

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecution execution) {
        String sql =
                """
                INSERT INTO workflow_schedule_execution
                    (org_id, execution_id, schedule_name, workflow_id, scheduled_time, execution_time, state, reason, zone_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (org_id, execution_id)
                DO UPDATE SET workflow_id     = EXCLUDED.workflow_id,
                              execution_time  = EXCLUDED.execution_time,
                              state           = EXCLUDED.state,
                              reason          = EXCLUDED.reason
                """;
        jdbc.update(
                sql,
                execution.getOrgId(),
                execution.getExecutionId(),
                execution.getScheduleName(),
                execution.getWorkflowId(),
                execution.getScheduledTime(),
                execution.getExecutionTime(),
                execution.getState() != null ? execution.getState().name() : null,
                execution.getReason(),
                execution.getZoneId());
    }

    @Override
    public WorkflowScheduleExecution readExecutionRecord(String orgId, String executionId) {
        String sql =
                """
                SELECT org_id, execution_id, schedule_name, workflow_id,
                       scheduled_time, execution_time, state, reason, zone_id
                FROM workflow_schedule_execution
                WHERE org_id = ? AND execution_id = ?
                """;
        List<WorkflowScheduleExecution> results =
                jdbc.query(sql, executionRowMapper(), orgId, executionId);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void removeExecutionRecord(String orgId, String executionId) {
        jdbc.update(
                "DELETE FROM workflow_schedule_execution WHERE org_id = ? AND execution_id = ?",
                orgId,
                executionId);
    }

    @Override
    public List<String> getPendingExecutionRecordIds(String orgId) {
        String sql =
                "SELECT execution_id FROM workflow_schedule_execution WHERE org_id = ? AND state = 'POLLED'";
        return jdbc.queryForList(sql, String.class, orgId);
    }

    @Override
    public List<WorkflowScheduleExecution> getExecutionRecords(
            String orgId, String scheduleName, int limit) {
        String sql =
                """
                SELECT org_id, execution_id, schedule_name, workflow_id,
                       scheduled_time, execution_time, state, reason, zone_id
                FROM workflow_schedule_execution
                WHERE org_id = ? AND schedule_name = ?
                ORDER BY execution_time DESC
                LIMIT ?
                """;
        return jdbc.query(sql, executionRowMapper(), orgId, scheduleName, limit);
    }

    // -------------------------------------------------------------------------
    // Next-run time management
    // -------------------------------------------------------------------------

    @Override
    public long getNextRunTimeInEpoch(String orgId, String scheduleName) {
        String sql =
                "SELECT next_run_time FROM workflow_schedule WHERE org_id = ? AND schedule_name = ?";
        List<Long> results = jdbc.queryForList(sql, Long.class, orgId, scheduleName);
        if (results.isEmpty() || results.get(0) == null) {
            return -1L;
        }
        return results.get(0);
    }

    @Override
    public void setNextRunTimeInEpoch(String orgId, String scheduleName, long epochMillis) {
        jdbc.update(
                "UPDATE workflow_schedule SET next_run_time = ? WHERE org_id = ? AND schedule_name = ?",
                epochMillis,
                orgId,
                scheduleName);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private RowMapper<WorkflowSchedule> scheduleRowMapper() {
        return (rs, rowNum) -> {
            String json = rs.getString("json_data");
            try {
                return objectMapper.readValue(json, WorkflowSchedule.class);
            } catch (Exception e) {
                throw new NonTransientException("Failed to deserialize WorkflowSchedule", e);
            }
        };
    }

    private RowMapper<WorkflowScheduleExecution> executionRowMapper() {
        return (rs, rowNum) -> {
            WorkflowScheduleExecution exec = new WorkflowScheduleExecution();
            exec.setOrgId(rs.getString("org_id"));
            exec.setExecutionId(rs.getString("execution_id"));
            exec.setScheduleName(rs.getString("schedule_name"));
            exec.setWorkflowId(rs.getString("workflow_id"));
            exec.setScheduledTime(rs.getLong("scheduled_time"));
            exec.setExecutionTime(rs.getLong("execution_time"));
            String state = rs.getString("state");
            if (state != null) {
                exec.setState(WorkflowScheduleExecution.ExecutionState.valueOf(state));
            }
            exec.setReason(rs.getString("reason"));
            exec.setZoneId(rs.getString("zone_id"));
            return exec;
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
