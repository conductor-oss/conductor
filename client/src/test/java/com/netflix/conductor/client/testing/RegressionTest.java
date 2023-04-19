/*
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.conductor.client.testing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowTestRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This test demonstrates how to use execution data from the previous executed workflows as golden
 * input and output and use them to regression test the workflow definition.
 *
 * <p>Regression tests are useful ensuring any changes to the workflow definition does not change
 * the behavior.
 */
public class RegressionTest extends AbstractWorkflowTests {

    // @Test
    // Tests are commented out since it requires a running server
    // Uses a previously executed successful run to verify the workflow execution, and it's output.
    public void verifyWorkflowOutput()
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        // Workflow Definition
        WorkflowDef def = getWorkflowDef("/workflows/workflow1.json");

        // Golden output to verify against
        Workflow workflow = getWorkflow("/test_data/workflow1_run.json");

        WorkflowTestRequest testRequest = new WorkflowTestRequest();
        testRequest.setInput(new HashMap<>());
        testRequest.setName(def.getName());
        testRequest.setVersion(def.getVersion());
        testRequest.setWorkflowDef(def);

        Map<String, List<WorkflowTestRequest.TaskMock>> taskRefToMockOutput = new HashMap<>();
        for (Task task : workflow.getTasks()) {
            List<WorkflowTestRequest.TaskMock> taskRuns = new ArrayList<>();
            WorkflowTestRequest.TaskMock mock = new WorkflowTestRequest.TaskMock();
            mock.setStatus(TaskResult.Status.valueOf(task.getStatus().name()));
            mock.setOutput(task.getOutputData());
            taskRuns.add(mock);
            taskRefToMockOutput.put(def.getTasks().get(0).getTaskReferenceName(), taskRuns);
        }

        testRequest.setTaskRefToMockOutput(taskRefToMockOutput);
        Workflow execution = workflowClient.testWorkflow(testRequest);
        assertNotNull(execution);
        assertEquals(workflow.getTasks().size(), execution.getTasks().size());
    }
}
