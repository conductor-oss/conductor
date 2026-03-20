/*
 * Copyright 2022 Conductor Authors.
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
package io.conductor.e2e.task;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class TaskTimeoutTests {

    private static MetadataClient metadataClient;
    private static WorkflowClient workflowClient;
    private static TaskClient taskClient;

    private static final String TASK_NAME = "task_timeout_test";
    private static final String WORKFLOW_NAME = "task_timeout_workflow";

    private static final String TASK_NAME_RESPONSE_TIMEOUT = "task_response_timeout_test";
    private static final String WORKFLOW_NAME_RESPONSE_TIMEOUT = "workflow_response_timeout_test";

    private static final String TASK_NAME_OVERALL_TIMEOUT = "task_overall_timeout_test";
    private static final String WORKFLOW_NAME_OVERALL_TIMEOUT = "workflow_overall_timeout_test";

    @BeforeAll
    static void setup() {
        metadataClient = ApiUtil.METADATA_CLIENT;
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;

        // Define TaskDef with pollTimeout and timeoutSeconds
        TaskDef taskDef = new TaskDef();
        taskDef.setName(TASK_NAME);
        taskDef.setPollTimeoutSeconds(25); // Task will be timed out if not polled in 5s
        taskDef.setTimeoutSeconds(100); // Task execution timeout
        taskDef.setResponseTimeoutSeconds(40);
        taskDef.setRetryCount(0); // No retries
        taskDef.setTimeoutPolicy(TaskDef.TimeoutPolicy.TIME_OUT_WF);

        // Register TaskDef
        metadataClient.registerTaskDefs(Collections.singletonList(taskDef));

        // Define Workflow
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(WORKFLOW_NAME);
        workflowDef.setDescription("Workflow to test pollTimeoutSeconds and timeoutSeconds");
        workflowDef.setVersion(1);

        // Create Task
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(TASK_NAME);
        workflowTask.setTaskReferenceName("task_timeout_ref");

        workflowDef.setTasks(Collections.singletonList(workflowTask));

        // Register Workflow
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));

        TaskDef taskDefResponseTimeout = new TaskDef();
        taskDefResponseTimeout.setName(TASK_NAME_RESPONSE_TIMEOUT);
        taskDefResponseTimeout.setPollTimeoutSeconds(6); // Ensure poll doesn't interfere
        taskDefResponseTimeout.setTimeoutSeconds(120); // Total timeout (should not trigger)
        taskDefResponseTimeout.setResponseTimeoutSeconds(
                5); // Task should timeout if not updated in 30s
        taskDefResponseTimeout.setRetryCount(0);
        taskDefResponseTimeout.setTimeoutPolicy(TaskDef.TimeoutPolicy.TIME_OUT_WF);

        // Register TaskDef
        metadataClient.registerTaskDefs(Collections.singletonList(taskDefResponseTimeout));

        // Define Workflow
        WorkflowDef workflowDefResponseTimeout = new WorkflowDef();
        workflowDefResponseTimeout.setName(WORKFLOW_NAME_RESPONSE_TIMEOUT);
        workflowDefResponseTimeout.setDescription("Workflow to test responseTimeoutSeconds");
        workflowDefResponseTimeout.setVersion(1);

        // Create Task
        WorkflowTask workflowTaskResponseTimeout = new WorkflowTask();
        workflowTaskResponseTimeout.setName(TASK_NAME_RESPONSE_TIMEOUT);
        workflowTaskResponseTimeout.setTaskReferenceName("task_response_timeout_ref");

        workflowDefResponseTimeout.setTasks(Collections.singletonList(workflowTaskResponseTimeout));

        // Register Workflow
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDefResponseTimeout));

        // Define TaskDef with overall timeoutSeconds
        TaskDef taskDefOverallTimeout = new TaskDef();
        taskDefOverallTimeout.setName(TASK_NAME_OVERALL_TIMEOUT);
        taskDefOverallTimeout.setPollTimeoutSeconds(10); // Ensure poll timeout is not interfering
        taskDefOverallTimeout.setTimeoutSeconds(25); // Task must be completed within 45 seconds
        taskDefOverallTimeout.setRetryCount(0);
        taskDefOverallTimeout.setResponseTimeoutSeconds(5);
        taskDefOverallTimeout.setTimeoutPolicy(TaskDef.TimeoutPolicy.TIME_OUT_WF);

        // Register TaskDef
        metadataClient.registerTaskDefs(Collections.singletonList(taskDefOverallTimeout));

        // Define Workflow
        WorkflowDef workflowDefOverallTimeout = new WorkflowDef();
        workflowDefOverallTimeout.setName(WORKFLOW_NAME_OVERALL_TIMEOUT);
        workflowDefOverallTimeout.setDescription("Workflow to test overall task timeout");
        workflowDefOverallTimeout.setVersion(1);

        // Create Task
        WorkflowTask workflowTaskOverallTimeout = new WorkflowTask();
        workflowTaskOverallTimeout.setName(TASK_NAME_OVERALL_TIMEOUT);
        workflowTaskOverallTimeout.setTaskReferenceName("task_overall_timeout_ref");

        workflowDefOverallTimeout.setTasks(Collections.singletonList(workflowTaskOverallTimeout));

        // Register Workflow
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDefOverallTimeout));
    }

    @Test
    void testTaskPollTimeout() throws InterruptedException {
        // Start Workflow
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(WORKFLOW_NAME);

        String workflowId = workflowClient.startWorkflow(request);
        assertNotNull(workflowId);

        await().pollInterval(30, TimeUnit.SECONDS)
                .atMost(45, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
                        });
    }

    @Test
    void testTaskResponseTimeout() throws InterruptedException {
        // Start Workflow
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(WORKFLOW_NAME_RESPONSE_TIMEOUT);

        String workflowId = workflowClient.startWorkflow(request);
        assertNotNull(workflowId);

        // Poll for the task but do not update it
        Task polledTask = taskClient.pollTask(TASK_NAME_RESPONSE_TIMEOUT, "test-worker", null);
        assertNotNull(polledTask);
        assertEquals(workflowId, polledTask.getWorkflowInstanceId());

        // Wait for response timeout (should timeout in 30s)
        await().pollInterval(30, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
                        });
    }

    @Test
    void testTaskOverallTimeout() throws InterruptedException {
        // Start Workflow
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(WORKFLOW_NAME_OVERALL_TIMEOUT);

        String workflowId = workflowClient.startWorkflow(request);
        assertNotNull(workflowId);

        // Poll for the task but do not complete it
        Task polledTask = taskClient.pollTask(TASK_NAME_OVERALL_TIMEOUT, "test-worker", null);
        assertNotNull(polledTask);
        assertEquals(workflowId, polledTask.getWorkflowInstanceId());

        // Wait for overall timeout (should timeout in 45s)
        await().pollInterval(4, TimeUnit.SECONDS)
                .atMost(40, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(Workflow.WorkflowStatus.TIMED_OUT, workflow.getStatus());
                        });
    }

    @AfterAll
    static void cleanup() {
        // Cleanup task and workflow
        metadataClient.unregisterTaskDef(TASK_NAME);
        metadataClient.unregisterWorkflowDef(WORKFLOW_NAME, 1);
    }
}
