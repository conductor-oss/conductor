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
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
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
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.es6.config.ElasticSearchProperties;
import com.netflix.conductor.es6.dao.query.parser.internal.ParserException;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;

@Trace
public class ElasticSearchRestDAOV6 extends ElasticSearchBaseDAO implements IndexDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchRestDAOV6.class);

    private static final int CORE_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 1L;

    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String LOG_DOC_TYPE = "task_log";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String MSG_DOC_TYPE = "message";

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMWW");

    private @interface HttpMethod {

        String GET = "GET";
        String POST = "POST";
        String PUT = "PUT";
        String HEAD = "HEAD";
    }

    private static final String className = ElasticSearchRestDAOV6.class.getSimpleName();

    private final String workflowIndexName;
    private final String taskIndexName;
    private final String eventIndexPrefix;
    private String eventIndexName;
    private final String messageIndexPrefix;
    private String messageIndexName;
    private String logIndexName;
    private final String logIndexPrefix;
    private final String docTypeOverride;

    private final String clusterHealthColor;
    private final ObjectMapper objectMapper;
    private final RestHighLevelClient elasticSearchClient;
    private final RestClient elasticSearchAdminClient;
    private final ExecutorService executorService;
    private final ExecutorService logExecutorService;
    private final ConcurrentHashMap<String, BulkRequests> bulkRequests;
    private final int indexBatchSize;
    private final long asyncBufferFlushTimeout;
    private final ElasticSearchProperties properties;

    private final RetryTemplate retryTemplate;

    static {
        SIMPLE_DATE_FORMAT.setTimeZone(GMT);
    }

    public ElasticSearchRestDAOV6(
            RestClientBuilder restClientBuilder,
            RetryTemplate retryTemplate,
            ElasticSearchProperties properties,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.elasticSearchAdminClient = restClientBuilder.build();
        this.elasticSearchClient = new RestHighLevelClient(restClientBuilder);
        this.clusterHealthColor = properties.getClusterHealthColor();
        this.bulkRequests = new ConcurrentHashMap<>();
        this.indexBatchSize = properties.getIndexBatchSize();
        this.asyncBufferFlushTimeout = properties.getAsyncBufferFlushTimeout().toMillis();
        this.properties = properties;

        this.indexPrefix = properties.getIndexPrefix();
        if (!properties.isAutoIndexManagementEnabled()
                && StringUtils.isNotBlank(properties.getDocumentTypeOverride())) {
            docTypeOverride = properties.getDocumentTypeOverride();
        } else {
            docTypeOverride = "";
        }

        this.workflowIndexName = getIndexName(WORKFLOW_DOC_TYPE);
        this.taskIndexName = getIndexName(TASK_DOC_TYPE);
        this.logIndexPrefix = this.indexPrefix + "_" + LOG_DOC_TYPE;
        this.messageIndexPrefix = this.indexPrefix + "_" + MSG_DOC_TYPE;
        this.eventIndexPrefix = this.indexPrefix + "_" + EVENT_DOC_TYPE;
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
                            LOGGER.warn(
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
                            LOGGER.warn(
                                    "Request {} to async log dao discarded in executor {}",
                                    runnable,
                                    executor);
                            Monitors.recordDiscardedIndexingCount("logQueue");
                        });

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::flushBulkRequests, 60, 30, TimeUnit.SECONDS);
        this.retryTemplate = retryTemplate;
    }

    @PreDestroy
    private void shutdown() {
        LOGGER.info("Gracefully shutdown executor service");
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

    private void createIndexesTemplates() {
        try {
            initIndexesTemplates();
            updateIndexesNames();
            Executors.newScheduledThreadPool(1)
                    .scheduleAtFixedRate(this::updateIndexesNames, 0, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            LOGGER.error("Error creating index templates!", e);
        }
    }

    private void initIndexesTemplates() {
        initIndexTemplate(LOG_DOC_TYPE);
        initIndexTemplate(EVENT_DOC_TYPE);
        initIndexTemplate(MSG_DOC_TYPE);
    }

    /** Initializes the index with the required templates and mappings. */
    private void initIndexTemplate(String type) {
        String template = "template_" + type;
        try {
            if (doesResourceNotExist("/_template/" + template)) {
                LOGGER.info("Creating the index template '" + template + "'");
                InputStream stream =
                        ElasticSearchDAOV6.class.getResourceAsStream("/" + template + ".json");
                byte[] templateSource = IOUtils.toByteArray(stream);

                HttpEntity entity =
                        new NByteArrayEntity(templateSource, ContentType.APPLICATION_JSON);
                elasticSearchAdminClient.performRequest(
                        HttpMethod.PUT, "/_template/" + template, Collections.emptyMap(), entity);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to init " + template, e);
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
        try {
            addIndex(indexName);
            return indexName;
        } catch (IOException e) {
            LOGGER.error("Failed to update log index name: {}", indexName, e);
            throw new NonTransientException("Failed to update log index name: " + indexName, e);
        }
    }

    private void createWorkflowIndex() {
        String indexName = getIndexName(WORKFLOW_DOC_TYPE);
        try {
            addIndex(indexName);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize index '{}'", indexName, e);
        }
        try {
            addMappingToIndex(indexName, WORKFLOW_DOC_TYPE, "/mappings_docType_workflow.json");
        } catch (IOException e) {
            LOGGER.error("Failed to add {} mapping", WORKFLOW_DOC_TYPE);
        }
    }

    private void createTaskIndex() {
        String indexName = getIndexName(TASK_DOC_TYPE);
        try {
            addIndex(indexName);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize index '{}'", indexName, e);
        }
        try {
            addMappingToIndex(indexName, TASK_DOC_TYPE, "/mappings_docType_task.json");
        } catch (IOException e) {
            LOGGER.error("Failed to add {} mapping", TASK_DOC_TYPE);
        }
    }

    /**
     * Waits for the ES cluster to become green.
     *
     * @throws Exception If there is an issue connecting with the ES cluster.
     */
    private void waitForHealthyCluster() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("wait_for_status", this.clusterHealthColor);
        params.put("timeout", "30s");

        elasticSearchAdminClient.performRequest("GET", "/_cluster/health", params);
    }

    /**
     * Adds an index to elasticsearch if it does not exist.
     *
     * @param index The name of the index to create.
     * @throws IOException If an error occurred during requests to ES.
     */
    private void addIndex(final String index) throws IOException {

        LOGGER.info("Adding index '{}'...", index);

        String resourcePath = "/" + index;

        if (doesResourceNotExist(resourcePath)) {

            try {
                ObjectNode setting = objectMapper.createObjectNode();
                ObjectNode indexSetting = objectMapper.createObjectNode();

                indexSetting.put("number_of_shards", properties.getIndexShardCount());
                indexSetting.put("number_of_replicas", properties.getIndexReplicasCount());

                setting.set("index", indexSetting);

                elasticSearchAdminClient.performRequest(
                        HttpMethod.PUT,
                        resourcePath,
                        Collections.emptyMap(),
                        new NStringEntity(setting.toString(), ContentType.APPLICATION_JSON));
                LOGGER.info("Added '{}' index", index);
            } catch (ResponseException e) {

                boolean errorCreatingIndex = true;

                Response errorResponse = e.getResponse();
                if (errorResponse.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                    JsonNode root =
                            objectMapper.readTree(EntityUtils.toString(errorResponse.getEntity()));
                    String errorCode = root.get("error").get("type").asText();
                    if ("index_already_exists_exception".equals(errorCode)) {
                        errorCreatingIndex = false;
                    }
                }

                if (errorCreatingIndex) {
                    throw e;
                }
            }
        } else {
            LOGGER.info("Index '{}' already exists", index);
        }
    }

    /**
     * Adds a mapping type to an index if it does not exist.
     *
     * @param index The name of the index.
     * @param mappingType The name of the mapping type.
     * @param mappingFilename The name of the mapping file to use to add the mapping if it does not
     *     exist.
     * @throws IOException If an error occurred during requests to ES.
     */
    private void addMappingToIndex(
            final String index, final String mappingType, final String mappingFilename)
            throws IOException {

        LOGGER.info("Adding '{}' mapping to index '{}'...", mappingType, index);

        String resourcePath = "/" + index + "/_mapping/" + mappingType;

        if (doesResourceNotExist(resourcePath)) {
            HttpEntity entity =
                    new NByteArrayEntity(
                            loadTypeMappingSource(mappingFilename).getBytes(),
                            ContentType.APPLICATION_JSON);
            elasticSearchAdminClient.performRequest(
                    HttpMethod.PUT, resourcePath, Collections.emptyMap(), entity);
            LOGGER.info("Added '{}' mapping", mappingType);
        } else {
            LOGGER.info("Mapping '{}' already exists", mappingType);
        }
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
        Response response = elasticSearchAdminClient.performRequest(HttpMethod.HEAD, resourcePath);
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
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

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {
        try {
            long startTime = Instant.now().toEpochMilli();
            String workflowId = workflow.getWorkflowId();
            byte[] docBytes = objectMapper.writeValueAsBytes(workflow);
            String docType =
                    StringUtils.isBlank(docTypeOverride) ? WORKFLOW_DOC_TYPE : docTypeOverride;

            IndexRequest request = new IndexRequest(workflowIndexName, docType, workflowId);
            request.source(docBytes, XContentType.JSON);
            elasticSearchClient.index(request, RequestOptions.DEFAULT);
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for indexing workflow: {}", endTime - startTime, workflowId);
            Monitors.recordESIndexTime("index_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            Monitors.error(className, "indexWorkflow");
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
            String taskId = task.getTaskId();
            String docType = StringUtils.isBlank(docTypeOverride) ? TASK_DOC_TYPE : docTypeOverride;

            indexObject(taskIndexName, docType, taskId, task);
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for  indexing task:{} in workflow: {}",
                    endTime - startTime,
                    taskId,
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

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> taskExecLogs) {
        if (taskExecLogs.isEmpty()) {
            return;
        }

        long startTime = Instant.now().toEpochMilli();
        BulkRequest bulkRequest = new BulkRequest();
        for (TaskExecLog log : taskExecLogs) {

            byte[] docBytes;
            try {
                docBytes = objectMapper.writeValueAsBytes(log);
            } catch (JsonProcessingException e) {
                LOGGER.error("Failed to convert task log to JSON for task {}", log.getTaskId());
                continue;
            }

            String docType = StringUtils.isBlank(docTypeOverride) ? LOG_DOC_TYPE : docTypeOverride;
            IndexRequest request = new IndexRequest(logIndexName, docType);
            request.source(docBytes, XContentType.JSON);
            bulkRequest.add(request);
        }

        try {
            elasticSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
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

            // Create the searchObjectIdsViaExpression source
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.sort(new FieldSortBuilder("createdTime").order(SortOrder.ASC));
            searchSourceBuilder.size(properties.getTaskLogResultLimit());

            // Generate the actual request to send to ES.
            String docType = StringUtils.isBlank(docTypeOverride) ? LOG_DOC_TYPE : docTypeOverride;
            SearchRequest searchRequest = new SearchRequest(logIndexPrefix + "*");
            searchRequest.types(docType);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest);

            return mapTaskExecLogsResponse(response);
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
    public List<Message> getMessages(String queue) {
        try {
            BoolQueryBuilder query = boolQueryBuilder("queue='" + queue + "'", "*");

            // Create the searchObjectIdsViaExpression source
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.sort(new FieldSortBuilder("created").order(SortOrder.ASC));

            // Generate the actual request to send to ES.
            String docType = StringUtils.isBlank(docTypeOverride) ? MSG_DOC_TYPE : docTypeOverride;
            SearchRequest searchRequest = new SearchRequest(messageIndexPrefix + "*");
            searchRequest.types(docType);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest);
            return mapGetMessagesResponse(response);
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
    public List<EventExecution> getEventExecutions(String event) {
        try {
            BoolQueryBuilder query = boolQueryBuilder("event='" + event + "'", "*");

            // Create the searchObjectIdsViaExpression source
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.sort(new FieldSortBuilder("created").order(SortOrder.ASC));

            // Generate the actual request to send to ES.
            String docType =
                    StringUtils.isBlank(docTypeOverride) ? EVENT_DOC_TYPE : docTypeOverride;
            SearchRequest searchRequest = new SearchRequest(eventIndexPrefix + "*");
            searchRequest.types(docType);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest);

            return mapEventExecutionsResponse(response);
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
            indexObject(messageIndexName, docType, doc);
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

            String docType =
                    StringUtils.isBlank(docTypeOverride) ? EVENT_DOC_TYPE : docTypeOverride;
            indexObject(eventIndexName, docType, id, eventExecution);
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
    public SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectsViaExpression(
                    query, start, count, sort, freeText, WORKFLOW_DOC_TYPE, true, String.class);
        } catch (Exception e) {
            throw new TransientException(e.getMessage(), e);
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

    @Override
    public SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectsViaExpression(
                    query, start, count, sort, freeText, TASK_DOC_TYPE, true, String.class);
        } catch (Exception e) {
            throw new TransientException(e.getMessage(), e);
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
        String docType = StringUtils.isBlank(docTypeOverride) ? WORKFLOW_DOC_TYPE : docTypeOverride;
        DeleteRequest request = new DeleteRequest(workflowIndexName, docType, workflowId);

        try {
            DeleteResponse response = elasticSearchClient.delete(request);

            if (response.getResult() == DocWriteResponse.Result.NOT_FOUND) {
                LOGGER.error("Index removal failed - document not found by id: {}", workflowId);
            }
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for removing workflow: {}", endTime - startTime, workflowId);
            Monitors.recordESIndexTime("remove_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (IOException e) {
            LOGGER.error("Failed to remove workflow {} from index", workflowId, e);
            Monitors.error(className, "remove");
        }
    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return CompletableFuture.runAsync(() -> removeWorkflow(workflowId), executorService);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        try {
            if (keys.length != values.length) {
                throw new IllegalArgumentException("Number of keys and values do not match");
            }

            long startTime = Instant.now().toEpochMilli();
            String docType =
                    StringUtils.isBlank(docTypeOverride) ? WORKFLOW_DOC_TYPE : docTypeOverride;
            UpdateRequest request =
                    new UpdateRequest(workflowIndexName, docType, workflowInstanceId);
            Map<String, Object> source =
                    IntStream.range(0, keys.length)
                            .boxed()
                            .collect(Collectors.toMap(i -> keys[i], i -> values[i]));
            request.doc(source);

            LOGGER.debug("Updating workflow {} with {}", workflowInstanceId, source);
            elasticSearchClient.update(request, RequestOptions.DEFAULT);
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for updating workflow: {}",
                    endTime - startTime,
                    workflowInstanceId);
            Monitors.recordESIndexTime("update_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            LOGGER.error("Failed to update workflow {}", workflowInstanceId, e);
            Monitors.error(className, "update");
        }
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
        GetRequest request = new GetRequest(workflowIndexName, docType, workflowInstanceId);

        GetResponse response;
        try {
            response = elasticSearchClient.get(request);
        } catch (IOException e) {
            LOGGER.error(
                    "Unable to get Workflow: {} from ElasticSearch index: {}",
                    workflowInstanceId,
                    workflowIndexName,
                    e);
            return null;
        }

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
        QueryBuilder queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjects(
                getIndexName(docType),
                queryBuilder,
                start,
                size,
                sortOptions,
                docType,
                idOnly,
                clazz);
    }

    private SearchResult<String> searchObjectIds(
            String indexName, QueryBuilder queryBuilder, int start, int size, String docType)
            throws IOException {
        return searchObjects(
                indexName, queryBuilder, start, size, null, docType, true, String.class);
    }

    /**
     * Tries to find objects for a given query in an index.
     *
     * @param indexName The name of the index.
     * @param queryBuilder The query to use for searching.
     * @param start The start to use.
     * @param size The total return size.
     * @param sortOptions A list of string options to sort in the form VALUE:ORDER; where ORDER is
     *     optional and can be either ASC OR DESC.
     * @param docType The document type to searchObjectIdsViaExpression for.
     * @return The SearchResults which includes the count and objects that were found.
     * @throws IOException If we cannot communicate with ES.
     */
    private <T> SearchResult<T> searchObjects(
            String indexName,
            QueryBuilder queryBuilder,
            int start,
            int size,
            List<String> sortOptions,
            String docType,
            boolean idOnly,
            Class<T> clazz)
            throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        searchSourceBuilder.from(start);
        searchSourceBuilder.size(size);
        if (idOnly) {
            searchSourceBuilder.fetchSource(false);
        }

        if (sortOptions != null && !sortOptions.isEmpty()) {

            for (String sortOption : sortOptions) {
                SortOrder order = SortOrder.ASC;
                String field = sortOption;
                int index = sortOption.indexOf(":");
                if (index > 0) {
                    field = sortOption.substring(0, index);
                    order = SortOrder.valueOf(sortOption.substring(index + 1));
                }
                searchSourceBuilder.sort(new FieldSortBuilder(field).order(order));
            }
        }

        // Generate the actual request to send to ES.
        docType = StringUtils.isBlank(docTypeOverride) ? docType : docTypeOverride;
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.types(docType);
        searchRequest.source(searchSourceBuilder);

        SearchResponse response = elasticSearchClient.search(searchRequest);
        return mapSearchResult(response, idOnly, clazz);
    }

    private <T> SearchResult<T> mapSearchResult(
            SearchResponse response, boolean idOnly, Class<T> clazz) {
        SearchHits searchHits = response.getHits();
        long count = searchHits.getTotalHits();
        List<T> result;
        if (idOnly) {
            result =
                    Arrays.stream(searchHits.getHits())
                            .map(hit -> clazz.cast(hit.getId()))
                            .collect(Collectors.toList());
        } else {
            result =
                    Arrays.stream(searchHits.getHits())
                            .map(
                                    hit -> {
                                        try {
                                            return objectMapper.readValue(
                                                    hit.getSourceAsString(), clazz);
                                        } catch (JsonProcessingException e) {
                                            LOGGER.error(
                                                    "Failed to de-serialize elasticsearch from source: {}",
                                                    hit.getSourceAsString(),
                                                    e);
                                        }
                                        return null;
                                    })
                            .collect(Collectors.toList());
        }
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

        SearchResult<String> workflowIds;
        try {
            workflowIds = searchObjectIds(indexName, q, 0, 1000, WORKFLOW_DOC_TYPE);
        } catch (IOException e) {
            LOGGER.error("Unable to communicate with ES to find archivable workflows", e);
            return Collections.emptyList();
        }

        return workflowIds.getResults();
    }

    @Override
    public long getWorkflowCount(String query, String freeText) {
        try {
            return getObjectCounts(query, freeText, WORKFLOW_DOC_TYPE);
        } catch (Exception e) {
            throw new TransientException(e.getMessage(), e);
        }
    }

    private long getObjectCounts(String structuredQuery, String freeTextQuery, String docType)
            throws ParserException, IOException {
        QueryBuilder queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(queryBuilder);

        String indexName = getIndexName(docType);
        CountRequest countRequest = new CountRequest(new String[] {indexName}, sourceBuilder);
        CountResponse countResponse =
                elasticSearchClient.count(countRequest, RequestOptions.DEFAULT);
        return countResponse.getCount();
    }

    private void indexObject(final String index, final String docType, final Object doc) {
        indexObject(index, docType, null, doc);
    }

    private void indexObject(
            final String index, final String docType, final String docId, final Object doc) {

        byte[] docBytes;
        try {
            docBytes = objectMapper.writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to convert {} '{}' to byte string", docType, docId);
            return;
        }

        IndexRequest request = new IndexRequest(index, docType, docId);
        request.source(docBytes, XContentType.JSON);

        if (bulkRequests.get(docType) == null) {
            bulkRequests.put(
                    docType, new BulkRequests(System.currentTimeMillis(), new BulkRequest()));
        }

        bulkRequests.get(docType).getBulkRequest().add(request);
        if (bulkRequests.get(docType).getBulkRequest().numberOfActions() >= this.indexBatchSize) {
            indexBulkRequest(docType);
        }
    }

    private synchronized void indexBulkRequest(String docType) {
        if (bulkRequests.get(docType).getBulkRequest() != null
                && bulkRequests.get(docType).getBulkRequest().numberOfActions() > 0) {
            synchronized (bulkRequests.get(docType).getBulkRequest()) {
                indexWithRetry(
                        bulkRequests.get(docType).getBulkRequest().get(),
                        "Bulk Indexing " + docType,
                        docType);
                bulkRequests.put(
                        docType, new BulkRequests(System.currentTimeMillis(), new BulkRequest()));
            }
        }
    }

    /**
     * Performs an index operation with a retry.
     *
     * @param request The index request that we want to perform.
     * @param operationDescription The type of operation that we are performing.
     */
    private void indexWithRetry(
            final BulkRequest request, final String operationDescription, String docType) {
        try {
            long startTime = Instant.now().toEpochMilli();
            retryTemplate.execute(
                    context -> elasticSearchClient.bulk(request, RequestOptions.DEFAULT));
            long endTime = Instant.now().toEpochMilli();
            LOGGER.debug(
                    "Time taken {} for indexing object of type: {}", endTime - startTime, docType);
            Monitors.recordESIndexTime("index_object", docType, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
            Monitors.recordWorkerQueueSize(
                    "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
        } catch (Exception e) {
            Monitors.error(className, "index");
            LOGGER.error("Failed to index {} for request type: {}", request, docType, e);
        }
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
                                entry.getValue().getBulkRequest() != null
                                        && entry.getValue().getBulkRequest().numberOfActions() > 0)
                .forEach(
                        entry -> {
                            LOGGER.debug(
                                    "Flushing bulk request buffer for type {}, size: {}",
                                    entry.getKey(),
                                    entry.getValue().getBulkRequest().numberOfActions());
                            indexBulkRequest(entry.getKey());
                        });
    }

    private static class BulkRequests {

        private final long lastFlushTime;
        private final BulkRequestWrapper bulkRequest;

        long getLastFlushTime() {
            return lastFlushTime;
        }

        BulkRequestWrapper getBulkRequest() {
            return bulkRequest;
        }

        BulkRequests(long lastFlushTime, BulkRequest bulkRequest) {
            this.lastFlushTime = lastFlushTime;
            this.bulkRequest = new BulkRequestWrapper(bulkRequest);
        }
    }
}
