
package com.netflix.conductor.tests.integration;

import com.netflix.conductor.tests.utils.MySQLTestRunner;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(MySQLTestRunner.class)

public class MySQLWorkflowServiceTest extends AbstractWorkflowServiceTest {

    @Override
    String startOrLoadWorkflowExecution(String snapshotResourceName, String workflowName, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain) {
        return workflowExecutor.startWorkflow(workflowName, version, correlationId, input, null, event, taskToDomain);
    }
}
