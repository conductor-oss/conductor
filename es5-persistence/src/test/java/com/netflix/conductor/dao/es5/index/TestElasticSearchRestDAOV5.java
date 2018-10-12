package com.netflix.conductor.dao.es5.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.es5.EmbeddedElasticSearchV5;
import com.netflix.conductor.dao.es5.TestConfiguration;
import org.elasticsearch.client.RestClient;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.Assert.*;


public class TestElasticSearchRestDAOV5 {

    private static Logger logger = LoggerFactory.getLogger(TestElasticSearchRestDAOV5.class);

    private static RestClient restClient;
    private static ElasticSearchRestDAOV5 indexDAO;

    private Workflow workflow;

    @BeforeClass
    public static void setup() throws Exception {

        EmbeddedElasticSearchV5.start();

        Configuration configuration = new TestConfiguration();
        ElasticSearchModuleV5 esRestModule = new ElasticSearchModuleV5(true);

        restClient = esRestModule.getRestClient(configuration);

        long startTime = System.currentTimeMillis();

        Map<String, String> params = new HashMap<>();
        params.put("wait_for_status", "yellow");
        params.put("timeout", "30s");

        while (true) {
            try {
                restClient.performRequest("GET", "/_cluster/health", params);
                break;
            } catch (IOException e) {
                logger.info("No ES nodes available yet.");
            }
            Thread.sleep(10000);

            if (System.currentTimeMillis() - startTime > 60000) {
                logger.error("Unable to connect to the ES cluster in time.");
                throw new RuntimeException("Unable to connect to ES cluster in time.");
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        indexDAO = new ElasticSearchRestDAOV5(restClient, configuration, objectMapper);

    }

    @AfterClass
    public static void closeClient() throws Exception {
        if (restClient != null) {
            restClient.close();
        }

        EmbeddedElasticSearchV5.stop();
    }

    @Before
    public void createTestWorkflow() {

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
    public void testNoResultsWhenCleanIndex() {

        SearchResult<String> results = indexDAO.searchWorkflows("", "*", 0, 10, null);
        assertEquals("Should have no results on an empty cluster", 0, results.getTotalHits());
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

}
