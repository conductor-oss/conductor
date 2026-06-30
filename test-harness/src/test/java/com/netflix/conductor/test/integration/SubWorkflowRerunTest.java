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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class SubWorkflowRerunTest extends AbstractSpecification {

    private static final String WORKFLOW_WITH_SUBWORKFLOW = "integration_test_wf_with_sub_wf";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    @Autowired SubWorkflow subWorkflowTask;

    String rootWorkflowId;
    String midLevelWorkflowId;
    String leafWorkflowId;

    TaskDef persistedTask2Definition;

    @BeforeEach
    void setup() throws Exception {
        workflowTestUtil.registerWorkflows(
                "simple_workflow_1_integration_test.json",
                "workflow_with_sub_workflow_1_integration_test.json");

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
        metadataService.getWorkflowDef(WORKFLOW_WITH_SUBWORKFLOW, 1);

        // Start the root workflow
        String correlationId = "rerun_on_root_in_3level_wf";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("subwf", WORKFLOW_WITH_SUBWORKFLOW);
        input.put("nextSubwf", SIMPLE_WORKFLOW);

        rootWorkflowId = startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1, correlationId, input, null);

        // Verify root workflow is RUNNING with first task SCHEDULED
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(1, rootWf.getTasks().size());
        assertEquals("integration_task_1", rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, rootWf.getTasks().get(0).getStatus());

        // Poll and complete integration_task_1
        Task task1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));
        verifyPolledAndAcknowledgedTask(task1);

        // Execute the SUB_WORKFLOW system task on root to start it
        Task rootSubWfTask =
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
        if (rootSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rootSubWfTask.getTaskId());
        }

        // Verify root workflow: integration_task_1 COMPLETED, SUB_WORKFLOW IN_PROGRESS
        Workflow rootWorkflowInstance =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWorkflowInstance.getStatus());
        assertEquals(2, rootWorkflowInstance.getTasks().size());
        assertEquals("integration_task_1", rootWorkflowInstance.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWorkflowInstance.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWorkflowInstance.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWorkflowInstance.getTasks().get(1).getStatus());

        // Sweep mid-level workflow so its tasks get scheduled
        midLevelWorkflowId = rootWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(midLevelWorkflowId);

        // Verify mid-level workflow is RUNNING with first task SCHEDULED
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(1, midWf.getTasks().size());
        assertEquals("integration_task_1", midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, midWf.getTasks().get(0).getStatus());

        // Poll and complete integration_task_1 in mid-level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));

        // Execute the SUB_WORKFLOW system task on mid-level
        Task midSubWfTask =
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
        if (midSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midSubWfTask.getTaskId());
        }
        leafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(leafWorkflowId);

        // Verify leaf workflow is RUNNING with first task SCHEDULED
        Workflow leafWorkflowInstance =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWorkflowInstance.getStatus());
        assertEquals(1, leafWorkflowInstance.getTasks().size());
        assertEquals("integration_task_1", leafWorkflowInstance.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWorkflowInstance.getTasks().get(0).getStatus());

        // Poll and complete integration_task_1, then fail integration_task_2 in leaf
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndFailTask(
                "integration_task_2", "task2.integration.worker", "failed");

        // Verify leaf workflow is FAILED
        Workflow failedLeaf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, failedLeaf.getStatus());
        assertEquals(2, failedLeaf.getTasks().size());
        assertEquals("integration_task_1", failedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedLeaf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", failedLeaf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, failedLeaf.getTasks().get(1).getStatus());

        // Sweep mid-level workflow so it picks up the leaf failure
        sweep(midLevelWorkflowId);

        // Verify mid-level workflow is FAILED
        Workflow failedMid = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, failedMid.getStatus());
        assertEquals(2, failedMid.getTasks().size());
        assertEquals("integration_task_1", failedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, failedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, failedMid.getTasks().get(1).getStatus());

        // Sweep root workflow so it picks up mid-level failure
        sweep(rootWorkflowId);

        // Verify root workflow is FAILED
        Workflow failedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, failedRoot.getStatus());
        assertEquals(2, failedRoot.getTasks().size());
        assertEquals("integration_task_1", failedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, failedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, failedRoot.getTasks().get(1).getStatus());
    }

    @AfterEach
    void restoreTaskDefinition() {
        metadataService.updateTaskDef(persistedTask2Definition);
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A rerun is executed on the root workflow.
     *
     * <p>Expectation: The root workflow spawns a NEW mid-level workflow, which in turn spawns a NEW
     * leaf workflow. When the leaf workflow completes successfully, both the NEW mid-level and root
     * workflows also complete successfully.
     */
    @Test
    @DisplayName("Test rerun on the root-level in a 3-level subworkflow")
    void testRerunOnRootLevelIn3LevelSubworkflow() {
        // when: do a rerun on the root workflow
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(rootWorkflowId);
        workflowExecutor.rerun(reRunWorkflowRequest);

        // then: poll and complete the 'integration_task_1' task
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op1", "task1.done"));

        // and: execute the SUB_WORKFLOW task on the root to create the new mid-level workflow
        Task rootSubWfTask =
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
        if (rootSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rootSubWfTask.getTaskId());
        }

        // and: verify that the root workflow created a new SUB_WORKFLOW task
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(2, rootWf.getTasks().size());
        assertEquals("integration_task_1", rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());

        // when: sweep the new mid-level workflow
        String newMidLevelWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newMidLevelWorkflowId);

        // then: verify that a new mid level workflow is created and is in RUNNING state
        assertNotEquals(midLevelWorkflowId, newMidLevelWorkflowId);
        Workflow newMidWf =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidWf.getStatus());
        assertEquals(1, newMidWf.getTasks().size());
        assertEquals("integration_task_1", newMidWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newMidWf.getTasks().get(0).getStatus());

        // when: poll and complete the integration_task_1 task in the mid-level workflow
        Task polledAndCompletedTry1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"),
                        5);

        // then: verify that the 'integration_task_1' was polled and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry1);

        // and: execute the SUB_WORKFLOW task on the mid level to create the new leaf workflow
        Task midSubWfTask =
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
        if (midSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midSubWfTask.getTaskId());
        }

        // and: sweep the new leaf workflow
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());

        // when: poll and complete the two tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // then: the new leaf workflow is in COMPLETED state
        Workflow completedLeaf =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeaf.getStatus());
        assertEquals(2, completedLeaf.getTasks().size());
        assertEquals("integration_task_1", completedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeaf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(1).getStatus());

        // when: the new mid level and root workflows are decided
        sweep(newMidLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: the new mid level workflow is in COMPLETED state
        Workflow completedMid =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedMid.getStatus());
        assertEquals(2, completedMid.getTasks().size());
        assertEquals("integration_task_1", completedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(1).getStatus());

        // then: the root workflow is in COMPLETED state
        Workflow completedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedRoot.getStatus());
        assertEquals(2, completedRoot.getTasks().size());
        assertEquals("integration_task_1", completedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(1).getStatus());
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A rerun is executed with taskId on the root workflow.
     *
     * <p>Expectation: The root workflow gets a new execution with the same id and both the
     * mid-level workflow and leaf workflows are also reran. When the leaf workflow completes
     * successfully, both the mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test rerun on the root-level with taskId in a 3-level subworkflow")
    void testRerunOnRootLevelWithTaskIdIn3LevelSubworkflow() {
        // when: do a rerun on the root workflow with taskId
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(rootWorkflowId);
        String reRunTaskId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .get(0)
                        .getTaskId();
        reRunWorkflowRequest.setReRunFromTaskId(reRunTaskId);
        workflowExecutor.rerun(reRunWorkflowRequest);

        // then: poll and complete the 'integration_task_1' task
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op1", "task1.done"));

        // and: execute the SUB_WORKFLOW task on the root to create the new mid-level workflow
        Task rootSubWfTask =
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
        if (rootSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rootSubWfTask.getTaskId());
        }

        // and: verify that the root workflow created a new SUB_WORKFLOW task
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(2, rootWf.getTasks().size());
        assertEquals("integration_task_1", rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());

        // when: sweep the new mid-level workflow
        String newMidLevelWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newMidLevelWorkflowId);

        // then: verify that a new mid level workflow is created and is in RUNNING state
        assertNotEquals(midLevelWorkflowId, newMidLevelWorkflowId);
        Workflow newMidWf =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidWf.getStatus());
        assertEquals(1, newMidWf.getTasks().size());
        assertEquals("integration_task_1", newMidWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newMidWf.getTasks().get(0).getStatus());

        // when: poll and complete the integration_task_1 task in the mid-level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));

        // and: execute the SUB_WORKFLOW task on the mid level to create the new leaf workflow
        Task midSubWfTask =
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
        if (midSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midSubWfTask.getTaskId());
        }

        // and: sweep the new leaf workflow
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());

        // when: poll and complete the two tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // then: the new leaf workflow is in COMPLETED state
        Workflow completedLeaf =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeaf.getStatus());
        assertEquals(2, completedLeaf.getTasks().size());
        assertEquals("integration_task_1", completedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeaf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(1).getStatus());

        // when: the new mid level and root workflows are decided
        sweep(newMidLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: the new mid level workflow is in COMPLETED state
        Workflow completedMid =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedMid.getStatus());
        assertEquals(2, completedMid.getTasks().size());
        assertEquals("integration_task_1", completedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(1).getStatus());

        // then: the root workflow is in COMPLETED state
        Workflow completedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedRoot.getStatus());
        assertEquals(2, completedRoot.getTasks().size());
        assertEquals("integration_task_1", completedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(1).getStatus());
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
        // when: do a rerun on the mid level workflow
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(midLevelWorkflowId);
        workflowExecutor.rerun(reRunWorkflowRequest);

        // then: verify that the mid workflow created a new execution
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(1, midWf.getTasks().size());
        assertEquals("integration_task_1", midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, midWf.getTasks().get(0).getStatus());

        // and: verify the SUB_WORKFLOW task in root workflow is updated
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(2, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());

        // when: poll and complete the task in the mid level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));

        // and: execute the SUB_WORKFLOW task on the mid level to create the new leaf workflow
        Task midSubWfTask =
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
        if (midSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midSubWfTask.getTaskId());
        }

        // and: sweep the new leaf workflow
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());

        // when: poll and complete the 2 tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // then: verify that the new leaf workflow reached COMPLETED state
        Workflow completedLeaf =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeaf.getStatus());
        assertEquals(2, completedLeaf.getTasks().size());
        assertEquals("integration_task_1", completedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeaf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(1).getStatus());

        // when: the mid level and root workflows are decided
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: verify that the mid level workflow reaches COMPLETED state
        Workflow completedMid =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedMid.getStatus());
        assertEquals(2, completedMid.getTasks().size());
        assertEquals("integration_task_1", completedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(1).getStatus());

        // and: verify that the root workflow reaches COMPLETED state
        // (subworkflowChanged flag is reset after decide)
        Workflow completedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedRoot.getStatus());
        assertEquals(2, completedRoot.getTasks().size());
        assertEquals("integration_task_1", completedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(1).getStatus());
        assertFalse(
                completedRoot
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A rerun is executed on the mid-level workflow with taskId.
     *
     * <p>Expectation: The mid-level workflow gets a new execution with the same id and spawns a NEW
     * leaf workflow and also updates its parent (root workflow). When the NEW leaf workflow
     * completes successfully, both the mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test rerun on the mid-level with taskId in a 3-level subworkflow")
    void testRerunOnMidLevelWithTaskIdIn3LevelSubworkflow() {
        // when: do a rerun on the mid level workflow with taskId
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(midLevelWorkflowId);
        String reRunTaskId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(0)
                        .getTaskId();
        reRunWorkflowRequest.setReRunFromTaskId(reRunTaskId);
        workflowExecutor.rerun(reRunWorkflowRequest);

        // then: verify that the mid workflow created a new execution
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(1, midWf.getTasks().size());
        assertEquals("integration_task_1", midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, midWf.getTasks().get(0).getStatus());

        // and: verify the SUB_WORKFLOW task in root workflow is updated
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(2, rootWf.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());

        // when: poll and complete the task in the mid level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));

        // and: execute the SUB_WORKFLOW task on the mid level to create the new leaf workflow
        Task midSubWfTask =
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
        if (midSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midSubWfTask.getTaskId());
        }

        // and: sweep the new leaf workflow
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());

        // when: poll and complete the 2 tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // then: verify that the new leaf workflow reached COMPLETED state
        Workflow completedLeaf =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeaf.getStatus());
        assertEquals(2, completedLeaf.getTasks().size());
        assertEquals("integration_task_1", completedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeaf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(1).getStatus());

        // when: the mid level and root workflows are decided
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: verify that the mid level workflow reaches COMPLETED state
        Workflow completedMid =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedMid.getStatus());
        assertEquals(2, completedMid.getTasks().size());
        assertEquals("integration_task_1", completedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(1).getStatus());

        // and: verify that the root workflow reaches COMPLETED state
        // (subworkflowChanged flag is reset after decide)
        Workflow completedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedRoot.getStatus());
        assertEquals(2, completedRoot.getTasks().size());
        assertEquals("integration_task_1", completedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(1).getStatus());
        assertFalse(
                completedRoot
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
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
        // when: do a rerun on the leaf workflow
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(leafWorkflowId);
        workflowExecutor.rerun(reRunWorkflowRequest);

        // then: verify that the leaf workflow creates a new execution
        Workflow leafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWf.getStatus());
        assertEquals(1, leafWf.getTasks().size());
        assertEquals("integration_task_1", leafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWf.getTasks().get(0).getStatus());

        // then: verify that the mid-level workflow is updated
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(2, midWf.getTasks().size());
        assertEquals("integration_task_1", midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());

        // and: verify that the root workflow's SUB_WORKFLOW is updated
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(2, rootWf.getTasks().size());
        assertEquals("integration_task_1", rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());

        // when: poll and complete the scheduled tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // then: verify that the leaf workflow reached COMPLETED state
        Workflow completedLeaf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeaf.getStatus());
        assertEquals(2, completedLeaf.getTasks().size());
        assertEquals("integration_task_1", completedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeaf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(1).getStatus());

        // when: the mid level and root workflows are decided
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: verify that the mid level workflow reaches COMPLETED state
        // (subworkflowChanged flag is reset after decide)
        Workflow completedMid =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedMid.getStatus());
        assertEquals(2, completedMid.getTasks().size());
        assertEquals("integration_task_1", completedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(1).getStatus());
        assertFalse(
                completedMid
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide

        // and: verify that the root workflow reaches COMPLETED state
        Workflow completedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedRoot.getStatus());
        assertEquals(2, completedRoot.getTasks().size());
        assertEquals("integration_task_1", completedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(1).getStatus());
        assertFalse(
                completedRoot
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A rerun is executed on the leaf workflow with taskId.
     *
     * <p>Expectation: The leaf workflow gets a new execution with the same id and updates both its
     * parent (mid-level) and grandparent (root). When the leaf workflow completes successfully,
     * both the mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test rerun on the leaf-level with taskId in a 3-level subworkflow")
    void testRerunOnLeafLevelWithTaskIdIn3LevelSubworkflow() {
        // when: do a rerun on the leaf workflow with taskId
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(leafWorkflowId);
        String reRunTaskId =
                workflowExecutionService
                        .getExecutionStatus(leafWorkflowId, true)
                        .getTasks()
                        .get(0)
                        .getTaskId();
        reRunWorkflowRequest.setReRunFromTaskId(reRunTaskId);
        workflowExecutor.rerun(reRunWorkflowRequest);

        // then: verify that the leaf workflow creates a new execution
        Workflow leafWf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWf.getStatus());
        assertEquals(1, leafWf.getTasks().size());
        assertEquals("integration_task_1", leafWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWf.getTasks().get(0).getStatus());

        // then: verify that the mid-level workflow is updated
        Workflow midWf = workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midWf.getStatus());
        assertEquals(2, midWf.getTasks().size());
        assertEquals("integration_task_1", midWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midWf.getTasks().get(1).getStatus());

        // and: verify that the root workflow's SUB_WORKFLOW is updated
        Workflow rootWf = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWf.getStatus());
        assertEquals(2, rootWf.getTasks().size());
        assertEquals("integration_task_1", rootWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWf.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWf.getTasks().get(1).getStatus());

        // when: poll and complete the scheduled tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // then: verify that the leaf workflow reached COMPLETED state
        Workflow completedLeaf = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedLeaf.getStatus());
        assertEquals(2, completedLeaf.getTasks().size());
        assertEquals("integration_task_1", completedLeaf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", completedLeaf.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedLeaf.getTasks().get(1).getStatus());

        // when: the mid level and root workflows are decided
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: verify that the mid level workflow reaches COMPLETED state
        // (subworkflowChanged flag is reset after decide)
        Workflow completedMid =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedMid.getStatus());
        assertEquals(2, completedMid.getTasks().size());
        assertEquals("integration_task_1", completedMid.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedMid.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedMid.getTasks().get(1).getStatus());
        assertFalse(
                completedMid
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide

        // and: verify that the root workflow reaches COMPLETED state
        Workflow completedRoot = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedRoot.getStatus());
        assertEquals(2, completedRoot.getTasks().size());
        assertEquals("integration_task_1", completedRoot.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, completedRoot.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedRoot.getTasks().get(1).getStatus());
        assertFalse(
                completedRoot
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
    }
}
