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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.*;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;

import static io.conductor.e2e.util.RegistrationUtil.registerWorkflowDef;
import static io.conductor.e2e.util.RegistrationUtil.registerWorkflowWithSubWorkflowDef;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class WorkflowRestartTests {

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
    @DisplayName("Check workflow with simple task and restart functionality")
    public void testRestartSimpleWorkflow() {
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        registerWorkflowDef(workflowName, "simple", "inline", metadataClient);

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
        String taskId = workflow.getTasks().get(1).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Wait for workflow to get failed
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.FAILED.name());
                        });

        // Restart the workflow
        workflowClient.restart(workflowId, false);
        // Check the workflow status and few other parameters
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertTrue(workflow1.getTasks().get(0).isExecuted());
                            assertFalse(workflow1.getTasks().get(1).isExecuted());
                        });

        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(
                workflowClient.getWorkflow(workflowId, true).getTasks().get(1).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Wait for workflow to get completed
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                        });

        metadataClient.unregisterWorkflowDef(workflowName, 1);
        metadataClient.unregisterTaskDef("simple");
        metadataClient.unregisterTaskDef("inline");
    }

    @Test
    @DisplayName(
            "Check workflow with simple task and restart functionality by changing workflow definition")
    public void testRestartSimpleWorkflowChangeDefinition() {
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        // Register workflow
        registerWorkflowDef(workflowName, "simple", "inline", metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        // Fail the simple task
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertFalse(workflow1.getTasks().size() < 2);
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        String taskId = workflow.getTasks().get(1).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Wait for workflow to get failed
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                        });

        // Change the workflow definition.
        WorkflowDef workflowDef = metadataClient.getWorkflowDef(workflowName, 1);
        addTasksInWorkflowDef(workflowDef);

        // Restart the workflow
        workflowClient.restart(workflowId, true);
        // Check the workflow status and few other parameters
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                            assertEquals(
                                    workflow1.getTasks().get(1).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                            assertTrue(workflow1.getTasks().get(0).isExecuted());
                            assertFalse(workflow1.getTasks().get(1).isExecuted());
                        });
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(
                workflowClient.getWorkflow(workflowId, true).getTasks().get(1).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Workflow will still be running since new simple task has been added.
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                        });

        // Complete the newly added simple task.
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(
                workflowClient.getWorkflow(workflowId, true).getTasks().get(2).getTaskId());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                        });

        metadataClient.unregisterWorkflowDef(workflowName, 1);
        metadataClient.unregisterTaskDef("simple");
        metadataClient.unregisterTaskDef("inline");
    }

    private void addTasksInWorkflowDef(WorkflowDef workflowDef) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("added");
        workflowTask.setName("added");
        workflowTask.setType(TaskType.SIMPLE.name());

        TaskDef task = new TaskDef();
        task.setName("added");
        task.setOwnerEmail("test@orkes.io");
        metadataClient.registerTaskDefs(Arrays.asList(task));
        workflowDef.getTasks().add(workflowTask);
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }

    @Test
    @DisplayName("Check workflow with sub_workflow task and restart functionality")
    public void testRestartWithSubWorkflow() {

        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        String workflowName = "restart-parent-with-sub-workflow";
        String subWorkflowName = "restart-sub-workflow";
        String taskName = "simple-no-restart";

        // Register workflow
        registerWorkflowWithSubWorkflowDef(workflowName, subWorkflowName, taskName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.print("Workflow id is " + workflowId);
        // Wait for sub-workflow to be started and get its ID
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertNotEquals(0, workflow1.getTasks().size());
                            assertNotNull(workflow1.getTasks().get(0).getSubWorkflowId());
                            assertFalse(workflow1.getTasks().get(0).getSubWorkflowId().isBlank());
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        String subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        String taskId = subWorkflow.getTasks().get(0).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Wait for parent workflow to get failed
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.FAILED.name());
                        });

        // Restart the sub workflow.
        workflowClient.restart(subworkflowId, false);
        // Check the workflow status and few other parameters
        String finalSubworkflowId = subworkflowId;
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 =
                                    workflowClient.getWorkflow(finalSubworkflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING.name(),
                                    workflow1.getStatus().name());
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.SCHEDULED.name());
                        });
        taskId = workflowClient.getWorkflow(subworkflowId, true).getTasks().get(0).getTaskId();

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
                                    "workflow " + workflow1.getWorkflowId() + " did not complete");
                        });

        // Check restart functionality at parent workflow level.
        workflowClient.restart(workflowId, false);
        workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        taskId = subWorkflow.getTasks().get(0).getTaskId();
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED.name(),
                                    workflow1.getStatus().name(),
                                    "workflow " + workflow1.getWorkflowId() + " did not complete");
                        });
    }

    @Test
    @DisplayName("Check workflow with sub_workflow task and restart parent workflow functionality")
    public void testRestartWithSubWorkflowWithRestartParent() {

        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        String workflowName = "restart-parent-with-orphan-sub-workflow";
        String subWorkflowName = "restart-sub-workflow";
        String taskName = "simple-no-restart2";

        // Register workflow
        registerWorkflowWithSubWorkflowDef(workflowName, subWorkflowName, taskName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.print("Workflow id is " + workflowId);
        // Wait for sub-workflow to be started and get its ID
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertNotEquals(0, workflow1.getTasks().size());
                            assertNotNull(workflow1.getTasks().get(0).getSubWorkflowId());
                            assertFalse(workflow1.getTasks().get(0).getSubWorkflowId().isBlank());
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        String subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        String taskId = subWorkflow.getTasks().get(0).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        // Wait for parent workflow to complete
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.COMPLETED.name());
                        });

        // Restart the sub workflow.
        workflowClient.restart(workflowId, false);
        // Check the workflow status and few other parameters
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING.name(),
                                    workflow1.getStatus().name());
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        // Complete the new sub workflow - wait for new sub-workflow ID
        final String wfIdForRestart2 = workflowId;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfIdForRestart2, true);
                            assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            assertFalse(wf.getTasks().get(0).getSubWorkflowId().isBlank());
                        });
        String newSubWorkflowId =
                workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getSubWorkflowId();
        taskId = workflowClient.getWorkflow(newSubWorkflowId, true).getTasks().get(0).getTaskId();
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(newSubWorkflowId);
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
                                    "workflow " + workflow1.getWorkflowId() + " did not complete");
                        });

        // Now restart the original sub_workflow.
        workflowClient.restart(subworkflowId, false);
        // Parent should not get affected since the parent does not contain any information about
        // this sub workflow.
        Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
        assertEquals(workflow1.getStatus().name(), Workflow.WorkflowStatus.COMPLETED.name());

        // Terminate the sub workflow.
        workflowClient.terminateWorkflow(subworkflowId, "Terminated");
    }

    @Test
    @DisplayName(
            "Check workflow with orphan sub_workflow task and retry sub-workflow functionality")
    public void testRetryWithOrphanSubWorkflowWithRestartParent() {

        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        String workflowName = "retry-parent-with-orphan-sub-workflow";
        String subWorkflowName = "retry-sub-workflow2";
        String taskName = "simple-no-retry3";

        // Register workflow
        registerWorkflowWithSubWorkflowDef(workflowName, subWorkflowName, taskName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.print("Workflow id is " + workflowId);
        // Wait for sub-workflow to be started and get its ID
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertNotEquals(0, workflow1.getTasks().size());
                            assertNotNull(workflow1.getTasks().get(0).getSubWorkflowId());
                            assertFalse(workflow1.getTasks().get(0).getSubWorkflowId().isBlank());
                        });
        // Fail the simple task
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        String subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        String taskId = subWorkflow.getTasks().get(0).getTaskId();
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        taskClient.updateTask(taskResult);

        // Wait for parent workflow to get failed
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.FAILED.name());
                        });

        // Restart the sub workflow.
        workflowClient.restart(workflowId, false);
        // Check the workflow status and few other parameters
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING.name(),
                                    workflow1.getStatus().name());
                            assertEquals(
                                    workflow1.getTasks().get(0).getStatus().name(),
                                    Task.Status.IN_PROGRESS.name());
                        });

        // Complete the new sub workflow - wait for new sub-workflow ID
        final String wfIdForRetry = workflowId;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfIdForRetry, true);
                            assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            assertFalse(wf.getTasks().get(0).getSubWorkflowId().isBlank());
                        });
        String newSubWorkflowId =
                workflowClient.getWorkflow(workflowId, true).getTasks().get(0).getSubWorkflowId();
        taskId = workflowClient.getWorkflow(newSubWorkflowId, true).getTasks().get(0).getTaskId();
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(newSubWorkflowId);
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
                                    "workflow " + workflow1.getWorkflowId() + " did not complete");
                        });

        // Now retry the original sub_workflow.
        workflowClient.retryWorkflow(List.of(subworkflowId));
        // Parent should not get affected since the parent does not contain any information about
        // this sub workflow.
        Workflow workflow1 = workflowClient.getWorkflow(workflowId, false);
        assertEquals(workflow1.getStatus().name(), Workflow.WorkflowStatus.COMPLETED.name());

        // Terminate the sub workflow.
        workflowClient.terminateWorkflow(subworkflowId, "Terminated");
    }

    @Test
    @DisplayName("Restarting optional subworkflow does not update parent workflow status")
    public void testRestartOptionalSubWorkflowDoesNotAffectParent() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;

        String parentWorkflowName = "parent-wf-2level-restart";
        String childWorkflowName = "sub-wf-2level-restart";
        String taskName = "simple-task-2level-restart";

        // Register child workflow
        registerWorkflowDef(childWorkflowName, taskName, taskName, metadataClient);
        // Register parent workflow with subworkflow
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

        // Fail the simple task in child workflow
        Workflow childWorkflow = workflowClient.getWorkflow(childWorkflowId, true);
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

        // Restart the child workflow
        workflowClient.restart(childWorkflowId, false);

        // Assert parent is still COMPLETED after subworkflow restart
        Workflow parentAfterRestart = workflowClient.getWorkflow(parentWorkflowId, false);
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED.name(), parentAfterRestart.getStatus().name());
    }
}
