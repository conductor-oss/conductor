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
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.metrics.Monitors;

import com.datastax.driver.core.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.archive.SchedulerSearchQuery;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;

/**
 * Cassandra implementation of {@link SchedulerArchivalDAO}.
 *
 * <p>Extends {@link CassandraBaseDAO} for session/properties management. Archival execution records
 * are stored in {@code scheduler_archival_executions} with schedule_name as the partition key and
 * scheduled_time as a clustering column for efficient per-schedule queries sorted by time. A
 * secondary lookup table {@code scheduler_archival_by_id} supports fetches by execution_id.
 */
public class CassandraSchedulerArchivalDAO extends CassandraBaseDAO
        implements SchedulerArchivalDAO {

    private static final Logger log = LoggerFactory.getLogger(CassandraSchedulerArchivalDAO.class);
    private static final String DAO_NAME = "cassandra";

    private static final String TABLE_ARCHIVAL = "scheduler_archival_executions";
    private static final String TABLE_ARCHIVAL_BY_ID = "scheduler_archival_by_id";

    // Max rows per UNLOGGED batch during cleanup. Each row produces 2 mutations
    // (archival + by_id), so 100 rows = 200 mutations — well within Cassandra's
    // default batch_size_warn_threshold_in_kb (128 KB).
    private static final int CLEANUP_BATCH_CHUNK_SIZE = 100;

    // Safety cap on full-table scan in the free-text search fallback path
    private static final int FREE_TEXT_SCAN_LIMIT = 10_000;

    // objectMapper is private in CassandraBaseDAO; keep a local reference for serialization
    private final ObjectMapper objectMapper;

    private PreparedStatement upsertArchivalStmt;
    private PreparedStatement upsertByIdStmt;
    private PreparedStatement selectByIdStmt;
    private PreparedStatement selectByScheduleStmt;
    private PreparedStatement deleteByScheduleAndTimeStmt;
    private PreparedStatement deleteByIdStmt;
    private PreparedStatement countByScheduleStmt;

    public CassandraSchedulerArchivalDAO(
            Session session, ObjectMapper objectMapper, CassandraProperties properties) {
        super(session, objectMapper, properties);
        this.objectMapper = objectMapper;
        ensureTables();
        prepareStatements();
    }

    private void ensureTables() {
        // Primary table: partitioned by schedule_name, clustered by scheduled_time DESC
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_ARCHIVAL
                        + " ("
                        + "schedule_name text,"
                        + "scheduled_time bigint,"
                        + "execution_id text,"
                        + "workflow_name text,"
                        + "workflow_id text,"
                        + "reason text,"
                        + "stack_trace text,"
                        + "state text,"
                        + "execution_time bigint,"
                        + "start_workflow_request text,"
                        + "PRIMARY KEY ((schedule_name), scheduled_time, execution_id)"
                        + ") WITH CLUSTERING ORDER BY (scheduled_time DESC, execution_id ASC)");

        // Lookup table for getExecutionById
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_ARCHIVAL_BY_ID
                        + " ("
                        + "execution_id text PRIMARY KEY,"
                        + "schedule_name text,"
                        + "workflow_name text,"
                        + "workflow_id text,"
                        + "reason text,"
                        + "stack_trace text,"
                        + "state text,"
                        + "scheduled_time bigint,"
                        + "execution_time bigint,"
                        + "start_workflow_request text"
                        + ")");
    }

    private void prepareStatements() {
        upsertArchivalStmt =
                session.prepare(
                        "INSERT INTO "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_ARCHIVAL
                                + " (schedule_name, scheduled_time, execution_id, workflow_name, workflow_id,"
                                + " reason, stack_trace, state, execution_time, start_workflow_request)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        upsertByIdStmt =
                session.prepare(
                        "INSERT INTO "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_ARCHIVAL_BY_ID
                                + " (execution_id, schedule_name, workflow_name, workflow_id,"
                                + " reason, stack_trace, state, scheduled_time, execution_time, start_workflow_request)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        selectByIdStmt =
                session.prepare(
                        "SELECT * FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_ARCHIVAL_BY_ID
                                + " WHERE execution_id = ?");
        selectByScheduleStmt =
                session.prepare(
                        "SELECT * FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_ARCHIVAL
                                + " WHERE schedule_name = ?");
        deleteByScheduleAndTimeStmt =
                session.prepare(
                        "DELETE FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_ARCHIVAL
                                + " WHERE schedule_name = ? AND scheduled_time = ? AND execution_id = ?");
        deleteByIdStmt =
                session.prepare(
                        "DELETE FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_ARCHIVAL_BY_ID
                                + " WHERE execution_id = ?");
        countByScheduleStmt =
                session.prepare(
                        "SELECT COUNT(*) FROM "
                                + properties.getKeyspace()
                                + "."
                                + TABLE_ARCHIVAL
                                + " WHERE schedule_name = ?");
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel model) {
        Monitors.recordDaoRequests(DAO_NAME, "saveArchivalRecord", "n/a", "n/a");
        String swrJson = serializeStartWorkflowRequest(model.getStartWorkflowRequest());
        String stateStr = model.getState() != null ? model.getState().name() : null;
        long scheduledTime = model.getScheduledTime() != null ? model.getScheduledTime() : 0L;
        long executionTime = model.getExecutionTime() != null ? model.getExecutionTime() : 0L;

        // Write to both tables
        BatchStatement batch = new BatchStatement(BatchStatement.Type.LOGGED);
        batch.add(
                upsertArchivalStmt.bind(
                        model.getScheduleName(),
                        scheduledTime,
                        model.getExecutionId(),
                        model.getWorkflowName(),
                        model.getWorkflowId(),
                        model.getReason(),
                        model.getStackTrace(),
                        stateStr,
                        executionTime,
                        swrJson));
        batch.add(
                upsertByIdStmt.bind(
                        model.getExecutionId(),
                        model.getScheduleName(),
                        model.getWorkflowName(),
                        model.getWorkflowId(),
                        model.getReason(),
                        model.getStackTrace(),
                        stateStr,
                        scheduledTime,
                        executionTime,
                        swrJson));
        session.execute(batch);
    }

    @Override
    public SearchResult<String> searchScheduledExecutions(
            String query, String freeText, int start, int count, List<String> sort) {
        Monitors.recordDaoRequests(DAO_NAME, "searchScheduledExecutions", "n/a", "n/a");

        SchedulerSearchQuery parsed = SchedulerSearchQuery.parse(query);

        // Determine which schedule names to query
        List<String> targetScheduleNames;
        if (parsed.hasScheduleNames()) {
            targetScheduleNames = parsed.getScheduleNames();
        } else {
            // Get all distinct schedule names from the partitioned table
            List<Row> distinctRows =
                    session.execute(
                                    "SELECT DISTINCT schedule_name FROM "
                                            + properties.getKeyspace()
                                            + "."
                                            + TABLE_ARCHIVAL)
                            .all();
            targetScheduleNames =
                    distinctRows.stream()
                            .map(r -> r.getString("schedule_name"))
                            .collect(Collectors.toList());
        }

        // Collect rows from each schedule partition
        List<WorkflowScheduleExecutionModel> allModels = new ArrayList<>();
        for (String scheduleName : targetScheduleNames) {
            List<Row> rows = session.execute(selectByScheduleStmt.bind(scheduleName)).all();
            for (Row row : rows) {
                allModels.add(rowToModel(row));
            }
        }

        // Filter by state
        if (parsed.hasStates()) {
            Set<String> stateSet = new HashSet<>(parsed.getStates());
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getState() != null
                                                    && stateSet.contains(m.getState().name()))
                            .collect(Collectors.toList());
        }

        // Filter by time range
        if (parsed.getScheduledTimeAfter() != null) {
            long after = parsed.getScheduledTimeAfter();
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getScheduledTime() != null
                                                    && m.getScheduledTime() > after)
                            .collect(Collectors.toList());
        }
        if (parsed.getScheduledTimeBefore() != null) {
            long before = parsed.getScheduledTimeBefore();
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getScheduledTime() != null
                                                    && m.getScheduledTime() < before)
                            .collect(Collectors.toList());
        }

        // Filter by workflow name (substring match)
        if (parsed.hasWorkflowName()) {
            String term = parsed.getWorkflowName().toLowerCase();
            allModels =
                    allModels.stream()
                            .filter(
                                    m ->
                                            m.getWorkflowName() != null
                                                    && m.getWorkflowName()
                                                            .toLowerCase()
                                                            .contains(term))
                            .collect(Collectors.toList());
        }

        // Filter by execution ID (exact match)
        if (parsed.hasExecutionId()) {
            String execId = parsed.getExecutionId();
            allModels =
                    allModels.stream()
                            .filter(m -> execId.equals(m.getExecutionId()))
                            .collect(Collectors.toList());
        }

        // Sort by scheduled_time DESC
        allModels.sort(
                (a, b) ->
                        Long.compare(
                                b.getScheduledTime() != null ? b.getScheduledTime() : 0,
                                a.getScheduledTime() != null ? a.getScheduledTime() : 0));

        long totalHits = allModels.size();
        int end = Math.min(start + count, allModels.size());
        List<String> ids =
                allModels.subList(start < allModels.size() ? start : allModels.size(), end).stream()
                        .map(WorkflowScheduleExecutionModel::getExecutionId)
                        .collect(Collectors.toList());
        return new SearchResult<>(totalHits, ids);
    }

    @Override
    public Map<String, WorkflowScheduleExecutionModel> getExecutionsByIds(
            Set<String> executionIds) {
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionsByIds", "n/a", "n/a");
        if (executionIds == null || executionIds.isEmpty()) {
            return new HashMap<>();
        }
        String cql =
                "SELECT * FROM "
                        + properties.getKeyspace()
                        + "."
                        + TABLE_ARCHIVAL_BY_ID
                        + " WHERE execution_id IN ?";
        ResultSet rs = session.execute(cql, new ArrayList<>(executionIds));
        Map<String, WorkflowScheduleExecutionModel> result = new HashMap<>();
        for (Row row : rs) {
            WorkflowScheduleExecutionModel model = rowToModel(row);
            result.put(model.getExecutionId(), model);
        }
        return result;
    }

    @Override
    public WorkflowScheduleExecutionModel getExecutionById(String executionId) {
        Monitors.recordDaoRequests(DAO_NAME, "getExecutionById", "n/a", "n/a");
        Row row = session.execute(selectByIdStmt.bind(executionId)).one();
        return row == null ? null : rowToModel(row);
    }

    @Override
    public void cleanupOldRecords(int archivalMaxRecords, int archivalMaxRecordThreshold) {
        Monitors.recordDaoRequests(DAO_NAME, "cleanupOldRecords", "n/a", "n/a");
        // Get all distinct schedule names from the by-id table
        List<Row> allRows =
                session.execute(
                                "SELECT DISTINCT schedule_name FROM "
                                        + properties.getKeyspace()
                                        + "."
                                        + TABLE_ARCHIVAL)
                        .all();
        Set<String> scheduleNames =
                allRows.stream().map(r -> r.getString("schedule_name")).collect(Collectors.toSet());

        for (String scheduleName : scheduleNames) {
            long count = session.execute(countByScheduleStmt.bind(scheduleName)).one().getLong(0);
            if (count <= archivalMaxRecordThreshold) {
                continue;
            }

            // Fetch all rows for this schedule (ordered by scheduled_time DESC from clustering)
            List<Row> rows = session.execute(selectByScheduleStmt.bind(scheduleName)).all();
            if (rows.size() <= archivalMaxRecords) {
                continue;
            }

            // Delete rows beyond the keep limit, chunked to avoid exceeding batch size thresholds
            List<Row> toDelete = rows.subList(archivalMaxRecords, rows.size());
            int chunkSize = CLEANUP_BATCH_CHUNK_SIZE;
            for (int i = 0; i < toDelete.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, toDelete.size());
                BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
                for (Row row : toDelete.subList(i, end)) {
                    batch.add(
                            deleteByScheduleAndTimeStmt.bind(
                                    scheduleName,
                                    row.getLong("scheduled_time"),
                                    row.getString("execution_id")));
                    batch.add(deleteByIdStmt.bind(row.getString("execution_id")));
                }
                session.execute(batch);
            }
            log.info(
                    "Cleaned up {} old archival records for schedule: {}",
                    toDelete.size(),
                    scheduleName);
        }
    }

    private WorkflowScheduleExecutionModel rowToModel(Row row) {
        WorkflowScheduleExecutionModel model = new WorkflowScheduleExecutionModel();
        model.setExecutionId(row.getString("execution_id"));
        model.setScheduleName(row.getString("schedule_name"));
        model.setWorkflowName(row.isNull("workflow_name") ? null : row.getString("workflow_name"));
        model.setWorkflowId(row.isNull("workflow_id") ? null : row.getString("workflow_id"));
        model.setReason(row.isNull("reason") ? null : row.getString("reason"));
        model.setStackTrace(row.isNull("stack_trace") ? null : row.getString("stack_trace"));
        String stateStr = row.isNull("state") ? null : row.getString("state");
        if (stateStr != null) {
            model.setState(WorkflowScheduleExecutionModel.State.valueOf(stateStr));
        }
        model.setScheduledTime(row.isNull("scheduled_time") ? null : row.getLong("scheduled_time"));
        model.setExecutionTime(row.isNull("execution_time") ? null : row.getLong("execution_time"));
        model.setStartWorkflowRequest(
                deserializeStartWorkflowRequest(
                        row.isNull("start_workflow_request")
                                ? null
                                : row.getString("start_workflow_request")));
        return model;
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
