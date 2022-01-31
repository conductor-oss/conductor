/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.es6.dao.index;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.RetryUtil;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.es6.config.ElasticSearchProperties;
import com.netflix.conductor.es6.dao.query.parser.internal.ParserException;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;

@Trace
public class ElasticSearchDAOV6 extends ElasticSearchBaseDAO implements IndexDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchDAOV6.class);

    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String LOG_DOC_TYPE = "task_log";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String MSG_DOC_TYPE = "message";

    private static final int RETRY_COUNT = 3;
    private static final int CORE_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 1L;
    private static final int UPDATE_REQUEST_RETRY_COUNT = 5;

    private static final String CLASS_NAME = ElasticSearchDAOV6.class.getSimpleName();

    private final String workflowIndexName;
    private final String taskIndexName;
    private final String eventIndexPrefix;
    private String eventIndexName;
    private final String messageIndexPrefix;
    private String messageIndexName;
    private String logIndexName;
    private final String logIndexPrefix;
    private final String docTypeOverride;

    private final ObjectMapper objectMapper;
    private final Client elasticSearchClient;

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMWW");

    private final ExecutorService executorService;
    private final ExecutorService logExecutorService;

    private final ConcurrentHashMap<String, BulkRequests> bulkRequests;
    private final int indexBatchSize;
    private final long asyncBufferFlushTimeout;
    private final ElasticSearchProperties properties;

    static {
        SIMPLE_DATE_FORMAT.setTimeZone(GMT);
    }

    public ElasticSearchDAOV6(
            Client elasticSearchClient,
            ElasticSearchProperties properties,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.elasticSearchClient = elasticSearchClient;
        this.indexPrefix = properties.getIndexPrefix();
        this.workflowIndexName = getIndexName(WORKFLOW_DOC_TYPE);
        this.taskIndexName = getIndexName(TASK_DOC_TYPE);
        this.logIndexPrefix = this.indexPrefix + "_" + LOG_DOC_TYPE;
        this.messageIndexPrefix = this.indexPrefix + "_" + MSG_DOC_TYPE;
        this.eventIndexPrefix = this.indexPrefix + "_" + EVENT_DOC_TYPE;
        int workerQueueSize = properties.getAsyncWorkerQueueSize();
        int maximumPoolSize = properties.getAsyncMaxPoolSize();
        this.bulkRequests = new ConcurrentHashMap<>();
        this.indexBatchSize = properties.getIndexBatchSize();
        this.asyncBufferFlushTimeout = properties.getAsyncBufferFlushTimeout().toMillis();
        this.properties = properties;

        if (!properties.isAutoIndexManagementEnabled()
                && StringUtils.isNotBlank(properties.getDocumentTypeOverride())) {
            docTypeOverride = properties.getDocumentTypeOverride();
        } else {
            docTypeOverride = "";
        }

        this.executorService =
                new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        maximumPoolSize,
                        KEEP_ALIVE_TIME,
                        TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>(workerQueueSize),
                        (runnable, executor) -> {
                            LOGGER.warn(
                                    "Request  {} to async dao discarded in executor {}",
                                    runnable,
                                    executor);
                            Monitors.recordDiscardedIndexingCount("indexQueue");
                        });

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
                            LOGGER.warn(
                                    "Request {} to async log dao discarded in executor {}",
                                    runnable,
                                    executor);
                            Monitors.recordDiscardedIndexingCount("logQueue");
                        });

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::flushBulkRequests, 60, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    private void shutdown() {
        LOGGER.info("Starting graceful shutdown of executor service");
        shutdownExecutorService(logExecutorService);
        shutdownExecutorService(executorService);
    }

    private void shutdownExecutorService(ExecutorService execService) {
        try {
            execService.shutdown();
            if (execService.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.debug("tasks completed, shutting down");
            } else {
                LOGGER.warn("Forcing shutdown after waiting for 30 seconds");
                execService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            LOGGER.warn(
                    "Shutdown interrupted, invoking shutdownNow on scheduledThreadPoolExecutor for delay queue");
            execService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @PostConstruct
    public void setup() throws Exception {
        waitForHealthyCluster();

        if (properties.isAutoIndexManagementEnabled()) {
            createIndexesTemplates();
            createWorkflowIndex();
            createTaskIndex();
        }
    }

    private void waitForHealthyCluster() throws Exception {
        elasticSearchClient
                .admin()
                .cluster()
                .prepareHealth()
                .setWaitForGreenStatus()
                .execute()
                .get();
    }

    /** Initializes the indexes templates task_log, message and event, and mappings. */
    private void createIndexesTemplates() {
        try {
            initIndexesTemplates();
            updateIndexesNames();
            Executors.newScheduledThreadPool(1)
                    .scheduleAtFixedRate(this::updateIndexesNames, 0, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            LOGGER.error("Error creating index templates", e);
        }
    }

    private void initIndexesTemplates() {
        initIndexTemplate(LOG_DOC_TYPE);
        initIndexTemplate(EVENT_DOC_TYPE);
        initIndexTemplate(MSG_DOC_TYPE);
    }

    private void initIndexTemplate(String type) {
        String template = "template_" + type;
        GetIndexTemplatesResponse result =
                elasticSearchClient
                        .admin()
                        .indices()
                        .prepareGetTemplates(template)
                        .execute()
                        .actionGet();
        if (result.getIndexTemplates().isEmpty()) {
            LOGGER.info("Creating the index template '{}'", template);
            try {
                String templateSource = loadTypeMappingSource("/" + template + ".json");
                elasticSearchClient
                        .admin()
                        .indices()
                        .preparePutTemplate(template)
                        .setSource(templateSource.getBytes(), XContentType.JSON)
                        .execute()
                        .actionGet();
            } catch (Exception e) {
                LOGGER.error("Failed to init " + template, e);
            }
        }
    }

    private void updateIndexesNames() {
        logIndexName = updateIndexName(LOG_DOC_TYPE);
        eventIndexName = updateIndexName(EVENT_DOC_TYPE);
        messageIndexName = updateIndexName(MSG_DOC_TYPE);
    }

    private String updateIndexName(String type) {
        String indexName =
                this.indexPrefix + "_" + type + "_" + SIMPLE_DATE_FORMAT.format(new Date());
        createIndex(indexName);
        return indexName;
    }

    private void createWorkflowIndex() {
        createIndex(workflowIndexName);
        addTypeMapping(workflowIndexName, WORKFLOW_DOC_TYPE, "/mappings_docType_workflow.json");
    }

    private void createTaskIndex() {
        createIndex(taskIndexName);
        addTypeMapping(taskIndexName, TASK_DOC_TYPE, "/mappings_docType_task.json");
    }

    private void createIndex(String indexName) {
        try {
            elasticSearchClient
                    .admin()
                    .indices()
                    .prepareGetIndex()
                    .addIndices(indexName)
                    .execute()
                    .actionGet();
        } catch (IndexNotFoundException infe) {
            try {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
                createIndexRequest.settings(
                        Settings.builder()
                                .put("index.number_of_shards", properties.getIndexShardCount())
                                .put(
                                        "index.number_of_replicas",
                                        properties.getIndexReplicasCount()));

                elasticSearchClient.admin().indices().create(createIndexRequest).actionGet();
            } catch (ResourceAlreadyExistsException done) {
                LOGGER.error("Failed to update log index name: {}", indexName, done);
            }
        }
    }

    private void addTypeMapping(String indexName, String type, String sourcePath) {
        GetMappingsResponse getMappingsResponse =
                elasticSearchClient
                        .admin()
                        .indices()
                        .prepareGetMappings(indexName)
                        .addTypes(type)
                        .execute()
                        .actionGet();
        if (getMappingsResponse.mappings().isEmpty()) {
            LOGGER.info("Adding the {} type mappings", indexName);
            try {
                String source = loadTypeMappingSource(sourcePath);
                elasticSearchClient
                        .admin()
                        .indices()
                        .preparePutMapping(indexName)
                        .setType(type)
                        .setSource(source, XContentType.JSON)
                        .execute()
                        .actionGet();
            } catch (Exception e) {
                LOGGER.error("Failed to init index " + indexName + " mappings", e);
            }
        }
    }

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {
        try {
            long startTime = Instant.now().toEpochMilli();
            String id = workflow.getWorkflowId();
            byte[] doc = objectMapper.writeValueAsBytes(workflow);
            String docType =
                    StringUtils.isBlank(docTypeOverride) ? WORKFLOW_DOC_TYPE : docTypeOverride;

            UpdateRequest req = buildUpdateRequest(id, doc, workflowIndexName, docType);
            new RetryUtil<UpdateResponse>()
                    .retryOnException(
                            () -> elasticSearchClient.update(req).actionGet(),
                            null,
                            null,
                            RETRY_COUNT,
                            "Indexing workflow document: " + workflow.getWorkflowId(),
                            "indexWorkflow");

            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for indexing workflow: {}",
                    endTime - startTime,
                    workflow.getWorkflowId());
            Monitors.recordESIndexTime("index_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "indexWorkflow");
            LOGGER.error("Failed to index workflow: {}", workflow.getWorkflowId(), e);
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
            String id = task.getTaskId();
            byte[] doc = objectMapper.writeValueAsBytes(task);
            String docType = StringUtils.isBlank(docTypeOverride) ? TASK_DOC_TYPE : docTypeOverride;

            UpdateRequest req = new UpdateRequest(taskIndexName, docType, id);
            req.doc(doc, XContentType.JSON);
            req.upsert(doc, XContentType.JSON);
            indexObject(req, TASK_DOC_TYPE);
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for  indexing task:{} in workflow: {}",
                    endTime - startTime,
                    task.getTaskId(),
                    task.getWorkflowId());
            Monitors.recordESIndexTime("index_task", TASK_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            LOGGER.error("Failed to index task: {}", task.getTaskId(), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(TaskSummary task) {
        return CompletableFuture.runAsync(() -> indexTask(task), executorService);
    }

    private void indexObject(UpdateRequest req, String docType) {
        if (bulkRequests.get(docType) == null) {
            bulkRequests.put(
                    docType,
                    new BulkRequests(
                            System.currentTimeMillis(), elasticSearchClient.prepareBulk()));
        }
        bulkRequests.get(docType).getBulkRequestBuilder().add(req);
        if (bulkRequests.get(docType).getBulkRequestBuilder().numberOfActions()
                >= this.indexBatchSize) {
            indexBulkRequest(docType);
        }
    }

    private synchronized void indexBulkRequest(String docType) {
        if (bulkRequests.get(docType).getBulkRequestBuilder() != null
                && bulkRequests.get(docType).getBulkRequestBuilder().numberOfActions() > 0) {
            updateWithRetry(bulkRequests.get(docType).getBulkRequestBuilder(), docType);
            bulkRequests.put(
                    docType,
                    new BulkRequests(
                            System.currentTimeMillis(), elasticSearchClient.prepareBulk()));
        }
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> taskExecLogs) {
        if (taskExecLogs.isEmpty()) {
            return;
        }

        try {
            long startTime = Instant.now().toEpochMilli();
            BulkRequestBuilderWrapper bulkRequestBuilder =
                    new BulkRequestBuilderWrapper(elasticSearchClient.prepareBulk());
            for (TaskExecLog log : taskExecLogs) {
                String docType =
                        StringUtils.isBlank(docTypeOverride) ? LOG_DOC_TYPE : docTypeOverride;
                IndexRequest request = new IndexRequest(logIndexName, docType);
                request.source(objectMapper.writeValueAsBytes(log), XContentType.JSON);
                bulkRequestBuilder.add(request);
            }
            new RetryUtil<BulkResponse>()
                    .retryOnException(
                            () -> bulkRequestBuilder.execute().actionGet(5, TimeUnit.SECONDS),
                            null,
                            BulkResponse::hasFailures,
                            RETRY_COUNT,
                            "Indexing task execution logs",
                            "addTaskExecutionLogs");
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug("Time taken {} for indexing taskExecutionLogs", endTime - startTime);
            Monitors.recordESIndexTime(
                    "index_task_execution_logs", LOG_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
        } catch (Exception e) {
            List<String> taskIds =
                    taskExecLogs.stream().map(TaskExecLog::getTaskId).collect(Collectors.toList());
            LOGGER.error("Failed to index task execution logs for tasks: {}", taskIds, e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        return CompletableFuture.runAsync(() -> addTaskExecutionLogs(logs), logExecutorService);
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        try {
            BoolQueryBuilder query = boolQueryBuilder("taskId='" + taskId + "'", "*");

            String docType = StringUtils.isBlank(docTypeOverride) ? LOG_DOC_TYPE : docTypeOverride;
            final SearchRequestBuilder srb =
                    elasticSearchClient
                            .prepareSearch(logIndexPrefix + "*")
                            .setQuery(query)
                            .setTypes(docType)
                            .setSize(properties.getTaskLogResultLimit())
                            .addSort(SortBuilders.fieldSort("createdTime").order(SortOrder.ASC));

            return mapTaskExecLogsResponse(srb.execute().actionGet());
        } catch (Exception e) {
            LOGGER.error("Failed to get task execution logs for task: {}", taskId, e);
        }
        return null;
    }

    private List<TaskExecLog> mapTaskExecLogsResponse(SearchResponse response) throws IOException {
        SearchHit[] hits = response.getHits().getHits();
        List<TaskExecLog> logs = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            String source = hit.getSourceAsString();
            TaskExecLog tel = objectMapper.readValue(source, TaskExecLog.class);
            logs.add(tel);
        }
        return logs;
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

            String docType = StringUtils.isBlank(docTypeOverride) ? MSG_DOC_TYPE : docTypeOverride;
            UpdateRequest req = new UpdateRequest(messageIndexName, docType, message.getId());
            req.doc(doc, XContentType.JSON);
            req.upsert(doc, XContentType.JSON);
            indexObject(req, MSG_DOC_TYPE);
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for  indexing message: {}",
                    endTime - startTime,
                    message.getId());
            Monitors.recordESIndexTime("add_message", MSG_DOC_TYPE, endTime - startTime);
        } catch (Exception e) {
            LOGGER.error("Failed to index message: {}", message.getId(), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        return CompletableFuture.runAsync(() -> addMessage(queue, message), executorService);
    }

    @Override
    public List<Message> getMessages(String queue) {
        try {
            BoolQueryBuilder fq = boolQueryBuilder("queue='" + queue + "'", "*");

            String docType = StringUtils.isBlank(docTypeOverride) ? MSG_DOC_TYPE : docTypeOverride;
            final SearchRequestBuilder srb =
                    elasticSearchClient
                            .prepareSearch(messageIndexPrefix + "*")
                            .setQuery(fq)
                            .setTypes(docType)
                            .addSort(SortBuilders.fieldSort("created").order(SortOrder.ASC));

            return mapGetMessagesResponse(srb.execute().actionGet());
        } catch (Exception e) {
            LOGGER.error("Failed to get messages for queue: {}", queue, e);
        }
        return null;
    }

    private List<Message> mapGetMessagesResponse(SearchResponse response) throws IOException {
        SearchHit[] hits = response.getHits().getHits();
        TypeFactory factory = TypeFactory.defaultInstance();
        MapType type = factory.constructMapType(HashMap.class, String.class, String.class);
        List<Message> messages = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            String source = hit.getSourceAsString();
            Map<String, String> mapSource = objectMapper.readValue(source, type);
            Message msg = new Message(mapSource.get("messageId"), mapSource.get("payload"), null);
            messages.add(msg);
        }
        return messages;
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {
        try {
            long startTime = Instant.now().toEpochMilli();
            byte[] doc = objectMapper.writeValueAsBytes(eventExecution);
            String id =
                    eventExecution.getName()
                            + "."
                            + eventExecution.getEvent()
                            + "."
                            + eventExecution.getMessageId()
                            + "."
                            + eventExecution.getId();
            String docType =
                    StringUtils.isBlank(docTypeOverride) ? EVENT_DOC_TYPE : docTypeOverride;
            UpdateRequest req = buildUpdateRequest(id, doc, eventIndexName, docType);
            indexObject(req, EVENT_DOC_TYPE);
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for indexing event execution: {}",
                    endTime - startTime,
                    eventExecution.getId());
            Monitors.recordESIndexTime("add_event_execution", EVENT_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
        } catch (Exception e) {
            LOGGER.error("Failed to index event execution: {}", eventExecution.getId(), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return CompletableFuture.runAsync(
                () -> addEventExecution(eventExecution), logExecutorService);
    }

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        try {
            BoolQueryBuilder fq = boolQueryBuilder("event='" + event + "'", "*");

            String docType =
                    StringUtils.isBlank(docTypeOverride) ? EVENT_DOC_TYPE : docTypeOverride;
            final SearchRequestBuilder srb =
                    elasticSearchClient
                            .prepareSearch(eventIndexPrefix + "*")
                            .setQuery(fq)
                            .setTypes(docType)
                            .addSort(SortBuilders.fieldSort("created").order(SortOrder.ASC));

            return mapEventExecutionsResponse(srb.execute().actionGet());
        } catch (Exception e) {
            LOGGER.error("Failed to get executions for event: {}", event, e);
        }
        return null;
    }

    private List<EventExecution> mapEventExecutionsResponse(SearchResponse response)
            throws IOException {
        SearchHit[] hits = response.getHits().getHits();
        List<EventExecution> executions = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            String source = hit.getSourceAsString();
            EventExecution tel = objectMapper.readValue(source, EventExecution.class);
            executions.add(tel);
        }
        return executions;
    }

    private void updateWithRetry(BulkRequestBuilderWrapper request, String docType) {
        try {
            long startTime = Instant.now().toEpochMilli();
            new RetryUtil<BulkResponse>()
                    .retryOnException(
                            () -> request.execute().actionGet(5, TimeUnit.SECONDS),
                            null,
                            BulkResponse::hasFailures,
                            RETRY_COUNT,
                            "Bulk Indexing " + docType,
                            "updateWithRetry");
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for indexing object of type: {}", endTime - startTime, docType);
            Monitors.recordESIndexTime("index_object", docType, endTime - startTime);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "index");
            LOGGER.error("Failed to index {} for requests", request.numberOfActions(), e);
        }
    }

    @Override
    public SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort) {
        return search(query, start, count, sort, freeText, WORKFLOW_DOC_TYPE);
    }

    @Override
    public long getWorkflowCount(String query, String freeText) {
        return count(query, freeText, WORKFLOW_DOC_TYPE);
    }

    @Override
    public SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort) {
        return search(query, start, count, sort, freeText, TASK_DOC_TYPE);
    }

    @Override
    public void removeWorkflow(String workflowId) {
        try {
            long startTime = Instant.now().toEpochMilli();
            DeleteRequest request =
                    new DeleteRequest(workflowIndexName, WORKFLOW_DOC_TYPE, workflowId);
            DeleteResponse response = elasticSearchClient.delete(request).actionGet();
            if (response.getResult() == DocWriteResponse.Result.DELETED) {
                LOGGER.error("Index removal failed - document not found by id: {}", workflowId);
            }
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for removing workflow: {}", endTime - startTime, workflowId);
            Monitors.recordESIndexTime("remove_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Throwable e) {
            LOGGER.error("Failed to remove workflow {} from index", workflowId, e);
            Monitors.error(CLASS_NAME, "remove");
        }
    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return CompletableFuture.runAsync(() -> removeWorkflow(workflowId), executorService);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        if (keys.length != values.length) {
            throw new ApplicationException(
                    ApplicationException.Code.INVALID_INPUT,
                    "Number of keys and values do not match");
        }

        long startTime = Instant.now().toEpochMilli();
        UpdateRequest request =
                new UpdateRequest(workflowIndexName, WORKFLOW_DOC_TYPE, workflowInstanceId);
        Map<String, Object> source =
                IntStream.range(0, keys.length)
                        .boxed()
                        .collect(Collectors.toMap(i -> keys[i], i -> values[i]));
        request.doc(source);
        LOGGER.debug(
                "Updating workflow {} in elasticsearch index: {}",
                workflowInstanceId,
                workflowIndexName);
        new RetryUtil<>()
                .retryOnException(
                        () -> elasticSearchClient.update(request).actionGet(),
                        null,
                        null,
                        RETRY_COUNT,
                        "Updating index for doc_type workflow",
                        "updateWorkflow");
        long endTime = Instant.now().toEpochMilli();
        LOGGER.debug(
                "Time taken {} for updating workflow: {}", endTime - startTime, workflowInstanceId);
        Monitors.recordESIndexTime("update_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
        Monitors.recordWorkerQueueSize(
                "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
    }

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(
            String workflowInstanceId, String[] keys, Object[] values) {
        return CompletableFuture.runAsync(
                () -> updateWorkflow(workflowInstanceId, keys, values), executorService);
    }

    @Override
    public String get(String workflowInstanceId, String fieldToGet) {
        String docType = StringUtils.isBlank(docTypeOverride) ? WORKFLOW_DOC_TYPE : docTypeOverride;
        GetRequest request =
                new GetRequest(workflowIndexName, docType, workflowInstanceId)
                        .fetchSourceContext(
                                new FetchSourceContext(
                                        true, new String[] {fieldToGet}, Strings.EMPTY_ARRAY));
        GetResponse response = elasticSearchClient.get(request).actionGet();

        if (response.isExists()) {
            Map<String, Object> sourceAsMap = response.getSourceAsMap();
            if (sourceAsMap.get(fieldToGet) != null) {
                return sourceAsMap.get(fieldToGet).toString();
            }
        }

        LOGGER.debug(
                "Unable to find Workflow: {} in ElasticSearch index: {}.",
                workflowInstanceId,
                workflowIndexName);
        return null;
    }

    private long count(String structuredQuery, String freeTextQuery, String docType) {
        try {
            docType = StringUtils.isBlank(docTypeOverride) ? docType : docTypeOverride;
            BoolQueryBuilder fq = boolQueryBuilder(structuredQuery, freeTextQuery);
            // The count api has been removed from the Java api, use the search api instead and set
            // size to 0.
            final SearchRequestBuilder srb =
                    elasticSearchClient
                            .prepareSearch(getIndexName(docType))
                            .setQuery(fq)
                            .setTypes(docType)
                            .storedFields("_id")
                            .setSize(0);
            SearchResponse response = srb.get();
            return response.getHits().getTotalHits();
        } catch (ParserException e) {
            throw new ApplicationException(
                    ApplicationException.Code.BACKEND_ERROR, e.getMessage(), e);
        }
    }

    private SearchResult<String> search(
            String structuredQuery,
            int start,
            int size,
            List<String> sortOptions,
            String freeTextQuery,
            String docType) {
        try {
            docType = StringUtils.isBlank(docTypeOverride) ? docType : docTypeOverride;
            BoolQueryBuilder fq = boolQueryBuilder(structuredQuery, freeTextQuery);
            final SearchRequestBuilder srb =
                    elasticSearchClient
                            .prepareSearch(getIndexName(docType))
                            .setQuery(fq)
                            .setTypes(docType)
                            .storedFields("_id")
                            .setFrom(start)
                            .setSize(size);

            addSortOptions(srb, sortOptions);

            return mapSearchResult(srb.get());
        } catch (ParserException e) {
            throw new ApplicationException(
                    ApplicationException.Code.BACKEND_ERROR, e.getMessage(), e);
        }
    }

    private void addSortOptions(SearchRequestBuilder srb, List<String> sortOptions) {
        if (sortOptions != null) {
            sortOptions.forEach(
                    sortOption -> {
                        SortOrder order = SortOrder.ASC;
                        String field = sortOption;
                        int indx = sortOption.indexOf(':');
                        // Can't be 0, need the field name at-least
                        if (indx > 0) {
                            field = sortOption.substring(0, indx);
                            order = SortOrder.valueOf(sortOption.substring(indx + 1));
                        }
                        srb.addSort(field, order);
                    });
        }
    }

    private SearchResult<String> mapSearchResult(SearchResponse response) {
        List<String> result = new LinkedList<>();
        response.getHits().forEach(hit -> result.add(hit.getId()));
        long count = response.getHits().getTotalHits();
        return new SearchResult<>(count, result);
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        QueryBuilder q =
                QueryBuilders.boolQuery()
                        .must(
                                QueryBuilders.rangeQuery("endTime")
                                        .lt(LocalDate.now().minusDays(archiveTtlDays).toString())
                                        .gte(
                                                LocalDate.now()
                                                        .minusDays(archiveTtlDays)
                                                        .minusDays(1)
                                                        .toString()))
                        .should(QueryBuilders.termQuery("status", "COMPLETED"))
                        .should(QueryBuilders.termQuery("status", "FAILED"))
                        .should(QueryBuilders.termQuery("status", "TIMED_OUT"))
                        .should(QueryBuilders.termQuery("status", "TERMINATED"))
                        .mustNot(QueryBuilders.existsQuery("archived"))
                        .minimumShouldMatch(1);
        String docType = StringUtils.isBlank(docTypeOverride) ? WORKFLOW_DOC_TYPE : docTypeOverride;
        SearchRequestBuilder s =
                elasticSearchClient
                        .prepareSearch(indexName)
                        .setTypes(docType)
                        .setQuery(q)
                        .setSize(1000);
        return extractSearchIds(s);
    }

    private UpdateRequest buildUpdateRequest(
            String id, byte[] doc, String indexName, String docType) {
        UpdateRequest req = new UpdateRequest(indexName, docType, id);
        req.doc(doc, XContentType.JSON);
        req.upsert(doc, XContentType.JSON);
        req.retryOnConflict(UPDATE_REQUEST_RETRY_COUNT);
        return req;
    }

    private List<String> extractSearchIds(SearchRequestBuilder s) {
        SearchResponse response = s.execute().actionGet();
        SearchHits hits = response.getHits();
        List<String> ids = new LinkedList<>();
        for (SearchHit hit : hits.getHits()) {
            ids.add(hit.getId());
        }
        return ids;
    }

    /**
     * Flush the buffers if bulk requests have not been indexed for the past {@link
     * ElasticSearchProperties#getAsyncBufferFlushTimeout()} seconds. This is to prevent data loss
     * in case the instance is terminated, while the buffer still holds documents to be indexed.
     */
    private void flushBulkRequests() {
        bulkRequests.entrySet().stream()
                .filter(
                        entry ->
                                (System.currentTimeMillis() - entry.getValue().getLastFlushTime())
                                        >= asyncBufferFlushTimeout)
                .filter(
                        entry ->
                                entry.getValue().getBulkRequestBuilder() != null
                                        && entry.getValue()
                                                        .getBulkRequestBuilder()
                                                        .numberOfActions()
                                                > 0)
                .forEach(
                        entry -> {
                            LOGGER.debug(
                                    "Flushing bulk request buffer for type {}, size: {}",
                                    entry.getKey(),
                                    entry.getValue().getBulkRequestBuilder().numberOfActions());
                            indexBulkRequest(entry.getKey());
                        });
    }

    private static class BulkRequests {

        private long lastFlushTime;
        private BulkRequestBuilderWrapper bulkRequestBuilder;

        public long getLastFlushTime() {
            return lastFlushTime;
        }

        public void setLastFlushTime(long lastFlushTime) {
            this.lastFlushTime = lastFlushTime;
        }

        public BulkRequestBuilderWrapper getBulkRequestBuilder() {
            return bulkRequestBuilder;
        }

        public void setBulkRequestBuilder(BulkRequestBuilder bulkRequestBuilder) {
            this.bulkRequestBuilder = new BulkRequestBuilderWrapper(bulkRequestBuilder);
        }

        BulkRequests(long lastFlushTime, BulkRequestBuilder bulkRequestBuilder) {
            this.lastFlushTime = lastFlushTime;
            this.bulkRequestBuilder = new BulkRequestBuilderWrapper(bulkRequestBuilder);
        }
    }
}
