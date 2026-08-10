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
package org.conductoross.conductor.scheduler.postgres.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.postgres.dao.PostgresBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.archive.SchedulerSearchQuery;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;

/**
 * PostgreSQL implementation of {@link SchedulerArchivalDAO}.
 *
 * <p>Extends {@link PostgresBaseDAO} for connection/transaction management. Archival execution
 * records are stored in the {@code workflow_scheduled_executions} table with individual columns for
 * efficient querying. Managed by Flyway ({@code db/migration_scheduler}).
 */
public class PostgresSchedulerArchivalDAO extends PostgresBaseDAO implements SchedulerArchivalDAO {

    private static final String DAO_NAME = "postgres";

    private static final String SELECT_COLUMNS =
            "execution_id, schedule_name, workflow_name, workflow_id,"
                    + " reason, stack_trace, state, scheduled_time, execution_time,"
                    + " start_workflow_request";

    public PostgresSchedulerArchivalDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel model) {
        Monitors.recordDaoRequests(DAO_NAME, "saveArchivalRecord", "n/a", "n/a");
        String sql =
                """
                INSERT INTO workflow_scheduled_executions
                    (execution_id, schedule_name, workflow_name, workflow_id,
                     reason, stack_trace, state, scheduled_time, execution_time,
                     start_workflow_request)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (execution_id)
                DO UPDATE SET schedule_name          = EXCLUDED.schedule_name,
                              workflow_name          = EXCLUDED.workflow_name,
                              workflow_id            = EXCLUDED.workflow_id,
                              reason                 = EXCLUDED.reason,
                              stack_trace            = EXCLUDED.stack_trace,
                              state                  = EXCLUDED.state,
                              scheduled_time         = EXCLUDED.scheduled_time,
                              execution_time         = EXCLUDED.execution_time,
                              start_workflow_request = EXCLUDED.start_workflow_request
                """;
        executeWithTransaction(
                sql,
                q ->
                        q.addParameter(model.getExecutionId())
                                .addParameter(model.getScheduleName())
                                .addParameter(model.getWorkflowName())
                                .addParameter(model.getWorkflowId())
                                .addParameter(model.getReason())
                                .addParameter(model.getStackTrace())
                                .addParameter(
                                        model.getState() != null ? model.getState().name() : null)
                                .addParameter(
                                        model.getScheduledTime() != null
                                                ? model.getScheduledTime()
                                                : 0L)
                                .addParameter(
                                        model.getExecutionTime() != null
                                                ? model.getExecutionTime()
                                                : 0L)
                                .addParameter(
                                        model.getStartWorkflowRequest() != null
                                                ? toJson(model.getStartWorkflowRequest())
                                                : null)
                                .executeUpdate());
    }

    @Override
    public SearchResult<String> searchScheduledExecutions(
            String query, String freeText, int start, int count, List<String> sort) {
        Monitors.recordDaoRequests(DAO_NAME, "searchScheduledExecutions", "n/a", "n/a");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        SchedulerSearchQuery parsed = SchedulerSearchQuery.parse(query);
        if (parsed.hasScheduleNames()) {
            String placeholders =
                    String.join(",", Collections.nCopies(parsed.getScheduleNames().size(), "?"));
            where.append(" AND schedule_name IN (").append(placeholders).append(")");
            params.addAll(parsed.getScheduleNames());
        }
        if (parsed.hasStates()) {
            String placeholders =
                    String.join(",", Collections.nCopies(parsed.getStates().size(), "?"));
            where.append(" AND state IN (").append(placeholders).append(")");
            params.addAll(parsed.getStates());
        }
        if (parsed.getScheduledTimeAfter() != null) {
            where.append(" AND scheduled_time > ?");
            params.add(parsed.getScheduledTimeAfter());
        }
        if (parsed.getScheduledTimeBefore() != null) {
            where.append(" AND scheduled_time < ?");
            params.add(parsed.getScheduledTimeBefore());
        }
        if (parsed.hasWorkflowName()) {
            where.append(" AND workflow_name ILIKE ?");
            params.add("%" + parsed.getWorkflowName() + "%");
        }
        if (parsed.hasExecutionId()) {
            where.append(" AND execution_id = ?");
            params.add(parsed.getExecutionId());
        }

        String countSql = "SELECT COUNT(*) FROM workflow_scheduled_executions" + where;
        long totalHits =
                queryWithTransaction(
                        countSql, q -> q.addParameters(params.toArray()).executeCount());

        String orderBy = buildOrderByClause(sort);
        String dataSql =
                "SELECT execution_id FROM workflow_scheduled_executions"
                        + where
                        + orderBy
                        + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(count);
        dataParams.add(start);

        List<String> ids =
                queryWithTransaction(
                        dataSql,
                        q -> q.addParameters(dataParams.toArray()).executeScalarList(String.class));

        return new SearchResult<>(totalHits, ids);
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
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionsByIds", "n/a", "n/a");
        if (executionIds == null || executionIds.isEmpty()) {
            return new HashMap<>();
        }
        String sql =
                "SELECT "
                        + SELECT_COLUMNS
                        + " FROM workflow_scheduled_executions WHERE execution_id = ANY(?)";
        List<WorkflowScheduleExecutionModel> list =
                queryWithTransaction(
                        sql,
                        q -> {
                            q.addParameter(new ArrayList<>(executionIds));
                            return q.executeAndFetch(this::mapRows);
                        });
        Map<String, WorkflowScheduleExecutionModel> result = new HashMap<>();
        for (WorkflowScheduleExecutionModel m : list) {
            result.put(m.getExecutionId(), m);
        }
        return result;
    }

    @Override
    public WorkflowScheduleExecutionModel getExecutionById(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionById", "n/a", "n/a");
        String sql =
                "SELECT "
                        + SELECT_COLUMNS
                        + " FROM workflow_scheduled_executions WHERE execution_id = ?";
        return queryWithTransaction(
                sql,
                q -> {
                    q.addParameter(executionId);
                    List<WorkflowScheduleExecutionModel> list = q.executeAndFetch(this::mapRows);
                    return list.isEmpty() ? null : list.get(0);
                });
    }

    @Override
    public void cleanupOldRecords(int archivalMaxRecords, int archivalMaxRecordThreshold) {
        Monitors.recordDaoRequests(DAO_NAME, "cleanupOldRecords", "n/a", "n/a");
        String schedSql =
                "SELECT schedule_name FROM workflow_scheduled_executions"
                        + " GROUP BY schedule_name HAVING COUNT(*) > ?";
        List<String> scheduleNames =
                queryWithTransaction(
                        schedSql,
                        q ->
                                q.addParameter(archivalMaxRecordThreshold)
                                        .executeScalarList(String.class));

        for (String scheduleName : scheduleNames) {
            String deleteSql =
                    """
                    DELETE FROM workflow_scheduled_executions
                    WHERE execution_id IN (
                        SELECT execution_id FROM workflow_scheduled_executions
                        WHERE schedule_name = ?
                        ORDER BY scheduled_time DESC
                        OFFSET ?
                    )
                    """;
            executeWithTransaction(
                    deleteSql,
                    q ->
                            q.addParameter(scheduleName)
                                    .addParameter(archivalMaxRecords)
                                    .executeDelete());
        }
    }

    private List<WorkflowScheduleExecutionModel> mapRows(ResultSet rs) throws SQLException {
        List<WorkflowScheduleExecutionModel> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    private WorkflowScheduleExecutionModel mapRow(ResultSet rs) throws SQLException {
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
        long scheduledTime = rs.getLong("scheduled_time");
        model.setScheduledTime(rs.wasNull() ? null : scheduledTime);
        long executionTime = rs.getLong("execution_time");
        model.setExecutionTime(rs.wasNull() ? null : executionTime);
        String swrJson = rs.getString("start_workflow_request");
        if (swrJson != null && !swrJson.isEmpty()) {
            try {
                model.setStartWorkflowRequest(
                        objectMapper.readValue(swrJson, StartWorkflowRequest.class));
            } catch (Exception e) {
                throw new NonTransientException("Failed to deserialize StartWorkflowRequest", e);
            }
        }
        return model;
    }
}
