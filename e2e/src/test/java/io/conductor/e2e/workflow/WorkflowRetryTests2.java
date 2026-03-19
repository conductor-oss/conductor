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
package io.conductor.e2e.workflow;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.Commons;

import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.awaitility.Awaitility.await;

@Slf4j
public class WorkflowRetryTests2 {
    private final MetadataClient metadataClient;
    private final WorkflowClient workflowClient;

    private final TaskClient taskClient;

    static final String taskName = "test_retry_operation";

    static final String workflowName = "Sample_Retry";

    public WorkflowRetryTests2() {
        metadataClient = ApiUtil.METADATA_CLIENT;
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
    }

    @Test
    @DisplayName("create workflow definition")
    public void createWorkflowDef() {
        registerTask();
        registerWorkflow();
    }

    @Test
    @Disabled
    @DisplayName("test retry operation")
    public void testRetry() {
        registerTask();
        registerWorkflow();
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setInput(new HashMap<>());
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
            assertNotEquals(0, workflow1.getTasks().size());
        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);

        String taskId = workflow.getTasks().get(0).getTaskId();
        // Fail the task
        failTask(workflow.getWorkflowId(), taskId);
        workflow = workflowClient.getWorkflow(workflowId, true);



        workflowClient.terminateWorkflow(workflowId, "testing out some stuff");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(()-> {
            Workflow wf = workflowClient.getWorkflow(workflowId, false);
            assertNotNull(wf);
            assertEquals(Workflow.WorkflowStatus.TERMINATED, wf.getStatus());
        });
        // Retry the workflow

        log.debug("Going to retry " + workflowId);
        workflowClient.retryLastFailedTask(workflowId);
        taskClient.updateTaskSync(workflow.getWorkflowId(), taskName, TaskResult.Status.COMPLETED, Map.of());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflowClient.getWorkflow(workflowId, false).getStatus());
    }

    private void completeTask(String workflowId, String taskId) {
        TaskResult taskResult = new TaskResult();
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId(taskId);
        taskResult.setWorkflowInstanceId(workflowId);
        taskClient.updateTask(taskResult);
    }

    private void failTask(String workflowId, String taskId) {
        TaskResult taskResult = new TaskResult();
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskResult.setReasonForIncompletion("No reason");
        taskResult.setTaskId(taskId);
        taskResult.setWorkflowInstanceId(workflowId);
        taskClient.updateTask(taskResult);
    }

    void registerTask() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(taskName);
        taskDef.setOwnerEmail("test@orkes.com");
        List<TaskDef> taskDefs = new ArrayList<>();
        taskDefs.add(taskDef);
        this.metadataClient.registerTaskDefs(taskDefs);
    }

    void registerWorkflow() {
        WorkflowDef workflowDef = getWorkflowDef();
        metadataClient.registerWorkflowDef(workflowDef);
    }

    static WorkflowDef getWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail(Commons.OWNER_EMAIL);
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(taskName);
        workflowTask.setTaskReferenceName(taskName);
        workflowDef.setTasks(List.of(workflowTask));
        return workflowDef;
    }

}
