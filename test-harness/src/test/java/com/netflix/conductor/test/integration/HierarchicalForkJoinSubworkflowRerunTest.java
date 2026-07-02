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
package com.netflix.conductor.test.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class HierarchicalForkJoinSubworkflowRerunTest extends AbstractSpecification {

    private static final String FORK_JOIN_HIERARCHICAL_SUB_WF = "hierarchical_fork_join_swf";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    @Autowired SubWorkflow subWorkflowTask;

    @Autowired Join joinTask;

    String rootWorkflowId;
    String midLevelWorkflowId;
    String leafWorkflowId;

    TaskDef persistedTask2Definition;

    @BeforeEach
    void setup() throws Exception {
        workflowTestUtil.registerWorkflows(
                "hierarchical_fork_join_swf.json", "simple_workflow_1_integration_test.json");

        // Modify task definition to 0 retries
        persistedTask2Definition =
                workflowTestUtil.getPersistedTaskDefinition("integration_task_2").get();
        TaskDef modifiedTask2Definition =
                new TaskDef(
                        persistedTask2Definition.getName(),
                        persistedTask2Definition.getDescription(),
                        persistedTask2Definition.getOwnerEmail(),
                        0,
                        persistedTask2Definition.getTimeoutSeconds(),
                        persistedTask2Definition.getResponseTimeoutSeconds());
        metadataService.updateTaskDef(modifiedTask2Definition);

        // Verify workflow definitions exist
        metadataService.getWorkflowDef(SIMPLE_WORKFLOW, 1);
        metadataService.getWorkflowDef(FORK_JOIN_HIERARCHICAL_SUB_WF, 1);

        // Start the root workflow
        String correlationId = "rerun_on_root_in_3level_wf";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("subwf", FORK_JOIN_HIERARCHICAL_SUB_WF);
        input.put("nextSubwf", SIMPLE_WORKFLOW);

        rootWorkflowId =
                startWorkflow(FORK_JOIN_HIERARCHICAL_SUB_WF, 1, correlationId, input, null);

        // Decide the root workflow
        workflowExecutor.decide(rootWorkflowId);
        Task rerunSetupSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (rerunSetupSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rerunSetupSubWfTask.getTaskId());
        }

        // Verify root workflow state
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // Poll and complete integration_task_2 in root
        Task pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2",
                        "task2.integration.worker",
                        Map.of("op", "task2.done"));
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

        // Verify root workflow after task completion
        Workflow rootWorkflowInstance =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWorkflowInstance.getStatus());
        assertEquals(4, rootWorkflowInstance.getTasks().size());

        // Sweep the mid-level workflow so its tasks get scheduled
        midLevelWorkflowId = rootWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(midLevelWorkflowId);

        // Poll and complete integration_task_2 in mid-level
        pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2",
                        "task2.integration.worker",
                        Map.of("op", "task2.done"));
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

        // Start the exact mid-level SUB_WORKFLOW task to avoid re-executing a stale parent queue
        // item
        String midLevelSubWorkflowTaskId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getTaskId();
        asyncSystemTaskExecutor.execute(subWorkflowTask, midLevelSubWorkflowTaskId);

        // Verify mid-level workflow state
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(4, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(3).getStatus());

        // Get leaf workflow id and sweep it
        Workflow midLevelWorkflowInstance =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        leafWorkflowId = midLevelWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(leafWorkflowId);

        // Verify leaf workflow is RUNNING
        Workflow leafWorkflowInstance =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWorkflowInstance.getStatus());
        assertEquals(1, leafWorkflowInstance.getTasks().size());
        assertEquals("integration_task_1", leafWorkflowInstance.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWorkflowInstance.getTasks().get(0).getStatus());

        // Poll and fail integration_task_2 in leaf (to make all workflows FAILED)
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        sweep(leafWorkflowId);
        workflowTestUtil.pollAndFailTask(
                "integration_task_2", "task2.integration.worker", "failed");

        // Verify leaf workflow is FAILED
        Workflow leafFailed = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, leafFailed.getStatus());
        assertEquals(2, leafFailed.getTasks().size());
        assertEquals("integration_task_1", leafFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafFailed.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", leafFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafFailed.getTasks().get(1).getStatus());

        // Sweep mid-level to propagate failure
        sweep(midLevelWorkflowId);

        // Verify mid-level workflow is FAILED
        Workflow midFailed = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, midFailed.getStatus());
        assertEquals(4, midFailed.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, midFailed.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midFailed.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midFailed.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midFailed.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                midFailed.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || midFailed.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS,
                "Mid-level JOIN should be CANCELED or IN_PROGRESS, was: "
                        + midFailed.getTasks().get(3).getStatus());

        // Sweep root to propagate failure
        sweep(rootWorkflowId);

        // Verify root workflow is FAILED
        Workflow rootFailed = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, rootFailed.getStatus());
        assertEquals(4, rootFailed.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, rootFailed.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootFailed.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootFailed.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootFailed.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                rootFailed.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || rootFailed.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS,
                "Root JOIN should be CANCELED or IN_PROGRESS, was: "
                        + rootFailed.getTasks().get(3).getStatus());
    }

    @AfterEach
    void restoreTaskDef() {
        if (persistedTask2Definition != null) {
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A rerun is executed on the root workflow.
     *
     * <p>Expectation: The root workflow gets a new execution with the same id and spawns a NEW
     * mid-level workflow, which in turn spawns a NEW leaf workflow. When the NEW leaf workflow
     * completes successfully, both the NEW mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test rerun on the root-level in a 3-level subworkflow")
    void testRerunOnRootLevelIn3LevelSubworkflow() {
        // do a rerun on the root workflow
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(rootWorkflowId);
        workflowExecutor.rerun(reRunWorkflowRequest);
        Task rerunRootSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (rerunRootSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rerunRootSubWfTask.getTaskId());
        }

        // Verify that the root workflow created a new execution
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(3).getStatus());

        // poll and complete integration_task_2 in root and mid level workflow
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String newMidLevelWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newMidLevelWorkflowId);

        // The root workflow has an integration_task_2. Its subworkflow also has an
        // integration_task_2.
        // We have NO guarantees which will be polled and completed first.
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> {
                            workflowTestUtil.pollAndCompleteTask(
                                    "integration_task_2",
                                    "task2.integration.worker",
                                    Map.of("op", "task2.done"));
                            Workflow rootWfCheck =
                                    workflowExecutionService.getExecutionStatus(
                                            rootWorkflowId, true);
                            Workflow midWfCheck =
                                    workflowExecutionService.getExecutionStatus(
                                            newMidLevelWorkflowId, true);
                            return rootWfCheck.getStatus() == Workflow.WorkflowStatus.RUNNING
                                    && rootWfCheck
                                            .getTasks()
                                            .get(2)
                                            .getTaskType()
                                            .equals("integration_task_2")
                                    && rootWfCheck.getTasks().get(2).getStatus()
                                            == Task.Status.COMPLETED
                                    && midWfCheck.getStatus() == Workflow.WorkflowStatus.RUNNING
                                    && midWfCheck
                                            .getTasks()
                                            .get(2)
                                            .getTaskType()
                                            .equals("integration_task_2")
                                    && midWfCheck.getTasks().get(2).getStatus()
                                            == Task.Status.COMPLETED;
                        });

        Task newMidRerunSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (newMidRerunSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, newMidRerunSubWfTask.getTaskId());
        }

        // Verify that a new mid level workflow is created and is in RUNNING state
        assertNotEquals(newMidLevelWorkflowId, midLevelWorkflowId);
        Workflow newMidWf =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidWf.getStatus());
        assertEquals(4, newMidWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, newMidWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, newMidWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, newMidWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", newMidWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, newMidWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, newMidWf.getTasks().get(3).getStatus());

        // mid level workflow is in RUNNING state — get leaf workflow
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // Verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(newLeafWorkflowId, leafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());

        // poll and complete the two tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // Verify the new leaf workflow is in COMPLETED state
        Workflow newLeafCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafCompleted.getStatus());
        assertEquals(2, newLeafCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(1).getStatus());

        // Sweep the new mid level and root workflows
        sweep(newMidLevelWorkflowId);
        sweep(rootWorkflowId);

        // Execute JOIN tasks
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // Verify the new mid level workflow is in COMPLETED state
        assertWorkflowIsCompleted(newMidLevelWorkflowId);

        // Verify the root workflow is in COMPLETED state
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A rerun is executed on the mid-level workflow.
     *
     * <p>Expectation: The mid-level workflow gets a new execution with the same id and spawns a NEW
     * leaf workflow and also updates its parent (root workflow). When the NEW leaf workflow
     * completes successfully, both the mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test rerun on the mid-level in a 3-level subworkflow")
    void testRerunOnMidLevelIn3LevelSubworkflow() {
        // do a rerun on the mid level workflow
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(midLevelWorkflowId);
        workflowExecutor.rerun(reRunWorkflowRequest);
        Task rerunMidSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        t.getTaskType().equals(TASK_TYPE_SUB_WORKFLOW)
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (rerunMidSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rerunMidSubWfTask.getTaskId());
        }

        // Verify that the mid workflow created a new execution
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(4, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(3).getStatus());

        // Verify the root workflow is updated
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow rootWf =
                                    workflowExecutionService.getExecutionStatus(
                                            rootWorkflowId, true);
                            return rootWf.getStatus() == Workflow.WorkflowStatus.RUNNING
                                    && rootWf.getTasks().size() == 4
                                    && rootWf.getTasks().get(0).getTaskType().equals(TASK_TYPE_FORK)
                                    && rootWf.getTasks().get(0).getStatus() == Task.Status.COMPLETED
                                    && rootWf.getTasks()
                                            .get(1)
                                            .getTaskType()
                                            .equals(TASK_TYPE_SUB_WORKFLOW)
                                    && rootWf.getTasks().get(1).getStatus()
                                            == Task.Status.IN_PROGRESS
                                    && rootWf.getTasks()
                                            .get(2)
                                            .getTaskType()
                                            .equals("integration_task_2")
                                    && rootWf.getTasks().get(2).getStatus() == Task.Status.COMPLETED
                                    && rootWf.getTasks().get(3).getTaskType().equals(TASK_TYPE_JOIN)
                                    // The reopened parent is also pushed for background evaluation,
                                    // so the JOIN may
                                    // still be CANCELED (from the failed run) or already reopened
                                    // to IN_PROGRESS by
                                    // the sweeper. Either is valid; only completion is not.
                                    && (rootWf.getTasks().get(3).getStatus() == Task.Status.CANCELED
                                            || rootWf.getTasks().get(3).getStatus()
                                                    == Task.Status.IN_PROGRESS);
                        });

        // poll and complete integration_task_2 in the mid level workflow, then get new leaf id
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // Verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(newLeafWorkflowId, leafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());

        // poll and complete the 2 tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // Verify that the new leaf workflow reached COMPLETED state
        Workflow newLeafCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafCompleted.getStatus());
        assertEquals(2, newLeafCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(1).getStatus());

        // Sweep the mid level and root workflows
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // Execute JOIN tasks
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // Verify that the mid level and root workflows reach COMPLETED state
        assertWorkflowIsCompleted(midLevelWorkflowId);
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A rerun is executed on the leaf workflow.
     *
     * <p>Expectation: The leaf workflow gets a new execution with the same id and updates both its
     * parent (mid-level) and grandparent (root). When the leaf workflow completes successfully,
     * both the mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test rerun on the leaf-level in a 3-level subworkflow")
    void testRerunOnLeafLevelIn3LevelSubworkflow() {
        // do a rerun on the leaf workflow
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(leafWorkflowId);
        workflowExecutor.rerun(reRunWorkflowRequest);

        // Verify that the leaf workflow created a new execution
        Workflow leafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWf.getStatus());
        assertEquals(1, leafWf.getTasks().size());
        assertEquals("integration_task_1", leafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWf.getTasks().get(0).getStatus());

        // Verify that the mid-level workflow is updated
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(4, midWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midWf.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                midWf.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || midWf.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS,
                "Mid-level JOIN should be CANCELED or IN_PROGRESS, was: "
                        + midWf.getTasks().get(3).getStatus());

        // Verify that the root workflow is updated
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(4, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWf.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                rootWf.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || rootWf.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS,
                "Root JOIN should be CANCELED or IN_PROGRESS, was: "
                        + rootWf.getTasks().get(3).getStatus());

        // Sweep the mid level and root workflows
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);
        String midJoinId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();
        String rootJoinId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTaskByRefName("fanouttask_join")
                        .getTaskId();

        // Verify that the mid level workflow's JOIN is updated
        Workflow midAfterSweep =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterSweep.getStatus());
        assertEquals(4, midAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                midAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", midAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterSweep.getTasks().get(3).getStatus());

        // Verify that the root workflow's JOIN is updated
        Workflow rootAfterSweep = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterSweep.getStatus());
        assertEquals(4, rootAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootAfterSweep.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootAfterSweep.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                rootAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", rootAfterSweep.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootAfterSweep.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootAfterSweep.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterSweep.getTasks().get(3).getStatus());

        // poll and complete both tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // Verify that the leaf workflow reached COMPLETED state
        Workflow leafCompleted = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, leafCompleted.getStatus());
        assertEquals(2, leafCompleted.getTasks().size());
        assertEquals("integration_task_1", leafCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafCompleted.getTasks().get(1).getStatus());

        // Sweep the mid level and root workflows
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // Execute JOIN tasks
        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // Verify that the mid level and root workflows reach COMPLETED state
        assertWorkflowIsCompleted(midLevelWorkflowId);
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    void assertWorkflowIsCompleted(String workflowId) {
        Workflow wf = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                wf.getStatus(),
                "Expected workflow " + workflowId + " to be COMPLETED but was " + wf.getStatus());
        assertEquals(4, wf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, wf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, wf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(1).getStatus());
        assertFalse(wf.getTasks().get(1).isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", wf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, wf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, wf.getTasks().get(3).getStatus());
    }
}
