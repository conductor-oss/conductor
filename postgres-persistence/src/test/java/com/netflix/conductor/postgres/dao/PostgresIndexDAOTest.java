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
package com.netflix.conductor.postgres.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.postgres.config.PostgresConfiguration;
import com.netflix.conductor.postgres.util.Query;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            PostgresConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@TestPropertySource(
        properties = {
            "conductor.app.asyncIndexingEnabled=false",
            "conductor.elasticsearch.version=0",
            "conductor.indexing.type=postgres",
            "spring.flyway.clean-disabled=false"
        })
@SpringBootTest
public class PostgresIndexDAOTest {

    @Autowired private PostgresIndexDAO indexDAO;

    @Autowired private ObjectMapper objectMapper;

    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired Flyway flyway;

    // clean the database between tests.
    @Before
    public void before() {
        flyway.migrate();
    }

    private WorkflowSummary getMockWorkflowSummary(String id) {
        WorkflowSummary wfs = new WorkflowSummary();
        wfs.setWorkflowId(id);
        wfs.setCorrelationId("correlation-id");
        wfs.setWorkflowType("workflow-type");
        wfs.setStartTime("2023-02-07T08:42:45Z");
        wfs.setStatus(Workflow.WorkflowStatus.COMPLETED);
        return wfs;
    }

    private TaskSummary getMockTaskSummary(String taskId) {
        TaskSummary ts = new TaskSummary();
        ts.setTaskId(taskId);
        ts.setTaskType("task-type");
        ts.setTaskDefName("task-def-name");
        ts.setStatus(Task.Status.COMPLETED);
        ts.setStartTime("2023-02-07T09:41:45Z");
        ts.setUpdateTime("2023-02-07T09:42:45Z");
        ts.setWorkflowType("workflow-type");
        return ts;
    }

    private TaskExecLog getMockTaskExecutionLog(String taskId, long createdTime, String log) {
        TaskExecLog tse = new TaskExecLog();
        tse.setTaskId(taskId);
        tse.setLog(log);
        tse.setCreatedTime(createdTime);
        return tse;
    }

    private void compareWorkflowSummary(WorkflowSummary wfs) throws SQLException {
        List<Map<String, Object>> result =
                queryDb(
                        String.format(
                                "SELECT * FROM workflow_index WHERE workflow_id = '%s'",
                                wfs.getWorkflowId()));
        assertEquals("Wrong number of rows returned", 1, result.size());
        assertEquals(
                "Workflow id does not match",
                wfs.getWorkflowId(),
                result.get(0).get("workflow_id"));
        assertEquals(
                "Correlation id does not match",
                wfs.getCorrelationId(),
                result.get(0).get("correlation_id"));
        assertEquals(
                "Workflow type does not match",
                wfs.getWorkflowType(),
                result.get(0).get("workflow_type"));
        TemporalAccessor ta = DateTimeFormatter.ISO_INSTANT.parse(wfs.getStartTime());
        Timestamp startTime = Timestamp.from(Instant.from(ta));
        assertEquals("Start time does not match", startTime, result.get(0).get("start_time"));
        assertEquals(
                "Status does not match", wfs.getStatus().toString(), result.get(0).get("status"));
    }

