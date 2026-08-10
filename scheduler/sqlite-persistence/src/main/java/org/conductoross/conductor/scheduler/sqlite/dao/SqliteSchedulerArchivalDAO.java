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

import java.util.*;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NonTransientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.archive.SchedulerSearchQuery;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;

/**
 * SQLite implementation of {@link SchedulerArchivalDAO}.
 *
 * <p>Stores execution records in the {@code workflow_scheduled_executions} table with individual
 * columns for efficient querying and filtering. Managed by Flyway ({@code
 * db/migration_scheduler_sqlite}).
 */
public class SqliteSchedulerArchivalDAO implements SchedulerArchivalDAO {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchedulerArchivalDAO.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SqliteSchedulerArchivalDAO(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel executionModel) {
        String sql =
                "INSERT OR REPLACE INTO workflow_scheduled_executions "
                        + "(execution_id, schedule_name, workflow_name, workflow_id, reason, "
                        + "stack_trace, state, scheduled_time, execution_time, start_workflow_request) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(
                sql,
                executionModel.getExecutionId(),
                executionModel.getScheduleName(),
                executionModel.getWorkflowName(),
                executionModel.getWorkflowId(),
                executionModel.getReason(),
                executionModel.getStackTrace(),
                executionModel.getState() != null ? executionModel.getState().name() : null,
                executionModel.getScheduledTime(),
                executionModel.getExecutionTime(),
                serializeStartWorkflowRequest(executionModel.getStartWorkflowRequest()));
    }

    @Override
    public SearchResult<String> searchScheduledExecutions(
            String query, String freeText, int start, int count, List<String> sort) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT execution_id FROM workflow_scheduled_executions WHERE 1=1");
        StringBuilder countSql =
                new StringBuilder("SELECT COUNT(*) FROM workflow_scheduled_executions WHERE 1=1");
        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        SchedulerSearchQuery parsed = SchedulerSearchQuery.parse(query);
        if (parsed.hasScheduleNames()) {
            String placeholders =
                    String.join(",", Collections.nCopies(parsed.getScheduleNames().size(), "?"));
            sql.append(" AND schedule_name IN (").append(placeholders).append(")");
            countSql.append(" AND schedule_name IN (").append(placeholders).append(")");
            params.addAll(parsed.getScheduleNames());
            countParams.addAll(parsed.getScheduleNames());
        }
        if (parsed.hasStates()) {
            String placeholders =
                    String.join(",", Collections.nCopies(parsed.getStates().size(), "?"));
            sql.append(" AND state IN (").append(placeholders).append(")");
            countSql.append(" AND state IN (").append(placeholders).append(")");
            params.addAll(parsed.getStates());
            countParams.addAll(parsed.getStates());
        }
        if (parsed.getScheduledTimeAfter() != null) {
            sql.append(" AND scheduled_time > ?");
            countSql.append(" AND scheduled_time > ?");
            params.add(parsed.getScheduledTimeAfter());
            countParams.add(parsed.getScheduledTimeAfter());
        }
        if (parsed.getScheduledTimeBefore() != null) {
            sql.append(" AND scheduled_time < ?");
            countSql.append(" AND scheduled_time < ?");
            params.add(parsed.getScheduledTimeBefore());
            countParams.add(parsed.getScheduledTimeBefore());
        }
        if (parsed.hasWorkflowName()) {
            sql.append(" AND workflow_name LIKE ?");
            countSql.append(" AND workflow_name LIKE ?");
            String like = "%" + parsed.getWorkflowName() + "%";
            params.add(like);
            countParams.add(like);
        }
        if (parsed.hasExecutionId()) {
            sql.append(" AND execution_id = ?");
            countSql.append(" AND execution_id = ?");
            params.add(parsed.getExecutionId());
            countParams.add(parsed.getExecutionId());
        }

        long totalHits =
                jdbc.queryForObject(
                        countSql.toString(), Long.class, countParams.toArray(new Object[0]));

        String orderBy = buildOrderByClause(sort);
        sql.append(orderBy).append(" LIMIT ? OFFSET ?");
        params.add(count);
        params.add(start);

        List<String> executionIds =
                jdbc.queryForList(sql.toString(), String.class, params.toArray(new Object[0]));

        return new SearchResult<>(totalHits, executionIds);
    }

    private static String buildOrderByClause(List<String> sortOptions) {
        if (sortOptions == null || sortOptions.isEmpty()) {
            return " ORDER BY scheduled_time DESC";
        }
        List<String> orderClauses = new ArrayList<>();
        for (String sortOption : sortOptions) {
            String[] parts = sortOption.split(":");
            String field = parts[0].trim();
            String direction =
                    parts.length > 1 && "ASC".equalsIgnoreCase(parts[1].trim()) ? "ASC" : "DESC";
            String column = SchedulerSearchQuery.resolveColumnName(field);
            orderClauses.add(column + " " + direction);
        }
        return " ORDER BY " + String.join(", ", orderClauses);
    }

    @Override
    public Map<String, WorkflowScheduleExecutionModel> getExecutionsByIds(
            Set<String> executionIds) {
        if (executionIds == null || executionIds.isEmpty()) {
            return new HashMap<>();
        }
        String placeholders = executionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql =
                "SELECT execution_id, schedule_name, workflow_name, workflow_id, reason, "
                        + "stack_trace, state, scheduled_time, execution_time, start_workflow_request "
                        + "FROM workflow_scheduled_executions "
                        + "WHERE execution_id IN ("
                        + placeholders
                        + ")";

        List<WorkflowScheduleExecutionModel> results =
                jdbc.query(sql, executionRowMapper(), executionIds.toArray());

        Map<String, WorkflowScheduleExecutionModel> resultMap = new HashMap<>();
        for (WorkflowScheduleExecutionModel model : results) {
            resultMap.put(model.getExecutionId(), model);
        }
        return resultMap;
    }

    @Override
    public WorkflowScheduleExecutionModel getExecutionById(String executionId) {
        String sql =
                "SELECT execution_id, schedule_name, workflow_name, workflow_id, reason, "
                        + "stack_trace, state, scheduled_time, execution_time, start_workflow_request "
                        + "FROM workflow_scheduled_executions WHERE execution_id = ?";
        List<WorkflowScheduleExecutionModel> results =
                jdbc.query(sql, executionRowMapper(), executionId);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void cleanupOldRecords(int archivalMaxRecords, int archivalMaxRecordThreshold) {
        String findSchedulesSql =
                "SELECT schedule_name, COUNT(*) AS cnt "
                        + "FROM workflow_scheduled_executions "
                        + "GROUP BY schedule_name "
                        + "HAVING COUNT(*) > ?";

        List<String> scheduleNames =
                jdbc.query(
                        findSchedulesSql,
                        (rs, rowNum) -> rs.getString("schedule_name"),
                        archivalMaxRecordThreshold);

        for (String scheduleName : scheduleNames) {
            String deleteSql =
                    "DELETE FROM workflow_scheduled_executions "
                            + "WHERE schedule_name = ? "
                            + "AND execution_id NOT IN ("
                            + "  SELECT execution_id FROM workflow_scheduled_executions "
                            + "  WHERE schedule_name = ? "
                            + "  ORDER BY scheduled_time DESC "
                            + "  LIMIT ?"
                            + ")";
            int deleted = jdbc.update(deleteSql, scheduleName, scheduleName, archivalMaxRecords);
            if (deleted > 0) {
                log.info(
                        "Cleaned up {} old archival records for schedule: {}",
                        deleted,
                        scheduleName);
            }
        }
    }

    private RowMapper<WorkflowScheduleExecutionModel> executionRowMapper() {
        return (rs, rowNum) -> {
            WorkflowScheduleExecutionModel model = new WorkflowScheduleExecutionModel();
            model.setExecutionId(rs.getString("execution_id"));
            model.setScheduleName(rs.getString("schedule_name"));
            model.setWorkflowName(rs.getString("workflow_name"));
            model.setWorkflowId(rs.getString("workflow_id"));
            model.setReason(rs.getString("reason"));
            model.setStackTrace(rs.getString("stack_trace"));
            String stateStr = rs.getString("state");
            if (stateStr != null) {
                model.setState(WorkflowScheduleExecutionModel.State.valueOf(stateStr));
            }
            model.setScheduledTime(rs.getObject("scheduled_time", Long.class));
            model.setExecutionTime(rs.getObject("execution_time", Long.class));
            model.setStartWorkflowRequest(
                    deserializeStartWorkflowRequest(rs.getString("start_workflow_request")));
            return model;
        };
    }

    private String serializeStartWorkflowRequest(StartWorkflowRequest request) {
        if (request == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new NonTransientException("Failed to serialize StartWorkflowRequest to JSON", e);
        }
    }

    private StartWorkflowRequest deserializeStartWorkflowRequest(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StartWorkflowRequest.class);
        } catch (Exception e) {
            throw new NonTransientException(
                    "Failed to deserialize StartWorkflowRequest from JSON", e);
        }
    }
}
