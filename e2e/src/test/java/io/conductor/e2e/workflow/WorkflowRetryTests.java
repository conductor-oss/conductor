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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
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

    /**
     * Uniform ceiling for every "await async workflow/task state" poll in this suite. All such
     * awaits are positive "eventually reaches X" conditions, so a generous ceiling is free on
     * passing runs (awaitility returns the instant the condition holds) and only adds headroom when
     * the WorkflowSweeper-paced child→parent completion cascade is slow under CI load. The ported
     * tests originally used a grab-bag of 3s–33s literals; the tight ones flaked under load (e.g.
     * child→parent FAILED/COMPLETED propagation goes through the async DECIDER_QUEUE, not a
     * synchronous path). Route every state-poll timeout through this one knob.
     */
    private static final int WF_AWAIT_SECS = 60;

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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(workflowId, true);
                            assertFalse(workflow1.getTasks().size() < 2);
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        // Fail the simple task
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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

        // Fail the simple task
        String subworkflowId = awaitSubWorkflowId(workflowId);
        String taskId = awaitTaskId(subworkflowId, 0);
        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Wait for parent workflow to get failed
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        taskId = awaitTaskId(subworkflowId, 1);

        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(subworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        // Fail the simple task
        String newSubworkflowId = awaitSubWorkflowId(newWorkflowId);
        taskId = awaitTaskId(newSubworkflowId, 0);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(newSubworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskClient.updateTask(taskResult);

        // Wait for parent workflow to get failed
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow1 = workflowClient.getWorkflow(newWorkflowId, false);
                            assertEquals(
                                    workflow1.getStatus().name(),
                                    Workflow.WorkflowStatus.RUNNING.name());
                        });

        newSubworkflowId = awaitSubWorkflowId(newWorkflowId);
        taskId = awaitTaskId(newSubworkflowId, 1);
        taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(newSubworkflowId);
        taskResult.setTaskId(taskId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
                await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
                await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
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

    private String awaitSubWorkflowId(String workflowId) {
        final String[] subWorkflowId = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertFalse(workflow.getTasks().isEmpty());
                            subWorkflowId[0] = workflow.getTasks().get(0).getSubWorkflowId();
                            assertNotNull(subWorkflowId[0]);
                            assertFalse(subWorkflowId[0].isBlank());
                        });
        return subWorkflowId[0];
    }

    private String awaitTaskId(String workflowId, int taskIndex) {
        final String[] taskId = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertTrue(workflow.getTasks().size() > taskIndex);
                            taskId[0] = workflow.getTasks().get(taskIndex).getTaskId();
                            assertNotNull(taskId[0]);
                            assertFalse(taskId[0].isBlank());
                        });
        return taskId[0];
    }

    // ==========================================================================
    // PR #3596: retry with resumeSubworkflowTasks=true
    //
    // Shape used in the parameterized test:
    //
    //   parent
    //     FORK(
    //       failing_sub_<status>:    SUB_WORKFLOW(failingChild)
    //         failingChild: A -> B -> C  (all SIMPLE)
    //       cancelled_sub_<status>:  SUB_WORKFLOW(cancelledChild)
    //         cancelledChild: D (SIMPLE)
    //     ) -> JOIN
    //
    // ==========================================================================

    public enum InnerFailStatus {
        FAILED(TaskResult.Status.FAILED),
        FAILED_WITH_TERMINAL_ERROR(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);

        final TaskResult.Status status;

        InnerFailStatus(TaskResult.Status s) {
            this.status = s;
        }
    }

    @ParameterizedTest(name = "innerFailStatus={0}")
    @EnumSource(InnerFailStatus.class)
    @DisplayName(
            "Retry parent with resumeSubworkflowTasks=true retries failed inner task in place across unsuccessful statuses")
    public void testRetryWithResumeFlagInForkPlusSubWorkflowAcrossFailedStatuses(
            InnerFailStatus failStatus) {
        long stamp = System.currentTimeMillis();
        String parentName = "retry-resume-fork-parent-" + failStatus + "-" + stamp;
        String failingChildName = "retry-resume-fork-failing-" + failStatus + "-" + stamp;
        String cancelledChildName = "retry-resume-fork-cancelled-" + failStatus + "-" + stamp;
        String taskARef = "task_a_" + failStatus + "_" + stamp;
        String taskBRef = "task_b_" + failStatus + "_" + stamp;
        String taskCRef = "task_c_" + failStatus + "_" + stamp;
        String taskDRef = "task_d_" + failStatus + "_" + stamp;
        String failingBranchRef = "failing_sub_" + failStatus + "_" + stamp;
        String cancelledBranchRef = "cancelled_sub_" + failStatus + "_" + stamp;

        terminateExistingRunningWorkflows(parentName);

        registerChildWithSequentialSimpleTasks(
                failingChildName, List.of(taskARef, taskBRef, taskCRef));
        registerChildWithSequentialSimpleTasks(cancelledChildName, List.of(taskDRef));
        registerParentForkWithTwoSubWorkflowBranches(
                parentName,
                failingBranchRef,
                failingChildName,
                cancelledBranchRef,
                cancelledChildName);

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentName);
        req.setVersion(1);
        String parentId = workflowClient.startWorkflow(req);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(parentId, true);
                            assertNotNull(
                                    findTaskByRef(wf, failingBranchRef).getSubWorkflowId(),
                                    "failing branch child must be scheduled");
                            assertNotNull(
                                    findTaskByRef(wf, cancelledBranchRef).getSubWorkflowId(),
                                    "cancelled branch child must be scheduled");
                        });
        Workflow parentSnapshot = workflowClient.getWorkflow(parentId, true);
        String failingChildId = findTaskByRef(parentSnapshot, failingBranchRef).getSubWorkflowId();
        String originalCancelledChildId =
                findTaskByRef(parentSnapshot, cancelledBranchRef).getSubWorkflowId();

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        findActiveTaskByRef(failingChildId, taskARef),
                                        "task A must be active before we complete it"));
        completeTaskWithStatus(
                failingChildId,
                findActiveTaskByRef(failingChildId, taskARef).getTaskId(),
                TaskResult.Status.COMPLETED);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        Task.Status.COMPLETED,
                                        statusOf(failingChildId, taskARef),
                                        "task A must reach COMPLETED before driving B to failure"));
        Task originalA = findTaskByRef(workflowClient.getWorkflow(failingChildId, true), taskARef);
        String originalATaskId = originalA.getTaskId();
        long originalAEndTime = originalA.getEndTime();

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        findActiveTaskByRef(failingChildId, taskBRef),
                                        "task B must become active after A completes"));
        completeTaskWithStatus(
                failingChildId,
                findActiveTaskByRef(failingChildId, taskBRef).getTaskId(),
                failStatus.status);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentId, true);
                            assertEquals(
                                    Workflow.WorkflowStatus.FAILED,
                                    parent.getStatus(),
                                    "parent should be FAILED after inner task " + failStatus);
                            assertEquals(
                                    Task.Status.CANCELED,
                                    findTaskByRef(parent, cancelledBranchRef).getStatus(),
                                    "cancelled sibling SUB_WORKFLOW task should be CANCELED");
                            assertNotNull(
                                    findTaskByRef(parent, failingBranchRef)
                                            .getReasonForIncompletion(),
                                    "failing branch should carry reasonForIncompletion while FAILED");
                        });

        workflowClient.retryLastFailedTask(parentId, true);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        Workflow.WorkflowStatus.RUNNING,
                                        workflowClient.getWorkflow(parentId, false).getStatus(),
                                        "parent should be RUNNING after retry-with-resume"));

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentId, true);

                            Task failingBranchNow = findTaskByRef(parent, failingBranchRef);
                            assertEquals(
                                    failingChildId,
                                    failingBranchNow.getSubWorkflowId(),
                                    "failing branch subWorkflowId must be PRESERVED (retry-in-place via resume flag)");
                            assertNull(
                                    failingBranchNow.getReasonForIncompletion(),
                                    "failing branch reasonForIncompletion must be cleared");

                            Task cancelledBranchNow = findTaskByRef(parent, cancelledBranchRef);
                            assertEquals(
                                    originalCancelledChildId,
                                    cancelledBranchNow.getSubWorkflowId(),
                                    "cancelled branch subWorkflowId must be PRESERVED (cancelled-sibling fix)");
                            assertNull(
                                    cancelledBranchNow.getReasonForIncompletion(),
                                    "cancelled branch reasonForIncompletion must be cleared");

                            Workflow failingChild =
                                    workflowClient.getWorkflow(failingChildId, true);
                            assertFalse(
                                    failingChild.getStatus().isTerminal(),
                                    "resumed failing child must be non-terminal, got "
                                            + failingChild.getStatus());

                            Task aAfterRetry =
                                    failingChild.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            taskARef.equals(
                                                                            t
                                                                                    .getReferenceTaskName())
                                                                    && t.getStatus()
                                                                            == Task.Status
                                                                                    .COMPLETED)
                                            .findFirst()
                                            .orElseThrow(
                                                    () ->
                                                            new AssertionError(
                                                                    "task A must remain COMPLETED after retry — resume=true must preserve completed siblings"));
                            assertEquals(
                                    originalATaskId,
                                    aAfterRetry.getTaskId(),
                                    "task A taskId must be UNCHANGED — proves it was not re-executed");
                            assertEquals(
                                    originalAEndTime,
                                    aAfterRetry.getEndTime(),
                                    "task A endTime must be UNCHANGED — proves it was not re-executed");

                            assertNotNull(
                                    findActiveTaskByRef(failingChildId, taskBRef),
                                    "task B must have an active fresh attempt after retry");
                        });

        completeTaskWithStatus(
                failingChildId,
                findActiveTaskByRef(failingChildId, taskBRef).getTaskId(),
                TaskResult.Status.COMPLETED);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        findActiveTaskByRef(failingChildId, taskCRef),
                                        "task C should be active after B completes"));
        completeTaskWithStatus(
                failingChildId,
                findActiveTaskByRef(failingChildId, taskCRef).getTaskId(),
                TaskResult.Status.COMPLETED);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        findActiveTaskByRef(originalCancelledChildId, taskDRef),
                                        "task D should be active in resumed cancelled child"));
        completeTaskWithStatus(
                originalCancelledChildId,
                findActiveTaskByRef(originalCancelledChildId, taskDRef).getTaskId(),
                TaskResult.Status.COMPLETED);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        Workflow.WorkflowStatus.COMPLETED,
                                        workflowClient.getWorkflow(parentId, false).getStatus(),
                                        "parent should complete end-to-end after retry-with-resume"));
    }

    private void registerChildWithSequentialSimpleTasks(String childName, List<String> taskRefs) {
        List<WorkflowTask> tasks = new ArrayList<>();
        List<TaskDef> taskDefs = new ArrayList<>();
        for (String ref : taskRefs) {
            TaskDef td = new TaskDef(ref);
            td.setRetryCount(0);
            td.setOwnerEmail("test@conductor.io");
            taskDefs.add(td);

            WorkflowTask wt = new WorkflowTask();
            wt.setName(ref);
            wt.setTaskReferenceName(ref);
            wt.setTaskDefinition(td);
            wt.setWorkflowTaskType(TaskType.SIMPLE);
            tasks.add(wt);
        }
        metadataClient.registerTaskDefs(taskDefs);

        WorkflowDef def = new WorkflowDef();
        def.setName(childName);
        def.setOwnerEmail("test@conductor.io");
        def.setTimeoutSeconds(600);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTasks(tasks);
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private void registerParentForkWithTwoSubWorkflowBranches(
            String parentName,
            String branchARef,
            String childAName,
            String branchBRef,
            String childBName) {
        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork_" + parentName);
        fork.setTaskReferenceName("fork_" + parentName);
        fork.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork.setForkTasks(
                List.of(
                        List.of(buildSubWorkflowTask(branchARef, childAName)),
                        List.of(buildSubWorkflowTask(branchBRef, childBName))));

        WorkflowTask join = new WorkflowTask();
        join.setName("join_" + parentName);
        join.setTaskReferenceName("join_" + parentName);
        join.setWorkflowTaskType(TaskType.JOIN);
        join.setJoinOn(List.of(branchARef, branchBRef));

        WorkflowDef def = new WorkflowDef();
        def.setName(parentName);
        def.setOwnerEmail("test@conductor.io");
        def.setTimeoutSeconds(900);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTasks(List.of(fork, join));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private WorkflowTask buildSubWorkflowTask(String ref, String childWorkflowName) {
        WorkflowTask t = new WorkflowTask();
        t.setName(ref);
        t.setTaskReferenceName(ref);
        t.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams params = new SubWorkflowParams();
        params.setName(childWorkflowName);
        params.setVersion(1);
        t.setSubWorkflowParam(params);
        return t;
    }

    private Task findTaskByRef(Workflow wf, String ref) {
        return wf.getTasks().stream()
                .filter(t -> ref.equals(t.getReferenceTaskName()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "task "
                                                + ref
                                                + " not found in workflow "
                                                + wf.getWorkflowId()));
    }

    private Task findActiveTaskByRef(String workflowId, String ref) {
        return workflowClient.getWorkflow(workflowId, true).getTasks().stream()
                .filter(t -> ref.equals(t.getReferenceTaskName()) && !t.getStatus().isTerminal())
                .findFirst()
                .orElse(null);
    }

    private Task.Status statusOf(String workflowId, String ref) {
        return findTaskByRef(workflowClient.getWorkflow(workflowId, true), ref).getStatus();
    }

    private void completeTaskWithStatus(
            String workflowId, String taskId, TaskResult.Status status) {
        TaskResult tr = new TaskResult();
        tr.setWorkflowInstanceId(workflowId);
        tr.setTaskId(taskId);
        tr.setStatus(status);
        if (status == TaskResult.Status.FAILED
                || status == TaskResult.Status.FAILED_WITH_TERMINAL_ERROR) {
            tr.setReasonForIncompletion("test-driven " + status);
        }
        taskClient.updateTask(tr);
    }

    /**
     * Exact-shape test: parent v1 = DO_WHILE(number=2) { FORK[sub_workflow_ref_1, sub_workflow_ref]
     * -> JOIN }, sub v1 = INLINE inline_ref -> SIMPLE simple_ref. Iter 1 succeeds end-to-end, iter
     * 2 branchA SIMPLE fails. Retry parent with resumeSubworkflowTasks=true. Asserts: - iter 1
     * untouched (workflow + inline/simple taskIds + endTimes + retryCount) - iter 2 branchB INLINE
     * that was COMPLETED before failure stays the same instance - iter 2 branchB SIMPLE restored in
     * place: exactly ONE non-terminal instance, no retried=true clone - parent reaches COMPLETED
     * end-to-end
     */
    @Test
    @DisplayName(
            "Retry with resumeSubworkflowTasks=true on exact prod shape: in-place reset on cancelled sibling, iter 1 untouched")
    public void testRetryWithResumeFlagSecondIterationFailureInForkInsideDoWhile() {
        long stamp = System.currentTimeMillis();
        String parentName = "parent_" + stamp;
        String childName = "sub_" + stamp;
        terminateExistingRunningWorkflows(parentName);

        registerExactShapeParentAndChild(parentName, childName);

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentName);
        req.setVersion(1);
        String parentId = workflowClient.startWorkflow(req);

        String[] iter1A = new String[1];
        String[] iter1B = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            iter1A[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref_1", 1);
                            iter1B[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref", 1);
                        });
        completeActiveSimpleRef(iter1A[0], TaskResult.Status.COMPLETED);
        completeActiveSimpleRef(iter1B[0], TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(iter1A[0], Workflow.WorkflowStatus.COMPLETED);
        awaitWorkflowStatus(iter1B[0], Workflow.WorkflowStatus.COMPLETED);

        Iter1Snapshot snapA = snapshotIter1Child(iter1A[0]);
        Iter1Snapshot snapB = snapshotIter1Child(iter1B[0]);

        String[] iter2A = new String[1];
        String[] iter2B = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            iter2A[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref_1", 2);
                            iter2B[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref", 2);
                        });
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        Task.Status.COMPLETED,
                                        onlyTaskByRef(iter2B[0], "inline_ref").getStatus()));
        Task iter2BInlineBefore = onlyTaskByRef(iter2B[0], "inline_ref");

        completeActiveSimpleRef(iter2A[0], TaskResult.Status.FAILED);
        awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.FAILED);

        workflowClient.retryLastFailedTask(parentId, true);
        awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.RUNNING);

        assertIter1Unchanged(iter1A[0], snapA);
        assertIter1Unchanged(iter1B[0], snapB);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Task inlineAfter = onlyTaskByRef(iter2B[0], "inline_ref");
                            assertEquals(
                                    Task.Status.COMPLETED,
                                    inlineAfter.getStatus(),
                                    "iter 2 branchB inline must remain COMPLETED");
                            assertEquals(
                                    iter2BInlineBefore.getTaskId(),
                                    inlineAfter.getTaskId(),
                                    "iter 2 branchB inline taskId unchanged");
                            assertEquals(
                                    iter2BInlineBefore.getEndTime(),
                                    inlineAfter.getEndTime(),
                                    "iter 2 branchB inline endTime unchanged");
                        });

        completeActiveSimpleRef(iter2A[0], TaskResult.Status.COMPLETED);
        completeActiveSimpleRef(iter2B[0], TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.COMPLETED);
    }

    private void registerExactShapeParentAndChild(String parentName, String childName) {
        TaskDef simpleDef = new TaskDef("simple");
        simpleDef.setRetryCount(0);
        simpleDef.setOwnerEmail("test@conductor.io");
        metadataClient.registerTaskDefs(List.of(simpleDef));

        WorkflowTask inline = new WorkflowTask();
        inline.setName("inline");
        inline.setTaskReferenceName("inline_ref");
        inline.setWorkflowTaskType(TaskType.INLINE);
        inline.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "expression",
                        "(function () { return $.value1 + $.value2; })();",
                        "value1",
                        1,
                        "value2",
                        2));

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

        WorkflowTask branchA = buildSubWorkflowTask("sub_workflow_ref_1", childName);
        branchA.setName("sub_workflow_1");
        WorkflowTask branchB = buildSubWorkflowTask("sub_workflow_ref", childName);
        branchB.setName("sub_workflow");

        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork_ref");
        fork.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork.setForkTasks(List.of(List.of(branchA), List.of(branchB)));

        WorkflowTask join = new WorkflowTask();
        join.setName("join");
        join.setTaskReferenceName("join_ref");
        join.setWorkflowTaskType(TaskType.JOIN);

        WorkflowTask dw = new WorkflowTask();
        dw.setName("do_while");
        dw.setTaskReferenceName("do_while_ref");
        dw.setWorkflowTaskType(TaskType.DO_WHILE);
        dw.setInputParameters(Map.of("number", 2));
        dw.setLoopCondition(
                "(function () { if ($.do_while_ref['iteration'] < $.number) { return true; } return false; })();");
        dw.setLoopOver(List.of(fork, join));

        WorkflowDef parentDef = new WorkflowDef();
        parentDef.setName(parentName);
        parentDef.setOwnerEmail("test@conductor.io");
        parentDef.setTimeoutSeconds(900);
        parentDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentDef.setTasks(List.of(dw));
        metadataClient.updateWorkflowDefs(List.of(parentDef));
    }

    private String subWorkflowIdAtIteration(String parentId, String branchRef, int iteration) {
        String suffix = "__" + iteration;
        Task t =
                workflowClient.getWorkflow(parentId, true).getTasks().stream()
                        .filter(
                                x ->
                                        (branchRef + suffix).equals(x.getReferenceTaskName())
                                                && x.getSubWorkflowId() != null)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                branchRef + suffix + " not materialised yet"));
        return t.getSubWorkflowId();
    }

    private void completeActiveSimpleRef(String childId, TaskResult.Status status) {
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        findActiveTaskByRef(childId, "simple_ref"),
                                        "simple_ref must be active in " + childId));
        completeTaskWithStatus(
                childId, findActiveTaskByRef(childId, "simple_ref").getTaskId(), status);
    }

    private Task onlyTaskByRef(String workflowId, String ref) {
        List<Task> matches =
                workflowClient.getWorkflow(workflowId, true).getTasks().stream()
                        .filter(t -> ref.equals(t.getReferenceTaskName()))
                        .collect(Collectors.toList());
        assertEquals(
                1,
                matches.size(),
                "expected exactly one " + ref + " in " + workflowId + ", got " + matches.size());
        return matches.get(0);
    }

    private void awaitWorkflowStatus(String workflowId, Workflow.WorkflowStatus expected) {
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        expected,
                                        workflowClient.getWorkflow(workflowId, false).getStatus(),
                                        workflowId + " must reach " + expected));
    }

    private static final class Iter1Snapshot {
        final long workflowEndTime;
        final String inlineTaskId;
        final long inlineEndTime;
        final String simpleTaskId;
        final long simpleEndTime;
        final int simpleRetryCount;

        Iter1Snapshot(long w, String ii, long ie, String si, long se, int sr) {
            workflowEndTime = w;
            inlineTaskId = ii;
            inlineEndTime = ie;
            simpleTaskId = si;
            simpleEndTime = se;
            simpleRetryCount = sr;
        }
    }

    private Iter1Snapshot snapshotIter1Child(String childId) {
        Workflow wf = workflowClient.getWorkflow(childId, true);
        Task inline =
                wf.getTasks().stream()
                        .filter(t -> "inline_ref".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElseThrow();
        Task simple =
                wf.getTasks().stream()
                        .filter(t -> "simple_ref".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElseThrow();
        return new Iter1Snapshot(
                wf.getEndTime(),
                inline.getTaskId(),
                inline.getEndTime(),
                simple.getTaskId(),
                simple.getEndTime(),
                simple.getRetryCount());
    }

    private void assertIter1Unchanged(String childId, Iter1Snapshot before) {
        Workflow wf = workflowClient.getWorkflow(childId, true);
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                wf.getStatus(),
                childId + " must stay COMPLETED");
        assertEquals(before.workflowEndTime, wf.getEndTime(), childId + " endTime unchanged");
        Task inline = onlyTaskByRef(childId, "inline_ref");
        Task simple = onlyTaskByRef(childId, "simple_ref");
        assertEquals(before.inlineTaskId, inline.getTaskId(), childId + " inline taskId unchanged");
        assertEquals(
                before.inlineEndTime, inline.getEndTime(), childId + " inline endTime unchanged");
        assertEquals(before.simpleTaskId, simple.getTaskId(), childId + " simple taskId unchanged");
        assertEquals(
                before.simpleEndTime, simple.getEndTime(), childId + " simple endTime unchanged");
        assertEquals(
                before.simpleRetryCount,
                simple.getRetryCount(),
                childId + " simple retryCount unchanged");
    }

    /**
     * 3-branch FORK inside a single-iteration DO_WHILE. Drive iter 1 so one branch COMPLETES, one
     * FAILS with FAILED_WITH_TERMINAL_ERROR, and one is CANCELED by the fork. Retry WITHOUT
     * resumeSubworkflowTasks: assert the FAILED + CANCELED branches get FRESH subWorkflowIds
     * (taskToBeRescheduled spawn-fresh path), the COMPLETED branch is untouched.
     */
    @Test
    @DisplayName(
            "Retry without resumeSubworkflowTasks: fresh children for unsuccessful SUB_WORKFLOW branches, COMPLETED branch untouched")
    public void testRetryWithoutResumeFlagSpawnsFreshChildrenForUnsuccessfulSubWorkflowSiblings() {
        Captured c = setupThreeBranchScenarioAndFail();

        workflowClient.retryWorkflow(List.of(c.parentId));
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.RUNNING);

        String[] newFailingChildHolder = new String[1];
        String[] newCancelledChildHolder = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertEquals(
                                    c.childCompleted,
                                    currentSubWorkflowId(
                                            c.parentId,
                                            "sub_workflow_ref_2__1",
                                            Task.Status.COMPLETED),
                                    "COMPLETED branch must keep its original subWorkflowId");
                            String activeFailingChild =
                                    activeSubWorkflowId(c.parentId, "sub_workflow_ref__1");
                            String activeCancelledChild =
                                    activeSubWorkflowId(c.parentId, "sub_workflow_ref_1__1");
                            assertNotNull(
                                    activeFailingChild,
                                    "sweeper must assign a fresh child id for the FAILED branch");
                            assertNotNull(
                                    activeCancelledChild,
                                    "sweeper must assign a fresh child id for the CANCELED branch");
                            assertNotEquals(
                                    c.childFailing,
                                    activeFailingChild,
                                    "FAILED branch must have a NEW subWorkflowId (no resume flag)");
                            assertNotEquals(
                                    c.childCancelled,
                                    activeCancelledChild,
                                    "CANCELED branch must have a NEW subWorkflowId (no resume flag)");
                            assertEquals(
                                    Workflow.WorkflowStatus.FAILED,
                                    workflowClient.getWorkflow(c.childFailing, false).getStatus(),
                                    "original failing child must stay FAILED");
                            assertTrue(
                                    workflowClient
                                            .getWorkflow(c.childCancelled, false)
                                            .getStatus()
                                            .isTerminal(),
                                    "original cancelled child must stay terminal");
                            newFailingChildHolder[0] = activeFailingChild;
                            newCancelledChildHolder[0] = activeCancelledChild;
                        });

        String newFailingChild = newFailingChildHolder[0];
        String newCancelledChild = newCancelledChildHolder[0];
        completeActiveSimpleRef(newFailingChild, TaskResult.Status.COMPLETED);
        completeActiveSimpleRef(newCancelledChild, TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.COMPLETED);
    }

    /**
     * Same 3-branch setup. Retry WITH resumeSubworkflowTasks=true: assert the FAILED + CANCELED
     * branches keep their original subWorkflowId (in-place restore via walk-up + cancelled-sibling
     * fix), and the COMPLETED branch is untouched.
     */
    @Test
    @DisplayName(
            "Retry with resumeSubworkflowTasks=true: in-place restore of unsuccessful SUB_WORKFLOW branches, COMPLETED branch untouched")
    public void testRetryWithResumeFlagRestoresUnsuccessfulSubWorkflowSiblingsInPlace() {
        Captured c = setupThreeBranchScenarioAndFail();

        workflowClient.retryLastFailedTask(c.parentId, true);
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.RUNNING);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertEquals(
                                    c.childCompleted,
                                    currentSubWorkflowId(
                                            c.parentId,
                                            "sub_workflow_ref_2__1",
                                            Task.Status.COMPLETED),
                                    "COMPLETED branch must keep its original subWorkflowId");
                            assertEquals(
                                    c.childFailing,
                                    activeSubWorkflowId(c.parentId, "sub_workflow_ref__1"),
                                    "FAILED branch subWorkflowId must be PRESERVED (resume flag)");
                            assertEquals(
                                    c.childCancelled,
                                    activeSubWorkflowId(c.parentId, "sub_workflow_ref_1__1"),
                                    "CANCELED branch subWorkflowId must be PRESERVED (resume flag)");
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(c.childFailing, false)
                                            .getStatus()
                                            .isTerminal(),
                                    "original failing child must be restored to non-terminal");
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(c.childCancelled, false)
                                            .getStatus()
                                            .isTerminal(),
                                    "original cancelled child must be restored to non-terminal");

                            assertInlinePreserved(
                                    c.childFailing,
                                    c.inlineFailingTaskId,
                                    c.inlineFailingEndTime,
                                    "failing branch child");
                            assertInlinePreserved(
                                    c.childCancelled,
                                    c.inlineCancelledTaskId,
                                    c.inlineCancelledEndTime,
                                    "cancelled branch child");
                            assertInlinePreserved(
                                    c.childCompleted,
                                    c.inlineCompletedTaskId,
                                    c.inlineCompletedEndTime,
                                    "completed branch child");
                        });

        completeActiveSimpleRef(c.childFailing, TaskResult.Status.COMPLETED);
        completeActiveSimpleRef(c.childCancelled, TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.COMPLETED);
    }

    private void assertInlinePreserved(
            String childId, String expectedTaskId, long expectedEndTime, String label) {
        Task inline = onlyTaskByRef(childId, "inline_ref");
        assertEquals(
                Task.Status.COMPLETED,
                inline.getStatus(),
                label + " inline_ref must remain COMPLETED after retry");
        assertEquals(
                expectedTaskId,
                inline.getTaskId(),
                label + " inline_ref taskId must be UNCHANGED (no re-execution)");
        assertEquals(
                expectedEndTime,
                inline.getEndTime(),
                label + " inline_ref endTime must be UNCHANGED (no re-execution)");
    }

    private static final class Captured {
        final String parentId;
        final String childFailing;
        final String childCancelled;
        final String childCompleted;
        final String inlineFailingTaskId;
        final long inlineFailingEndTime;
        final String inlineCancelledTaskId;
        final long inlineCancelledEndTime;
        final String inlineCompletedTaskId;
        final long inlineCompletedEndTime;

        Captured(
                String p,
                String f,
                String c1,
                String c2,
                String ifId,
                long ifEnd,
                String icId,
                long icEnd,
                String icompId,
                long icompEnd) {
            parentId = p;
            childFailing = f;
            childCancelled = c1;
            childCompleted = c2;
            inlineFailingTaskId = ifId;
            inlineFailingEndTime = ifEnd;
            inlineCancelledTaskId = icId;
            inlineCancelledEndTime = icEnd;
            inlineCompletedTaskId = icompId;
            inlineCompletedEndTime = icompEnd;
        }
    }

    private Captured setupThreeBranchScenarioAndFail() {
        long stamp = System.currentTimeMillis();
        String parentName = "parent_3br_" + stamp;
        String childName = "sub_3br_" + stamp;
        terminateExistingRunningWorkflows(parentName);
        registerExactShapeParentAndChildWithThreeBranches(parentName, childName);

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentName);
        req.setVersion(1);
        String parentId = workflowClient.startWorkflow(req);

        String[] cA = new String[1];
        String[] cB = new String[1];
        String[] cC = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            cA[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref_1", 1);
                            cB[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref", 1);
                            cC[0] = subWorkflowIdAtIteration(parentId, "sub_workflow_ref_2", 1);
                        });

        completeActiveSimpleRef(cC[0], TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(cC[0], Workflow.WorkflowStatus.COMPLETED);

        completeActiveSimpleRef(cB[0], TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.FAILED);

        Task forkTaskNow =
                workflowClient.getWorkflow(parentId, true).getTasks().stream()
                        .filter(
                                t ->
                                        t.getReferenceTaskName() != null
                                                && t.getReferenceTaskName().startsWith("fork_"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("fork task not found in parent"));
        assertEquals(
                Task.Status.COMPLETED,
                forkTaskNow.getStatus(),
                "FORK_JOIN must stay COMPLETED on branch failure (does not propagate)");

        Task inlineF = onlyTaskByRef(cB[0], "inline_ref");
        Task inlineCa = onlyTaskByRef(cA[0], "inline_ref");
        Task inlineCo = onlyTaskByRef(cC[0], "inline_ref");

        return new Captured(
                parentId,
                cB[0],
                cA[0],
                cC[0],
                inlineF.getTaskId(),
                inlineF.getEndTime(),
                inlineCa.getTaskId(),
                inlineCa.getEndTime(),
                inlineCo.getTaskId(),
                inlineCo.getEndTime());
    }

    private void registerExactShapeParentAndChildWithThreeBranches(
            String parentName, String childName) {
        TaskDef simpleDef = new TaskDef("simple");
        simpleDef.setRetryCount(0);
        simpleDef.setOwnerEmail("test@conductor.io");
        metadataClient.registerTaskDefs(List.of(simpleDef));

        WorkflowTask inline = new WorkflowTask();
        inline.setName("inline");
        inline.setTaskReferenceName("inline_ref");
        inline.setWorkflowTaskType(TaskType.INLINE);
        inline.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "expression",
                        "(function () { return $.value1 + $.value2; })();",
                        "value1",
                        1,
                        "value2",
                        2));

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

        WorkflowTask branchA = buildSubWorkflowTask("sub_workflow_ref_1", childName);
        branchA.setName("sub_workflow_1");
        WorkflowTask branchB = buildSubWorkflowTask("sub_workflow_ref", childName);
        branchB.setName("sub_workflow");
        WorkflowTask branchC = buildSubWorkflowTask("sub_workflow_ref_2", childName);
        branchC.setName("sub_workflow_2");

        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork_ref");
        fork.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork.setForkTasks(List.of(List.of(branchA), List.of(branchB), List.of(branchC)));

        WorkflowTask join = new WorkflowTask();
        join.setName("join");
        join.setTaskReferenceName("join_ref");
        join.setWorkflowTaskType(TaskType.JOIN);

        WorkflowTask dw = new WorkflowTask();
        dw.setName("do_while");
        dw.setTaskReferenceName("do_while_ref");
        dw.setWorkflowTaskType(TaskType.DO_WHILE);
        dw.setInputParameters(Map.of("number", 1));
        dw.setLoopCondition(
                "(function () { if ($.do_while_ref['iteration'] < $.number) { return true; } return false; })();");
        dw.setLoopOver(List.of(fork, join));

        WorkflowDef parentDef = new WorkflowDef();
        parentDef.setName(parentName);
        parentDef.setOwnerEmail("test@conductor.io");
        parentDef.setTimeoutSeconds(900);
        parentDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentDef.setTasks(List.of(dw));
        metadataClient.updateWorkflowDefs(List.of(parentDef));
    }

    private String currentSubWorkflowId(
            String parentId, String exactRef, Task.Status expectedStatus) {
        return workflowClient.getWorkflow(parentId, true).getTasks().stream()
                .filter(
                        t ->
                                exactRef.equals(t.getReferenceTaskName())
                                        && t.getStatus() == expectedStatus)
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "no " + exactRef + " task with status " + expectedStatus))
                .getSubWorkflowId();
    }

    private String activeSubWorkflowId(String parentId, String exactRef) {
        return workflowClient.getWorkflow(parentId, true).getTasks().stream()
                .filter(
                        t ->
                                exactRef.equals(t.getReferenceTaskName())
                                        && !t.getStatus().isTerminal())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no active " + exactRef + " task"))
                .getSubWorkflowId();
    }

    /**
     * FORK_JOIN_DYNAMIC where all branches are SUB_WORKFLOW. One branch will be COMPLETED, one
     * FAILED_WITH_TERMINAL_ERROR, one CANCELED by the fork. Retry WITH resume flag must restore
     * both unsuccessful branches in place (subWorkflowId preserved).
     */
    @Test
    @DisplayName(
            "Retry with resumeSubworkflowTasks=true on FORK_JOIN_DYNAMIC: in-place restore of unsuccessful branches, COMPLETED branch untouched")
    public void testRetryWithResumeFlagInDynamicForkRestoresUnsuccessfulBranchesInPlace() {
        DynamicCaptured c = setupDynamicForkScenarioAndFail();

        workflowClient.retryLastFailedTask(c.parentId, true);
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.RUNNING);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertEquals(
                                    c.childCompleted,
                                    currentSubWorkflowId(
                                            c.parentId, c.completedRef, Task.Status.COMPLETED),
                                    "COMPLETED branch must keep its original subWorkflowId");
                            assertEquals(
                                    c.childFailing,
                                    activeSubWorkflowId(c.parentId, c.failingRef),
                                    "FAILED branch subWorkflowId must be PRESERVED (resume flag)");
                            assertEquals(
                                    c.childCancelled,
                                    activeSubWorkflowId(c.parentId, c.cancelledRef),
                                    "CANCELED branch subWorkflowId must be PRESERVED (resume flag)");
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(c.childFailing, false)
                                            .getStatus()
                                            .isTerminal(),
                                    "original failing child must be restored to non-terminal");
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(c.childCancelled, false)
                                            .getStatus()
                                            .isTerminal(),
                                    "original cancelled child must be restored to non-terminal");

                            assertInlinePreserved(
                                    c.childFailing,
                                    c.inlineFailingTaskId,
                                    c.inlineFailingEndTime,
                                    "failing branch child");
                            assertInlinePreserved(
                                    c.childCancelled,
                                    c.inlineCancelledTaskId,
                                    c.inlineCancelledEndTime,
                                    "cancelled branch child");
                            assertInlinePreserved(
                                    c.childCompleted,
                                    c.inlineCompletedTaskId,
                                    c.inlineCompletedEndTime,
                                    "completed branch child");
                        });

        completeActiveSimpleRef(c.childFailing, TaskResult.Status.COMPLETED);
        completeActiveSimpleRef(c.childCancelled, TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.COMPLETED);
    }

    /**
     * Same shape, retry WITHOUT the resume flag — taskToBeRescheduled nulls the SUB_WORKFLOW
     * siblings' subWorkflowId so each FAILED/CANCELED dynamic branch gets a fresh child.
     */
    @Test
    @DisplayName(
            "Retry without resumeSubworkflowTasks on FORK_JOIN_DYNAMIC: fresh children for unsuccessful branches, COMPLETED branch untouched")
    public void
            testRetryWithoutResumeFlagInDynamicForkSpawnsFreshChildrenForUnsuccessfulBranches() {
        DynamicCaptured c = setupDynamicForkScenarioAndFail();

        workflowClient.retryWorkflow(List.of(c.parentId));
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.RUNNING);

        String[] newDynFailingChildHolder = new String[1];
        String[] newDynCancelledChildHolder = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertEquals(
                                    c.childCompleted,
                                    currentSubWorkflowId(
                                            c.parentId, c.completedRef, Task.Status.COMPLETED),
                                    "COMPLETED branch must keep its original subWorkflowId");
                            String activeFailingChild =
                                    activeSubWorkflowId(c.parentId, c.failingRef);
                            String activeCancelledChild =
                                    activeSubWorkflowId(c.parentId, c.cancelledRef);
                            assertNotNull(
                                    activeFailingChild,
                                    "sweeper must assign a fresh child id for the FAILED branch");
                            assertNotNull(
                                    activeCancelledChild,
                                    "sweeper must assign a fresh child id for the CANCELED branch");
                            assertNotEquals(
                                    c.childFailing,
                                    activeFailingChild,
                                    "FAILED branch must have a NEW subWorkflowId (no resume flag)");
                            assertNotEquals(
                                    c.childCancelled,
                                    activeCancelledChild,
                                    "CANCELED branch must have a NEW subWorkflowId (no resume flag)");
                            newDynFailingChildHolder[0] = activeFailingChild;
                            newDynCancelledChildHolder[0] = activeCancelledChild;
                        });

        String newFailingChild = newDynFailingChildHolder[0];
        String newCancelledChild = newDynCancelledChildHolder[0];
        completeActiveSimpleRef(newFailingChild, TaskResult.Status.COMPLETED);
        completeActiveSimpleRef(newCancelledChild, TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.COMPLETED);
    }

    private static final class DynamicCaptured {
        final String parentId;
        final String failingRef;
        final String cancelledRef;
        final String completedRef;
        final String childFailing;
        final String childCancelled;
        final String childCompleted;
        final String inlineFailingTaskId;
        final long inlineFailingEndTime;
        final String inlineCancelledTaskId;
        final long inlineCancelledEndTime;
        final String inlineCompletedTaskId;
        final long inlineCompletedEndTime;

        DynamicCaptured(
                String p,
                String fr,
                String cr,
                String compr,
                String f,
                String c1,
                String c2,
                String ifId,
                long ifEnd,
                String icId,
                long icEnd,
                String icompId,
                long icompEnd) {
            parentId = p;
            failingRef = fr;
            cancelledRef = cr;
            completedRef = compr;
            childFailing = f;
            childCancelled = c1;
            childCompleted = c2;
            inlineFailingTaskId = ifId;
            inlineFailingEndTime = ifEnd;
            inlineCancelledTaskId = icId;
            inlineCancelledEndTime = icEnd;
            inlineCompletedTaskId = icompId;
            inlineCompletedEndTime = icompEnd;
        }
    }

    private DynamicCaptured setupDynamicForkScenarioAndFail() {
        long stamp = System.currentTimeMillis();
        String parentName = "parent_dynfork_" + stamp;
        String childName = "sub_dynfork_" + stamp;
        String failingRef = "branch_failing_" + stamp;
        String cancelledRef = "branch_cancelled_" + stamp;
        String completedRef = "branch_completed_" + stamp;

        terminateExistingRunningWorkflows(parentName);
        registerDynamicForkParentAndChild(parentName, childName);

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(parentName);
        req.setVersion(1);
        String parentId = workflowClient.startWorkflow(req);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        findActiveTaskByRef(parentId, "prep_ref"),
                                        "prep_ref must be active before driving dynamic fork"));
        completeDynamicForkPrep(parentId, childName, failingRef, cancelledRef, completedRef);

        String[] cF = new String[1];
        String[] cCa = new String[1];
        String[] cCo = new String[1];
        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentId, true);
                            cF[0] =
                                    parent.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            failingRef.equals(
                                                                            t
                                                                                    .getReferenceTaskName())
                                                                    && t.getSubWorkflowId() != null)
                                            .findFirst()
                                            .orElseThrow(
                                                    () ->
                                                            new AssertionError(
                                                                    failingRef
                                                                            + " not materialised"))
                                            .getSubWorkflowId();
                            cCa[0] =
                                    parent.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            cancelledRef.equals(
                                                                            t
                                                                                    .getReferenceTaskName())
                                                                    && t.getSubWorkflowId() != null)
                                            .findFirst()
                                            .orElseThrow(
                                                    () ->
                                                            new AssertionError(
                                                                    cancelledRef
                                                                            + " not materialised"))
                                            .getSubWorkflowId();
                            cCo[0] =
                                    parent.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            completedRef.equals(
                                                                            t
                                                                                    .getReferenceTaskName())
                                                                    && t.getSubWorkflowId() != null)
                                            .findFirst()
                                            .orElseThrow(
                                                    () ->
                                                            new AssertionError(
                                                                    completedRef
                                                                            + " not materialised"))
                                            .getSubWorkflowId();
                        });

        completeActiveSimpleRef(cCo[0], TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(cCo[0], Workflow.WorkflowStatus.COMPLETED);

        completeActiveSimpleRef(cF[0], TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        awaitWorkflowStatus(parentId, Workflow.WorkflowStatus.FAILED);

        Task dynForkNow = onlyTaskByRef(parentId, "dyn_fork_ref");
        assertEquals(
                Task.Status.COMPLETED,
                dynForkNow.getStatus(),
                "FORK_JOIN_DYNAMIC must stay COMPLETED on branch failure (does not propagate)");

        Task inlineF = onlyTaskByRef(cF[0], "inline_ref");
        Task inlineCa = onlyTaskByRef(cCa[0], "inline_ref");
        Task inlineCo = onlyTaskByRef(cCo[0], "inline_ref");

        return new DynamicCaptured(
                parentId,
                failingRef,
                cancelledRef,
                completedRef,
                cF[0],
                cCa[0],
                cCo[0],
                inlineF.getTaskId(),
                inlineF.getEndTime(),
                inlineCa.getTaskId(),
                inlineCa.getEndTime(),
                inlineCo.getTaskId(),
                inlineCo.getEndTime());
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
        inline.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "expression",
                        "(function () { return $.value1 + $.value2; })();",
                        "value1",
                        1,
                        "value2",
                        2));

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
        dynFork.setInputParameters(
                Map.of(
                        "dynamicTasks", "${prep_ref.output.dynamicTasks}",
                        "dynamicTasksInput", "${prep_ref.output.dynamicTasksInput}"));
        dynFork.setDynamicForkTasksParam("dynamicTasks");
        dynFork.setDynamicForkTasksInputParamName("dynamicTasksInput");

        WorkflowTask join = new WorkflowTask();
        join.setName("dyn_join");
        join.setTaskReferenceName("dyn_join_ref");
        join.setWorkflowTaskType(TaskType.JOIN);

        WorkflowDef parentDef = new WorkflowDef();
        parentDef.setName(parentName);
        parentDef.setOwnerEmail("test@conductor.io");
        parentDef.setTimeoutSeconds(900);
        parentDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        parentDef.setTasks(List.of(prep, dynFork, join));
        metadataClient.updateWorkflowDefs(List.of(parentDef));
    }

    private void completeDynamicForkPrep(
            String parentId,
            String childName,
            String failingRef,
            String cancelledRef,
            String completedRef) {
        WorkflowTask failingDyn = buildDynamicSubWorkflowTask(failingRef, childName);
        WorkflowTask cancelledDyn = buildDynamicSubWorkflowTask(cancelledRef, childName);
        WorkflowTask completedDyn = buildDynamicSubWorkflowTask(completedRef, childName);

        java.util.HashMap<String, Object> output = new java.util.HashMap<>();
        output.put("dynamicTasks", List.of(failingDyn, cancelledDyn, completedDyn));
        output.put(
                "dynamicTasksInput",
                Map.of(
                        failingRef, Map.of(),
                        cancelledRef, Map.of(),
                        completedRef, Map.of()));

        Task prep = findActiveTaskByRef(parentId, "prep_ref");
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
     * Retry on the FAILING child sub-workflow directly (one of the dynamic-fork branches), not on
     * the parent. Walk-up via updateAndPushParents from that child reaches the parent, where the
     * cancelled-sibling fix fires: cancelled branch restored in place (subWorkflowId preserved),
     * COMPLETED branch untouched, failing child's simple_ref retried in place.
     */
    @Test
    @DisplayName(
            "Retry on the FAILING dynamic-fork child sub-workflow directly: walk-up restores cancelled sibling in place, COMPLETED untouched")
    public void testRetryOnFailingChildSubWorkflowInDynamicForkPreservesCancelledSibling() {
        DynamicCaptured c = setupDynamicForkScenarioAndFail();

        workflowClient.retryWorkflow(List.of(c.childFailing));
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.RUNNING);

        await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertEquals(
                                    c.childCompleted,
                                    currentSubWorkflowId(
                                            c.parentId, c.completedRef, Task.Status.COMPLETED),
                                    "COMPLETED branch must keep its original subWorkflowId");
                            assertEquals(
                                    c.childFailing,
                                    activeSubWorkflowId(c.parentId, c.failingRef),
                                    "failing branch subWorkflowId must be PRESERVED (retry on the child itself)");
                            assertEquals(
                                    c.childCancelled,
                                    activeSubWorkflowId(c.parentId, c.cancelledRef),
                                    "cancelled branch subWorkflowId must be PRESERVED (walk-up + cancelled-sibling fix)");
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(c.childFailing, false)
                                            .getStatus()
                                            .isTerminal(),
                                    "failing child must be restored to non-terminal");
                            assertFalse(
                                    workflowClient
                                            .getWorkflow(c.childCancelled, false)
                                            .getStatus()
                                            .isTerminal(),
                                    "cancelled child must be restored to non-terminal");

                            assertInlinePreserved(
                                    c.childFailing,
                                    c.inlineFailingTaskId,
                                    c.inlineFailingEndTime,
                                    "failing branch child");
                            assertInlinePreserved(
                                    c.childCancelled,
                                    c.inlineCancelledTaskId,
                                    c.inlineCancelledEndTime,
                                    "cancelled branch child");
                            assertInlinePreserved(
                                    c.childCompleted,
                                    c.inlineCompletedTaskId,
                                    c.inlineCompletedEndTime,
                                    "completed branch child");
                        });

        completeActiveSimpleRef(c.childFailing, TaskResult.Status.COMPLETED);
        completeActiveSimpleRef(c.childCancelled, TaskResult.Status.COMPLETED);
        awaitWorkflowStatus(c.parentId, Workflow.WorkflowStatus.COMPLETED);
    }

    /**
     * Customer-reported regression after PR #3458: a fork where every branch is a SUB_WORKFLOW (no
     * SIMPLE siblings). When one sub-workflow fails and others get CANCELLED, retry/rerun must
     * resume the cancelled siblings — and to avoid re-executing already-COMPLETED work inside the
     * cancelled children, it must do so by retrying the existing terminated child workflows
     * in-place (preserving subWorkflowId) rather than spawning brand-new ones.
     */
    @Test
    @DisplayName(
            "Retry sub-workflow inside all-SUB_WORKFLOW fork retries each CANCELLED sibling in-place")
    public void testRetrySubWorkflowReschedulesAllSubWorkflowSiblingsInFork() {
        long stamp = System.currentTimeMillis();
        String parentWfName = "rerun-allsubwf-parent-" + stamp;
        String failingSubWfName = "rerun-allsubwf-failing-child-" + stamp;
        String cancelledSubWfName = "rerun-allsubwf-cancelled-child-" + stamp;

        String taskInFailingSubRef = "task_in_failing_sub";
        String taskInCancelledSubRef = "task_in_cancelled_sub";
        String failingSubRef = "failing_sub_ref";
        String cancelledSubRef = "cancelled_sub_ref";
        String forkRef = "fork_allsubwf";
        String joinRef = "join_allsubwf";

        registerWorkflow(failingSubWfName, List.of(simpleTask(taskInFailingSubRef)));
        registerWorkflow(cancelledSubWfName, List.of(simpleTask(taskInCancelledSubRef)));
        registerWorkflow(
                parentWfName,
                List.of(
                        forkJoin(
                                forkRef,
                                List.of(
                                        List.of(subWorkflowTask(failingSubRef, failingSubWfName)),
                                        List.of(
                                                subWorkflowTask(
                                                        cancelledSubRef, cancelledSubWfName)))),
                        join(joinRef, List.of(failingSubRef, cancelledSubRef))));

        try {
            String workflowId = start(parentWfName);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                Task failingSub =
                                        findActiveTask(
                                                wf, failingSubRef, "failing_sub_ref not found");
                                Task cancelledSub =
                                        findActiveTask(
                                                wf, cancelledSubRef, "cancelled_sub_ref not found");
                                assertNotNull(failingSub.getSubWorkflowId());
                                assertNotNull(cancelledSub.getSubWorkflowId());
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(failingSub.getSubWorkflowId(), true)
                                                .getTasks()
                                                .isEmpty());
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(cancelledSub.getSubWorkflowId(), true)
                                                .getTasks()
                                                .isEmpty());
                            });

            String failingSubWorkflowId =
                    findActiveTask(workflowId, failingSubRef, "missing").getSubWorkflowId();
            String originalCancelledSubWorkflowId =
                    findActiveTask(workflowId, cancelledSubRef, "missing").getSubWorkflowId();

            completeTask(
                    findActiveTask(
                            failingSubWorkflowId,
                            taskInFailingSubRef,
                            "failing inner task not active"),
                    TaskResult.Status.FAILED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        wf.getStatus(),
                                        "Parent should be FAILED");
                                assertEquals(
                                        Task.Status.CANCELED,
                                        statusOf(wf, cancelledSubRef),
                                        "cancelled_sub_ref should be CANCELED");
                                assertNotNull(
                                        taskOf(wf, failingSubRef).getReasonForIncompletion(),
                                        "failing_sub_ref should have reasonForIncompletion populated while FAILED");
                            });

            workflowClient.retryWorkflow(List.of(failingSubWorkflowId));
            awaitWorkflowStatus(
                    workflowId,
                    Workflow.WorkflowStatus.RUNNING,
                    "Parent should be RUNNING after retry");

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task cancelledSiblingNow =
                                        findActiveTask(
                                                workflowId,
                                                cancelledSubRef,
                                                "Expected an active cancelled_sub_ref task after retry");
                                assertEquals(
                                        originalCancelledSubWorkflowId,
                                        cancelledSiblingNow.getSubWorkflowId(),
                                        "subWorkflowId must be PRESERVED (retry-in-place), not regenerated");
                                Workflow resumedChild =
                                        workflowClient.getWorkflow(
                                                cancelledSiblingNow.getSubWorkflowId(), true);
                                assertFalse(
                                        resumedChild.getStatus().isTerminal(),
                                        "Resumed child must be non-terminal");
                                assertFalse(
                                        resumedChild.getTasks().isEmpty(),
                                        "Resumed child must still have its tasks");

                                Task resumedFailingSub =
                                        findActiveTask(
                                                workflowId,
                                                failingSubRef,
                                                "failing_sub_ref must be active after retry");
                                assertNull(
                                        resumedFailingSub.getReasonForIncompletion(),
                                        "failing_sub_ref reasonForIncompletion must be cleared after retry walk-up");
                            });

            String resumedChildId =
                    findActiveTask(workflowId, cancelledSubRef, "active cancelled_sub_ref missing")
                            .getSubWorkflowId();
            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    findActiveTask(
                                            resumedChildId,
                                            taskInCancelledSubRef,
                                            "Inner task in resumed child should be active"));
            completeTask(
                    findActiveTask(
                            resumedChildId, taskInCancelledSubRef, "resumed inner task missing"),
                    TaskResult.Status.COMPLETED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    findActiveTask(
                                            failingSubWorkflowId,
                                            taskInFailingSubRef,
                                            "Retried task in failing sub-workflow should be active"));
            completeTask(
                    findActiveTask(
                            failingSubWorkflowId,
                            taskInFailingSubRef,
                            "retried failing inner task missing"),
                    TaskResult.Status.COMPLETED);

            awaitWorkflowStatus(
                    workflowId,
                    Workflow.WorkflowStatus.COMPLETED,
                    20,
                    "Parent workflow should reach COMPLETED");

        } finally {
            terminateAll(parentWfName, failingSubWfName, cancelledSubWfName);
        }
    }

    /**
     * Customer-reported scenario: nested SUB_WORKFLOW forks. When the user retries from the deepest
     * failed task, updateAndPushParents walks up TWO levels. CANCELLED SUB_WORKFLOW siblings at
     * BOTH levels must spawn fresh children. Without the fix, neither level would recover.
     */
    @Test
    @DisplayName(
            "Retry of deeply-nested sub-workflow reschedules CANCELLED SUB_WORKFLOW siblings at every parent level")
    public void testRetryNestedSubWorkflowsReschedulesSiblingsAtEveryLevel() {
        long stamp = System.currentTimeMillis();
        String topWfName = "rerun-nested-top-" + stamp;
        String midWfName = "rerun-nested-mid-" + stamp;
        String innerFailingWfName = "rerun-nested-inner-failing-" + stamp;
        String innerCancelledWfName = "rerun-nested-inner-cancelled-" + stamp;
        String outerCancelledWfName = "rerun-nested-outer-cancelled-" + stamp;

        String innerFailingTaskRef = "task_in_inner_failing";
        String innerCancelledTaskRef = "task_in_inner_cancelled";
        String outerCancelledTaskRef = "task_in_outer_cancelled";

        registerWorkflow(innerFailingWfName, List.of(simpleTask(innerFailingTaskRef)));
        registerWorkflow(innerCancelledWfName, List.of(simpleTask(innerCancelledTaskRef)));
        registerWorkflow(outerCancelledWfName, List.of(simpleTask(outerCancelledTaskRef)));

        registerWorkflow(
                midWfName,
                List.of(
                        forkJoin(
                                "inner_fork",
                                List.of(
                                        List.of(
                                                subWorkflowTask(
                                                        "inner_failing_ref", innerFailingWfName)),
                                        List.of(
                                                subWorkflowTask(
                                                        "inner_cancelled_ref",
                                                        innerCancelledWfName)))),
                        join("inner_join", List.of("inner_failing_ref", "inner_cancelled_ref"))));

        registerWorkflow(
                topWfName,
                List.of(
                        forkJoin(
                                "top_fork",
                                List.of(
                                        List.of(subWorkflowTask("mid_ref", midWfName)),
                                        List.of(
                                                subWorkflowTask(
                                                        "outer_cancelled_ref",
                                                        outerCancelledWfName)))),
                        join("top_join", List.of("mid_ref", "outer_cancelled_ref"))),
                900);

        try {
            String topId = start(topWfName);

            String[] midIdHolder = new String[1];
            String[] innerFailingIdHolder = new String[1];
            String[] innerCancelledIdHolder = new String[1];
            String[] outerCancelledIdHolder = new String[1];

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow top = workflowClient.getWorkflow(topId, true);
                                Task midTask =
                                        top.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "mid_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "mid_ref not found"));
                                Task outerCancelled =
                                        top.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "outer_cancelled_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "outer_cancelled_ref not found"));
                                assertNotNull(midTask.getSubWorkflowId());
                                assertNotNull(outerCancelled.getSubWorkflowId());
                                midIdHolder[0] = midTask.getSubWorkflowId();
                                outerCancelledIdHolder[0] = outerCancelled.getSubWorkflowId();

                                Workflow mid = workflowClient.getWorkflow(midIdHolder[0], true);
                                Task innerFailing =
                                        mid.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "inner_failing_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "inner_failing_ref not found"));
                                Task innerCancelled =
                                        mid.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "inner_cancelled_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "inner_cancelled_ref not found"));
                                assertNotNull(innerFailing.getSubWorkflowId());
                                assertNotNull(innerCancelled.getSubWorkflowId());
                                innerFailingIdHolder[0] = innerFailing.getSubWorkflowId();
                                innerCancelledIdHolder[0] = innerCancelled.getSubWorkflowId();

                                assertFalse(
                                        workflowClient
                                                .getWorkflow(innerFailingIdHolder[0], true)
                                                .getTasks()
                                                .isEmpty());
                            });

            String midId = midIdHolder[0];
            String innerFailingId = innerFailingIdHolder[0];
            String originalInnerCancelledId = innerCancelledIdHolder[0];
            String originalOuterCancelledId = outerCancelledIdHolder[0];

            completeTask(
                    findActiveTask(
                            innerFailingId, innerFailingTaskRef, "inner failing task not active"),
                    TaskResult.Status.FAILED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow top = workflowClient.getWorkflow(topId, true);
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        top.getStatus(),
                                        "Top should be FAILED. Got: " + top.getStatus());
                                assertEquals(
                                        Task.Status.CANCELED,
                                        statusOf(top, "outer_cancelled_ref"),
                                        "outer_cancelled_ref should be CANCELED");
                                assertNotNull(
                                        taskOf(top, "mid_ref").getReasonForIncompletion(),
                                        "top.mid_ref should have reasonForIncompletion populated while FAILED");

                                Workflow mid = workflowClient.getWorkflow(midId, true);
                                assertEquals(Workflow.WorkflowStatus.FAILED, mid.getStatus());
                                assertEquals(
                                        Task.Status.CANCELED,
                                        statusOf(mid, "inner_cancelled_ref"),
                                        "inner_cancelled_ref should be CANCELED");
                                assertNotNull(
                                        taskOf(mid, "inner_failing_ref").getReasonForIncompletion(),
                                        "mid.inner_failing_ref should have reasonForIncompletion populated while FAILED");
                            });

            workflowClient.retryWorkflow(List.of(innerFailingId));

            awaitWorkflowStatus(
                    topId,
                    Workflow.WorkflowStatus.RUNNING,
                    20,
                    "Top should resume to RUNNING after retry");
            awaitWorkflowStatus(
                    midId,
                    Workflow.WorkflowStatus.RUNNING,
                    20,
                    "Mid should resume to RUNNING after retry");

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task active =
                                        findActiveTask(
                                                midId,
                                                "inner_cancelled_ref",
                                                "Expected an active inner_cancelled_ref after retry");
                                assertEquals(
                                        originalInnerCancelledId,
                                        active.getSubWorkflowId(),
                                        "Mid level: subWorkflowId must be PRESERVED (retry-in-place)");
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(active.getSubWorkflowId(), true)
                                                .getStatus()
                                                .isTerminal(),
                                        "Mid level resumed child must be non-terminal");
                                assertNull(
                                        findActiveTask(
                                                        midId,
                                                        "inner_failing_ref",
                                                        "inner_failing_ref must be active after retry")
                                                .getReasonForIncompletion(),
                                        "mid.inner_failing_ref reasonForIncompletion must be cleared after retry walk-up");
                            });

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task active =
                                        findActiveTask(
                                                topId,
                                                "outer_cancelled_ref",
                                                "Expected an active outer_cancelled_ref after retry");
                                assertEquals(
                                        originalOuterCancelledId,
                                        active.getSubWorkflowId(),
                                        "Top level: subWorkflowId must be PRESERVED (retry-in-place)");
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(active.getSubWorkflowId(), true)
                                                .getStatus()
                                                .isTerminal(),
                                        "Top level resumed child must be non-terminal");
                                assertNull(
                                        findActiveTask(
                                                        topId,
                                                        "mid_ref",
                                                        "mid_ref must be active after retry")
                                                .getReasonForIncompletion(),
                                        "top.mid_ref reasonForIncompletion must be cleared after retry walk-up");
                            });

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    findActiveTask(
                                            originalInnerCancelledId,
                                            innerCancelledTaskRef,
                                            "inner_cancelled inner task must be active"));
            completeTask(
                    findActiveTask(
                            originalInnerCancelledId,
                            innerCancelledTaskRef,
                            "inner_cancelled inner task missing"),
                    TaskResult.Status.COMPLETED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    findActiveTask(
                                            originalOuterCancelledId,
                                            outerCancelledTaskRef,
                                            "outer_cancelled inner task must be active"));
            completeTask(
                    findActiveTask(
                            originalOuterCancelledId,
                            outerCancelledTaskRef,
                            "outer_cancelled inner task missing"),
                    TaskResult.Status.COMPLETED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    findActiveTask(
                                            innerFailingId,
                                            innerFailingTaskRef,
                                            "retried inner failing task must be active"));
            completeTask(
                    findActiveTask(
                            innerFailingId,
                            innerFailingTaskRef,
                            "retried inner failing task missing"),
                    TaskResult.Status.COMPLETED);

            awaitWorkflowStatus(
                    topId,
                    Workflow.WorkflowStatus.COMPLETED,
                    30,
                    "Top workflow should reach COMPLETED end-to-end");

        } finally {
            terminateAll(
                    topWfName,
                    midWfName,
                    innerFailingWfName,
                    innerCancelledWfName,
                    outerCancelledWfName);
        }
    }

    /**
     * Retrying from the leaf failed task walks updateAndPushParents through three parent levels.
     * SIMPLE cancelled siblings at the grandchild and mid levels are resumed by the existing
     * else-branch (reset to SCHEDULED + re-queue). The SUB_WORKFLOW cancelled sibling at the top
     * level is resumed in-place by the new branch (existing child id preserved).
     */
    @Test
    @DisplayName("Retry resumes cancelled siblings at parent, child, and grandchild fork levels")
    public void testRetryNestedForkWithSimpleAndSubWorkflowSiblings() {
        long stamp = System.currentTimeMillis();
        String topWfName = "rerun-3lvl-top-" + stamp;
        String midSubWfName = "rerun-3lvl-mid-" + stamp;
        String grandchildSubWfName = "rerun-3lvl-grandchild-" + stamp;
        String failingLeafWfName = "rerun-3lvl-leaf-" + stamp;
        String topCancelledSubWfName = "rerun-3lvl-top-cancelled-" + stamp;

        String failingLeafTaskRef = "task_in_failing_leaf";
        String grandchildCancelledSimpleRef = "grandchild_cancelled_simple";
        String midCancelledSimpleRef = "mid_cancelled_simple";
        String topCancelledTaskRef = "task_in_top_cancelled";

        registerWorkflow(failingLeafWfName, List.of(simpleTask(failingLeafTaskRef)));
        registerWorkflow(topCancelledSubWfName, List.of(simpleTask(topCancelledTaskRef)));

        registerWorkflow(
                grandchildSubWfName,
                List.of(
                        forkJoin(
                                "grandchild_fork",
                                List.of(
                                        List.of(
                                                subWorkflowTask(
                                                        "failing_leaf_ref", failingLeafWfName)),
                                        List.of(simpleTask(grandchildCancelledSimpleRef)))),
                        join(
                                "grandchild_join",
                                List.of("failing_leaf_ref", grandchildCancelledSimpleRef))));

        registerWorkflow(
                midSubWfName,
                List.of(
                        forkJoin(
                                "mid_fork",
                                List.of(
                                        List.of(
                                                subWorkflowTask(
                                                        "grandchild_ref", grandchildSubWfName)),
                                        List.of(simpleTask(midCancelledSimpleRef)))),
                        join("mid_join", List.of("grandchild_ref", midCancelledSimpleRef))));

        registerWorkflow(
                topWfName,
                List.of(
                        forkJoin(
                                "top_fork",
                                List.of(
                                        List.of(subWorkflowTask("mid_ref", midSubWfName)),
                                        List.of(
                                                subWorkflowTask(
                                                        "top_cancelled_ref",
                                                        topCancelledSubWfName)))),
                        join("top_join", List.of("mid_ref", "top_cancelled_ref"))),
                900);

        try {
            String topId = start(topWfName);

            String[] midIdHolder = new String[1];
            String[] grandchildIdHolder = new String[1];
            String[] failingLeafIdHolder = new String[1];
            String[] topCancelledIdHolder = new String[1];

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow top = workflowClient.getWorkflow(topId, true);
                                Task midTask =
                                        top.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "mid_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "mid_ref not found"));
                                Task topCancelled =
                                        top.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "top_cancelled_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "top_cancelled_ref not found"));
                                assertNotNull(midTask.getSubWorkflowId());
                                assertNotNull(topCancelled.getSubWorkflowId());
                                midIdHolder[0] = midTask.getSubWorkflowId();
                                topCancelledIdHolder[0] = topCancelled.getSubWorkflowId();

                                Workflow mid = workflowClient.getWorkflow(midIdHolder[0], true);
                                Task grandchild =
                                        mid.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "grandchild_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "grandchild_ref not found"));
                                assertNotNull(grandchild.getSubWorkflowId());
                                grandchildIdHolder[0] = grandchild.getSubWorkflowId();

                                Workflow grandchildWf =
                                        workflowClient.getWorkflow(grandchildIdHolder[0], true);
                                Task leaf =
                                        grandchildWf.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                "failing_leaf_ref"
                                                                        .equals(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "failing_leaf_ref not found"));
                                assertNotNull(leaf.getSubWorkflowId());
                                failingLeafIdHolder[0] = leaf.getSubWorkflowId();
                                assertFalse(
                                        workflowClient
                                                .getWorkflow(failingLeafIdHolder[0], true)
                                                .getTasks()
                                                .isEmpty());
                            });

            String midId = midIdHolder[0];
            String grandchildId = grandchildIdHolder[0];
            String failingLeafId = failingLeafIdHolder[0];
            String originalTopCancelledId = topCancelledIdHolder[0];

            Workflow leaf = workflowClient.getWorkflow(failingLeafId, true);
            Task leafInner =
                    leaf.getTasks().stream()
                            .filter(t -> failingLeafTaskRef.equals(t.getReferenceTaskName()))
                            .findFirst()
                            .orElseThrow();
            completeTask(leafInner, TaskResult.Status.FAILED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow top = workflowClient.getWorkflow(topId, true);
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        top.getStatus(),
                                        "Top should be FAILED");
                                assertEquals(
                                        Task.Status.CANCELED,
                                        statusOf(top, "top_cancelled_ref"),
                                        "top_cancelled_ref should be CANCELED");
                                assertNotNull(
                                        taskOf(top, "mid_ref").getReasonForIncompletion(),
                                        "top.mid_ref should have reasonForIncompletion populated while FAILED");

                                Workflow mid = workflowClient.getWorkflow(midId, true);
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        mid.getStatus(),
                                        "Mid should be FAILED");
                                assertEquals(
                                        Task.Status.CANCELED,
                                        statusOf(mid, midCancelledSimpleRef),
                                        "mid_cancelled_simple should be CANCELED");
                                assertNotNull(
                                        taskOf(mid, "grandchild_ref").getReasonForIncompletion(),
                                        "mid.grandchild_ref should have reasonForIncompletion populated while FAILED");

                                Workflow grandchild =
                                        workflowClient.getWorkflow(grandchildId, true);
                                assertEquals(
                                        Workflow.WorkflowStatus.FAILED,
                                        grandchild.getStatus(),
                                        "Grandchild should be FAILED");
                                assertEquals(
                                        Task.Status.CANCELED,
                                        statusOf(grandchild, grandchildCancelledSimpleRef),
                                        "grandchild_cancelled_simple should be CANCELED");
                                assertNotNull(
                                        taskOf(grandchild, "failing_leaf_ref")
                                                .getReasonForIncompletion(),
                                        "grandchild.failing_leaf_ref should have reasonForIncompletion populated while FAILED");
                            });

            workflowClient.retryWorkflow(List.of(failingLeafId));

            awaitWorkflowStatus(
                    topId, Workflow.WorkflowStatus.RUNNING, 30, "Top must resume to RUNNING");
            awaitWorkflowStatus(
                    midId, Workflow.WorkflowStatus.RUNNING, 30, "Mid must resume to RUNNING");
            awaitWorkflowStatus(
                    grandchildId,
                    Workflow.WorkflowStatus.RUNNING,
                    30,
                    "Grandchild must resume to RUNNING");

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                findActiveTask(
                                        grandchildId,
                                        grandchildCancelledSimpleRef,
                                        "Expected an active grandchild_cancelled_simple after retry");
                                assertNull(
                                        findActiveTask(
                                                        grandchildId,
                                                        "failing_leaf_ref",
                                                        "failing_leaf_ref must be active after retry")
                                                .getReasonForIncompletion(),
                                        "grandchild.failing_leaf_ref reasonForIncompletion must be cleared after retry walk-up");
                            });

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                findActiveTask(
                                        midId,
                                        midCancelledSimpleRef,
                                        "Expected an active mid_cancelled_simple after retry");
                                assertNull(
                                        findActiveTask(
                                                        midId,
                                                        "grandchild_ref",
                                                        "grandchild_ref must be active after retry")
                                                .getReasonForIncompletion(),
                                        "mid.grandchild_ref reasonForIncompletion must be cleared after retry walk-up");
                            });

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task topCancelledNow =
                                        findActiveTask(
                                                topId,
                                                "top_cancelled_ref",
                                                "Expected an active top_cancelled_ref after retry");
                                assertEquals(
                                        originalTopCancelledId,
                                        topCancelledNow.getSubWorkflowId(),
                                        "Top level: subWorkflowId must be PRESERVED (in-place retry)");
                                Workflow resumedChild =
                                        workflowClient.getWorkflow(
                                                topCancelledNow.getSubWorkflowId(), true);
                                assertFalse(
                                        resumedChild.getStatus().isTerminal(),
                                        "Resumed top-cancelled child must be non-terminal");
                                assertNull(
                                        findActiveTask(
                                                        topId,
                                                        "mid_ref",
                                                        "mid_ref must be active after retry")
                                                .getReasonForIncompletion(),
                                        "top.mid_ref reasonForIncompletion must be cleared after retry walk-up");
                            });

            completeTask(
                    findActiveTask(
                            grandchildId,
                            grandchildCancelledSimpleRef,
                            "grandchild_cancelled_simple active task missing"),
                    TaskResult.Status.COMPLETED);
            completeTask(
                    findActiveTask(
                            midId,
                            midCancelledSimpleRef,
                            "mid_cancelled_simple active task missing"),
                    TaskResult.Status.COMPLETED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    findActiveTask(
                                            originalTopCancelledId,
                                            topCancelledTaskRef,
                                            "Inner task in resumed top-cancelled child must be active"));
            completeTask(
                    findActiveTask(
                            originalTopCancelledId,
                            topCancelledTaskRef,
                            "top-cancelled inner task missing"),
                    TaskResult.Status.COMPLETED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    findActiveTask(
                                            failingLeafId,
                                            failingLeafTaskRef,
                                            "Retried leaf task must be active"));
            completeTask(
                    findActiveTask(failingLeafId, failingLeafTaskRef, "leaf retried task missing"),
                    TaskResult.Status.COMPLETED);

            awaitWorkflowStatus(
                    topId,
                    Workflow.WorkflowStatus.COMPLETED,
                    30,
                    "Top workflow should reach COMPLETED end-to-end");

        } finally {
            terminateAll(
                    topWfName,
                    midSubWfName,
                    grandchildSubWfName,
                    failingLeafWfName,
                    topCancelledSubWfName);
        }
    }

    /**
     * The key correctness property of retry-in-place over fresh-spawn: tasks that already COMPLETED
     * inside the cancelled child must STAY COMPLETED after retry, never re-execute.
     */
    @Test
    @DisplayName(
            "Retry-in-place preserves COMPLETED tasks inside the cancelled child (no duplicate execution)")
    public void testRetryInPlacePreservesCompletedTasksInsideCancelledChild() {
        long stamp = System.currentTimeMillis();
        String parentWfName = "rerun-preserve-completed-parent-" + stamp;
        String failingSubWfName = "rerun-preserve-completed-failing-" + stamp;
        String cancelledSubWfName = "rerun-preserve-completed-cancelled-" + stamp;
        String taskInFailingSubRef = "task_in_failing_pc";
        String taskARef = "task_a_in_cancelled_pc";
        String taskBRef = "task_b_in_cancelled_pc";
        String failingSubRef = "failing_sub_ref_pc";
        String cancelledSubRef = "cancelled_sub_ref_pc";

        registerWorkflow(failingSubWfName, List.of(simpleTask(taskInFailingSubRef)));
        registerWorkflow(cancelledSubWfName, List.of(simpleTask(taskARef), simpleTask(taskBRef)));
        registerWorkflow(
                parentWfName,
                List.of(
                        forkJoin(
                                "fork_pc",
                                List.of(
                                        List.of(subWorkflowTask(failingSubRef, failingSubWfName)),
                                        List.of(
                                                subWorkflowTask(
                                                        cancelledSubRef, cancelledSubWfName)))),
                        join("join_pc", List.of(failingSubRef, cancelledSubRef))));

        try {
            String workflowId = start(parentWfName);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task cancelledSub =
                                        findActiveTask(
                                                workflowId,
                                                cancelledSubRef,
                                                "cancelled sibling missing");
                                assertNotNull(cancelledSub.getSubWorkflowId());
                                findActiveTask(
                                        cancelledSub.getSubWorkflowId(),
                                        taskARef,
                                        "taskA should be active in cancelled child");
                                Task failingSub =
                                        findActiveTask(
                                                workflowId, failingSubRef, "failing sub missing");
                                assertNotNull(failingSub.getSubWorkflowId());
                            });

            String failingSubWorkflowId =
                    findActiveTask(workflowId, failingSubRef, "missing").getSubWorkflowId();
            String originalCancelledChildId =
                    findActiveTask(workflowId, cancelledSubRef, "missing").getSubWorkflowId();

            completeTask(
                    findActiveTask(
                            originalCancelledChildId,
                            taskARef,
                            "taskA must be active in cancelled child"),
                    TaskResult.Status.COMPLETED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow ch =
                                        workflowClient.getWorkflow(originalCancelledChildId, true);
                                assertEquals(
                                        Task.Status.COMPLETED,
                                        statusOf(ch, taskARef),
                                        "taskA should be COMPLETED in cancelled child");
                                findActiveTask(
                                        ch,
                                        taskBRef,
                                        "taskB should be active (taskA done, taskB scheduled/in-progress)");
                            });
            Task originalTaskA =
                    workflowClient.getWorkflow(originalCancelledChildId, true).getTasks().stream()
                            .filter(
                                    t ->
                                            taskARef.equals(t.getReferenceTaskName())
                                                    && t.getStatus() == Task.Status.COMPLETED)
                            .findFirst()
                            .orElseThrow();
            String originalTaskAId = originalTaskA.getTaskId();
            long originalTaskAEndTime = originalTaskA.getEndTime();

            completeTask(
                    findActiveTask(
                            failingSubWorkflowId,
                            taskInFailingSubRef,
                            "failing inner task not active"),
                    TaskResult.Status.FAILED);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow wf = workflowClient.getWorkflow(workflowId, true);
                                assertEquals(Workflow.WorkflowStatus.FAILED, wf.getStatus());
                                Workflow ch =
                                        workflowClient.getWorkflow(originalCancelledChildId, true);
                                assertTrue(
                                        ch.getStatus().isTerminal(),
                                        "Cancelled child should be terminal. Got: "
                                                + ch.getStatus());
                                assertEquals(
                                        Task.Status.COMPLETED,
                                        statusOf(ch, taskARef),
                                        "taskA must still be COMPLETED inside the terminated cancelled child");
                                assertNotNull(
                                        taskOf(wf, failingSubRef).getReasonForIncompletion(),
                                        "failing_sub_ref should have reasonForIncompletion populated while FAILED");
                            });

            workflowClient.retryWorkflow(List.of(failingSubWorkflowId));

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task siblingTaskNow =
                                        findActiveTask(
                                                workflowId,
                                                cancelledSubRef,
                                                "cancelled sibling task should be active after retry");
                                assertEquals(
                                        originalCancelledChildId,
                                        siblingTaskNow.getSubWorkflowId(),
                                        "subWorkflowId must be PRESERVED — retry-in-place");

                                Workflow ch =
                                        workflowClient.getWorkflow(originalCancelledChildId, true);
                                assertFalse(
                                        ch.getStatus().isTerminal(),
                                        "Resumed child must be non-terminal. Got: "
                                                + ch.getStatus());

                                assertNull(
                                        findActiveTask(
                                                        workflowId,
                                                        failingSubRef,
                                                        "failing_sub_ref must be active after retry")
                                                .getReasonForIncompletion(),
                                        "failing_sub_ref reasonForIncompletion must be cleared after retry walk-up");

                                Task taskAAfterRetry =
                                        ch.getTasks().stream()
                                                .filter(
                                                        t ->
                                                                taskARef.equals(
                                                                                t
                                                                                        .getReferenceTaskName())
                                                                        && t.getStatus()
                                                                                == Task.Status
                                                                                        .COMPLETED)
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "taskA must remain COMPLETED after retry — fresh-spawn would have re-executed it"));
                                assertEquals(
                                        originalTaskAId,
                                        taskAAfterRetry.getTaskId(),
                                        "taskA must be the same task instance — not a re-executed copy");
                                assertEquals(
                                        originalTaskAEndTime,
                                        taskAAfterRetry.getEndTime(),
                                        "taskA's endTime must be unchanged — proves it was not re-executed");
                            });

        } finally {
            terminateAll(parentWfName, failingSubWfName, cancelledSubWfName);
        }
    }

    /**
     * Retrying a sub-workflow (the child) re-runs its failed inner task in place and the parent's
     * SUB_WORKFLOW task is resumed with the same child id.
     */
    @Test
    @DisplayName("Retry on a sub-workflow retries the failed task inside it")
    public void testRetrySubWorkflowRetriesFailedInnerTask() {
        long stamp = System.currentTimeMillis();
        String parentWfName = "retry-subwf-inner-parent-" + stamp;
        String childWfName = "retry-subwf-inner-child-" + stamp;
        String innerTaskRef = "inner_task_retry_inner";
        String subRef = "sub_ref_retry_inner";

        registerWorkflow(childWfName, List.of(simpleTask(innerTaskRef)));
        registerWorkflow(parentWfName, List.of(subWorkflowTask(subRef, childWfName)));

        try {
            String workflowId = start(parentWfName);

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task sub =
                                        findActiveTask(
                                                workflowId, subRef, "sub task should be active");
                                assertNotNull(sub.getSubWorkflowId());
                                findActiveTask(
                                        sub.getSubWorkflowId(),
                                        innerTaskRef,
                                        "inner task should be active in child");
                            });

            String childId =
                    findActiveTask(workflowId, subRef, "sub task should be active")
                            .getSubWorkflowId();

            Task innerTask = findActiveTask(childId, innerTaskRef, "inner task not active");
            String failedTaskId = innerTask.getTaskId();
            completeTask(innerTask, TaskResult.Status.FAILED);

            awaitWorkflowStatus(
                    workflowId, Workflow.WorkflowStatus.FAILED, "Parent should be FAILED");
            awaitWorkflowStatus(childId, Workflow.WorkflowStatus.FAILED, "Child should be FAILED");
            assertNotNull(
                    taskOf(workflowClient.getWorkflow(workflowId, true), subRef)
                            .getReasonForIncompletion(),
                    "parent sub_ref should have reasonForIncompletion populated while FAILED");

            workflowClient.retryWorkflow(List.of(childId));

            awaitWorkflowStatus(
                    childId, Workflow.WorkflowStatus.RUNNING, "Child must be RUNNING after retry");
            awaitWorkflowStatus(
                    workflowId,
                    Workflow.WorkflowStatus.RUNNING,
                    "Parent must be RUNNING after retry");

            await().atMost(WF_AWAIT_SECS, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                Task retried =
                                        findActiveTask(
                                                childId,
                                                innerTaskRef,
                                                "A fresh attempt of inner_task must be active after retry");
                                assertNotEquals(
                                        failedTaskId,
                                        retried.getTaskId(),
                                        "Retried task should be a new attempt (different taskId)");
                                Task subNow =
                                        findActiveTask(
                                                workflowId,
                                                subRef,
                                                "parent sub_ref must be active");
                                assertEquals(
                                        childId,
                                        subNow.getSubWorkflowId(),
                                        "Parent's SUB_WORKFLOW task must still point at the same child");
                                assertNull(
                                        subNow.getReasonForIncompletion(),
                                        "parent sub_ref reasonForIncompletion must be cleared after retry walk-up");
                            });

            completeTask(
                    findActiveTask(childId, innerTaskRef, "retried task missing"),
                    TaskResult.Status.COMPLETED);

            awaitWorkflowStatus(
                    workflowId,
                    Workflow.WorkflowStatus.COMPLETED,
                    20,
                    "Parent should reach COMPLETED");

        } finally {
            terminateAll(parentWfName, childWfName);
        }
    }

    // ---------------- Shared helpers ----------------

    private Workflow completeTask(Task task, TaskResult.Status status) {
        return taskClient.updateTaskSync(
                task.getWorkflowInstanceId(),
                task.getReferenceTaskName(),
                status,
                task.getOutputData());
    }

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

    private static WorkflowTask forkJoin(String refName, List<List<WorkflowTask>> branches) {
        WorkflowTask fork = new WorkflowTask();
        fork.setTaskReferenceName(refName);
        fork.setName("FORK_JOIN");
        fork.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork.setForkTasks(branches);
        return fork;
    }

    private static WorkflowTask join(String refName, List<String> joinOn) {
        WorkflowTask join = new WorkflowTask();
        join.setTaskReferenceName(refName);
        join.setName("JOIN");
        join.setWorkflowTaskType(TaskType.JOIN);
        join.setJoinOn(joinOn);
        return join;
    }

    private void registerWorkflow(String name, List<WorkflowTask> tasks) {
        registerWorkflow(name, tasks, 600);
    }

    private void registerWorkflow(String name, List<WorkflowTask> tasks, int timeoutSeconds) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setOwnerEmail("test@conductor.io");
        def.setTimeoutSeconds(timeoutSeconds);
        def.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        def.setTasks(tasks);
        metadataClient.registerWorkflowDef(def);
    }

    private static Task findActiveTask(Workflow wf, String refName, String errorMessage) {
        return wf.getTasks().stream()
                .filter(
                        t ->
                                refName.equals(t.getReferenceTaskName())
                                        && !t.getStatus().isTerminal())
                .findFirst()
                .orElseThrow(() -> new AssertionError(errorMessage));
    }

    private Task findActiveTask(String workflowId, String refName, String errorMessage) {
        return findActiveTask(workflowClient.getWorkflow(workflowId, true), refName, errorMessage);
    }

    private static Task.Status statusOf(Workflow wf, String refName) {
        return taskOf(wf, refName).getStatus();
    }

    private static Task taskOf(Workflow wf, String refName) {
        return wf.getTasks().stream()
                .filter(t -> refName.equals(t.getReferenceTaskName()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        refName + " not found in workflow " + wf.getWorkflowId()));
    }

    private String start(String workflowName) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(workflowName);
        req.setVersion(1);
        return workflowClient.startWorkflow(req);
    }

    private void terminateAll(String... workflowNames) {
        for (String name : workflowNames) {
            try {
                terminateExistingRunningWorkflows(name);
            } catch (Exception ignore) {
                /* best-effort */
            }
        }
    }

    private void awaitWorkflowStatus(
            String workflowId, Workflow.WorkflowStatus expected, String message) {
        awaitWorkflowStatus(workflowId, expected, WF_AWAIT_SECS, message);
    }

    private void awaitWorkflowStatus(
            String workflowId, Workflow.WorkflowStatus expected, int seconds, String message) {
        await().atMost(seconds, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        expected,
                                        workflowClient.getWorkflow(workflowId, false).getStatus(),
                                        message));
    }
}
