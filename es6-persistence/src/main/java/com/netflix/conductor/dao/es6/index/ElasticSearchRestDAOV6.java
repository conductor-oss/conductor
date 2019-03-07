package com.netflix.conductor.dao.es6.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.RetryUtil;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.query.parser.ParserException;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Trace
@Singleton
public class ElasticSearchRestDAOV6 extends ElasticSearchBaseDAO implements IndexDAO {

    private static Logger logger = LoggerFactory.getLogger(ElasticSearchRestDAOV6.class);

    private static final int RETRY_COUNT = 3;
    private static final int CORE_POOL_SIZE = 6;
    private static final int MAXIMUM_POOL_SIZE = 12;
    private static final long KEEP_ALIVE_TIME = 1L;

    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String LOG_DOC_TYPE = "task_log";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String MSG_DOC_TYPE = "message";

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMww");

    private @interface HttpMethod {
        String GET = "GET";
        String POST = "POST";
        String PUT = "PUT";
        String HEAD = "HEAD";
    }

    private static final String className = ElasticSearchRestDAOV6.class.getSimpleName();

    private String workflowIndexName;

    private String taskIndexName;

    private String eventIndexPrefix;

    private String eventIndexName;

    private String messageIndexPrefix;

    private String messageIndexName;

    private String logIndexName;

    private String logIndexPrefix;

    private final String clusterHealthColor;

    private final ObjectMapper objectMapper;
    private final RestHighLevelClient elasticSearchClient;
    private final RestClient elasticSearchAdminClient;
    private final ExecutorService executorService;


    static {
        SIMPLE_DATE_FORMAT.setTimeZone(GMT);
    }

    @Inject
    public ElasticSearchRestDAOV6(RestClientBuilder restClientBuilder, ElasticSearchConfiguration config, ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.elasticSearchAdminClient = restClientBuilder.build();
        this.elasticSearchClient = new RestHighLevelClient(restClientBuilder);
        this.clusterHealthColor = config.getClusterHealthColor();

        this.indexPrefix = config.getIndexName();
        this.workflowIndexName = indexName(WORKFLOW_DOC_TYPE);
        this.taskIndexName = indexName(TASK_DOC_TYPE);
        this.logIndexPrefix = this.indexPrefix + "_" + LOG_DOC_TYPE;
        this.messageIndexPrefix = this.indexPrefix + "_" + MSG_DOC_TYPE;
        this.eventIndexPrefix = this.indexPrefix + "_" + EVENT_DOC_TYPE;

        // Set up a workerpool for performing async operations.
        this.executorService = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
    }

    @Override
    public void setup() throws Exception {
        waitForHealthyCluster();

        createIndexesTemplates();

        createWorkflowIndex();

        createTaskIndex();
    }

