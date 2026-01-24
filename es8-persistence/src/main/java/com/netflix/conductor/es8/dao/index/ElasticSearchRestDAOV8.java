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
package com.netflix.conductor.es8.dao.index;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

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
import com.netflix.conductor.es8.config.ElasticSearchProperties;
import com.netflix.conductor.es8.dao.query.parser.internal.ParserException;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.*;

@Trace
public class ElasticSearchRestDAOV8 extends ElasticSearchBaseDAO implements IndexDAO {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchRestDAOV8.class);

    private static final String CLASS_NAME = ElasticSearchRestDAOV8.class.getSimpleName();

    private static final int CORE_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 1L;

    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String LOG_DOC_TYPE = "task_log";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String MSG_DOC_TYPE = "message";
    private static final String ILM_POLICY_NAME = "conductor-default-ilm-policy";
    private static final String ILM_ROLLOVER_MAX_PRIMARY_SHARD_SIZE = "50gb";
    private static final String ILM_ROLLOVER_MAX_AGE = "30d";


    private @interface HttpMethod {

        String GET = "GET";
        String POST = "POST";
        String PUT = "PUT";
        String HEAD = "HEAD";
    }

    private static final String className = ElasticSearchRestDAOV8.class.getSimpleName();

    private final String workflowIndexName;
    private final String taskIndexName;
    private final String eventIndexPrefix;
    private final String eventIndexName;
    private final String messageIndexPrefix;
    private final String messageIndexName;
    private final String logIndexName;
    private final String logIndexPrefix;

    private final String clusterHealthColor;
    private final ElasticsearchClient elasticSearchClient;
    private final ElasticsearchAsyncClient elasticSearchAsyncClient;
    private final ElasticsearchTransport transport;
    private final RestClient elasticSearchAdminClient;
    private final ExecutorService executorService;
    private final ExecutorService logExecutorService;
    private final ConcurrentHashMap<Pair<String, Refresh>, BulkIngester<Void>> bulkIngesters;
    private final ConcurrentHashMap<Long, Long> bulkRequestStartTimes;
    private final ScheduledExecutorService bulkScheduler;
    private final int indexBatchSize;
    private final int asyncBufferFlushTimeout;
    private final ElasticSearchProperties properties;
    private final RetryTemplate retryTemplate;

    public ElasticSearchRestDAOV8(
            RestClientBuilder restClientBuilder,
            RetryTemplate retryTemplate,
            ElasticSearchProperties properties,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.elasticSearchAdminClient = restClientBuilder.build();
        this.transport = new RestClientTransport(
                this.elasticSearchAdminClient, new JacksonJsonpMapper(objectMapper));
        this.elasticSearchClient = new ElasticsearchClient(transport);
        this.elasticSearchAsyncClient = new ElasticsearchAsyncClient(transport);
        this.clusterHealthColor = properties.getClusterHealthColor();
        this.bulkIngesters = new ConcurrentHashMap<>();
        this.bulkRequestStartTimes = new ConcurrentHashMap<>();
        this.bulkScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "es8-bulk-flush");
                            thread.setDaemon(true);
                            return thread;
                        });
        this.indexBatchSize = properties.getIndexBatchSize();
        this.asyncBufferFlushTimeout = (int) properties.getAsyncBufferFlushTimeout().getSeconds();
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
    }

    @PreDestroy
    private void shutdown() {
        logger.info("Gracefully shutdown executor service");
        bulkIngesters.values().forEach(BulkIngester::close);
        shutdownExecutorService(bulkScheduler);
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
        waitForHealthyCluster();

        if (properties.isAutoIndexManagementEnabled()) {
            createIndexesTemplates();
            createWorkflowIndex();
            createTaskIndex();
            ensureDataStream(logIndexName);
            ensureDataStream(messageIndexName);
            ensureDataStream(eventIndexName);
        }
    }

    private void createIndexesTemplates() {
        try {
            ensureIlmPolicy();
            ensureComponentTemplate();
            initIndexesTemplates();
        } catch (Exception e) {
            logger.error("Error creating index templates!", e);
        }
    }

    private void initIndexesTemplates() {
        TemplateDefinition workflowDefinition =
                loadTemplateDefinition("/template_workflow.json");
        initIndexAliasTemplate(
                "template_workflow",
                workflowIndexName + "-*",
                workflowDefinition.mappings,
                workflowDefinition.settings,
                workflowIndexName);

        TemplateDefinition taskDefinition = loadTemplateDefinition("/template_task.json");
        initIndexAliasTemplate(
                "template_task",
                taskIndexName + "-*",
                taskDefinition.mappings,
                taskDefinition.settings,
                taskIndexName);

        TemplateDefinition logDefinition = loadTemplateDefinition("/template_task_log.json");
        initDataStreamTemplate(
                "template_" + LOG_DOC_TYPE,
                logIndexName,
                logDefinition.mappings,
                logDefinition.settings,
                "createdTime");

        TemplateDefinition eventDefinition = loadTemplateDefinition("/template_event.json");
        initDataStreamTemplate(
                "template_" + EVENT_DOC_TYPE,
                eventIndexName,
                eventDefinition.mappings,
                eventDefinition.settings,
                "created");

        TemplateDefinition messageDefinition = loadTemplateDefinition("/template_message.json");
        initDataStreamTemplate(
                "template_" + MSG_DOC_TYPE,
                messageIndexName,
                messageDefinition.mappings,
                messageDefinition.settings,
                "created");
    }

    /** Initializes the index template for alias-based indices. */
    private void initIndexAliasTemplate(
            String templateName,
            String indexPattern,
            JsonNode mappings,
            JsonNode additionalSettings,
            String aliasName) {
        try {
            logger.info("Creating/updating the index template '{}'", templateName);
            ObjectNode root = objectMapper.createObjectNode();
            root.putArray("index_patterns").add(indexPattern);
            root.put("priority", 500);
            root.putArray("composed_of").add("conductor-common-settings");

            ObjectNode template = root.putObject("template");
            ObjectNode settings = template.putObject("settings");
            if (additionalSettings != null && additionalSettings.isObject()) {
                settings.setAll((ObjectNode) additionalSettings);
            }
            settings.put("index.lifecycle.rollover_alias", aliasName);

            if (mappings != null) {
                template.set("mappings", mappings);
            }

            ObjectNode aliases = template.putObject("aliases");
            aliases.set(aliasName, objectMapper.createObjectNode());

            HttpEntity entity =
                    new NStringEntity(objectMapper.writeValueAsString(root), ContentType.APPLICATION_JSON);
            Request request = new Request(HttpMethod.PUT, "/_index_template/" + templateName);
            request.setEntity(entity);
            elasticSearchAdminClient.performRequest(request);
        } catch (Exception e) {
            logger.error("Failed to init " + templateName, e);
        }
    }

    /** Initializes the index template for data streams. */
    private void initDataStreamTemplate(
            String templateName,
            String dataStreamName,
            JsonNode mappings,
            JsonNode additionalSettings,
            String timestampField) {
        try {
            logger.info("Creating/updating the data stream template '{}'", templateName);
            ObjectNode root = objectMapper.createObjectNode();
            root.putArray("index_patterns").add(dataStreamName);
            root.put("priority", 500);
            root.putArray("composed_of").add("conductor-common-settings");
            ObjectNode dataStream = objectMapper.createObjectNode();
            if (timestampField != null && !timestampField.isBlank()) {
                ObjectNode timestamp = objectMapper.createObjectNode();
                timestamp.put("name", timestampField);
                dataStream.set("timestamp_field", timestamp);
            }
            root.set("data_stream", dataStream);

            ObjectNode template = root.putObject("template");
            ObjectNode settings = template.putObject("settings");
            if (additionalSettings != null && additionalSettings.isObject()) {
                settings.setAll((ObjectNode) additionalSettings);
            }
            if (mappings != null) {
                template.set("mappings", mappings);
            }

            HttpEntity entity =
                    new NStringEntity(
                            objectMapper.writeValueAsString(root), ContentType.APPLICATION_JSON);
            Request request = new Request(HttpMethod.PUT, "/_index_template/" + templateName);
            request.setEntity(entity);
            elasticSearchAdminClient.performRequest(request);
        } catch (Exception e) {
            logger.error("Failed to init " + templateName, e);
        }
    }

    private void createWorkflowIndex() {
        try {
            ensureWriteIndex(workflowIndexName);
        } catch (IOException e) {
            logger.error("Failed to initialize index alias '{}'", workflowIndexName, e);
        }
    }

    private void createTaskIndex() {
        try {
            ensureWriteIndex(taskIndexName);
        } catch (IOException e) {
            logger.error("Failed to initialize index alias '{}'", taskIndexName, e);
        }
    }

    private void ensureIlmPolicy() {
        try {
            String resourcePath = "/_ilm/policy/" + ILM_POLICY_NAME;
            if (doesResourceNotExist(resourcePath)) {
                ObjectNode root = objectMapper.createObjectNode();
                ObjectNode policy = root.putObject("policy");
                ObjectNode phases = policy.putObject("phases");
                ObjectNode hot = phases.putObject("hot");
                ObjectNode actions = hot.putObject("actions");
                ObjectNode rollover = actions.putObject("rollover");
                rollover.put("max_primary_shard_size", ILM_ROLLOVER_MAX_PRIMARY_SHARD_SIZE);
                rollover.put("max_age", ILM_ROLLOVER_MAX_AGE);

                Request request = new Request(HttpMethod.PUT, resourcePath);
                request.setEntity(
                        new NStringEntity(
                                objectMapper.writeValueAsString(root),
                                ContentType.APPLICATION_JSON));
                elasticSearchAdminClient.performRequest(request);
                logger.info("Created ILM policy '{}'", ILM_POLICY_NAME);
            }
        } catch (Exception e) {
            logger.error("Failed to create ILM policy '{}'", ILM_POLICY_NAME, e);
        }
    }

    private void ensureComponentTemplate() {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode template = root.putObject("template");
            ObjectNode settings = template.putObject("settings");
            settings.put("number_of_shards", properties.getIndexShardCount());
            settings.put("number_of_replicas", properties.getIndexReplicasCount());
            settings.put("index.lifecycle.name", ILM_POLICY_NAME);
            String refreshInterval = formatRefreshInterval(properties.getIndexRefreshInterval());
            if (refreshInterval != null) {
                settings.put("index.refresh_interval", refreshInterval);
            }

            Request request = new Request(HttpMethod.PUT, "/_component_template/conductor-common-settings");
            request.setEntity(
                    new NStringEntity(
                            objectMapper.writeValueAsString(root), ContentType.APPLICATION_JSON));
            elasticSearchAdminClient.performRequest(request);
            logger.info("Created/updated component template 'conductor-common-settings'");
        } catch (Exception e) {
            logger.error("Failed to create component template 'conductor-common-settings'", e);
        }
    }

    private String formatRefreshInterval(Duration refreshInterval) {
        if (refreshInterval == null) {
            return null;
        }
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            return "-1";
        }
        return refreshInterval.toMillis() + "ms";
    }

    private TemplateDefinition loadTemplateDefinition(String templateResource) {
        try (InputStream stream =
                ElasticSearchRestDAOV8.class.getResourceAsStream(templateResource)) {
            if (stream == null) {
                throw new IOException("Template resource not found: " + templateResource);
            }
            JsonNode root = objectMapper.readTree(IOUtils.toString(stream));
            JsonNode templateNode = root.get("template");
            JsonNode settings = templateNode != null ? templateNode.get("settings") : null;
            JsonNode mappings = templateNode != null ? templateNode.get("mappings") : null;
            return new TemplateDefinition(settings, mappings);
        } catch (IOException e) {
            throw new NonTransientException("Failed to load template: " + templateResource, e);
        }
    }

    private void ensureWriteIndex(String aliasName) throws IOException {
        String aliasPath = "/_alias/" + aliasName;
        if (doesResourceNotExist(aliasPath)) {
            String indexName = aliasName + "-000001";
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode aliases = root.putObject("aliases");
            ObjectNode alias = aliases.putObject(aliasName);
            alias.put("is_write_index", true);
            Request request = new Request(HttpMethod.PUT, "/" + indexName);
            request.setEntity(
                    new NStringEntity(
                            objectMapper.writeValueAsString(root), ContentType.APPLICATION_JSON));
            elasticSearchAdminClient.performRequest(request);
            logger.info("Created write index '{}' for alias '{}'", indexName, aliasName);
        }
    }

    private void ensureDataStream(String dataStreamName) throws IOException {
        String dataStreamPath = "/_data_stream/" + dataStreamName;
        if (doesResourceNotExist(dataStreamPath)) {
            Request request = new Request(HttpMethod.PUT, dataStreamPath);
            elasticSearchAdminClient.performRequest(request);
            logger.info("Created data stream '{}'", dataStreamName);
        }
    }

    private static class TemplateDefinition {
        private final JsonNode settings;
        private final JsonNode mappings;

        private TemplateDefinition(JsonNode settings, JsonNode mappings) {
            this.settings = settings;
            this.mappings = mappings;
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
        Request request = new Request("GET", "/_cluster/health");
        request.addParameters(params);
        elasticSearchAdminClient.performRequest(request);
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
                Response response =
                        elasticSearchAdminClient.performRequest(
                                new Request(HttpMethod.GET, resourcePath));
                return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
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

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {
        try {
            long startTime = Instant.now().toEpochMilli();
            String workflowId = workflow.getWorkflowId();
            Refresh refresh =
                    properties.isWaitForIndexRefresh() ? Refresh.WaitFor : null;
            elasticSearchClient.index(
                    i -> {
                        i.index(workflowIndexName).id(workflowId).document(workflow);
                        if (refresh != null) {
                            i.refresh(refresh);
                        }
                        return i;
                    });
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

            Refresh refreshPolicy =
                    properties.isWaitForIndexRefresh() ? Refresh.WaitFor : null;
            indexObject(taskIndexName, TASK_DOC_TYPE, taskId, task, refreshPolicy);
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
            operations.add(
                    BulkOperation.of(
                            op ->
                                    op.index(
                                            i ->
                                                    i.index(logIndexName)
                                                            .document(log))));
        }

        try {
            elasticSearchClient.bulk(b -> b.operations(operations));
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
                                                                            field.field("createdTime")
                                                                                    .order(
                                                                                            SortOrder.Asc)))
                                            .size(properties.getTaskLogResultLimit())
                                            .trackTotalHits(t -> t.enabled(true)),
                            TaskExecLog.class);

            return mapTaskExecLogsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get task execution logs for task: {}", taskId, e);
        }
        return null;
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
                                                                                            SortOrder.Asc)))
                                            .trackTotalHits(t -> t.enabled(true)),
                            Map.class);
            return mapGetMessagesResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get messages for queue: {}", queue, e);
        }
        return null;
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
                                                                                            SortOrder.Asc)))
                                            .trackTotalHits(t -> t.enabled(true)),
                            EventExecution.class);

            return mapEventExecutionsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get executions for event: {}", event, e);
        }
        return null;
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

            indexObject(eventIndexName, EVENT_DOC_TYPE, id, eventExecution, null);
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
        Query queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjects(
                getIndexName(docType), queryBuilder, start, size, sortOptions, idOnly, clazz);
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
            DeleteResponse response =
                    elasticSearchClient.delete(
                            d -> d.index(workflowIndexName).id(workflowId));

            if (response.result() == Result.NotFound) {
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

            logger.debug("Updating workflow {} with {}", workflowInstanceId, source);
            elasticSearchClient.update(
                    u ->
                            u.index(workflowIndexName)
                                    .id(workflowInstanceId)
                                    .doc(source),
                    Map.class);
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

        try {
            DeleteResponse response =
                    elasticSearchClient.delete(d -> d.index(taskIndexName).id(taskId));

            if (response.result() != Result.Deleted) {
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

            logger.debug("Updating task: {} of workflow: {} with {}", taskId, workflowId, source);
            elasticSearchClient.update(
                    u -> u.index(taskIndexName).id(taskId).doc(source), Map.class);
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
        try {
            GetResponse<Map> response =
                    elasticSearchClient.get(
                            g -> g.index(workflowIndexName).id(workflowInstanceId),
                            Map.class);
            if (response.found()) {
                Map sourceAsMap = response.source();
                if (sourceAsMap != null && sourceAsMap.get(fieldToGet) != null) {
                    return sourceAsMap.get(fieldToGet).toString();
                }
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
        Query queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjectIds(getIndexName(docType), queryBuilder, start, size, sortOptions);
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
        Query queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjects(
                getIndexName(docType), queryBuilder, start, size, sortOptions, false, clazz);
    }

    private SearchResult<String> searchObjectIds(
            String indexName, Query queryBuilder, int start, int size) throws IOException {
        return searchObjectIds(indexName, queryBuilder, start, size, null);
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
            String indexName,
            Query queryBuilder,
            int start,
            int size,
            List<String> sortOptions)
            throws IOException {
        List<SortOptions> sort = buildSortOptions(sortOptions);
        SearchResponse<Void> response =
                elasticSearchClient.search(
                        s -> {
                            s.index(indexName)
                                    .query(queryBuilder)
                                    .from(start)
                                    .size(size)
                                    .trackTotalHits(t -> t.enabled(true))
                                    .source(src -> src.fetch(false));
                            if (!sort.isEmpty()) {
                                s.sort(sort);
                            }
                            return s;
                        },
                        Void.class);

        List<String> result =
                response.hits().hits().stream()
                        .map(hit -> hit.id())
                        .collect(Collectors.toList());
        long count = totalHits(response);
        return new SearchResult<>(count, result);
    }

    private <T> SearchResult<T> searchObjects(
            String indexName,
            Query queryBuilder,
            int start,
            int size,
            List<String> sortOptions,
            boolean idOnly,
            Class<T> clazz)
            throws IOException {
        List<SortOptions> sort = buildSortOptions(sortOptions);
        SearchResponse<T> response =
                elasticSearchClient.search(
                        s -> {
                            s.index(indexName)
                                    .query(queryBuilder)
                                    .from(start)
                                    .size(size)
                                    .trackTotalHits(t -> t.enabled(true));
                            if (idOnly) {
                                s.source(src -> src.fetch(false));
                            }
                            if (!sort.isEmpty()) {
                                s.sort(sort);
                            }
                            return s;
                        },
                        clazz);
        return mapSearchResult(response, idOnly, clazz);
    }

    private <T> SearchResult<T> mapSearchResult(
            SearchResponse<T> response, boolean idOnly, Class<T> clazz) {
        long count = totalHits(response);
        List<T> result;
        if (idOnly) {
            result =
                    response.hits().hits().stream()
                            .map(hit -> clazz.cast(hit.id()))
                            .collect(Collectors.toList());
        } else {
            result =
                    response.hits().hits().stream()
                            .map(hit -> hit.source())
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
        }
        return new SearchResult<>(count, result);
    }

    private List<SortOptions> buildSortOptions(List<String> sortOptions) {
        if (sortOptions == null || sortOptions.isEmpty()) {
            return Collections.emptyList();
        }
        List<SortOptions> options = new ArrayList<>();
        for (String sortOption : sortOptions) {
            SortOrder order = SortOrder.Asc;
            String field = sortOption;
            int index = sortOption.indexOf(":");
            if (index > 0) {
                field = sortOption.substring(0, index);
                String orderValue = sortOption.substring(index + 1).trim().toUpperCase();
                if ("DESC".equals(orderValue)) {
                    order = SortOrder.Desc;
                }
            }
            String sortField = field;
            SortOrder sortOrder = order;
            options.add(
                    SortOptions.of(
                            s ->
                                    s.field(
                                            f -> f.field(sortField).order(sortOrder))));
        }
        return options;
    }

    private long totalHits(SearchResponse<?> response) {
        if (response.hits().total() != null) {
            return response.hits().total().value();
        }
        return response.hits().hits().size();
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        String archiveTo = LocalDate.now().minusDays(archiveTtlDays).toString();
        String archiveFrom =
                LocalDate.now().minusDays(archiveTtlDays).minusDays(1).toString();
        Query q =
                Query.of(
                        qb ->
                                qb.bool(
                                        b ->
                                                b.must(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.range(
                                                                                        r ->
                                                                                                r.field(
                                                                                                                "endTime")
                                                                                                        .lt(
                                                                                                                JsonData.of(
                                                                                                                        archiveTo))
                                                                                                        .gte(
                                                                                                                JsonData.of(
                                                                                                                        archiveFrom)))))
                                                        .should(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                "COMPLETED"))))
                                                        .should(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                "FAILED"))))
                                                        .should(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                "TIMED_OUT"))))
                                                        .should(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                "TERMINATED"))))
                                                        .mustNot(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.exists(
                                                                                        e ->
                                                                                                e.field(
                                                                                                        "archived"))))
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
        Query queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);

        String indexName = getIndexName(docType);
        CountResponse countResponse =
                elasticSearchClient.count(c -> c.index(indexName).query(queryBuilder));
        return countResponse.count();
    }

    public List<String> searchRecentRunningWorkflows(
            int lastModifiedHoursAgoFrom, int lastModifiedHoursAgoTo) {
        Instant now = Instant.now();
        long fromMillis =
                now.minus(Duration.ofHours(lastModifiedHoursAgoFrom)).toEpochMilli();
        long toMillis = now.minus(Duration.ofHours(lastModifiedHoursAgoTo)).toEpochMilli();
        Query q =
                Query.of(
                        qb ->
                                qb.bool(
                                        b ->
                                                b.must(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.range(
                                                                                        r ->
                                                                                                r.field(
                                                                                                                "updateTime")
                                                                                                        .gt(
                                                                                                                JsonData.of(
                                                                                                                        fromMillis)))))
                                                        .must(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.range(
                                                                                        r ->
                                                                                                r.field(
                                                                                                                "updateTime")
                                                                                                        .lt(
                                                                                                                JsonData.of(
                                                                                                                        toMillis)))))
                                                        .must(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                "RUNNING"))))));

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
        indexObject(index, docType, null, doc, null);
    }

    private void indexObject(
            final String index,
            final String docType,
            final String docId,
            final Object doc,
            final Refresh refreshPolicy) {
        BulkOperation operation =
                BulkOperation.of(
                        op ->
                                op.index(
                                        i -> {
                                            i.index(index).document(doc);
                                            if (docId != null) {
                                                i.id(docId);
                                            }
                                            return i;
                                        }));
        BulkIngester<Void> ingester = getBulkIngester(docType, refreshPolicy);
        ingester.add(operation);
    }

    private BulkIngester<Void> getBulkIngester(String docType, Refresh refreshPolicy) {
        Pair<String, Refresh> requestKey = new ImmutablePair<>(docType, refreshPolicy);
        return bulkIngesters.computeIfAbsent(
                requestKey, key -> createBulkIngester(docType, refreshPolicy));
    }

    private BulkIngester<Void> createBulkIngester(String docType, Refresh refreshPolicy) {
        BulkListener<Void> listener =
                new BulkListener<>() {
                    @Override
                    public void beforeBulk(
                            long executionId, BulkRequest request, List<Void> contexts) {
                        bulkRequestStartTimes.put(executionId, System.currentTimeMillis());
                    }

                    @Override
                    public void afterBulk(
                            long executionId,
                            BulkRequest request,
                            List<Void> contexts,
                            BulkResponse response) {
                        long duration = recordBulkDuration(executionId);
                        if (duration >= 0) {
                            Monitors.recordESIndexTime("index_object", docType, duration);
                        }
                        Monitors.recordWorkerQueueSize(
                                "indexQueue",
                                ((ThreadPoolExecutor) executorService).getQueue().size());
                        Monitors.recordWorkerQueueSize(
                                "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());

                        if (response.errors()) {
                            long errorCount =
                                    response.items().stream()
                                            .filter(item -> item.error() != null)
                                            .count();
                            Monitors.error(className, "index");
                            logger.warn(
                                    "Bulk indexing reported {} failures for doc type {} ({} items)",
                                    errorCount,
                                    docType,
                                    response.items().size());
                        }
                    }

                    @Override
                    public void afterBulk(
                            long executionId,
                            BulkRequest request,
                            List<Void> contexts,
                            Throwable failure) {
                        long duration = recordBulkDuration(executionId);
                        if (duration >= 0) {
                            Monitors.recordESIndexTime("index_object", docType, duration);
                        }
                        Monitors.error(className, "index");
                        logger.error("Bulk indexing failed for doc type {}", docType, failure);
                        try {
                            retryTemplate.execute(
                                    context -> {
                                        elasticSearchClient.bulk(request);
                                        return null;
                                    });
                        } catch (Exception retryException) {
                            logger.error(
                                    "Bulk indexing retry failed for doc type {}", docType, retryException);
                        }
                        Monitors.recordWorkerQueueSize(
                                "indexQueue",
                                ((ThreadPoolExecutor) executorService).getQueue().size());
                        Monitors.recordWorkerQueueSize(
                                "logQueue", ((ThreadPoolExecutor) logExecutorService).getQueue().size());
                    }
                };

        return BulkIngester.of(
                builder -> {
                    builder.client(elasticSearchAsyncClient);
                    builder.maxOperations(indexBatchSize);
                    builder.maxConcurrentRequests(
                            Math.max(1, Math.min(4, properties.getAsyncMaxPoolSize())));
                    if (asyncBufferFlushTimeout > 0) {
                        builder.flushInterval(asyncBufferFlushTimeout, TimeUnit.SECONDS, bulkScheduler);
                    }
                    builder.listener(listener);
                    if (refreshPolicy != null) {
                        builder.globalSettings(b -> b.refresh(refreshPolicy));
                    }
                    return builder;
                });
    }

    private long recordBulkDuration(long executionId) {
        Long start = bulkRequestStartTimes.remove(executionId);
        if (start == null) {
            return -1L;
        }
        return System.currentTimeMillis() - start;
    }
}
