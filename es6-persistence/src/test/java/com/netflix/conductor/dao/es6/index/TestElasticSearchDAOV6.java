package com.netflix.conductor.dao.es6.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.ElasticSearchTransportClientProvider;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.SystemPropertiesElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.es6.EmbeddedElasticSearchV6;
import com.netflix.conductor.support.TestUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestElasticSearchDAOV6 {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMWW");

    private static final String INDEX_PREFIX = "conductor";
    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String MSG_DOC_TYPE = "message";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String LOG_INDEX_PREFIX = "task_log";

    private static ElasticSearchConfiguration configuration;
    private static Client elasticSearchClient;
    private static ElasticSearchDAOV6 indexDAO;
    private static EmbeddedElasticSearch embeddedElasticSearch;

    @BeforeClass
    public static void startServer() throws Exception {
        System.setProperty(ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME, "9203");
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME, "localhost:9303");
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_INDEX_BATCH_SIZE_PROPERTY_NAME, "1");

        configuration = new SystemPropertiesElasticSearchConfiguration();
        String host = configuration.getEmbeddedHost();
        int port = configuration.getEmbeddedPort();
        String clusterName = configuration.getEmbeddedClusterName();

        embeddedElasticSearch = new EmbeddedElasticSearchV6(clusterName, host, port);
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

        ObjectMapper objectMapper = new JsonMapperProvider().get();
        indexDAO = new ElasticSearchDAOV6(elasticSearchClient, configuration, objectMapper);
    }

    @AfterClass
    public static void closeClient() throws Exception {
        if (elasticSearchClient != null) {
            elasticSearchClient.close();
        }

        embeddedElasticSearch.stop();
    }

    @Before
    public void setup() throws Exception {
        indexDAO.setup();
    }

    @After
    public void tearDown() {
        deleteAllIndices();
    }

    private static void deleteAllIndices() {
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

    @Test
    public void assertInitialSetup() {
        SIMPLE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));

        String workflowIndex = INDEX_PREFIX + "_" + WORKFLOW_DOC_TYPE;
        String taskIndex = INDEX_PREFIX + "_" + TASK_DOC_TYPE;

        String taskLogIndex = INDEX_PREFIX + "_" + LOG_INDEX_PREFIX + "_" + SIMPLE_DATE_FORMAT.format(new Date());
        String messageIndex = INDEX_PREFIX + "_" + MSG_DOC_TYPE + "_" + SIMPLE_DATE_FORMAT.format(new Date());
        String eventIndex = INDEX_PREFIX + "_" + EVENT_DOC_TYPE + "_" + SIMPLE_DATE_FORMAT.format(new Date());

        assertTrue("Index 'conductor_workflow' should exist", indexExists("conductor_workflow"));
        assertTrue("Index 'conductor_task' should exist", indexExists("conductor_task"));

        assertTrue("Index '" + taskLogIndex + "' should exist", indexExists(taskLogIndex));
        assertTrue("Index '" + messageIndex + "' should exist", indexExists(messageIndex));
        assertTrue("Index '" + eventIndex + "' should exist", indexExists(eventIndex));

        assertTrue("Mapping 'workflow' for index 'conductor' should exist", doesMappingExist(workflowIndex, WORKFLOW_DOC_TYPE));
        assertTrue("Mapping 'task' for index 'conductor' should exist", doesMappingExist(taskIndex, TASK_DOC_TYPE));
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
    public void shouldIndexWorkflow() {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        WorkflowSummary summary = new WorkflowSummary(workflow);

        indexDAO.indexWorkflow(workflow);

        assertWorkflowSummary(workflow.getWorkflowId(), summary);
    }

    @Test
    public void shouldIndexWorkflowAsync() throws Exception {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        WorkflowSummary summary = new WorkflowSummary(workflow);

        indexDAO.asyncIndexWorkflow(workflow).get();

        assertWorkflowSummary(workflow.getWorkflowId(), summary);
    }

    @Test
    public void shouldRemoveWorkflow() {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        indexDAO.indexWorkflow(workflow);

        // wait for workflow to be indexed
        List<String> workflows = tryFindResults(() -> searchWorkflows(workflow.getWorkflowId()), 1);
        assertEquals(1, workflows.size());

        indexDAO.removeWorkflow(workflow.getWorkflowId());

        workflows = tryFindResults(() -> searchWorkflows(workflow.getWorkflowId()), 0);

        assertTrue("Workflow was not removed.", workflows.isEmpty());
    }

    @Test
    public void shouldAsyncRemoveWorkflow() throws Exception {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        indexDAO.indexWorkflow(workflow);

        // wait for workflow to be indexed
        List<String> workflows = tryFindResults(() -> searchWorkflows(workflow.getWorkflowId()), 1);
        assertEquals(1, workflows.size());

        indexDAO.asyncRemoveWorkflow(workflow.getWorkflowId()).get();

        workflows = tryFindResults(() -> searchWorkflows(workflow.getWorkflowId()), 0);

        assertTrue("Workflow was not removed.", workflows.isEmpty());
    }

    @Test
    public void shouldUpdateWorkflow() {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        WorkflowSummary summary = new WorkflowSummary(workflow);

        indexDAO.indexWorkflow(workflow);

        indexDAO.updateWorkflow(workflow.getWorkflowId(), new String[]{"status"}, new Object[]{Workflow.WorkflowStatus.COMPLETED});

        summary.setStatus(Workflow.WorkflowStatus.COMPLETED);
        assertWorkflowSummary(workflow.getWorkflowId(), summary);
    }

    @Test
    public void shouldAsyncUpdateWorkflow() throws Exception {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        WorkflowSummary summary = new WorkflowSummary(workflow);

        indexDAO.indexWorkflow(workflow);

        indexDAO.asyncUpdateWorkflow(workflow.getWorkflowId(), new String[]{"status"}, new Object[]{Workflow.WorkflowStatus.FAILED}).get();

        summary.setStatus(Workflow.WorkflowStatus.FAILED);
        assertWorkflowSummary(workflow.getWorkflowId(), summary);
    }

    @Test
    public void shouldIndexTask() {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        Task task = workflow.getTasks().get(0);

        TaskSummary summary = new TaskSummary(task);

        indexDAO.indexTask(task);

        List<String> tasks = tryFindResults(() -> searchTasks(workflow));

        assertEquals(summary.getTaskId(), tasks.get(0));
    }

    @Test
    public void indexTaskWithBatchSizeTwo() throws Exception {
        embeddedElasticSearch.stop();
        startElasticSearchWithBatchSize(2);
        String correlationId = "some-correlation-id";

        Task task = new Task();
        task.setTaskId("some-task-id");
        task.setWorkflowInstanceId("some-workflow-instance-id");
        task.setTaskType("some-task-type");
        task.setStatus(Task.Status.FAILED);
        task.setInputData(new HashMap<String, Object>() {{ put("input_key", "input_value"); }});
        task.setCorrelationId(correlationId);
        task.setTaskDefName("some-task-def-name");
        task.setReasonForIncompletion("some-failure-reason");

        indexDAO.indexTask(task);
        indexDAO.indexTask(task);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    SearchResult<String> result = indexDAO
                            .searchTasks("correlationId='" + correlationId + "'", "*", 0, 10000, null);

                    assertTrue("should return 1 or more search results", result.getResults().size() > 0);
                    assertEquals("taskId should match the indexed task", "some-task-id", result.getResults().get(0));
                });

        embeddedElasticSearch.stop();
        startElasticSearchWithBatchSize(1);
    }

    private void startElasticSearchWithBatchSize(int i) throws Exception {
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_INDEX_BATCH_SIZE_PROPERTY_NAME, String.valueOf(i));

        configuration = new SystemPropertiesElasticSearchConfiguration();
        String host = configuration.getEmbeddedHost();
        int port = configuration.getEmbeddedPort();
        String clusterName = configuration.getEmbeddedClusterName();

        embeddedElasticSearch = new EmbeddedElasticSearchV6(clusterName, host, port);
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

        ObjectMapper objectMapper = new JsonMapperProvider().get();
        indexDAO = new ElasticSearchDAOV6(elasticSearchClient, configuration, objectMapper);
    }

    @Test
    public void shouldIndexTaskAsync() throws Exception {
        Workflow workflow = TestUtils.loadWorkflowSnapshot("workflow");
        Task task = workflow.getTasks().get(0);

        TaskSummary summary = new TaskSummary(task);

        indexDAO.asyncIndexTask(task).get();

        List<String> tasks = tryFindResults(() -> searchTasks(workflow));

        assertEquals(summary.getTaskId(), tasks.get(0));
    }

    @Test
    public void shouldAddTaskExecutionLogs() {
        List<TaskExecLog> logs = new ArrayList<>();
        String taskId = uuid();
        logs.add(createLog(taskId, "log1"));
        logs.add(createLog(taskId, "log2"));
        logs.add(createLog(taskId, "log3"));

        indexDAO.addTaskExecutionLogs(logs);

        List<TaskExecLog> indexedLogs = tryFindResults(() -> indexDAO.getTaskExecutionLogs(taskId), 3);

        assertEquals(3, indexedLogs.size());

        assertTrue("Not all logs was indexed", indexedLogs.containsAll(logs));
    }

    @Test
    public void shouldAddTaskExecutionLogsAsync() throws Exception {
        List<TaskExecLog> logs = new ArrayList<>();
        String taskId = uuid();
        logs.add(createLog(taskId, "log1"));
        logs.add(createLog(taskId, "log2"));
        logs.add(createLog(taskId, "log3"));

        indexDAO.asyncAddTaskExecutionLogs(logs).get();

        List<TaskExecLog> indexedLogs = tryFindResults(() -> indexDAO.getTaskExecutionLogs(taskId), 3);

        assertEquals(3, indexedLogs.size());

        assertTrue("Not all logs was indexed", indexedLogs.containsAll(logs));
    }

    @Test
    public void shouldAddMessage() {
        String queue = "queue";
        Message message1 = new Message(uuid(), "payload1", null);
        Message message2 = new Message(uuid(), "payload2", null);

        indexDAO.addMessage(queue, message1);
        indexDAO.addMessage(queue, message2);

        List<Message> indexedMessages = tryFindResults(() -> indexDAO.getMessages(queue), 2);

        assertEquals(2, indexedMessages.size());

        assertTrue("Not all messages was indexed", indexedMessages.containsAll(Arrays.asList(message1, message2)));
    }

    @Test
    public void shouldAddEventExecution() {
        String event = "event";
        EventExecution execution1 = createEventExecution(event);
        EventExecution execution2 = createEventExecution(event);

        indexDAO.addEventExecution(execution1);
        indexDAO.addEventExecution(execution2);

        List<EventExecution> indexedExecutions = tryFindResults(() -> indexDAO.getEventExecutions(event), 2);

        assertEquals(2, indexedExecutions.size());

        assertTrue("Not all event executions was indexed", indexedExecutions.containsAll(Arrays.asList(execution1, execution2)));
    }

    @Test
    public void shouldAsyncAddEventExecution() throws Exception {
        String event = "event2";
        EventExecution execution1 = createEventExecution(event);
        EventExecution execution2 = createEventExecution(event);

        indexDAO.asyncAddEventExecution(execution1).get();
        indexDAO.asyncAddEventExecution(execution2).get();

        List<EventExecution> indexedExecutions = tryFindResults(() -> indexDAO.getEventExecutions(event), 2);

        assertEquals(2, indexedExecutions.size());

        assertTrue("Not all event executions was indexed", indexedExecutions.containsAll(Arrays.asList(execution1, execution2)));
    }

    @Test
    public void shouldAddIndexPrefixToIndexTemplate() throws Exception {
        String json = TestUtils.loadJsonResource("expected_template_task_log");

        String content = indexDAO.loadTypeMappingSource("/template_task_log.json");

        assertEquals(json, content);
    }

    @Test
    public void shouldSearchRecentRunningWorkflows() throws Exception {
        Workflow oldWorkflow = TestUtils.loadWorkflowSnapshot("workflow");
        oldWorkflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        oldWorkflow.setUpdateTime(new DateTime().minusHours(2).toDate().getTime());

        Workflow recentWorkflow = TestUtils.loadWorkflowSnapshot("workflow");
        recentWorkflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        recentWorkflow.setUpdateTime(new DateTime().minusHours(1).toDate().getTime());

        Workflow tooRecentWorkflow = TestUtils.loadWorkflowSnapshot("workflow");
        tooRecentWorkflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        tooRecentWorkflow.setUpdateTime(new DateTime().toDate().getTime());

        indexDAO.indexWorkflow(oldWorkflow);
        indexDAO.indexWorkflow(recentWorkflow);
        indexDAO.indexWorkflow(tooRecentWorkflow);

        Thread.sleep(1000);

        List<String> ids = indexDAO.searchRecentRunningWorkflows(2, 1);

        assertEquals(1, ids.size());
        assertEquals(recentWorkflow.getWorkflowId(), ids.get(0));
    }

    private void assertWorkflowSummary(String workflowId, WorkflowSummary summary) {
        assertEquals(summary.getWorkflowType(), indexDAO.get(workflowId, "workflowType"));
        assertEquals(String.valueOf(summary.getVersion()), indexDAO.get(workflowId, "version"));
        assertEquals(summary.getWorkflowId(), indexDAO.get(workflowId, "workflowId"));
        assertEquals(summary.getCorrelationId(), indexDAO.get(workflowId, "correlationId"));
        assertEquals(summary.getStartTime(), indexDAO.get(workflowId, "startTime"));
        assertEquals(summary.getUpdateTime(), indexDAO.get(workflowId, "updateTime"));
        assertEquals(summary.getEndTime(), indexDAO.get(workflowId, "endTime"));
        assertEquals(summary.getStatus().name(), indexDAO.get(workflowId, "status"));
        assertEquals(summary.getInput(), indexDAO.get(workflowId, "input"));
        assertEquals(summary.getOutput(), indexDAO.get(workflowId, "output"));
        assertEquals(summary.getReasonForIncompletion(), indexDAO.get(workflowId, "reasonForIncompletion"));
        assertEquals(String.valueOf(summary.getExecutionTime()), indexDAO.get(workflowId, "executionTime"));
        assertEquals(summary.getEvent(), indexDAO.get(workflowId, "event"));
        assertEquals(summary.getFailedReferenceTaskNames(), indexDAO.get(workflowId, "failedReferenceTaskNames"));
    }

    private <T> List<T> tryFindResults(Supplier<List<T>> searchFunction) {
        return tryFindResults(searchFunction, 1);
    }

    private <T> List<T> tryFindResults(Supplier<List<T>> searchFunction, int resultsCount) {
        List<T> result = Collections.emptyList();
        for (int i = 0; i < 20; i++) {
            result = searchFunction.get();
            if (result.size() == resultsCount) {
                return result;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return result;
    }

    private List<String> searchWorkflows(String workflowId) {
        return indexDAO.searchWorkflows("", "workflowId:\"" + workflowId + "\"", 0, 100, Collections.emptyList()).getResults();
    }

    private List<String> searchTasks(Workflow workflow) {
        return indexDAO.searchTasks("", "workflowId:\"" + workflow.getWorkflowId() + "\"", 0, 100, Collections.emptyList()).getResults();
    }

    private TaskExecLog createLog(String taskId, String log) {
        TaskExecLog taskExecLog = new TaskExecLog(log);
        taskExecLog.setTaskId(taskId);
        return taskExecLog;
    }

    private EventExecution createEventExecution(String event) {
        EventExecution execution = new EventExecution(uuid(), uuid());
        execution.setName("name");
        execution.setEvent(event);
        execution.setCreated(System.currentTimeMillis());
        execution.setStatus(EventExecution.Status.COMPLETED);
        execution.setAction(EventHandler.Action.Type.start_workflow);
        execution.setOutput(ImmutableMap.of("a", 1, "b", 2, "c", 3));
        return execution;
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

}
