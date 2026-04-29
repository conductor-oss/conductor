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
package org.conductoross.conductor.scheduler.cassandra.dao;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NonTransientException;

import com.datastax.driver.core.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * Cassandra implementation of {@link SchedulerDAO}.
 *
 * <p>Schedules are stored as JSON blobs in the {@code scheduler_schedules} table. Execution records
 * use the {@code scheduler_executions} table. Tables are created by {@link
 * CassandraSchedulerDAO#ensureTables()} on construction.
 */
public class CassandraSchedulerDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(CassandraSchedulerDAO.class);

    private static final String TABLE_SCHEDULES = "scheduler_schedules";
    private static final String TABLE_EXECUTIONS = "scheduler_executions";

    private final Session session;
    private final ObjectMapper objectMapper;
    private final CassandraProperties properties;

    private PreparedStatement upsertScheduleStmt;
    private PreparedStatement selectScheduleByNameStmt;
    private PreparedStatement selectAllSchedulesStmt;
    private PreparedStatement selectSchedulesByWorkflowStmt;
    private PreparedStatement deleteScheduleStmt;
    private PreparedStatement updateNextRunTimeStmt;
    private PreparedStatement selectNextRunTimeStmt;

    private PreparedStatement upsertExecutionStmt;
    private PreparedStatement selectExecutionByIdStmt;
    private PreparedStatement deleteExecutionStmt;
    private PreparedStatement selectExecutionsByScheduleStmt;
    private PreparedStatement selectPendingExecutionsStmt;

    public CassandraSchedulerDAO(
            Session session, ObjectMapper objectMapper, CassandraProperties properties) {
        this.session = session;
        this.objectMapper = objectMapper;
        this.properties = properties;
        ensureTables();
        prepareStatements();
    }

    private void ensureTables() {
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_SCHEDULES
                        + " ("
                        + "scheduler_name text PRIMARY KEY,"
                        + "workflow_name text,"
                        + "json_data text,"
                        + "next_run_time bigint"
                        + ")");
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_EXECUTIONS
                        + " ("
                        + "execution_id text PRIMARY KEY,"
                        + "schedule_name text,"
                        + "state text,"
                        + "json_data text"
                        + ")");
        // Secondary index for lookups by schedule_name and state
        session.execute(
                "CREATE INDEX IF NOT EXISTS idx_exec_schedule ON "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_EXECUTIONS
                        + " (schedule_name)");
        session.execute(
                "CREATE INDEX IF NOT EXISTS idx_exec_state ON "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_EXECUTIONS
                        + " (state)");
        session.execute(
                "CREATE INDEX IF NOT EXISTS idx_sched_workflow ON "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_SCHEDULES
                        + " (workflow_name)");
    }

    private void prepareStatements() {
        upsertScheduleStmt =
                session.prepare(
                        "INSERT INTO "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_SCHEDULES
                                + " (scheduler_name, workflow_name, json_data, next_run_time) VALUES (?, ?, ?, ?)");
        selectScheduleByNameStmt =
                session.prepare(
                        "SELECT json_data FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_SCHEDULES
                                + " WHERE scheduler_name = ?");
        selectAllSchedulesStmt =
                session.prepare(
                        "SELECT json_data FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_SCHEDULES);
        selectSchedulesByWorkflowStmt =
                session.prepare(
                        "SELECT json_data FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_SCHEDULES
                                + " WHERE workflow_name = ?");
        deleteScheduleStmt =
                session.prepare(
                        "DELETE FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_SCHEDULES
                                + " WHERE scheduler_name = ?");
        updateNextRunTimeStmt =
                session.prepare(
                        "UPDATE "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_SCHEDULES
                                + " SET next_run_time = ? WHERE scheduler_name = ?");
        selectNextRunTimeStmt =
                session.prepare(
                        "SELECT next_run_time FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_SCHEDULES
                                + " WHERE scheduler_name = ?");

        upsertExecutionStmt =
                session.prepare(
                        "INSERT INTO "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_EXECUTIONS
                                + " (execution_id, schedule_name, state, json_data) VALUES (?, ?, ?, ?)");
        selectExecutionByIdStmt =
                session.prepare(
                        "SELECT json_data FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_EXECUTIONS
                                + " WHERE execution_id = ?");
        deleteExecutionStmt =
                session.prepare(
                        "DELETE FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_EXECUTIONS
                                + " WHERE execution_id = ?");
        selectExecutionsByScheduleStmt =
                session.prepare(
                        "SELECT execution_id FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_EXECUTIONS
                                + " WHERE schedule_name = ?");
        selectPendingExecutionsStmt =
                session.prepare(
                        "SELECT execution_id FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_EXECUTIONS
                                + " WHERE state = ?");
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel schedule) {
        session.execute(
                upsertScheduleStmt.bind(
                        schedule.getName(),
                        schedule.getStartWorkflowRequest() != null
                                ? schedule.getStartWorkflowRequest().getName()
                                : null,
                        toJson(schedule),
                        schedule.getNextRunTime()));
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String orgId, String name) {
        Row row = session.execute(selectScheduleByNameStmt.bind(name)).one();
        return row == null
                ? null
                : fromJson(row.getString("json_data"), WorkflowScheduleModel.class);
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules(String orgId) {
        return session.execute(selectAllSchedulesStmt.bind()).all().stream()
                .map(row -> fromJson(row.getString("json_data"), WorkflowScheduleModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String orgId, String workflowName) {
        return session.execute(selectSchedulesByWorkflowStmt.bind(workflowName)).all().stream()
                .map(row -> fromJson(row.getString("json_data"), WorkflowScheduleModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(String orgId, Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        // Cassandra IN clause on partition key
        String cql =
                "SELECT json_data FROM "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_SCHEDULES
                        + " WHERE scheduler_name IN ?";
        ResultSet rs = session.execute(cql, new ArrayList<>(names));
        Map<String, WorkflowScheduleModel> result = new HashMap<>();
        for (Row row : rs) {
            WorkflowScheduleModel model =
                    fromJson(row.getString("json_data"), WorkflowScheduleModel.class);
            result.put(model.getName(), model);
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String orgId, String name) {
        // Cassandra can't DELETE by secondary index; select execution IDs first, then delete each
        List<Row> execRows = session.execute(selectExecutionsByScheduleStmt.bind(name)).all();
        for (Row row : execRows) {
            session.execute(deleteExecutionStmt.bind(row.getString("execution_id")));
        }
        session.execute(deleteScheduleStmt.bind(name));
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel execution) {
        session.execute(
                upsertExecutionStmt.bind(
                        execution.getExecutionId(),
                        execution.getScheduleName(),
                        execution.getState() != null ? execution.getState().name() : null,
                        toJson(execution)));
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String orgId, String executionId) {
        Row row = session.execute(selectExecutionByIdStmt.bind(executionId)).one();
        return row == null
                ? null
                : fromJson(row.getString("json_data"), WorkflowScheduleExecutionModel.class);
    }

    @Override
    public void removeExecutionRecord(String orgId, String executionId) {
        session.execute(deleteExecutionStmt.bind(executionId));
    }

    @Override
    public List<String> getPendingExecutionRecordIds(String orgId) {
        return session.execute(selectPendingExecutionsStmt.bind("POLLED")).all().stream()
                .map(row -> row.getString("execution_id"))
                .collect(Collectors.toList());
    }

    @Override
    public long getNextRunTimeInEpoch(String orgId, String scheduleName) {
        Row row = session.execute(selectNextRunTimeStmt.bind(scheduleName)).one();
        if (row == null || row.isNull("next_run_time")) {
            return -1L;
        }
        return row.getLong("next_run_time");
    }

    @Override
    public void setNextRunTimeInEpoch(String orgId, String scheduleName, long epochMillis) {
        session.execute(updateNextRunTimeStmt.bind(epochMillis, scheduleName));
    }

    @Override
    public SearchResult<WorkflowScheduleModel> searchSchedules(
            String orgId,
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        // Cassandra doesn't support complex queries; fetch all and filter in-memory.
        // Acceptable for schedule counts (typically < 1000 per deployment).
        List<WorkflowScheduleModel> all = getAllSchedules(orgId);
        List<WorkflowScheduleModel> filtered =
                all.stream()
                        .filter(
                                s -> {
                                    if (workflowName != null
                                            && !workflowName.isEmpty()
                                            && s.getStartWorkflowRequest() != null
                                            && !workflowName.equals(
                                                    s.getStartWorkflowRequest().getName())) {
                                        return false;
                                    }
                                    if (scheduleName != null
                                            && !scheduleName.isEmpty()
                                            && !s.getName().contains(scheduleName)) {
                                        return false;
                                    }
                                    if (paused != null && s.isPaused() != paused) {
                                        return false;
                                    }
                                    return true;
                                })
                        .sorted(Comparator.comparing(WorkflowScheduleModel::getName))
                        .collect(Collectors.toList());

        long totalHits = filtered.size();
        int end = Math.min(start + size, filtered.size());
        List<WorkflowScheduleModel> page =
                start < filtered.size() ? filtered.subList(start, end) : List.of();
        return new SearchResult<>(totalHits, page);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NonTransientException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new NonTransientException("Failed to deserialize JSON", e);
        }
    }
}
