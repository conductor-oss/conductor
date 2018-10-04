package com.netflix.conductor.dao.mysql;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.ExecutionDAOTest;
import com.netflix.conductor.dao.IndexDAO;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@SuppressWarnings("Duplicates")
public class MySQLExecutionDAOTest extends ExecutionDAOTest {

    private MySQLDAOTestUtil testMySQL;
    private MySQLExecutionDAO executionDAO;

    @Rule public TestName name = new TestName();

    @Before
    public void setup() throws Exception {
        testMySQL = new MySQLDAOTestUtil(name.getMethodName());
        executionDAO = new MySQLExecutionDAO(
                mock(IndexDAO.class),
                testMySQL.getObjectMapper(),
                testMySQL.getDataSource()
        );
        testMySQL.resetAllData();
    }

    @After
    public void teardown() throws Exception {
        testMySQL.resetAllData();
        testMySQL.getDataSource().close();
    }

    @Test
    public void testPendingByCorrelationId() throws Exception {

        WorkflowDef def = new WorkflowDef();
        def.setName("pending_count_correlation_jtest");

        Workflow workflow = createTestWorkflow();
        workflow.setWorkflowDefinition(def);

        String idBase = workflow.getWorkflowId();
        generateWorkflows(workflow, idBase, 10);

        List<Workflow> bycorrelationId = getExecutionDAO().getWorkflowsByCorrelationId("corr001", true);
        assertNotNull(bycorrelationId);
        assertEquals(10, bycorrelationId.size());

	}

    @Override
    public ExecutionDAO getExecutionDAO() {
        return executionDAO;
    }

}
