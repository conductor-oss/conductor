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
import com.netflix.conductor.cassandra.dao.CassandraBaseDAO;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.metrics.Monitors;

import com.datastax.driver.core.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * Cassandra implementation of {@link SchedulerDAO}.
 *
 * <p>Extends {@link CassandraBaseDAO} for session/properties management. Schedules are stored as
 * JSON blobs in the {@code scheduler_schedules} table. Execution records use the {@code
 * scheduler_executions} table. Tables are created by {@link #ensureTables()} on construction.
 */
public class CassandraSchedulerDAO extends CassandraBaseDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(CassandraSchedulerDAO.class);
    private static final String DAO_NAME = "cassandra";

    private static final String TABLE_SCHEDULES = "scheduler_schedules";
    private static final String TABLE_EXECUTIONS = "scheduler_executions";
    private static final String TABLE_EXEC_BY_SCHEDULE = "scheduler_exec_by_schedule";
    private static final String TABLE_EXEC_BY_STATE = "scheduler_exec_by_state";
    private static final String TABLE_SCHED_BY_WORKFLOW = "scheduler_sched_by_workflow";

    // objectMapper is private in CassandraBaseDAO; keep a local reference for serialization
    private final ObjectMapper objectMapper;

    private PreparedStatement upsertScheduleStmt;
    private PreparedStatement selectScheduleByNameStmt;
    private PreparedStatement selectAllSchedulesStmt;
    private PreparedStatement deleteScheduleStmt;
    private PreparedStatement updateNextRunTimeStmt;
    private PreparedStatement selectNextRunTimeStmt;

    private PreparedStatement upsertExecutionStmt;
    private PreparedStatement selectExecutionByIdStmt;
    private PreparedStatement deleteExecutionStmt;

    // Lookup table statements (replacing secondary indexes)
    private PreparedStatement insertExecByScheduleStmt;
    private PreparedStatement selectExecByScheduleStmt;
    private PreparedStatement deleteExecByScheduleStmt;
    private PreparedStatement insertExecByStateStmt;
    private PreparedStatement selectExecByStateStmt;
    private PreparedStatement deleteExecByStateStmt;
    private PreparedStatement insertSchedByWorkflowStmt;
    private PreparedStatement selectSchedByWorkflowStmt;
    private PreparedStatement deleteSchedByWorkflowStmt;

    public CassandraSchedulerDAO(
            Session session, ObjectMapper objectMapper, CassandraProperties properties) {
        super(session, objectMapper, properties);
        this.objectMapper = objectMapper;
        ensureTables();
        prepareStatements();
    }

    private void ensureTables() {
        String ks = properties.getKeyspace();
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
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
                        + ks
                        + "."
                        + TABLE_EXECUTIONS
                        + " ("
                        + "execution_id text PRIMARY KEY,"
                        + "schedule_name text,"
                        + "state text,"
                        + "json_data text"
                        + ")");
        // Denormalized lookup tables (replaces secondary indexes for efficient queries)
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
                        + "."
                        + TABLE_EXEC_BY_SCHEDULE
                        + " (schedule_name text, execution_id text,"
                        + " PRIMARY KEY (schedule_name, execution_id))");
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
                        + "."
                        + TABLE_EXEC_BY_STATE
                        + " (state text, execution_id text,"
                        + " PRIMARY KEY (state, execution_id))");
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
                        + "."
                        + TABLE_SCHED_BY_WORKFLOW
                        + " (workflow_name text, scheduler_name text,"
                        + " PRIMARY KEY (workflow_name, scheduler_name))");
    }

    private void prepareStatements() {
        String ks = properties.getKeyspace();
        upsertScheduleStmt =
                session.prepare(
                        "INSERT INTO "
                                + ks
                                + "."
                                + TABLE_SCHEDULES
                                + " (scheduler_name, workflow_name, json_data, next_run_time)"
                                + " VALUES (?, ?, ?, ?)");
        selectScheduleByNameStmt =
                session.prepare(
                        "SELECT json_data FROM "
                                + ks
                                + "."
                                + TABLE_SCHEDULES
                                + " WHERE scheduler_name = ?");
        selectAllSchedulesStmt =
                session.prepare("SELECT json_data FROM " + ks + "." + TABLE_SCHEDULES);
        deleteScheduleStmt =
                session.prepare(
                        "DELETE FROM " + ks + "." + TABLE_SCHEDULES + " WHERE scheduler_name = ?");
        updateNextRunTimeStmt =
                session.prepare(
                        "UPDATE "
                                + ks
                                + "."
                                + TABLE_SCHEDULES
                                + " SET next_run_time = ? WHERE scheduler_name = ?");
        selectNextRunTimeStmt =
                session.prepare(
                        "SELECT next_run_time FROM "
                                + ks
                                + "."
                                + TABLE_SCHEDULES
                                + " WHERE scheduler_name = ?");

        upsertExecutionStmt =
                session.prepare(
                        "INSERT INTO "
                                + ks
                                + "."
                                + TABLE_EXECUTIONS
                                + " (execution_id, schedule_name, state, json_data)"
                                + " VALUES (?, ?, ?, ?)");
        selectExecutionByIdStmt =
                session.prepare(
                        "SELECT json_data, state FROM "
                                + ks
                                + "."
                                + TABLE_EXECUTIONS
                                + " WHERE execution_id = ?");
        deleteExecutionStmt =
                session.prepare(
                        "DELETE FROM " + ks + "." + TABLE_EXECUTIONS + " WHERE execution_id = ?");

        // Lookup table statements
        insertExecByScheduleStmt =
                session.prepare(
                        "INSERT INTO "
                                + ks
                                + "."
                                + TABLE_EXEC_BY_SCHEDULE
                                + " (schedule_name, execution_id) VALUES (?, ?)");
        selectExecByScheduleStmt =
                session.prepare(
                        "SELECT execution_id FROM "
                                + ks
                                + "."
                                + TABLE_EXEC_BY_SCHEDULE
                                + " WHERE schedule_name = ?");
        deleteExecByScheduleStmt =
                session.prepare(
                        "DELETE FROM "
                                + ks
                                + "."
                                + TABLE_EXEC_BY_SCHEDULE
                                + " WHERE schedule_name = ? AND execution_id = ?");
        insertExecByStateStmt =
                session.prepare(
                        "INSERT INTO "
                                + ks
                                + "."
                                + TABLE_EXEC_BY_STATE
                                + " (state, execution_id) VALUES (?, ?)");
        selectExecByStateStmt =
                session.prepare(
                        "SELECT execution_id FROM "
                                + ks
                                + "."
                                + TABLE_EXEC_BY_STATE
                                + " WHERE state = ?");
        deleteExecByStateStmt =
                session.prepare(
                        "DELETE FROM "
                                + ks
                                + "."
                                + TABLE_EXEC_BY_STATE
                                + " WHERE state = ? AND execution_id = ?");
        insertSchedByWorkflowStmt =
                session.prepare(
                        "INSERT INTO "
                                + ks
                                + "."
                                + TABLE_SCHED_BY_WORKFLOW
                                + " (workflow_name, scheduler_name) VALUES (?, ?)");
        selectSchedByWorkflowStmt =
                session.prepare(
                        "SELECT scheduler_name FROM "
                                + ks
                                + "."
                                + TABLE_SCHED_BY_WORKFLOW
                                + " WHERE workflow_name = ?");
        deleteSchedByWorkflowStmt =
                session.prepare(
                        "DELETE FROM "
                                + ks
                                + "."
                                + TABLE_SCHED_BY_WORKFLOW
                                + " WHERE workflow_name = ? AND scheduler_name = ?");
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel schedule) {
        Monitors.recordDaoRequests(DAO_NAME, "updateSchedule", "n/a", "n/a");

        // Remove old workflow_name lookup if workflow changed
        Row existing = session.execute(selectScheduleByNameStmt.bind(schedule.getName())).one();
        if (existing != null) {
            WorkflowScheduleModel old =
                    fromJson(existing.getString("json_data"), WorkflowScheduleModel.class);
            if (old.getStartWorkflowRequest() != null
                    && old.getStartWorkflowRequest().getName() != null) {
                String oldWf = old.getStartWorkflowRequest().getName();
                String newWf =
                        schedule.getStartWorkflowRequest() != null
                                ? schedule.getStartWorkflowRequest().getName()
                                : null;
                if (!oldWf.equals(newWf)) {
                    session.execute(deleteSchedByWorkflowStmt.bind(oldWf, schedule.getName()));
                }
            }
        }

        session.execute(
                upsertScheduleStmt.bind(
                        schedule.getName(),
                        schedule.getStartWorkflowRequest() != null
                                ? schedule.getStartWorkflowRequest().getName()
                                : null,
                        toJson(schedule),
                        schedule.getNextRunTime()));

        // Maintain workflow lookup table
        if (schedule.getStartWorkflowRequest() != null
                && schedule.getStartWorkflowRequest().getName() != null) {
            session.execute(
                    insertSchedByWorkflowStmt.bind(
                            schedule.getStartWorkflowRequest().getName(), schedule.getName()));
        }
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String name) {
        Monitors.recordDaoRequests(DAO_NAME, "findScheduleByName", "n/a", "n/a");
        Row row = session.execute(selectScheduleByNameStmt.bind(name)).one();
        return row == null
                ? null
                : fromJson(row.getString("json_data"), WorkflowScheduleModel.class);
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules() {
        Monitors.recordDaoRequests(DAO_NAME, "getAllSchedules", "n/a", "n/a");
        return session.execute(selectAllSchedulesStmt.bind()).all().stream()
                .map(row -> fromJson(row.getString("json_data"), WorkflowScheduleModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String workflowName) {
        Monitors.recordDaoRequests(DAO_NAME, "findAllSchedules", "n/a", "n/a");
        List<Row> lookupRows = session.execute(selectSchedByWorkflowStmt.bind(workflowName)).all();
        if (lookupRows.isEmpty()) {
            return new ArrayList<>();
        }
        // Batch-fetch schedules using IN clause instead of N+1 individual queries
        List<String> schedulerNames =
                lookupRows.stream()
                        .map(row -> row.getString("scheduler_name"))
                        .collect(Collectors.toList());
        String cql =
                "SELECT json_data FROM "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_SCHEDULES
                        + " WHERE scheduler_name IN ?";
        ResultSet rs = session.execute(cql, schedulerNames);
        return rs.all().stream()
                .map(row -> fromJson(row.getString("json_data"), WorkflowScheduleModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(Set<String> names) {
        Monitors.recordDaoRequests(DAO_NAME, "findAllByNames", "n/a", "n/a");
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
    public void deleteWorkflowSchedule(String name) {
        Monitors.recordDaoRequests(DAO_NAME, "deleteWorkflowSchedule", "n/a", "n/a");

        // Remove workflow lookup entry
        Row schedRow = session.execute(selectScheduleByNameStmt.bind(name)).one();
        if (schedRow != null) {
            WorkflowScheduleModel sched =
                    fromJson(schedRow.getString("json_data"), WorkflowScheduleModel.class);
            if (sched.getStartWorkflowRequest() != null
                    && sched.getStartWorkflowRequest().getName() != null) {
                session.execute(
                        deleteSchedByWorkflowStmt.bind(
                                sched.getStartWorkflowRequest().getName(), name));
            }
        }

        // Delete all executions for this schedule using the lookup table
        List<Row> execRows = session.execute(selectExecByScheduleStmt.bind(name)).all();
        if (!execRows.isEmpty()) {
            // Batch-fetch execution states using IN clause (avoids N+1 reads)
            List<String> execIds =
                    execRows.stream()
                            .map(row -> row.getString("execution_id"))
                            .collect(Collectors.toList());
            String cql =
                    "SELECT execution_id, state FROM "
                            + properties.getKeyspace()
                            + "."
                            + TABLE_EXECUTIONS
                            + " WHERE execution_id IN ?";
            Map<String, String> statesByExecId = new HashMap<>();
            for (Row r : session.execute(cql, execIds)) {
                statesByExecId.put(
                        r.getString("execution_id"),
                        r.isNull("state") ? null : r.getString("state"));
            }

            // Batch all deletes (state lookup + execution + schedule lookup)
            BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
            for (String execId : execIds) {
                String state = statesByExecId.get(execId);
                if (state != null) {
                    batch.add(deleteExecByStateStmt.bind(state, execId));
                }
                batch.add(deleteExecutionStmt.bind(execId));
                batch.add(deleteExecByScheduleStmt.bind(name, execId));
            }
            session.execute(batch);
        }
        session.execute(deleteScheduleStmt.bind(name));
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel execution) {
        Monitors.recordDaoRequests(DAO_NAME, "saveExecutionRecord", "n/a", "n/a");
        String execId = execution.getExecutionId();
        String stateStr = execution.getState() != null ? execution.getState().name() : null;

        // If updating an existing record, remove old state lookup entry
        Row existing = session.execute(selectExecutionByIdStmt.bind(execId)).one();
        if (existing != null) {
            String oldState = existing.isNull("state") ? null : existing.getString("state");
            if (oldState != null && !oldState.equals(stateStr)) {
                session.execute(deleteExecByStateStmt.bind(oldState, execId));
            }
        }

        session.execute(
                upsertExecutionStmt.bind(
                        execId, execution.getScheduleName(), stateStr, toJson(execution)));

        // Maintain lookup tables
        session.execute(insertExecByScheduleStmt.bind(execution.getScheduleName(), execId));
        if (stateStr != null) {
            session.execute(insertExecByStateStmt.bind(stateStr, execId));
        }
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "readExecutionRecord", "n/a", "n/a");
        Row row = session.execute(selectExecutionByIdStmt.bind(executionId)).one();
        return row == null
                ? null
                : fromJson(row.getString("json_data"), WorkflowScheduleExecutionModel.class);
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "removeExecutionRecord", "n/a", "n/a");
        // Read execution to get schedule_name and state for lookup table cleanup
        Row row = session.execute(selectExecutionByIdStmt.bind(executionId)).one();
        if (row != null) {
            WorkflowScheduleExecutionModel exec =
                    fromJson(row.getString("json_data"), WorkflowScheduleExecutionModel.class);
            session.execute(deleteExecByScheduleStmt.bind(exec.getScheduleName(), executionId));
            if (exec.getState() != null) {
                session.execute(deleteExecByStateStmt.bind(exec.getState().name(), executionId));
            }
        }
        session.execute(deleteExecutionStmt.bind(executionId));
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        Monitors.recordDaoRequests(DAO_NAME, "getPendingExecutionRecordIds", "n/a", "n/a");
        return session.execute(selectExecByStateStmt.bind("POLLED")).all().stream()
                .map(row -> row.getString("execution_id"))
                .collect(Collectors.toList());
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        Monitors.recordDaoRequests(DAO_NAME, "getNextRunTimeInEpoch", "n/a", "n/a");
        Row row = session.execute(selectNextRunTimeStmt.bind(scheduleName)).one();
        if (row == null || row.isNull("next_run_time")) {
            return -1L;
        }
        return row.getLong("next_run_time");
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        Monitors.recordDaoRequests(DAO_NAME, "setNextRunTimeInEpoch", "n/a", "n/a");
        session.execute(updateNextRunTimeStmt.bind(epochMillis, scheduleName));
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
        // Cassandra doesn't support complex queries; fetch all and filter in-memory.
        // Acceptable for schedule counts (typically < 1000 per deployment).
        List<WorkflowScheduleModel> all = getAllSchedules();
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
