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
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class SubWorkflowRestartTest extends AbstractSpecification {

    private static final String WORKFLOW_WITH_SUBWORKFLOW = "integration_test_wf_with_sub_wf";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    @Autowired SubWorkflow subWorkflowTask;

    String rootWorkflowId;
    String midLevelWorkflowId;
    String leafWorkflowId;

    TaskDef persistedTask2Definition;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "simple_one_task_sub_workflow_integration_test.json",
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

        // Input required to start the workflow execution
        String correlationId = "retry_on_root_in_3level_wf";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("subwf", WORKFLOW_WITH_SUBWORKFLOW);
        input.put("nextSubwf", SIMPLE_WORKFLOW);

        // Start the workflow
        rootWorkflowId = startWorkflow(WORKFLOW_WITH_SUBWORKFLOW, 1, correlationId, input, null);

        // Verify that the workflow is in a RUNNING state
        Workflow rootWfInitial = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWfInitial.getStatus());
        assertEquals(1, rootWfInitial.getTasks().size());
        assertEquals("integration_task_1", rootWfInitial.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, rootWfInitial.getTasks().get(0).getStatus());

        // Poll and complete the integration_task_1 task
        Task pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask);

        // The subworkflow task should be in SCHEDULED state; start it via system task call
        Task rootSubWfTask =
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
        if (rootSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, rootSubWfTask.getTaskId());
        }

        // Verify that the 'sub_workflow_task' is in IN_PROGRESS state
        Workflow rootWorkflowInstance =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWorkflowInstance.getStatus());
        assertEquals(2, rootWorkflowInstance.getTasks().size());
        assertEquals("integration_task_1", rootWorkflowInstance.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWorkflowInstance.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWorkflowInstance.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootWorkflowInstance.getTasks().get(1).getStatus());

        // The mid-level subworkflow is decided so its first task is scheduled
        midLevelWorkflowId = rootWorkflowInstance.getTasks().get(1).getSubWorkflowId();
        sweep(midLevelWorkflowId);

        // Verify that the mid-level workflow is RUNNING, and first task is in SCHEDULED state
        Workflow midLevelWfInitial =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midLevelWfInitial.getStatus());
        assertEquals(1, midLevelWfInitial.getTasks().size());
        assertEquals("integration_task_1", midLevelWfInitial.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, midLevelWfInitial.getTasks().get(0).getStatus());

        // Poll and complete the integration_task_1 task in the mid-level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));

        // The subworkflow task in mid-level should be SCHEDULED; start it via system task call
        Task midSubWfTask =
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

        // Verify that the leaf workflow is RUNNING, and first task is in SCHEDULED state
        Workflow leafWorkflowInstance =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafWorkflowInstance.getStatus());
        assertEquals(1, leafWorkflowInstance.getTasks().size());
        assertEquals("integration_task_1", leafWorkflowInstance.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafWorkflowInstance.getTasks().get(0).getStatus());

        // Poll and fail the integration_task_2 task
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

        // The mid level workflow is 'decided'
        sweep(midLevelWorkflowId);

        // The mid level subworkflow is in FAILED state
        Workflow midLevelWfFailed =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, midLevelWfFailed.getStatus());
        assertEquals(2, midLevelWfFailed.getTasks().size());
        assertEquals("integration_task_1", midLevelWfFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWfFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midLevelWfFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, midLevelWfFailed.getTasks().get(1).getStatus());

        // The root level workflow is 'decided'
        sweep(rootWorkflowId);

        // The root level workflow is in FAILED state
        Workflow rootWfFailed = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, rootWfFailed.getStatus());
        assertEquals(2, rootWfFailed.getTasks().size());
        assertEquals("integration_task_1", rootWfFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, rootWfFailed.getTasks().get(1).getStatus());
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

        // poll and complete the 'integration_task_1' task
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op1", "task1.done"));

        // execute the SUB_WORKFLOW task on the root to create the new mid-level workflow
        Task newRootSubWfTask =
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
        if (newRootSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, newRootSubWfTask.getTaskId());
        }

        // verify that the root workflow created a new SUB_WORKFLOW task
        Workflow rootAfterRestart =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterRestart.getStatus());
        assertEquals(2, rootAfterRestart.getTasks().size());
        assertEquals("integration_task_1", rootAfterRestart.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootAfterRestart.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRestart.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterRestart.getTasks().get(1).getStatus());

        String newMidLevelWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newMidLevelWorkflowId);

        // verify that a new mid level workflow is created and is in RUNNING state
        assertNotEquals(midLevelWorkflowId, newMidLevelWorkflowId);
        Workflow newMidLevelWf =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidLevelWf.getStatus());
        assertEquals(1, newMidLevelWf.getTasks().size());
        assertEquals("integration_task_1", newMidLevelWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newMidLevelWf.getTasks().get(0).getStatus());

        // poll and complete the integration_task_1 task in the mid-level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        Task newMidSubWfTask =
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
        if (newMidSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, newMidSubWfTask.getTaskId());
        }
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
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

        // the new leaf workflow is in COMPLETED state
        Workflow newLeafWfCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafWfCompleted.getStatus());
        assertEquals(2, newLeafWfCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(1).getStatus());

        // the new mid level and root workflows are 'decided'
        sweep(newMidLevelWorkflowId);
        sweep(rootWorkflowId);

        // the new mid level workflow is in COMPLETED state
        Workflow newMidLevelWfCompleted =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newMidLevelWfCompleted.getStatus());
        assertEquals(2, newMidLevelWfCompleted.getTasks().size());
        assertEquals("integration_task_1", newMidLevelWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidLevelWfCompleted.getTasks().get(0).getStatus());
        assertEquals(
                TASK_TYPE_SUB_WORKFLOW, newMidLevelWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidLevelWfCompleted.getTasks().get(1).getStatus());

        // the root workflow is in COMPLETED state
        Workflow rootWfCompleted =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootWfCompleted.getStatus());
        assertEquals(2, rootWfCompleted.getTasks().size());
        assertEquals("integration_task_1", rootWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfCompleted.getTasks().get(1).getStatus());
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
        // do a restart on the mid level workflow
        workflowExecutor.restart(midLevelWorkflowId, false);

        // verify that the mid workflow created a new execution
        Workflow midAfterRestart =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterRestart.getStatus());
        assertEquals(1, midAfterRestart.getTasks().size());
        assertEquals("integration_task_1", midAfterRestart.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, midAfterRestart.getTasks().get(0).getStatus());

        // verify the SUB_WORKFLOW task in root workflow is updated
        Workflow rootAfterMidRestart =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterMidRestart.getStatus());
        assertEquals(2, rootAfterMidRestart.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterMidRestart.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterMidRestart.getTasks().get(1).getStatus());

        // poll and complete the task in the mid level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        Task midRestartSubWfTask =
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
        if (midRestartSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, midRestartSubWfTask.getTaskId());
        }
        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWf = workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWf.getStatus());
        assertEquals(1, newLeafWf.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, newLeafWf.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", newLeafWf.getTasks().get(0).getTaskType());

        // poll and complete the 2 tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // verify that the new leaf workflow reached COMPLETED state
        Workflow newLeafWfCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafWfCompleted.getStatus());
        assertEquals(2, newLeafWfCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafWfCompleted.getTasks().get(1).getStatus());

        // the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // verify that the mid level and root workflows reach COMPLETED state
        Workflow midLevelWfCompleted =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, midLevelWfCompleted.getStatus());
        assertEquals(2, midLevelWfCompleted.getTasks().size());
        assertEquals("integration_task_1", midLevelWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWfCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midLevelWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWfCompleted.getTasks().get(1).getStatus());

        Workflow rootWfCompleted =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootWfCompleted.getStatus());
        assertEquals(2, rootWfCompleted.getTasks().size());
        assertEquals("integration_task_1", rootWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfCompleted.getTasks().get(1).getStatus());
        // flag is reset after decide
        assertFalse(rootWfCompleted.getTasks().get(1).isSubworkflowChanged());
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
        // do a restart on the leaf workflow
        workflowExecutor.restart(leafWorkflowId, false);

        // verify that the leaf workflow creates a new execution
        Workflow leafAfterRestart =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafAfterRestart.getStatus());
        assertEquals(1, leafAfterRestart.getTasks().size());
        assertEquals("integration_task_1", leafAfterRestart.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafAfterRestart.getTasks().get(0).getStatus());

        // verify that the mid-level workflow is updated
        Workflow midAfterLeafRestart =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterLeafRestart.getStatus());
        assertEquals(2, midAfterLeafRestart.getTasks().size());
        assertEquals("integration_task_1", midAfterLeafRestart.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midAfterLeafRestart.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterLeafRestart.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterLeafRestart.getTasks().get(1).getStatus());

        // verify that the root workflow's SUB_WORKFLOW is updated
        Workflow rootAfterLeafRestart =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterLeafRestart.getStatus());
        assertEquals(2, rootAfterLeafRestart.getTasks().size());
        assertEquals("integration_task_1", rootAfterLeafRestart.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootAfterLeafRestart.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterLeafRestart.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterLeafRestart.getTasks().get(1).getStatus());

        // poll and complete the scheduled task in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // verify that the leaf workflow reached COMPLETED state
        Workflow leafWfCompleted =
                workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, leafWfCompleted.getStatus());
        assertEquals(2, leafWfCompleted.getTasks().size());
        assertEquals("integration_task_1", leafWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", leafWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafWfCompleted.getTasks().get(1).getStatus());

        // the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // verify that the mid level and root workflows reach COMPLETED state
        Workflow midLevelWfCompleted =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, midLevelWfCompleted.getStatus());
        assertEquals(2, midLevelWfCompleted.getTasks().size());
        assertEquals("integration_task_1", midLevelWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWfCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midLevelWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelWfCompleted.getTasks().get(1).getStatus());
        // flag is reset after decide
        assertFalse(midLevelWfCompleted.getTasks().get(1).isSubworkflowChanged());

        Workflow rootWfCompleted =
                workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootWfCompleted.getStatus());
        assertEquals(2, rootWfCompleted.getTasks().size());
        assertEquals("integration_task_1", rootWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootWfCompleted.getTasks().get(1).getStatus());
        // flag is reset after decide
        assertFalse(rootWfCompleted.getTasks().get(1).isSubworkflowChanged());
    }
}
