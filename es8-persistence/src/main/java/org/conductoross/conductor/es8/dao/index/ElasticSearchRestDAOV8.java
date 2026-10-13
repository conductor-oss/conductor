/*
 * Copyright 2023 Conductor Authors.
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
package org.conductoross.conductor.es8.dao.index;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.conductoross.conductor.es8.config.ElasticSearchProperties;
import org.conductoross.conductor.es8.dao.query.parser.internal.ParserException;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.metrics.Monitors;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.*;

@Trace
public class ElasticSearchRestDAOV8 implements IndexDAO {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchRestDAOV8.class);

    private static final int CORE_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 1L;

    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String LOG_DOC_TYPE = "task_log";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String MSG_DOC_TYPE = "message";
    private static final int TASK_LOG_DELETE_BATCH_SIZE = 500;

    private @interface HttpMethod {

        String GET = "GET";
        String POST = "POST";
        String PUT = "PUT";
        String HEAD = "HEAD";
    }

    private static final String className = ElasticSearchRestDAOV8.class.getSimpleName();

    /**
     * A document created this long after the alias write index was created cannot live in an older
     * backing index; the margin absorbs clock skew between the Elasticsearch master and this JVM.
     */
    private static final long NEW_DOCUMENT_CLOCK_SKEW_MS = 60_000L;

    private final String workflowIndexName;
    private final String taskIndexName;
    private final String eventIndexPrefix;
    private final String eventIndexName;
    private final String messageIndexPrefix;
    private final String messageIndexName;
    private final String logIndexName;
    private final String logIndexPrefix;

    private final ElasticsearchClient elasticSearchClient;
    private final ElasticsearchAsyncClient elasticSearchAsyncClient;
    private final ElasticsearchTransport transport;
    private final RestClient elasticSearchAdminClient;
    private final ExecutorService executorService;
    private final ExecutorService logExecutorService;
    private final ElasticSearchProperties properties;
    private final RetryTemplate retryTemplate;
    private final ObjectMapper objectMapper;
    private final String indexPrefix;
    private final Es8IndexManagementSupport indexManagementSupport;
    private final Es8SearchSupport searchSupport;
    private final Es8BulkIngestionSupport bulkIngestionSupport;

    /**
     * Document id -> backing index name for documents this node has written or located. With ILM
     * rollover a document can live in an older backing index than the alias write index; without
     * this cache every index/update/delete needs an alias-wide ids search (one probe per shard of
     * every backing index) to find it.
     */
    private final Cache<String, String> docIndexCache;

    /** Alias -> resolved write index (name + creation date), refreshed every writeIndexCacheTtl. */
    private final Cache<String, WriteIndexInfo> writeIndexCache;

    private record WriteIndexInfo(String name, long creationDateMillis) {}

    public ElasticSearchRestDAOV8(
            RestClientBuilder restClientBuilder,
            RetryTemplate retryTemplate,
            ElasticSearchProperties properties,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.elasticSearchAdminClient = restClientBuilder.build();
        this.transport =
                new RestClientTransport(
                        this.elasticSearchAdminClient, new JacksonJsonpMapper(objectMapper));
        this.elasticSearchClient = new ElasticsearchClient(transport);
        this.elasticSearchAsyncClient = new ElasticsearchAsyncClient(transport);
        this.properties = properties;

        this.indexPrefix = properties.getIndexPrefix();

        this.workflowIndexName = getIndexName(WORKFLOW_DOC_TYPE);
        this.taskIndexName = getIndexName(TASK_DOC_TYPE);
        this.logIndexPrefix = getIndexName(LOG_DOC_TYPE);
        this.messageIndexPrefix = getIndexName(MSG_DOC_TYPE);
        this.eventIndexPrefix = getIndexName(EVENT_DOC_TYPE);
        this.logIndexName = this.logIndexPrefix;
        this.messageIndexName = this.messageIndexPrefix;
        this.eventIndexName = this.eventIndexPrefix;
        int workerQueueSize = properties.getAsyncWorkerQueueSize();
        int maximumPoolSize = properties.getAsyncMaxPoolSize();

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
                                    "Request  {} to async dao discarded in executor {}",
                                    runnable,
                                    executor);
                            Monitors.recordDiscardedIndexingCount("indexQueue");
                        });

        // Set up a workerpool for performing async operations for task_logs, event_executions,
        // message
        int corePoolSize = 1;
        maximumPoolSize = 2;
        long keepAliveTime = 30L;
        this.logExecutorService =
                new ThreadPoolExecutor(
                        corePoolSize,
                        maximumPoolSize,
                        keepAliveTime,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(workerQueueSize),
                        (runnable, executor) -> {
                            logger.warn(
                                    "Request {} to async log dao discarded in executor {}",
                                    runnable,
                                    executor);
                            Monitors.recordDiscardedIndexingCount("logQueue");
                        });
        this.retryTemplate = retryTemplate;
        this.indexManagementSupport =
                new Es8IndexManagementSupport(
                        this.elasticSearchClient,
                        this.retryTemplate,
                        this.properties,
                        this.objectMapper,
                        this.indexPrefix,
                        this.workflowIndexName,
                        this.taskIndexName,
                        this.logIndexName,
                        this.messageIndexName,
                        this.eventIndexName);
        this.searchSupport = new Es8SearchSupport(this.elasticSearchClient, this.indexPrefix);
        this.bulkIngestionSupport =
                new Es8BulkIngestionSupport(
                        this.elasticSearchClient,
                        this.elasticSearchAsyncClient,
                        this.retryTemplate,
                        this.properties,
                        this.executorService,
                        this.logExecutorService,
                        className);
        this.docIndexCache =
                CacheBuilder.newBuilder()
                        .maximumSize(Math.max(0, properties.getDocIndexCacheSize()))
                        .expireAfterAccess(Duration.ofDays(1))
                        .build();
        this.writeIndexCache =
                CacheBuilder.newBuilder()
                        .maximumSize(16)
                        .expireAfterWrite(properties.getWriteIndexCacheTtl())
                        .build();
    }

    @PreDestroy
    private void shutdown() {
        logger.info("Gracefully shutdown executor service");
        bulkIngestionSupport.close();
        shutdownExecutorService(logExecutorService);
        shutdownExecutorService(executorService);
        try {
            transport.close();
        } catch (IOException e) {
            logger.warn("Failed to close Elasticsearch transport", e);
        }
    }

    private void shutdownExecutorService(ExecutorService execService) {
        try {
            execService.shutdown();
            if (execService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.debug("tasks completed, shutting down");
            } else {
                logger.warn("Forcing shutdown after waiting for 30 seconds");
                execService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            logger.warn(
                    "Shutdown interrupted, invoking shutdownNow on scheduledThreadPoolExecutor for delay queue");
            execService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @PostConstruct
    public void setup() throws Exception {
        indexManagementSupport.setup();
    }

    private Query boolQueryBuilder(String expression, String queryString) throws ParserException {
        return searchSupport.boolQueryBuilder(expression, queryString);
    }

    private String getIndexName(String documentType) {
        if (StringUtils.isBlank(indexPrefix)) {
            return documentType;
        }
        if (indexPrefix.endsWith("_")) {
            return indexPrefix + documentType;
        }
        return indexPrefix + "_" + documentType;
    }

    /**
     * Determines whether a resource exists in ES. This will call a GET method to a particular path
     * and return true if status 200; false otherwise.
     *
     * @param resourcePath The path of the resource to get.
     * @return True if it exists; false otherwise.
     * @throws IOException If an error occurred during requests to ES.
     */
    public boolean doesResourceExist(final String resourcePath) throws IOException {
        Request request = new Request(HttpMethod.HEAD, resourcePath);
        try {
            Response response = elasticSearchAdminClient.performRequest(request);
            return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        } catch (ResponseException e) {
            int statusCode = e.getResponse().getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return false;
            }
            if (statusCode == HttpStatus.SC_METHOD_NOT_ALLOWED) {
                try {
                    Response response =
                            elasticSearchAdminClient.performRequest(
                                    new Request(HttpMethod.GET, resourcePath));
                    return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
                } catch (ResponseException inner) {
                    int innerStatus = inner.getResponse().getStatusLine().getStatusCode();
                    if (innerStatus == HttpStatus.SC_NOT_FOUND) {
                        return false;
                    }
                    throw inner;
                }
            }
            throw e;
        }
    }

    /**
     * The inverse of doesResourceExist.
     *
     * @param resourcePath The path of the resource to check.
     * @return True if it does not exist; false otherwise.
     * @throws IOException If an error occurred during requests to ES.
     */
    public boolean doesResourceNotExist(final String resourcePath) throws IOException {
        return !doesResourceExist(resourcePath);
    }

    private <T> T executeWithRetry(Callable<T> action) throws IOException {
        try {
            return retryTemplate.execute(context -> action.call());
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IOException("Elasticsearch operation failed", e);
        }
    }

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {
        try {
            long startTime = Instant.now().toEpochMilli();
            String workflowId = workflow.getWorkflowId();
            Refresh refresh = writeRefreshPolicy();
            indexDocumentWithIlmFallback(
                    workflowIndexName,
                    WORKFLOW_DOC_TYPE,
                    workflowId,
                    workflow,
                    refresh,
                    workflow.getUpdateTime(),
                    workflow.getStartTime());
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for indexing workflow: {}", endTime - startTime, workflowId);
            Monitors.recordESIndexTime("index_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            Monitors.error(className, "indexWorkflow");
            logger.error("Failed to index workflow: {}", workflow.getWorkflowId(), e);
            throw new TransientException(
                    String.format("Failed to index workflow %s", workflow.getWorkflowId()), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(WorkflowSummary workflow) {
        return CompletableFuture.runAsync(() -> indexWorkflow(workflow), executorService);
    }

    @Override
    public void indexTask(TaskSummary task) {
        try {
            long startTime = Instant.now().toEpochMilli();
            String taskId = task.getTaskId();
            Refresh refreshPolicy = writeRefreshPolicy();
            indexDocumentWithIlmFallback(
                    taskIndexName,
                    TASK_DOC_TYPE,
                    taskId,
                    task,
                    refreshPolicy,
                    task.getUpdateTime(),
                    task.getScheduledTime());
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for  indexing task:{} in workflow: {}",
                    endTime - startTime,
                    taskId,
                    task.getWorkflowId());
            Monitors.recordESIndexTime("index_task", TASK_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            logger.error("Failed to index task: {}", task.getTaskId(), e);
            throw new TransientException(
                    String.format("Failed to index task %s", task.getTaskId()), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(TaskSummary task) {
        return CompletableFuture.runAsync(() -> indexTask(task), executorService);
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> taskExecLogs) {
        if (taskExecLogs.isEmpty()) {
            return;
        }

        long startTime = Instant.now().toEpochMilli();
        List<BulkOperation> operations = new ArrayList<>();
        for (TaskExecLog log : taskExecLogs) {
            operations.add(
                    BulkOperation.of(op -> op.index(i -> i.index(logIndexName).document(log))));
        }

        try {
            Refresh refreshPolicy = writeRefreshPolicy();
            executeWithRetry(
                    () ->
                            elasticSearchClient.bulk(
                                    b -> {
                                        b.operations(operations);
                                        if (refreshPolicy != null) {
                                            b.refresh(refreshPolicy);
                                        }
                                        return b;
                                    }));
            long endTime = Instant.now().toEpochMilli();
            logger.debug("Time taken {} for indexing taskExecutionLogs", endTime - startTime);
            Monitors.recordESIndexTime(
                    "index_task_execution_logs", LOG_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
        } catch (Exception e) {
            List<String> taskIds =
                    taskExecLogs.stream().map(TaskExecLog::getTaskId).collect(Collectors.toList());
            logger.error("Failed to index task execution logs for tasks: {}", taskIds, e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        return CompletableFuture.runAsync(() -> addTaskExecutionLogs(logs), logExecutorService);
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        try {
            Query query = boolQueryBuilder("taskId='" + taskId + "'", "*");

            SearchResponse<TaskExecLog> response =
                    elasticSearchClient.search(
                            s ->
                                    s.index(logIndexPrefix)
                                            .query(query)
                                            .sort(
                                                    sort ->
                                                            sort.field(
                                                                    field ->
                                                                            field.field(
                                                                                            "createdTime")
                                                                                    .order(
                                                                                            SortOrder
                                                                                                    .Asc)))
                                            .size(properties.getTaskLogResultLimit())
                                            .trackTotalHits(t -> t.enabled(true)),
                            TaskExecLog.class);

            return mapTaskExecLogsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get task execution logs for task: {}", taskId, e);
        }
        return Collections.emptyList();
    }

    private List<TaskExecLog> mapTaskExecLogsResponse(SearchResponse<TaskExecLog> response) {
        List<TaskExecLog> logs = new ArrayList<>();
        response.hits()
                .hits()
                .forEach(
                        hit -> {
                            if (hit.source() != null) {
                                logs.add(hit.source());
                            }
                        });
        return logs;
    }

    @Override
    public List<Message> getMessages(String queue) {
        try {
            Query query = boolQueryBuilder("queue='" + queue + "'", "*");

            SearchResponse<Map> response =
                    elasticSearchClient.search(
                            s ->
                                    s.index(messageIndexPrefix)
                                            .query(query)
                                            .sort(
                                                    sort ->
                                                            sort.field(
                                                                    field ->
                                                                            field.field("created")
                                                                                    .order(
                                                                                            SortOrder
                                                                                                    .Asc)))
                                            .trackTotalHits(t -> t.enabled(true)),
                            Map.class);
            return mapGetMessagesResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get messages for queue: {}", queue, e);
        }
        return Collections.emptyList();
    }

    private List<Message> mapGetMessagesResponse(SearchResponse<Map> response) {
        List<Message> messages = new ArrayList<>();
        response.hits()
                .hits()
                .forEach(
                        hit -> {
                            Map source = hit.source();
                            if (source == null) {
                                return;
                            }
                            Object messageId = source.get("messageId");
                            Object payload = source.get("payload");
                            Message msg =
                                    new Message(
                                            messageId != null ? messageId.toString() : null,
                                            payload != null ? payload.toString() : null,
                                            null);
                            messages.add(msg);
                        });
        return messages;
    }

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        try {
            Query query = boolQueryBuilder("event='" + event + "'", "*");

            SearchResponse<EventExecution> response =
                    elasticSearchClient.search(
                            s ->
                                    s.index(eventIndexPrefix)
                                            .query(query)
                                            .sort(
                                                    sort ->
                                                            sort.field(
                                                                    field ->
                                                                            field.field("created")
                                                                                    .order(
                                                                                            SortOrder
                                                                                                    .Asc)))
                                            .trackTotalHits(t -> t.enabled(true)),
                            EventExecution.class);

            return mapEventExecutionsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get executions for event: {}", event, e);
        }
        return Collections.emptyList();
    }

    private List<EventExecution> mapEventExecutionsResponse(
            SearchResponse<EventExecution> response) {
        List<EventExecution> executions = new ArrayList<>();
        response.hits()
                .hits()
                .forEach(
                        hit -> {
                            if (hit.source() != null) {
                                executions.add(hit.source());
                            }
                        });
        return executions;
    }

    @Override
    public void addMessage(String queue, Message message) {
        try {
            long startTime = Instant.now().toEpochMilli();
            Map<String, Object> doc = new HashMap<>();
            doc.put("messageId", message.getId());
            doc.put("payload", message.getPayload());
            doc.put("queue", queue);
            doc.put("created", System.currentTimeMillis());

            indexObject(messageIndexName, MSG_DOC_TYPE, doc);
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for  indexing message: {}",
                    endTime - startTime,
                    message.getId());
            Monitors.recordESIndexTime("add_message", MSG_DOC_TYPE, endTime - startTime);
        } catch (Exception e) {
            logger.error("Failed to index message: {}", message.getId(), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        return CompletableFuture.runAsync(() -> addMessage(queue, message), executorService);
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {
        try {
            long startTime = Instant.now().toEpochMilli();
            String id =
                    eventExecution.getName()
                            + "."
                            + eventExecution.getEvent()
                            + "."
                            + eventExecution.getMessageId()
                            + "."
                            + eventExecution.getId();

            indexObject(eventIndexName, EVENT_DOC_TYPE, id, eventExecution, writeRefreshPolicy());
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for indexing event execution: {}",
                    endTime - startTime,
                    eventExecution.getId());
            Monitors.recordESIndexTime("add_event_execution", EVENT_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
        } catch (Exception e) {
            logger.error("Failed to index event execution: {}", eventExecution.getId(), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return CompletableFuture.runAsync(
                () -> addEventExecution(eventExecution), logExecutorService);
    }

    @Override
    public SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectIdsViaExpression(
                    query, start, count, sort, freeText, WORKFLOW_DOC_TYPE);
        } catch (Exception e) {
            throw new NonTransientException(e.getMessage(), e);
        }
    }

    @Override
    public SearchResult<WorkflowSummary> searchWorkflowSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectsViaExpression(
                    query,
                    start,
                    count,
                    sort,
                    freeText,
                    WORKFLOW_DOC_TYPE,
                    false,
                    WorkflowSummary.class);
        } catch (Exception e) {
            throw new TransientException(e.getMessage(), e);
        }
    }

    private <T> SearchResult<T> searchObjectsViaExpression(
            String structuredQuery,
            int start,
            int size,
            List<String> sortOptions,
            String freeTextQuery,
            String docType,
            boolean idOnly,
            Class<T> clazz)
            throws ParserException, IOException {
        return searchSupport.searchObjectsViaExpression(
                structuredQuery, start, size, sortOptions, freeTextQuery, docType, idOnly, clazz);
    }

    @Override
    public SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectIdsViaExpression(query, start, count, sort, freeText, TASK_DOC_TYPE);
        } catch (Exception e) {
            throw new NonTransientException(e.getMessage(), e);
        }
    }

    @Override
    public SearchResult<TaskSummary> searchTaskSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectsViaExpression(
                    query, start, count, sort, freeText, TASK_DOC_TYPE, false, TaskSummary.class);
        } catch (Exception e) {
            throw new TransientException(e.getMessage(), e);
        }
    }

    @Override
    public void removeWorkflow(String workflowId) {
        long startTime = Instant.now().toEpochMilli();
        try {
            List<String> taskIds = findTaskIdsForWorkflow(workflowId);
            if (!taskIds.isEmpty()) {
                deleteTaskLogsByTaskIds(taskIds);
            }
            deleteTasksByWorkflowId(workflowId);

            DeleteOutcome outcome = deleteByIdWithIlmFallback(workflowIndexName, workflowId);
            if (outcome == DeleteOutcome.NOT_FOUND) {
                logger.error("Index removal failed - document not found by id: {}", workflowId);
            }
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for removing workflow: {}", endTime - startTime, workflowId);
            Monitors.recordESIndexTime("remove_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (IOException e) {
            logger.error("Failed to remove workflow {} from index", workflowId, e);
            Monitors.error(className, "remove");
        }
    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return CompletableFuture.runAsync(() -> removeWorkflow(workflowId), executorService);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        if (keys.length != values.length) {
            throw new NonTransientException("Number of keys and values do not match");
        }

        long startTime = Instant.now().toEpochMilli();
        Map<String, Object> source =
                IntStream.range(0, keys.length)
                        .boxed()
                        .collect(Collectors.toMap(i -> keys[i], i -> values[i]));

        logger.debug("Updating workflow {} with {}", workflowInstanceId, source);
        try {
            UpdateOutcome outcome =
                    updateByIdWithIlmFallback(workflowIndexName, workflowInstanceId, source);
            if (outcome == UpdateOutcome.NOT_FOUND) {
                throw new NotFoundException(
                        "Workflow %s not found in index alias %s",
                        workflowInstanceId, workflowIndexName);
            }
        } catch (IOException e) {
            Monitors.error(className, "update");
            throw new TransientException(
                    String.format("Failed to update workflow %s", workflowInstanceId), e);
        } catch (RuntimeException e) {
            Monitors.error(className, "update");
            throw e;
        } finally {
            long endTime = Instant.now().toEpochMilli();
            Monitors.recordESIndexTime("update_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        }
    }

    @Override
    public void removeTask(String workflowId, String taskId) {
        long startTime = Instant.now().toEpochMilli();

        SearchResult<String> taskSearchResult =
                searchTasks(
                        String.format("(taskId='%s') AND (workflowId='%s')", taskId, workflowId),
                        "*",
                        0,
                        1,
                        null);

        if (taskSearchResult.getTotalHits() == 0) {
            logger.error("Task: {} does not belong to workflow: {}", taskId, workflowId);
            Monitors.error(className, "removeTask");
            return;
        }

        try {
            DeleteOutcome outcome = deleteByIdWithIlmFallback(taskIndexName, taskId);
            if (outcome != DeleteOutcome.DELETED) {
                logger.error("Index removal failed - task not found by id: {}", workflowId);
                Monitors.error(className, "removeTask");
            }
            deleteTaskLogsByTaskIds(Collections.singletonList(taskId));
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for removing task:{} of workflow: {}",
                    endTime - startTime,
                    taskId,
                    workflowId);
            Monitors.recordESIndexTime("remove_task", "", endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (IOException e) {
            logger.error(
                    "Failed to remove task {} of workflow: {} from index", taskId, workflowId, e);
            Monitors.error(className, "removeTask");
        }
    }

    private List<String> findTaskIdsForWorkflow(String workflowId) {
        int pageSize = 500;
        int start = 0;
        long totalHits = 0;
        List<String> taskIds = new ArrayList<>();
        try {
            Query query = Query.of(q -> q.term(t -> t.field("workflowId").value(workflowId)));
            do {
                SearchResult<String> result =
                        searchObjectIds(taskIndexName, query, start, pageSize, null);
                if (totalHits == 0) {
                    totalHits = result.getTotalHits();
                }
                taskIds.addAll(result.getResults());
                start += pageSize;
                if (start >= 10_000) {
                    if (totalHits > taskIds.size()) {
                        logger.warn(
                                "Task log cleanup capped at {} tasks for workflow {} (totalHits={})",
                                taskIds.size(),
                                workflowId,
                                totalHits);
                    }
                    break;
                }
            } while (start < totalHits);
        } catch (Exception e) {
            logger.warn("Failed to fetch task ids for workflow {}", workflowId, e);
        }
        return taskIds;
    }

    private void deleteTasksByWorkflowId(String workflowId) {
        Query query = Query.of(q -> q.term(t -> t.field("workflowId").value(workflowId)));
        deleteByQuery(taskIndexName, query, "tasks for workflow " + workflowId);
    }

    private void deleteTaskLogsByTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        List<String> uniqueIds =
                taskIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (int i = 0; i < uniqueIds.size(); i += TASK_LOG_DELETE_BATCH_SIZE) {
            List<String> batch =
                    uniqueIds.subList(
                            i, Math.min(i + TASK_LOG_DELETE_BATCH_SIZE, uniqueIds.size()));
            Query query =
                    Query.of(
                            q ->
                                    q.terms(
                                            t ->
                                                    t.field("taskId")
                                                            .terms(
                                                                    tv ->
                                                                            tv.value(
                                                                                    batch.stream()
                                                                                            .map(
                                                                                                    FieldValue
                                                                                                            ::of)
                                                                                            .collect(
                                                                                                    Collectors
                                                                                                            .toList())))));
            deleteByQuery(logIndexName, query, "task logs");
        }
    }

    private void deleteByQuery(String indexName, Query query, String description) {
        try {
            DeleteByQueryResponse response =
                    executeWithRetry(
                            () ->
                                    elasticSearchClient.deleteByQuery(
                                            d -> d.index(indexName).query(query).refresh(true)));
            if (response.failures() != null && !response.failures().isEmpty()) {
                logger.warn(
                        "Delete-by-query for {} had {} failures",
                        description,
                        response.failures().size());
            }
        } catch (Exception e) {
            logger.warn("Delete-by-query failed for {}", description, e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncRemoveTask(String workflowId, String taskId) {
        return CompletableFuture.runAsync(() -> removeTask(workflowId, taskId), executorService);
    }

    @Override
    public void updateTask(String workflowId, String taskId, String[] keys, Object[] values) {
        if (keys.length != values.length) {
            throw new NonTransientException("Number of keys and values do not match");
        }

        long startTime = Instant.now().toEpochMilli();
        Map<String, Object> source =
                IntStream.range(0, keys.length)
                        .boxed()
                        .collect(Collectors.toMap(i -> keys[i], i -> values[i]));

        logger.debug("Updating task: {} of workflow: {} with {}", taskId, workflowId, source);
        try {
            UpdateOutcome outcome = updateByIdWithIlmFallback(taskIndexName, taskId, source);
            if (outcome == UpdateOutcome.NOT_FOUND) {
                throw new NotFoundException(
                        "Task %s not found in index alias %s", taskId, taskIndexName);
            }
        } catch (IOException e) {
            Monitors.error(className, "update");
            throw new TransientException(
                    String.format("Failed to update task %s of workflow %s", taskId, workflowId),
                    e);
        } catch (RuntimeException e) {
            Monitors.error(className, "update");
            throw e;
        } finally {
            long endTime = Instant.now().toEpochMilli();
            Monitors.recordESIndexTime("update_task", "", endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        }
    }

    private enum UpdateOutcome {
        UPDATED,
        SKIPPED_STALE,
        NOT_FOUND
    }

    private enum DeleteOutcome {
        DELETED,
        NOT_FOUND
    }

    /**
     * With ILM rollover, an alias write index may not contain the requested document (it might live
     * in an older backing index). This method first attempts an update against the alias; on
     * NOT_FOUND, it resolves the backing index via an ids query and retries the update against that
     * index.
     */
    private UpdateOutcome updateByIdWithIlmFallback(
            String indexAlias, String id, Map<String, Object> source) throws IOException {
        Optional<String> located = locateDocumentIndex(indexAlias, id, Optional.empty());
        if (located.isEmpty()) {
            return UpdateOutcome.NOT_FOUND;
        }
        String targetIndex = located.get();

        if (isStaleWrite(targetIndex, id, source.get("updateTime"))) {
            logger.warn(
                    "Skipping stale partial update for document {} in alias {} because incoming updateTime is older than the indexed document",
                    id,
                    indexAlias);
            return UpdateOutcome.SKIPPED_STALE;
        }

        UpdateOutcome outcome = updateById(targetIndex, id, source);
        if (outcome != UpdateOutcome.NOT_FOUND) {
            return outcome;
        }

        // The cached location was wrong (document deleted or moved): forget it and resolve once
        // through the alias-wide search.
        forgetDocumentIndex(indexAlias, id);
        Optional<String> resolvedIndex = resolveSingleIndexForId(indexAlias, id);
        if (resolvedIndex.isEmpty()) {
            return UpdateOutcome.NOT_FOUND;
        }
        rememberDocumentIndex(indexAlias, id, resolvedIndex.get());
        return updateById(resolvedIndex.get(), id, source);
    }

    private UpdateOutcome updateById(String indexOrAlias, String id, Map<String, Object> source)
            throws IOException {
        try {
            Refresh refreshPolicy = writeRefreshPolicy();
            UpdateResponse<Map> response =
                    executeWithRetry(
                            () ->
                                    elasticSearchClient.update(
                                            u -> {
                                                u.index(indexOrAlias).id(id).doc(source);
                                                if (refreshPolicy != null) {
                                                    u.refresh(refreshPolicy);
                                                }
                                                return u;
                                            },
                                            Map.class));
            return response.result() == Result.NotFound
                    ? UpdateOutcome.NOT_FOUND
                    : UpdateOutcome.UPDATED;
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return UpdateOutcome.NOT_FOUND;
            }
            throw e;
        }
    }

    private Optional<String> resolveSingleIndexForId(String indexAlias, String id)
            throws IOException {
        Query idsQuery = Query.of(q -> q.ids(i -> i.values(id)));
        SearchResponse<Void> response =
                executeWithRetry(
                        () ->
                                elasticSearchClient.search(
                                        s ->
                                                s.index(indexAlias)
                                                        .query(idsQuery)
                                                        .size(2)
                                                        .source(src -> src.fetch(false)),
                                        Void.class));

        List<String> indices =
                response.hits().hits().stream()
                        .map(hit -> hit.index())
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .toList();
        if (indices.isEmpty()) {
            return Optional.empty();
        }
        if (indices.size() > 1) {
            throw new NonTransientException(
                    String.format(
                            "Found %d documents for id %s across multiple indices behind alias %s: %s",
                            indices.size(), id, indexAlias, indices));
        }
        return Optional.of(indices.getFirst());
    }

    private DeleteOutcome deleteByIdWithIlmFallback(String indexAlias, String id)
            throws IOException {
        try {
            Optional<String> located = locateDocumentIndex(indexAlias, id, Optional.empty());
            if (located.isEmpty()) {
                return DeleteOutcome.NOT_FOUND;
            }
            DeleteOutcome outcome = deleteById(located.get(), id);
            if (outcome == DeleteOutcome.DELETED) {
                return outcome;
            }
            Optional<String> resolvedIndex = resolveSingleIndexForId(indexAlias, id);
            if (resolvedIndex.isEmpty()) {
                return DeleteOutcome.NOT_FOUND;
            }
            return deleteById(resolvedIndex.get(), id);
        } finally {
            forgetDocumentIndex(indexAlias, id);
        }
    }

    private DeleteOutcome deleteById(String indexName, String id) throws IOException {
        try {
            Refresh refreshPolicy = writeRefreshPolicy();
            DeleteResponse response =
                    executeWithRetry(
                            () ->
                                    elasticSearchClient.delete(
                                            d -> {
                                                d.index(indexName).id(id);
                                                if (refreshPolicy != null) {
                                                    d.refresh(refreshPolicy);
                                                }
                                                return d;
                                            }));
            return response.result() == Result.Deleted
                    ? DeleteOutcome.DELETED
                    : DeleteOutcome.NOT_FOUND;
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return DeleteOutcome.NOT_FOUND;
            }
            throw e;
        }
    }

    private Map getDocumentSourceByIdWithIlmFallback(String indexAlias, String id)
            throws IOException {
        Optional<String> located = locateDocumentIndex(indexAlias, id, Optional.empty());
        if (located.isEmpty()) {
            return null;
        }
        return getDocumentSource(located.get(), id);
    }

    private Map getDocumentSource(String indexName, String id) throws IOException {
        try {
            GetResponse<Map> response =
                    elasticSearchClient.get(g -> g.index(indexName).id(id), Map.class);
            return response.found() ? response.source() : null;
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private boolean existsInIndex(String indexName, String id) throws IOException {
        try {
            return elasticSearchClient.exists(e -> e.index(indexName).id(id)).value();
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private String docIndexCacheKey(String indexAlias, String id) {
        return indexAlias + "\u0000" + id;
    }

    private void rememberDocumentIndex(String indexAlias, String id, String indexName) {
        if (properties.getDocIndexCacheSize() > 0 && StringUtils.isNotBlank(indexName)) {
            docIndexCache.put(docIndexCacheKey(indexAlias, id), indexName);
        }
    }

    private void forgetDocumentIndex(String indexAlias, String id) {
        docIndexCache.invalidate(docIndexCacheKey(indexAlias, id));
    }

    /**
     * Find the backing index that holds {@code id} behind {@code indexAlias}, cheapest check first:
     *
     * <ol>
     *   <li>the document-location cache (no round trip);
     *   <li>a HEAD on the alias write index (one shard, hot tier);
     *   <li>if the document was created after the write index was created it cannot live in an
     *       older backing index, so it is new: return empty without an alias-wide search;
     *   <li>otherwise the alias-wide ids search (one probe per shard of every backing index).
     * </ol>
     *
     * Returns empty when the document does not exist anywhere behind the alias.
     */
    private Optional<String> locateDocumentIndex(
            String indexAlias, String id, Optional<Long> createdAtMillis) throws IOException {
        String cached = docIndexCache.getIfPresent(docIndexCacheKey(indexAlias, id));
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<WriteIndexInfo> writeIndex = writeIndexInfo(indexAlias);
        if (writeIndex.isPresent()) {
            if (existsInIndex(writeIndex.get().name(), id)) {
                rememberDocumentIndex(indexAlias, id, writeIndex.get().name());
                return Optional.of(writeIndex.get().name());
            }
            if (createdAtMillis.isPresent()
                    && writeIndex.get().creationDateMillis() > 0
                    && createdAtMillis.get()
                            >= writeIndex.get().creationDateMillis() + NEW_DOCUMENT_CLOCK_SKEW_MS) {
                return Optional.empty();
            }
        }

        Optional<String> resolvedIndex = resolveSingleIndexForId(indexAlias, id);
        resolvedIndex.ifPresent(index -> rememberDocumentIndex(indexAlias, id, index));
        return resolvedIndex;
    }

    private <T> void indexDocumentWithIlmFallback(
            String indexAlias,
            String docType,
            String id,
            T doc,
            Refresh refreshPolicy,
            Object incomingUpdateTime,
            Object createdAt)
            throws IOException {
        Optional<String> located = locateDocumentIndex(indexAlias, id, toEpochMillis(createdAt));
        if (located.isEmpty()) {
            // New document: write through the alias so Elasticsearch routes it to the current
            // write index even if our cached write index just rolled over.
            indexObject(indexAlias, docType, id, doc, refreshPolicy);
            return;
        }

        String targetIndex = located.get();
        if (isStaleWrite(targetIndex, id, incomingUpdateTime)) {
            logger.warn(
                    "Skipping stale {} index write for document {} in alias {} because incoming updateTime is older than the indexed document",
                    docType,
                    id,
                    indexAlias);
            return;
        }
        indexObject(targetIndex, docType, id, doc, refreshPolicy);
    }

    /**
     * True when the document already indexed in {@code indexName} is newer than the incoming one.
     */
    private boolean isStaleWrite(String indexName, String id, Object incomingUpdateTime)
            throws IOException {
        Optional<Long> incomingUpdateTimeMs = toEpochMillis(incomingUpdateTime);
        if (incomingUpdateTimeMs.isEmpty()) {
            return false;
        }

        Map existingSource = getDocumentSource(indexName, id);
        if (existingSource == null) {
            return false;
        }

        Optional<Long> indexedUpdateTimeMs = toEpochMillis(existingSource.get("updateTime"));
        return indexedUpdateTimeMs.isPresent()
                && incomingUpdateTimeMs.get() < indexedUpdateTimeMs.get();
    }

    private Optional<Long> toEpochMillis(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }

        String text = value.toString();
        if (StringUtils.isBlank(text)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Instant.parse(text).toEpochMilli());
        } catch (Exception ignored) {
            // fall through
        }

        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<WriteIndexInfo> writeIndexInfo(String alias) throws IOException {
        WriteIndexInfo cached = writeIndexCache.getIfPresent(alias);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> name = resolveWriteIndexForAlias(alias);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        WriteIndexInfo info = new WriteIndexInfo(name.get(), indexCreationDateMillis(name.get()));
        writeIndexCache.put(alias, info);
        return Optional.of(info);
    }

    /** Creation date of a backing index in epoch millis, or 0 when it cannot be read. */
    private long indexCreationDateMillis(String indexName) {
        Request request =
                new Request(HttpMethod.GET, "/" + indexName + "/_settings/index.creation_date");
        try {
            Response response = elasticSearchAdminClient.performRequest(request);
            JsonNode root = objectMapper.readTree(response.getEntity().getContent());
            JsonNode node =
                    root.path(indexName).path("settings").path("index").path("creation_date");
            return node.isMissingNode() ? 0L : Long.parseLong(node.asText("0"));
        } catch (IOException | NumberFormatException e) {
            logger.warn("Could not read creation date of index {}: {}", indexName, e.toString());
            return 0L;
        }
    }

    private Optional<String> resolveWriteIndexForAlias(String alias) throws IOException {
        Request request = new Request(HttpMethod.GET, "/_alias/" + alias);
        try {
            Response response = elasticSearchAdminClient.performRequest(request);
            JsonNode root = objectMapper.readTree(response.getEntity().getContent());
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }

            List<String> writeIndices = new ArrayList<>();
            Iterator<String> indexNames = root.fieldNames();
            while (indexNames.hasNext()) {
                String indexName = indexNames.next();
                JsonNode indexNode = root.get(indexName);
                if (indexNode == null) {
                    continue;
                }
                JsonNode aliasesNode = indexNode.get("aliases");
                if (aliasesNode == null || !aliasesNode.isObject()) {
                    continue;
                }
                JsonNode aliasNode = aliasesNode.get(alias);
                if (aliasNode == null || !aliasNode.isObject()) {
                    continue;
                }
                JsonNode isWriteIndexNode = aliasNode.get("is_write_index");
                if (isWriteIndexNode != null && isWriteIndexNode.asBoolean(false)) {
                    writeIndices.add(indexName);
                }
            }

            if (writeIndices.isEmpty()) {
                List<String> allIndices = new ArrayList<>();
                root.fieldNames().forEachRemaining(allIndices::add);
                if (allIndices.size() == 1) {
                    return Optional.of(allIndices.getFirst());
                }
                return Optional.empty();
            }
            if (writeIndices.size() > 1) {
                throw new NonTransientException(
                        String.format(
                                "Alias %s has %d write indices: %s",
                                alias, writeIndices.size(), writeIndices));
            }
            return Optional.of(writeIndices.getFirst());
        } catch (ResponseException e) {
            int statusCode = e.getResponse().getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public CompletableFuture<Void> asyncUpdateTask(
            String workflowId, String taskId, String[] keys, Object[] values) {
        return CompletableFuture.runAsync(
                () -> updateTask(workflowId, taskId, keys, values), executorService);
    }

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(
            String workflowInstanceId, String[] keys, Object[] values) {
        return CompletableFuture.runAsync(
                () -> updateWorkflow(workflowInstanceId, keys, values), executorService);
    }

    @Override
    public String get(String workflowInstanceId, String fieldToGet) {
        try {
            Map sourceAsMap =
                    getDocumentSourceByIdWithIlmFallback(workflowIndexName, workflowInstanceId);
            if (sourceAsMap != null && sourceAsMap.get(fieldToGet) != null) {
                return sourceAsMap.get(fieldToGet).toString();
            }
        } catch (IOException e) {
            logger.error(
                    "Unable to get Workflow: {} from ElasticSearch index: {}",
                    workflowInstanceId,
                    workflowIndexName,
                    e);
            return null;
        }

        logger.debug(
                "Unable to find Workflow: {} in ElasticSearch index: {}.",
                workflowInstanceId,
                workflowIndexName);
        return null;
    }

    private SearchResult<String> searchObjectIdsViaExpression(
            String structuredQuery,
            int start,
            int size,
            List<String> sortOptions,
            String freeTextQuery,
            String docType)
            throws ParserException, IOException {
        return searchSupport.searchObjectIdsViaExpression(
                structuredQuery, start, size, sortOptions, freeTextQuery, docType);
    }

    private SearchResult<String> searchObjectIds(
            String indexName, Query queryBuilder, int start, int size) throws IOException {
        return searchSupport.searchObjectIds(indexName, queryBuilder, start, size);
    }

    /**
     * Tries to find object ids for a given query in an index.
     *
     * @param indexName The name of the index.
     * @param queryBuilder The query to use for searching.
     * @param start The start to use.
     * @param size The total return size.
     * @param sortOptions A list of string options to sort in the form VALUE:ORDER; where ORDER is
     *     optional and can be either ASC OR DESC.
     * @return The SearchResults which includes the count and IDs that were found.
     * @throws IOException If we cannot communicate with ES.
     */
    private SearchResult<String> searchObjectIds(
            String indexName, Query queryBuilder, int start, int size, List<String> sortOptions)
            throws IOException {
        return searchSupport.searchObjectIds(indexName, queryBuilder, start, size, sortOptions);
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        try {
            return searchSupport.searchArchivableWorkflows(indexName, archiveTtlDays);
        } catch (IOException e) {
            logger.error("Unable to communicate with ES to find archivable workflows", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long getWorkflowCount(String query, String freeText) {
        try {
            return getObjectCounts(query, freeText, WORKFLOW_DOC_TYPE);
        } catch (Exception e) {
            throw new NonTransientException(e.getMessage(), e);
        }
    }

    private long getObjectCounts(String structuredQuery, String freeTextQuery, String docType)
            throws ParserException, IOException {
        return searchSupport.getObjectCounts(structuredQuery, freeTextQuery, docType);
    }

    public List<String> searchRecentRunningWorkflows(
            int lastModifiedHoursAgoFrom, int lastModifiedHoursAgoTo) {
        try {
            return searchSupport.searchRecentRunningWorkflows(
                    workflowIndexName, lastModifiedHoursAgoFrom, lastModifiedHoursAgoTo);
        } catch (IOException e) {
            logger.error("Unable to communicate with ES to find recent running workflows", e);
            return Collections.emptyList();
        }
    }

    private void indexObject(final String index, final String docType, final Object doc) {
        bulkIngestionSupport.indexObject(index, docType, doc);
    }

    private void indexObject(
            final String index,
            final String docType,
            final String docId,
            final Object doc,
            final Refresh refreshPolicy) {
        bulkIngestionSupport.indexObject(index, docType, docId, doc, refreshPolicy);
    }

    private Refresh writeRefreshPolicy() {
        if (properties.isRefreshOnWrite()) {
            return Refresh.True;
        }
        return properties.isWaitForIndexRefresh() ? Refresh.WaitFor : null;
    }
}
