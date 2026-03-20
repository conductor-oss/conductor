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
package io.conductor.e2e.workflow;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import io.conductor.e2e.util.ApiUtil;
import lombok.extern.slf4j.Slf4j;

import static io.conductor.e2e.util.RegistrationUtil.registerWorkflowDef;
import static io.conductor.e2e.util.RegistrationUtil.registerWorkflowWithSubWorkflowDef;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class WorkflowRetryTests {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
    }

    @Test
    @DisplayName("Check workflow with simple task and retry functionality")
    public void testRetrySimpleWorkflow() {
        String workflowName = "retry-simple-workflow";
        String taskDefName = "retry-simple-task1";

        terminateExistingRunningWorkflows(workflowName);

        // Register workflow
        registerWorkflowDef(workflowName, taskDefName, taskDefName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(4, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertFalse(workflow1.getTasks().size() < 2);
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertNotEquals(0, workflow1.getTasks().size());
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);
        String taskId = workflow.getTasks().get(1).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskResult.setReasonForIncompletion("failed");
        taskClient.updateTask(taskResult);

        // Wait for workflow to get failed
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.FAILED.name());
                        });

        // Retry the workflow
        workflowClient.retryLastFailedTask(workflowId);
        // Check the workflow status and few other parameters
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                            assertTrue(workflow1.getLastRetriedTime() != 0L);
                            assertEquals(
                                    workflow1.getTasks().get(2).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                        });

        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(
                workflowClient.getWorkflow(workflowId, true).getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                        });
    }

    @Test
    @DisplayName("Check workflow with sub_workflow task and retry functionality")
    public void testRetryWithSubWorkflow() {

        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        String workflowName = "retry-parent-with-sub-workflow";
        String subWorkflowName = "retry-sub-workflow";
        String taskName = "simple-no-retry2";

        terminateExistingRunningWorkflows(workflowName);

        // Register workflow
        registerWorkflowWithSubWorkflowDef(workflowName, subWorkflowName, taskName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.print("Workflow id is " + workflowId);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(1, workflow.getTasks().size());
                            assertNotNull(workflow.getTasks().get(0).getSubWorkflowId());
                        });
        // Fail the simple task
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        String subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        String taskId = subWorkflow.getTasks().get(0).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Wait for parent workflow to get failed
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.FAILED.name());
                        });

        // Retry the sub workflow.
        workflowClient.retryLastFailedTask(subworkflowId);
        // Check the workflow status and few other parameters
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(subworkflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING.name(),
                                    workflow1.getStatus().name());
                            assertTrue(workflow1.getLastRetriedTime() != 0L);
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.FAILED.name());
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                        });
        taskId = workflowClient.getWorkflow(subworkflowId, true).getTasks().get(1).getTaskId();

        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        await().atMost(33, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED.name(),
                                    workflow1.getStatus().name(),
                                    "workflow " + workflowId + " did not complete");
                        });

        // Check retry at parent workflow level.
        String newWorkflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.print("Workflow id is " + newWorkflowId);
        Workflow newWorkflow = workflowClient.getWorkflow(newWorkflowId, true);
        // Fail the simple task
        String newSubworkflowId = newWorkflow.getTasks().get(0).getSubWorkflowId();
        Workflow newSubWorkflow = workflowClient.getWorkflow(newSubworkflowId, true);
        taskId = newSubWorkflow.getTasks().get(0).getTaskId();
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(newSubworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Wait for parent workflow to get failed
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(newWorkflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.FAILED.name());
                        });

        // Retry parent workflow.
        workflowClient.retryLastFailedTask(newWorkflowId);

        // Wait for parent workflow to get failed
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(newWorkflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                        });

        newWorkflow = workflowClient.getWorkflow(newWorkflowId, true);
        newSubworkflowId = newWorkflow.getTasks().get(0).getSubWorkflowId();
        newSubWorkflow = workflowClient.getWorkflow(newSubworkflowId, true);
        taskId = newSubWorkflow.getTasks().get(1).getTaskId();
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(newSubworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(newWorkflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                        });
    }

    @Test
    @DisplayName("Check workflow with sub_workflow task and retry functionality with optional task")
    public void testRetryWithSubWorkflowOptionalLevels() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;

        String parentWorkflowName = "parent-wf-2level";
        String childWorkflowName = "sub-wf-2level";
        String taskName = "simple-task-2level";

        terminateExistingRunningWorkflows(parentWorkflowName);

        // Register child workflow (level 2)
        registerWorkflowDef(childWorkflowName, taskName, taskName, metadataClient);
        // Register parent workflow (level 1) with sub_workflow = child
        registerWorkflowWithSubWorkflowDef(
                parentWorkflowName, childWorkflowName, taskName, metadataClient);
        // Set the sub_workflow task in parent as optional
        WorkflowDef parentDef = metadataClient.getWorkflowDef(parentWorkflowName, 1);
        parentDef.getTasks().get(0).setOptional(true);
        metadataClient.updateWorkflowDefs(List.of(parentDef));

        // Start parent workflow
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(parentWorkflowName);
        startWorkflowRequest.setVersion(1);

        String parentWorkflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Wait for the child sub-workflow to be scheduled and get its ID
        String childWorkflowId =
                await().atMost(10, TimeUnit.SECONDS)
                        .pollInterval(1, TimeUnit.SECONDS)
                        .until(
                                () -> {
                                    Workflow parentWorkflow =
                                            workflowClient.getWorkflow(parentWorkflowId, true);
                                    if (!parentWorkflow.getTasks().isEmpty()) {
                                        String subId =
                                                parentWorkflow.getTasks().get(0).getSubWorkflowId();
                                        if (subId != null && !subId.isEmpty()) {
                                            return subId;
                                        }
                                    }
                                    return null;
                                },
                                id -> id != null);

        // Wait for the child workflow to be available
        Workflow childWorkflow =
                await().atMost(10, TimeUnit.SECONDS)
                        .pollInterval(1, TimeUnit.SECONDS)
                        .until(
                                () -> {
                                    try {
                                        return workflowClient.getWorkflow(childWorkflowId, true);
                                    } catch (Exception e) {
                                        return null;
                                    }
                                },
                                w -> w != null);

        // Fail the simple task in child workflow
        String childTaskId = childWorkflow.getTasks().get(0).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(childWorkflowId);
        taskResult.setTaskId(childTaskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Wait for parent to complete (since child is optional)
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentWorkflowId, false);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED.name(),
                                    parent.getStatus().name());
                        });

        // Retry the child workflow
        workflowClient.retryLastFailedTask(childWorkflowId);

        // Check child is running, parent remains completed
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow child = workflowClient.getWorkflow(childWorkflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING.name(),
                                    child.getStatus().name());
                            Workflow parent = workflowClient.getWorkflow(parentWorkflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED.name(),
                                    parent.getStatus().name());
                        });
    }

    private void terminateExistingRunningWorkflows(String workflowName) {
        // clean up first
        try {
            SearchResult<WorkflowSummary> found =
                    workflowClient.search(
                            "workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
            System.out.println(
                    "Found " + found.getResults().size() + " running workflows to be cleaned up");
            found.getResults()
                    .forEach(
                            workflowSummary -> {
                                System.out.println(
                                        "Going to terminate "
                                                + workflowSummary.getWorkflowId()
                                                + " with status "
                                                + workflowSummary.getStatus());
                                workflowClient.terminateWorkflow(
                                        workflowSummary.getWorkflowId(), "terminate - retry test");
                            });
        } catch (Exception e) {
            if (!(e instanceof ConductorClientException)) {
                throw e;
            }
        }
    }
}
