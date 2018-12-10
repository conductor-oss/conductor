package com.netflix.conductor.dao.es5.index;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventHandler.Action.Type;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.es5.index.query.parser.Expression;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.ElasticSearchRestClientProvider;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.SystemPropertiesElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.es5.EmbeddedElasticSearchV5;
import com.netflix.conductor.elasticsearch.query.parser.ParserException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestElasticSearchRestDAOV5 {

    private static final Logger logger = LoggerFactory.getLogger(TestElasticSearchRestDAOV5.class);

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMww");

    private static final String INDEX_NAME = "conductor";
    private static final String LOG_INDEX_PREFIX = "task_log";

    private static final String MSG_DOC_TYPE = "message";
    private static final String EVENT_DOC_TYPE = "event";

    private static ElasticSearchConfiguration configuration;
    private static RestClient restClient;
    private static RestHighLevelClient elasticSearchClient;
    private static ElasticSearchRestDAOV5 indexDAO;
    private static EmbeddedElasticSearch embeddedElasticSearch;
    private static ObjectMapper objectMapper;

    private Workflow workflow;

    private @interface HttpMethod {
        String GET = "GET";
        String POST = "POST";
        String PUT = "PUT";
        String HEAD = "HEAD";
        String DELETE = "DELETE";
    }

    @BeforeClass
    public static void startServer() throws Exception {
        System.setProperty(ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME, "9204");
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME, "http://localhost:9204");

        configuration = new SystemPropertiesElasticSearchConfiguration();

        String host = configuration.getEmbeddedHost();
        int port = configuration.getEmbeddedPort();
        String clusterName = configuration.getEmbeddedClusterName();

        embeddedElasticSearch = new EmbeddedElasticSearchV5(clusterName, host, port);
        embeddedElasticSearch.start();

        ElasticSearchRestClientProvider restClientProvider =
            new ElasticSearchRestClientProvider(configuration);
        restClient = restClientProvider.get();
        elasticSearchClient = new RestHighLevelClient(restClient);

        Map<String, String> params = new HashMap<>();
        params.put("wait_for_status", "yellow");
        params.put("timeout", "30s");

        restClient.performRequest("GET", "/_cluster/health", params);

        objectMapper = new ObjectMapper();
        indexDAO = new ElasticSearchRestDAOV5(restClient, configuration, objectMapper);
    }

    @AfterClass
    public static void closeClient() throws Exception {
        if (restClient != null) {
            restClient.close();
        }

        embeddedElasticSearch.stop();
    }

    @Before
    public void createTestWorkflow() throws Exception {
        // define indices
        indexDAO.setup();

        // initialize workflow
        workflow = new Workflow();
        workflow.getInput().put("requestId", "request id 001");
        workflow.getInput().put("hasAwards", true);
        workflow.getInput().put("channelMapping", 5);
        Map<String, Object> name = new HashMap<>();
        name.put("name", "The Who");
        name.put("year", 1970);
        Map<String, Object> name2 = new HashMap<>();
        name2.put("name", "The Doors");
        name2.put("year", 1975);

        List<Object> names = new LinkedList<>();
        names.add(name);
        names.add(name2);

        workflow.getOutput().put("name", name);
        workflow.getOutput().put("names", names);
        workflow.getOutput().put("awards", 200);

        Task task = new Task();
        task.setReferenceTaskName("task2");
        task.getOutputData().put("location", "http://location");
        task.setStatus(Task.Status.COMPLETED);

        Task task2 = new Task();
        task2.setReferenceTaskName("task3");
        task2.getOutputData().put("refId", "abcddef_1234_7890_aaffcc");
        task2.setStatus(Task.Status.SCHEDULED);

        workflow.getTasks().add(task);
        workflow.getTasks().add(task2);
    }

    @After
    public void tearDown() throws IOException {
        deleteAllIndices();
    }

    private void deleteAllIndices() throws IOException {
        Response beforeResponse = restClient.performRequest(HttpMethod.GET, "/_cat/indices");

        Reader streamReader = new InputStreamReader(beforeResponse.getEntity().getContent());
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            String[] fields = line.split("\\s");
            String endpoint = String.format("/%s", fields[2]);

            restClient.performRequest(HttpMethod.DELETE, endpoint);
        }
    }

    private boolean indexExists(final String index) throws IOException {
        return indexDAO.doesResourceExist("/" + index);
    }

    private boolean doesMappingExist(final String index, final String mappingName) throws IOException {
        return indexDAO.doesResourceExist("/" + index + "/_mapping/" + mappingName);
    }

    @Test
    public void assertInitialSetup() throws Exception {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMww");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        String taskLogIndex = "task_log_" + dateFormat.format(new Date());

        assertTrue("Index 'conductor' should exist", indexExists("conductor"));
        assertTrue("Index '" + taskLogIndex + "' should exist", indexExists(taskLogIndex));

        assertTrue("Mapping 'workflow' for index 'conductor' should exist", doesMappingExist("conductor", "workflow"));
        assertTrue("Mapping 'task' for inndex 'conductor' should exist", doesMappingExist("conductor", "task"));
    }

    @Test
    public void testWorkflowCRUD() {

        String testWorkflowType = "testworkflow";
        String testId = "1";

        workflow.setWorkflowId(testId);
        workflow.setWorkflowType(testWorkflowType);

        // Create
        String workflowType = indexDAO.get(testId, "workflowType");
        assertNull("Workflow should not exist", workflowType);

        // Get
        indexDAO.indexWorkflow(workflow);

        workflowType = indexDAO.get(testId, "workflowType");
        assertEquals("Should have found our workflow type", testWorkflowType, workflowType);

        // Update
        String newWorkflowType = "newworkflowtype";
        String[] keyChanges = {"workflowType"};
        String[] valueChanges = {newWorkflowType};

        indexDAO.updateWorkflow(testId, keyChanges, valueChanges);

        workflowType = indexDAO.get(testId, "workflowType");
        assertEquals("Should have updated our new workflow type", newWorkflowType, workflowType);

        // Delete
        indexDAO.removeWorkflow(testId);

        workflowType = indexDAO.get(testId, "workflowType");
        assertNull("We should no longer have our workflow in the system", workflowType);
    }

    @Test
    public void taskExecutionLogs() throws Exception {
        TaskExecLog taskExecLog1 = new TaskExecLog();
        taskExecLog1.setTaskId("some-task-id");
        long createdTime1 = LocalDateTime.of(2018, 11, 01, 06, 33, 22)
            .toEpochSecond(ZoneOffset.UTC);
        taskExecLog1.setCreatedTime(createdTime1);
        taskExecLog1.setLog("some-log");
        TaskExecLog taskExecLog2 = new TaskExecLog();
        taskExecLog2.setTaskId("some-task-id");
        long createdTime2 = LocalDateTime.of(2018, 11, 01, 06, 33, 22)
            .toEpochSecond(ZoneOffset.UTC);
        taskExecLog2.setCreatedTime(createdTime2);
        taskExecLog2.setLog("some-log");
        List<TaskExecLog> logsToAdd = Arrays.asList(taskExecLog1, taskExecLog2);
        indexDAO.addTaskExecutionLogs(logsToAdd);

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                List<TaskExecLog> taskExecutionLogs = indexDAO.getTaskExecutionLogs("some-task-id");
                assertEquals(2, taskExecutionLogs.size());
            });
		}

    @Test
    public void indexTask() throws Exception {
        String correlationId = "some-correlation-id";

        Task task = new Task();
        task.setTaskId("some-task-id");
        task.setWorkflowInstanceId("some-workflow-instance-id");
        task.setTaskType("some-task-type");
        task.setStatus(Status.FAILED);
        task.setInputData(new HashMap<String, Object>() {{ put("input_key", "input_value"); }});
        task.setCorrelationId(correlationId);
        task.setTaskDefName("some-task-def-name");
        task.setReasonForIncompletion("some-failure-reason");

        indexDAO.indexTask(task);

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SearchResult<String> result = indexDAO
                    .searchTasks("correlationId='" + correlationId + "'", "*", 0, 10000, null);

                assertTrue("should return 1 or more search results", result.getResults().size() > 0);
                assertEquals("taskId should match the indexed task", "some-task-id", result.getResults().get(0));
            });
    }

    @Test
    public void addMessage() {
        String messageId = "some-message-id";

        Message message = new Message();
        message.setId(messageId);
        message.setPayload("some-payload");
        message.setReceipt("some-receipt");

        indexDAO.addMessage("some-queue", message);

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SearchResponse searchResponse = searchObjectIdsViaExpression(
                    LOG_INDEX_PREFIX + "*",
                    "messageId='" + messageId + "'",
                    0,
                    10000,
                    null,
                    "*",
                    MSG_DOC_TYPE
                );
                assertTrue("should return 1 or more search results", searchResponse.getHits().getTotalHits() > 0);

                SearchHit searchHit = searchResponse.getHits().getAt(0);
                String resourcePath =
                    String.format("/%s/%s/%s", searchHit.getIndex(), MSG_DOC_TYPE, searchHit.getId());
                Response response = restClient.performRequest(HttpMethod.GET, resourcePath);

                String responseBody = IOUtils.toString(response.getEntity().getContent());
                logger.info("responseBody: {}", responseBody);

                TypeReference<HashMap<String, Object>> typeRef =
                    new TypeReference<HashMap<String, Object>>() {};
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, typeRef);
                Map<String, Object> source = (Map<String, Object>) responseMap.get("_source");
                assertEquals("indexed message id should match", messageId, source.get("messageId"));
                assertEquals("indexed payload should match", "some-payload", source.get("payload"));
            });
    }

    @Test
    public void addEventExecution() {
        String messageId = "some-message-id";

        EventExecution eventExecution = new EventExecution();
        eventExecution.setId("some-id");
        eventExecution.setMessageId(messageId);
        eventExecution.setAction(Type.complete_task);
        eventExecution.setEvent("some-event");
        eventExecution.setStatus(EventExecution.Status.COMPLETED);

        indexDAO.addEventExecution(eventExecution);

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                SearchResponse searchResponse = searchObjectIdsViaExpression(
                    LOG_INDEX_PREFIX + "*",
                    "messageId='" + messageId + "'",
                    0,
                    10000,
                    null,
                    "*",
                    EVENT_DOC_TYPE
                );
                assertTrue("should return 1 or more search results", searchResponse.getHits().getTotalHits() > 0);

                SearchHit searchHit = searchResponse.getHits().getAt(0);
                String resourcePath =
                    String.format("/%s/%s/%s", searchHit.getIndex(), EVENT_DOC_TYPE, searchHit.getId());
                Response response = restClient.performRequest(HttpMethod.GET, resourcePath);

                String responseBody = IOUtils.toString(response.getEntity().getContent());
                TypeReference<HashMap<String, Object>> typeRef =
                    new TypeReference<HashMap<String, Object>>() {
                    };
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, typeRef);

                Map<String, Object> sourceMap = (Map<String, Object>) responseMap.get("_source");
                assertEquals("indexed id should match", "some-id", sourceMap.get("id"));
                assertEquals("indexed message id should match", messageId, sourceMap.get("messageId"));
                assertEquals("indexed action should match", Type.complete_task.name(), sourceMap.get("action"));
                assertEquals("indexed event should match", "some-event", sourceMap.get("event"));
                assertEquals("indexed status should match", EventExecution.Status.COMPLETED.name(), sourceMap.get("status"));
            });
    }

    private SearchResponse searchObjectIdsViaExpression(String indexName, String structuredQuery, int start, int size,
        List<String> sortOptions, String freeTextQuery, String docType) throws ParserException, IOException {

        // Build query
        QueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
        if(StringUtils.isNotEmpty(structuredQuery)) {
            Expression expression = Expression.fromString(structuredQuery);
            queryBuilder = expression.getFilterBuilder();
        }

        BoolQueryBuilder filterQuery = QueryBuilders.boolQuery().must(queryBuilder);
        QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery(freeTextQuery);
        BoolQueryBuilder fq = QueryBuilders.boolQuery().must(stringQuery).must(filterQuery);

        return searchObjectIds(indexName, fq, start, size, sortOptions, docType);
    }

    /**
     * Tries to find object ids for a given query in an index.
     *
     * @param indexName The name of the index.
     * @param queryBuilder The query to use for searching.
     * @param start The start to use.
     * @param size The total return size.
     * @param sortOptions A list of string options to sort in the form VALUE:ORDER; where ORDER is optional and can be either ASC OR DESC.
     * @param docType The document type to searchObjectIdsViaExpression for.
     *
     * @return The SearchResults which includes the count and IDs that were found.
     * @throws IOException If we cannot communicate with ES.
     */
    private SearchResponse searchObjectIds(String indexName, QueryBuilder queryBuilder, int start, int size, List<String> sortOptions, String docType) throws IOException {

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

        return elasticSearchClient.search(searchRequest);
    }

}
