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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.sqlite.dao.SqliteBaseDAO;
import com.netflix.conductor.sqlite.util.Query;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SqliteSchedulerDAO extends SqliteBaseDAO implements SchedulerDAO {

    public SqliteSchedulerDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void updateSchedule(WorkflowSchedule schedule) {
        String sql =
                "INSERT OR REPLACE INTO scheduler "
                        + "(scheduler_name, workflow_name, json_data, next_run_time) "
                        + "VALUES (?, ?, ?, ?)";
        executeWithTransaction(
                sql,
                q ->
                        q.addParameter(schedule.getName())
                                .addParameter(
                                        schedule.getStartWorkflowRequest() != null
                                                ? schedule.getStartWorkflowRequest().getName()
                                                : null)
                                .addJsonParameter(schedule)
                                .addParameter(schedule.getNextRunTime())
                                .executeUpdate());
    }

    @Override
    public WorkflowSchedule findScheduleByName(String name) {
        String sql = "SELECT json_data FROM scheduler WHERE scheduler_name = ?";
        return queryWithTransaction(
                sql, q -> q.addParameter(name).executeAndFetchFirst(WorkflowSchedule.class));
    }

    @Override
    public List<WorkflowSchedule> getAllSchedules() {
        return queryWithTransaction(
                "SELECT json_data FROM scheduler", q -> q.executeAndFetch(WorkflowSchedule.class));
    }

    @Override
    public List<WorkflowSchedule> findAllSchedules(String workflowName) {
        String sql = "SELECT json_data FROM scheduler WHERE workflow_name = ?";
        return queryWithTransaction(
                sql, q -> q.addParameter(workflowName).executeAndFetch(WorkflowSchedule.class));
    }

    @Override
    public Map<String, WorkflowSchedule> findAllByNames(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        String sql =
                "SELECT json_data FROM scheduler WHERE scheduler_name IN ("
                        + Query.generateInBindings(names.size())
                        + ")";
        List<WorkflowSchedule> schedules =
                queryWithTransaction(
                        sql,
                        q -> {
                            names.forEach(q::addParameter);
                            return q.executeAndFetch(WorkflowSchedule.class);
                        });
        Map<String, WorkflowSchedule> result = new HashMap<>();
        for (WorkflowSchedule s : schedules) {
            result.put(s.getName(), s);
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        withTransaction(
                tx -> {
                    execute(
                            tx,
                            "DELETE FROM scheduler_execution WHERE schedule_name = ?",
                            q -> q.addParameter(name).executeDelete());
                    execute(
                            tx,
                            "DELETE FROM scheduler WHERE scheduler_name = ?",
                            q -> q.addParameter(name).executeDelete());
                });
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecution execution) {
        String sql =
                "INSERT OR REPLACE INTO scheduler_execution "
                        + "(execution_id, schedule_name, state, execution_time, json_data) "
                        + "VALUES (?, ?, ?, ?, ?)";
        executeWithTransaction(
                sql,
                q ->
                        q.addParameter(execution.getExecutionId())
                                .addParameter(execution.getScheduleName())
                                .addParameter(
                                        execution.getState() != null
                                                ? execution.getState().name()
                                                : null)
                                .addParameter(execution.getExecutionTime())
                                .addJsonParameter(execution)
                                .executeUpdate());
    }

    @Override
    public WorkflowScheduleExecution readExecutionRecord(String executionId) {
        String sql = "SELECT json_data FROM scheduler_execution WHERE execution_id = ?";
        return queryWithTransaction(
                sql,
                q ->
                        q.addParameter(executionId)
                                .executeAndFetchFirst(WorkflowScheduleExecution.class));
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        executeWithTransaction(
                "DELETE FROM scheduler_execution WHERE execution_id = ?",
                q -> q.addParameter(executionId).executeDelete());
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        return queryWithTransaction(
                "SELECT execution_id FROM scheduler_execution WHERE state = 'POLLED'",
                q -> q.executeScalarList(String.class));
    }

    @Override
    public List<WorkflowScheduleExecution> getPendingExecutionRecords() {
        return queryWithTransaction(
                "SELECT json_data FROM scheduler_execution WHERE state = 'POLLED'",
                q -> q.executeAndFetch(WorkflowScheduleExecution.class));
    }

    @Override
    public List<WorkflowScheduleExecution> getExecutionRecords(String scheduleName, int limit) {
        String sql =
                "SELECT json_data FROM scheduler_execution "
                        + "WHERE schedule_name = ? "
                        + "ORDER BY execution_time DESC "
                        + "LIMIT ?";
        return queryWithTransaction(
                sql,
                q ->
                        q.addParameter(scheduleName)
                                .addParameter(limit)
                                .executeAndFetch(WorkflowScheduleExecution.class));
    }

    @Override
    public List<WorkflowScheduleExecution> getAllExecutionRecords(int limit) {
        String sql =
                "SELECT json_data FROM scheduler_execution "
                        + "ORDER BY execution_time DESC "
                        + "LIMIT ?";
        return queryWithTransaction(
                sql, q -> q.addParameter(limit).executeAndFetch(WorkflowScheduleExecution.class));
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        String sql = "SELECT next_run_time FROM scheduler WHERE scheduler_name = ?";
        return queryWithTransaction(
                sql,
                q ->
                        q.addParameter(scheduleName)
                                .executeAndFetch(
                                        rs -> {
                                            if (!rs.next()) return -1L;
                                            long val = rs.getLong(1);
                                            return rs.wasNull() ? -1L : val;
                                        }));
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        executeWithTransaction(
                "UPDATE scheduler SET next_run_time = ? WHERE scheduler_name = ?",
                q -> q.addParameter(epochMillis).addParameter(scheduleName).executeUpdate());
    }
}
