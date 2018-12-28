
package com.netflix.conductor.tests.integration;

import java.util.Map;

public class MySQLWorkflowServiceTest  {

    
    String startOrLoadWorkflowExecution(String snapshotResourceName, String workflowName, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain) {
        // return workflowExecutor.startWorkflow(workflowName, version, correlationId, input, null, event, taskToDomain);
        return null;
    }
}
