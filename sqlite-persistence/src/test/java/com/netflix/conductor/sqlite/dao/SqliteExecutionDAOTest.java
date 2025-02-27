/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.ExecutionDAOTest;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sqlite.config.SqliteConfiguration;

import com.google.common.collect.Iterables;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            SqliteConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public class SqliteExecutionDAOTest extends ExecutionDAOTest {

    @Autowired private SqliteExecutionDAO executionDAO;

    @Autowired Flyway flyway;

    // clean the database between tests.
    @Before
    public void before() {
        flyway.migrate();
    }

    @Test
    public void testPendingByCorrelationId() {

        WorkflowDef def = new WorkflowDef();
        def.setName("pending_count_correlation_jtest");

        WorkflowModel workflow = createTestWorkflow();
        workflow.setWorkflowDefinition(def);

        generateWorkflows(workflow, 10);

        List<WorkflowModel> bycorrelationId =
                getExecutionDAO()
                        .getWorkflowsByCorrelationId(
                                "pending_count_correlation_jtest", "corr001", true);
        assertNotNull(bycorrelationId);
        assertEquals(10, bycorrelationId.size());
    }

    @Test
    public void testRemoveWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("workflow");

        WorkflowModel workflow = createTestWorkflow();
        workflow.setWorkflowDefinition(def);

        List<String> ids = generateWorkflows(workflow, 1);

        assertEquals(1, getExecutionDAO().getPendingWorkflowCount("workflow"));
        ids.forEach(wfId -> getExecutionDAO().removeWorkflow(wfId));
        assertEquals(0, getExecutionDAO().getPendingWorkflowCount("workflow"));
    }

    @Test
    public void testRemoveWorkflowWithExpiry() {
        WorkflowDef def = new WorkflowDef();
        def.setName("workflow");

        WorkflowModel workflow = createTestWorkflow();
        workflow.setWorkflowDefinition(def);

        List<String> ids = generateWorkflows(workflow, 1);

        final ExecutionDAO execDao = Mockito.spy(getExecutionDAO());
        assertEquals(1, execDao.getPendingWorkflowCount("workflow"));
        ids.forEach(wfId -> execDao.removeWorkflowWithExpiry(wfId, 1));
        Mockito.verify(execDao, Mockito.timeout(10 * 1000)).removeWorkflow(Iterables.getLast(ids));
    }

    @Override
    public ExecutionDAO getExecutionDAO() {
        return executionDAO;
    }
}
