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
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.conductoross.conductor.es8.config.ElasticSearchProperties;
import org.conductoross.conductor.es8.dao.query.parser.Expression;
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
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.metrics.Monitors;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.ilm.IlmPolicy;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.*;

@Trace
public class ElasticSearchRestDAOV8 implements IndexDAO {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchRestDAOV8.class);

    private static final String CLASS_NAME = ElasticSearchRestDAOV8.class.getSimpleName();

    private static final int CORE_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 1L;

    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String LOG_DOC_TYPE = "task_log";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String MSG_DOC_TYPE = "message";
    private static final String ILM_ROLLOVER_MAX_PRIMARY_SHARD_SIZE = "50gb";
    private static final int TASK_LOG_DELETE_BATCH_SIZE = 500;

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
    private final ObjectMapper objectMapper;
    private final String indexPrefix;
    private final String resourcePrefix;
    private final String ilmPolicyName;
    private final String componentTemplateName;

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
        this.resourcePrefix = normalizeResourcePrefix(this.indexPrefix);
        this.ilmPolicyName = prefixedResourceName(this.resourcePrefix, "default-ilm-policy");
        this.componentTemplateName = prefixedResourceName(this.resourcePrefix, "common-settings");

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
            createTaskLogIndex();
            createMessageIndex();
            createEventIndex();
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
        TemplateDefinition workflowDefinition = loadTemplateDefinition("/template_workflow.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_workflow"),
                "template_workflow",
                workflowIndexName + "-*",
                workflowDefinition.mappings,
                workflowDefinition.settings,
                workflowIndexName);

        TemplateDefinition taskDefinition = loadTemplateDefinition("/template_task.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_task"),
                "template_task",
                taskIndexName + "-*",
                taskDefinition.mappings,
                taskDefinition.settings,
                taskIndexName);

        TemplateDefinition logDefinition = loadTemplateDefinition("/template_task_log.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_" + LOG_DOC_TYPE),
                "template_" + LOG_DOC_TYPE,
                logIndexName + "-*",
                logDefinition.mappings,
                logDefinition.settings,
                logIndexName);

        TemplateDefinition eventDefinition = loadTemplateDefinition("/template_event.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_" + EVENT_DOC_TYPE),
                "template_" + EVENT_DOC_TYPE,
                eventIndexName + "-*",
                eventDefinition.mappings,
                eventDefinition.settings,
                eventIndexName);

        TemplateDefinition messageDefinition = loadTemplateDefinition("/template_message.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_" + MSG_DOC_TYPE),
                "template_" + MSG_DOC_TYPE,
                messageIndexName + "-*",
                messageDefinition.mappings,
                messageDefinition.settings,
                messageIndexName);
    }

    /** Initializes the index template for alias-based indices. */
    private void initIndexAliasTemplate(
            String templateName,
            String legacyTemplateName,
            String indexPattern,
            JsonNode mappings,
            JsonNode additionalSettings,
            String aliasName) {
        try {
            logger.info("Creating/updating the index template '{}'", templateName);
            deleteLegacyIndexTemplateIfOwned(
                    legacyTemplateName, templateName, indexPattern, aliasName);
            IndexTemplateMapping.Builder template =
                    new IndexTemplateMapping.Builder()
                            .settings(buildIndexTemplateSettings(additionalSettings, aliasName))
                            .aliases(aliasName, a -> a);
            if (mappings != null) {
                template.mappings(parseTypeMapping(mappings));
            }
            executeWithRetry(
                    () -> {
                        elasticSearchClient
                                .indices()
                                .putIndexTemplate(
                                        r ->
                                                r.name(templateName)
                                                        .indexPatterns(indexPattern)
                                                        .priority(500L)
                                                        .composedOf(componentTemplateName)
                                                        .template(template.build()));
                        return null;
                    });
        } catch (Exception e) {
            logger.error("Failed to init " + templateName, e);
        }
    }

    private void deleteLegacyIndexTemplateIfOwned(
            String legacyTemplateName,
            String replacementTemplateName,
            String expectedIndexPattern,
            String expectedAliasName)
            throws IOException {
        if (StringUtils.isBlank(legacyTemplateName)
                || legacyTemplateName.equals(replacementTemplateName)) {
            return;
        }

        var legacyTemplate = getIndexTemplate(legacyTemplateName);
        if (legacyTemplate == null) {
            return;
        }

        var indexPatterns = legacyTemplate.indexTemplate().indexPatterns();
        if (indexPatterns == null || !indexPatterns.contains(expectedIndexPattern)) {
            return;
        }

        var template = legacyTemplate.indexTemplate().template();
        if (template == null || template.aliases() == null) {
            return;
        }
        if (!template.aliases().containsKey(expectedAliasName)) {
            return;
        }

        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .indices()
                            .deleteIndexTemplate(r -> r.name(legacyTemplateName));
                    return null;
                });
        logger.info(
                "Deleted legacy index template '{}' for pattern '{}' (replaced by '{}')",
                legacyTemplateName,
                expectedIndexPattern,
                replacementTemplateName);
    }

    private co.elastic.clients.elasticsearch.indices.get_index_template.IndexTemplateItem
            getIndexTemplate(String templateName) throws IOException {
        try {
            var response =
                    executeWithRetry(
                            () ->
                                    elasticSearchClient
                                            .indices()
                                            .getIndexTemplate(r -> r.name(templateName)));
            if (response.indexTemplates() == null || response.indexTemplates().isEmpty()) {
                return null;
            }
            return response.indexTemplates().getFirst();
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return null;
            }
            throw e;
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

    private void createTaskLogIndex() {
        try {
            ensureWriteIndex(logIndexName);
        } catch (IOException e) {
            logger.error("Failed to initialize index alias '{}'", logIndexName, e);
        }
    }

    private void createMessageIndex() {
        try {
            ensureWriteIndex(messageIndexName);
        } catch (IOException e) {
            logger.error("Failed to initialize index alias '{}'", messageIndexName, e);
        }
    }

    private void createEventIndex() {
        try {
            ensureWriteIndex(eventIndexName);
        } catch (IOException e) {
            logger.error("Failed to initialize index alias '{}'", eventIndexName, e);
        }
    }

    private void ensureIlmPolicy() {
        try {
            if (ilmPolicyExists(ilmPolicyName)) {
                return;
            }
            IlmPolicy policy =
                    IlmPolicy.of(
                            p ->
                                    p.phases(
                                            ph ->
                                                    ph.hot(
                                                            hot ->
                                                                    hot.actions(
                                                                            a ->
                                                                                    a.rollover(
                                                                                            r ->
                                                                                                    r
                                                                                                            .maxPrimaryShardSize(
                                                                                                                    ILM_ROLLOVER_MAX_PRIMARY_SHARD_SIZE))))));
            executeWithRetry(
                    () -> {
                        elasticSearchClient
                                .ilm()
                                .putLifecycle(r -> r.name(ilmPolicyName).policy(policy));
                        return null;
                    });
            logger.info("Created ILM policy '{}'", ilmPolicyName);
        } catch (Exception e) {
            logger.error("Failed to create ILM policy '{}'", ilmPolicyName, e);
        }
    }

    private void ensureComponentTemplate() {
        try {
            IndexSettings settings = buildCommonIndexSettings();
            executeWithRetry(
                    () -> {
                        elasticSearchClient
                                .cluster()
                                .putComponentTemplate(
                                        r ->
                                                r.name(componentTemplateName)
                                                        .template(t -> t.settings(settings)));
                        return null;
                    });
            logger.info("Created/updated component template '{}'", componentTemplateName);
        } catch (Exception e) {
            logger.error("Failed to create component template '{}'", componentTemplateName, e);
        }
    }

    private static HealthStatus parseHealthStatus(String value) {
        if (value == null) {
            return HealthStatus.Green;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "green" -> HealthStatus.Green;
            case "yellow" -> HealthStatus.Yellow;
            case "red" -> HealthStatus.Red;
            default -> HealthStatus.Green;
        };
    }

    private boolean ilmPolicyExists(String policyName) throws IOException {
        try {
            elasticSearchClient.ilm().getLifecycle(r -> r.name(policyName));
            return true;
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private TypeMapping parseTypeMapping(JsonNode mappings) throws IOException {
        String json = objectMapper.writeValueAsString(mappings);
        return TypeMapping.of(b -> b.withJson(new StringReader(json)));
    }

    private IndexSettings buildIndexTemplateSettings(
            JsonNode additionalSettings, String rolloverAlias) throws IOException {
        IndexSettings.Builder builder = new IndexSettings.Builder();
        if (additionalSettings != null
                && additionalSettings.isObject()
                && additionalSettings.size() > 0) {
            builder.withJson(new StringReader(objectMapper.writeValueAsString(additionalSettings)));
        }
        builder.lifecycle(l -> l.rolloverAlias(rolloverAlias));
        return builder.build();
    }

    private IndexSettings buildCommonIndexSettings() {
        IndexSettings.Builder builder =
                new IndexSettings.Builder()
                        .numberOfShards(String.valueOf(properties.getIndexShardCount()))
                        .numberOfReplicas(String.valueOf(properties.getIndexReplicasCount()))
                        .lifecycle(l -> l.name(ilmPolicyName));

        String refreshInterval = formatRefreshInterval(properties.getIndexRefreshInterval());
        if (refreshInterval != null) {
            builder.refreshInterval(Time.of(t -> t.time(refreshInterval)));
        }
        return builder.build();
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
        BooleanResponse exists =
                executeWithRetry(
                        () -> elasticSearchClient.indices().existsAlias(r -> r.name(aliasName)));
        if (exists.value()) {
            return;
        }

        String indexName = aliasName + "-000001";
        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .indices()
                            .create(
                                    r ->
                                            r.index(indexName)
                                                    .aliases(aliasName, a -> a.isWriteIndex(true)));
                    return null;
                });
        logger.info("Created write index '{}' for alias '{}'", indexName, aliasName);
    }

    // task logs, messages, and event executions are ILM-managed rollover indices (alias + write
    // index) similar to workflow/task indices.

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
        HealthStatus waitForStatus = parseHealthStatus(clusterHealthColor);
        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .cluster()
                            .health(
                                    h ->
                                            h.waitForStatus(waitForStatus)
                                                    .timeout(Time.of(t -> t.time("30s"))));
                    return null;
                });
    }

    private Query boolQueryBuilder(String expression, String queryString) throws ParserException {
        Query queryBuilder = Query.of(q -> q.matchAll(m -> m));
        if (StringUtils.isNotEmpty(expression)) {
            Expression exp = Expression.fromString(expression);
            queryBuilder = exp.getFilterBuilder();
        }
        Query baseQuery = queryBuilder;
        Query filterQuery = Query.of(q -> q.bool(b -> b.must(baseQuery)));
        Query stringQuery = Query.of(q -> q.simpleQueryString(qs -> qs.query(queryString)));
        return Query.of(q -> q.bool(b -> b.must(stringQuery).must(filterQuery)));
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

    private static String normalizeResourcePrefix(String indexPrefix) {
        String prefix = StringUtils.trimToNull(indexPrefix);
        if (prefix == null) {
            return null;
        }
        prefix = StringUtils.stripEnd(prefix, "_-");
        return StringUtils.trimToNull(prefix);
    }

    private static String prefixedResourceName(String resourcePrefix, String baseName) {
        if (StringUtils.isBlank(resourcePrefix)) {
            return baseName;
        }
        if (StringUtils.isBlank(baseName)) {
            throw new IllegalArgumentException("baseName must not be blank");
        }
        return resourcePrefix + "-" + baseName;
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
            Refresh refresh = properties.isWaitForIndexRefresh() ? Refresh.WaitFor : null;
            executeWithRetry(
                    () ->
                            elasticSearchClient.index(
                                    i -> {
                                        i.index(workflowIndexName)
                                                .id(workflowId)
                                                .document(workflow);
                                        if (refresh != null) {
                                            i.refresh(refresh);
                                        }
                                        return i;
                                    }));
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

            Refresh refreshPolicy = properties.isWaitForIndexRefresh() ? Refresh.WaitFor : null;
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
                    BulkOperation.of(op -> op.index(i -> i.index(logIndexName).document(log))));
        }

        try {
            executeWithRetry(() -> elasticSearchClient.bulk(b -> b.operations(operations)));
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
                                                                                            SortOrder
                                                                                                    .Asc)))
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
                                                                                            SortOrder
                                                                                                    .Asc)))
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
            List<String> taskIds = findTaskIdsForWorkflow(workflowId);
            if (!taskIds.isEmpty()) {
                deleteTaskLogsByTaskIds(taskIds);
            }
            deleteTasksByWorkflowId(workflowId);

            DeleteResponse response =
                    executeWithRetry(
                            () ->
                                    elasticSearchClient.delete(
                                            d -> d.index(workflowIndexName).id(workflowId)));

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
            executeWithRetry(
                    () ->
                            elasticSearchClient.update(
                                    u ->
                                            u.index(workflowIndexName)
                                                    .id(workflowInstanceId)
                                                    .doc(source),
                                    Map.class));
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
        }

        try {
            DeleteResponse response =
                    executeWithRetry(
                            () ->
                                    elasticSearchClient.delete(
                                            d -> d.index(taskIndexName).id(taskId)));

            if (response.result() != Result.Deleted) {
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
            executeWithRetry(
                    () ->
                            elasticSearchClient.update(
                                    u -> u.index(taskIndexName).id(taskId).doc(source), Map.class));
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
                            g -> g.index(workflowIndexName).id(workflowInstanceId), Map.class);
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
            String indexName, Query queryBuilder, int start, int size, List<String> sortOptions)
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
                response.hits().hits().stream().map(hit -> hit.id()).collect(Collectors.toList());
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
            options.add(SortOptions.of(s -> s.field(f -> f.field(sortField).order(sortOrder))));
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
        String archiveFrom = LocalDate.now().minusDays(archiveTtlDays).minusDays(1).toString();
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
                                                                                                r
                                                                                                        .untyped(
                                                                                                                u ->
                                                                                                                        u.field(
                                                                                                                                        "endTime")
                                                                                                                                .lt(
                                                                                                                                        JsonData
                                                                                                                                                .of(
                                                                                                                                                        archiveTo))
                                                                                                                                .gte(
                                                                                                                                        JsonData
                                                                                                                                                .of(
                                                                                                                                                        archiveFrom))))))
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
                                                                                                e
                                                                                                        .field(
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
        long fromMillis = now.minus(Duration.ofHours(lastModifiedHoursAgoFrom)).toEpochMilli();
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
                                                                                                r
                                                                                                        .untyped(
                                                                                                                u ->
                                                                                                                        u.field(
                                                                                                                                        "updateTime")
                                                                                                                                .gt(
                                                                                                                                        JsonData
                                                                                                                                                .of(
                                                                                                                                                        fromMillis))))))
                                                        .must(
                                                                Query.of(
                                                                        q1 ->
                                                                                q1.range(
                                                                                        r ->
                                                                                                r
                                                                                                        .untyped(
                                                                                                                u ->
                                                                                                                        u.field(
                                                                                                                                        "updateTime")
                                                                                                                                .lt(
                                                                                                                                        JsonData
                                                                                                                                                .of(
                                                                                                                                                        toMillis))))))
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
                                "logQueue",
                                ((ThreadPoolExecutor) logExecutorService).getQueue().size());

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
                                    "Bulk indexing retry failed for doc type {}",
                                    docType,
                                    retryException);
                        }
                        Monitors.recordWorkerQueueSize(
                                "indexQueue",
                                ((ThreadPoolExecutor) executorService).getQueue().size());
                        Monitors.recordWorkerQueueSize(
                                "logQueue",
                                ((ThreadPoolExecutor) logExecutorService).getQueue().size());
                    }
                };

        return BulkIngester.of(
                builder -> {
                    builder.client(elasticSearchAsyncClient);
                    builder.maxOperations(indexBatchSize);
                    builder.maxConcurrentRequests(
                            Math.max(1, Math.min(4, properties.getAsyncMaxPoolSize())));
                    if (asyncBufferFlushTimeout > 0) {
                        builder.flushInterval(
                                asyncBufferFlushTimeout, TimeUnit.SECONDS, bulkScheduler);
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
