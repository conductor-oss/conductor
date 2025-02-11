package com.netflix.conductor.sqlite.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.sqlite.config.SqliteProperties;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.*;

public class SqliteIndexDAO extends SqliteBaseDAO implements IndexDAO  {

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
        this.executorService = null;
        //  this.properties = properties;
       // this.onlyIndexOnStatusChange = properties.getOnlyIndexOnStatusChange();

        //int maximumPoolSize = properties.getAsyncMaxPoolSize();
        //int workerQueueSize = properties.getAsyncWorkerQueueSize();

        // Set up a workerpool for performing async operations.
//        this.executorService =
//                new ThreadPoolExecutor(
//                        CORE_POOL_SIZE,
//                        maximumPoolSize,
//                        KEEP_ALIVE_TIME,
//                        TimeUnit.MINUTES,
//                        new LinkedBlockingQueue<>(workerQueueSize),
//                        (runnable, executor) -> {
//                            logger.warn(
//                                    "Request {} to async dao discarded in executor {}",
//                                    runnable,
//                                    executor);
//                            Monitors.recordDiscardedIndexingCount("indexQueue");
//                        });
    }

    @Override
    public void setup() throws Exception {

    }

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {

    }

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(WorkflowSummary workflow) {
        return null;
    }

    @Override
    public void indexTask(TaskSummary task) {

    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(TaskSummary task) {
        return null;
    }

    @Override
    public SearchResult<String> searchWorkflows(String query, String freeText, int start, int count, List<String> sort) {
        return null;
    }

    @Override
    public SearchResult<WorkflowSummary> searchWorkflowSummary(String query, String freeText, int start, int count, List<String> sort) {
        return null;
    }

    @Override
    public SearchResult<String> searchTasks(String query, String freeText, int start, int count, List<String> sort) {
        return null;
    }

    @Override
    public SearchResult<TaskSummary> searchTaskSummary(String query, String freeText, int start, int count, List<String> sort) {
        return null;
    }

    @Override
    public void removeWorkflow(String workflowId) {

    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return null;
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {

    }

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        return null;
    }

    @Override
    public void removeTask(String workflowId, String taskId) {

    }

    @Override
    public CompletableFuture<Void> asyncRemoveTask(String workflowId, String taskId) {
        return null;
    }

    @Override
    public void updateTask(String workflowId, String taskId, String[] keys, Object[] values) {

    }

    @Override
    public CompletableFuture<Void> asyncUpdateTask(String workflowId, String taskId, String[] keys, Object[] values) {
        return null;
    }

    @Override
    public String get(String workflowInstanceId, String key) {
        return "";
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> logs) {

    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        return null;
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        return List.of();
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {

    }

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        return List.of();
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return null;
    }

    @Override
    public void addMessage(String queue, Message msg) {

    }

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        return null;
    }

    @Override
    public List<Message> getMessages(String queue) {
        return List.of();
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        return List.of();
    }

    @Override
    public long getWorkflowCount(String query, String freeText) {
        return 0;
    }
}
