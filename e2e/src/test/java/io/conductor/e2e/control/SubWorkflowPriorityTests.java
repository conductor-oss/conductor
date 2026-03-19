/*
 * Copyright 2022 Orkes, Inc.
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
package io.conductor.e2e.control;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.RegistrationUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.awaitility.Awaitility.await;

public class SubWorkflowPriorityTests {

    @Test
    public void testSubworkflowDynamicPriority() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;

        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String parentWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String subWorkflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        RegistrationUtil.registerWorkflowWithSubWorkflowDef(parentWorkflowName, subWorkflowName, taskName, metadataClient);
        WorkflowDef workflowDef =  metadataClient.getWorkflowDef(parentWorkflowName, 1);
        //Set sub workflow version to 0
        workflowDef.getTasks().get(0).getSubWorkflowParam().setPriority("${workflow.input.priority}");
        workflowDef.getTasks().get(0).getSubWorkflowParam().setIdempotencyKey("${workflow.input.idempotencyKey}");
        metadataClient.registerWorkflowDef(workflowDef);

        // Trigger workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWorkflowName);
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setPriority(29);
        startWorkflowRequest.setInput(Map.of("priority", 29, "idempotencyKey", "my_key"));

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // User1 should be able to complete task/workflow
        String subWorkflowId = workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getSubWorkflowId();
        TaskResult taskResult  = new TaskResult();
        taskResult.setWorkflowInstanceId(subWorkflowId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        Workflow subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
        assertEquals(29, subWorkflow.getPriority());
        taskResult.setTaskId(subWorkflow.getTasks().get(0).getTaskId());
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        await().atMost(42, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
            assertEquals(workflow1.getStatus().name(), Workflow.WorkflowStatus.COMPLETED.name());
            Workflow subworkflow = workflowClient.getWorkflow(subWorkflowId, false);
            assertEquals(29, subworkflow.getPriority());
            assertEquals("my_key", subworkflow.getIdempotencyKey());
        });

        // Cleanup
        metadataClient.unregisterWorkflowDef(parentWorkflowName, 1);
        metadataClient.unregisterWorkflowDef(subWorkflowName, 1);
        metadataClient.unregisterTaskDef(taskName);
    }

}
