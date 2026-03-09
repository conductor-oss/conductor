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
package org.conductoross.conductor.os3.dao.index;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.conductoross.conductor.os3.config.OpenSearchProperties;
import org.conductoross.conductor.os3.dao.query.parser.internal.ParserException;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
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
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Trace
public class OpenSearchRestDAO extends OpenSearchBaseDAO implements IndexDAO {

    private static final Logger logger = LoggerFactory.getLogger(OpenSearchRestDAO.class);

    private static final String CLASS_NAME = OpenSearchRestDAO.class.getSimpleName();

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

    private static final String className = OpenSearchRestDAO.class.getSimpleName();

    private final String workflowIndexName;
    private final String taskIndexName;
    private final String eventIndexPrefix;
    private String eventIndexName;
    private final String messageIndexPrefix;
    private String messageIndexName;
    private String logIndexName;
    private final String logIndexPrefix;

    private final String clusterHealthColor;
    private final OpenSearchClient openSearchClient;
    private final RestClient openSearchAdminClient;
    private final ExecutorService executorService;
    private final ExecutorService logExecutorService;
    private final ConcurrentHashMap<String, BulkRequests> bulkRequests;
    private final int indexBatchSize;
    private final int asyncBufferFlushTimeout;
    private final OpenSearchProperties properties;
    private final RetryTemplate retryTemplate;

    static {
        SIMPLE_DATE_FORMAT.setTimeZone(GMT);
    }

