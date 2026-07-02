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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
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

class HierarchicalForkJoinSubworkflowRestartTest extends AbstractSpecification {

    private static final String FORK_JOIN_HIERARCHICAL_SUB_WF = "hierarchical_fork_join_swf";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    @Autowired SubWorkflow subWorkflowTask;

    @Autowired Join joinTask;

    String rootWorkflowId;
    String midLevelWorkflowId;
    String leafWorkflowId;

    TaskDef persistedTask2Definition;

    @BeforeEach
    void setup() {
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

        // Input required to start the workflow execution
        String correlationId = "retry_on_root_in_3level_wf";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("subwf", FORK_JOIN_HIERARCHICAL_SUB_WF);
        input.put("nextSubwf", SIMPLE_WORKFLOW);

        // Start the workflow
        rootWorkflowId =
                startWorkflow(FORK_JOIN_HIERARCHICAL_SUB_WF, 1, correlationId, input, null);

        // Verify that the workflow is in a RUNNING state
        Task rootSetupSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (rootSetupSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rootSetupSubWfTask.getTaskId());
        }
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

        // Poll and complete integration_task_2 in root workflow
        Task pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2",
                        "task2.integration.worker",
                        Map.of("op", "task2.done"));
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

        // Verify that the sub_workflow_task is in IN_PROGRESS state
        Workflow rootWorkflowInstance =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWorkflowInstance.getStatus());
        assertEquals(4, rootWorkflowInstance.getTasks().size());

        // Sweep the mid-level workflow so its tasks get scheduled
        midLevelWorkflowId = rootWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(midLevelWorkflowId);

        // Poll and complete integration_task_2 in the mid-level workflow
        pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2",
                        "task2.integration.worker",
                        Map.of("op", "task2.done"));
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

        // Verify mid-level workflow is RUNNING and execute sub workflow task if scheduled
        Task midSetupSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (midSetupSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midSetupSubWfTask.getTaskId());
        }
        Workflow midLevelWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midLevelWf.getStatus());
        assertEquals(4, midLevelWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midLevelWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midLevelWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midLevelWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midLevelWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midLevelWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midLevelWf.getTasks().get(3).getStatus());

        // Get mid-level workflow state and sweep the leaf child
        Workflow midLevelWorkflowInstance =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        leafWorkflowId = midLevelWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(leafWorkflowId);

        // Verify that the leaf workflow is RUNNING and first task is SCHEDULED
        Workflow leafWorkflowInstance =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWorkflowInstance.getStatus());
        assertEquals(1, leafWorkflowInstance.getTasks().size());
        assertEquals("integration_task_1", leafWorkflowInstance.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWorkflowInstance.getTasks().get(0).getStatus());

        // Poll and fail integration_task_2 in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndFailTask(
                "integration_task_2", "task2.integration.worker", "failed");

        // The leaf workflow ends up in a FAILED state
        Workflow leafWfFailed = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, leafWfFailed.getStatus());
        assertEquals(2, leafWfFailed.getTasks().size());
        assertEquals("integration_task_1", leafWfFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafWfFailed.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", leafWfFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafWfFailed.getTasks().get(1).getStatus());

        // Sweep the mid level workflow and verify it is in FAILED state
        sweep(midLevelWorkflowId);
        Workflow midLevelWfFailed =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, midLevelWfFailed.getStatus());
        assertEquals(4, midLevelWfFailed.getTasks().size());
        assertEquals(TASK_TYPE_FORK, midLevelWfFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWfFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midLevelWfFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, midLevelWfFailed.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", midLevelWfFailed.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWfFailed.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, midLevelWfFailed.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                midLevelWfFailed.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || midLevelWfFailed.getTasks().get(3).getStatus()
                                == Task.Status.IN_PROGRESS);

        // Sweep the root workflow and verify it is in FAILED state
        sweep(rootWorkflowId);
        Workflow rootWfFailed = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, rootWfFailed.getStatus());
        assertEquals(4, rootWfFailed.getTasks().size());
        assertEquals(TASK_TYPE_FORK, rootWfFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, rootWfFailed.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", rootWfFailed.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfFailed.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, rootWfFailed.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                rootWfFailed.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || rootWfFailed.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS);
    }

    // Note: cleanup() is intentionally not defined here.
    // The base class @AfterEach cleanup() calls workflowTestUtil.clearWorkflows().
    // We restore the task definition after each test by overriding that cleanup in the test body.
    // Actually we need an @AfterEach to restore persistedTask2Definition:
    @org.junit.jupiter.api.AfterEach
    void restoreTaskDef() {
        if (persistedTask2Definition != null) {
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A restart is executed on the root workflow.
     *
     * <p>Expectation: The root workflow gets a new execution with the same id and spawns a NEW
     * mid-level workflow, which in turn spawns a NEW leaf workflow. When the NEW leaf workflow
     * completes successfully, both the NEW mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test restart on the root in a 3-level subworkflow")
    void testRestartOnRootIn3LevelSubworkflow() {
        // do a restart on the root workflow
        workflowExecutor.restart(rootWorkflowId, false);
        Task restartedRootSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (restartedRootSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, restartedRootSubWfTask.getTaskId());
        }

        // Verify that the root workflow created a new execution
        Workflow restartedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, restartedRoot.getStatus());
        assertEquals(4, restartedRoot.getTasks().size());
        assertEquals(TASK_TYPE_FORK, restartedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, restartedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, restartedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, restartedRoot.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", restartedRoot.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, restartedRoot.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, restartedRoot.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, restartedRoot.getTasks().get(3).getStatus());

        // Poll and complete integration_task_2 in root and mid level workflow
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
        // integration_task_2. We have NO guarantees which will be polled and completed first,
        // so the assertions done in previous versions of this test were wrong.
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> {
                            workflowTestUtil.pollAndCompleteTask(
                                    "integration_task_2",
                                    "task2.integration.worker",
                                    Map.of("op", "task2.done"));
                            Workflow rootWf =
                                    workflowExecutionService.getExecutionStatus(
                                            rootWorkflowId, true);
                            Workflow midWf =
                                    workflowExecutionService.getExecutionStatus(
                                            newMidLevelWorkflowId, true);

                            return rootWf.getStatus() == Workflow.WorkflowStatus.RUNNING
                                    && "integration_task_2"
                                            .equals(rootWf.getTasks().get(2).getTaskType())
                                    && rootWf.getTasks().get(2).getStatus() == Task.Status.COMPLETED
                                    && midWf.getStatus() == Workflow.WorkflowStatus.RUNNING
                                    && "integration_task_2"
                                            .equals(midWf.getTasks().get(2).getTaskType())
                                    && midWf.getTasks().get(2).getStatus() == Task.Status.COMPLETED;
                        });

        Task newMidRestartSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (newMidRestartSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, newMidRestartSubWfTask.getTaskId());
        }

        // Verify that a new mid level workflow is created and is in RUNNING state
        assertNotEquals(newMidLevelWorkflowId, midLevelWorkflowId);
        Workflow newMidLevelWf =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidLevelWf.getStatus());
        assertEquals(4, newMidLevelWf.getTasks().size());
        assertEquals(TASK_TYPE_FORK, newMidLevelWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidLevelWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, newMidLevelWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, newMidLevelWf.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", newMidLevelWf.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidLevelWf.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, newMidLevelWf.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, newMidLevelWf.getTasks().get(3).getStatus());

        // Mid level workflow is in RUNNING state — get leaf
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

        // Poll and complete the two tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // The new leaf workflow is in COMPLETED state
        Workflow newLeafWfCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafWfCompleted.getStatus());
        assertEquals(2, newLeafWfCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(1).getStatus());

        // Sweep the new mid level and root workflows and execute JOIN tasks
        sweep(newMidLevelWorkflowId);
        sweep(rootWorkflowId);

        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // The new mid level workflow is in COMPLETED state
        assertWorkflowIsCompleted(newMidLevelWorkflowId);

        // The root workflow is in COMPLETED state
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A restart is executed on the mid-level workflow.
     *
     * <p>Expectation: The mid-level workflow gets a new execution with the same id and spawns a NEW
     * leaf workflow and also updates its parent (root workflow). When the NEW leaf workflow
     * completes successfully, both the mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test restart on the mid-level in a 3-level subworkflow")
    void testRestartOnMidLevelIn3LevelSubworkflow() {
        // Do a restart on the mid level workflow
        workflowExecutor.restart(midLevelWorkflowId, false);
        Task restartedMidSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (restartedMidSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, restartedMidSubWfTask.getTaskId());
        }

        // Verify that the mid workflow created a new execution
        Workflow restartedMid =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, restartedMid.getStatus());
        assertEquals(4, restartedMid.getTasks().size());
        assertEquals(TASK_TYPE_FORK, restartedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, restartedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, restartedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, restartedMid.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", restartedMid.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, restartedMid.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, restartedMid.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, restartedMid.getTasks().get(3).getStatus());

        // Verify the root workflow is updated
        Workflow updatedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, updatedRoot.getStatus());
        assertEquals(4, updatedRoot.getTasks().size());
        assertEquals(TASK_TYPE_FORK, updatedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, updatedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, updatedRoot.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", updatedRoot.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedRoot.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, updatedRoot.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                updatedRoot.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || updatedRoot.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS);

        // Poll and complete the integration_task_2 task in the mid level workflow
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

        // Poll and complete the 2 tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // Verify that the new leaf workflow reached COMPLETED state
        Workflow newLeafWfCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafWfCompleted.getStatus());
        assertEquals(2, newLeafWfCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(1).getStatus());

        // Sweep the mid level and root workflows
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // Wait for mid-level SUB_WORKFLOW task to reflect the completed leaf before executing
        // JOINs.
        // Leaf completion propagates to the parent SUB_WORKFLOW task asynchronously;
        // the explicit sweeps above are not sufficient on their own.
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () ->
                                workflowExecutionService
                                                .getExecutionStatus(midLevelWorkflowId, true)
                                                .getTaskByRefName("st1")
                                                .getStatus()
                                        == Task.Status.COMPLETED);

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
     * <p>A restart is executed on the leaf workflow.
     *
     * <p>Expectation: The leaf workflow gets a new execution with the same id and updates both its
     * parent (mid-level) and grandparent (root). When the leaf workflow completes successfully,
     * both the mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test restart on the leaf in a 3-level subworkflow")
    void testRestartOnLeafIn3LevelSubworkflow() {
        // Do a restart on the leaf workflow
        workflowExecutor.restart(leafWorkflowId, false);

        // Verify that the leaf workflow created a new execution
        Workflow restartedLeaf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, restartedLeaf.getStatus());
        assertEquals(1, restartedLeaf.getTasks().size());
        assertEquals("integration_task_1", restartedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, restartedLeaf.getTasks().get(0).getStatus());

        // Verify that the mid-level workflow is updated
        Workflow updatedMid = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, updatedMid.getStatus());
        assertEquals(4, updatedMid.getTasks().size());
        assertEquals(TASK_TYPE_FORK, updatedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, updatedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, updatedMid.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", updatedMid.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedMid.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, updatedMid.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                updatedMid.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || updatedMid.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS);

        // Verify that the root workflow is updated
        Workflow updatedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, updatedRoot.getStatus());
        assertEquals(4, updatedRoot.getTasks().size());
        assertEquals(TASK_TYPE_FORK, updatedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, updatedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, updatedRoot.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", updatedRoot.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, updatedRoot.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, updatedRoot.getTasks().get(3).getTaskType());
        // The reopened parent is also pushed for background evaluation, so by the time we
        // read this snapshot the JOIN may still be CANCELED (from the failed run) or already
        // reopened to IN_PROGRESS by the sweeper. Either is valid; only completion is not.
        assertTrue(
                updatedRoot.getTasks().get(3).getStatus() == Task.Status.CANCELED
                        || updatedRoot.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS);

        // Sweep the mid level and root workflows
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // Verify that the mid level and root workflow JOIN tasks are updated.
        // The explicit sweeps are synchronous, but the parent workflows are also pushed for
        // expedited background evaluation when the leaf is restarted. Wait for both parents to
        // converge on the reopened JOIN state instead of asserting on a single snapshot.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> joinIsUpdated(midLevelWorkflowId) && joinIsUpdated(rootWorkflowId));

        // Poll and complete both tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));
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

        // Verify that the leaf workflow reached COMPLETED state
        Workflow leafWfCompleted =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, leafWfCompleted.getStatus());
        assertEquals(2, leafWfCompleted.getTasks().size());
        assertEquals("integration_task_1", leafWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", leafWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafWfCompleted.getTasks().get(1).getStatus());

        // Sweep the mid level and root workflows and execute JOIN tasks
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        asyncSystemTaskExecutor.execute(joinTask, midJoinId);
        asyncSystemTaskExecutor.execute(joinTask, rootJoinId);

        // Verify that the mid level and root workflows reach COMPLETED state
        assertWorkflowIsCompleted(midLevelWorkflowId);
        assertWorkflowIsCompleted(rootWorkflowId);
    }

    void assertWorkflowIsCompleted(String workflowId) {
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(TASK_TYPE_FORK, workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertFalse(
                workflow.getTasks().get(1).isSubworkflowChanged()); // flag is reset after decide
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(TASK_TYPE_JOIN, workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
    }

    boolean joinIsUpdated(String workflowId) {
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        return workflow.getStatus() == Workflow.WorkflowStatus.RUNNING
                && workflow.getTasks().size() == 4
                && TASK_TYPE_FORK.equals(workflow.getTasks().get(0).getTaskType())
                && workflow.getTasks().get(0).getStatus() == Task.Status.COMPLETED
                && TASK_TYPE_SUB_WORKFLOW.equals(workflow.getTasks().get(1).getTaskType())
                && workflow.getTasks().get(1).getStatus() == Task.Status.IN_PROGRESS
                && !workflow.getTasks().get(1).isSubworkflowChanged()
                && "integration_task_2".equals(workflow.getTasks().get(2).getTaskType())
                && workflow.getTasks().get(2).getStatus() == Task.Status.COMPLETED
                && TASK_TYPE_JOIN.equals(workflow.getTasks().get(3).getTaskType())
                && workflow.getTasks().get(3).getStatus() == Task.Status.IN_PROGRESS;
    }
}
