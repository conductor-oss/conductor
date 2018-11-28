package com.netflix.conductor.dao.es5.index;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import com.netflix.conductor.elasticsearch.ElasticSearchTransportClientProvider;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.SystemPropertiesElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.es5.EmbeddedElasticSearchV5;
import com.netflix.conductor.elasticsearch.query.parser.ParserException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestElasticSearchDAOV5 {

	private static final Logger logger = LoggerFactory.getLogger(TestElasticSearchDAOV5.class);

	private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMww");

	private static final String MSG_DOC_TYPE = "message";
	private static final String EVENT_DOC_TYPE = "event";
	private static final String LOG_INDEX_PREFIX = "task_log";

	private static ElasticSearchConfiguration configuration;
	private static Client elasticSearchClient;
	private static ElasticSearchDAOV5 indexDAO;
	private static EmbeddedElasticSearch embeddedElasticSearch;

	private Workflow workflow;

	@BeforeClass
	public static void startServer() throws Exception {
		System.setProperty(ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME, "9203");
		System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME, "localhost:9303");

		configuration = new SystemPropertiesElasticSearchConfiguration();
		String host = configuration.getEmbeddedHost();
		int port = configuration.getEmbeddedPort();
		String clusterName = configuration.getEmbeddedClusterName();

		embeddedElasticSearch = new EmbeddedElasticSearchV5(clusterName, host, port);
		embeddedElasticSearch.start();

		ElasticSearchTransportClientProvider transportClientProvider =
				new ElasticSearchTransportClientProvider(configuration);
		elasticSearchClient = transportClientProvider.get();

		elasticSearchClient.admin()
				.cluster()
				.prepareHealth()
				.setWaitForGreenStatus()
				.execute()
				.get();

		ObjectMapper objectMapper = new ObjectMapper();
		indexDAO = new ElasticSearchDAOV5(elasticSearchClient, configuration, objectMapper);
	}

	@AfterClass
	public static void closeClient() throws Exception {
		if (elasticSearchClient != null) {
			elasticSearchClient.close();
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
	public void tearDown() {
		deleteAllIndices();
	}

	private void deleteAllIndices() {

		ImmutableOpenMap<String, IndexMetaData> indices = elasticSearchClient.admin().cluster()
				.prepareState().get().getState()
				.getMetaData().getIndices();

		indices.forEach(cursor -> {
			try {
				elasticSearchClient.admin()
						.indices()
						.delete(new DeleteIndexRequest(cursor.value.getIndex().getName()))
						.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private boolean indexExists(final String index) {
		IndicesExistsRequest request = new IndicesExistsRequest(index);
		try {
			return elasticSearchClient.admin().indices().exists(request).get().isExists();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean doesMappingExist(final String index, final String mappingName) {
		GetMappingsRequest request = new GetMappingsRequest()
				.indices(index);
		try {
			GetMappingsResponse response = elasticSearchClient.admin()
					.indices()
					.getMappings(request)
					.get();

			return response.getMappings()
					.get(index)
					.containsKey(mappingName);
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void assertInitialSetup() throws Exception {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMww");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		String taskLogIndex = "task_log_" + dateFormat.format(new Date());

		assertTrue("Index 'conductor' should exist", indexExists("conductor"));
		assertTrue("Index '" + taskLogIndex + "' should exist", indexExists(taskLogIndex));

		assertTrue("Mapping 'workflow' for index 'conductor' should exist", doesMappingExist("conductor", "workflow"));
		assertTrue("Mapping 'task' for index 'conductor' should exist", doesMappingExist("conductor", "task"));
	}

	@Test
	public void testWorkflowCRUD() throws Exception {
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

		await()
				.atMost(3, TimeUnit.SECONDS)
				.untilAsserted(
						() -> {
							String actualWorkflowType = indexDAO.get(testId, "workflowType");
							assertEquals("Should have updated our new workflow type", newWorkflowType, actualWorkflowType);
						}
				);

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
                SearchResponse searchResponse = search(
                    LOG_INDEX_PREFIX + "*",
                    "messageId='" + messageId + "'",
                    0,
                    10000,
                    "*",
                    MSG_DOC_TYPE
                );
				assertEquals("search results should be length 1", searchResponse.getHits().getTotalHits(), 1);

                SearchHit searchHit = searchResponse.getHits().getAt(0);
				GetResponse response = elasticSearchClient
                    .prepareGet(searchHit.getIndex(), MSG_DOC_TYPE, searchHit.getId())
                    .get();
				assertEquals("indexed message id should match", messageId, response.getSource().get("messageId"));
				assertEquals("indexed payload should match", "some-payload", response.getSource().get("payload"));
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
                SearchResponse searchResponse = search(
                    LOG_INDEX_PREFIX + "*",
                    "messageId='" + messageId + "'",
                    0,
                    10000,
                    "*",
                    EVENT_DOC_TYPE
                );

				assertEquals("search results should be length 1", searchResponse.getHits().getTotalHits(), 1);

				SearchHit searchHit = searchResponse.getHits().getAt(0);
				GetResponse response = elasticSearchClient
					.prepareGet(searchHit.getIndex(), EVENT_DOC_TYPE, searchHit.getId())
					.get();

				assertEquals("indexed message id should match", messageId, response.getSource().get("messageId"));
				assertEquals("indexed id should match", "some-id", response.getSource().get("id"));
				assertEquals("indexed status should match", EventExecution.Status.COMPLETED.name(), response.getSource().get("status"));
			});
	}


    private SearchResponse search(String indexName, String structuredQuery, int start,
        int size, String freeTextQuery, String docType) throws ParserException {
        QueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
        if (StringUtils.isNotEmpty(structuredQuery)) {
            Expression expression = Expression.fromString(structuredQuery);
            queryBuilder = expression.getFilterBuilder();
        }

        BoolQueryBuilder filterQuery = QueryBuilders.boolQuery().must(queryBuilder);
        QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery(freeTextQuery);
        BoolQueryBuilder fq = QueryBuilders.boolQuery().must(stringQuery).must(filterQuery);
        final SearchRequestBuilder srb = elasticSearchClient.prepareSearch(indexName)
            .setQuery(fq)
            .setTypes(docType)
            .storedFields("_id")
            .setFrom(start)
            .setSize(size);

        return srb.get();
    }

}