    public OpenSearchRestDAO(
            RestClient restClient,
            OpenSearchClient openSearchClient,
            RetryTemplate retryTemplate,
            OpenSearchProperties properties,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.openSearchClient = openSearchClient;
        this.openSearchAdminClient = restClient;
        this.clusterHealthColor = properties.getClusterHealthColor();
        this.bulkRequests = new ConcurrentHashMap<>();
        this.indexBatchSize = properties.getIndexBatchSize();
        this.asyncBufferFlushTimeout = (int) properties.getAsyncBufferFlushTimeout().getSeconds();
        this.properties = properties;

        this.indexPrefix = properties.getIndexPrefix();

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

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::flushBulkRequests, 60, 30, TimeUnit.SECONDS);
        this.retryTemplate = retryTemplate;
    }

    @PreDestroy
    private void shutdown() {
        logger.info("Gracefully shutdown executor service");
        shutdownExecutorService(logExecutorService);
        shutdownExecutorService(executorService);
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
            logger.error("Error creating index templates!", e);
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
            if (doesResourceNotExist("/_index_template/" + template)) {
                logger.info("Creating the index template '" + template + "'");
                InputStream stream =
                        OpenSearchRestDAO.class.getResourceAsStream("/" + template + ".json");
                byte[] templateSource = IOUtils.toByteArray(stream);

                HttpEntity entity =
                        new ByteArrayEntity(templateSource, ContentType.APPLICATION_JSON);
                Request request = new Request(HttpMethod.PUT, "/_index_template/" + template);
                request.setEntity(entity);
                String test =
                        IOUtils.toString(
                                openSearchAdminClient
                                        .performRequest(request)
                                        .getEntity()
                                        .getContent());
            }
        } catch (Exception e) {
            logger.error("Failed to init " + template, e);
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
            logger.error("Failed to update log index name: {}", indexName, e);
            throw new NonTransientException(e.getMessage(), e);
        }
    }

    private void createWorkflowIndex() {
        String indexName = getIndexName(WORKFLOW_DOC_TYPE);
        try {
            addIndex(indexName, "/mappings_docType_workflow.json");
        } catch (IOException e) {
            logger.error("Failed to initialize index '{}'", indexName, e);
        }
    }

    private void createTaskIndex() {
        String indexName = getIndexName(TASK_DOC_TYPE);
        try {
            addIndex(indexName, "/mappings_docType_task.json");
        } catch (IOException e) {
            logger.error("Failed to initialize index '{}'", indexName, e);
        }
    }

    /**
     * Waits for the ES cluster to become green.
     *
     * @throws Exception If there is an issue connecting with the ES cluster.
     */
    private void waitForHealthyCluster() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("timeout", "30s");
        params.put("wait_for_status", this.clusterHealthColor);
        Request request = new Request("GET", "/_cluster/health");
        request.addParameters(params);
        openSearchAdminClient.performRequest(request);
    }

    /**
     * Adds an index to opensearch if it does not exist.
     *
     * @param index The name of the index to create.
     * @param mappingFilename Index mapping filename
     * @throws IOException If an error occurred during requests to ES.
     */
    private void addIndex(String index, final String mappingFilename) throws IOException {
        logger.info("Adding index '{}'...", index);
        String resourcePath = "/" + index;
        if (doesResourceNotExist(resourcePath)) {
            try {
                ObjectNode setting = objectMapper.createObjectNode();
                ObjectNode indexSetting = objectMapper.createObjectNode();
                ObjectNode root = objectMapper.createObjectNode();
                indexSetting.put("number_of_shards", properties.getIndexShardCount());
                indexSetting.put("number_of_replicas", properties.getIndexReplicasCount());
                JsonNode mappingNodeValue =
                        objectMapper.readTree(loadTypeMappingSource(mappingFilename));
                root.set("settings", indexSetting);
                root.set("mappings", mappingNodeValue);
                Request request = new Request(HttpMethod.PUT, resourcePath);
                request.setEntity(
                        new StringEntity(
                                objectMapper.writeValueAsString(root),
                                ContentType.APPLICATION_JSON));
                openSearchAdminClient.performRequest(request);
                logger.info("Added '{}' index", index);
            } catch (ResponseException e) {

                boolean errorCreatingIndex = true;

                Response errorResponse = e.getResponse();
                if (errorResponse.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                    try {
                        JsonNode root =
                                objectMapper.readTree(
                                        EntityUtils.toString(errorResponse.getEntity()));
                        String errorCode = root.get("error").get("type").asText();
                        if ("index_already_exists_exception".equals(errorCode)) {
                            errorCreatingIndex = false;
                        }
                    } catch (org.apache.hc.core5.http.ParseException pe) {
                        logger.warn("Failed to parse error response", pe);
                    }
                }

                if (errorCreatingIndex) {
                    throw e;
                }
            }
        } else {
            logger.info("Index '{}' already exists", index);
        }
    }

    /**
     * Adds an index to opensearch if it does not exist.
     *
     * @param index The name of the index to create.
     * @throws IOException If an error occurred during requests to ES.
     */
    private void addIndex(final String index) throws IOException {

        logger.info("Adding index '{}'...", index);

        String resourcePath = "/" + index;

        if (doesResourceNotExist(resourcePath)) {

            try {
                ObjectNode setting = objectMapper.createObjectNode();
                ObjectNode indexSetting = objectMapper.createObjectNode();

                indexSetting.put("number_of_shards", properties.getIndexShardCount());
                indexSetting.put("number_of_replicas", properties.getIndexReplicasCount());

                setting.set("settings", indexSetting);

                Request request = new Request(HttpMethod.PUT, resourcePath);
                request.setEntity(
                        new StringEntity(setting.toString(), ContentType.APPLICATION_JSON));
                openSearchAdminClient.performRequest(request);
                logger.info("Added '{}' index", index);
            } catch (ResponseException e) {

                boolean errorCreatingIndex = true;

                Response errorResponse = e.getResponse();
                if (errorResponse.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                    try {
                        JsonNode root =
                                objectMapper.readTree(
                                        EntityUtils.toString(errorResponse.getEntity()));
                        String errorCode = root.get("error").get("type").asText();
                        if ("index_already_exists_exception".equals(errorCode)) {
                            errorCreatingIndex = false;
                        }
                    } catch (org.apache.hc.core5.http.ParseException pe) {
                        logger.warn("Failed to parse error response", pe);
                    }
                }

                if (errorCreatingIndex) {
                    throw e;
                }
            }
        } else {
            logger.info("Index '{}' already exists", index);
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

        logger.info("Adding '{}' mapping to index '{}'...", mappingType, index);

        String resourcePath = "/" + index + "/_mapping";

        if (doesResourceNotExist(resourcePath)) {
            HttpEntity entity =
                    new ByteArrayEntity(
                            loadTypeMappingSource(mappingFilename).getBytes(),
                            ContentType.APPLICATION_JSON);
            Request request = new Request(HttpMethod.PUT, resourcePath);
            request.setEntity(entity);
            openSearchAdminClient.performRequest(request);
            logger.info("Added '{}' mapping", mappingType);
        } else {
            logger.info("Mapping '{}' already exists", mappingType);
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
        Request request = new Request(HttpMethod.HEAD, resourcePath);
        Response response = openSearchAdminClient.performRequest(request);
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

            IndexRequest<WorkflowSummary> request =
                    new IndexRequest.Builder<WorkflowSummary>()
                            .index(workflowIndexName)
                            .id(workflowId)
                            .document(workflow)
                            .build();
            openSearchClient.index(request);
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for indexing workflow: {}", endTime - startTime, workflowId);
            Monitors.recordESIndexTime("index_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            Monitors.error(className, "indexWorkflow");
            logger.error("Failed to index workflow: {}", workflow.getWorkflowId(), e);
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

            indexObject(taskIndexName, TASK_DOC_TYPE, taskId, task);
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
            BulkOperation operation =
                    BulkOperation.of(b -> b.index(idx -> idx.index(logIndexName).document(log)));
            operations.add(operation);
        }

        try {
            BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
            openSearchClient.bulk(bulkRequest);
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
            Query query = boolQuery("taskId='" + taskId + "'", "*");

            SearchRequest searchRequest =
                    new SearchRequest.Builder()
                            .index(logIndexPrefix + "*")
                            .query(query)
                            .sort(s -> s.field(f -> f.field("createdTime").order(SortOrder.Asc)))
                            .size(properties.getTaskLogResultLimit())
                            .build();

            SearchResponse<TaskExecLog> response =
                    openSearchClient.search(searchRequest, TaskExecLog.class);

            return mapTaskExecLogsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get task execution logs for task: {}", taskId, e);
        }
        return null;
    }

    private List<TaskExecLog> mapTaskExecLogsResponse(SearchResponse<TaskExecLog> response)
            throws IOException {
        List<Hit<TaskExecLog>> hits = response.hits().hits();
        List<TaskExecLog> logs = new ArrayList<>(hits.size());
        for (Hit<TaskExecLog> hit : hits) {
            TaskExecLog tel = hit.source();
            if (tel != null) {
                logs.add(tel);
            }
        }
        return logs;
    }

    @Override
    public List<Message> getMessages(String queue) {
        try {
            Query query = boolQuery("queue='" + queue + "'", "*");

            SearchRequest searchRequest =
                    new SearchRequest.Builder()
                            .index(messageIndexPrefix + "*")
                            .query(query)
                            .sort(s -> s.field(f -> f.field("created").order(SortOrder.Asc)))
                            .build();

            SearchResponse<com.fasterxml.jackson.databind.node.ObjectNode> response =
                    openSearchClient.search(
                            searchRequest, com.fasterxml.jackson.databind.node.ObjectNode.class);
            return mapGetMessagesResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get messages for queue: {}", queue, e);
        }
        return null;
    }

    private List<Message> mapGetMessagesResponse(
            SearchResponse<com.fasterxml.jackson.databind.node.ObjectNode> response)
            throws IOException {
        List<Hit<com.fasterxml.jackson.databind.node.ObjectNode>> hits = response.hits().hits();
        List<Message> messages = new ArrayList<>(hits.size());
        for (Hit<com.fasterxml.jackson.databind.node.ObjectNode> hit : hits) {
            com.fasterxml.jackson.databind.node.ObjectNode source = hit.source();
            if (source != null) {
                String messageId =
                        source.get("messageId") != null ? source.get("messageId").asText() : null;
                String payload =
                        source.get("payload") != null ? source.get("payload").asText() : null;
                Message msg = new Message(messageId, payload, null);
                messages.add(msg);
            }
        }
        return messages;
    }

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        try {
            Query query = boolQuery("event='" + event + "'", "*");

            SearchRequest searchRequest =
                    new SearchRequest.Builder()
                            .index(eventIndexPrefix + "*")
                            .query(query)
                            .sort(s -> s.field(f -> f.field("created").order(SortOrder.Asc)))
                            .build();

            SearchResponse<EventExecution> response =
                    openSearchClient.search(searchRequest, EventExecution.class);

            return mapEventExecutionsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get executions for event: {}", event, e);
        }
        return null;
    }

    private List<EventExecution> mapEventExecutionsResponse(SearchResponse<EventExecution> response)
            throws IOException {
        List<Hit<EventExecution>> hits = response.hits().hits();
        List<EventExecution> executions = new ArrayList<>(hits.size());
        for (Hit<EventExecution> hit : hits) {
            EventExecution exec = hit.source();
            if (exec != null) {
                executions.add(exec);
            }
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

            indexObject(eventIndexName, EVENT_DOC_TYPE, id, eventExecution);
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
        Query query = boolQuery(structuredQuery, freeTextQuery);
        return searchObjects(getIndexName(docType), query, start, size, sortOptions, idOnly, clazz);
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
        DeleteRequest request =
                new DeleteRequest.Builder().index(workflowIndexName).id(workflowId).build();

        try {
            DeleteResponse response = openSearchClient.delete(request);

            if (response.result() == org.opensearch.client.opensearch._types.Result.NotFound) {
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
        try {
            if (keys.length != values.length) {
                throw new NonTransientException("Number of keys and values do not match");
            }

            long startTime = Instant.now().toEpochMilli();
            Map<String, Object> source =
                    IntStream.range(0, keys.length)
                            .boxed()
                            .collect(Collectors.toMap(i -> keys[i], i -> values[i]));

            UpdateRequest<Object, Object> request =
                    new UpdateRequest.Builder<Object, Object>()
                            .index(workflowIndexName)
                            .id(workflowInstanceId)
                            .doc(source)
                            .build();

            logger.debug("Updating workflow {} with {}", workflowInstanceId, source);
            openSearchClient.update(request, Object.class);
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for updating workflow: {}",
                    endTime - startTime,
                    workflowInstanceId);
            Monitors.recordESIndexTime("update_workflow", WORKFLOW_DOC_TYPE, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            logger.error("Failed to update workflow {}", workflowInstanceId, e);
            Monitors.error(className, "update");
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

        DeleteRequest request = new DeleteRequest.Builder().index(taskIndexName).id(taskId).build();

        try {
            DeleteResponse response = openSearchClient.delete(request);

            if (response.result() != org.opensearch.client.opensearch._types.Result.Deleted) {
                logger.error("Index removal failed - task not found by id: {}", workflowId);
                Monitors.error(className, "removeTask");
                return;
            }
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

    @Override
    public CompletableFuture<Void> asyncRemoveTask(String workflowId, String taskId) {
        return CompletableFuture.runAsync(() -> removeTask(workflowId, taskId), executorService);
    }

    @Override
    public void updateTask(String workflowId, String taskId, String[] keys, Object[] values) {
        try {
            if (keys.length != values.length) {
                throw new IllegalArgumentException("Number of keys and values do not match");
            }

            long startTime = Instant.now().toEpochMilli();
            Map<String, Object> source =
                    IntStream.range(0, keys.length)
                            .boxed()
                            .collect(Collectors.toMap(i -> keys[i], i -> values[i]));

            UpdateRequest<Object, Object> request =
                    new UpdateRequest.Builder<Object, Object>()
                            .index(taskIndexName)
                            .id(taskId)
                            .doc(source)
                            .build();

            logger.debug("Updating task: {} of workflow: {} with {}", taskId, workflowId, source);
            openSearchClient.update(request, Object.class);
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for updating task: {} of workflow: {}",
                    endTime - startTime,
                    taskId,
                    workflowId);
            Monitors.recordESIndexTime("update_task", "", endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
        } catch (Exception e) {
            logger.error("Failed to update task: {} of workflow: {}", taskId, workflowId, e);
            Monitors.error(className, "update");
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
        GetRequest request =
                new GetRequest.Builder().index(workflowIndexName).id(workflowInstanceId).build();
        GetResponse<com.fasterxml.jackson.databind.node.ObjectNode> response;
        try {
            response =
                    openSearchClient.get(
                            request, com.fasterxml.jackson.databind.node.ObjectNode.class);
        } catch (IOException e) {
            logger.error(
                    "Unable to get Workflow: {} from openSearch index: {}",
                    workflowInstanceId,
                    workflowIndexName,
                    e);
            return null;
        }

        if (response.found()) {
            com.fasterxml.jackson.databind.node.ObjectNode source = response.source();
            if (source != null && source.has(fieldToGet)) {
                return source.get(fieldToGet).asText();
            }
        }

        logger.debug(
                "Unable to find Workflow: {} in openSearch index: {}.",
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
        Query query = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjectIds(getIndexName(docType), query, start, size, sortOptions);
    }

    private <T> SearchResult<T> searchObjectIdsViaExpression(
            String structuredQuery,
            int start,
            int size,
            List<String> sortOptions,
            String freeTextQuery,
            String docType,
            Class<T> clazz)
            throws ParserException, IOException {
        Query query = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjects(getIndexName(docType), query, start, size, sortOptions, false, clazz);
    }

    private SearchResult<String> searchObjectIds(String indexName, Query query, int start, int size)
            throws IOException {
        return searchObjectIds(indexName, query, start, size, null);
    }

    /**
     * Tries to find object ids for a given query in an index.
     *
     * @param indexName The name of the index.
     * @param query The query to use for searching.
     * @param start The start to use.
     * @param size The total return size.
     * @param sortOptions A list of string options to sort in the form VALUE:ORDER; where ORDER is
     *     optional and can be either ASC OR DESC.
     * @return The SearchResults which includes the count and IDs that were found.
     * @throws IOException If we cannot communicate with ES.
     */
    private SearchResult<String> searchObjectIds(
            String indexName, Query query, int start, int size, List<String> sortOptions)
            throws IOException {

        SearchRequest.Builder requestBuilder =
                new SearchRequest.Builder().index(indexName).query(query).from(start).size(size);

        if (sortOptions != null && !sortOptions.isEmpty()) {
            for (String sortOption : sortOptions) {
                SortOrder order = SortOrder.Asc;
                String field = sortOption;
                int index = sortOption.indexOf(":");
                if (index > 0) {
                    field = sortOption.substring(0, index);
                    String orderStr = sortOption.substring(index + 1);
                    order = "DESC".equalsIgnoreCase(orderStr) ? SortOrder.Desc : SortOrder.Asc;
                }
                final String finalField = field;
                final SortOrder finalOrder = order;
                requestBuilder.sort(s -> s.field(f -> f.field(finalField).order(finalOrder)));
            }
        }

        SearchRequest searchRequest = requestBuilder.build();
        SearchResponse<com.fasterxml.jackson.databind.node.ObjectNode> response =
                openSearchClient.search(
                        searchRequest, com.fasterxml.jackson.databind.node.ObjectNode.class);

        List<String> result =
                response.hits().hits().stream().map(Hit::id).collect(Collectors.toList());
        long count = response.hits().total() != null ? response.hits().total().value() : 0;
        return new SearchResult<>(count, result);
    }

    private <T> SearchResult<T> searchObjects(
            String indexName,
            Query query,
            int start,
            int size,
            List<String> sortOptions,
            boolean idOnly,
            Class<T> clazz)
            throws IOException {

        SearchRequest.Builder requestBuilder =
                new SearchRequest.Builder().index(indexName).query(query).from(start).size(size);

        if (idOnly) {
            requestBuilder.source(s -> s.fetch(false));
        }

        if (sortOptions != null && !sortOptions.isEmpty()) {
            for (String sortOption : sortOptions) {
                SortOrder order = SortOrder.Asc;
                String field = sortOption;
                int index = sortOption.indexOf(":");
                if (index > 0) {
                    field = sortOption.substring(0, index);
                    String orderStr = sortOption.substring(index + 1);
                    order = "DESC".equalsIgnoreCase(orderStr) ? SortOrder.Desc : SortOrder.Asc;
                }
                final String finalField = field;
                final SortOrder finalOrder = order;
                requestBuilder.sort(s -> s.field(f -> f.field(finalField).order(finalOrder)));
            }
        }

        SearchRequest searchRequest = requestBuilder.build();
        SearchResponse<T> response = openSearchClient.search(searchRequest, clazz);
        return mapSearchResult(response, idOnly, clazz);
    }

    private <T> SearchResult<T> mapSearchResult(
            SearchResponse<T> response, boolean idOnly, Class<T> clazz) {
        HitsMetadata<T> hitsMetadata = response.hits();
        long count = hitsMetadata.total() != null ? hitsMetadata.total().value() : 0;
        List<T> result;
        if (idOnly) {
            result =
                    hitsMetadata.hits().stream()
                            .map(hit -> clazz.cast(hit.id()))
                            .collect(Collectors.toList());
        } else {
            result =
                    hitsMetadata.hits().stream()
                            .map(Hit::source)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toList());
        }
        return new SearchResult<>(count, result);
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        String ltDate = LocalDate.now().minusDays(archiveTtlDays).toString();
        String gteDate = LocalDate.now().minusDays(archiveTtlDays).minusDays(1).toString();

        Query q =
                Query.of(
                        query ->
                                query.bool(
                                        b ->
                                                b.must(
                                                                m ->
                                                                        m.range(
                                                                                r ->
                                                                                        r.field(
                                                                                                        "endTime")
                                                                                                .lt(
                                                                                                        JsonData
                                                                                                                .of(
                                                                                                                        ltDate))
                                                                                                .gte(
                                                                                                        JsonData
                                                                                                                .of(
                                                                                                                        gteDate))))
                                                        .should(
                                                                s ->
                                                                        s.term(
                                                                                t ->
                                                                                        t.field(
                                                                                                        "status")
                                                                                                .value(
                                                                                                        FieldValue
                                                                                                                .of(
                                                                                                                        "COMPLETED"))))
                                                        .should(
                                                                s ->
                                                                        s.term(
                                                                                t ->
                                                                                        t.field(
                                                                                                        "status")
                                                                                                .value(
                                                                                                        FieldValue
                                                                                                                .of(
                                                                                                                        "FAILED"))))
                                                        .should(
                                                                s ->
                                                                        s.term(
                                                                                t ->
                                                                                        t.field(
                                                                                                        "status")
                                                                                                .value(
                                                                                                        FieldValue
                                                                                                                .of(
                                                                                                                        "TIMED_OUT"))))
                                                        .should(
                                                                s ->
                                                                        s.term(
                                                                                t ->
                                                                                        t.field(
                                                                                                        "status")
                                                                                                .value(
                                                                                                        FieldValue
                                                                                                                .of(
                                                                                                                        "TERMINATED"))))
                                                        .mustNot(
                                                                mn ->
                                                                        mn.exists(
                                                                                e ->
                                                                                        e.field(
                                                                                                "archived")))
                                                        .minimumShouldMatch("1")));

        SearchResult<String> workflowIds;
        try {
            workflowIds = searchObjectIds(indexName, q, 0, 1000);
        } catch (IOException e) {
            logger.error("Unable to communicate with ES to find archivable workflows", e);
            return Collections.emptyList();
        }

        return workflowIds.getResults();
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
        Query query = boolQuery(structuredQuery, freeTextQuery);

        String indexName = getIndexName(docType);
        CountRequest countRequest =
                new CountRequest.Builder().index(indexName).query(query).build();
        CountResponse countResponse = openSearchClient.count(countRequest);
        return countResponse.count();
    }

    public List<String> searchRecentRunningWorkflows(
            int lastModifiedHoursAgoFrom, int lastModifiedHoursAgoTo) {
        Instant now = Instant.now();
        long fromMillis = now.minusSeconds(lastModifiedHoursAgoFrom * 3600L).toEpochMilli();
        long toMillis = now.minusSeconds(lastModifiedHoursAgoTo * 3600L).toEpochMilli();

        Query q =
                Query.of(
                        query ->
                                query.bool(
                                        b ->
                                                b.must(
                                                                Query.of(
                                                                        m ->
                                                                                m.range(
                                                                                        r ->
                                                                                                r.field(
                                                                                                                "updateTime")
                                                                                                        .gt(
                                                                                                                JsonData
                                                                                                                        .of(
                                                                                                                                fromMillis)))))
                                                        .must(
                                                                Query.of(
                                                                        m ->
                                                                                m.range(
                                                                                        r ->
                                                                                                r.field(
                                                                                                                "updateTime")
                                                                                                        .lt(
                                                                                                                JsonData
                                                                                                                        .of(
                                                                                                                                toMillis)))))
                                                        .must(
                                                                Query.of(
                                                                        m ->
                                                                                m.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                FieldValue
                                                                                                                        .of(
                                                                                                                                "RUNNING")))))));

        SearchResult<String> workflowIds;
        try {
            workflowIds =
                    searchObjectIds(
                            workflowIndexName,
                            q,
                            0,
                            5000,
                            Collections.singletonList("updateTime:ASC"));
        } catch (IOException e) {
            logger.error("Unable to communicate with ES to find recent running workflows", e);
            return Collections.emptyList();
        }

        return workflowIds.getResults();
    }

    private void indexObject(final String index, final String docType, final Object doc) {
        indexObject(index, docType, null, doc);
    }

    private void indexObject(
            final String index, final String docType, final String docId, final Object doc) {

        BulkOperation operation =
                BulkOperation.of(
                        b ->
                                b.index(
                                        idx -> {
                                            idx.index(index).document(JsonData.of(doc));
                                            if (docId != null) {
                                                idx.id(docId);
                                            }
                                            return idx;
                                        }));

        synchronized (this) {
            if (bulkRequests.get(docType) == null) {
                bulkRequests.put(docType, new BulkRequests(System.currentTimeMillis()));
            }

            bulkRequests.get(docType).addOperation(operation);
            if (bulkRequests.get(docType).numberOfOperations() >= this.indexBatchSize) {
                indexBulkRequest(docType);
            }
        }
    }

    private synchronized void indexBulkRequest(String docType) {
        BulkRequests requests = bulkRequests.get(docType);
        if (requests != null && requests.numberOfOperations() > 0) {
            synchronized (requests.getOperations()) {
                indexWithRetry(requests.getOperations(), "Bulk Indexing " + docType, docType);
                bulkRequests.put(docType, new BulkRequests(System.currentTimeMillis()));
            }
        }
    }

    /**
     * Performs an index operation with a retry.
     *
     * @param operations The bulk operations to perform.
     * @param operationDescription The type of operation that we are performing.
     */
    private void indexWithRetry(
            final List<BulkOperation> operations,
            final String operationDescription,
            String docType) {
        try {
            long startTime = Instant.now().toEpochMilli();
            BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
            retryTemplate.execute(context -> openSearchClient.bulk(bulkRequest));
            long endTime = Instant.now().toEpochMilli();
            logger.debug(
                    "Time taken {} for indexing object of type: {}", endTime - startTime, docType);
            Monitors.recordESIndexTime("index_object", docType, endTime - startTime);
            Monitors.recordWorkerQueueSize(
                    "indexQueue", ((ThreadPoolExecutor) executorService).getQueue().size());
            Monitors.recordWorkerQueueSize(
                    "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
        } catch (Exception e) {
            Monitors.error(className, "index");
            logger.error(
                    "Failed to index {} for request type: {}", operationDescription, docType, e);
        }
    }

    /**
     * Flush the buffers if bulk requests have not been indexed for the past {@link
     * OpenSearchProperties#getAsyncBufferFlushTimeout()} seconds This is to prevent data loss in
     * case the instance is terminated, while the buffer still holds documents to be indexed.
     */
    private void flushBulkRequests() {
        bulkRequests.entrySet().stream()
                .filter(
                        entry ->
                                (System.currentTimeMillis() - entry.getValue().getLastFlushTime())
                                        >= asyncBufferFlushTimeout * 1000L)
                .filter(entry -> entry.getValue().numberOfOperations() > 0)
                .forEach(
                        entry -> {
                            logger.debug(
                                    "Flushing bulk request buffer for type {}, size: {}",
                                    entry.getKey(),
                                    entry.getValue().numberOfOperations());
                            indexBulkRequest(entry.getKey());
                        });
    }

    private static class BulkRequests {

        private final long lastFlushTime;
        private final List<BulkOperation> operations;

        long getLastFlushTime() {
            return lastFlushTime;
        }

        List<BulkOperation> getOperations() {
            return operations;
        }

        int numberOfOperations() {
            return operations.size();
        }

        void addOperation(BulkOperation op) {
            operations.add(op);
        }

        BulkRequests(long lastFlushTime) {
            this.lastFlushTime = lastFlushTime;
            this.operations = new ArrayList<>();
        }
    }
}
