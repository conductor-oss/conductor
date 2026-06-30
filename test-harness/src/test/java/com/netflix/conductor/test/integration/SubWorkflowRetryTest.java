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

class SubWorkflowRetryTest extends AbstractSpecification {

    @Autowired SubWorkflow subWorkflowTask;

    private static final String WORKFLOW_WITH_SUBWORKFLOW = "integration_test_wf_with_sub_wf";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    String rootWorkflowId;
    String midLevelWorkflowId;
    String leafWorkflowId;

    TaskDef persistedTask2Definition;

    @BeforeEach
    void setup() throws Exception {
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

        // Verify existing workflow definitions
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
        Workflow rootWorkflow = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootWorkflow.getStatus());
        assertEquals(1, rootWorkflow.getTasks().size());
        assertEquals("integration_task_1", rootWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, rootWorkflow.getTasks().get(0).getStatus());

        // Poll and complete the integration_task_1 task
        Task pollAndCompleteTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1",
                        "task1.integration.worker",
                        Map.of("op", "task1.done"));

        // Verify that the 'integration_task_1' was polled and acknowledged
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

        // Verify that the 'sub_workflow_task' is in a IN_PROGRESS state
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
        Workflow midLevelWorkflow =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midLevelWorkflow.getStatus());
        assertEquals(1, midLevelWorkflow.getTasks().size());
        assertEquals("integration_task_1", midLevelWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, midLevelWorkflow.getTasks().get(0).getStatus());

        // Poll and complete the integration_task_1 task in the mid-level workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));

        // The subworkflow task should be in SCHEDULED state; start it via system task call
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
        Workflow leafWorkflow = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, leafWorkflow.getStatus());
        assertEquals(2, leafWorkflow.getTasks().size());
        assertEquals("integration_task_1", leafWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafWorkflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", leafWorkflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafWorkflow.getTasks().get(1).getStatus());

        // The mid level workflow is 'decided'
        sweep(midLevelWorkflowId);

        // The mid level subworkflow is in FAILED state
        Workflow midLevelFailed =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, midLevelFailed.getStatus());
        assertEquals(2, midLevelFailed.getTasks().size());
        assertEquals("integration_task_1", midLevelFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midLevelFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midLevelFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, midLevelFailed.getTasks().get(1).getStatus());

        // The root level workflow is 'decided'
        sweep(rootWorkflowId);

        // The root level workflow is in FAILED state
        Workflow rootFailed = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, rootFailed.getStatus());
        assertEquals(2, rootFailed.getTasks().size());
        assertEquals("integration_task_1", rootFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootFailed.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, rootFailed.getTasks().get(1).getStatus());
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed on the root workflow.
     *
     * <p>Expectation: The root workflow spawns a NEW mid-level workflow, which in turn spawns a NEW
     * leaf workflow. When the leaf workflow completes successfully, both the NEW mid-level and root
     * workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the root in a 3-level subworkflow")
    void testRetryOnRootIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the root workflow and execute the new root SUB_WORKFLOW task
        // A retry only re-runs the failed SUB_WORKFLOW task; the root's integration_task_1
        // stays COMPLETED, so there is no integration_task_1 to poll here.
        workflowExecutor.retry(rootWorkflowId, false);
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

        // then: verify that the root workflow created a new SUB_WORKFLOW task
        Workflow rootAfterRetry = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterRetry.getStatus());
        assertEquals(3, rootAfterRetry.getTasks().size());
        assertEquals("integration_task_1", rootAfterRetry.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootAfterRetry.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, rootAfterRetry.getTasks().get(1).getStatus());
        assertTrue(rootAfterRetry.getTasks().get(1).isRetried());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRetry.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterRetry.getTasks().get(2).getStatus());
        assertEquals(
                rootAfterRetry.getTasks().get(1).getTaskId(),
                rootAfterRetry.getTasks().get(2).getRetriedTaskId());

        String newMidLevelWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(rootWorkflowId, true)
                        .getTasks()
                        .get(2)
                        .getSubWorkflowId();
        sweep(newMidLevelWorkflowId);

        // then: verify that a new mid level workflow is created and is in RUNNING state
        assertNotEquals(midLevelWorkflowId, newMidLevelWorkflowId);
        Workflow newMidLevelWorkflow =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidLevelWorkflow.getStatus());
        assertEquals(1, newMidLevelWorkflow.getTasks().size());
        assertEquals("integration_task_1", newMidLevelWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newMidLevelWorkflow.getTasks().get(0).getStatus());

        // when: the new mid level workflow completes its first task and starts its subworkflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op1", "task1.done"));
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

        // then: the new mid level workflow tracks the child subworkflow
        Workflow newMidTrackingChild =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newMidTrackingChild.getStatus());
        assertEquals(2, newMidTrackingChild.getTasks().size());
        assertEquals("integration_task_1", newMidTrackingChild.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidTrackingChild.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, newMidTrackingChild.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, newMidTrackingChild.getTasks().get(1).getStatus());

        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(newMidLevelWorkflowId, true)
                        .getTasks()
                        .get(1)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWorkflow =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWorkflow.getStatus());
        assertEquals(1, newLeafWorkflow.getTasks().size());
        assertEquals("integration_task_1", newLeafWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWorkflow.getTasks().get(0).getStatus());

        // when: poll and complete the two tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // then: the new leaf workflow is in COMPLETED state
        Workflow newLeafCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafCompleted.getStatus());
        assertEquals(2, newLeafCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(1).getStatus());

        // when: the new mid level and root workflows are 'decided'
        sweep(newMidLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: the new mid level workflow is in COMPLETED state
        Workflow newMidCompleted =
                workflowExecutionService.getExecutionStatus(newMidLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newMidCompleted.getStatus());
        assertEquals(2, newMidCompleted.getTasks().size());
        assertEquals("integration_task_1", newMidCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, newMidCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newMidCompleted.getTasks().get(1).getStatus());

        // then: the root workflow is in COMPLETED state
        Workflow rootCompleted = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootCompleted.getStatus());
        assertEquals(3, rootCompleted.getTasks().size());
        assertEquals("integration_task_1", rootCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, rootCompleted.getTasks().get(1).getStatus());
        assertTrue(rootCompleted.getTasks().get(1).isRetried());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootCompleted.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(2).getStatus());
        assertEquals(
                rootCompleted.getTasks().get(1).getTaskId(),
                rootCompleted.getTasks().get(2).getRetriedTaskId());
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed with resume flag on the root workflow.
     *
     * <p>Expectation: The leaf workflow is retried and both its parent (mid-level) and grand parent
     * (root) workflows are also retried. When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the root with resume flag in a 3-level subworkflow")
    void testRetryOnRootWithResumeFlagIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the root workflow
        workflowExecutor.retry(rootWorkflowId, true);

        // then: verify that the sub workflow task in root workflow is updated
        Workflow rootAfterRetry = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterRetry.getStatus());
        assertEquals(2, rootAfterRetry.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterRetry.getTasks().get(1).getStatus());

        // and: verify that the sub workflow task in mid level workflow is updated
        Workflow midAfterRetry =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterRetry.getStatus());
        assertEquals(2, midAfterRetry.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterRetry.getTasks().get(1).getStatus());

        // and: verify that the previously failed task in leaf workflow is in SCHEDULED state
        Workflow leafAfterRetry = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafAfterRetry.getStatus());
        assertEquals(3, leafAfterRetry.getTasks().size());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafAfterRetry.getTasks().get(1).getStatus());
        assertTrue(leafAfterRetry.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafAfterRetry.getTasks().get(2).getStatus());
        assertEquals(
                leafAfterRetry.getTasks().get(1).getTaskId(),
                leafAfterRetry.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: the mid level workflow is in RUNNING state
        Workflow midAfterSweep =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterSweep.getStatus());
        assertEquals(2, midAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                midAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after "decide"

        // and: the root workflow is in RUNNING state
        Workflow rootAfterSweep = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterSweep.getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                rootAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after "decide"

        // when: poll and complete the integration_task_2 task
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // then: verify that the leaf workflow is in COMPLETED state
        Workflow leafCompleted = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, leafCompleted.getStatus());
        assertEquals(3, leafCompleted.getTasks().size());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafCompleted.getTasks().get(1).getStatus());
        assertTrue(leafCompleted.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafCompleted.getTasks().get(2).getStatus());
        assertEquals(
                leafCompleted.getTasks().get(1).getTaskId(),
                leafCompleted.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: the new mid level workflow is in COMPLETED state
        Workflow midCompleted =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, midCompleted.getStatus());
        assertEquals(2, midCompleted.getTasks().size());
        assertEquals("integration_task_1", midCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, midCompleted.getTasks().get(1).getStatus());

        // and: the root workflow is in COMPLETED state
        Workflow rootCompleted = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootCompleted.getStatus());
        assertEquals(2, rootCompleted.getTasks().size());
        assertEquals("integration_task_1", rootCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(1).getStatus());
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed on the mid-level workflow.
     *
     * <p>Expectation: The mid-level workflow spawns a NEW leaf workflow and also updates its parent
     * (root workflow). When the NEW leaf workflow completes successfully, both the mid-level and
     * root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the mid-level in a 3-level subworkflow")
    void testRetryOnMidLevelIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the mid level workflow
        workflowExecutor.retry(midLevelWorkflowId, false);
        Task retryMidSubWfTask =
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
        if (retryMidSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, retryMidSubWfTask.getTaskId());
        }

        // then: verify that the mid workflow created a new SUB_WORKFLOW task
        Workflow midAfterRetry =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterRetry.getStatus());
        assertEquals(3, midAfterRetry.getTasks().size());
        assertEquals("integration_task_1", midAfterRetry.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midAfterRetry.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, midAfterRetry.getTasks().get(1).getStatus());
        assertTrue(midAfterRetry.getTasks().get(1).isRetried());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterRetry.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterRetry.getTasks().get(2).getStatus());
        assertEquals(
                midAfterRetry.getTasks().get(1).getTaskId(),
                midAfterRetry.getTasks().get(2).getRetriedTaskId());

        // and: verify the SUB_WORKFLOW task in root workflow is updated
        Workflow rootAfterRetry = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterRetry.getStatus());
        assertEquals(2, rootAfterRetry.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterRetry.getTasks().get(1).getStatus());

        String newLeafWorkflowId =
                workflowExecutionService
                        .getExecutionStatus(midLevelWorkflowId, true)
                        .getTasks()
                        .get(2)
                        .getSubWorkflowId();
        sweep(newLeafWorkflowId);

        // then: verify that a new leaf workflow is created and is in RUNNING state
        assertNotEquals(leafWorkflowId, newLeafWorkflowId);
        Workflow newLeafWorkflow =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newLeafWorkflow.getStatus());
        assertEquals(1, newLeafWorkflow.getTasks().size());
        assertEquals("integration_task_1", newLeafWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newLeafWorkflow.getTasks().get(0).getStatus());

        // when: poll and complete the 2 tasks in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // then: verify that the new leaf workflow reached COMPLETED state
        Workflow newLeafCompleted =
                workflowExecutionService.getExecutionStatus(newLeafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newLeafCompleted.getStatus());
        assertEquals(2, newLeafCompleted.getTasks().size());
        assertEquals("integration_task_1", newLeafCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newLeafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newLeafCompleted.getTasks().get(1).getStatus());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: verify that the mid level and root workflows reach COMPLETED state
        Workflow midCompleted =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, midCompleted.getStatus());
        assertEquals(3, midCompleted.getTasks().size());
        assertEquals("integration_task_1", midCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, midCompleted.getTasks().get(1).getStatus());
        assertTrue(midCompleted.getTasks().get(1).isRetried());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midCompleted.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, midCompleted.getTasks().get(2).getStatus());
        assertEquals(
                midCompleted.getTasks().get(1).getTaskId(),
                midCompleted.getTasks().get(2).getRetriedTaskId());

        Workflow rootCompleted = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootCompleted.getStatus());
        assertEquals(2, rootCompleted.getTasks().size());
        assertEquals("integration_task_1", rootCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(1).getStatus());
        assertFalse(
                rootCompleted
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed with resume flag on the mid-level workflow.
     *
     * <p>Expectation: The leaf workflow is retried and both its parent (mid-level) and grand parent
     * (root) workflows are also retried. When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the mid-level with resume flag in a 3-level subworkflow")
    void testRetryOnMidLevelWithResumeFlagIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the mid level workflow (with resume flag)
        workflowExecutor.retry(midLevelWorkflowId, true);

        // then: verify that the sub workflow task in root workflow is updated
        Workflow rootAfterRetry = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterRetry.getStatus());
        assertEquals(2, rootAfterRetry.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterRetry.getTasks().get(1).getStatus());

        // and: verify that the sub workflow task in mid level workflow is updated
        Workflow midAfterRetry =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterRetry.getStatus());
        assertEquals(2, midAfterRetry.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterRetry.getTasks().get(1).getStatus());

        // and: verify that the previously failed task in leaf workflow is in SCHEDULED state
        Workflow leafAfterRetry = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafAfterRetry.getStatus());
        assertEquals(3, leafAfterRetry.getTasks().size());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafAfterRetry.getTasks().get(1).getStatus());
        assertTrue(leafAfterRetry.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafAfterRetry.getTasks().get(2).getStatus());
        assertEquals(
                leafAfterRetry.getTasks().get(1).getTaskId(),
                leafAfterRetry.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: the mid level workflow is in RUNNING state
        Workflow midAfterSweep =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterSweep.getStatus());
        assertEquals(2, midAfterSweep.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                midAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after "decide"

        // and: the root workflow is in RUNNING state
        Workflow rootAfterSweep = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterSweep.getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterSweep.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterSweep.getTasks().get(1).getStatus());
        assertFalse(
                rootAfterSweep
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after "decide"

        // when: poll and complete the previously failed integration_task_2 task
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // then: verify that the leaf workflow is in COMPLETED state
        Workflow leafCompleted = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, leafCompleted.getStatus());
        assertEquals(3, leafCompleted.getTasks().size());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafCompleted.getTasks().get(1).getStatus());
        assertTrue(leafCompleted.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafCompleted.getTasks().get(2).getStatus());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: the mid level workflow is in COMPLETED state
        Workflow midCompleted =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, midCompleted.getStatus());
        assertEquals(2, midCompleted.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, midCompleted.getTasks().get(1).getStatus());
        assertFalse(
                midCompleted
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide

        // and: the root workflow is in COMPLETED state
        Workflow rootCompleted = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootCompleted.getStatus());
        assertEquals(2, rootCompleted.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(1).getStatus());
        assertFalse(
                rootCompleted
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed on the leaf workflow.
     *
     * <p>Expectation: The leaf workflow resumes its FAILED task and updates both its parent
     * (mid-level) and grandparent (root). When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the leaf in a 3-level subworkflow")
    void testRetryOnLeafIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the leaf workflow
        workflowExecutor.retry(leafWorkflowId, false);

        // then: verify that the leaf workflow is in RUNNING state and failed task is retried
        Workflow leafAfterRetry = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafAfterRetry.getStatus());
        assertEquals(3, leafAfterRetry.getTasks().size());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafAfterRetry.getTasks().get(1).getStatus());
        assertTrue(leafAfterRetry.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafAfterRetry.getTasks().get(2).getStatus());
        assertEquals(
                leafAfterRetry.getTasks().get(1).getTaskId(),
                leafAfterRetry.getTasks().get(2).getRetriedTaskId());

        // then: verify that the mid-level workflow is updated
        Workflow midAfterRetry =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterRetry.getStatus());
        assertEquals(2, midAfterRetry.getTasks().size());
        assertEquals("integration_task_1", midAfterRetry.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midAfterRetry.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterRetry.getTasks().get(1).getStatus());

        // and: verify that the root workflow is updated
        Workflow rootAfterRetry = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterRetry.getStatus());
        assertEquals(2, rootAfterRetry.getTasks().size());
        assertEquals("integration_task_1", rootAfterRetry.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootAfterRetry.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterRetry.getTasks().get(1).getStatus());

        // when: poll and complete the scheduled task in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // then: verify that the leaf workflow reached COMPLETED state
        Workflow leafCompleted = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, leafCompleted.getStatus());
        assertEquals(3, leafCompleted.getTasks().size());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafCompleted.getTasks().get(1).getStatus());
        assertTrue(leafCompleted.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafCompleted.getTasks().get(2).getStatus());
        assertEquals(
                leafCompleted.getTasks().get(1).getTaskId(),
                leafCompleted.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: verify that the mid level and root workflows reach COMPLETED state
        Workflow midCompleted =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, midCompleted.getStatus());
        assertEquals(2, midCompleted.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, midCompleted.getTasks().get(1).getStatus());
        assertFalse(
                midCompleted
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide

        Workflow rootCompleted = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootCompleted.getStatus());
        assertEquals(2, rootCompleted.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(1).getStatus());
        assertFalse(
                rootCompleted
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
    }

    /**
     * On a 3-level workflow where all workflows reach FAILED state because of a FAILED task in the
     * leaf workflow.
     *
     * <p>A retry is executed with resume flag on the leaf workflow.
     *
     * <p>Expectation: The leaf workflow resumes its FAILED task and updates both its parent
     * (mid-level) and grandparent (root). When the leaf workflow completes successfully, both the
     * mid-level and root workflows also complete successfully.
     */
    @Test
    @DisplayName("Test retry on the leaf with resume flag in a 3-level subworkflow")
    void testRetryOnLeafWithResumeFlagIn3LevelSubworkflow() throws Exception {
        // when: do a retry on the leaf workflow (with resume flag)
        workflowExecutor.retry(leafWorkflowId, true);

        // then: verify that the leaf workflow is in RUNNING state and failed task is retried
        Workflow leafAfterRetry = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, leafAfterRetry.getStatus());
        assertEquals(3, leafAfterRetry.getTasks().size());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafAfterRetry.getTasks().get(1).getStatus());
        assertTrue(leafAfterRetry.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafAfterRetry.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, leafAfterRetry.getTasks().get(2).getStatus());
        assertEquals(
                leafAfterRetry.getTasks().get(1).getTaskId(),
                leafAfterRetry.getTasks().get(2).getRetriedTaskId());

        // then: verify that the mid-level workflow is updated
        Workflow midAfterRetry =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, midAfterRetry.getStatus());
        assertEquals(2, midAfterRetry.getTasks().size());
        assertEquals("integration_task_1", midAfterRetry.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, midAfterRetry.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, midAfterRetry.getTasks().get(1).getStatus());

        // and: verify that the root workflow is updated
        Workflow rootAfterRetry = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, rootAfterRetry.getStatus());
        assertEquals(2, rootAfterRetry.getTasks().size());
        assertEquals("integration_task_1", rootAfterRetry.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootAfterRetry.getTasks().get(0).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootAfterRetry.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, rootAfterRetry.getTasks().get(1).getStatus());

        // when: poll and complete the scheduled task in the leaf workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // then: verify that the leaf workflow reached COMPLETED state
        Workflow leafCompleted = workflowExecutionService.getExecutionStatus(leafWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, leafCompleted.getStatus());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, leafCompleted.getTasks().get(1).getStatus());
        assertTrue(leafCompleted.getTasks().get(1).isRetried());
        assertEquals("integration_task_2", leafCompleted.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, leafCompleted.getTasks().get(2).getStatus());
        assertEquals(
                leafCompleted.getTasks().get(1).getTaskId(),
                leafCompleted.getTasks().get(2).getRetriedTaskId());

        // when: the mid level and root workflows are 'decided'
        sweep(midLevelWorkflowId);
        sweep(rootWorkflowId);

        // then: verify that the mid level and root workflows reach COMPLETED state
        Workflow midCompleted =
                workflowExecutionService.getExecutionStatus(midLevelWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, midCompleted.getStatus());
        assertEquals(2, midCompleted.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, midCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, midCompleted.getTasks().get(1).getStatus());
        assertFalse(
                midCompleted
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide

        Workflow rootCompleted = workflowExecutionService.getExecutionStatus(rootWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, rootCompleted.getStatus());
        assertEquals(2, rootCompleted.getTasks().size());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, rootCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, rootCompleted.getTasks().get(1).getStatus());
        assertFalse(
                rootCompleted
                        .getTasks()
                        .get(1)
                        .isSubworkflowChanged()); // flag is reset after decide
    }
}
