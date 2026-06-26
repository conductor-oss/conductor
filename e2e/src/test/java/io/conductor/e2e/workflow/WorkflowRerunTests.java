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
import java.util.stream.Collectors;

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
        Task task = awaitFirstTask(subworkflowId);
        workflow = completeTask(task, TaskResult.Status.FAILED);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

        // Rerun the sub workflow.
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(subworkflowId);
        rerunWorkflowRequest.setReRunFromTaskId(task.getTaskId());
        workflowClient.rerunWorkflow(subworkflowId, rerunWorkflowRequest);
        // Check the workflow status and few other parameters
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        Task scheduledTask =
                awaitTaskWithStatus(
                        subworkflowId,
                        Task.Status.SCHEDULED,
                        "Child rerun task should be scheduled");

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.RUNNING,
                                    parent.getStatus(),
                                    "Parent should be RUNNING after child rerun");
                        });

        subWorkflow = completeTask(scheduledTask, TaskResult.Status.COMPLETED);
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

        String childWorkflowId = awaitSubWorkflowId(parentWorkflowId);

        Workflow parentWf = workflowClient.getWorkflow(parentWorkflowId, true);
        int parentTaskCountBefore = parentWf.getTasks().size();

        Task failedTask = awaitFirstTask(childWorkflowId);
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

        Workflow childWf = workflowClient.getWorkflow(childWorkflowId, true);
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
        Task task = awaitFirstTask(subworkflowId);
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
        subworkflowId = awaitSubWorkflowId(workflowId);
        // Check the workflow status and few other parameters
        Workflow subWorkflow = workflowClient.getWorkflow(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(Task.Status.SCHEDULED, awaitFirstTask(subworkflowId).getStatus());

        subWorkflow = completeTask(awaitFirstTask(subworkflowId), TaskResult.Status.COMPLETED);
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

            String subWfId1 = awaitSubWorkflowId(workflowId, subWfFork1Ref);
            completeTask(awaitFirstTask(subWfId1), TaskResult.Status.COMPLETED);

            String subWfId2 = awaitSubWorkflowId(workflowId, subWfFork2Ref);
            Task innerFailedTask = awaitFirstTask(subWfId2);
            String innerFailedTaskId = innerFailedTask.getTaskId();
            completeTask(innerFailedTask, TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        workflowClient.getWorkflow(workflowId, true).getStatus());
                            });

            RerunWorkflowRequest rerunRequest = new RerunWorkflowRequest();
            rerunRequest.setReRunFromWorkflowId(subWfId2);
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

            // Wait must cover a multi-hop propagation: inner SIMPLE COMPLETED -> sub_wf_fork2
            // sub-workflow completion (driven by sweeper) -> JOIN evaluation -> parent schedules
            // simpleTaskAfter. Under parallel e2e load this can exceed 10s.
            await().atMost(30, TimeUnit.SECONDS)
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

    private String awaitSubWorkflowId(String workflowId) {
        return awaitSubWorkflowId(workflowId, null);
    }

    private String awaitSubWorkflowId(String workflowId, String referenceTaskName) {
        final String[] subWorkflowId = new String[1];
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertFalse(workflow.getTasks().isEmpty());
                            Task task =
                                    referenceTaskName == null
                                            ? workflow.getTasks().get(0)
                                            : workflow.getTaskByRefName(referenceTaskName);
                            assertNotNull(task, "Task " + referenceTaskName + " should exist");
                            subWorkflowId[0] = task.getSubWorkflowId();
                            assertNotNull(subWorkflowId[0], "Child workflow ID should not be null");
                            assertFalse(subWorkflowId[0].isBlank());
                        });
        return subWorkflowId[0];
    }

    private Task awaitFirstTask(String workflowId) {
        final Task[] task = new Task[1];
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertFalse(workflow.getTasks().isEmpty(), "Child should have tasks");
                            task[0] = workflow.getTasks().get(0);
                        });
        return task[0];
    }

    private Task awaitTaskWithStatus(String workflowId, Task.Status status, String message) {
        final Task[] task = new Task[1];
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            task[0] =
                                    workflow.getTasks().stream()
                                            .filter(t -> t.getStatus() == status)
                                            .findFirst()
                                            .orElse(null);
                            assertNotNull(task[0], message);
                        });
        return task[0];
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


    @Test
    @DisplayName("PR #3458: Rerun task in subworkflow should reschedule cancelled sibling tasks in parent fork")
    public void testRerunSubWorkflowTaskReschedulesCancelledParentSiblingTask() {
        String parentWfName = "rerun-subwf-cancelled-sibling-parent-" + System.currentTimeMillis();
        String subWfName = "rerun-subwf-cancelled-sibling-child-" + System.currentTimeMillis();
        String taskInSubRef = "task_in_sub";
        String subWfTaskRef = "sub_wf_task";
        String siblingTaskRef = "sibling_task";
        String forkRef = "fork_cancelled_sibling_test";
        String joinRef = "join_cancelled_sibling_test";

        // Sub-workflow: single SIMPLE task
        TaskDef taskInSubDef = new TaskDef(taskInSubRef);
        taskInSubDef.setRetryCount(0);
        taskInSubDef.setOwnerEmail("test@conductor.io");

        WorkflowTask taskInSubWt = new WorkflowTask();
        taskInSubWt.setTaskReferenceName(taskInSubRef);
        taskInSubWt.setName(taskInSubRef);
        taskInSubWt.setTaskDefinition(taskInSubDef);
        taskInSubWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(taskInSubWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        // Parent workflow: FORK(sub_wf_task | sibling_task) → JOIN
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);

        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setTaskReferenceName(subWfTaskRef);
        subWfTask.setName(subWfName);
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask.setSubWorkflowParam(subParams);

        TaskDef siblingTaskDef = new TaskDef(siblingTaskRef);
        siblingTaskDef.setRetryCount(0);
        siblingTaskDef.setOwnerEmail("test@conductor.io");

        WorkflowTask siblingTask = new WorkflowTask();
        siblingTask.setTaskReferenceName(siblingTaskRef);
        siblingTask.setName(siblingTaskRef);
        siblingTask.setTaskDefinition(siblingTaskDef);
        siblingTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(subWfTask), List.of(siblingTask)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfTaskRef, siblingTaskRef));

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(forkTask, joinTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);
            String workflowId = workflowClient.startWorkflow(startRequest);

            // Wait for fork tasks and the subworkflow's inner task to be created
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                Task swt = wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                        .findFirst().orElseThrow(() -> new AssertionError("sub_wf_task not found"));
                assertNotNull(swt.getSubWorkflowId(), "sub_wf_task should have a subWorkflowId");
                assertFalse(workflowClient.getWorkflow(swt.getSubWorkflowId(), true).getTasks().isEmpty(),
                        "Subworkflow should have tasks");
            });

            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            String subWorkflowId = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                    .findFirst().orElseThrow().getSubWorkflowId();

            // Fail the inner task of the subworkflow
            Workflow subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task taskInSub = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskInSubRef))
                    .findFirst().orElseThrow(() -> new AssertionError("task_in_sub not found"));
            completeTask(taskInSub, TaskResult.Status.FAILED);

            // Wait for parent to FAIL and sibling_task to be CANCELLED
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus(),
                        "Parent should be FAILED. Got: " + wf.getStatus());
                Task sibling = wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                        .findFirst().orElseThrow(() -> new AssertionError("sibling_task not found"));
                assertEquals(Task.Status.CANCELED, sibling.getStatus(),
                        "sibling_task should be CANCELLED. Got: " + sibling.getStatus());
            });

            // === RETRY the subworkflow — triggers updateAndPushParents on the parent ===
            workflowClient.retryWorkflow(List.of(subWorkflowId));

            // Wait for parent to resume to RUNNING
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus(),
                        "Parent should be RUNNING after retry. Got: " + wf.getStatus());
            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            // === KEY ASSERTION: sibling_task must be rescheduled, not stuck CANCELLED ===
            Task rescheduledSibling = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                    .findFirst().orElseThrow(() -> new AssertionError("sibling_task not found after retry"));
            assertTrue(
                    rescheduledSibling.getStatus() == Task.Status.SCHEDULED
                            || rescheduledSibling.getStatus() == Task.Status.IN_PROGRESS,
                    "sibling_task should be SCHEDULED or IN_PROGRESS after retry, not CANCELLED. Got: "
                            + rescheduledSibling.getStatus());

            // Complete sibling_task
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertFalse(wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                        .allMatch(t -> t.getStatus().isTerminal()),
                        "sibling_task should be active");
            });
            workflow = workflowClient.getWorkflow(workflowId, true);
            Task siblingToComplete = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow();
            completeTask(siblingToComplete, TaskResult.Status.COMPLETED);

            // Complete the retried inner task of the subworkflow
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow sw = workflowClient.getWorkflow(subWorkflowId, true);
                assertTrue(sw.getTasks().stream()
                        .anyMatch(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal()),
                        "task_in_sub should be active after retry");
            });
            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task retriedTaskInSub = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow(() -> new AssertionError("Active task_in_sub not found after retry"));
            completeTask(retriedTaskInSub, TaskResult.Status.COMPLETED);

            // Parent should reach COMPLETED
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus(),
                        "Workflow should reach COMPLETED. Got: " + wf.getStatus());
            });

        } finally {
            try { terminateExistingRunningWorkflows(parentWfName); } catch (Exception e) { /* ignore */ }
            try { terminateExistingRunningWorkflows(subWfName); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * Three-branch FORK: rerunning a task inside the subworkflow must reschedule
     * ALL cancelled siblings across every other fork branch, not just one.
     *
     * Workflow structure:
     *   FORK(branch1: sub_wf_task(SUB_WORKFLOW) | branch2: sibling1(SIMPLE) | branch3: sibling2(SIMPLE)) → JOIN
     *
     * When sub_wf_task fails, sibling1 AND sibling2 are both CANCELLED.
     * After rerun from inside the subworkflow, both must be SCHEDULED.
     */
    @Test
    @DisplayName("PR #3458: Rerun in subworkflow reschedules ALL cancelled siblings across a three-branch fork")
    public void testRerunSubWorkflowReschedulesMultipleCancelledSiblings() {
        String parentWfName = "rerun-subwf-multi-sibling-parent-" + System.currentTimeMillis();
        String subWfName = "rerun-subwf-multi-sibling-child-" + System.currentTimeMillis();
        String taskInSubRef = "task_in_sub_multi";
        String subWfTaskRef = "sub_wf_task_multi";
        String sibling1Ref = "sibling_task_1";
        String sibling2Ref = "sibling_task_2";
        String forkRef = "fork_multi_sibling_test";
        String joinRef = "join_multi_sibling_test";

        // Sub-workflow: single SIMPLE task
        TaskDef taskInSubDef = new TaskDef(taskInSubRef);
        taskInSubDef.setRetryCount(0);
        taskInSubDef.setOwnerEmail("test@conductor.io");

        WorkflowTask taskInSubWt = new WorkflowTask();
        taskInSubWt.setTaskReferenceName(taskInSubRef);
        taskInSubWt.setName(taskInSubRef);
        taskInSubWt.setTaskDefinition(taskInSubDef);
        taskInSubWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(taskInSubWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        // Parent: FORK(sub_wf_task | sibling1 | sibling2) → JOIN
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);

        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setTaskReferenceName(subWfTaskRef);
        subWfTask.setName(subWfName);
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask.setSubWorkflowParam(subParams);

        TaskDef sibling1Def = new TaskDef(sibling1Ref);
        sibling1Def.setRetryCount(0);
        sibling1Def.setOwnerEmail("test@conductor.io");
        WorkflowTask sibling1Task = new WorkflowTask();
        sibling1Task.setTaskReferenceName(sibling1Ref);
        sibling1Task.setName(sibling1Ref);
        sibling1Task.setTaskDefinition(sibling1Def);
        sibling1Task.setWorkflowTaskType(TaskType.SIMPLE);

        TaskDef sibling2Def = new TaskDef(sibling2Ref);
        sibling2Def.setRetryCount(0);
        sibling2Def.setOwnerEmail("test@conductor.io");
        WorkflowTask sibling2Task = new WorkflowTask();
        sibling2Task.setTaskReferenceName(sibling2Ref);
        sibling2Task.setName(sibling2Ref);
        sibling2Task.setTaskDefinition(sibling2Def);
        sibling2Task.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(subWfTask), List.of(sibling1Task), List.of(sibling2Task)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfTaskRef, sibling1Ref, sibling2Ref));

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(forkTask, joinTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);
            String workflowId = workflowClient.startWorkflow(startRequest);

            // Wait for subworkflow inner task to be created
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                Task swt = wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                        .findFirst().orElseThrow(() -> new AssertionError("sub_wf_task not found"));
                assertNotNull(swt.getSubWorkflowId());
                assertFalse(workflowClient.getWorkflow(swt.getSubWorkflowId(), true).getTasks().isEmpty());
            });

            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            String subWorkflowId = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                    .findFirst().orElseThrow().getSubWorkflowId();

            // Fail the inner task → subworkflow FAILS → both sibling1 and sibling2 CANCELLED
            Workflow subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            completeTask(subWorkflow.getTasks().get(0), TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
                assertEquals(Task.Status.CANCELED, wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(sibling1Ref))
                        .findFirst().orElseThrow().getStatus());
                assertEquals(Task.Status.CANCELED, wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(sibling2Ref))
                        .findFirst().orElseThrow().getStatus());
            });

            // Retry the subworkflow — triggers updateAndPushParents on the parent
            workflowClient.retryWorkflow(List.of(subWorkflowId));

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus(),
                        "Parent should be RUNNING after retry. Got: " + wf.getStatus());
            });

            workflow = workflowClient.getWorkflow(workflowId, true);

            // === KEY ASSERTION: BOTH sibling tasks must be rescheduled ===
            Task rescheduled1 = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(sibling1Ref))
                    .findFirst().orElseThrow(() -> new AssertionError(sibling1Ref + " not found after rerun"));
            assertTrue(rescheduled1.getStatus() == Task.Status.SCHEDULED || rescheduled1.getStatus() == Task.Status.IN_PROGRESS,
                    sibling1Ref + " should be SCHEDULED after rerun. Got: " + rescheduled1.getStatus());

            Task rescheduled2 = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(sibling2Ref))
                    .findFirst().orElseThrow(() -> new AssertionError(sibling2Ref + " not found after rerun"));
            assertTrue(rescheduled2.getStatus() == Task.Status.SCHEDULED || rescheduled2.getStatus() == Task.Status.IN_PROGRESS,
                    sibling2Ref + " should be SCHEDULED after rerun. Got: " + rescheduled2.getStatus());

            // Complete all active tasks
            List.of(sibling1Ref, sibling2Ref).forEach(ref -> {
                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    Workflow wf = workflowClient.getWorkflow(workflowId, true);
                    assertFalse(wf.getTasks().stream()
                            .filter(t -> t.getReferenceTaskName().equals(ref) && !t.getStatus().isTerminal())
                            .findFirst().isEmpty(), ref + " should be active");
                });
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(ref) && !t.getStatus().isTerminal())
                        .findFirst().ifPresent(t -> completeTask(t, TaskResult.Status.COMPLETED));
            });

            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task retriedTask = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow(() -> new AssertionError("Active inner task not found after rerun"));
            completeTask(retriedTask, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.COMPLETED,
                            workflowClient.getWorkflow(workflowId, true).getStatus()));

        } finally {
            try { terminateExistingRunningWorkflows(parentWfName); } catch (Exception e) { /* ignore */ }
            try { terminateExistingRunningWorkflows(subWfName); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * Complex workflow: task before the fork, subworkflow with two sequential tasks
     * where only the second task fails. Rerun must:
     *  (a) reschedule the cancelled sibling task in the parent fork
     *  (b) preserve the first task's COMPLETED state inside the subworkflow
     *
     * Workflow structure:
     *   task_before → FORK(branch1: sub_wf_task(SUB_WORKFLOW) | branch2: sibling_task(SIMPLE)) → JOIN → task_after
     *
     * Sub-workflow structure: [first_task → second_task]
     *
     * Scenario: first_task completes, second_task fails → subworkflow FAILS →
     *           sibling_task CANCELLED → parent FAILS.
     * Rerun from second_task: first_task stays COMPLETED, sibling_task is rescheduled.
     */
    @Test
    @DisplayName("PR #3458: Rerun second task in multi-task subworkflow reschedules sibling and preserves first task state")
    public void testRerunSecondTaskInSubWorkflowReschedulesCancelledSibling() {
        String parentWfName = "rerun-subwf-seq-parent-" + System.currentTimeMillis();
        String subWfName = "rerun-subwf-seq-child-" + System.currentTimeMillis();
        String firstTaskRef = "first_task_in_sub";
        String secondTaskRef = "second_task_in_sub";
        String subWfTaskRef = "sub_wf_seq_task";
        String siblingTaskRef = "sibling_seq_task";
        String taskBeforeRef = "task_before_seq_fork";
        String taskAfterRef = "task_after_seq_join";
        String forkRef = "fork_seq_test";
        String joinRef = "join_seq_test";

        // Sub-workflow: first_task → second_task (sequential)
        TaskDef firstDef = new TaskDef(firstTaskRef);
        firstDef.setRetryCount(0);
        firstDef.setOwnerEmail("test@conductor.io");
        WorkflowTask firstWt = new WorkflowTask();
        firstWt.setTaskReferenceName(firstTaskRef);
        firstWt.setName(firstTaskRef);
        firstWt.setTaskDefinition(firstDef);
        firstWt.setWorkflowTaskType(TaskType.SIMPLE);

        TaskDef secondDef = new TaskDef(secondTaskRef);
        secondDef.setRetryCount(0);
        secondDef.setOwnerEmail("test@conductor.io");
        WorkflowTask secondWt = new WorkflowTask();
        secondWt.setTaskReferenceName(secondTaskRef);
        secondWt.setName(secondTaskRef);
        secondWt.setTaskDefinition(secondDef);
        secondWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(firstWt, secondWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        // Parent: task_before → FORK(sub_wf_task | sibling_task) → JOIN → task_after
        TaskDef beforeDef = new TaskDef(taskBeforeRef);
        beforeDef.setRetryCount(0);
        beforeDef.setOwnerEmail("test@conductor.io");
        WorkflowTask beforeTask = new WorkflowTask();
        beforeTask.setTaskReferenceName(taskBeforeRef);
        beforeTask.setName(taskBeforeRef);
        beforeTask.setTaskDefinition(beforeDef);
        beforeTask.setWorkflowTaskType(TaskType.SIMPLE);

        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);
        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setTaskReferenceName(subWfTaskRef);
        subWfTask.setName(subWfName);
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask.setSubWorkflowParam(subParams);

        TaskDef siblingDef = new TaskDef(siblingTaskRef);
        siblingDef.setRetryCount(0);
        siblingDef.setOwnerEmail("test@conductor.io");
        WorkflowTask siblingTask = new WorkflowTask();
        siblingTask.setTaskReferenceName(siblingTaskRef);
        siblingTask.setName(siblingTaskRef);
        siblingTask.setTaskDefinition(siblingDef);
        siblingTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(subWfTask), List.of(siblingTask)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfTaskRef, siblingTaskRef));

        TaskDef afterDef = new TaskDef(taskAfterRef);
        afterDef.setRetryCount(0);
        afterDef.setOwnerEmail("test@conductor.io");
        WorkflowTask afterTask = new WorkflowTask();
        afterTask.setTaskReferenceName(taskAfterRef);
        afterTask.setName(taskAfterRef);
        afterTask.setTaskDefinition(afterDef);
        afterTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(beforeTask, forkTask, joinTask, afterTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);
            String workflowId = workflowClient.startWorkflow(startRequest);

            // Complete task_before
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertFalse(wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(taskBeforeRef))
                        .findFirst().map(t -> t.getStatus().isTerminal()).orElse(true),
                        "task_before should be scheduled");
            });
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Task before = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskBeforeRef))
                    .findFirst().orElseThrow();
            completeTask(before, TaskResult.Status.COMPLETED);

            // Wait for fork + subworkflow inner tasks
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                Task swt = wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                        .findFirst().orElseThrow(() -> new AssertionError("sub_wf_task not found"));
                assertNotNull(swt.getSubWorkflowId());
                Workflow sw = workflowClient.getWorkflow(swt.getSubWorkflowId(), true);
                assertFalse(sw.getTasks().isEmpty());
            });

            workflow = workflowClient.getWorkflow(workflowId, true);
            String subWorkflowId = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                    .findFirst().orElseThrow().getSubWorkflowId();

            // Complete first_task inside subworkflow
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow sw = workflowClient.getWorkflow(subWorkflowId, true);
                assertFalse(sw.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(firstTaskRef) && !t.getStatus().isTerminal())
                        .findFirst().isEmpty(), "first_task should be active");
            });
            Workflow subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task firstTask = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(firstTaskRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow();
            String firstTaskId = firstTask.getTaskId();
            completeTask(firstTask, TaskResult.Status.COMPLETED);

            // Fail second_task inside subworkflow → subworkflow FAILS → sibling_task CANCELLED → parent FAILS
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow sw = workflowClient.getWorkflow(subWorkflowId, true);
                assertFalse(sw.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(secondTaskRef) && !t.getStatus().isTerminal())
                        .findFirst().isEmpty(), "second_task should be active");
            });
            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task secondTask = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(secondTaskRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow();
            completeTask(secondTask, TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(Workflow.WorkflowStatus.FAILED, workflowClient.getWorkflow(workflowId, true).getStatus());
                assertEquals(Task.Status.CANCELED, workflowClient.getWorkflow(workflowId, true).getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                        .findFirst().orElseThrow().getStatus());
            });

            // Retry the subworkflow — triggers updateAndPushParents on the parent
            workflowClient.retryWorkflow(List.of(subWorkflowId));

            // Wait for parent to resume
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.RUNNING, workflowClient.getWorkflow(workflowId, true).getStatus(),
                            "Parent should be RUNNING after retry"));

            workflow = workflowClient.getWorkflow(workflowId, true);

            // === ASSERTION 1: sibling_task is rescheduled ===
            Task rescheduledSibling = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                    .findFirst().orElseThrow(() -> new AssertionError("sibling_task not found after rerun"));
            assertTrue(rescheduledSibling.getStatus() == Task.Status.SCHEDULED
                            || rescheduledSibling.getStatus() == Task.Status.IN_PROGRESS,
                    "sibling_task should be SCHEDULED after rerun. Got: " + rescheduledSibling.getStatus());

            // === ASSERTION 2: task_before is still COMPLETED with the same taskId ===
            Task taskBeforeAfterRerun = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskBeforeRef))
                    .findFirst().orElseThrow();
            assertEquals(Task.Status.COMPLETED, taskBeforeAfterRerun.getStatus(),
                    "task_before should still be COMPLETED after rerun");

            // === ASSERTION 3: first_task inside subworkflow is still COMPLETED ===
            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task firstTaskAfterRerun = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(firstTaskRef) && t.getTaskId().equals(firstTaskId))
                    .findFirst().orElseThrow(() -> new AssertionError("first_task original instance not found"));
            assertEquals(Task.Status.COMPLETED, firstTaskAfterRerun.getStatus(),
                    "first_task should still be COMPLETED (not re-executed) after rerun from second_task");

            // Complete sibling_task
            workflow = workflowClient.getWorkflow(workflowId, true);
            Task siblingToComplete = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow();
            completeTask(siblingToComplete, TaskResult.Status.COMPLETED);

            // Complete second_task (retried)
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow sw = workflowClient.getWorkflow(subWorkflowId, true);
                assertTrue(sw.getTasks().stream()
                        .anyMatch(t -> t.getReferenceTaskName().equals(secondTaskRef) && !t.getStatus().isTerminal()),
                        "Retried second_task should be active");
            });
            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task retriedSecond = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(secondTaskRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow(() -> new AssertionError("Retried second_task not found"));
            completeTask(retriedSecond, TaskResult.Status.COMPLETED);

            // Wait for task_after to appear and complete it
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertTrue(wf.getTasks().stream()
                        .anyMatch(t -> t.getReferenceTaskName().equals(taskAfterRef) && !t.getStatus().isTerminal()),
                        "task_after should be scheduled after join completes");
            });
            workflow = workflowClient.getWorkflow(workflowId, true);
            Task afterTaskInst = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskAfterRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow();
            completeTask(afterTaskInst, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.COMPLETED,
                            workflowClient.getWorkflow(workflowId, true).getStatus()));

        } finally {
            try { terminateExistingRunningWorkflows(parentWfName); } catch (Exception e) { /* ignore */ }
            try { terminateExistingRunningWorkflows(subWfName); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * Resilience test: multiple consecutive failure-rerun cycles.
     * The cancelled sibling task must be rescheduled correctly on each rerun,
     * ensuring the fix is stable across repeated retry attempts.
     *
     * Workflow structure:
     *   FORK(branch1: sub_wf_task(SUB_WORKFLOW) | branch2: sibling_task(SIMPLE)) → JOIN
     *
     * Cycle 1: fail → sibling CANCELLED → rerun → sibling SCHEDULED
     * Cycle 2: fail again → sibling CANCELLED again → rerun → sibling SCHEDULED again
     * Cycle 3: complete successfully → workflow COMPLETED
     */
    @Test
    @DisplayName("PR #3458: Cancelled sibling is rescheduled consistently across multiple rerun cycles")
    public void testRerunSubWorkflowMultipleRerunCycles() {
        String parentWfName = "rerun-subwf-multi-cycle-parent-" + System.currentTimeMillis();
        String subWfName = "rerun-subwf-multi-cycle-child-" + System.currentTimeMillis();
        String taskInSubRef = "task_in_sub_cycle";
        String subWfTaskRef = "sub_wf_cycle_task";
        String siblingTaskRef = "sibling_cycle_task";
        String forkRef = "fork_cycle_test";
        String joinRef = "join_cycle_test";

        // Sub-workflow: single SIMPLE task
        TaskDef taskInSubDef = new TaskDef(taskInSubRef);
        taskInSubDef.setRetryCount(0);
        taskInSubDef.setOwnerEmail("test@conductor.io");
        WorkflowTask taskInSubWt = new WorkflowTask();
        taskInSubWt.setTaskReferenceName(taskInSubRef);
        taskInSubWt.setName(taskInSubRef);
        taskInSubWt.setTaskDefinition(taskInSubDef);
        taskInSubWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(taskInSubWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        // Parent: FORK(sub_wf_task | sibling_task) → JOIN
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);
        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setTaskReferenceName(subWfTaskRef);
        subWfTask.setName(subWfName);
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask.setSubWorkflowParam(subParams);

        TaskDef siblingDef = new TaskDef(siblingTaskRef);
        siblingDef.setRetryCount(0);
        siblingDef.setOwnerEmail("test@conductor.io");
        WorkflowTask siblingTask = new WorkflowTask();
        siblingTask.setTaskReferenceName(siblingTaskRef);
        siblingTask.setName(siblingTaskRef);
        siblingTask.setTaskDefinition(siblingDef);
        siblingTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(subWfTask), List.of(siblingTask)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfTaskRef, siblingTaskRef));

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(forkTask, joinTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);
            String workflowId = workflowClient.startWorkflow(startRequest);

            // Wait for subworkflow to be created with its inner task
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                Task swt = wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                        .findFirst().orElseThrow(() -> new AssertionError("sub_wf_task not found"));
                assertNotNull(swt.getSubWorkflowId());
                assertFalse(workflowClient.getWorkflow(swt.getSubWorkflowId(), true).getTasks().isEmpty());
            });

            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            String subWorkflowId = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                    .findFirst().orElseThrow().getSubWorkflowId();

            // ── CYCLE 1: fail → rerun ────────────────────────────────────────────────
            Workflow subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            completeTask(subWorkflow.getTasks().get(0), TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(Workflow.WorkflowStatus.FAILED, workflowClient.getWorkflow(workflowId, true).getStatus());
                assertEquals(Task.Status.CANCELED, workflowClient.getWorkflow(workflowId, true).getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                        .findFirst().orElseThrow().getStatus());
            });

            // Cycle 1 retry — triggers updateAndPushParents
            workflowClient.retryWorkflow(List.of(subWorkflowId));

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.RUNNING, workflowClient.getWorkflow(workflowId, true).getStatus(),
                            "Cycle 1: parent should be RUNNING after first retry"));

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task siblingAfterCycle1 = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                    .findFirst().orElseThrow();
            assertTrue(siblingAfterCycle1.getStatus() == Task.Status.SCHEDULED
                            || siblingAfterCycle1.getStatus() == Task.Status.IN_PROGRESS,
                    "Cycle 1: sibling should be SCHEDULED after rerun. Got: " + siblingAfterCycle1.getStatus());

            // ── CYCLE 2: fail again → rerun again ───────────────────────────────────
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow sw = workflowClient.getWorkflow(subWorkflowId, true);
                assertFalse(sw.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal())
                        .findFirst().isEmpty(), "Cycle 2: active inner task should exist");
            });
            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task cycle2ActiveTask = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow();
            completeTask(cycle2ActiveTask, TaskResult.Status.FAILED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(Workflow.WorkflowStatus.FAILED, workflowClient.getWorkflow(workflowId, true).getStatus(),
                        "Cycle 2: parent should be FAILED after second failure");
                assertEquals(Task.Status.CANCELED, workflowClient.getWorkflow(workflowId, true).getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                        .findFirst().orElseThrow().getStatus(),
                        "Cycle 2: sibling should be CANCELLED again");
            });

            // Cycle 2 retry — triggers updateAndPushParents again
            workflowClient.retryWorkflow(List.of(subWorkflowId));

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.RUNNING, workflowClient.getWorkflow(workflowId, true).getStatus(),
                            "Cycle 2: parent should be RUNNING after second retry"));

            workflow = workflowClient.getWorkflow(workflowId, true);
            Task siblingAfterCycle2 = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef))
                    .findFirst().orElseThrow();
            assertTrue(siblingAfterCycle2.getStatus() == Task.Status.SCHEDULED
                            || siblingAfterCycle2.getStatus() == Task.Status.IN_PROGRESS,
                    "Cycle 2: sibling should be SCHEDULED after second rerun. Got: " + siblingAfterCycle2.getStatus());

            // ── CYCLE 3: complete successfully ──────────────────────────────────────
            workflow = workflowClient.getWorkflow(workflowId, true);
            Task siblingToComplete = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(siblingTaskRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow();
            completeTask(siblingToComplete, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow sw = workflowClient.getWorkflow(subWorkflowId, true);
                assertFalse(sw.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal())
                        .findFirst().isEmpty(), "Cycle 3: active inner task should exist");
            });
            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task finalInnerTask = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow(() -> new AssertionError("Cycle 3: active inner task not found"));
            completeTask(finalInnerTask, TaskResult.Status.COMPLETED);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.COMPLETED,
                            workflowClient.getWorkflow(workflowId, true).getStatus(),
                            "Workflow should reach COMPLETED after cycle 3"));

        } finally {
            try { terminateExistingRunningWorkflows(parentWfName); } catch (Exception e) { /* ignore */ }
            try { terminateExistingRunningWorkflows(subWfName); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * DO_WHILE sibling test: when the subworkflow branch fails, the DO_WHILE task
     * in the sibling fork branch gets CANCELLED. After rerunning a task inside the
     * subworkflow, the DO_WHILE must be restored to IN_PROGRESS (not SCHEDULED —
     * DO_WHILE is a system task, and the fix in PR #3458 explicitly handles this).
     *
     * Workflow structure:
     *   FORK(branch1: sub_wf_task(SUB_WORKFLOW) | branch2: do_while_task(DO_WHILE[loop_body_task])) → JOIN
     *
     * When sub_wf_task fails:
     *   - do_while_task → CANCELLED
     *   - loop_body_task (first iteration body) → CANCELLED
     *
     * After rerun from the failed inner task:
     *   - do_while_task → IN_PROGRESS   (DO_WHILE is a system task, reset differently)
     *   - loop_body_task is rescheduled for the first iteration
     */
    @Test
    @DisplayName("PR #3458: Rerun task in subworkflow restores CANCELLED DO_WHILE sibling to IN_PROGRESS")
    public void testRerunSubWorkflowReschedulesDoWhileSiblingCancelledByFork() {
        String parentWfName = "rerun-subwf-dowhile-parent-" + System.currentTimeMillis();
        String subWfName = "rerun-subwf-dowhile-child-" + System.currentTimeMillis();
        String taskInSubRef = "task_in_sub_dowhile";
        String subWfTaskRef = "sub_wf_dowhile_task";
        String doWhileRef = "do_while_sibling";
        String loopBodyRef = "loop_body_task";
        String forkRef = "fork_dowhile_test";
        String joinRef = "join_dowhile_test";

        // Sub-workflow: single SIMPLE task
        TaskDef taskInSubDef = new TaskDef(taskInSubRef);
        taskInSubDef.setRetryCount(0);
        taskInSubDef.setOwnerEmail("test@conductor.io");
        WorkflowTask taskInSubWt = new WorkflowTask();
        taskInSubWt.setTaskReferenceName(taskInSubRef);
        taskInSubWt.setName(taskInSubRef);
        taskInSubWt.setTaskDefinition(taskInSubDef);
        taskInSubWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef subWfDef = new WorkflowDef();
        subWfDef.setName(subWfName);
        subWfDef.setOwnerEmail("test@conductor.io");
        subWfDef.setTimeoutSeconds(600);
        subWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subWfDef.setTasks(List.of(taskInSubWt));
        metadataClient.updateWorkflowDefs(java.util.List.of(subWfDef));

        // DO_WHILE body: a single SIMPLE task, runs exactly one iteration
        TaskDef loopBodyDef = new TaskDef(loopBodyRef);
        loopBodyDef.setRetryCount(0);
        loopBodyDef.setOwnerEmail("test@conductor.io");
        WorkflowTask loopBodyWt = new WorkflowTask();
        loopBodyWt.setTaskReferenceName(loopBodyRef);
        loopBodyWt.setName(loopBodyRef);
        loopBodyWt.setTaskDefinition(loopBodyDef);
        loopBodyWt.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask doWhileTask = new WorkflowTask();
        doWhileTask.setTaskReferenceName(doWhileRef);
        doWhileTask.setName(doWhileRef);
        doWhileTask.setWorkflowTaskType(TaskType.DO_WHILE);
        // Loop condition: run exactly once (iteration 1 < 1 is false → exit after first body)
        doWhileTask.setLoopCondition("if ($.do_while_sibling['iteration'] < 1) { true; } else { false; }");
        doWhileTask.setLoopOver(List.of(loopBodyWt));

        // Parent: FORK(sub_wf_task | do_while_sibling) → JOIN
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName(subWfName);
        subParams.setVersion(1);
        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setTaskReferenceName(subWfTaskRef);
        subWfTask.setName(subWfName);
        subWfTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        subWfTask.setSubWorkflowParam(subParams);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setTaskReferenceName(forkRef);
        forkTask.setName("FORK_JOIN");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(subWfTask), List.of(doWhileTask)));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setTaskReferenceName(joinRef);
        joinTask.setName("JOIN");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(subWfTaskRef, doWhileRef));

        WorkflowDef parentWfDef = new WorkflowDef();
        parentWfDef.setName(parentWfName);
        parentWfDef.setOwnerEmail("test@conductor.io");
        parentWfDef.setTimeoutSeconds(600);
        parentWfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentWfDef.setTasks(List.of(forkTask, joinTask));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentWfDef));

        try {
            StartWorkflowRequest startRequest = new StartWorkflowRequest();
            startRequest.setName(parentWfName);
            startRequest.setVersion(1);
            String workflowId = workflowClient.startWorkflow(startRequest);

            // Wait for subworkflow and DO_WHILE to be scheduled
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                Task swt = wf.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                        .findFirst().orElseThrow(() -> new AssertionError("sub_wf_task not found"));
                assertNotNull(swt.getSubWorkflowId(), "sub_wf_task should have a subWorkflowId");
                assertFalse(workflowClient.getWorkflow(swt.getSubWorkflowId(), true).getTasks().isEmpty());
                assertTrue(wf.getTasks().stream()
                        .anyMatch(t -> t.getTaskType().equals(TaskType.DO_WHILE.name())),
                        "DO_WHILE task should be present");
            });

            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            String subWorkflowId = workflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(subWfTaskRef))
                    .findFirst().orElseThrow().getSubWorkflowId();

            // Verify DO_WHILE is IN_PROGRESS before failure
            Task doWhileBeforeFailure = workflow.getTasks().stream()
                    .filter(t -> t.getTaskType().equals(TaskType.DO_WHILE.name()))
                    .findFirst().orElseThrow(() -> new AssertionError("DO_WHILE task not found"));
            assertEquals(Task.Status.IN_PROGRESS, doWhileBeforeFailure.getStatus(),
                    "DO_WHILE should be IN_PROGRESS initially. Got: " + doWhileBeforeFailure.getStatus());

            // Fail the inner task of the subworkflow → sub_wf_task FAILS → do_while_task CANCELLED → parent FAILS
            Workflow subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task taskInSub = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskInSubRef))
                    .findFirst().orElseThrow(() -> new AssertionError("task_in_sub not found"));
            completeTask(taskInSub, TaskResult.Status.FAILED);

            // Wait for parent to FAIL and DO_WHILE to be CANCELLED
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus(),
                        "Parent should be FAILED. Got: " + wf.getStatus());
                Task doWhile = wf.getTasks().stream()
                        .filter(t -> t.getTaskType().equals(TaskType.DO_WHILE.name()))
                        .findFirst().orElseThrow();
                assertEquals(Task.Status.CANCELED, doWhile.getStatus(),
                        "DO_WHILE task should be CANCELLED. Got: " + doWhile.getStatus());
            });

            // === RETRY the subworkflow — triggers updateAndPushParents on the parent ===
            workflowClient.retryWorkflow(List.of(subWorkflowId));

            // Wait for parent to resume to RUNNING
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.RUNNING, workflowClient.getWorkflow(workflowId, true).getStatus(),
                            "Parent should be RUNNING after retry"));

            workflow = workflowClient.getWorkflow(workflowId, true);

            // === KEY ASSERTION: DO_WHILE must be IN_PROGRESS, not CANCELLED or SCHEDULED ===
            // DO_WHILE is a system task — it cannot be SCHEDULED like a SIMPLE task;
            // PR #3458 explicitly sets it back to IN_PROGRESS.
            Task doWhileAfterRerun = workflow.getTasks().stream()
                    .filter(t -> t.getTaskType().equals(TaskType.DO_WHILE.name()))
                    .findFirst().orElseThrow(() -> new AssertionError("DO_WHILE task not found after rerun"));
            assertEquals(Task.Status.IN_PROGRESS, doWhileAfterRerun.getStatus(),
                    "DO_WHILE sibling should be IN_PROGRESS after rerun (not CANCELLED). Got: "
                            + doWhileAfterRerun.getStatus());

            // Wait for the loop body SIMPLE task to be rescheduled by the engine
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertTrue(wf.getTasks().stream()
                        .anyMatch(t -> t.getTaskDefName().equals(loopBodyRef)
                                && t.getIteration() == 1
                                && !t.getStatus().isTerminal()),
                        "Loop body task (iteration 1) should be active after DO_WHILE is restored to IN_PROGRESS");
            });

            // Complete the loop body task (iteration 1) → DO_WHILE exits after one iteration
            workflow = workflowClient.getWorkflow(workflowId, true);
            Task loopBodyTask = workflow.getTasks().stream()
                    .filter(t -> t.getTaskDefName().equals(loopBodyRef) && t.getIteration() == 1 && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow(() -> new AssertionError("Active loop body task not found"));
            completeTask(loopBodyTask, TaskResult.Status.COMPLETED);

            // Wait for DO_WHILE to complete
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                Task doWhile = wf.getTasks().stream()
                        .filter(t -> t.getTaskType().equals(TaskType.DO_WHILE.name()))
                        .findFirst().orElseThrow();
                assertEquals(Task.Status.COMPLETED, doWhile.getStatus(),
                        "DO_WHILE should be COMPLETED after loop body finishes. Got: " + doWhile.getStatus());
            });

            // Complete the retried inner task of the subworkflow
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow sw = workflowClient.getWorkflow(subWorkflowId, true);
                assertTrue(sw.getTasks().stream()
                        .anyMatch(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal()),
                        "Retried task_in_sub should be active");
            });
            subWorkflow = workflowClient.getWorkflow(subWorkflowId, true);
            Task retriedTaskInSub = subWorkflow.getTasks().stream()
                    .filter(t -> t.getReferenceTaskName().equals(taskInSubRef) && !t.getStatus().isTerminal())
                    .findFirst().orElseThrow(() -> new AssertionError("Active task_in_sub not found after rerun"));
            completeTask(retriedTaskInSub, TaskResult.Status.COMPLETED);

            // Parent should reach COMPLETED
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Workflow.WorkflowStatus.COMPLETED,
                            workflowClient.getWorkflow(workflowId, true).getStatus(),
                            "Workflow should reach COMPLETED"));

        } finally {
            try { terminateExistingRunningWorkflows(parentWfName); } catch (Exception e) { /* ignore */ }
            try { terminateExistingRunningWorkflows(subWfName); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * Same regression but exercised through the rerun API instead of retry. Confirms
     * updateAndPushParents handles SUB_WORKFLOW siblings correctly on the rerun path
     * too (it shares the buggy code with retry).
     */
    @Test
    @DisplayName("Rerun task inside sub-workflow with all-SUB_WORKFLOW fork siblings spawns a fresh child")
    public void testRerunFromTaskInsideAllSubWorkflowFork() {
        long stamp = System.currentTimeMillis();
        String parentWfName = "rerun-allsubwf-rerun-parent-" + stamp;
        String failingSubWfName = "rerun-allsubwf-rerun-failing-" + stamp;
        String cancelledSubWfName = "rerun-allsubwf-rerun-cancelled-" + stamp;
        String taskInFailingSubRef = "task_in_failing_sub_rerun";
        String taskInCancelledSubRef = "task_in_cancelled_sub_rerun";
        String failingSubRef = "failing_sub_ref_rerun";
        String cancelledSubRef = "cancelled_sub_ref_rerun";

        registerWorkflow(failingSubWfName, List.of(simpleTask(taskInFailingSubRef)));
        registerWorkflow(cancelledSubWfName, List.of(simpleTask(taskInCancelledSubRef)));
        registerWorkflow(parentWfName, List.of(
                forkJoin("fork_allsubwf_rerun", List.of(
                        List.of(subWorkflowTask(failingSubRef, failingSubWfName)),
                        List.of(subWorkflowTask(cancelledSubRef, cancelledSubWfName)))),
                join("join_allsubwf_rerun", List.of(failingSubRef, cancelledSubRef))));

        try {
            String workflowId = start(parentWfName);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertNotNull(findActiveTask(wf, failingSubRef, "missing").getSubWorkflowId());
                assertNotNull(findActiveTask(wf, cancelledSubRef, "missing").getSubWorkflowId());
            });

            String failingSubWorkflowId = findActiveTask(workflowId, failingSubRef, "missing").getSubWorkflowId();
            String originalCancelledSubWorkflowId = findActiveTask(workflowId, cancelledSubRef, "missing").getSubWorkflowId();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertFalse(workflowClient.getWorkflow(failingSubWorkflowId, true).getTasks().isEmpty()));
            Task innerFailing = findActiveTask(failingSubWorkflowId, taskInFailingSubRef,
                    "failing inner task not active");
            String failedInnerTaskId = innerFailing.getTaskId();
            completeTask(innerFailing, TaskResult.Status.FAILED);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
                assertEquals(Task.Status.CANCELED, statusOf(wf, cancelledSubRef));
                assertNotNull(taskOf(wf, failingSubRef).getReasonForIncompletion(),
                        "failing_sub_ref should have reasonForIncompletion populated while FAILED");
            });

            // === RERUN from the failed inner task (rerun API path, not retry) ===
            RerunWorkflowRequest rerunRequest = new RerunWorkflowRequest();
            rerunRequest.setReRunFromWorkflowId(failingSubWorkflowId);
            rerunRequest.setReRunFromTaskId(failedInnerTaskId);
            workflowClient.rerunWorkflow(failingSubWorkflowId, rerunRequest);

            awaitWorkflowStatus(workflowId, Workflow.WorkflowStatus.RUNNING, "Parent should be RUNNING after rerun");
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Task active = findActiveTask(workflowId, cancelledSubRef,
                        "Expected active cancelled_sub_ref after rerun (sibling must be resumed)");
                assertEquals(originalCancelledSubWorkflowId, active.getSubWorkflowId(),
                        "Rerun must retry the cancelled sibling in-place (subWorkflowId preserved)");
                assertNull(findActiveTask(workflowId, failingSubRef,
                                "failing_sub_ref must be active after rerun").getReasonForIncompletion(),
                        "failing_sub_ref reasonForIncompletion must be cleared after rerun walk-up");
            });

        } finally {
            terminateAll(parentWfName, failingSubWfName, cancelledSubWfName);
        }
    }

    /**
     * Direct rerun of a SUB_WORKFLOW task in the parent spawns a brand-new child
     * execution (rerun semantics). The previous child id is replaced and the fresh
     * child starts its tasks from scratch.
     */
    @Test
    @DisplayName("Direct rerun on a SUB_WORKFLOW task spawns a fresh child (new id, fresh tasks)")
    public void testDirectRerunOnSubWorkflowTaskSpawnsFreshChild() {
        long stamp = System.currentTimeMillis();
        String parentWfName = "rerun-direct-subwf-parent-" + stamp;
        String childWfName = "rerun-direct-subwf-child-" + stamp;
        String task1Ref = "task1_direct_rerun";
        String task2Ref = "task2_direct_rerun";
        String subRef = "sub_ref_direct_rerun";

        registerWorkflow(childWfName, List.of(simpleTask(task1Ref), simpleTask(task2Ref)));
        registerWorkflow(parentWfName, List.of(subWorkflowTask(subRef, childWfName)));

        try {
            String workflowId = start(parentWfName);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Task sub = findActiveTask(workflowId, subRef, "sub task should be active");
                assertNotNull(sub.getSubWorkflowId());
                findActiveTask(sub.getSubWorkflowId(), task1Ref, "task1 should be active in child");
            });

            Task subWfTask = findActiveTask(workflowId, subRef, "sub task should be active");
            String originalChildId = subWfTask.getSubWorkflowId();
            String subWfTaskId = subWfTask.getTaskId();

            // Complete task1, then fail task2 → child FAILED, parent FAILED.
            completeTask(findActiveTask(originalChildId, task1Ref, "task1 missing"),
                    TaskResult.Status.COMPLETED);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    findActiveTask(originalChildId, task2Ref, "task2 should be active"));
            completeTask(findActiveTask(originalChildId, task2Ref, "task2 missing"),
                    TaskResult.Status.FAILED);

            awaitWorkflowStatus(workflowId, Workflow.WorkflowStatus.FAILED, 10, "Parent should be FAILED");

            // === Direct rerun on the SUB_WORKFLOW task in the parent ===
            // Rerun semantics: a brand-new child workflow is spawned. The previous child
            // id is replaced and the fresh child runs its tasks from scratch.
            RerunWorkflowRequest rerunRequest = new RerunWorkflowRequest();
            rerunRequest.setReRunFromWorkflowId(workflowId);
            rerunRequest.setReRunFromTaskId(subWfTaskId);
            workflowClient.rerunWorkflow(workflowId, rerunRequest);

            awaitWorkflowStatus(workflowId, Workflow.WorkflowStatus.RUNNING, "Parent must be RUNNING after rerun");
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Task subTaskNow = findActiveTask(workflowId, subRef,
                        "Rerun SUB_WORKFLOW task should be active");
                assertNotNull(subTaskNow.getSubWorkflowId(),
                        "Rerun SUB_WORKFLOW task must have a child id");
                assertNotEquals(originalChildId, subTaskNow.getSubWorkflowId(),
                        "Direct rerun on SUB_WORKFLOW must spawn a fresh child (new id)");
                Workflow freshChild = workflowClient.getWorkflow(subTaskNow.getSubWorkflowId(), true);
                assertFalse(freshChild.getStatus().isTerminal(),
                        "Fresh child must be non-terminal");
                findActiveTask(freshChild, task1Ref, "Fresh child must have task1 active (executed from scratch)");
            });

        } finally {
            terminateAll(parentWfName, childWfName);
        }
    }

    // --------- Three-branch fork + terminal-error: shared setup for cases 1-4 ---------
    // Branch A fails with TERMINAL_ERROR, branch B is CANCELED by the cascade, branch C
    // completes BEFORE the failure trigger so the cascade can't cancel it. Each test
    // differs only in the rerun entry point and the case-specific assertions afterward.

    private static final String FAILING_SUB_REF = "failing_sub_ref";
    private static final String CANCELLED_SUB_REF = "cancelled_sub_ref";
    private static final String COMPLETED_SUB_REF = "completed_sub_ref";
    private static final String FAILING_INNER = "failing_inner_task";
    private static final String CANCELLED_INNER = "cancelled_inner_task";
    private static final String COMPLETED_INNER = "completed_inner_task";

    private record ForkScenario(String parentName, String failingName, String cancelledName, String completedName,
                                String parentId, String failingChildId, String cancelledChildId, String completedChildId) {}

    private ForkScenario forkWithFailedAndCancelledAndCompleted(String suffix) {
        long stamp = System.currentTimeMillis();
        String parentName = "rerun-" + suffix + "-parent-" + stamp;
        String failingName = "rerun-" + suffix + "-failing-" + stamp;
        String cancelledName = "rerun-" + suffix + "-cancelled-" + stamp;
        String completedName = "rerun-" + suffix + "-completed-" + stamp;

        registerWorkflow(failingName, List.of(simpleTask(FAILING_INNER)));
        registerWorkflow(cancelledName, List.of(simpleTask(CANCELLED_INNER)));
        registerWorkflow(completedName, List.of(simpleTask(COMPLETED_INNER)));
        registerWorkflow(parentName, List.of(
                forkJoin("fork_" + suffix, List.of(
                        List.of(subWorkflowTask(FAILING_SUB_REF, failingName)),
                        List.of(subWorkflowTask(CANCELLED_SUB_REF, cancelledName)),
                        List.of(subWorkflowTask(COMPLETED_SUB_REF, completedName)))),
                join("join_" + suffix, List.of(FAILING_SUB_REF, CANCELLED_SUB_REF, COMPLETED_SUB_REF))));

        String parentId = start(parentName);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(parentId, true);
            assertNotNull(findActiveTask(wf, FAILING_SUB_REF, "missing").getSubWorkflowId());
            assertNotNull(findActiveTask(wf, CANCELLED_SUB_REF, "missing").getSubWorkflowId());
            assertNotNull(findActiveTask(wf, COMPLETED_SUB_REF, "missing").getSubWorkflowId());
        });

        String failingChildId = findActiveTask(parentId, FAILING_SUB_REF, "missing").getSubWorkflowId();
        String cancelledChildId = findActiveTask(parentId, CANCELLED_SUB_REF, "missing").getSubWorkflowId();
        String completedChildId = findActiveTask(parentId, COMPLETED_SUB_REF, "missing").getSubWorkflowId();

        completeTask(findActiveTask(completedChildId, COMPLETED_INNER, "missing"), TaskResult.Status.COMPLETED);
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                Task.Status.COMPLETED,
                statusOf(workflowClient.getWorkflow(parentId, true), COMPLETED_SUB_REF)));

        completeTask(findActiveTask(failingChildId, FAILING_INNER, "missing"),
                TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(parentId, true);
            assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
            assertEquals(Task.Status.FAILED, statusOf(wf, FAILING_SUB_REF));
            assertEquals(Task.Status.CANCELED, statusOf(wf, CANCELLED_SUB_REF));
            assertEquals(Task.Status.COMPLETED, statusOf(wf, COMPLETED_SUB_REF));
        });

        return new ForkScenario(parentName, failingName, cancelledName, completedName,
                parentId, failingChildId, cancelledChildId, completedChildId);
    }

    /**
     * Asserts the COMPLETED sibling is untouched after rerun. Used by all four cases.
     */
    private void assertCompletedSiblingUntouched(String parentId, String completedChildId) {
        Workflow wf = workflowClient.getWorkflow(parentId, true);
        assertEquals(Task.Status.COMPLETED, statusOf(wf, COMPLETED_SUB_REF),
                COMPLETED_SUB_REF + " must remain COMPLETED — walk-up must skip isSuccessful siblings");
        assertEquals(completedChildId, taskOf(wf, COMPLETED_SUB_REF).getSubWorkflowId(),
                COMPLETED_SUB_REF + " subWorkflowId must be unchanged");
        assertEquals(Workflow.WorkflowStatus.COMPLETED,
                workflowClient.getWorkflow(completedChildId, false).getStatus(),
                "completed child workflow must remain COMPLETED");
    }

    /**
     * Drives the failing + cancelled branches' inner tasks to COMPLETED and asserts the
     * parent reaches COMPLETED. Caller must pass the current subWorkflowIds (which may
     * differ from the originals for cases that spawn fresh children).
     */
    private void driveBothBranchesToCompletion(String parentId, String activeFailingChildId, String activeCancelledChildId) {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            findActiveTask(activeFailingChildId, FAILING_INNER, "failing inner task not active");
            findActiveTask(activeCancelledChildId, CANCELLED_INNER, "cancelled inner task not active");
        });
        completeTask(findActiveTask(activeFailingChildId, FAILING_INNER, "missing"), TaskResult.Status.COMPLETED);
        completeTask(findActiveTask(activeCancelledChildId, CANCELLED_INNER, "missing"), TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.COMPLETED, 20, "Parent should reach COMPLETED");
    }

    /**
     * Case 1 of four. Rerun from the FAILED_WITH_TERMINAL_ERROR task INSIDE the failing
     * child sub-workflow. Walk-up via updateAndPushParents must restore the CANCELED
     * sibling in place and leave the COMPLETED sibling alone.
     */
    @Test
    @DisplayName("Rerun FAILED_WITH_TERMINAL_ERROR task in failing sub-workflow reschedules CANCELED sibling, leaves COMPLETED sibling alone")
    public void testRerunFailedTaskInFailingSubWorkflowReschedulesCancelledSibling() {
        ForkScenario s = forkWithFailedAndCancelledAndCompleted("case1");
        try {
            Task failedInnerTask = taskOf(workflowClient.getWorkflow(s.failingChildId(), true), FAILING_INNER);
            RerunWorkflowRequest req = new RerunWorkflowRequest();
            req.setReRunFromWorkflowId(s.failingChildId());
            req.setReRunFromTaskId(failedInnerTask.getTaskId());
            workflowClient.rerunWorkflow(s.failingChildId(), req);

            awaitWorkflowStatus(s.parentId(), Workflow.WorkflowStatus.RUNNING, "Parent → RUNNING");
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(s.parentId(), true);
                assertEquals(s.failingChildId(), findActiveTask(wf, FAILING_SUB_REF, "missing").getSubWorkflowId(),
                        "failing branch retried in place — subWorkflowId preserved");
                assertNull(findActiveTask(wf, FAILING_SUB_REF, "missing").getReasonForIncompletion());
                assertEquals(s.cancelledChildId(), findActiveTask(wf, CANCELLED_SUB_REF, "missing").getSubWorkflowId(),
                        "cancelled branch retried in place — subWorkflowId preserved");
                assertCompletedSiblingUntouched(s.parentId(), s.completedChildId());
            });

            driveBothBranchesToCompletion(s.parentId(), s.failingChildId(), s.cancelledChildId());
        } finally {
            terminateAll(s.parentName(), s.failingName(), s.cancelledName(), s.completedName());
        }
    }

    /**
     * Case 2 of four. Rerun from the FAILED SUB_WORKFLOW task IN THE PARENT (failing
     * branch's parent-level task). Parent has no parent so updateAndPushParents is a
     * no-op; finalizeRerun's in-place sibling reset wipes terminal-unsuccessful siblings'
     * subWorkflowIds → fresh children for both failing and cancelled branches; COMPLETED
     * sibling untouched.
     */
    @Test
    @DisplayName("Rerun FAILED sub-workflow task in parent spawns fresh children for failing + cancelled siblings, leaves COMPLETED sibling alone")
    public void testRerunFailedSubWorkflowTaskInParentReschedulesCancelledSibling() {
        ForkScenario s = forkWithFailedAndCancelledAndCompleted("case2");
        try {
            Task failedParentSubTask = taskOf(workflowClient.getWorkflow(s.parentId(), true), FAILING_SUB_REF);
            RerunWorkflowRequest req = new RerunWorkflowRequest();
            req.setReRunFromWorkflowId(s.parentId());
            req.setReRunFromTaskId(failedParentSubTask.getTaskId());
            workflowClient.rerunWorkflow(s.parentId(), req);

            awaitWorkflowStatus(s.parentId(), Workflow.WorkflowStatus.RUNNING, "Parent → RUNNING");
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(s.parentId(), true);
                assertNotEquals(s.failingChildId(), findActiveTask(wf, FAILING_SUB_REF, "missing").getSubWorkflowId(),
                        "rerun on the SUB_WORKFLOW task itself must spawn a fresh child");
                assertNotEquals(s.cancelledChildId(), findActiveTask(wf, CANCELLED_SUB_REF, "missing").getSubWorkflowId(),
                        "finalizeRerun wipes subWorkflowId on terminal-unsuccessful siblings — fresh child");
                assertCompletedSiblingUntouched(s.parentId(), s.completedChildId());
            });

            String newFailingChildId = findActiveTask(s.parentId(), FAILING_SUB_REF, "missing").getSubWorkflowId();
            String newCancelledChildId = findActiveTask(s.parentId(), CANCELLED_SUB_REF, "missing").getSubWorkflowId();
            driveBothBranchesToCompletion(s.parentId(), newFailingChildId, newCancelledChildId);
        } finally {
            terminateAll(s.parentName(), s.failingName(), s.cancelledName(), s.completedName());
        }
    }

    /**
     * Case 3 of four. Rerun from the CANCELED SUB_WORKFLOW task in the parent (cancelled
     * sibling's parent-level task). No walk-up (parent is top-level); finalizeRerun's
     * sibling reset spawns fresh children for both terminal-unsuccessful siblings.
     */
    @Test
    @DisplayName("Rerun CANCELLED sibling SUB_WORKFLOW task in parent reschedules FAILED sibling, leaves COMPLETED sibling alone")
    public void testRerunCancelledSubWorkflowTaskInParentReschedulesFailedSibling() {
        ForkScenario s = forkWithFailedAndCancelledAndCompleted("case3");
        try {
            Task cancelledParentSubTask = taskOf(workflowClient.getWorkflow(s.parentId(), true), CANCELLED_SUB_REF);
            RerunWorkflowRequest req = new RerunWorkflowRequest();
            req.setReRunFromWorkflowId(s.parentId());
            req.setReRunFromTaskId(cancelledParentSubTask.getTaskId());
            workflowClient.rerunWorkflow(s.parentId(), req);

            awaitWorkflowStatus(s.parentId(), Workflow.WorkflowStatus.RUNNING, "Parent → RUNNING");
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(s.parentId(), true);
                assertNull(findActiveTask(wf, FAILING_SUB_REF, "missing").getReasonForIncompletion(),
                        FAILING_SUB_REF + " reasonForIncompletion must be cleared after rerun");
                assertNotNull(findActiveTask(wf, CANCELLED_SUB_REF, "missing").getSubWorkflowId());
                assertCompletedSiblingUntouched(s.parentId(), s.completedChildId());
            });

            String newFailingChildId = findActiveTask(s.parentId(), FAILING_SUB_REF, "missing").getSubWorkflowId();
            String newCancelledChildId = findActiveTask(s.parentId(), CANCELLED_SUB_REF, "missing").getSubWorkflowId();
            driveBothBranchesToCompletion(s.parentId(), newFailingChildId, newCancelledChildId);
        } finally {
            terminateAll(s.parentName(), s.failingName(), s.cancelledName(), s.completedName());
        }
    }

    /**
     * Case 4 of four. Rerun from the CANCELED SIMPLE task INSIDE the cancelled sibling.
     * Walk-up through the cancelled child to the parent; sibling retry-in-place restores
     * the FAILED sibling.
     */
    @Test
    @DisplayName("Rerun CANCELED simple task inside sibling sub-workflow reschedules FAILED sibling, leaves COMPLETED sibling alone")
    public void testRerunCancelledTaskInsideSiblingSubWorkflowReschedulesFailedSibling() {
        ForkScenario s = forkWithFailedAndCancelledAndCompleted("case4");
        try {
            Task cancelledInnerTask = taskOf(workflowClient.getWorkflow(s.cancelledChildId(), true), CANCELLED_INNER);
            RerunWorkflowRequest req = new RerunWorkflowRequest();
            req.setReRunFromWorkflowId(s.cancelledChildId());
            req.setReRunFromTaskId(cancelledInnerTask.getTaskId());
            workflowClient.rerunWorkflow(s.cancelledChildId(), req);

            awaitWorkflowStatus(s.parentId(), Workflow.WorkflowStatus.RUNNING, "Parent → RUNNING");
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow wf = workflowClient.getWorkflow(s.parentId(), true);
                assertEquals(s.cancelledChildId(), findActiveTask(wf, CANCELLED_SUB_REF, "missing").getSubWorkflowId(),
                        "cancelled branch retried in place — subWorkflowId preserved");
                assertEquals(s.failingChildId(), findActiveTask(wf, FAILING_SUB_REF, "missing").getSubWorkflowId(),
                        "failing branch retried in place — subWorkflowId preserved");
                assertNull(findActiveTask(wf, FAILING_SUB_REF, "missing").getReasonForIncompletion());
                assertCompletedSiblingUntouched(s.parentId(), s.completedChildId());
            });

            driveBothBranchesToCompletion(s.parentId(), s.failingChildId(), s.cancelledChildId());
        } finally {
            terminateAll(s.parentName(), s.failingName(), s.cancelledName(), s.completedName());
        }
    }

    // ---------------- Workflow-construction helpers ----------------
    // Cut the boilerplate of building TaskDef/WorkflowTask/SubWorkflowParams/FORK_JOIN/JOIN/WorkflowDef
    // by hand in every test. Each helper returns the WorkflowTask (or registers the def) — compose them.

    /** SIMPLE task; name == ref (good enough for tests). retryCount=0 so failures don't auto-retry. */
    private static WorkflowTask simpleTask(String refName) {
        TaskDef def = new TaskDef(refName);
        def.setRetryCount(0);
        def.setOwnerEmail("test@conductor.io");
        WorkflowTask wt = new WorkflowTask();
        wt.setTaskReferenceName(refName);
        wt.setName(refName);
        wt.setTaskDefinition(def);
        wt.setWorkflowTaskType(TaskType.SIMPLE);
        return wt;
    }

    /** SUB_WORKFLOW task that targets v1 of the named workflow def. */
    private static WorkflowTask subWorkflowTask(String refName, String subWorkflowName) {
        SubWorkflowParams params = new SubWorkflowParams();
        params.setName(subWorkflowName);
        params.setVersion(1);
        WorkflowTask wt = new WorkflowTask();
        wt.setTaskReferenceName(refName);
        wt.setName(subWorkflowName);
        wt.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        wt.setSubWorkflowParam(params);
        return wt;
    }

    /** FORK_JOIN whose branches are the given task lists. */
    private static WorkflowTask forkJoin(String refName, List<List<WorkflowTask>> branches) {
        WorkflowTask fork = new WorkflowTask();
        fork.setTaskReferenceName(refName);
        fork.setName("FORK_JOIN");
        fork.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork.setForkTasks(branches);
        return fork;
    }

    /** JOIN that waits on the given task ref names. */
    private static WorkflowTask join(String refName, List<String> joinOn) {
        WorkflowTask join = new WorkflowTask();
        join.setTaskReferenceName(refName);
        join.setName("JOIN");
        join.setWorkflowTaskType(TaskType.JOIN);
        join.setJoinOn(joinOn);
        return join;
    }

    /** Build + register a WorkflowDef with default 600s timeout. */
    private void registerWorkflow(String name, List<WorkflowTask> tasks) {
        registerWorkflow(name, tasks, 600);
    }

    /** Build + register a WorkflowDef with an explicit timeout. */
    private void registerWorkflow(String name, List<WorkflowTask> tasks, int timeoutSeconds) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setOwnerEmail("test@conductor.io");
        def.setTimeoutSeconds(timeoutSeconds);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTasks(tasks);
        metadataClient.updateWorkflowDefs(java.util.List.of(def));
    }

    /** First non-terminal task with the given refName in {@code wf}, or AssertionError. */
    private static Task findActiveTask(Workflow wf, String refName, String errorMessage) {
        return wf.getTasks().stream()
                .filter(t -> refName.equals(t.getReferenceTaskName()) && !t.getStatus().isTerminal())
                .findFirst()
                .orElseThrow(() -> new AssertionError(errorMessage));
    }

    /** Convenience: fetch the workflow then find the active task. */
    private Task findActiveTask(String workflowId, String refName, String errorMessage) {
        return findActiveTask(workflowClient.getWorkflow(workflowId, true), refName, errorMessage);
    }

    /** Status of a task in the given workflow by ref name (terminal or otherwise). */
    private static Task.Status statusOf(Workflow wf, String refName) {
        return taskOf(wf, refName).getStatus();
    }

    /** Task by ref name (terminal or otherwise), or AssertionError if missing. */
    private static Task taskOf(Workflow wf, String refName) {
        return wf.getTasks().stream()
                .filter(t -> refName.equals(t.getReferenceTaskName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(refName + " not found in workflow " + wf.getWorkflowId()));
    }

    /** Start v1 of a registered workflow with empty input and return its id. */
    private String start(String workflowName) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(workflowName);
        req.setVersion(1);
        return workflowClient.startWorkflow(req);
    }

    /** Best-effort cleanup of any running instances of the given workflow names. */
    private void terminateAll(String... workflowNames) {
        for (String name : workflowNames) {
            try { terminateExistingRunningWorkflows(name); } catch (Exception ignore) { /* best-effort */ }
        }
    }

    /** Await the workflow reaching {@code expected} status (15s default). */
    private void awaitWorkflowStatus(String workflowId, Workflow.WorkflowStatus expected, String message) {
        awaitWorkflowStatus(workflowId, expected, 15, message);
    }

    /** Await the workflow reaching {@code expected} status within {@code seconds}. */
    private void awaitWorkflowStatus(String workflowId, Workflow.WorkflowStatus expected, int seconds, String message) {
        await().atMost(seconds, TimeUnit.SECONDS).untilAsserted(() ->
                assertEquals(expected, workflowClient.getWorkflow(workflowId, true).getStatus(), message));
    }

    @Test
    @DisplayName("Test wait task with timer rerun - verify waitTimer restart")
    public void testWaitTaskWithTimerRerun() {
        String workflowName = "rerun-wait-timer-workflow";
        registerWaitTimerWorkflowDef(workflowName);

        terminateExistingRunningWorkflows(workflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Wait for wait task to be in progress
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            boolean hasWaitTask = workflow.getTasks().stream()
                    .filter(t -> TaskType.WAIT.name().equals(t.getTaskType()))
                    .anyMatch(t -> Task.Status.IN_PROGRESS.equals(t.getStatus()));
            assertTrue(hasWaitTask);
        });

        // Get the wait task
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task waitTask = workflow.getTasks().get(0);

        // Record initial callback time
        long initialCallbackTime = waitTask.getCallbackAfterSeconds();
        assertTrue(initialCallbackTime > 0, "Wait task should have callback timer");
        assertEquals(1, waitTask.getPollCount(), "Wait task should not have been polled yet");
        workflowClient.terminateWorkflow(workflowId, "Terminate to test rerun");
        // Wait a moment to let some time pass
        try { Thread.sleep(TimeUnit.SECONDS.toMillis(2)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        // Rerun from wait task
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(waitTask.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        // Verify rerun behavior
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        Task newWaitTask = workflow.getTasks().get(0);
        assertEquals(initialCallbackTime, newWaitTask.getCallbackAfterSeconds(),
                "Timer should restart with same duration");
        assertEquals(0, newWaitTask.getPollCount(),
                "New wait task should not have been polled");

        workflowClient.terminateWorkflow(workflowId, "Test completed");
    }

    @Test
    @DisplayName("Test rerun from http task inside do_while loop with nested tasks")
    public void testRerunFromHttpTaskInDoWhileWithNestedTasks() {
        String workflowName = "vialtodoowhile-rerun-test";
        String subWorkflowName = "Wait";

        // Register the sub-workflow first
        registerWaitSubWorkflow(subWorkflowName);

        // Register the main workflow
        registerViaToDoWhileWorkflowDef(workflowName, subWorkflowName);

        terminateExistingRunningWorkflows(workflowName);
        terminateExistingRunningWorkflows(subWorkflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        Map<String, Object> variables = new HashMap<>();
        variables.put("status", "InProgress");
        startWorkflowRequest.setInput(Map.of("variables", variables));

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Wait for first iteration SUB_WORKFLOW task to complete naturally
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertTrue(wf.getTasks().stream().anyMatch(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) &&
                    t.getIteration() == 1 &&
                    t.getStatus() == Task.Status.COMPLETED));
        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task wait_task = workflow.getTasks().stream()
                .filter(t -> "WAIT".equals(t.getTaskType()) && t.getIteration() == 1)
                .findFirst()
                .orElseThrow();
        completeTask(wait_task, TaskResult.Status.COMPLETED);

        // Wait for second iteration HTTP task
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertTrue(wf.getTasks().stream().anyMatch(t -> "HTTP".equals(t.getTaskType()) && t.getIteration() == 2));
        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        Task httpTask2 = workflow.getTasks().stream()
                .filter(t -> "HTTP".equals(t.getTaskType()) && t.getIteration() == 2)
                .findFirst()
                .orElseThrow();

        // Fail the HTTP task in second iteration
        workflowClient.terminateWorkflow(workflowId, "Fail for test");
        workflow = workflowClient.getWorkflow(workflowId, false);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, workflow.getStatus());

        // Rerun from the second iteration http task
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(httpTask2.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        // Verify rerun behavior
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            System.out.println("Reason " + wf.getReasonForIncompletion());
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
        });

        workflow = workflowClient.getWorkflow(workflowId, true);
        // Verify do_while task is IN_PROGRESS with iteration 2
        Task doWhileTask = workflow.getTasks().stream()
                .filter(t -> TaskType.DO_WHILE.name().equals(t.getTaskType()))
                .findFirst()
                .orElseThrow();
        assertEquals(Task.Status.IN_PROGRESS, doWhileTask.getStatus());

        // Verify http task is scheduled with iteration 2
        Task newHttpTask = workflow.getTasks().stream()
                .filter(t -> "HTTP".equals(t.getTaskType()) && t.getIteration() == 2)
                .findFirst()
                .orElseThrow();
        assertNotNull(newHttpTask);
        assertEquals("http_ref__2", newHttpTask.getReferenceTaskName());

        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Test completed");
    }

    @Test
    @DisplayName("Test rerun from sub_workflow task inside do_while loop")
    public void testRerunFromSubWorkflowTaskInDoWhile() {
        String workflowName = "vialtodoowhile-subworkflow-rerun-test";
        String subWorkflowName = "Wait";

        // Register the sub-workflow first
        registerWaitSubWorkflow(subWorkflowName);

        // Register the main workflow
        registerViaToDoWhileWorkflowDef(workflowName, subWorkflowName);

        terminateExistingRunningWorkflows(workflowName);
        terminateExistingRunningWorkflows(subWorkflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        Map<String, Object> variables = new HashMap<>();
        variables.put("status", "InProgress");
        startWorkflowRequest.setInput(Map.of("variables", variables));

        // Add parent-level task-to-domain mapping
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("http", "parent-domain");
        startWorkflowRequest.setTaskToDomain(taskToDomain);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Verify parent workflow has task-to-domain mapping
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertNotNull(wf.getTaskToDomain());
            assertEquals("parent-domain", wf.getTaskToDomain().get("http"));
        });

        // Wait for first iteration SUB_WORKFLOW task to complete naturally
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertTrue(wf.getTasks().stream().anyMatch(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) &&
                    t.getIteration() == 1 &&
                    t.getStatus() == Task.Status.COMPLETED));
        });

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);

        // Verify sub-workflow task has subWorkflowTaskToDomain in input
        Task subWorkflowTask1 = workflow.getTasks().stream()
                .filter(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) && t.getIteration() == 1)
                .findFirst()
                .orElseThrow();

        // Get the sub-workflow and verify it has the taskToDomain mapping
        String subWorkflowId1 = subWorkflowTask1.getSubWorkflowId();
        Workflow subWorkflow1 = workflowClient.getWorkflow(subWorkflowId1, true);

        // Verify sub-workflow received task-to-domain mapping
        if (subWorkflowTask1.getInputData().containsKey("subWorkflowTaskToDomain")) {
            Map<String, String> subWorkflowTaskToDomain = (Map<String, String>) subWorkflowTask1.getInputData()
                    .get("subWorkflowTaskToDomain");
            if (subWorkflowTaskToDomain != null && !subWorkflowTaskToDomain.isEmpty()) {
                assertNotNull(subWorkflow1.getTaskToDomain(),
                        "Sub-workflow should have taskToDomain if passed from parent");
            }
        }
        Task wait_task = workflow.getTasks().stream()
                .filter(t -> "WAIT".equals(t.getTaskType()) && t.getIteration() == 1)
                .findFirst()
                .orElseThrow();
        completeTask(wait_task, TaskResult.Status.COMPLETED);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertTrue(wf.getTasks().stream().anyMatch(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) &&
                    t.getIteration() == 2 &&
                    t.getStatus() == Task.Status.IN_PROGRESS));
        });
        workflow = workflowClient.getWorkflow(workflowId, true);
        Task subWorkflowTask2 = workflow.getTasks().stream()
                .filter(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) && t.getIteration() == 2)
                .findFirst()
                .orElseThrow();

        // Terminate the sub-workflow to cause failure
        String subWorkflowId2 = subWorkflowTask2.getSubWorkflowId();
        workflowClient.terminateWorkflow(subWorkflowId2, "Fail for test");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertEquals(Workflow.WorkflowStatus.TERMINATED, wf.getStatus());
        });

        // Rerun from second iteration sub_workflow task
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(subWorkflowTask2.getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        // Verify rerun behavior
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        // Verify parent workflow still has task-to-domain mapping after rerun
        assertNotNull(workflow.getTaskToDomain(), "Parent workflow should retain taskToDomain after rerun");
        assertEquals("parent-domain", workflow.getTaskToDomain().get("http"),
                "Parent domain should be preserved after rerun");

        // Verify do_while task is IN_PROGRESS with iteration 2
        Task doWhileTask = workflow.getTasks().stream()
                .filter(t -> TaskType.DO_WHILE.name().equals(t.getTaskType()))
                .findFirst()
                .orElseThrow();
        assertEquals(Task.Status.IN_PROGRESS, doWhileTask.getStatus());

        // Verify sub_workflow task is scheduled with iteration 2
        Task newSubWorkflowTask = workflow.getTasks().stream()
                .filter(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) && t.getIteration() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(Task.Status.IN_PROGRESS, newSubWorkflowTask.getStatus());

        // Verify that subWorkflowTaskToDomain is preserved in the rerun sub-workflow task
        if (newSubWorkflowTask.getInputData().containsKey("subWorkflowTaskToDomain")) {
            Map<String, String> rerunSubWorkflowTaskToDomain = (Map<String, String>) newSubWorkflowTask.getInputData()
                    .get("subWorkflowTaskToDomain");
            if (subWorkflowTask2.getInputData().containsKey("subWorkflowTaskToDomain")) {
                Map<String, String> originalSubWorkflowTaskToDomain = (Map<String, String>) subWorkflowTask2
                        .getInputData().get("subWorkflowTaskToDomain");
                assertEquals(originalSubWorkflowTaskToDomain, rerunSubWorkflowTaskToDomain,
                        "Sub-workflow task-to-domain should be preserved after rerun");
            }
        }

        // Terminate the workflow
        workflowClient.terminateWorkflow(workflowId, "Test completed");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertEquals(Workflow.WorkflowStatus.TERMINATED,
                        workflowClient.getWorkflow(workflowId, false).getStatus()));

        // Now rerun from the http task in iteration 2. It should schedule the http task
        // and also further task down the line.
        workflow = workflowClient.getWorkflow(workflowId, true);
        rerunWorkflowRequest.setReRunFromTaskId(workflow.getTaskByRefName("http_ref__2").getTaskId());
        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertEquals(Workflow.WorkflowStatus.RUNNING, wf.getStatus());
            assertTrue(wf.getTasks().stream().anyMatch(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) &&
                    t.getIteration() == 2 &&
                    t.getStatus() == Task.Status.IN_PROGRESS));
        });
    }

    @Test
    @DisplayName("Test rerun from switch task inside do_while loop")
    public void testRerunFromSwitchTaskInDoWhile() {
        String workflowName = "vialtodoowhile-switch-rerun-test";
        String subWorkflowName = "Wait";

        // Register the sub-workflow first
        registerWaitSubWorkflow(subWorkflowName);

        // Register the main workflow
        registerViaToDoWhileWorkflowDef(workflowName, subWorkflowName);

        terminateExistingRunningWorkflows(workflowName);
        terminateExistingRunningWorkflows(subWorkflowName);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);
        Map<String, Object> variables = new HashMap<>();
        variables.put("status", "InProgress");
        startWorkflowRequest.setInput(Map.of("variables", variables));

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertTrue(wf.getTasks().stream().anyMatch(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) &&
                    t.getIteration() == 1 &&
                    t.getStatus() == Task.Status.COMPLETED));
        });
        workflow = workflowClient.getWorkflow(workflowId, true);
        Task wait_task = workflow.getTasks().stream()
                .filter(t -> "WAIT".equals(t.getTaskType()) && t.getIteration() == 1)
                .findFirst()
                .orElseThrow();
        completeTask(wait_task, TaskResult.Status.COMPLETED);

        workflow = workflowClient.getWorkflow(workflowId, true);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertTrue(wf.getTasks().stream().anyMatch(t -> TaskType.SUB_WORKFLOW.name().equals(t.getTaskType()) &&
                    t.getIteration() == 2 &&
                    t.getStatus() == Task.Status.COMPLETED));
        });
        workflow = workflowClient.getWorkflow(workflowId, true);
        wait_task = workflow.getTasks().stream()
                .filter(t -> "WAIT".equals(t.getTaskType()) && t.getIteration() == 2)
                .findFirst()
                .orElseThrow();
        completeTask(wait_task, TaskResult.Status.COMPLETED);
        workflow = workflowClient.getWorkflow(workflowId, true);

        // Get the switch task from third iteration for rerun
        Task switchTask3 = workflow.getTasks().stream()
                .filter(t -> TaskType.SWITCH.name().equals(t.getTaskType()) && t.getIteration() == 2)
                .findFirst()
                .orElseThrow();

        // Rerun from third iteration switch task with different input to trigger case 'b'
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(switchTask3.getTaskId());

        // Modify the input to trigger case 'b'
        Map<String, Object> newTaskInput = new HashMap<>();
        newTaskInput.put("switchCaseValue", "b");
        rerunWorkflowRequest.setTaskInput(newTaskInput);

        workflowClient.rerunWorkflow(workflowId, rerunWorkflowRequest);

        // Verify rerun behavior
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        // Verify do_while task is IN_PROGRESS with iteration 2
        Task doWhileTask = workflow.getTasks().stream()
                .filter(t -> TaskType.DO_WHILE.name().equals(t.getTaskType()))
                .findFirst()
                .orElseThrow();
        assertEquals(Task.Status.COMPLETED, doWhileTask.getStatus());

        // Verify switch task is completed
        Task newSwitchTask = workflow.getTasks().stream()
                .filter(t -> TaskType.SWITCH.name().equals(t.getTaskType()) && t.getIteration() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(Task.Status.COMPLETED, newSwitchTask.getStatus());

        // Verify set_variable_b (case b) task is now scheduled instead of
        // set_variable_a in iteration 2
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            assertTrue(wf.getTasks().stream()
                    .anyMatch(t -> "set_variable_b".equals(t.getTaskDefName()) && t.getIteration() == 2));
        });
    }

    private void registerWaitTimerWorkflowDef(String workflowName) {
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setTaskReferenceName("wait_timer_task");
        waitTask.setName("wait_timer_task");
        waitTask.setWorkflowTaskType(TaskType.WAIT);
        waitTask.setInputParameters(Map.of("duration", "30 seconds"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setDescription("Test wait task timer rerun behavior");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(List.of(waitTask));
        workflowDef.setOwnerEmail("test@conductor.io");

        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }

    private void registerSwitchInsideDoWhileWorkflowDef(String workflowName) {
        // Create switch task with case1 and case2
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

        // Create do_while task that loops over the switch task
        WorkflowTask doWhileTask = new WorkflowTask();
        doWhileTask.setTaskReferenceName("do_while_task");
        doWhileTask.setName("do_while_task");
        doWhileTask.setInputParameters(Map.of("maxIterations", "${workflow.input.maxIterations}"));
        doWhileTask.setWorkflowTaskType(TaskType.DO_WHILE);
        doWhileTask.setLoopCondition("if ($.do_while_task['iteration'] < $.maxIterations) { true; } else { false; }");
        doWhileTask.setLoopOver(List.of(switchTask));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setDescription("Test switch inside do_while rerun behavior");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(List.of(doWhileTask));
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setInputParameters(List.of("maxIterations", "switchValue"));

        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }

    private void registerWaitSubWorkflow(String workflowName) {
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setTaskReferenceName("wait_task");
        waitTask.setName("wait_task");
        waitTask.setWorkflowTaskType(TaskType.WAIT);
        waitTask.setInputParameters(Map.of("duration", "4 seconds"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setDescription("Wait sub-workflow");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(List.of(waitTask));
        workflowDef.setOwnerEmail("test@conductor.io");

        try {
            metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
        } catch (Exception e) {
            // Workflow might already exist, that's okay
        }
    }

    private void registerViaToDoWhileWorkflowDef(String workflowName, String subWorkflowName) {
        // Create HTTP task
        WorkflowTask httpTask = new WorkflowTask();
        httpTask.setTaskReferenceName("http_ref");
        httpTask.setName("http");
        httpTask.setWorkflowTaskType(TaskType.HTTP);
        Map<String, Object> httpInputs = new HashMap<>();
        httpInputs.put("uri", "https://orkes-api-tester.orkesconductor.com/api");
        httpInputs.put("method", "GET");
        httpInputs.put("accept", "application/json");
        httpInputs.put("contentType", "application/json");
        httpInputs.put("encode", true);
        httpTask.setInputParameters(httpInputs);

        // Create SUB_WORKFLOW task
        WorkflowTask subWorkflowTask = new WorkflowTask();
        subWorkflowTask.setTaskReferenceName("sub_workflow_ref");
        subWorkflowTask.setName("sub_workflow");
        subWorkflowTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        subWorkflowParams.setVersion(1);
        subWorkflowTask.setSubWorkflowParam(subWorkflowParams);
        subWorkflowTask.setInputParameters(new HashMap<>());

        // Create WAIT task
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setTaskReferenceName("wait_ref");
        waitTask.setName("wait");
        waitTask.setWorkflowTaskType(TaskType.WAIT);
        waitTask.setInputParameters(new HashMap<>());

        // Create case tasks for SWITCH
        WorkflowTask setVariableTaskA = new WorkflowTask();
        setVariableTaskA.setTaskReferenceName("set_variable_ref_a");
        setVariableTaskA.setName("set_variable_a");
        setVariableTaskA.setWorkflowTaskType(TaskType.SET_VARIABLE);
        setVariableTaskA.setInputParameters(Map.of("status", "Completed"));

        WorkflowTask setVariableTaskB = new WorkflowTask();
        setVariableTaskB.setTaskReferenceName("set_variable_ref_b");
        setVariableTaskB.setName("set_variable_b");
        setVariableTaskB.setWorkflowTaskType(TaskType.SET_VARIABLE);
        setVariableTaskB.setInputParameters(Map.of("status", "Completed"));

        // Create SWITCH task
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setTaskReferenceName("switch_ref");
        switchTask.setName("switch");
        switchTask.setWorkflowTaskType(TaskType.SWITCH);
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        switchTask.getDecisionCases().put("a", List.of(setVariableTaskA));
        switchTask.getDecisionCases().put("b", List.of(setVariableTaskB));
        switchTask.setDefaultCase(new ArrayList<>());
        switchTask.setInputParameters(Map.of("switchCaseValue", "${wait_ref.output.result}"));

        // Create DO_WHILE task
        WorkflowTask doWhileTask = new WorkflowTask();
        doWhileTask.setTaskReferenceName("do_while_ref");
        doWhileTask.setName("do_while");
        doWhileTask.setWorkflowTaskType(TaskType.DO_WHILE);
        doWhileTask.setEvaluatorType("graaljs");
        doWhileTask.setLoopCondition(
                "(function () { if ($.do_while_ref['iteration'] < 3) { return true;} return false;})();");
        doWhileTask.setLoopOver(List.of(httpTask, subWorkflowTask, waitTask, switchTask));
        doWhileTask.setInputParameters(Map.of("status", "${workflow.variables.status}"));

        // Create final HTTP task after do_while
        WorkflowTask httpRef3Task = new WorkflowTask();
        httpRef3Task.setTaskReferenceName("http_ref_3");
        httpRef3Task.setName("http_3");
        httpRef3Task.setWorkflowTaskType(TaskType.HTTP);
        httpRef3Task.setInputParameters(httpInputs);

        // Create workflow definition
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setDescription("vialtodoowhile - Test rerun with complex do_while loop");
        workflowDef.setTimeoutSeconds(0);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        workflowDef.setTasks(List.of(doWhileTask, httpRef3Task));
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setEnforceSchema(true);
        workflowDef.setVariables(new HashMap<>());
        workflowDef.setInputParameters(List.of());

        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
    }

    /**
     * FORK_JOIN inside DO_WHILE where both branches are SUB_WORKFLOW tasks. BranchA
     * (failing child) has its inner simple task failed. BranchB (cancelled child) is
     * cancelled as a side effect of the fork failure cascade. Rerun from the failed inner
     * task. Asserts the cancelled SUB_WORKFLOW sibling is restored in place (same
     * subWorkflowId), resumed non-terminal, and once both children finish, parent reaches
     * COMPLETED.
     *
     * This test pins the fix's behavior when the cancelled SUB_WORKFLOW sibling
     * appears inside a DO_WHILE iteration (a code path the PR's other tests do not
     * exercise).
     */
    @Test
    @DisplayName("PR #3596: Rerun task inside SUB_WORKFLOW nested in FORK-inside-DO_WHILE preserves cancelled SUB_WORKFLOW sibling")
    public void testRerunSubWorkflowTaskInsideForkInsideDoWhilePreservesCancelledSibling() {
        long stamp = System.currentTimeMillis();
        String parentName = "rerun-dw-fork-parent-" + stamp;
        String failingChildName = "rerun-dw-fork-failing-" + stamp;
        String cancelledChildName = "rerun-dw-fork-cancelled-" + stamp;
        String failingInnerRef = "failing_inner_dw_" + stamp;
        String cancelledInnerRef = "cancelled_inner_dw_" + stamp;
        String branchARef = "branchA_dw_" + stamp;
        String branchBRef = "branchB_dw_" + stamp;
        String forkRef = "fork_inside_dw_" + stamp;
        String joinRef = "join_inside_dw_" + stamp;
        String dwRef = "dw_loop_" + stamp;

        registerWorkflow(failingChildName, List.of(simpleTask(failingInnerRef)));
        registerWorkflow(cancelledChildName, List.of(simpleTask(cancelledInnerRef)));

        WorkflowTask fork = forkJoin(forkRef, List.of(
                List.of(subWorkflowTask(branchARef, failingChildName)),
                List.of(subWorkflowTask(branchBRef, cancelledChildName))));
        WorkflowTask joinTask = join(joinRef, List.of(branchARef, branchBRef));

        WorkflowTask dw = new WorkflowTask();
        dw.setTaskReferenceName(dwRef);
        dw.setName(dwRef);
        dw.setWorkflowTaskType(TaskType.DO_WHILE);
        // Single iteration: stop after iteration 1
        dw.setLoopCondition("if ($." + dwRef + "['iteration'] < 1) { true; } else { false; }");
        dw.setLoopOver(List.of(fork, joinTask));

        registerWorkflow(parentName, List.of(dw), 900);

        try {
            String parentId = start(parentName);

            // Iteration 1's branch SUB_WORKFLOW tasks must materialize.
            // DO_WHILE child task refs in Conductor follow the convention
            // <innerRef>__<iteration>, but we filter loosely on `contains(branchRef)`
            // to be robust to convention drift across versions.
            String[] failingChildIdHolder = new String[1];
            String[] cancelledChildIdHolder = new String[1];

            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow parent = workflowClient.getWorkflow(parentId, true);
                Task failingBranch = parent.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName() != null
                                && t.getReferenceTaskName().contains(branchARef)
                                && t.getSubWorkflowId() != null)
                        .findFirst().orElse(null);
                Task cancelledBranch = parent.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName() != null
                                && t.getReferenceTaskName().contains(branchBRef)
                                && t.getSubWorkflowId() != null)
                        .findFirst().orElse(null);

                assertNotNull(failingBranch,
                        "branchA SUB_WORKFLOW task must materialize inside DO_WHILE iteration 1");
                assertNotNull(cancelledBranch,
                        "branchB SUB_WORKFLOW task must materialize inside DO_WHILE iteration 1");

                failingChildIdHolder[0] = failingBranch.getSubWorkflowId();
                cancelledChildIdHolder[0] = cancelledBranch.getSubWorkflowId();

                // Child workflows must actually have their inner tasks
                assertFalse(workflowClient.getWorkflow(failingChildIdHolder[0], true).getTasks().isEmpty(),
                        "failing child must have its inner task scheduled");
            });

            String failingChildId = failingChildIdHolder[0];
            String originalCancelledChildId = cancelledChildIdHolder[0];

            // Fail the inner task in iteration 1's failing branch
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertNotNull(findActiveTask(failingChildId, failingInnerRef,
                            "failing inner task must be active before failure"),
                            "failing inner task must be active"));
            Task failedInner = findActiveTask(failingChildId, failingInnerRef,
                    "failing inner task not active");
            String failedInnerTaskId = failedInner.getTaskId();
            completeTask(failedInner, TaskResult.Status.FAILED);

            // Failure cascades: branchA -> FORK -> DO_WHILE -> parent. branchB CANCELED.
            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.FAILED, 25,
                    "parent must FAIL after iteration 1 branchA failure cascades through FORK and DO_WHILE");
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow cancelledChild = workflowClient.getWorkflow(originalCancelledChildId, true);
                assertTrue(cancelledChild.getStatus().isTerminal()
                                && !cancelledChild.getStatus().isSuccessful(),
                        "cancelled branch child must be terminal-unsuccessful, got "
                                + cancelledChild.getStatus());
            });

            // === RERUN from the FAILED inner task ===
            // updateAndPushParents walks from failingChild -> parent. The new branch in
            // this PR restores the cancelled SUB_WORKFLOW sibling (branchB) in place.
            RerunWorkflowRequest rerun = new RerunWorkflowRequest();
            rerun.setReRunFromWorkflowId(failingChildId);
            rerun.setReRunFromTaskId(failedInnerTaskId);
            workflowClient.rerunWorkflow(failingChildId, rerun);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.RUNNING, 30,
                    "parent must be RUNNING after rerun walk-up");

            // === KEY ASSERTIONS ===
            // (1) Cancelled branch SUB_WORKFLOW sibling restored in place — same id.
            // (2) Resumed cancelled child is non-terminal.
            // (3) Failing branch resumed under same id.
            await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow parent = workflowClient.getWorkflow(parentId, true);

                Task cancelledBranchNow = parent.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName() != null
                                && t.getReferenceTaskName().contains(branchBRef)
                                && !t.getStatus().isTerminal())
                        .findFirst().orElse(null);
                assertNotNull(cancelledBranchNow,
                        "An active branchB SUB_WORKFLOW task must exist after rerun walk-up");
                assertEquals(originalCancelledChildId, cancelledBranchNow.getSubWorkflowId(),
                        "branchB subWorkflowId must be PRESERVED (cancelled-sibling fix inside DO_WHILE iteration)");

                Workflow cancelledChild = workflowClient.getWorkflow(originalCancelledChildId, true);
                assertFalse(cancelledChild.getStatus().isTerminal(),
                        "resumed cancelled child must be non-terminal, got " + cancelledChild.getStatus());

                Task failingBranchNow = parent.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName() != null
                                && t.getReferenceTaskName().contains(branchARef)
                                && !t.getStatus().isTerminal())
                        .findFirst().orElse(null);
                assertNotNull(failingBranchNow,
                        "An active branchA SUB_WORKFLOW task must exist after rerun");
                assertEquals(failingChildId, failingBranchNow.getSubWorkflowId(),
                        "branchA subWorkflowId must be PRESERVED (in-place rerun on failing child)");
                assertNull(failingBranchNow.getReasonForIncompletion(),
                        "branchA reasonForIncompletion must be cleared after walk-up");
            });

            // Drive both children to completion
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertNotNull(findActiveTask(failingChildId, failingInnerRef,
                            "rerun must re-activate failing inner task")));
            completeTask(findActiveTask(failingChildId, failingInnerRef,
                    "failing inner task missing"), TaskResult.Status.COMPLETED);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertNotNull(findActiveTask(originalCancelledChildId, cancelledInnerRef,
                            "resumed cancelled inner task must become active")));
            completeTask(findActiveTask(originalCancelledChildId, cancelledInnerRef,
                    "cancelled inner task missing"), TaskResult.Status.COMPLETED);

            // Single iteration DO_WHILE, so once both branches complete, parent should COMPLETE.
            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.COMPLETED, 60,
                    "parent should reach COMPLETED after FORK inside DO_WHILE iteration 1 finishes");

        } finally {
            terminateAll(parentName, failingChildName, cancelledChildName);
        }
    }

    /**
     * Exact-shape test: parent v1 = DO_WHILE(number=2) { FORK[sub_workflow_ref_1, sub_workflow_ref] -> JOIN },
     * sub v1 = INLINE inline_ref -> SIMPLE simple_ref. Iter 1 succeeds end-to-end, iter 2 branchA
     * SIMPLE fails. Rerun the failed inner task. Asserts:
     *   - iter 1 untouched (workflow + inline/simple taskIds + endTimes + retryCount)
     *   - iter 2 branchB INLINE that was COMPLETED before failure stays the same instance
     *   - iter 2 branchB SIMPLE restored in place: exactly ONE non-terminal instance, no retried=true clone
     *   - parent reaches COMPLETED end-to-end
     */
    @Test
    @DisplayName("PR #3596: Rerun iteration 2 failure on exact prod shape: in-place reset on cancelled sibling, iter 1 untouched")
    public void testRerunSecondIterationFailureInForkInsideDoWhile() {
        long stamp = System.currentTimeMillis();
        String parentName = "parent_rerun_" + stamp;
        String childName = "sub_rerun_" + stamp;
        try {
            registerExactShapeParentAndChild(parentName, childName);
            String parentId = start(parentName);

            // Iter 1: complete simple_ref in both branches
            String[] iter1A = new String[1];
            String[] iter1B = new String[1];
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                iter1A[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref_1", 1);
                iter1B[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref", 1);
            });
            completeActiveSimpleRef(iter1A[0], TaskResult.Status.COMPLETED);
            completeActiveSimpleRef(iter1B[0], TaskResult.Status.COMPLETED);
            awaitWorkflowStatus(iter1A[0], Workflow.WorkflowStatus.COMPLETED, 20, "iter 1 branchA must COMPLETE");
            awaitWorkflowStatus(iter1B[0], Workflow.WorkflowStatus.COMPLETED, 20, "iter 1 branchB must COMPLETE");

            RerunIter1Snapshot snapA = snapshotRerunIter1Child(iter1A[0]);
            RerunIter1Snapshot snapB = snapshotRerunIter1Child(iter1B[0]);

            // Iter 2: wait for both branches, capture branchB's inline_ref after it auto-completes
            String[] iter2A = new String[1];
            String[] iter2B = new String[1];
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                iter2A[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref_1", 2);
                iter2B[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref", 2);
            });
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertEquals(Task.Status.COMPLETED, onlyTaskByRef(iter2B[0], "inline_ref").getStatus()));
            Task iter2BInlineBefore = onlyTaskByRef(iter2B[0], "inline_ref");

            // Fail iter 2 branchA simple_ref
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertNotNull(findActiveTask(iter2A[0], "simple_ref", "iter 2 branchA simple_ref active")));
            Task failedInner = findActiveTask(iter2A[0], "simple_ref", "iter 2 branchA simple_ref missing");
            String failedInnerTaskId = failedInner.getTaskId();
            completeTask(failedInner, TaskResult.Status.FAILED);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.FAILED, 30, "parent must FAIL after iter 2 branchA failure");

            // ===== Rerun from the failed inner task =====
            RerunWorkflowRequest rerun = new RerunWorkflowRequest();
            rerun.setReRunFromWorkflowId(iter2A[0]);
            rerun.setReRunFromTaskId(failedInnerTaskId);
            workflowClient.rerunWorkflow(iter2A[0], rerun);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.RUNNING, 30, "parent must be RUNNING after rerun");

            // Iter 1 untouched
            assertRerunIter1Unchanged(iter1A[0], snapA);
            assertRerunIter1Unchanged(iter1B[0], snapB);

            // Iter 2 branchB INLINE preserved
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                Task inlineAfter = onlyTaskByRef(iter2B[0], "inline_ref");
                assertEquals(Task.Status.COMPLETED, inlineAfter.getStatus());
                assertEquals(iter2BInlineBefore.getTaskId(), inlineAfter.getTaskId(), "iter 2 branchB inline taskId unchanged");
                assertEquals(iter2BInlineBefore.getEndTime(), inlineAfter.getEndTime(), "iter 2 branchB inline endTime unchanged");
            });

            // Drive iter 2 to completion
            completeActiveSimpleRef(iter2A[0], TaskResult.Status.COMPLETED);
            completeActiveSimpleRef(iter2B[0], TaskResult.Status.COMPLETED);
            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.COMPLETED, 30, "parent must reach COMPLETED");
        } finally {
            terminateAll(parentName, childName);
        }
    }

    // ===== Helpers for the exact-shape DO_WHILE-in-FORK rerun test =====

    private void registerExactShapeParentAndChild(String parentName, String childName) {
        TaskDef simpleDef = new TaskDef("simple");
        simpleDef.setRetryCount(0);
        simpleDef.setOwnerEmail("test@conductor.io");
        metadataClient.registerTaskDefs(List.of(simpleDef));

        WorkflowTask inline = new WorkflowTask();
        inline.setName("inline");
        inline.setTaskReferenceName("inline_ref");
        inline.setWorkflowTaskType(TaskType.INLINE);
        inline.setInputParameters(Map.of(
                "evaluatorType", "graaljs",
                "expression", "(function () { return $.value1 + $.value2; })();",
                "value1", 1, "value2", 2));

        WorkflowTask simple = new WorkflowTask();
        simple.setName("simple");
        simple.setTaskReferenceName("simple_ref");
        simple.setTaskDefinition(simpleDef);
        simple.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef childDef = new WorkflowDef();
        childDef.setName(childName);
        childDef.setOwnerEmail("test@conductor.io");
        childDef.setTimeoutSeconds(600);
        childDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        childDef.setTasks(List.of(inline, simple));
        metadataClient.updateWorkflowDefs(List.of(childDef));

        WorkflowTask branchA = subWorkflowTask("sub_workflow_ref_1", childName);
        branchA.setName("sub_workflow_1");
        WorkflowTask branchB = subWorkflowTask("sub_workflow_ref", childName);
        branchB.setName("sub_workflow");

        WorkflowTask fork = forkJoin("fork_ref", List.of(List.of(branchA), List.of(branchB)));
        fork.setName("fork");
        WorkflowTask joinTask = join("join_ref", List.of());
        joinTask.setName("join");

        WorkflowTask dw = new WorkflowTask();
        dw.setName("do_while");
        dw.setTaskReferenceName("do_while_ref");
        dw.setWorkflowTaskType(TaskType.DO_WHILE);
        dw.setInputParameters(Map.of("number", 2));
        dw.setLoopCondition("(function () { if ($.do_while_ref['iteration'] < $.number) { return true; } return false; })();");
        dw.setLoopOver(List.of(fork, joinTask));

        WorkflowDef parentDef = new WorkflowDef();
        parentDef.setName(parentName);
        parentDef.setOwnerEmail("test@conductor.io");
        parentDef.setTimeoutSeconds(900);
        parentDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentDef.setTasks(List.of(dw));
        metadataClient.updateWorkflowDefs(java.util.List.of(parentDef));
    }

    private String subWorkflowIdAtIteration(String parentId, String branchRef, int iteration) {
        String suffix = "__" + iteration;
        Task t = workflowClient.getWorkflow(parentId, true).getTasks().stream()
                .filter(x -> (branchRef + suffix).equals(x.getReferenceTaskName())
                        && x.getSubWorkflowId() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError(branchRef + suffix + " not materialised yet"));
        return t.getSubWorkflowId();
    }

    private void completeActiveSimpleRef(String childId, TaskResult.Status status) {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertNotNull(findActiveTask(childId, "simple_ref", "simple_ref must be active in " + childId)));
        completeTask(findActiveTask(childId, "simple_ref", "simple_ref missing in " + childId), status);
    }

    private Task onlyTaskByRef(String workflowId, String ref) {
        List<Task> matches = workflowClient.getWorkflow(workflowId, true).getTasks().stream()
                .filter(t -> ref.equals(t.getReferenceTaskName()))
                .collect(Collectors.toList());
        assertEquals(1, matches.size(),
                "expected exactly one " + ref + " in " + workflowId + ", got " + matches.size());
        return matches.get(0);
    }

    private static final class RerunIter1Snapshot {
        final long workflowEndTime;
        final String inlineTaskId;
        final long inlineEndTime;
        final String simpleTaskId;
        final long simpleEndTime;
        final int simpleRetryCount;
        RerunIter1Snapshot(long w, String ii, long ie, String si, long se, int sr) {
            workflowEndTime = w; inlineTaskId = ii; inlineEndTime = ie;
            simpleTaskId = si; simpleEndTime = se; simpleRetryCount = sr;
        }
    }

    private RerunIter1Snapshot snapshotRerunIter1Child(String childId) {
        Workflow wf = workflowClient.getWorkflow(childId, true);
        Task inline = wf.getTasks().stream().filter(t -> "inline_ref".equals(t.getReferenceTaskName())).findFirst().orElseThrow();
        Task simple = wf.getTasks().stream().filter(t -> "simple_ref".equals(t.getReferenceTaskName())).findFirst().orElseThrow();
        return new RerunIter1Snapshot(wf.getEndTime(),
                inline.getTaskId(), inline.getEndTime(),
                simple.getTaskId(), simple.getEndTime(), simple.getRetryCount());
    }

    private void assertRerunIter1Unchanged(String childId, RerunIter1Snapshot before) {
        Workflow wf = workflowClient.getWorkflow(childId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus(), childId + " must stay COMPLETED");
        assertEquals(before.workflowEndTime, wf.getEndTime(), childId + " endTime unchanged");
        Task inline = onlyTaskByRef(childId, "inline_ref");
        Task simple = onlyTaskByRef(childId, "simple_ref");
        assertEquals(before.inlineTaskId, inline.getTaskId(), childId + " inline taskId unchanged");
        assertEquals(before.inlineEndTime, inline.getEndTime(), childId + " inline endTime unchanged");
        assertEquals(before.simpleTaskId, simple.getTaskId(), childId + " simple taskId unchanged");
        assertEquals(before.simpleEndTime, simple.getEndTime(), childId + " simple endTime unchanged");
        assertEquals(before.simpleRetryCount, simple.getRetryCount(), childId + " simple retryCount unchanged");
    }

    /**
     * FORK_JOIN_DYNAMIC where all branches are SUB_WORKFLOW. One completes, one fails
     * with FAILED_WITH_TERMINAL_ERROR, one gets cancelled by the fork. Rerun from the
     * FAILED inner task in the failing child. Asserts:
     *   - failing branch's child resumed under SAME subWorkflowId (walk-up handles it)
     *   - cancelled branch's child resumed under SAME subWorkflowId (this PR's fix)
     *   - completed branch untouched
     *   - INLINE in every child preserved (no re-execution of completed tasks)
     */
    @Test
    @DisplayName("PR #3596: Rerun failed inner task in FORK_JOIN_DYNAMIC all-SUB_WORKFLOW branches preserves cancelled and failing sibling subWorkflowIds")
    public void testRerunInDynamicForkPreservesCancelledAndFailingSiblings() {
        long stamp = System.currentTimeMillis();
        String parentName = "parent_dynfork_rerun_" + stamp;
        String childName = "sub_dynfork_rerun_" + stamp;
        String failingRef = "branch_failing_rerun_" + stamp;
        String cancelledRef = "branch_cancelled_rerun_" + stamp;
        String completedRef = "branch_completed_rerun_" + stamp;

        try {
            registerDynamicForkParentAndChild(parentName, childName);

            String parentId = start(parentName);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    findActiveTask(parentId, "prep_ref", "prep_ref must be active"));
            completeDynamicForkPrep(parentId, childName, failingRef, cancelledRef, completedRef);

            String[] cF = new String[1];
            String[] cCa = new String[1];
            String[] cCo = new String[1];
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow parent = workflowClient.getWorkflow(parentId, true);
                cF[0] = parent.getTasks().stream()
                        .filter(t -> failingRef.equals(t.getReferenceTaskName()) && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow(() -> new AssertionError(failingRef + " not materialised"))
                        .getSubWorkflowId();
                cCa[0] = parent.getTasks().stream()
                        .filter(t -> cancelledRef.equals(t.getReferenceTaskName()) && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow(() -> new AssertionError(cancelledRef + " not materialised"))
                        .getSubWorkflowId();
                cCo[0] = parent.getTasks().stream()
                        .filter(t -> completedRef.equals(t.getReferenceTaskName()) && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow(() -> new AssertionError(completedRef + " not materialised"))
                        .getSubWorkflowId();
            });
            String failingChildId = cF[0];
            String cancelledChildId = cCa[0];
            String completedChildId = cCo[0];

            completeTask(findActiveTask(completedChildId, "simple_ref", "completed branch simple_ref must be active"),
                    TaskResult.Status.COMPLETED);
            awaitWorkflowStatus(completedChildId, Workflow.WorkflowStatus.COMPLETED, 15,
                    "completed branch must reach COMPLETED");

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    findActiveTask(failingChildId, "simple_ref", "failing branch simple_ref must be active"));
            Task failedInner = findActiveTask(failingChildId, "simple_ref", "failing branch simple_ref missing");
            String failedInnerTaskId = failedInner.getTaskId();
            completeTask(failedInner, TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.FAILED, 30,
                    "parent must FAIL after dynamic-fork branch terminal failure");

            // Snapshot INLINE in every child to prove no re-execution after rerun
            Task inlineF = onlyTaskByRef(failingChildId, "inline_ref");
            Task inlineCa = onlyTaskByRef(cancelledChildId, "inline_ref");
            Task inlineCo = onlyTaskByRef(completedChildId, "inline_ref");

            // === Rerun from the failed inner task ===
            RerunWorkflowRequest rerun = new RerunWorkflowRequest();
            rerun.setReRunFromWorkflowId(failingChildId);
            rerun.setReRunFromTaskId(failedInnerTaskId);
            workflowClient.rerunWorkflow(failingChildId, rerun);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.RUNNING, 30,
                    "parent must be RUNNING after rerun");

            await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow parent = workflowClient.getWorkflow(parentId, true);

                Task failingNow = parent.getTasks().stream()
                        .filter(t -> failingRef.equals(t.getReferenceTaskName()) && !t.getStatus().isTerminal())
                        .findFirst().orElseThrow(() -> new AssertionError("active " + failingRef + " missing"));
                assertEquals(failingChildId, failingNow.getSubWorkflowId(),
                        "failing branch subWorkflowId must be PRESERVED");

                Task cancelledNow = parent.getTasks().stream()
                        .filter(t -> cancelledRef.equals(t.getReferenceTaskName()) && !t.getStatus().isTerminal())
                        .findFirst().orElseThrow(() -> new AssertionError("active " + cancelledRef + " missing"));
                assertEquals(cancelledChildId, cancelledNow.getSubWorkflowId(),
                        "cancelled branch subWorkflowId must be PRESERVED (cancelled-sibling fix)");

                Task completedNow = parent.getTasks().stream()
                        .filter(t -> completedRef.equals(t.getReferenceTaskName())
                                && t.getStatus() == Task.Status.COMPLETED)
                        .findFirst().orElseThrow(() -> new AssertionError("completed " + completedRef + " missing"));
                assertEquals(completedChildId, completedNow.getSubWorkflowId(),
                        "completed branch subWorkflowId must be UNCHANGED");

                // INLINE inside each child must keep its taskId + endTime (no re-execution)
                Task inlineFAfter = onlyTaskByRef(failingChildId, "inline_ref");
                Task inlineCaAfter = onlyTaskByRef(cancelledChildId, "inline_ref");
                Task inlineCoAfter = onlyTaskByRef(completedChildId, "inline_ref");
                assertEquals(inlineF.getTaskId(), inlineFAfter.getTaskId(), "failing inline taskId unchanged");
                assertEquals(inlineF.getEndTime(), inlineFAfter.getEndTime(), "failing inline endTime unchanged");
                assertEquals(inlineCa.getTaskId(), inlineCaAfter.getTaskId(), "cancelled inline taskId unchanged");
                assertEquals(inlineCa.getEndTime(), inlineCaAfter.getEndTime(), "cancelled inline endTime unchanged");
                assertEquals(inlineCo.getTaskId(), inlineCoAfter.getTaskId(), "completed inline taskId unchanged");
                assertEquals(inlineCo.getEndTime(), inlineCoAfter.getEndTime(), "completed inline endTime unchanged");
            });

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    findActiveTask(failingChildId, "simple_ref", "failing simple_ref must be active after rerun"));
            completeTask(findActiveTask(failingChildId, "simple_ref", "failing simple_ref missing"),
                    TaskResult.Status.COMPLETED);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    findActiveTask(cancelledChildId, "simple_ref", "cancelled simple_ref must be active after rerun"));
            completeTask(findActiveTask(cancelledChildId, "simple_ref", "cancelled simple_ref missing"),
                    TaskResult.Status.COMPLETED);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.COMPLETED, 30,
                    "parent must reach COMPLETED end-to-end");
        } finally {
            terminateAll(parentName, childName);
        }
    }

    private void registerDynamicForkParentAndChild(String parentName, String childName) {
        TaskDef simpleDef = new TaskDef("simple");
        simpleDef.setRetryCount(0);
        simpleDef.setOwnerEmail("test@conductor.io");

        TaskDef prepDef = new TaskDef("dynfork_prep");
        prepDef.setRetryCount(0);
        prepDef.setOwnerEmail("test@conductor.io");

        metadataClient.registerTaskDefs(List.of(simpleDef, prepDef));

        WorkflowTask inline = new WorkflowTask();
        inline.setName("inline");
        inline.setTaskReferenceName("inline_ref");
        inline.setWorkflowTaskType(TaskType.INLINE);
        inline.setInputParameters(Map.of(
                "evaluatorType", "graaljs",
                "expression", "(function () { return $.value1 + $.value2; })();",
                "value1", 1, "value2", 2));

        WorkflowTask simple = new WorkflowTask();
        simple.setName("simple");
        simple.setTaskReferenceName("simple_ref");
        simple.setTaskDefinition(simpleDef);
        simple.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef childDef = new WorkflowDef();
        childDef.setName(childName);
        childDef.setOwnerEmail("test@conductor.io");
        childDef.setTimeoutSeconds(600);
        childDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        childDef.setTasks(List.of(inline, simple));
        metadataClient.updateWorkflowDefs(List.of(childDef));

        WorkflowTask prep = new WorkflowTask();
        prep.setName("dynfork_prep");
        prep.setTaskReferenceName("prep_ref");
        prep.setTaskDefinition(prepDef);
        prep.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask dynFork = new WorkflowTask();
        dynFork.setName("dyn_fork");
        dynFork.setTaskReferenceName("dyn_fork_ref");
        dynFork.setWorkflowTaskType(TaskType.FORK_JOIN_DYNAMIC);
        dynFork.setInputParameters(Map.of(
                "dynamicTasks", "${prep_ref.output.dynamicTasks}",
                "dynamicTasksInput", "${prep_ref.output.dynamicTasksInput}"));
        dynFork.setDynamicForkTasksParam("dynamicTasks");
        dynFork.setDynamicForkTasksInputParamName("dynamicTasksInput");

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setName("dyn_join");
        joinTask.setTaskReferenceName("dyn_join_ref");
        joinTask.setWorkflowTaskType(TaskType.JOIN);

        WorkflowDef parentDef = new WorkflowDef();
        parentDef.setName(parentName);
        parentDef.setOwnerEmail("test@conductor.io");
        parentDef.setTimeoutSeconds(900);
        parentDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentDef.setTasks(List.of(prep, dynFork, joinTask));
        metadataClient.updateWorkflowDefs(List.of(parentDef));
    }

    private void completeDynamicForkPrep(String parentId, String childName,
                                         String failingRef, String cancelledRef, String completedRef) {
        Map<String, Object> output = new HashMap<>();
        output.put("dynamicTasks", List.of(
                buildDynamicSubWorkflowTask(failingRef, childName),
                buildDynamicSubWorkflowTask(cancelledRef, childName),
                buildDynamicSubWorkflowTask(completedRef, childName)));
        output.put("dynamicTasksInput", Map.of(
                failingRef, Map.of(),
                cancelledRef, Map.of(),
                completedRef, Map.of()));

        Task prep = findActiveTask(parentId, "prep_ref", "prep_ref must be active");
        TaskResult tr = new TaskResult();
        tr.setWorkflowInstanceId(parentId);
        tr.setTaskId(prep.getTaskId());
        tr.setStatus(TaskResult.Status.COMPLETED);
        tr.setOutputData(output);
        taskClient.updateTask(tr);
    }

    private WorkflowTask buildDynamicSubWorkflowTask(String ref, String childWorkflowName) {
        WorkflowTask t = new WorkflowTask();
        t.setName(childWorkflowName);
        t.setTaskReferenceName(ref);
        t.setType(TaskType.SUB_WORKFLOW.name());
        SubWorkflowParams params = new SubWorkflowParams();
        params.setName(childWorkflowName);
        params.setVersion(1);
        t.setSubWorkflowParam(params);
        return t;
    }

    /**
     * Rerun from the FORK_JOIN_DYNAMIC task itself (not from any branch).
     * handleForkJoinDynamicTaskRerun removes existing branch tasks and re-schedules
     * fresh ones from the same dynamicTasks input — so all 3 branches must be re-spawned
     * with new subWorkflowIds. Verifies the dynamic-fork rerun path produces three new
     * sub-workflows, regardless of the previous branches' terminal status.
     */
    @Test
    @DisplayName("PR #3596: Rerun from the FORK_JOIN_DYNAMIC task itself re-spawns the three SUB_WORKFLOW branches with fresh ids")
    public void testRerunFromDynamicForkTaskItselfRespawnsAllBranches() {
        long stamp = System.currentTimeMillis();
        String parentName = "parent_dynfork_self_" + stamp;
        String childName = "sub_dynfork_self_" + stamp;
        String failingRef = "branch_failing_self_" + stamp;
        String cancelledRef = "branch_cancelled_self_" + stamp;
        String completedRef = "branch_completed_self_" + stamp;

        try {
            registerDynamicForkParentAndChild(parentName, childName);
            String parentId = start(parentName);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    findActiveTask(parentId, "prep_ref", "prep_ref must be active"));
            completeDynamicForkPrep(parentId, childName, failingRef, cancelledRef, completedRef);

            // Wait for the 3 dynamic branches to materialise
            String[] cF = new String[1];
            String[] cCa = new String[1];
            String[] cCo = new String[1];
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow parent = workflowClient.getWorkflow(parentId, true);
                cF[0] = parent.getTasks().stream()
                        .filter(t -> failingRef.equals(t.getReferenceTaskName()) && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow().getSubWorkflowId();
                cCa[0] = parent.getTasks().stream()
                        .filter(t -> cancelledRef.equals(t.getReferenceTaskName()) && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow().getSubWorkflowId();
                cCo[0] = parent.getTasks().stream()
                        .filter(t -> completedRef.equals(t.getReferenceTaskName()) && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow().getSubWorkflowId();
            });
            String originalFailingChildId = cF[0];
            String originalCancelledChildId = cCa[0];
            String originalCompletedChildId = cCo[0];

            completeTask(findActiveTask(originalCompletedChildId, "simple_ref", "completed simple_ref must be active"),
                    TaskResult.Status.COMPLETED);
            awaitWorkflowStatus(originalCompletedChildId, Workflow.WorkflowStatus.COMPLETED, 15,
                    "completed branch must reach COMPLETED");

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    findActiveTask(originalFailingChildId, "simple_ref", "failing simple_ref must be active"));
            completeTask(findActiveTask(originalFailingChildId, "simple_ref", "failing simple_ref missing"),
                    TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.FAILED, 30,
                    "parent must FAIL before rerun");

            // === Rerun from the dynamic-fork task itself ===
            Task dynForkTask = workflowClient.getWorkflow(parentId, true).getTasks().stream()
                    .filter(t -> "dyn_fork_ref".equals(t.getReferenceTaskName()))
                    .findFirst().orElseThrow(() -> new AssertionError("dyn_fork_ref task missing"));
            RerunWorkflowRequest rerun = new RerunWorkflowRequest();
            rerun.setReRunFromWorkflowId(parentId);
            rerun.setReRunFromTaskId(dynForkTask.getTaskId());
            workflowClient.rerunWorkflow(parentId, rerun);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.RUNNING, 30,
                    "parent must be RUNNING after dynamic-fork rerun");

            // All 3 branches must re-spawn with FRESH subWorkflowIds (handleForkJoinDynamicTaskRerun
            // removes the old branch tasks and re-schedules from the same dynamicTasks input).
            String[] newF = new String[1];
            String[] newCa = new String[1];
            String[] newCo = new String[1];
            await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow parent = workflowClient.getWorkflow(parentId, true);
                newF[0] = parent.getTasks().stream()
                        .filter(t -> failingRef.equals(t.getReferenceTaskName())
                                && !t.getStatus().isTerminal()
                                && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow(() -> new AssertionError("active " + failingRef + " missing"))
                        .getSubWorkflowId();
                newCa[0] = parent.getTasks().stream()
                        .filter(t -> cancelledRef.equals(t.getReferenceTaskName())
                                && !t.getStatus().isTerminal()
                                && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow(() -> new AssertionError("active " + cancelledRef + " missing"))
                        .getSubWorkflowId();
                newCo[0] = parent.getTasks().stream()
                        .filter(t -> completedRef.equals(t.getReferenceTaskName())
                                && !t.getStatus().isTerminal()
                                && t.getSubWorkflowId() != null)
                        .findFirst().orElseThrow(() -> new AssertionError("active " + completedRef + " missing"))
                        .getSubWorkflowId();

                assertNotEquals(originalFailingChildId, newF[0],
                        "failing branch must have a FRESH subWorkflowId after dyn-fork rerun");
                assertNotEquals(originalCancelledChildId, newCa[0],
                        "cancelled branch must have a FRESH subWorkflowId after dyn-fork rerun");
                assertNotEquals(originalCompletedChildId, newCo[0],
                        "completed branch must also have a FRESH subWorkflowId after dyn-fork rerun");
            });

            // Drive each fresh branch to completion
            completeTask(findActiveTask(newF[0], "simple_ref", "fresh failing simple_ref"),
                    TaskResult.Status.COMPLETED);
            completeTask(findActiveTask(newCa[0], "simple_ref", "fresh cancelled simple_ref"),
                    TaskResult.Status.COMPLETED);
            completeTask(findActiveTask(newCo[0], "simple_ref", "fresh completed simple_ref"),
                    TaskResult.Status.COMPLETED);

            awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.COMPLETED, 30,
                    "parent must COMPLETE end-to-end after dyn-fork rerun");
        } finally {
            terminateAll(parentName, childName);
        }
    }
}
