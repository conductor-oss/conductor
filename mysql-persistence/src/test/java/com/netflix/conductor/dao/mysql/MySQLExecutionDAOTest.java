package com.netflix.conductor.dao.mysql;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.ExecutionDAOTest;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@SuppressWarnings("Duplicates")
public class MySQLExecutionDAOTest extends ExecutionDAOTest {

    private final MySQLDAOTestUtil testMySQL = new MySQLDAOTestUtil();
    private MySQLMetadataDAO metadata;
    private MySQLExecutionDAO dao;

    @Before
    public void setup() throws Exception {
        metadata = new MySQLMetadataDAO(
                testMySQL.getObjectMapper(),
                testMySQL.getDataSource(),
                testMySQL.getTestConfiguration()
        );
        dao = new MySQLExecutionDAO(
                mock(IndexDAO.class),
                metadata,
                testMySQL.getObjectMapper(),
                testMySQL.getDataSource()
        );
        testMySQL.resetAllData();
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
        return dao;
    }

    @Override
    public MetadataDAO getMetadataDAO() {
        return metadata;
    }
}
