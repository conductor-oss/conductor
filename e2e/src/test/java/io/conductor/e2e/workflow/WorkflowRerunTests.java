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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.*;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;

import static io.conductor.e2e.util.RegistrationUtil.registerWorkflowDef;
import static io.conductor.e2e.util.RegistrationUtil.registerWorkflowWithSubWorkflowDef;
import static io.conductor.e2e.util.TestUtil.getResourceAsString;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class WorkflowRerunTests {

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
    @DisplayName("Check workflow with simple task and rerun functionality")
    public void testRerunSimpleWorkflow() {

        String workflowName = "re-run-workflow";
        String taskName1 = "re-run-task1";
        String taskName2 = "re-run-task2";
        // Register workflow
        registerWorkflowDef(workflowName, taskName1, taskName2, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(33, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(workflowId, true)
                                            .getTasks()
                                            .isEmpty());
                        });

        await().atMost(33, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(workflowId, true)
                                            .getTasks()
                                            .isEmpty());
                            assertTrue(
                                    workflowClient.getWorkflow(workflowId, true).getTasks().size()
                                            > 1);
                        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        Task task = workflow.getTasks().get(1);
        workflow =
                taskClient.updateTaskSync(
                        workflowId,
                        task.getReferenceTaskName(),
                        TaskResult.Status.FAILED,
                        new HashMap<>());
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(task.getTaskId());
        // Rerun the workflow
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(workflow.getStatus().name(), Workflow.WorkflowStatus.RUNNING.name());
        assertEquals(workflow.getTasks().get(1).getStatus().name(), Task.Status.SCHEDULED.name());

        workflow =
                taskClient.updateTaskSync(
                        workflowId,
                        task.getReferenceTaskName(),
                        TaskResult.Status.COMPLETED,
                        new HashMap<>());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    @DisplayName("Fail a task and then rerun it")
    public void testFailTaskAndRerun() {
        String workflowName = "re-run-fail-task-workflow";
        String taskName1 = "re-run-fail-task1";
        String taskName2 = "re-run-fail-task2";

        // Register workflow
        registerWorkflowDef(workflowName, taskName1, taskName2, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Wait for tasks to be created
        await().atMost(33, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(workflowId, true)
                                            .getTasks()
                                            .isEmpty());
                            assertTrue(
                                    workflowClient.getWorkflow(workflowId, true).getTasks().size()
                                            > 1);
                        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);

        // Fail the first task
        Task task = workflow.getTasks().get(1);
        workflow =
                taskClient.updateTaskSync(
                        workflowId,
                        task.getReferenceTaskName(),
                        TaskResult.Status.FAILED,
                        new HashMap<>());
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        // Rerun from the failed task
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(task.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // Complete the rerun task
        workflow =
                taskClient.updateTaskSync(
                        workflowId,
                        task.getReferenceTaskName(),
                        TaskResult.Status.COMPLETED,
                        new HashMap<>());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    @DisplayName("Check workflow with sub_workflow task and rerun functionality")
    public void testRerunWithSubWorkflow() throws Exception {

        String workflowName = "workflow-re-run-with-sub-workflow";
        String taskName = "re-run-with-sub-task";
        String subWorkflowName = "workflow-re-run-sub-workflow";

        terminateExistingRunningWorkflows(workflowName);
        terminateExistingRunningWorkflows(subWorkflowName);

        // Register workflow
        registerWorkflowWithSubWorkflowDef(workflowName, subWorkflowName, taskName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.print("Workflow id is " + workflowId);
        // Wait for sub-workflow to be started and get its ID
        final String wfIdRerun = workflowId;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfIdRerun, true);
                            assertFalse(wf.getTasks().isEmpty());
                            assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            assertFalse(wf.getTasks().get(0).getSubWorkflowId().isBlank());
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        String subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        Task task = subWorkflow.getTasks().get(0);
        workflow = completeTask(task, TaskResult.Status.FAILED);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        // Rerun the sub workflow.
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(subworkflowId);
        rerunWorkflowRequest.setReRunFromTaskId(task.getTaskId());
        workflowClient.rerunWorkflow(subworkflowId, rerunWorkflowRequest);
        // Check the workflow status and few other parameters
        subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(0).getStatus());

        subWorkflow = completeTask(subWorkflow.getTasks().get(0), TaskResult.Status.COMPLETED);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWorkflow.getStatus());

        await().pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow1.getStatus());
                        });
    }

    @Test
    @DisplayName("Rerun task in sub-workflow after parent is archived should preserve parent tasks")
    public void testRerunSubWorkflowAfterArchival() {
        String workflowName = "archived-rerun-parent-wf";
        String subWorkflowName = "archived-rerun-child-wf";
        String taskName = "archived-rerun-simple-task";

        terminateExistingRunningWorkflows(workflowName);
        terminateExistingRunningWorkflows(subWorkflowName);

        registerWorkflowWithSubWorkflowDef(workflowName, subWorkflowName, taskName, metadataClient);

        StartWorkflowRequest startRequest = new StartWorkflowRequest();
        startRequest.setName(workflowName);
        startRequest.setVersion(1);
        String parentWorkflowId = workflowClient.startWorkflow(startRequest);
        System.out.println("Started parent workflow: " + parentWorkflowId);

        Workflow parentWf = workflowClient.getWorkflow(parentWorkflowId, true);
        String childWorkflowId = parentWf.getTasks().get(0).getSubWorkflowId();
        assertNotNull(childWorkflowId, "Child workflow ID should not be null");

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow child = workflowClient.getWorkflow(childWorkflowId, true);
                            assertFalse(child.getTasks().isEmpty(), "Child should have tasks");
                        });

        parentWf = workflowClient.getWorkflow(parentWorkflowId, true);
        int parentTaskCountBefore = parentWf.getTasks().size();

        Workflow childWf = workflowClient.getWorkflow(childWorkflowId, true);
        Task failedTask = childWf.getTasks().get(0);
        completeTask(failedTask, TaskResult.Status.FAILED);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentWorkflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.FAILED,
                                    parent.getStatus(),
                                    "Parent should be FAILED after child task failure");
                        });

        // forceUploadToDocumentStore is enterprise-only; skip archival wait in OSS
        // forceUploadToDocumentStore();

        // Rerun from the failed task in the child workflow
        RerunWorkflowRequest rerunRequest = new RerunWorkflowRequest();
        rerunRequest.setReRunFromWorkflowId(childWorkflowId);
        rerunRequest.setReRunFromTaskId(failedTask.getTaskId());
        workflowClient.rerunWorkflow(childWorkflowId, rerunRequest);

        // Wait for parent to be RUNNING
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentWorkflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING,
                                    parent.getStatus(),
                                    "Parent should be RUNNING after child rerun");
                        });

        // KEY ASSERTION: parent tasks should NOT be wiped
        Workflow parentAfterRerun = workflowClient.getWorkflow(parentWorkflowId, true);
        assertTrue(
                parentAfterRerun.getTasks().size() >= parentTaskCountBefore,
                String.format(
                        "BUG: Parent tasks wiped from %d to %d after rerun",
                        parentTaskCountBefore, parentAfterRerun.getTasks().size()));

        Task subWfTaskAfterRerun =
                parentAfterRerun.getTasks().stream()
                        .filter(t -> t.getTaskType().equals("SUB_WORKFLOW"))
                        .findFirst()
                        .orElse(null);
        assertNotNull(
                subWfTaskAfterRerun, "SUB_WORKFLOW task should still exist in parent after rerun");

        // Complete the rescheduled task in child
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow child = workflowClient.getWorkflow(childWorkflowId, true);
                            Task scheduledTask =
                                    child.getTasks().stream()
                                            .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                            .findFirst()
                                            .orElse(null);
                            assertNotNull(
                                    scheduledTask, "Rescheduled task should be SCHEDULED in child");
                        });

        childWf = workflowClient.getWorkflow(childWorkflowId, true);
        Task scheduledTask =
                childWf.getTasks().stream()
                        .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElseThrow();
        completeTask(scheduledTask, TaskResult.Status.COMPLETED);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentWorkflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    parent.getStatus(),
                                    "Parent should be COMPLETED after child completes");
                        });
    }

    public static class SearchResultList {
        public int totalHits = 0;
        public List<WorkflowSummary> results = new ArrayList<>();
    }

    @Test
    @DisplayName(
            "Check workflow with sub_workflow task and rerun functionality from parent workflow")
    public void testRerunWithSubWorkflowFromParentWorkflow() {

        String workflowName = "workflow-re-run-with-sub-workflow";
        String taskName = "re-run-with-sub-task";
        String subWorkflowName = "workflow-re-run-sub-workflow";

        terminateExistingRunningWorkflows(workflowName);
        terminateExistingRunningWorkflows(subWorkflowName);

        // Register workflow
        registerWorkflowWithSubWorkflowDef(workflowName, subWorkflowName, taskName, metadataClient);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.print("Workflow id is " + workflowId);
        // Wait for sub-workflow to be started and get its ID
        final String wfIdParentRerun = workflowId;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfIdParentRerun, true);
                            assertFalse(wf.getTasks().isEmpty());
                            assertNotNull(wf.getTasks().get(0).getSubWorkflowId());
                            assertFalse(wf.getTasks().get(0).getSubWorkflowId().isBlank());
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        String subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        Task task = subWorkflow.getTasks().get(0);
        workflow = completeTask(task, TaskResult.Status.FAILED);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        // Wait for parent workflow to transition to FAILED before rerunning
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        workflowClient
                                                .getWorkflow(wfIdParentRerun, false)
                                                .getStatus()));

        // Rerun the sub workflow.
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(subworkflowId);
        rerunWorkflowRequest.setReRunFromTaskId(task.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);
        // SubworkflowId will be changed since we are rerunning from parent workflow
        workflow = workflowClient.getWorkflow(workflowId, true);
        subworkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        // Check the workflow status and few other parameters
        subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(Task.Status.SCHEDULED, subWorkflow.getTasks().get(0).getStatus());

        subWorkflow = completeTask(subWorkflow.getTasks().get(0), TaskResult.Status.COMPLETED);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWorkflow.getStatus());

        await().pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfIdParentRerun, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    wf.getStatus(),
                                    "Parent workflow should be completed");
                        });
    }

    @Test
    @Disabled(
            "Fork-join rerun from a completed branch task does not re-schedule sibling branches in conductor-oss")
    @DisplayName("Check workflow fork join task rerun")
    public void testRerunForkJoinWorkflow() {

        String workflowName = "re-run-fork-workflow";
        // Register workflow
        registerForkJoinWorkflowDef(workflowName, metadataClient);

        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(4, workflow.getTasks().size());

        // Complete and Fail the simple task
        workflow = completeTask(workflow.getTasks().get(2), TaskResult.Status.COMPLETED);
        workflow =
                completeTask(
                        workflow.getTasks().get(1), TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);

        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(workflow.getTasks().get(1).getTaskId());

        // Retry the workflow
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);
        // Check the workflow status and few other parameters
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());

        workflow.getTasks().stream()
                .filter(task -> task.getWorkflowTask().getType().equals(TaskType.SIMPLE.toString()))
                .filter(simpleTask -> !simpleTask.getStatus().isTerminal())
                .forEach(
                        running ->
                                taskClient.updateTaskSync(
                                        workflowId,
                                        running.getReferenceTaskName(),
                                        TaskResult.Status.COMPLETED,
                                        new HashMap<>()));

        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    @Disabled(
            "Rerun from DO_WHILE task after fork terminates workflow instead of resuming in conductor-oss")
    @DisplayName("Check workflow fork join task rerun")
    public void testRerunForkJoinWorkflowWithLoopTask() {

        String workflowName = "re-run-fork-workflow-loop-task";
        // Register workflow
        registerForkJoinWorkflowDef2(workflowName, metadataClient);

        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(4, workflow.getTasks().size());

        // Complete and Fail the simple task
        completeTask(workflow.getTasks().get(1), TaskResult.Status.COMPLETED);
        completeTask(workflow.getTasks().get(2), TaskResult.Status.COMPLETED);

        // Wait for JOIN to complete and DO_WHILE to be scheduled (async after fork branches
        // complete)
        final String wfId1 = workflowId;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            assertTrue(
                                    workflowClient.getWorkflow(wfId1, true).getTasks().size() >= 5);
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);

        // terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated by e2e");
        // rerun the workflow using successful join task id.
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromTaskId(workflow.getTasks().get(4).getTaskId());
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);

        // Retry the workflow
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);
        // Check the workflow status and few other parameters
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfId1, true);
                            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
                            assertTrue(wf.getTasks().size() >= 6);
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(TaskType.JOIN.name(), workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals(TaskType.DO_WHILE.name(), workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated");
    }

    @Test
    @Disabled(
            "Rerun from DO_WHILE task after fork terminates workflow instead of resuming in conductor-oss")
    @DisplayName("Check workflow fork join task rerun as loop task to rerun from")
    public void testRerunForkJoinWorkflowWithLoopTask2() {

        String workflowName = "re-run-fork-workflow-loop-task";
        // Register workflow
        registerForkJoinWorkflowDef2(workflowName, metadataClient);

        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(4, workflow.getTasks().size());

        // Complete the fork branch tasks
        completeTask(workflow.getTasks().get(1), TaskResult.Status.COMPLETED);
        completeTask(workflow.getTasks().get(2), TaskResult.Status.COMPLETED);

        // Wait for JOIN to complete and DO_WHILE to be scheduled (async after fork branches
        // complete)
        final String wfId2 = workflowId;
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            assertTrue(
                                    workflowClient.getWorkflow(wfId2, true).getTasks().size() >= 5);
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);

        // terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated by e2e");
        // rerun the workflow using successful join task id.
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromTaskId(workflow.getTasks().get(4).getTaskId());
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);

        // Rerun the workflow
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);
        // Check the workflow status and few other parameters
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfId2, true);
                            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
                            assertTrue(wf.getTasks().size() >= 6);
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);

        assertEquals(TaskType.JOIN.name(), workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals(TaskType.DO_WHILE.name(), workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated");
    }

    @Test
    @Disabled(
            "Rerun from DO_WHILE iteration task after fork terminates workflow instead of resuming in conductor-oss")
    @DisplayName("Check workflow fork join task rerun with iteration more than 1")
    public void testRerunForkJoinWorkflowWithLoopOverTask() {

        String workflowName = "re-run-fork-workflow-loop-task";
        // Register workflow
        registerForkJoinWorkflowDef2(workflowName, metadataClient);

        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(4, workflow.getTasks().size());

        // Complete fork branches, then wait for DO_WHILE iteration 1 to appear
        final String wfId3 = workflowId;
        completeTask(workflow.getTasks().get(1), TaskResult.Status.COMPLETED);
        completeTask(workflow.getTasks().get(2), TaskResult.Status.COMPLETED);

        // Wait for JOIN to complete and first DO_WHILE iteration (indices 4,5) to be scheduled
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            assertTrue(
                                    workflowClient.getWorkflow(wfId3, true).getTasks().size() >= 6);
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);

        // Complete first iteration of the loop simple task (index 5)
        completeTask(workflow.getTasks().get(5), TaskResult.Status.COMPLETED);

        // Wait for second DO_WHILE iteration (index 6) to be scheduled
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            assertTrue(
                                    workflowClient.getWorkflow(wfId3, true).getTasks().size() >= 7);
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);

        // Complete second iteration of the loop simple task (index 6)
        completeTask(workflow.getTasks().get(6), TaskResult.Status.COMPLETED);
        workflow = workflowClient.getWorkflow(workflowId, true);

        // terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated by e2e");
        // rerun the workflow using first iteration loop task.
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        // Rerun from second iteration of the simple_task
        rerunWorkflowRequest.setReRunFromTaskId(workflow.getTasks().get(6).getTaskId());
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);

        // Retry the workflow
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);
        // Check the workflow status and few other parameters
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfId3, true);
                            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
                            assertTrue(wf.getTasks().size() >= 7);
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(TaskType.JOIN.name(), workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals(TaskType.DO_WHILE.name(), workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(6).getStatus());
        assertEquals(
                workflow.getTasks().get(4).getIteration(),
                workflow.getTasks().get(6).getIteration());
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Terminated");
    }

    @Test
    @Disabled(
            "Rerunning a RUNNING workflow is not allowed in conductor-oss (requires terminal state); conductor-oss throws ConflictException")
    @DisplayName("When rerunning from a duration wait task should be IN PROGRESS")
    void testRerunFromWaitTask() {
        var workflowDef = registerWaitWorkflow();

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowDef.getName());
        startWorkflowRequest.setVersion(1);
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Wait for the 1-second WAIT task to complete (WAIT sweeper may take up to 15s in
        // conductor-oss)
        final String wfIdWait = workflowId;
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(wfIdWait, true);
                            assertFalse(wf.getTasks().isEmpty());
                            assertEquals(
                                    Task.Status.COMPLETED,
                                    wf.getTasks().get(0).getStatus(),
                                    "1 second task not completed");
                            assertEquals(2, wf.getTasks().size(), "Expected 2 tasks");
                        });

        var workflow = workflowClient.getWorkflow(workflowId, true);
        // if task0 is completed, there's a worker polling and completing duration tasks
        var task0 = workflow.getTasks().get(0);
        assertEquals("WAIT", task0.getTaskType());
        assertEquals(Task.Status.COMPLETED, task0.getStatus(), "1 second task not completed");
        assertEquals(2, workflow.getTasks().size(), "Expected 2 tasks");

        // rerun from the 60 second duration task
        var task1 = workflow.getTasks().get(1);
        var rerunRequest = new RerunWorkflowRequest();
        rerunRequest.setReRunFromTaskId(task1.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunRequest);

        // After 3 seconds, if there is a worker polling and the
        // task was available to be polled it should have been polled
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(2, workflow.getTasks().size());

        task1 = workflow.getTasks().get(1);
        assertEquals("WAIT", task1.getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, task1.getStatus());

        var task2 = workflow.getTasks().get(1);
        assertEquals("WAIT", task2.getTaskType());
        // the task should NOT have been polled
        assertEquals(0, task2.getPollCount());
        // 60 seconds duration
        assertEquals(60, task2.getCallbackAfterSeconds(), task2.getCallbackAfterSeconds());
        // status should be IN_PROGRESS
        assertEquals(Task.Status.IN_PROGRESS, task2.getStatus());

        workflowClient.terminateWorkflow(workflowId, "Test passed");
    }

    @Test
    @Disabled(
            "Rerun from sub-workflow task inside fork terminates workflow instead of resuming in conductor-oss")
    @DisplayName(
            "Ticket #7097: Rerun from SUB_WORKFLOW task inside FORK should not restart from beginning")
    public void testRerunSubWorkflowInsideFork() {

        String parentWfName = "rerun-fork-subwf-parent-" + System.currentTimeMillis();
        String subWfName = "rerun-fork-subwf-child-" + System.currentTimeMillis();
        String simpleTaskName = "rerun-fork-subwf-simple-task";
        String simpleTaskBefore = "simple_task_before";
        String simpleTaskAfter = "simple_task_after";
        String subWfFork1Ref = "sub_wf_fork1";
        String subWfFork2Ref = "sub_wf_fork2";
        String forkRef = "fork_rerun_test";
        String joinRef = "join_rerun_test";

        TaskDef simpleTaskDef = new TaskDef(simpleTaskName);
        simpleTaskDef.setRetryCount(0);
        simpleTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask simpleTaskWt = new WorkflowTask();
        simpleTaskWt.setTaskReferenceName(simpleTaskName);
        simpleTaskWt.setName(simpleTaskName);
        simpleTaskWt.setTaskDefinition(simpleTaskDef);
        simpleTaskWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setDescription("Sub workflow for rerun-fork test");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(simpleTaskWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        TaskDef beforeTaskDef = new TaskDef(simpleTaskBefore);
        beforeTaskDef.setRetryCount(0);
        beforeTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask beforeTask = new WorkflowTask();
        beforeTask.setTaskReferenceName(simpleTaskBefore);
        beforeTask.setName(simpleTaskBefore);
        beforeTask.setTaskDefinition(beforeTaskDef);
        beforeTask.setWorkflowTaskType(TaskType.SIMPLE);

        SubWorkflowParams subParams1 = new SubWorkflowParams();
        subParams1.setName(subWfName);
        subParams1.setVersion(1);

        WorkflowTask subWfTask1 = new WorkflowTask();
        subWfTask1.setTaskReferenceName(subWfFork1Ref);
        subWfTask1.setName(subWfName);
        subWfTask1.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask1.setSubWorkflowParam(subParams1);

        SubWorkflowParams subParams2 = new SubWorkflowParams();
        subParams2.setName(subWfName);
        subParams2.setVersion(1);

        WorkflowTask subWfTask2 = new WorkflowTask();
        subWfTask2.setTaskReferenceName(subWfFork2Ref);
        subWfTask2.setName(subWfName);
        subWfTask2.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask2.setSubWorkflowParam(subParams2);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(subWfTask1), List.of(subWfTask2)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfFork1Ref, subWfFork2Ref));

        TaskDef afterTaskDef = new TaskDef(simpleTaskAfter);
        afterTaskDef.setRetryCount(0);
        afterTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask afterTask = new WorkflowTask();
        afterTask.setTaskReferenceName(simpleTaskAfter);
        afterTask.setName(simpleTaskAfter);
        afterTask.setTaskDefinition(afterTaskDef);
        afterTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setDescription("Test rerun from SUB_WORKFLOW inside FORK (#7097)");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(beforeTask, forkTask, joinTask, afterTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);

            String workflowId = workflowClient.startWorkflow(startRequest);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertFalse(wf.getTasks().isEmpty(), "Tasks should be created");
                            });

            Workflow workflow = workflowClient.getWorkflow(workflowId, true);

            Task taskBefore =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(simpleTaskBefore))
                            .findFirst()
                            .orElseThrow();
            workflow = completeTask(taskBefore, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertTrue(
                                        wf.getTasks().size() >= 5,
                                        "Should have at least 5 tasks. Got: "
                                                + wf.getTasks().size());
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            Task subWfTask1Instance =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork1Ref))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("sub_wf_fork1 task not found"));
            Task subWfTask2Instance =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork2Ref))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("sub_wf_fork2 task not found"));

            String subWfId1 = subWfTask1Instance.getSubWorkflowId();
            assertNotNull(subWfId1, "sub_wf_fork1 should have a subWorkflowId");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow sw = workflowClient.getWorkflow(subWfId1, true);
                                assertFalse(
                                        sw.getTasks().isEmpty(),
                                        "Sub-workflow 1 should have tasks");
                            });
            Workflow subWf1 = workflowClient.getWorkflow(subWfId1, true);
            completeTask(subWf1.getTasks().get(0), TaskResult.Status.COMPLETED);

            String subWfId2 = subWfTask2Instance.getSubWorkflowId();
            assertNotNull(subWfId2, "sub_wf_fork2 should have a subWorkflowId");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow sw = workflowClient.getWorkflow(subWfId2, true);
                                assertFalse(
                                        sw.getTasks().isEmpty(),
                                        "Sub-workflow 2 should have tasks");
                            });
            Workflow subWf2 = workflowClient.getWorkflow(subWfId2, true);
            completeTask(subWf2.getTasks().get(0), TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            String taskBeforeId =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(simpleTaskBefore))
                            .findFirst()
                            .orElseThrow()
                            .getTaskId();

            Task failedSubWfTask =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork2Ref))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "sub_wf_fork2 not found after failure"));

            assertEquals(Task.Status.FAILED, failedSubWfTask.getStatus());
            String failedTaskId = failedSubWfTask.getTaskId();

            RerunWorkflowRequest rerunRequest = new RerunWorkflowRequest();
            rerunRequest.setReRunFromWorkflowId(workflowId);
            rerunRequest.setReRunFromTaskId(failedTaskId);
            workflowClient.rerunWorkflow(workflowId, rerunRequest);

            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            workflow = workflowClient.getWorkflow(workflowId, true);

            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            Task taskBeforeAfterRerun =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(simpleTaskBefore))
                            .findFirst()
                            .orElse(null);
            assertNotNull(
                    taskBeforeAfterRerun, "simple_task_before should still exist after rerun");
            assertEquals(Task.Status.COMPLETED, taskBeforeAfterRerun.getStatus());
            assertEquals(taskBeforeId, taskBeforeAfterRerun.getTaskId());

            Task forkAfterRerun =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(forkRef))
                            .findFirst()
                            .orElse(null);
            assertNotNull(forkAfterRerun, "FORK task should still exist after rerun");

            Task rerunSubWfTask =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork2Ref))
                            .filter(
                                    t ->
                                            !t.getStatus().isTerminal()
                                                    || t.getStatus() == Task.Status.SCHEDULED)
                            .findFirst()
                            .orElse(null);
            assertNotNull(rerunSubWfTask, "sub_wf_fork2 should be re-scheduled after rerun");

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                Task swTask =
                                        wf.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                t.getReferenceTaskName()
                                                                        .equals(subWfFork2Ref))
                                                .findFirst()
                                                .orElseThrow();
                                assertNotNull(swTask.getSubWorkflowId());
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task rerunSubWf2 =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork2Ref))
                            .findFirst()
                            .orElseThrow();
            String newSubWfId2 = rerunSubWf2.getSubWorkflowId();

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow sw = workflowClient.getWorkflow(newSubWfId2, true);
                                assertFalse(sw.getTasks().isEmpty());
                            });
            Workflow newSubWf2 = workflowClient.getWorkflow(newSubWfId2, true);
            completeTask(newSubWf2.getTasks().get(0), TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertTrue(
                                        wf.getTasks().stream()
                                                .anyMatch(
                                                        t ->
                                                                t.getReferenceTaskName()
                                                                                .equals(
                                                                                        simpleTaskAfter)
                                                                        && !t.getStatus()
                                                                                .isTerminal()));
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task afterTask2 =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(simpleTaskAfter))
                            .findFirst()
                            .orElseThrow();
            completeTask(afterTask2, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                            });

        } finally {
            try {
                terminateExistingRunningWorkflows(parentWfName);
            } catch (Exception e) {
                /* ignore */
            }
            try {
                terminateExistingRunningWorkflows(subWfName);
            } catch (Exception e) {
                /* ignore */
            }
        }
    }

    @Test
    @Disabled(
            "Rerun from sub-workflow task inside fork terminates workflow instead of resuming in conductor-oss")
    @DisplayName(
            "Ticket #7097 (full): Rerun from second SUB_WORKFLOW in FORK branch with preceding task")
    public void testRerunSubWorkflowInsideFork_SequentialBranch() {

        String parentWfName = "rerun-fork-seq-subwf-" + System.currentTimeMillis();
        String subWfName = "rerun-fork-seq-subwf-child-" + System.currentTimeMillis();
        String simpleTaskName = "rerun-fork-seq-simple-task";
        String taskBeforeRef = "task_before_fork";
        String subWfBranch1Ref = "sub_wf_branch1";
        String subWfBranch2aRef = "sub_wf_branch2a";
        String subWfBranch2bRef = "sub_wf_branch2b";
        String forkRef = "fork_seq_test";
        String joinRef = "join_seq_test";

        TaskDef simpleTaskDef = new TaskDef(simpleTaskName);
        simpleTaskDef.setRetryCount(0);
        simpleTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask simpleTaskWt = new WorkflowTask();
        simpleTaskWt.setTaskReferenceName(simpleTaskName);
        simpleTaskWt.setName(simpleTaskName);
        simpleTaskWt.setTaskDefinition(simpleTaskDef);
        simpleTaskWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setDescription("Sub workflow for sequential fork rerun test");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(simpleTaskWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        TaskDef beforeTaskDef = new TaskDef(taskBeforeRef);
        beforeTaskDef.setRetryCount(0);
        beforeTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask beforeTask = new WorkflowTask();
        beforeTask.setTaskReferenceName(taskBeforeRef);
        beforeTask.setName(taskBeforeRef);
        beforeTask.setTaskDefinition(beforeTaskDef);
        beforeTask.setWorkflowTaskType(TaskType.SIMPLE);

        SubWorkflowParams subParams1 = new SubWorkflowParams();
        subParams1.setName(subWfName);
        subParams1.setVersion(1);

        WorkflowTask branch1Task = new WorkflowTask();
        branch1Task.setTaskReferenceName(subWfBranch1Ref);
        branch1Task.setName(subWfName);
        branch1Task.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        branch1Task.setSubWorkflowParam(subParams1);

        SubWorkflowParams subParams2a = new SubWorkflowParams();
        subParams2a.setName(subWfName);
        subParams2a.setVersion(1);

        WorkflowTask branch2aTask = new WorkflowTask();
        branch2aTask.setTaskReferenceName(subWfBranch2aRef);
        branch2aTask.setName(subWfName);
        branch2aTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        branch2aTask.setSubWorkflowParam(subParams2a);

        SubWorkflowParams subParams2b = new SubWorkflowParams();
        subParams2b.setName(subWfName);
        subParams2b.setVersion(1);

        WorkflowTask branch2bTask = new WorkflowTask();
        branch2bTask.setTaskReferenceName(subWfBranch2bRef);
        branch2bTask.setName(subWfName);
        branch2bTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        branch2bTask.setSubWorkflowParam(subParams2b);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(branch1Task), List.of(branch2aTask, branch2bTask)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfBranch1Ref, subWfBranch2bRef));

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setDescription("Exact reproduction of ticket #7097");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(beforeTask, forkTask, joinTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);

            String workflowId = workflowClient.startWorkflow(startRequest);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertFalse(wf.getTasks().isEmpty());
                            });

            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task taskBefore =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(taskBeforeRef))
                            .findFirst()
                            .orElseThrow();
            completeTask(taskBefore, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertTrue(wf.getTasks().size() >= 5);
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            Task branch1 =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfBranch1Ref))
                            .findFirst()
                            .orElseThrow();
            String subWfId1 = branch1.getSubWorkflowId();
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow sw = workflowClient.getWorkflow(subWfId1, true);
                                assertFalse(sw.getTasks().isEmpty());
                            });
            Workflow sw1 = workflowClient.getWorkflow(subWfId1, true);
            completeTask(sw1.getTasks().get(0), TaskResult.Status.COMPLETED);

            Task branch2a =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfBranch2aRef))
                            .findFirst()
                            .orElseThrow();
            String subWfId2a = branch2a.getSubWorkflowId();
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow sw = workflowClient.getWorkflow(subWfId2a, true);
                                assertFalse(sw.getTasks().isEmpty());
                            });
            Workflow sw2a = workflowClient.getWorkflow(subWfId2a, true);
            completeTask(sw2a.getTasks().get(0), TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertTrue(
                                        wf.getTasks().stream()
                                                .anyMatch(
                                                        t ->
                                                                t.getReferenceTaskName()
                                                                        .equals(subWfBranch2bRef)));
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task branch2b =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfBranch2bRef))
                            .findFirst()
                            .orElseThrow();
            String subWfId2b = branch2b.getSubWorkflowId();
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow sw = workflowClient.getWorkflow(subWfId2b, true);
                                assertFalse(sw.getTasks().isEmpty());
                            });

            Workflow sw2b = workflowClient.getWorkflow(subWfId2b, true);
            completeTask(sw2b.getTasks().get(0), TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            String taskBeforeId =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(taskBeforeRef))
                            .findFirst()
                            .orElseThrow()
                            .getTaskId();
            Task failedTask =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfBranch2bRef))
                            .findFirst()
                            .orElseThrow();

            RerunWorkflowRequest rerunRequest = new RerunWorkflowRequest();
            rerunRequest.setReRunFromWorkflowId(workflowId);
            rerunRequest.setReRunFromTaskId(failedTask.getTaskId());
            workflowClient.rerunWorkflow(workflowId, rerunRequest);

            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            workflow = workflowClient.getWorkflow(workflowId, true);

            Task taskBeforeAfterRerun =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(taskBeforeRef))
                            .findFirst()
                            .orElse(null);
            assertNotNull(taskBeforeAfterRerun, "task_before_fork should exist after rerun");
            assertEquals(Task.Status.COMPLETED, taskBeforeAfterRerun.getStatus());
            assertEquals(taskBeforeId, taskBeforeAfterRerun.getTaskId());

            assertNotNull(
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(forkRef))
                            .findFirst()
                            .orElse(null),
                    "FORK task should still exist");

            Task branch2aAfter =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfBranch2aRef))
                            .findFirst()
                            .orElse(null);
            assertNotNull(branch2aAfter, "sub_wf_branch2a should still exist");
            assertEquals(Task.Status.COMPLETED, branch2aAfter.getStatus());

            assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                Task swTask =
                                        wf.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                t.getReferenceTaskName()
                                                                        .equals(subWfBranch2bRef))
                                                .findFirst()
                                                .orElseThrow();
                                assertNotNull(swTask.getSubWorkflowId());
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task rerunBranch2b =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfBranch2bRef))
                            .findFirst()
                            .orElseThrow();
            String newSubWfId2b = rerunBranch2b.getSubWorkflowId();

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow sw = workflowClient.getWorkflow(newSubWfId2b, true);
                                assertFalse(sw.getTasks().isEmpty());
                            });
            Workflow newSw2b = workflowClient.getWorkflow(newSubWfId2b, true);
            completeTask(newSw2b.getTasks().get(0), TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                            });

        } finally {
            try {
                terminateExistingRunningWorkflows(parentWfName);
            } catch (Exception e) {
                /* ignore */
            }
            try {
                terminateExistingRunningWorkflows(subWfName);
            } catch (Exception e) {
                /* ignore */
            }
        }
    }

    @Test
    @DisplayName("Rerun from inner task of SUB_WORKFLOW in FORK branch")
    public void testRerunFromTaskInsideSubWorkflowInFork() {

        String parentWfName = "rerun-inner-task-fork-subwf-" + System.currentTimeMillis();
        String subWfName = "rerun-inner-task-fork-subwf-child-" + System.currentTimeMillis();
        String simpleTaskName = "rerun-inner-simple-task";
        String simpleTaskBefore = "simple_task_before";
        String simpleTaskAfter = "simple_task_after";
        String subWfFork1Ref = "sub_wf_fork1";
        String subWfFork2Ref = "sub_wf_fork2";
        String forkRef = "fork_inner_test";
        String joinRef = "join_inner_test";

        TaskDef simpleTaskDef = new TaskDef(simpleTaskName);
        simpleTaskDef.setRetryCount(0);
        simpleTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask simpleTaskWt = new WorkflowTask();
        simpleTaskWt.setTaskReferenceName(simpleTaskName);
        simpleTaskWt.setName(simpleTaskName);
        simpleTaskWt.setTaskDefinition(simpleTaskDef);
        simpleTaskWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setDescription("Sub workflow for inner task rerun test");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(simpleTaskWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        TaskDef beforeTaskDef = new TaskDef(simpleTaskBefore);
        beforeTaskDef.setRetryCount(0);
        beforeTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask beforeTask = new WorkflowTask();
        beforeTask.setTaskReferenceName(simpleTaskBefore);
        beforeTask.setName(simpleTaskBefore);
        beforeTask.setTaskDefinition(beforeTaskDef);
        beforeTask.setWorkflowTaskType(TaskType.SIMPLE);

        SubWorkflowParams subParams1 = new SubWorkflowParams();
        subParams1.setName(subWfName);
        subParams1.setVersion(1);
        WorkflowTask subWfTask1 = new WorkflowTask();
        subWfTask1.setTaskReferenceName(subWfFork1Ref);
        subWfTask1.setName(subWfName);
        subWfTask1.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask1.setSubWorkflowParam(subParams1);

        SubWorkflowParams subParams2 = new SubWorkflowParams();
        subParams2.setName(subWfName);
        subParams2.setVersion(1);
        WorkflowTask subWfTask2 = new WorkflowTask();
        subWfTask2.setTaskReferenceName(subWfFork2Ref);
        subWfTask2.setName(subWfName);
        subWfTask2.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask2.setSubWorkflowParam(subParams2);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(subWfTask1), List.of(subWfTask2)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfFork1Ref, subWfFork2Ref));

        TaskDef afterTaskDef = new TaskDef(simpleTaskAfter);
        afterTaskDef.setRetryCount(0);
        afterTaskDef.setOwnerEmail("test@conductor.io");
        WorkflowTask afterTask = new WorkflowTask();
        afterTask.setTaskReferenceName(simpleTaskAfter);
        afterTask.setName(simpleTaskAfter);
        afterTask.setTaskDefinition(afterTaskDef);
        afterTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setDescription("Test rerun from inner task of SUB_WORKFLOW in FORK");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(beforeTask, forkTask, joinTask, afterTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);
            String workflowId = workflowClient.startWorkflow(startRequest);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(workflowId, true)
                                                .getTasks()
                                                .isEmpty());
                            });

            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task taskBefore =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(simpleTaskBefore))
                            .findFirst()
                            .orElseThrow();
            workflow = completeTask(taskBefore, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertTrue(
                                        workflowClient
                                                        .getWorkflow(workflowId, true)
                                                        .getTasks()
                                                        .size()
                                                >= 5);
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            Task subWfTask1Instance =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork1Ref))
                            .findFirst()
                            .orElseThrow();
            Task subWfTask2Instance =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork2Ref))
                            .findFirst()
                            .orElseThrow();

            String subWfId1 = subWfTask1Instance.getSubWorkflowId();
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(subWfId1, true)
                                                .getTasks()
                                                .isEmpty());
                            });
            completeTask(
                    workflowClient.getWorkflow(subWfId1, true).getTasks().get(0),
                    TaskResult.Status.COMPLETED);

            String subWfId2 = subWfTask2Instance.getSubWorkflowId();
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(subWfId2, true)
                                                .getTasks()
                                                .isEmpty());
                            });
            Workflow subWf2 = workflowClient.getWorkflow(subWfId2, true);
            String innerFailedTaskId = subWf2.getTasks().get(0).getTaskId();
            completeTask(subWf2.getTasks().get(0), TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        workflowClient.getWorkflow(workflowId, true).getStatus());
                            });

            RerunWorkflowRequest rerunRequest = new RerunWorkflowRequest();
            rerunRequest.setReRunFromWorkflowId(workflowId);
            rerunRequest.setReRunFromTaskId(innerFailedTaskId);
            workflowClient.rerunWorkflow(workflowId, rerunRequest);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(
                                        Workflow.WorkflowStatus.RUNNING,
                                        wf.getStatus(),
                                        "Parent workflow should be RUNNING after nested rerun. "
                                                + "reason="
                                                + wf.getReasonForIncompletion());
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            Task taskBeforeAfterRerun =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(simpleTaskBefore))
                            .findFirst()
                            .orElse(null);
            assertNotNull(taskBeforeAfterRerun);
            assertEquals(Task.Status.COMPLETED, taskBeforeAfterRerun.getStatus());

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                Task swTask =
                                        wf.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                t.getReferenceTaskName()
                                                                        .equals(subWfFork2Ref))
                                                .findFirst()
                                                .orElseThrow();
                                assertNotNull(swTask.getSubWorkflowId());
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(swTask.getSubWorkflowId(), true)
                                                .getTasks()
                                                .isEmpty());
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task rerunSubWf2Task =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(subWfFork2Ref))
                            .findFirst()
                            .orElseThrow();
            String newSubWfId2 = rerunSubWf2Task.getSubWorkflowId();
            Workflow newSubWf2 = workflowClient.getWorkflow(newSubWfId2, true);
            completeTask(newSubWf2.getTasks().get(0), TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertTrue(
                                        wf.getTasks().stream()
                                                .anyMatch(
                                                        t ->
                                                                t.getReferenceTaskName()
                                                                                .equals(
                                                                                        simpleTaskAfter)
                                                                        && !t.getStatus()
                                                                                .isTerminal()));
                            });

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task afterTask2 =
                    workflow.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(simpleTaskAfter))
                            .findFirst()
                            .orElseThrow();
            completeTask(afterTask2, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertEquals(
                                        Workflow.WorkflowStatus.COMPLETED,
                                        workflowClient.getWorkflow(workflowId, true).getStatus());
                            });

        } finally {
            try {
                terminateExistingRunningWorkflows(parentWfName);
            } catch (Exception e) {
                /* ignore */
            }
            try {
                terminateExistingRunningWorkflows(subWfName);
            } catch (Exception e) {
                /* ignore */
            }
        }
    }

    @Test
    @Disabled(
            "DO_WHILE task rerun leaves workflow TERMINATED due to sync task status not being reset to SCHEDULED in conductor-oss")
    @DisplayName(
            "Test do_while task rerun - verify cancellation of previous iterations and fresh start")
    public void testDoWhileRerun() {
        String workflowName = "rerun-do-while-workflow";
        WorkflowDef workflowDef = registerDoWhileWorkflowDef(workflowName);

        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setInput(Map.of("maxIterations", 3));

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("simple_do_while_task", "test-domain");
        startWorkflowRequest.setTaskToDomain(taskToDomain);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertFalse(workflow.getTasks().isEmpty());
                        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertNotNull(workflow.getTaskToDomain());
        assertEquals("test-domain", workflow.getTaskToDomain().get("simple_do_while_task"));

        Task firstIterationTask =
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        "simple_do_while_task".equals(t.getTaskDefName())
                                                && t.getIteration() == 1)
                        .findFirst()
                        .orElseThrow();
        assertEquals("test-domain", firstIterationTask.getDomain());
        completeTask(firstIterationTask, TaskResult.Status.COMPLETED);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertTrue(wf.getTasks().stream().anyMatch(t -> t.getIteration() == 2));
                        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        Task secondIterationTask =
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        "simple_do_while_task".equals(t.getTaskDefName())
                                                && t.getIteration() == 2)
                        .findFirst()
                        .orElseThrow();
        assertEquals("test-domain", secondIterationTask.getDomain());

        String firstTaskIdBeforeRerun =
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        "simple_do_while_task".equals(t.getTaskDefName())
                                                && t.getIteration() == 1)
                        .map(Task::getTaskId)
                        .findFirst()
                        .orElseThrow();
        String secondTaskIdBeforeRerun = secondIterationTask.getTaskId();

        workflow = completeTask(secondIterationTask, TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        Task doWhileTask =
                workflow.getTasks().stream()
                        .filter(t -> TaskType.DO_WHILE.name().equals(t.getTaskType()))
                        .findFirst()
                        .orElseThrow();

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(doWhileTask.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        assertEquals(TaskType.DO_WHILE.name(), workflow.getTasks().get(0).getTaskType());
        assertEquals(1, workflow.getTasks().get(0).getIteration());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(0).getStatus());

        Task firstIterationAfterRerun = workflow.getTasks().get(1);
        assertEquals("simple_do_while_task", firstIterationAfterRerun.getTaskType());
        assertEquals(Task.Status.SCHEDULED, firstIterationAfterRerun.getStatus());
        assertEquals(1, firstIterationAfterRerun.getIteration());
        assertEquals("test-domain", firstIterationAfterRerun.getDomain());

        assertNotEquals(firstTaskIdBeforeRerun, firstIterationAfterRerun.getTaskId());

        boolean hasSecondIterationAfterRerun =
                workflow.getTasks().stream().anyMatch(t -> t.getIteration() == 2);
        assertFalse(hasSecondIterationAfterRerun);
    }

    @Test
    @Disabled(
            "SWITCH task rerun leaves workflow TERMINATED due to sync task status not being reset to SCHEDULED in conductor-oss")
    @DisplayName(
            "Test switch task rerun - verify cancellation of previous branches and re-execution")
    public void testSwitchTaskRerun() {
        String workflowName = "rerun-switch-workflow";
        registerSwitchWorkflowDef(workflowName);

        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setInput(Map.of("switchValue", "case1"));

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task case1Task = workflow.getTasks().get(1);

        workflow = completeTask(case1Task, TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        Task switchTask = workflow.getTasks().get(0);

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(switchTask.getTaskId());
        rerunWorkflowRequest.setTaskInput(Map.of("switchValue", "case2"));
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());
        assertEquals("case2_task", workflow.getTasks().get(1).getReferenceTaskName());

        workflow = completeTask(workflow.getTasks().get(1), TaskResult.Status.COMPLETED);
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    @Disabled(
            "Rerun from fork-join workflow with wait/switch tasks terminates workflow instead of resuming in conductor-oss")
    @DisplayName("Test rerun with fork-join containing wait, wait_for_webhook, and switch tasks")
    public void testRerunForkJoinWithWaitAndSwitchTasks() {
        String workflowName = "test-rerun-fork-join-wait-switch-" + System.currentTimeMillis();

        registerForkJoinWithWaitAndSwitchWorkflow(workflowName);
        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        assertNotNull(workflowId);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertTrue(workflow.getTasks().size() >= 5);
                            assertTrue(
                                    workflow.getTasks().stream()
                                            .anyMatch(
                                                    t ->
                                                            "wait_task"
                                                                            .equals(
                                                                                    t
                                                                                            .getReferenceTaskName())
                                                                    && Task.Status.IN_PROGRESS
                                                                            .equals(
                                                                                    t
                                                                                            .getStatus())));
                            assertTrue(
                                    workflow.getTasks().stream()
                                            .anyMatch(
                                                    t ->
                                                            "wait_for_webhook_task"
                                                                            .equals(
                                                                                    t
                                                                                            .getReferenceTaskName())
                                                                    && Task.Status.IN_PROGRESS
                                                                            .equals(
                                                                                    t
                                                                                            .getStatus())));
                            assertTrue(
                                    workflow.getTasks().stream()
                                            .anyMatch(
                                                    t ->
                                                            "simple_task_case1"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName())));
                            assertTrue(
                                    workflow.getTasks().stream()
                                            .anyMatch(
                                                    t ->
                                                            "simple_task_fork4"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName())));
                        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task simpleTask =
                workflow.getTasks().stream()
                        .filter(t -> "simple_task_case1".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Simple task not found"));

        workflow = completeTask(simpleTask, TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Task.Status.CANCELED,
                                    wf.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            "wait_task"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName()))
                                            .findFirst()
                                            .orElseThrow()
                                            .getStatus());
                            assertEquals(
                                    Task.Status.CANCELED,
                                    wf.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            "wait_for_webhook_task"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName()))
                                            .findFirst()
                                            .orElseThrow()
                                            .getStatus());
                        });

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(simpleTask.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING,
                                    workflowClient.getWorkflow(workflowId, true).getStatus());
                        });

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Task.Status.IN_PROGRESS,
                                    wf.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            "wait_task"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName()))
                                            .reduce((first, second) -> second)
                                            .orElseThrow()
                                            .getStatus());
                            assertEquals(
                                    Task.Status.IN_PROGRESS,
                                    wf.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            "wait_for_webhook_task"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName()))
                                            .reduce((first, second) -> second)
                                            .orElseThrow()
                                            .getStatus());
                            Task newSimpleTask =
                                    wf.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            "simple_task_case1"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName()))
                                            .reduce((first, second) -> second)
                                            .orElseThrow();
                            assertTrue(
                                    newSimpleTask.getStatus() == Task.Status.SCHEDULED
                                            || newSimpleTask.getStatus()
                                                    == Task.Status.IN_PROGRESS);
                            Task newSimpleTaskFork4 =
                                    wf.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            "simple_task_fork4"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName()))
                                            .reduce((first, second) -> second)
                                            .orElseThrow();
                            assertTrue(
                                    newSimpleTaskFork4.getStatus() == Task.Status.SCHEDULED
                                            || newSimpleTaskFork4.getStatus()
                                                    == Task.Status.IN_PROGRESS);
                        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        Task simpleTaskFork4ToComplete =
                workflow.getTasks().stream()
                        .filter(t -> "simple_task_fork4".equals(t.getReferenceTaskName()))
                        .reduce((first, second) -> second)
                        .orElseThrow();
        completeTask(simpleTaskFork4ToComplete, TaskResult.Status.COMPLETED);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Task.Status.COMPLETED,
                                    wf.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            "wait_task"
                                                                    .equals(
                                                                            t
                                                                                    .getReferenceTaskName()))
                                            .reduce((first, second) -> second)
                                            .orElseThrow()
                                            .getStatus());
                        });

        workflowClient.terminateWorkflows(List.of(workflowId), "e2e");
    }

    @Test
    @Disabled(
            "SWITCH task rerun leaves workflow TERMINATED due to sync task status not being reset to SCHEDULED in conductor-oss")
    @SneakyThrows
    @DisplayName("When rerunning a workflow, tasks in a SWITCH are executed again")
    public void switchRerunIssue() {
        var mapper = new ObjectMapperProvider().getObjectMapper();

        var wfDef =
                mapper.readValue(
                        getResourceAsString("metadata/switch_rerun_issue.json"), WorkflowDef.class);
        metadataClient.updateWorkflowDefs(java.util.List.of(wfDef));

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wfDef.getName());
        startWorkflowRequest.setVersion(wfDef.getVersion());
        startWorkflowRequest.setInput(Map.of("case", "yes"));

        var wfId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(wfId, false);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                        });

        var workflow = workflowClient.getWorkflow(wfId, true);
        assertSwitchRerunIssueWorkflowState(workflow);

        var task0 = workflow.getTasks().getFirst();

        var rerunRequest = new RerunWorkflowRequest();
        rerunRequest.setReRunFromTaskId(task0.getTaskId());

        var rerun = workflowClient.rerunWorkflow(workflow.getWorkflowId(), rerunRequest);
        assertNotNull(rerun);
        assertEquals(workflow.getWorkflowId(), rerun);

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(wfId, false);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                        });

        workflow = workflowClient.getWorkflow(wfId, true);
        assertSwitchRerunIssueWorkflowState(workflow);
    }

    private static void assertSwitchRerunIssueWorkflowState(Workflow wf) {
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
        assertEquals(4, wf.getTasks().size());

        var inline1 = wf.getTaskByRefName("inline_ref_1");
        assertEquals(Task.Status.COMPLETED, inline1.getStatus());
        assertNotNull(inline1.getOutputData().get("result"));

        var wait = wf.getTaskByRefName("wait_ref");
        assertEquals(Task.Status.COMPLETED, wait.getStatus());

        var switchTask = wf.getTaskByRefName("switch_ref");
        assertEquals(Task.Status.COMPLETED, switchTask.getStatus());

        var inline2 = wf.getTaskByRefName("inline_ref_2");
        assertEquals(Task.Status.COMPLETED, inline2.getStatus());
        assertNotNull(inline2.getOutputData().get("result"));

        assertEquals(inline1.getOutputData().get("result"), inline2.getOutputData().get("result"));
        assertEquals(1, inline1.getSeq());
        assertEquals(2, wait.getSeq());
        assertEquals(3, switchTask.getSeq());
        assertEquals(4, inline2.getSeq());
    }

    @Test
    @Disabled("SWITCH task inside DO_WHILE rerun leaves workflow TERMINATED in conductor-oss")
    @SneakyThrows
    @DisplayName(
            "When rerunning a workflow, tasks in a SWITCH task inside a DO_WHILE are executed again")
    public void switchRerunIssue2() {
        var mapper = new ObjectMapperProvider().getObjectMapper();

        var wfDef =
                mapper.readValue(
                        getResourceAsString("metadata/switch_rerun_issue_2.json"),
                        WorkflowDef.class);
        metadataClient.updateWorkflowDefs(java.util.List.of(wfDef));

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wfDef.getName());
        startWorkflowRequest.setVersion(wfDef.getVersion());
        startWorkflowRequest.setInput(Map.of("case", "yes"));

        var wfId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(wfId, false);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                        });

        var workflow = workflowClient.getWorkflow(wfId, true);
        assertSwitchRerunIssue2WorkflowState(workflow);

        var task0 =
                workflow.getTasks().stream()
                        .filter(it -> it.getReferenceTaskName().equals("inline_ref_1__3"))
                        .findFirst()
                        .orElseThrow();

        var rerunRequest = new RerunWorkflowRequest();
        rerunRequest.setReRunFromTaskId(task0.getTaskId());

        var rerun = workflowClient.rerunWorkflow(workflow.getWorkflowId(), rerunRequest);
        assertNotNull(rerun);
        assertEquals(workflow.getWorkflowId(), rerun);

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var wf = workflowClient.getWorkflow(wfId, false);
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                        });

        workflow = workflowClient.getWorkflow(wfId, true);
        assertSwitchRerunIssue2WorkflowState(workflow);
    }

    private static void assertSwitchRerunIssue2WorkflowState(Workflow wf) {
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());

        var inline1 = wf.getTaskByRefName("inline_ref_1__3");
        assertEquals(Task.Status.COMPLETED, inline1.getStatus());
        assertNotNull(inline1.getOutputData().get("result"));

        var wait = wf.getTaskByRefName("wait_ref__3");
        assertEquals(Task.Status.COMPLETED, wait.getStatus());

        var switchTask0 = wf.getTaskByRefName("switch_ref__3");
        assertEquals(Task.Status.COMPLETED, switchTask0.getStatus());

        var switchTask1 = wf.getTaskByRefName("switch_ref_1__3");
        assertEquals(Task.Status.COMPLETED, switchTask1.getStatus());

        var inline2 = wf.getTaskByRefName("inline_ref_2__3");
        assertEquals(Task.Status.COMPLETED, inline2.getStatus());
        assertNotNull(inline2.getOutputData().get("result"));

        var inline3 = wf.getTaskByRefName("inline_ref_3__3");
        assertEquals(Task.Status.COMPLETED, inline3.getStatus());
        assertNotNull(inline3.getOutputData().get("result"));

        assertEquals(inline1.getOutputData().get("result"), inline2.getOutputData().get("result"));
        assertEquals(inline1.getOutputData().get("result"), inline3.getOutputData().get("result"));
    }

    private Workflow completeTask(Task task, TaskResult.Status status) {
        return taskClient.updateTaskSync(
                task.getWorkflowInstanceId(),
                task.getReferenceTaskName(),
                status,
                task.getOutputData());
    }

    private void terminateExistingRunningWorkflows(String workflowName) {
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
                                        workflowSummary.getWorkflowId(), "terminate - rerun test");
                            });
        } catch (Exception e) {
            if (!(e instanceof ConductorClientException)) {
                throw e;
            }
        }
    }

    private void registerForkJoinWorkflowDef(String workflowName, MetadataClient metadataClient) {

        String fork_task = "fork_task";
        String fork_task1 = "fork_task_1";
        String fork_task2 = "fork_task_2";
        String join_task = "join_task";

        WorkflowTask fork_task_1 = new WorkflowTask();
        fork_task_1.setTaskReferenceName(fork_task1);
        fork_task_1.setName(fork_task1);
        fork_task_1.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask fork_task_2 = new WorkflowTask();
        fork_task_2.setTaskReferenceName(fork_task2);
        fork_task_2.setName(fork_task2);
        fork_task_2.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask fork_workflow_task = new WorkflowTask();
        fork_workflow_task.setTaskReferenceName(fork_task);
        fork_workflow_task.setName(fork_task);
        fork_workflow_task.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork_workflow_task.setForkTasks(List.of(List.of(fork_task_1), List.of(fork_task_2)));

        WorkflowTask join = new WorkflowTask();
        join.setTaskReferenceName(join_task);
        join.setName(join_task);
        join.setWorkflowTaskType(TaskType.JOIN);
        join.setJoinOn(List.of(fork_task1, fork_task2));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test retry");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(fork_workflow_task, join));
        workflowDef.setOwnerEmail("test@conductor.io");
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }

    private void registerForkJoinWorkflowDef2(String workflowName, MetadataClient metadataClient) {

        String fork_task = "fork_task";
        String fork_task1 = "fork_task_1";
        String fork_task2 = "fork_task_2";
        String join_task = "join_task";
        String loop_task = "loop_task";
        String simple_task = "simple_task";

        WorkflowTask fork_task_1 = new WorkflowTask();
        fork_task_1.setTaskReferenceName(fork_task1);
        fork_task_1.setName(fork_task1);
        fork_task_1.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask fork_task_2 = new WorkflowTask();
        fork_task_2.setTaskReferenceName(fork_task2);
        fork_task_2.setName(fork_task2);
        fork_task_2.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(simple_task);
        simpleTask.setName(simple_task);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask loopTask = new WorkflowTask();
        loopTask.setTaskReferenceName(loop_task);
        loopTask.setName(loop_task);
        loopTask.setWorkflowTaskType(TaskType.DO_WHILE);
        loopTask.setLoopCondition("if ($.loop_task['iteration'] < 10) { true; } else { false;} ");
        loopTask.setLoopOver(List.of(simpleTask));

        WorkflowTask fork_workflow_task = new WorkflowTask();
        fork_workflow_task.setTaskReferenceName(fork_task);
        fork_workflow_task.setName(fork_task);
        fork_workflow_task.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork_workflow_task.setForkTasks(List.of(List.of(fork_task_1), List.of(fork_task_2)));

        WorkflowTask join = new WorkflowTask();
        join.setTaskReferenceName(join_task);
        join.setName(join_task);
        join.setWorkflowTaskType(TaskType.JOIN);
        join.setJoinOn(List.of(fork_task1, fork_task2));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test retry");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(fork_workflow_task, join, loopTask));
        workflowDef.setOwnerEmail("test@conductor.io");
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }

    private WorkflowDef registerWaitWorkflow() {
        var waitTask0 = new WorkflowTask();
        waitTask0.setTaskReferenceName("wait_0_ref");
        waitTask0.setName("wait_0");
        waitTask0.setWorkflowTaskType(TaskType.WAIT);
        waitTask0.setInputParameters(Map.of("duration", "1 seconds"));

        var waitTask1 = new WorkflowTask();
        waitTask1.setTaskReferenceName("wait_1_ref");
        waitTask1.setName("wait_1");
        waitTask1.setWorkflowTaskType(TaskType.WAIT);
        waitTask1.setInputParameters(Map.of("duration", "60 seconds"));

        var workflowDef = new WorkflowDef();
        workflowDef.setName("testRerunWaitTask");
        workflowDef.setVersion(1);
        workflowDef.setDescription("Rerun On Wait Task");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(List.of(waitTask0, waitTask1));
        workflowDef.setOwnerEmail("test@conductor.io");

        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
        return workflowDef;
    }

    private WorkflowDef registerDoWhileWorkflowDef(String workflowName) {
        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName("simple_do_while_task");
        simpleTask.setName("simple_do_while_task");
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask doWhileTask = new WorkflowTask();
        doWhileTask.setTaskReferenceName("do_while_task");
        doWhileTask.setName("do_while_task");
        doWhileTask.setInputParameters(Map.of("maxIterations", "${workflow.input.maxIterations}"));
        doWhileTask.setWorkflowTaskType(TaskType.DO_WHILE);
        doWhileTask.setLoopCondition(
                "if ($.do_while_task['iteration'] < $.maxIterations) { true; } else { false; }");
        doWhileTask.setLoopOver(List.of(simpleTask));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setDescription("Test do_while rerun behavior");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(List.of(doWhileTask));
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setInputParameters(List.of("maxIterations"));

        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
        return workflowDef;
    }

    private void registerSwitchWorkflowDef(String workflowName) {
        WorkflowTask case1Task = new WorkflowTask();
        case1Task.setTaskReferenceName("case1_task");
        case1Task.setName("case1_task");
        case1Task.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask case2Task = new WorkflowTask();
        case2Task.setTaskReferenceName("case2_task");
        case2Task.setName("case2_task");
        case2Task.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask defaultTask = new WorkflowTask();
        defaultTask.setTaskReferenceName("default_task");
        defaultTask.setName("default_task");
        defaultTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setTaskReferenceName("switch_task");
        switchTask.setName("switch_task");
        switchTask.setWorkflowTaskType(TaskType.SWITCH);
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchValue");
        switchTask.getDecisionCases().put("case1", List.of(case1Task));
        switchTask.getDecisionCases().put("case2", List.of(case2Task));
        switchTask.setDefaultCase(List.of(defaultTask));
        switchTask.setInputParameters(Map.of("switchValue", "${workflow.input.switchValue}"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setDescription("Test switch rerun behavior");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(List.of(switchTask));
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setInputParameters(List.of("switchValue"));

        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }

    private void registerForkJoinWithWaitAndSwitchWorkflow(String workflowName) {
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setTaskReferenceName("wait_task");
        waitTask.setName("wait_task");
        waitTask.setWorkflowTaskType(TaskType.WAIT);
        waitTask.setInputParameters(Map.of("duration", "10 seconds"));

        WorkflowTask waitForWebhookTask = new WorkflowTask();
        waitForWebhookTask.setTaskReferenceName("wait_for_webhook_task");
        waitForWebhookTask.setName("wait_for_webhook_task");
        waitForWebhookTask.setType("WAIT_FOR_WEBHOOK");
        waitForWebhookTask.setInputParameters(
                Map.of("matches", Map.of("$['id']", "${workflow.input.key}")));

        WorkflowTask simpleTaskCase1 = new WorkflowTask();
        simpleTaskCase1.setTaskReferenceName("simple_task_case1");
        simpleTaskCase1.setName("simple_task_case1");
        simpleTaskCase1.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask httpTaskCase2 = new WorkflowTask();
        httpTaskCase2.setTaskReferenceName("http_task_case2");
        httpTaskCase2.setName("http_task_case2");
        httpTaskCase2.setWorkflowTaskType(TaskType.HTTP);
        httpTaskCase2.setInputParameters(
                Map.of("uri", "https://orkes-api-tester.orkesconductor.com/api", "method", "GET"));

        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setTaskReferenceName("switch_task");
        switchTask.setName("switch_task");
        switchTask.setWorkflowTaskType(TaskType.SWITCH);
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchValue");
        switchTask.getDecisionCases().put("case1", List.of(simpleTaskCase1));
        switchTask.getDecisionCases().put("case2", List.of(httpTaskCase2));
        switchTask.setDefaultCase(new ArrayList<>());
        switchTask.setInputParameters(Map.of("switchValue", "case1"));

        WorkflowTask simpleTaskFork4 = new WorkflowTask();
        simpleTaskFork4.setTaskReferenceName("simple_task_fork4");
        simpleTaskFork4.setName("simple_task_fork4");
        simpleTaskFork4.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName("fork_task");
        forkTask.setName("fork_task");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(
                List.of(
                        List.of(waitTask),
                        List.of(waitForWebhookTask),
                        List.of(switchTask),
                        List.of(simpleTaskFork4)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName("join_task");
        joinTask.setName("join_task");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(
                List.of("wait_task", "wait_for_webhook_task", "switch_task", "simple_task_fork4"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setDescription(
                "Test rerun with fork-join containing wait, wait_for_webhook, and switch tasks");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(List.of(forkTask, joinTask));
        workflowDef.setOwnerEmail("test@conductor.io");

        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }
}
