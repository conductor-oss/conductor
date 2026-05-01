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

import java.util.*;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.postgres.dao.PostgresBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * PostgreSQL implementation of {@link SchedulerDAO}.
 *
 * <p>Mirrors the Orkes Conductor schema: schedules and execution records are stored as JSON blobs
 * in {@code scheduler} and {@code scheduler_execution} tables respectively. The {@code
 * scheduler_execution} table additionally carries {@code schedule_name} and {@code state} columns
 * to support efficient queries (OSS has no queue infrastructure to offload this work).
 *
 * <p>Managed by Flyway ({@code db/migration_scheduler}).
 */
public class PostgresSchedulerDAO extends PostgresBaseDAO implements SchedulerDAO {

    private static final String DAO_NAME = "postgres";

    public PostgresSchedulerDAO(
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
                            """
                            INSERT INTO scheduler (scheduler_name, workflow_name, json_data, next_run_time)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (scheduler_name)
                            DO UPDATE SET workflow_name = EXCLUDED.workflow_name,
                                          json_data     = EXCLUDED.json_data,
                                          next_run_time = EXCLUDED.next_run_time
                            """,
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
                            "DELETE FROM scheduler_next_run WHERE key = ?",
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
        String sql = "SELECT json_data FROM scheduler WHERE scheduler_name = ANY(?)";
        List<WorkflowScheduleModel> schedules =
                queryWithTransaction(
                        sql,
                        q -> {
                            q.addParameter(new ArrayList<>(names));
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
                            "DELETE FROM scheduler_next_run WHERE key = ?",
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
                """
                INSERT INTO scheduler_execution (execution_id, schedule_name, state, json_data)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (execution_id)
                DO UPDATE SET state     = EXCLUDED.state,
                              json_data = EXCLUDED.json_data
                """;
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
                q -> q.executeAndFetch(String.class));
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        Monitors.recordDaoRequests(DAO_NAME, "getNextRunTimeInEpoch", "n/a", "n/a");
        String sql = "SELECT epoch_millis FROM scheduler_next_run WHERE key = ?";
        Long result =
                queryWithTransaction(
                        sql, q -> q.addParameter(scheduleName).executeAndFetchFirst(Long.class));
        return result == null ? -1L : result;
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        Monitors.recordDaoRequests(DAO_NAME, "setNextRunTimeInEpoch", "n/a", "n/a");
        // Upsert into scheduler_next_run so any key is accepted: schedule names (single-cron) and
        // JSON payload strings like {"name":"s","cron":"...","id":0} (multi-cron) both work.
        executeWithTransaction(
                """
                INSERT INTO scheduler_next_run (key, epoch_millis)
                VALUES (?, ?)
                ON CONFLICT (key) DO UPDATE SET epoch_millis = EXCLUDED.epoch_millis
                """,
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
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (workflowName != null && !workflowName.isEmpty()) {
            where.append(" AND workflow_name = ?");
            params.add(workflowName);
        }
        if (scheduleName != null && !scheduleName.isEmpty()) {
            where.append(" AND scheduler_name ILIKE ? ESCAPE '\\'");
            String escaped =
                    scheduleName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.add("%" + escaped + "%");
        }
        if (paused != null) {
            where.append(" AND (json_data::jsonb->>'paused')::boolean = ?");
            params.add(paused);
        }

        String countSql = "SELECT COUNT(*) FROM scheduler" + where;
        long totalHits =
                queryWithTransaction(
                        countSql, q -> q.addParameters(params.toArray()).executeCount());

        String dataSql =
                "SELECT json_data FROM scheduler"
                        + where
                        + " ORDER BY scheduler_name LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(start);

        List<WorkflowScheduleModel> results =
                queryWithTransaction(
                        dataSql,
                        q ->
                                q.addParameters(dataParams.toArray())
                                        .executeAndFetch(WorkflowScheduleModel.class));

        return new SearchResult<>(totalHits, results);
    }
}
