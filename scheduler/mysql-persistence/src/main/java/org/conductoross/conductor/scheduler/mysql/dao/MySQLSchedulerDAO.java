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
package org.conductoross.conductor.scheduler.mysql.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.mysql.dao.MySQLBaseDAO;
import com.netflix.conductor.mysql.util.Query;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * MySQL implementation of {@link SchedulerDAO}.
 *
 * <p>Extends {@link MySQLBaseDAO} for connection/transaction management and uses the {@link Query}
 * utility for parameterised statements. Functionally equivalent to the PostgreSQL implementation
 * but uses MySQL-compatible SQL syntax ({@code ON DUPLICATE KEY UPDATE} instead of {@code ON
 * CONFLICT}). Schedules and execution records are stored as JSON blobs in {@code scheduler} and
 * {@code scheduler_execution} tables respectively.
 *
 * <p>Managed by Flyway ({@code db/migration_scheduler_mysql}).
 */
public class MySQLSchedulerDAO extends MySQLBaseDAO implements SchedulerDAO {

    private static final String DAO_NAME = "mysql";

    public MySQLSchedulerDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel schedule) {
        Monitors.recordDaoRequests(DAO_NAME, "updateSchedule", "n/a", "n/a");
        withTransaction(
                tx -> {
                    execute(
                            tx,
                            "INSERT INTO scheduler (scheduler_name, workflow_name, json_data, next_run_time) "
                                    + "VALUES (?, ?, ?, ?) "
                                    + "ON DUPLICATE KEY UPDATE "
                                    + "    workflow_name = VALUES(workflow_name), "
                                    + "    json_data = VALUES(json_data), "
                                    + "    next_run_time = VALUES(next_run_time)",
                            q -> {
                                q.addParameter(schedule.getName())
                                        .addParameter(
                                                schedule.getStartWorkflowRequest() != null
                                                        ? schedule.getStartWorkflowRequest()
                                                                .getName()
                                                        : null)
                                        .addParameter(toJson(schedule))
                                        .addParameter(schedule.getNextRunTime())
                                        .executeUpdate();
                            });
                    // Reset the cached next-run-time so SchedulerService can set a fresh value
                    // via setNextRunTimeInEpoch after recomputing the schedule.
                    execute(
                            tx,
                            "DELETE FROM scheduler_next_run WHERE `key` = ?",
                            q -> q.addParameter(schedule.getName()).executeDelete());
                });
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String name) {
        Monitors.recordDaoRequests(DAO_NAME, "findScheduleByName", "n/a", "n/a");
        String sql = "SELECT json_data FROM scheduler WHERE scheduler_name = ?";
        return queryWithTransaction(
                sql, q -> q.addParameter(name).executeAndFetchFirst(WorkflowScheduleModel.class));
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules() {
        Monitors.recordDaoRequests(DAO_NAME, "getAllSchedules", "n/a", "n/a");
        return queryWithTransaction(
                "SELECT json_data FROM scheduler",
                q -> q.executeAndFetch(WorkflowScheduleModel.class));
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String workflowName) {
        Monitors.recordDaoRequests(DAO_NAME, "findAllSchedules", "n/a", "n/a");
        String sql = "SELECT json_data FROM scheduler WHERE workflow_name = ?";
        return queryWithTransaction(
                sql,
                q -> q.addParameter(workflowName).executeAndFetch(WorkflowScheduleModel.class));
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(Set<String> names) {
        Monitors.recordDaoRequests(DAO_NAME, "findAllByNames", "n/a", "n/a");
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        String placeholders = Query.generateInBindings(names.size());
        List<WorkflowScheduleModel> schedules =
                queryWithTransaction(
                        "SELECT json_data FROM scheduler WHERE scheduler_name IN ("
                                + placeholders
                                + ")",
                        q -> {
                            for (String name : names) {
                                q.addParameter(name);
                            }
                            return q.executeAndFetch(WorkflowScheduleModel.class);
                        });
        Map<String, WorkflowScheduleModel> result = new HashMap<>();
        for (WorkflowScheduleModel s : schedules) {
            result.put(s.getName(), s);
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        Monitors.recordDaoRequests(DAO_NAME, "deleteWorkflowSchedule", "n/a", "n/a");
        withTransaction(
                tx -> {
                    execute(
                            tx,
                            "DELETE FROM scheduler_execution WHERE schedule_name = ?",
                            q -> q.addParameter(name).executeDelete());
                    execute(
                            tx,
                            "DELETE FROM scheduler_next_run WHERE `key` = ?",
                            q -> q.addParameter(name).executeDelete());
                    execute(
                            tx,
                            "DELETE FROM scheduler WHERE scheduler_name = ?",
                            q -> q.addParameter(name).executeDelete());
                });
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel execution) {
        Monitors.recordDaoRequests(DAO_NAME, "saveExecutionRecord", "n/a", "n/a");
        String sql =
                "INSERT INTO scheduler_execution (execution_id, schedule_name, state, json_data) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "    state     = VALUES(state), "
                        + "    json_data = VALUES(json_data)";
        executeWithTransaction(
                sql,
                q ->
                        q.addParameter(execution.getExecutionId())
                                .addParameter(execution.getScheduleName())
                                .addParameter(
                                        execution.getState() != null
                                                ? execution.getState().name()
                                                : null)
                                .addParameter(toJson(execution))
                                .executeUpdate());
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "readExecutionRecord", "n/a", "n/a");
        String sql = "SELECT json_data FROM scheduler_execution WHERE execution_id = ?";
        return queryWithTransaction(
                sql,
                q ->
                        q.addParameter(executionId)
                                .executeAndFetchFirst(WorkflowScheduleExecutionModel.class));
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "removeExecutionRecord", "n/a", "n/a");
        executeWithTransaction(
                "DELETE FROM scheduler_execution WHERE execution_id = ?",
                q -> q.addParameter(executionId).executeDelete());
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        Monitors.recordDaoRequests(DAO_NAME, "getPendingExecutionRecordIds", "n/a", "n/a");
        return queryWithTransaction(
                "SELECT execution_id FROM scheduler_execution WHERE state = 'POLLED'",
                q -> q.executeScalarList(String.class));
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        Monitors.recordDaoRequests(DAO_NAME, "getNextRunTimeInEpoch", "n/a", "n/a");
        String sql = "SELECT epoch_millis FROM scheduler_next_run WHERE `key` = ?";
        Long result =
                queryWithTransaction(
                        sql, q -> q.addParameter(scheduleName).executeAndFetchFirst(Long.class));
        return result != null ? result : -1L;
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        Monitors.recordDaoRequests(DAO_NAME, "setNextRunTimeInEpoch", "n/a", "n/a");
        // Upsert into scheduler_next_run so any key is accepted: schedule names (single-cron) and
        // JSON payload strings like {"name":"s","cron":"...","id":0} (multi-cron) both work.
        executeWithTransaction(
                "INSERT INTO scheduler_next_run (`key`, epoch_millis) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE epoch_millis = VALUES(epoch_millis)",
                q -> q.addParameter(scheduleName).addParameter(epochMillis).executeUpdate());
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

        Monitors.recordDaoRequests(DAO_NAME, "searchSchedules", "n/a", "n/a");
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
            sql.append(" AND scheduler_name LIKE ? ESCAPE '\\\\'");
            countSql.append(" AND scheduler_name LIKE ? ESCAPE '\\\\'");
            String escaped =
                    scheduleName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.add("%" + escaped + "%");
            countParams.add("%" + escaped + "%");
        }
        if (paused != null) {
            sql.append(" AND JSON_EXTRACT(json_data, '$.paused') = ?");
            countSql.append(" AND JSON_EXTRACT(json_data, '$.paused') = ?");
            params.add(paused);
            countParams.add(paused);
        }

        long totalHits =
                queryWithTransaction(
                        countSql.toString(),
                        q -> {
                            q.addParameters(countParams.toArray());
                            return q.executeCount();
                        });

        sql.append(" ORDER BY scheduler_name ASC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(start);

        List<WorkflowScheduleModel> results =
                queryWithTransaction(
                        sql.toString(),
                        q -> {
                            q.addParameters(params.toArray());
                            return q.executeAndFetch(WorkflowScheduleModel.class);
                        });

        return new SearchResult<>(totalHits, results);
    }
}