    private List<Map<String, Object>> queryDb(String query) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            try (Query q = new Query(objectMapper, c, query)) {
                return q.executeAndFetchMap();
            }
        }
    }

    private void compareTaskSummary(TaskSummary ts) throws SQLException {
        List<Map<String, Object>> result =
                queryDb(
                        String.format(
                                "SELECT * FROM task_index WHERE task_id = '%s'", ts.getTaskId()));
        assertEquals("Wrong number of rows returned", 1, result.size());
        assertEquals("Task id does not match", ts.getTaskId(), result.get(0).get("task_id"));
        assertEquals("Task type does not match", ts.getTaskType(), result.get(0).get("task_type"));
        assertEquals(
                "Task def name does not match",
                ts.getTaskDefName(),
                result.get(0).get("task_def_name"));
        TemporalAccessor startTa = DateTimeFormatter.ISO_INSTANT.parse(ts.getStartTime());
        Timestamp startTime = Timestamp.from(Instant.from(startTa));
        assertEquals("Start time does not match", startTime, result.get(0).get("start_time"));
        TemporalAccessor updateTa = DateTimeFormatter.ISO_INSTANT.parse(ts.getUpdateTime());
        Timestamp updateTime = Timestamp.from(Instant.from(updateTa));
        assertEquals("Update time does not match", updateTime, result.get(0).get("update_time"));
        assertEquals(
                "Status does not match", ts.getStatus().toString(), result.get(0).get("status"));
        assertEquals(
                "Workflow type does not match",
                ts.getWorkflowType().toString(),
                result.get(0).get("workflow_type"));
    }

    @Test
    public void testIndexNewWorkflow() throws SQLException {
        WorkflowSummary wfs = getMockWorkflowSummary("workflow-id");

        indexDAO.indexWorkflow(wfs);

        compareWorkflowSummary(wfs);
    }

    @Test
    public void testIndexExistingWorkflow() throws SQLException {
        WorkflowSummary wfs = getMockWorkflowSummary("workflow-id");

        indexDAO.indexWorkflow(wfs);

        compareWorkflowSummary(wfs);

        wfs.setStatus(Workflow.WorkflowStatus.FAILED);

        indexDAO.indexWorkflow(wfs);

        compareWorkflowSummary(wfs);
    }

    @Test
    public void testIndexNewTask() throws SQLException {
        TaskSummary ts = getMockTaskSummary("task-id");

        indexDAO.indexTask(ts);

        compareTaskSummary(ts);
    }

    @Test
    public void testIndexExistingTask() throws SQLException {
        TaskSummary ts = getMockTaskSummary("task-id");

        indexDAO.indexTask(ts);

        compareTaskSummary(ts);

        ts.setStatus(Task.Status.FAILED);

        indexDAO.indexTask(ts);

        compareTaskSummary(ts);
    }

    @Test
    public void testAddTaskExecutionLogs() throws SQLException {
        List<TaskExecLog> logs = new ArrayList<>();
        String taskId = UUID.randomUUID().toString();
        logs.add(getMockTaskExecutionLog(taskId, 1675845986000L, "Log 1"));
        logs.add(getMockTaskExecutionLog(taskId, 1675845987000L, "Log 2"));

        indexDAO.addTaskExecutionLogs(logs);

        List<Map<String, Object>> records =
                queryDb("SELECT * FROM task_execution_logs ORDER BY created_time ASC");
        assertEquals("Wrong number of logs returned", 2, records.size());
        assertEquals(logs.get(0).getLog(), records.get(0).get("log"));
        assertEquals(new Date(1675845986000L), records.get(0).get("created_time"));
        assertEquals(logs.get(1).getLog(), records.get(1).get("log"));
        assertEquals(new Date(1675845987000L), records.get(1).get("created_time"));
    }

    @Test
    public void testSearchWorkflowSummary() {
        WorkflowSummary wfs = getMockWorkflowSummary("workflow-id");

        indexDAO.indexWorkflow(wfs);

        String query = String.format("workflowId=\"%s\"", wfs.getWorkflowId());
        SearchResult<WorkflowSummary> results =
                indexDAO.searchWorkflowSummary(query, "*", 0, 15, new ArrayList());
        assertEquals("No results returned", 1, results.getResults().size());
        assertEquals(
                "Wrong workflow returned",
                wfs.getWorkflowId(),
                results.getResults().get(0).getWorkflowId());
    }

    @Test
    public void testFullTextSearchWorkflowSummary() {
        WorkflowSummary wfs = getMockWorkflowSummary("workflow-id");

        indexDAO.indexWorkflow(wfs);

        String freeText = "notworkflow-id";
        SearchResult<WorkflowSummary> results =
                indexDAO.searchWorkflowSummary("", freeText, 0, 15, new ArrayList());
        assertEquals("Wrong number of results returned", 0, results.getResults().size());

        freeText = "workflow-id";
        results = indexDAO.searchWorkflowSummary("", freeText, 0, 15, new ArrayList());
        assertEquals("No results returned", 1, results.getResults().size());
        assertEquals(
                "Wrong workflow returned",
                wfs.getWorkflowId(),
                results.getResults().get(0).getWorkflowId());
    }

    @Test
    public void testJsonSearchWorkflowSummary() {
        WorkflowSummary wfs = getMockWorkflowSummary("workflow-id");
        wfs.setVersion(3);

        indexDAO.indexWorkflow(wfs);

        String freeText = "{\"correlationId\":\"not-the-id\"}";
        SearchResult<WorkflowSummary> results =
                indexDAO.searchWorkflowSummary("", freeText, 0, 15, new ArrayList());
        assertEquals("Wrong number of results returned", 0, results.getResults().size());

        freeText = "{\"correlationId\":\"correlation-id\", \"version\":3}";
        results = indexDAO.searchWorkflowSummary("", freeText, 0, 15, new ArrayList());
        assertEquals("No results returned", 1, results.getResults().size());
        assertEquals(
                "Wrong workflow returned",
                wfs.getWorkflowId(),
                results.getResults().get(0).getWorkflowId());
    }

    @Test
    public void testSearchWorkflowSummaryPagination() {
        for (int i = 0; i < 5; i++) {
            WorkflowSummary wfs = getMockWorkflowSummary("workflow-id-" + i);
            indexDAO.indexWorkflow(wfs);
        }

        List<String> orderBy = Arrays.asList(new String[] {"workflowId:DESC"});
        SearchResult<WorkflowSummary> results =
                indexDAO.searchWorkflowSummary("", "*", 0, 2, orderBy);
        assertEquals("Wrong totalHits returned", 3, results.getTotalHits());
        assertEquals("Wrong number of results returned", 2, results.getResults().size());
        assertEquals(
                "Results returned in wrong order",
                "workflow-id-4",
                results.getResults().get(0).getWorkflowId());
        assertEquals(
                "Results returned in wrong order",
                "workflow-id-3",
                results.getResults().get(1).getWorkflowId());
        results = indexDAO.searchWorkflowSummary("", "*", 2, 2, orderBy);
        assertEquals("Wrong totalHits returned", 5, results.getTotalHits());
        assertEquals("Wrong number of results returned", 2, results.getResults().size());
        assertEquals(
                "Results returned in wrong order",
                "workflow-id-2",
                results.getResults().get(0).getWorkflowId());
        assertEquals(
                "Results returned in wrong order",
                "workflow-id-1",
                results.getResults().get(1).getWorkflowId());
        results = indexDAO.searchWorkflowSummary("", "*", 4, 2, orderBy);
        assertEquals("Wrong totalHits returned", 7, results.getTotalHits());
        assertEquals("Wrong number of results returned", 2, results.getResults().size());
        assertEquals(
                "Results returned in wrong order",
                "workflow-id-0",
                results.getResults().get(0).getWorkflowId());
    }

    @Test
    public void testSearchTaskSummary() {
        TaskSummary ts = getMockTaskSummary("task-id");

        indexDAO.indexTask(ts);

        String query = String.format("taskId=\"%s\"", ts.getTaskId());
        SearchResult<TaskSummary> results =
                indexDAO.searchTaskSummary(query, "*", 0, 15, new ArrayList());
        assertEquals("No results returned", 1, results.getResults().size());
        assertEquals(
                "Wrong task returned", ts.getTaskId(), results.getResults().get(0).getTaskId());
    }

    @Test
    public void testSearchTaskSummaryPagination() {
        for (int i = 0; i < 5; i++) {
            TaskSummary ts = getMockTaskSummary("task-id-" + i);
            indexDAO.indexTask(ts);
        }

        List<String> orderBy = Arrays.asList(new String[] {"taskId:DESC"});
        SearchResult<TaskSummary> results = indexDAO.searchTaskSummary("", "*", 0, 2, orderBy);
        assertEquals("Wrong totalHits returned", 3, results.getTotalHits());
        assertEquals("Wrong number of results returned", 2, results.getResults().size());
        assertEquals(
                "Results returned in wrong order",
                "task-id-4",
                results.getResults().get(0).getTaskId());
        assertEquals(
                "Results returned in wrong order",
                "task-id-3",
                results.getResults().get(1).getTaskId());
        results = indexDAO.searchTaskSummary("", "*", 2, 2, orderBy);
        assertEquals("Wrong totalHits returned", 5, results.getTotalHits());
        assertEquals("Wrong number of results returned", 2, results.getResults().size());
        assertEquals(
                "Results returned in wrong order",
                "task-id-2",
                results.getResults().get(0).getTaskId());
        assertEquals(
                "Results returned in wrong order",
                "task-id-1",
                results.getResults().get(1).getTaskId());
        results = indexDAO.searchTaskSummary("", "*", 4, 2, orderBy);
        assertEquals("Wrong totalHits returned", 7, results.getTotalHits());
        assertEquals("Wrong number of results returned", 2, results.getResults().size());
        assertEquals(
                "Results returned in wrong order",
                "task-id-0",
                results.getResults().get(0).getTaskId());
    }

    @Test
    public void testGetTaskExecutionLogs() throws SQLException {
        List<TaskExecLog> logs = new ArrayList<>();
        String taskId = UUID.randomUUID().toString();
        logs.add(getMockTaskExecutionLog(taskId, new Date(1675845986000L).getTime(), "Log 1"));
        logs.add(getMockTaskExecutionLog(taskId, new Date(1675845987000L).getTime(), "Log 2"));

        indexDAO.addTaskExecutionLogs(logs);

        List<TaskExecLog> records = indexDAO.getTaskExecutionLogs(logs.get(0).getTaskId());
        assertEquals("Wrong number of logs returned", 2, records.size());
        assertEquals(logs.get(0).getLog(), records.get(0).getLog());
        assertEquals(logs.get(0).getCreatedTime(), 1675845986000L);
        assertEquals(logs.get(1).getLog(), records.get(1).getLog());
        assertEquals(logs.get(1).getCreatedTime(), 1675845987000L);
    }

    @Test
    public void testRemoveWorkflow() throws SQLException {
        String workflowId = UUID.randomUUID().toString();
        WorkflowSummary wfs = getMockWorkflowSummary(workflowId);
        indexDAO.indexWorkflow(wfs);

        List<Map<String, Object>> workflow_records =
                queryDb("SELECT * FROM workflow_index WHERE workflow_id = '" + workflowId + "'");
        assertEquals("Workflow index record was not created", 1, workflow_records.size());

        indexDAO.removeWorkflow(workflowId);

        workflow_records =
                queryDb("SELECT * FROM workflow_index WHERE workflow_id = '" + workflowId + "'");
        assertEquals("Workflow index record was not deleted", 0, workflow_records.size());
    }

    @Test
    public void testRemoveTask() throws SQLException {
        String workflowId = UUID.randomUUID().toString();

        String taskId = UUID.randomUUID().toString();
        TaskSummary ts = getMockTaskSummary(taskId);
        indexDAO.indexTask(ts);

        List<TaskExecLog> logs = new ArrayList<>();
        logs.add(getMockTaskExecutionLog(taskId, new Date(1675845986000L).getTime(), "Log 1"));
        logs.add(getMockTaskExecutionLog(taskId, new Date(1675845987000L).getTime(), "Log 2"));
        indexDAO.addTaskExecutionLogs(logs);

        List<Map<String, Object>> task_records =
                queryDb("SELECT * FROM task_index WHERE task_id = '" + taskId + "'");
        assertEquals("Task index record was not created", 1, task_records.size());

        List<Map<String, Object>> log_records =
                queryDb("SELECT * FROM task_execution_logs WHERE task_id = '" + taskId + "'");
        assertEquals("Task execution logs were not created", 2, log_records.size());

        indexDAO.removeTask(workflowId, taskId);

        task_records = queryDb("SELECT * FROM task_index WHERE task_id = '" + taskId + "'");
        assertEquals("Task index record was not deleted", 0, task_records.size());

        log_records = queryDb("SELECT * FROM task_execution_logs WHERE task_id = '" + taskId + "'");
        assertEquals("Task execution logs were not deleted", 0, log_records.size());
    }
}
