/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.sqlite.config.SqliteProperties;
import com.netflix.conductor.sqlite.util.SqliteIndexQueryBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SqliteIndexDAO extends SqliteBaseDAO implements IndexDAO {

    private final SqliteProperties properties;
    private final ExecutorService executorService;

    private static final int CORE_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 1L;

    private boolean onlyIndexOnStatusChange;

    public SqliteIndexDAO(
            RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            SqliteProperties properties) {
        super(retryTemplate, objectMapper, dataSource);
        this.properties = properties;
        this.onlyIndexOnStatusChange = properties.getOnlyIndexOnStatusChange();

        int maximumPoolSize = properties.getAsyncMaxPoolSize();
        int workerQueueSize = properties.getAsyncWorkerQueueSize();

        // Set up a workerpool for performing async operations.
        this.executorService =
                new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        maximumPoolSize,
                        KEEP_ALIVE_TIME,
                        TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>(workerQueueSize),
                        (runnable, executor) -> {
                            logger.warn(
                                    "Request {} to async dao discarded in executor {}",
                                    runnable,
                                    executor);
                            Monitors.recordDiscardedIndexingCount("indexQueue");
                        });
    }

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {
        String INSERT_WORKFLOW_INDEX_SQL =
                "INSERT INTO workflow_index (workflow_id, correlation_id, workflow_type, start_time, update_time, status, json_data) "
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (workflow_id) "
                        + " DO UPDATE SET correlation_id = excluded.correlation_id, workflow_type = excluded.workflow_type, "
                        + " start_time = excluded.start_time, status = excluded.status, json_data = excluded.json_data, "
                        + " update_time = excluded.update_time "
                        + " WHERE excluded.update_time >= workflow_index.update_time";

        if (onlyIndexOnStatusChange) {
            INSERT_WORKFLOW_INDEX_SQL += " AND workflow_index.status != excluded.status";
        }

        TemporalAccessor updateTa = DateTimeFormatter.ISO_INSTANT.parse(workflow.getUpdateTime());
        Timestamp updateTime = Timestamp.from(Instant.from(updateTa));

        TemporalAccessor ta = DateTimeFormatter.ISO_INSTANT.parse(workflow.getStartTime());
        Timestamp startTime = Timestamp.from(Instant.from(ta));

        int rowsUpdated =
                queryWithTransaction(
                        INSERT_WORKFLOW_INDEX_SQL,
                        q ->
                                q.addParameter(workflow.getWorkflowId())
                                        .addParameter(workflow.getCorrelationId())
                                        .addParameter(workflow.getWorkflowType())
                                        .addParameter(startTime.toString())
                                        .addParameter(updateTime.toString())
                                        .addParameter(workflow.getStatus().toString())
                                        .addJsonParameter(workflow)
                                        .executeUpdate());
        logger.debug("Sqlite index workflow rows updated: {}", rowsUpdated);
    }

    @Override
    public SearchResult<WorkflowSummary> searchWorkflowSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        SqliteIndexQueryBuilder queryBuilder =
                new SqliteIndexQueryBuilder(
                        "workflow_index", query, freeText, start, count, sort, properties);

        List<WorkflowSummary> results =
                queryWithTransaction(
                        queryBuilder.getQuery(),
                        q -> {
                            queryBuilder.addParameters(q);
                            queryBuilder.addPagingParameters(q);
                            return q.executeAndFetch(WorkflowSummary.class);
                        });

        List<String> totalHitResults =
                queryWithTransaction(
                        queryBuilder.getCountQuery(),
                        q -> {
                            queryBuilder.addParameters(q);
                            return q.executeAndFetch(String.class);
                        });

        int totalHits = Integer.valueOf(totalHitResults.get(0));
        return new SearchResult<>(totalHits, results);
    }

    @Override
    public void indexTask(TaskSummary task) {
        String INSERT_TASK_INDEX_SQL =
                "INSERT INTO task_index (task_id, task_type, task_def_name, status, start_time, update_time, workflow_type, json_data)"
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (task_id) "
                        + "DO UPDATE SET task_type = excluded.task_type, task_def_name = excluded.task_def_name, "
                        + "status = excluded.status, update_time = excluded.update_time, json_data = excluded.json_data "
                        + "WHERE excluded.update_time >= task_index.update_time";

        if (onlyIndexOnStatusChange) {
            INSERT_TASK_INDEX_SQL += " AND task_index.status != excluded.status";
        }

        TemporalAccessor updateTa = DateTimeFormatter.ISO_INSTANT.parse(task.getUpdateTime());
        Timestamp updateTime = Timestamp.from(Instant.from(updateTa));

        TemporalAccessor startTa = DateTimeFormatter.ISO_INSTANT.parse(task.getStartTime());
        Timestamp startTime = Timestamp.from(Instant.from(startTa));

        int rowsUpdated =
                queryWithTransaction(
                        INSERT_TASK_INDEX_SQL,
                        q ->
                                q.addParameter(task.getTaskId())
                                        .addParameter(task.getTaskType())
                                        .addParameter(task.getTaskDefName())
                                        .addParameter(task.getStatus().toString())
                                        .addParameter(startTime.toString())
                                        .addParameter(updateTime.toString())
                                        .addParameter(task.getWorkflowType())
                                        .addJsonParameter(task)
                                        .executeUpdate());
        logger.debug("Sqlite index task rows updated: {}", rowsUpdated);
    }

    @Override
    public SearchResult<TaskSummary> searchTaskSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        SqliteIndexQueryBuilder queryBuilder =
                new SqliteIndexQueryBuilder(
                        "task_index", query, freeText, start, count, sort, properties);

        List<TaskSummary> results =
                queryWithTransaction(
                        queryBuilder.getQuery(),
                        q -> {
                            queryBuilder.addParameters(q);
                            queryBuilder.addPagingParameters(q);
                            return q.executeAndFetch(TaskSummary.class);
                        });

        List<String> totalHitResults =
                queryWithTransaction(
                        queryBuilder.getCountQuery(),
                        q -> {
                            queryBuilder.addParameters(q);
                            return q.executeAndFetch(String.class);
                        });

        int totalHits = Integer.valueOf(totalHitResults.get(0));
        return new SearchResult<>(totalHits, results);
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> logs) {
        String INSERT_LOG =
                "INSERT INTO task_execution_logs (task_id, created_time, log) VALUES (?, ?, ?)";
        for (TaskExecLog log : logs) {
            queryWithTransaction(
                    INSERT_LOG,
                    q ->
                            q.addParameter(log.getTaskId())
                                    .addParameter(new Timestamp(log.getCreatedTime()))
                                    .addParameter(log.getLog())
                                    .executeUpdate());
        }
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        return queryWithTransaction(
                "SELECT log, task_id, created_time FROM task_execution_logs WHERE task_id = ? ORDER BY created_time ASC",
                q ->
                        q.addParameter(taskId)
                                .executeAndFetch(
                                        rs -> {
                                            List<TaskExecLog> result = new ArrayList<>();
                                            while (rs.next()) {
                                                TaskExecLog log = new TaskExecLog();
                                                log.setLog(rs.getString("log"));
                                                log.setTaskId(rs.getString("task_id"));
                                                log.setCreatedTime(
                                                        rs.getTimestamp("created_time").getTime());
                                                result.add(log);
                                            }
                                            return result;
                                        }));
    }

    @Override
    public void setup() {}

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(WorkflowSummary workflow) {
        logger.info("asyncIndexWorkflow is not supported for Sqlite indexing");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(TaskSummary task) {
        logger.info("asyncIndexTask is not supported for Sqlite indexing");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort) {
        logger.info("searchWorkflows is not supported for Sqlite indexing");
        return null;
    }

    @Override
    public SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort) {
        logger.info("searchTasks is not supported for Sqlite indexing");
        return null;
    }

    @Override
    public void removeWorkflow(String workflowId) {
        String REMOVE_WORKFLOW_SQL = "DELETE FROM workflow_index WHERE workflow_id = ?";

        queryWithTransaction(REMOVE_WORKFLOW_SQL, q -> q.addParameter(workflowId).executeUpdate());
    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return CompletableFuture.runAsync(() -> removeWorkflow(workflowId), executorService);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        logger.info("updateWorkflow is not supported for Sqlite indexing");
    }

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(
            String workflowInstanceId, String[] keys, Object[] values) {
        logger.info("asyncUpdateWorkflow is not supported for Sqlite indexing");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void removeTask(String workflowId, String taskId) {
        String REMOVE_TASK_SQL = "DELETE FROM task_index WHERE task_id = ?";
        String REMOVE_TASK_EXECUTION_SQL = "DELETE FROM task_execution_logs WHERE task_id =?";
        withTransaction(
                connection -> {
                    queryWithTransaction(
                            REMOVE_TASK_SQL, q -> q.addParameter(taskId).executeUpdate());
                    queryWithTransaction(
                            REMOVE_TASK_EXECUTION_SQL, q -> q.addParameter(taskId).executeUpdate());
                });
    }

    @Override
    public CompletableFuture<Void> asyncRemoveTask(String workflowId, String taskId) {
        return CompletableFuture.runAsync(() -> removeTask(workflowId, taskId), executorService);
    }

    @Override
    public void updateTask(String workflowId, String taskId, String[] keys, Object[] values) {
        logger.info("updateTask is not supported for Sqlite indexing");
    }

    @Override
    public CompletableFuture<Void> asyncUpdateTask(
            String workflowId, String taskId, String[] keys, Object[] values) {
        logger.info("asyncUpdateTask is not supported for Sqlite indexing");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String get(String workflowInstanceId, String key) {
        logger.info("get is not supported for Sqlite indexing");
        return null;
    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        logger.info("asyncAddTaskExecutionLogs is not supported for Sqlite indexing");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {
        logger.info("addEventExecution is not supported for Sqlite indexing");
    }

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        logger.info("getEventExecutions is not supported for Sqlite indexing");
        return null;
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        logger.info("asyncAddEventExecution is not supported for Sqlite indexing");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void addMessage(String queue, Message msg) {
        logger.info("addMessage is not supported for Sqlite indexing");
    }

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        logger.info("asyncAddMessage is not supported for Sqlite indexing");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<Message> getMessages(String queue) {
        logger.info("getMessages is not supported for Sqlite indexing");
        return null;
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        logger.info("searchArchivableWorkflows is not supported for Sqlite indexing");
        return null;
    }

    public long getWorkflowCount(String query, String freeText) {
        logger.info("getWorkflowCount is not supported for Sqlite indexing");
        return 0;
    }
}