    private void createIndexesTemplates() {
        try {
            initIndexesTemplates();
            updateIndexesNames();
            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this::updateIndexesNames, 0, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void initIndexesTemplates() {
        initIndexTemplate(LOG_DOC_TYPE);
        initIndexTemplate(EVENT_DOC_TYPE);
        initIndexTemplate(MSG_DOC_TYPE);
    }

    /**
     * Initializes the index with the required templates and mappings.
     */
    private void initIndexTemplate(String type) {
        String template = "template_" + type;
        try {
            if (doesResourceNotExist("/_template/" + template)) {
                logger.info("Creating the index template '" + template + "'");
                InputStream stream = ElasticSearchDAOV6.class.getResourceAsStream("/" + template + ".json");
                byte[] templateSource = IOUtils.toByteArray(stream);

                HttpEntity entity = new NByteArrayEntity(templateSource, ContentType.APPLICATION_JSON);
                elasticSearchAdminClient.performRequest(HttpMethod.PUT, "/_template/" + template, Collections.emptyMap(), entity);
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
        String indexName = this.indexPrefix + "_" + type + "_" + SIMPLE_DATE_FORMAT.format(new Date());
        try {
            addIndex(indexName);
            return indexName;
        } catch (IOException e) {
            logger.error("Failed to update log index name: {}", indexName, e);
            throw new ApplicationException(e.getMessage(), e);
        }
    }

    private void createWorkflowIndex() {
        String indexName = indexName(WORKFLOW_DOC_TYPE);
        try {
            addIndex(indexName);
        } catch (IOException e) {
            logger.error("Failed to initialize index '{}'", indexName, e);
        }
        try {
            addMappingToIndex(indexName, WORKFLOW_DOC_TYPE, "/mappings_docType_workflow.json");
        } catch (IOException e) {
            logger.error("Failed to add {} mapping", WORKFLOW_DOC_TYPE);
        }
    }

    private void createTaskIndex() {
        String indexName = indexName(TASK_DOC_TYPE);
        try {
            addIndex(indexName);
        } catch (IOException e) {
            logger.error("Failed to initialize index '{}'", indexName, e);
        }
        try {
            addMappingToIndex(indexName, TASK_DOC_TYPE, "/mappings_docType_task.json");
        } catch (IOException e) {
            logger.error("Failed to add {} mapping", TASK_DOC_TYPE);
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

        logger.info("Adding index '{}'...", index);

        String resourcePath = "/" + index;

        if (doesResourceNotExist(resourcePath)) {

            try {
                elasticSearchAdminClient.performRequest(HttpMethod.PUT, resourcePath);

                logger.info("Added '{}' index", index);
            } catch (ResponseException e) {

                boolean errorCreatingIndex = true;

                Response errorResponse = e.getResponse();
                if (errorResponse.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                    JsonNode root = objectMapper.readTree(EntityUtils.toString(errorResponse.getEntity()));
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
            logger.info("Index '{}' already exists", index);
        }
    }

    /**
     * Adds a mapping type to an index if it does not exist.
     *
     * @param index           The name of the index.
     * @param mappingType     The name of the mapping type.
     * @param mappingFilename The name of the mapping file to use to add the mapping if it does not exist.
     * @throws IOException If an error occurred during requests to ES.
     */
    private void addMappingToIndex(final String index, final String mappingType, final String mappingFilename) throws IOException {

        logger.info("Adding '{}' mapping to index '{}'...", mappingType, index);

        String resourcePath = "/" + index + "/_mapping/" + mappingType;

        if (doesResourceNotExist(resourcePath)) {
            HttpEntity entity = new NByteArrayEntity(loadTypeMappingSource(mappingFilename).getBytes(), ContentType.APPLICATION_JSON);
            elasticSearchAdminClient.performRequest(HttpMethod.PUT, resourcePath, Collections.emptyMap(), entity);
            logger.info("Added '{}' mapping", mappingType);
        } else {
            logger.info("Mapping '{}' already exists", mappingType);
        }
    }

    /**
     * Determines whether a resource exists in ES. This will call a GET method to a particular path and
     * return true if status 200; false otherwise.
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
    public void indexWorkflow(Workflow workflow) {

        String workflowId = workflow.getWorkflowId();
        WorkflowSummary summary = new WorkflowSummary(workflow);

        indexObject(workflowIndexName, WORKFLOW_DOC_TYPE, workflowId, summary);
    }

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(Workflow workflow) {
        return CompletableFuture.runAsync(() -> indexWorkflow(workflow), executorService);
    }

    @Override
    public void indexTask(Task task) {

        String taskId = task.getTaskId();
        TaskSummary summary = new TaskSummary(task);

        indexObject(taskIndexName, TASK_DOC_TYPE, taskId, summary);
    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(Task task) {
        return CompletableFuture.runAsync(() -> indexTask(task), executorService);
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> taskExecLogs) {
        if (taskExecLogs.isEmpty()) {
            return;
        }

        BulkRequest bulkRequest = new BulkRequest();

        for (TaskExecLog log : taskExecLogs) {

            byte[] docBytes;
            try {
                docBytes = objectMapper.writeValueAsBytes(log);
            } catch (JsonProcessingException e) {
                logger.error("Failed to convert task log to JSON for task {}", log.getTaskId());
                continue;
            }

            IndexRequest request = new IndexRequest(logIndexName, LOG_DOC_TYPE);
            request.source(docBytes, XContentType.JSON);
            bulkRequest.add(request);
        }

        try {
            new RetryUtil<BulkResponse>().retryOnException(() -> {
                try {
                    return elasticSearchClient.bulk(bulkRequest);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, null, BulkResponse::hasFailures, RETRY_COUNT, "Indexing all execution logs into doc_type task", "addTaskExecutionLogs");
        } catch (Exception e) {
            List<String> taskIds = taskExecLogs.stream().map(TaskExecLog::getTaskId).collect(Collectors.toList());
            logger.error("Failed to index task execution logs for tasks: {}", taskIds, e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        return CompletableFuture.runAsync(() -> addTaskExecutionLogs(logs), executorService);
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        try {
            BoolQueryBuilder query = boolQueryBuilder("taskId='" + taskId + "'", "*");

            // Create the searchObjectIdsViaExpression source
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.sort(new FieldSortBuilder("createdTime").order(SortOrder.ASC));

            // Generate the actual request to send to ES.
            SearchRequest searchRequest = new SearchRequest(logIndexPrefix + "*");
            searchRequest.types(LOG_DOC_TYPE);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest);

            return mapTaskExecLogsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get task execution logs for task: {}", taskId, e);
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
            SearchRequest searchRequest = new SearchRequest(messageIndexPrefix + "*");
            searchRequest.types(MSG_DOC_TYPE);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest);
            return mapGetMessagesResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get messages for queue: {}", queue, e);
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
            SearchRequest searchRequest = new SearchRequest(eventIndexPrefix + "*");
            searchRequest.types(EVENT_DOC_TYPE);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = elasticSearchClient.search(searchRequest);

            return mapEventExecutionsResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get executions for event: {}", event, e);
        }
        return null;
    }

    private List<EventExecution> mapEventExecutionsResponse(SearchResponse response) throws IOException {
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
        Map<String, Object> doc = new HashMap<>();
        doc.put("messageId", message.getId());
        doc.put("payload", message.getPayload());
        doc.put("queue", queue);
        doc.put("created", System.currentTimeMillis());

        indexObject(messageIndexName, MSG_DOC_TYPE, doc);
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {
        String id = eventExecution.getName() + "." + eventExecution.getEvent() + "." + eventExecution.getMessageId() + "." + eventExecution.getId();

        indexObject(eventIndexName, EVENT_DOC_TYPE, id, eventExecution);
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return CompletableFuture.runAsync(() -> addEventExecution(eventExecution), executorService);
    }

    @Override
    public SearchResult<String> searchWorkflows(String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectIdsViaExpression(query, start, count, sort, freeText, WORKFLOW_DOC_TYPE);
        } catch (Exception e) {
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public SearchResult<String> searchTasks(String query, String freeText, int start, int count, List<String> sort) {
        try {
            return searchObjectIdsViaExpression(query, start, count, sort, freeText, TASK_DOC_TYPE);
        } catch (Exception e) {
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void removeWorkflow(String workflowId) {

        DeleteRequest request = new DeleteRequest(workflowIndexName, WORKFLOW_DOC_TYPE, workflowId);

        try {
            DeleteResponse response = elasticSearchClient.delete(request);

            if (response.getResult() == DocWriteResponse.Result.NOT_FOUND) {
                logger.error("Index removal failed - document not found by id: {}", workflowId);
            }

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
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT, "Number of keys and values do not match");
        }

        UpdateRequest request = new UpdateRequest(workflowIndexName, WORKFLOW_DOC_TYPE, workflowInstanceId);
        Map<String, Object> source = IntStream.range(0, keys.length).boxed()
                .collect(Collectors.toMap(i -> keys[i], i -> values[i]));
        request.doc(source);

        logger.debug("Updating workflow {} with {}", workflowInstanceId, source);

        new RetryUtil<UpdateResponse>().retryOnException(() -> {
            try {
                return elasticSearchClient.update(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, null, null, RETRY_COUNT, "Updating index for doc_type workflow", "updateWorkflow");
    }

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        return CompletableFuture.runAsync(() -> updateWorkflow(workflowInstanceId, keys, values), executorService);
    }

    @Override
    public String get(String workflowInstanceId, String fieldToGet) {

        GetRequest request = new GetRequest(workflowIndexName, WORKFLOW_DOC_TYPE, workflowInstanceId);

        GetResponse response;
        try {
            response = elasticSearchClient.get(request);
        } catch (IOException e) {
            logger.error("Unable to get Workflow: {} from ElasticSearch index: {}", workflowInstanceId, workflowIndexName, e);
            return null;
        }

        if (response.isExists()) {
            Map<String, Object> sourceAsMap = response.getSourceAsMap();
            if (sourceAsMap.get(fieldToGet) != null) {
                return sourceAsMap.get(fieldToGet).toString();
            }
        }

        logger.debug("Unable to find Workflow: {} in ElasticSearch index: {}.", workflowInstanceId, workflowIndexName);
        return null;
    }

    private SearchResult<String> searchObjectIdsViaExpression(String structuredQuery, int start, int size, List<String> sortOptions, String freeTextQuery, String docType) throws ParserException, IOException {
        QueryBuilder queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjectIds(indexName(docType), queryBuilder, start, size, sortOptions, docType);
    }

    private SearchResult<String> searchObjectIds(String indexName, QueryBuilder queryBuilder, int start, int size, String docType) throws IOException {
        return searchObjectIds(indexName, queryBuilder, start, size, null, docType);
    }

    /**
     * Tries to find object ids for a given query in an index.
     *
     * @param indexName    The name of the index.
     * @param queryBuilder The query to use for searching.
     * @param start        The start to use.
     * @param size         The total return size.
     * @param sortOptions  A list of string options to sort in the form VALUE:ORDER; where ORDER is optional and can be either ASC OR DESC.
     * @param docType      The document type to searchObjectIdsViaExpression for.
     * @return The SearchResults which includes the count and IDs that were found.
     * @throws IOException If we cannot communicate with ES.
     */
    private SearchResult<String> searchObjectIds(String indexName, QueryBuilder queryBuilder, int start, int size, List<String> sortOptions, String docType) throws IOException {

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        searchSourceBuilder.from(start);
        searchSourceBuilder.size(size);

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
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.types(docType);
        searchRequest.source(searchSourceBuilder);

        SearchResponse response = elasticSearchClient.search(searchRequest);

        List<String> result = new LinkedList<>();
        response.getHits().forEach(hit -> result.add(hit.getId()));
        long count = response.getHits().getTotalHits();
        return new SearchResult<>(count, result);
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        QueryBuilder q = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("endTime").lt(LocalDate.now().minusDays(archiveTtlDays).toString()))
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
            logger.error("Unable to communicate with ES to find archivable workflows", e);
            return Collections.emptyList();
        }

        return workflowIds.getResults();
    }

    public List<String> searchRecentRunningWorkflows(int lastModifiedHoursAgoFrom, int lastModifiedHoursAgoTo) {
        DateTime dateTime = new DateTime();
        QueryBuilder q = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("updateTime")
                        .gt(dateTime.minusHours(lastModifiedHoursAgoFrom)))
                .must(QueryBuilders.rangeQuery("updateTime")
                        .lt(dateTime.minusHours(lastModifiedHoursAgoTo)))
                .must(QueryBuilders.termQuery("status", "RUNNING"));

        SearchResult<String> workflowIds;
        try {
            workflowIds = searchObjectIds(workflowIndexName, q, 0, 5000, Collections.singletonList("updateTime:ASC"), WORKFLOW_DOC_TYPE);
        } catch (IOException e) {
            logger.error("Unable to communicate with ES to find recent running workflows", e);
            return Collections.emptyList();
        }

        return workflowIds.getResults();
    }

    private void indexObject(final String index, final String docType, final Object doc) {
        indexObject(index, docType, null, doc);
    }

    private void indexObject(final String index, final String docType, final String docId, final Object doc) {

        byte[] docBytes;
        try {
            docBytes = objectMapper.writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert {} '{}' to byte string", docType, docId);
            return;
        }

        IndexRequest request = new IndexRequest(index, docType, docId);
        request.source(docBytes, XContentType.JSON);

        indexWithRetry(request, "Indexing " + docType + ": " + docId);
    }

    /**
     * Performs an index operation with a retry.
     *
     * @param request              The index request that we want to perform.
     * @param operationDescription The type of operation that we are performing.
     */
    private void indexWithRetry(final IndexRequest request, final String operationDescription) {

        try {
            new RetryUtil<IndexResponse>().retryOnException(() -> {
                try {
                    return elasticSearchClient.index(request);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, null, null, RETRY_COUNT, operationDescription, "indexWithRetry");
        } catch (Exception e) {
            Monitors.error(className, "index");
            logger.error("Failed to index {} for request type: {}", request.id(), request.type(), e);
        }
    }

}
