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
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.postgres.config.PostgresConfiguration;
import com.netflix.conductor.postgres.util.Query;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;

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
            "conductor.postgres.onlyIndexOnStatusChange=true",
            "spring.flyway.clean-disabled=false"
        })
@SpringBootTest
public class PostgresIndexDAOStatusChangeOnlyTest {

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
        wfs.setStatus(Workflow.WorkflowStatus.RUNNING);
        return wfs;
    }

    private TaskSummary getMockTaskSummary(String taskId) {
        TaskSummary ts = new TaskSummary();
        ts.setTaskId(taskId);
        ts.setTaskType("task-type");
        ts.setTaskDefName("task-def-name");
        ts.setStatus(Task.Status.SCHEDULED);
        ts.setStartTime("2023-02-07T09:41:45Z");
        ts.setUpdateTime("2023-02-07T09:42:45Z");
        ts.setWorkflowType("workflow-type");
        return ts;
    }

    private List<Map<String, Object>> queryDb(String query) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            try (Query q = new Query(objectMapper, c, query)) {
                return q.executeAndFetchMap();
            }
        }
    }

    public void checkWorkflow(String workflowId, String status, String correlationId)
            throws SQLException {
        List<Map<String, Object>> result =
                queryDb(
                        String.format(
                                "SELECT * FROM workflow_index WHERE workflow_id = '%s'",
                                workflowId));
        assertEquals("Wrong number of rows returned", 1, result.size());
        assertEquals("Wrong status returned", status, result.get(0).get("status"));
        assertEquals(
                "Correlation id does not match",
                correlationId,
                result.get(0).get("correlation_id"));
    }

    public void checkTask(String taskId, String status, String updateTime) throws SQLException {
        List<Map<String, Object>> result =
                queryDb(String.format("SELECT * FROM task_index WHERE task_id = '%s'", taskId));
        assertEquals("Wrong number of rows returned", 1, result.size());
        assertEquals("Wrong status returned", status, result.get(0).get("status"));
        assertEquals(
                "Update time does not match",
                updateTime,
                result.get(0).get("update_time").toString());
    }

    @Test
    public void testIndexWorkflowOnlyStatusChange() throws SQLException {
        WorkflowSummary wfs = getMockWorkflowSummary("workflow-id");
        indexDAO.indexWorkflow(wfs);

        // retrieve the record, make sure it exists
        checkWorkflow("workflow-id", "RUNNING", "correlation-id");

        // Change the record, but not the status, and re-index
        wfs.setCorrelationId("new-correlation-id");
        indexDAO.indexWorkflow(wfs);

        // retrieve the record, make sure it hasn't changed
        checkWorkflow("workflow-id", "RUNNING", "correlation-id");

        // Change the status and re-index
        wfs.setStatus(Workflow.WorkflowStatus.FAILED);
        indexDAO.indexWorkflow(wfs);

        // retrieve the record, make sure it has changed
        checkWorkflow("workflow-id", "FAILED", "new-correlation-id");
    }

    @Test
    public void testIndexTaskOnlyStatusChange() throws SQLException {
        TaskSummary ts = getMockTaskSummary("task-id");

        indexDAO.indexTask(ts);

        // retrieve the record, make sure it exists
        checkTask("task-id", "SCHEDULED", "2023-02-07 09:42:45.0");

        // Change the record, but not the status
        ts.setUpdateTime("2023-02-07T10:42:45Z");
        indexDAO.indexTask(ts);

        // retrieve the record, make sure it hasn't changed
        checkTask("task-id", "SCHEDULED", "2023-02-07 09:42:45.0");

        // Change the status and re-index
        ts.setStatus(Task.Status.FAILED);
        indexDAO.indexTask(ts);

        // retrieve the record, make sure it has changed
        checkTask("task-id", "FAILED", "2023-02-07 10:42:45.0");
    }
}
