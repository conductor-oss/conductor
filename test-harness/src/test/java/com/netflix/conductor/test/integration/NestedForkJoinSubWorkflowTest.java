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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.*;

class NestedForkJoinSubWorkflowTest extends AbstractSpecification {

    private static final String FORK_JOIN_NESTED_SUB_WF = "nested_fork_join_swf";
    private static final String SIMPLE_WORKFLOW = "integration_test_wf";

    @Autowired Join joinTask;

    @Autowired SubWorkflow subWorkflowTask;

    String parentWorkflowId;
    String subworkflowId;

    TaskDef persistedTask2Definition;

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "nested_fork_join_swf.json", "simple_workflow_1_integration_test.json");

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
        metadataService.getWorkflowDef(FORK_JOIN_NESTED_SUB_WF, 1);

        // Input required to start the workflow execution
        String correlationId = "retry_on_root_in_3level_wf";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("subwf", SIMPLE_WORKFLOW);

        // Start the workflow
        parentWorkflowId = startWorkflow(FORK_JOIN_NESTED_SUB_WF, 1, correlationId, input, null);

        // Verify that the workflow is in a RUNNING state; start nested sub workflow task if
        // scheduled
        Task nestedSetupSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(parentWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (nestedSetupSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, nestedSetupSubWfTask.getTaskId());
        }

        Workflow parentWf = workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWf.getStatus());
        List<Task> tasks = parentWf.getTasks();
        assertEquals(7, tasks.size());
        assertEquals(TASK_TYPE_FORK, tasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, tasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, tasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, tasks.get(2).getStatus());
        assertEquals("integration_task_2", tasks.get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, tasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, tasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, tasks.get(4).getStatus());
        assertEquals("integration_task_2", tasks.get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, tasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, tasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, tasks.get(6).getStatus());

        // Poll and complete the integration_task_2 task (twice, one per fork branch)
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task1.integration.worker", Map.of("op", "task1.done"));

        // Verify that the sub_workflow_task is in IN_PROGRESS state
        Workflow parentWorkflowInstance =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWorkflowInstance.getStatus());
        List<Task> tasks2 = parentWorkflowInstance.getTasks();
        assertEquals(7, tasks2.size());
        assertEquals(TASK_TYPE_FORK, tasks2.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks2.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, tasks2.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks2.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, tasks2.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, tasks2.get(2).getStatus());
        assertEquals("integration_task_2", tasks2.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks2.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, tasks2.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, tasks2.get(4).getStatus());
        assertEquals("integration_task_2", tasks2.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks2.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, tasks2.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, tasks2.get(6).getStatus());

        // Sweep the mid-level (sub) workflow and verify first task is SCHEDULED
        subworkflowId = parentWorkflowInstance.getTasks().get(2).getSubWorkflowId();
        sweep(subworkflowId);
        Workflow subWf = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWf.getStatus());
        assertEquals(1, subWf.getTasks().size());
        assertEquals("integration_task_1", subWf.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subWf.getTasks().get(0).getStatus());

        // Poll and complete integration_task_1, then fail integration_task_2 in the sub workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndFailTask(
                "integration_task_2", "task2.integration.worker", "task2 failed");

        // The sub workflow ends up in a FAILED state
        Workflow subWfFailed = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, subWfFailed.getStatus());
        assertEquals(2, subWfFailed.getTasks().size());
        assertEquals("integration_task_1", subWfFailed.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, subWfFailed.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", subWfFailed.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.FAILED, subWfFailed.getTasks().get(1).getStatus());

        // Sweep the parent workflow and verify it is in FAILED state
        sweep(parentWorkflowId);
        Workflow parentWfFailed =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, parentWfFailed.getStatus());
        List<Task> failedTasks = parentWfFailed.getTasks();
        assertEquals(7, failedTasks.size());
        assertEquals(TASK_TYPE_FORK, failedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, failedTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, failedTasks.get(2).getTaskType());
        assertEquals(Task.Status.FAILED, failedTasks.get(2).getStatus());
        assertEquals("integration_task_2", failedTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, failedTasks.get(4).getTaskType());
        assertEquals(Task.Status.CANCELED, failedTasks.get(4).getStatus());
        assertEquals("integration_task_2", failedTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, failedTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, failedTasks.get(6).getTaskType());
        assertEquals(Task.Status.CANCELED, failedTasks.get(6).getStatus());
    }

    @AfterEach
    void restoreTaskDef() {
        if (persistedTask2Definition != null) {
            metadataService.updateTaskDef(persistedTask2Definition);
        }
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED
     * task in the sub workflow.
     *
     * <p>A restart is executed on the sub workflow.
     *
     * <p>Expectation: The sub workflow spawns a execution with the same id. When the sub workflow
     * completes successfully, the parent workflow also completes successfully.
     */
    @Test
    @DisplayName("test restart on the sub workflow in a nested fork join workflow")
    void testRestartOnSubWorkflowInNestedForkJoinWorkflow() {
        // Restart the sub workflow and sweep the parent
        workflowExecutor.restart(subworkflowId, false);
        sweep(parentWorkflowId);

        // Verify that the subworkflow is in RUNNING state
        Workflow subWfRunning = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWfRunning.getStatus());
        assertEquals(1, subWfRunning.getTasks().size());
        assertEquals("integration_task_1", subWfRunning.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subWfRunning.getTasks().get(0).getStatus());

        // Verify that the parent workflow is updated
        Workflow parentWfRunning =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfRunning.getStatus());
        List<Task> parentTasks = parentWfRunning.getTasks();
        assertEquals(7, parentTasks.size());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(2).getStatus());
        assertEquals("integration_task_2", parentTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(6).getStatus());

        // Get JOIN task IDs before second sweep
        Workflow workflow = workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        String outerJoinId = workflow.getTaskByRefName("outer_join").getTaskId();
        String innerJoinId = workflow.getTaskByRefName("inner_join").getTaskId();

        // Sweep the parent workflow again and verify the flag is reset and JOIN is updated
        sweep(parentWorkflowId);
        Workflow parentWfAfterSweep =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfAfterSweep.getStatus());
        List<Task> sweepTasks = parentWfAfterSweep.getTasks();
        assertEquals(7, sweepTasks.size());
        assertEquals(TASK_TYPE_FORK, sweepTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, sweepTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, sweepTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(2).getStatus());
        assertFalse(sweepTasks.get(2).isSubworkflowChanged());
        assertEquals("integration_task_2", sweepTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, sweepTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(4).getStatus());
        assertEquals("integration_task_2", sweepTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, sweepTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(6).getStatus());

        // Poll and complete both tasks in the sub workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // Verify that the subworkflow completed
        Workflow subWfCompleted = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWfCompleted.getStatus());
        assertEquals(2, subWfCompleted.getTasks().size());
        assertEquals("integration_task_1", subWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, subWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", subWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, subWfCompleted.getTasks().get(1).getStatus());

        // Verify that the parent workflow's sub workflow task is completed
        Workflow parentWfSubCompleted =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfSubCompleted.getStatus());
        List<Task> parentSubCompletedTasks = parentWfSubCompleted.getTasks();
        assertEquals(7, parentSubCompletedTasks.size());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentSubCompletedTasks.get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(2).getStatus());
        assertFalse(parentSubCompletedTasks.get(2).isSubworkflowChanged());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(6).getStatus());

        // Sweep the parent and execute JOIN tasks
        sweep(parentWorkflowId);
        asyncSystemTaskExecutor.execute(joinTask, innerJoinId);
        asyncSystemTaskExecutor.execute(joinTask, outerJoinId);

        // Verify that the parent workflow reaches COMPLETED with all tasks completed
        assertParentWorkflowIsComplete();
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED
     * task in the sub workflow.
     *
     * <p>A restart is executed on the parent workflow.
     *
     * <p>Expectation: The parent workflow spawns a execution with the same id, which in turn
     * creates a new instance of the sub workflow. When the sub workflow completes successfully, the
     * parent workflow also completes successfully.
     */
    @Test
    @DisplayName("test restart on the parent workflow in a nested fork join workflow")
    void testRestartOnParentWorkflowInNestedForkJoinWorkflow() {
        // Restart the parent workflow and execute scheduled sub workflow task if present
        workflowExecutor.restart(parentWorkflowId, false);
        Task restartedNestedSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(parentWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (restartedNestedSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, restartedNestedSubWfTask.getTaskId());
        }

        // Verify that the parent workflow is in RUNNING state
        Workflow parentWfRunning =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfRunning.getStatus());
        List<Task> parentTasks = parentWfRunning.getTasks();
        assertEquals(7, parentTasks.size());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(2).getStatus());
        assertEquals("integration_task_2", parentTasks.get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, parentTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentTasks.get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, parentTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(6).getStatus());

        // Poll and complete integration_task_2 tasks (two branches) and get JOIN IDs
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));
        Workflow workflow = workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        String outerJoinId = workflow.getTaskByRefName("outer_join").getTaskId();
        String innerJoinId = workflow.getTaskByRefName("inner_join").getTaskId();

        // Verify that SUB_WORKFLOW task is in IN_PROGRESS state
        Workflow parentWorkflowInstance =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWorkflowInstance.getStatus());
        List<Task> instanceTasks = parentWorkflowInstance.getTasks();
        assertEquals(7, instanceTasks.size());
        assertEquals(TASK_TYPE_FORK, instanceTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, instanceTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, instanceTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, instanceTasks.get(2).getStatus());
        assertEquals("integration_task_2", instanceTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, instanceTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, instanceTasks.get(4).getStatus());
        assertEquals("integration_task_2", instanceTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, instanceTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, instanceTasks.get(6).getStatus());

        // Verify that a new instance of the sub workflow is created
        String newSubWorkflowId = parentWorkflowInstance.getTasks().get(2).getSubWorkflowId();
        sweep(newSubWorkflowId);
        assertNotEquals(subworkflowId, newSubWorkflowId);
        Workflow newSubWfRunning =
                workflowExecutionService.getExecutionStatus(newSubWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newSubWfRunning.getStatus());
        assertEquals(1, newSubWfRunning.getTasks().size());
        assertEquals("integration_task_1", newSubWfRunning.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newSubWfRunning.getTasks().get(0).getStatus());

        // Poll and complete both tasks in the sub workflow
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // Verify that the new subworkflow completed
        Workflow newSubWfCompleted =
                workflowExecutionService.getExecutionStatus(newSubWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newSubWfCompleted.getStatus());
        assertEquals(2, newSubWfCompleted.getTasks().size());
        assertEquals("integration_task_1", newSubWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newSubWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newSubWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newSubWfCompleted.getTasks().get(1).getStatus());

        // Verify that the parent workflow's sub workflow task is completed
        Workflow parentWfSubCompleted =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfSubCompleted.getStatus());
        List<Task> parentSubCompletedTasks = parentWfSubCompleted.getTasks();
        assertEquals(7, parentSubCompletedTasks.size());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentSubCompletedTasks.get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(2).getStatus());
        assertFalse(parentSubCompletedTasks.get(2).isSubworkflowChanged());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(6).getStatus());

        // Sweep the parent and execute JOIN tasks
        sweep(parentWorkflowId);
        asyncSystemTaskExecutor.execute(joinTask, innerJoinId);
        asyncSystemTaskExecutor.execute(joinTask, outerJoinId);

        // Verify that the parent workflow reaches COMPLETED with all tasks completed
        assertParentWorkflowIsComplete();
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED
     * task in the sub workflow.
     *
     * <p>A retry is executed on the parent workflow.
     *
     * <p>Expectation: The parent workflow spawns a execution with the same id, which in turn
     * creates a new instance of the sub workflow. When the sub workflow completes successfully, the
     * parent workflow also completes successfully.
     */
    @Test
    @DisplayName("test retry on the parent workflow in a nested fork join workflow")
    void testRetryOnParentWorkflowInNestedForkJoinWorkflow() {
        // Retry the parent workflow and execute scheduled sub workflow task if present
        workflowExecutor.retry(parentWorkflowId, false);
        Task retriedNestedSubWfTask =
                workflowExecutionService
                        .getExecutionStatus(parentWorkflowId, true)
                        .getTasks()
                        .stream()
                        .filter(
                                t ->
                                        TASK_TYPE_SUB_WORKFLOW.equals(t.getTaskType())
                                                && t.getStatus() == Task.Status.SCHEDULED)
                        .findFirst()
                        .orElse(null);
        if (retriedNestedSubWfTask != null) {
            asyncSystemTaskExecutor.execute(subWorkflowTask, retriedNestedSubWfTask.getTaskId());
        }

        // Verify that the parent workflow is in RUNNING state with 8 tasks (original SUB_WORKFLOW
        // is FAILED/retried, a new SUB_WORKFLOW task is appended)
        Workflow parentWfRetried =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfRetried.getStatus());
        List<Task> retriedTasks = parentWfRetried.getTasks();
        assertEquals(8, retriedTasks.size());
        assertEquals(TASK_TYPE_FORK, retriedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, retriedTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, retriedTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, retriedTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, retriedTasks.get(2).getTaskType());
        assertEquals(Task.Status.FAILED, retriedTasks.get(2).getStatus());
        assertTrue(retriedTasks.get(2).isRetried());
        assertEquals("integration_task_2", retriedTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, retriedTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, retriedTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, retriedTasks.get(4).getStatus());
        assertEquals("integration_task_2", retriedTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, retriedTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, retriedTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, retriedTasks.get(6).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, retriedTasks.get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, retriedTasks.get(7).getStatus());
        assertEquals(retriedTasks.get(2).getTaskId(), retriedTasks.get(7).getRetriedTaskId());

        // Verify again (second then block in Groovy maps to re-read and assert)
        Workflow parentWorkflowInstance =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWorkflowInstance.getStatus());
        List<Task> instanceTasks = parentWorkflowInstance.getTasks();
        assertEquals(8, instanceTasks.size());
        assertEquals(TASK_TYPE_FORK, instanceTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, instanceTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, instanceTasks.get(2).getTaskType());
        assertEquals(Task.Status.FAILED, instanceTasks.get(2).getStatus());
        assertTrue(instanceTasks.get(2).isRetried());
        assertEquals("integration_task_2", instanceTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, instanceTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, instanceTasks.get(4).getStatus());
        assertEquals("integration_task_2", instanceTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, instanceTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, instanceTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, instanceTasks.get(6).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, instanceTasks.get(7).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, instanceTasks.get(7).getStatus());
        assertEquals(instanceTasks.get(2).getTaskId(), instanceTasks.get(7).getRetriedTaskId());

        // Verify that a new instance of the sub workflow is created
        String newSubWorkflowId = parentWorkflowInstance.getTasks().get(7).getSubWorkflowId();
        assertNotEquals(subworkflowId, newSubWorkflowId);
        sweep(newSubWorkflowId);
        Workflow newSubWfRunning =
                workflowExecutionService.getExecutionStatus(newSubWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, newSubWfRunning.getStatus());
        assertEquals(1, newSubWfRunning.getTasks().size());
        assertEquals("integration_task_1", newSubWfRunning.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, newSubWfRunning.getTasks().get(0).getStatus());

        // Get JOIN task IDs and poll/complete both tasks in the sub workflow
        Workflow workflow = workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        String outerJoinId = workflow.getTaskByRefName("outer_join").getTaskId();
        String innerJoinId = workflow.getTaskByRefName("inner_join").getTaskId();
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "task1.integration.worker", Map.of("op", "task1.done"));
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // Verify that the new subworkflow completed
        Workflow newSubWfCompleted =
                workflowExecutionService.getExecutionStatus(newSubWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, newSubWfCompleted.getStatus());
        assertEquals(2, newSubWfCompleted.getTasks().size());
        assertEquals("integration_task_1", newSubWfCompleted.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, newSubWfCompleted.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", newSubWfCompleted.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, newSubWfCompleted.getTasks().get(1).getStatus());

        // Verify that the parent workflow's sub workflow task is completed
        Workflow parentWfSubCompleted =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfSubCompleted.getStatus());
        List<Task> parentSubCompletedTasks = parentWfSubCompleted.getTasks();
        assertEquals(8, parentSubCompletedTasks.size());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentSubCompletedTasks.get(2).getTaskType());
        assertEquals(Task.Status.FAILED, parentSubCompletedTasks.get(2).getStatus());
        assertTrue(parentSubCompletedTasks.get(2).isRetried());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(6).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentSubCompletedTasks.get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(7).getStatus());
        assertEquals(
                parentSubCompletedTasks.get(2).getTaskId(),
                parentSubCompletedTasks.get(7).getRetriedTaskId());

        // Sweep the parent and execute JOIN tasks
        sweep(parentWorkflowId);
        asyncSystemTaskExecutor.execute(joinTask, innerJoinId);
        asyncSystemTaskExecutor.execute(joinTask, outerJoinId);

        // Verify that the parent workflow reaches COMPLETED with all tasks completed
        Workflow parentWfFinal =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, parentWfFinal.getStatus());
        List<Task> finalTasks = parentWfFinal.getTasks();
        assertEquals(8, finalTasks.size());
        assertEquals(TASK_TYPE_FORK, finalTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, finalTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, finalTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, finalTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, finalTasks.get(2).getTaskType());
        assertEquals(Task.Status.FAILED, finalTasks.get(2).getStatus());
        assertTrue(finalTasks.get(2).isRetried());
        assertEquals("integration_task_2", finalTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, finalTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, finalTasks.get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, finalTasks.get(4).getStatus());
        assertEquals("integration_task_2", finalTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, finalTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, finalTasks.get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, finalTasks.get(6).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, finalTasks.get(7).getTaskType());
        assertEquals(Task.Status.COMPLETED, finalTasks.get(7).getStatus());
        assertEquals(finalTasks.get(2).getTaskId(), finalTasks.get(7).getRetriedTaskId());
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED
     * task in the sub workflow.
     *
     * <p>A retry with resume flag is executed on the parent workflow.
     *
     * <p>Expectation: The parent workflow resumes, the sub workflow retries only the failed task.
     * When the sub workflow completes successfully, the parent workflow also completes
     * successfully.
     */
    @Test
    @DisplayName("test retry with resume on the parent workflow in a nested fork join workflow")
    void testRetryWithResumeOnParentWorkflowInNestedForkJoinWorkflow() {
        // Retry the parent workflow with resume=true and sweep the parent
        workflowExecutor.retry(parentWorkflowId, true);
        sweep(parentWorkflowId);

        // Verify that the sub workflow is in RUNNING state with retried task
        Workflow subWfRunning = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWfRunning.getStatus());
        List<Task> subTasks = subWfRunning.getTasks();
        assertEquals(3, subTasks.size());
        assertEquals("integration_task_1", subTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, subTasks.get(0).getStatus());
        assertEquals("integration_task_2", subTasks.get(1).getTaskType());
        assertEquals(Task.Status.FAILED, subTasks.get(1).getStatus());
        assertTrue(subTasks.get(1).isRetried());
        assertEquals("integration_task_2", subTasks.get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subTasks.get(2).getStatus());
        assertEquals(subTasks.get(1).getTaskId(), subTasks.get(2).getRetriedTaskId());

        // Verify that the parent's SUB_WORKFLOW task is updated
        Workflow parentWfRunning =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfRunning.getStatus());
        List<Task> parentTasks = parentWfRunning.getTasks();
        assertEquals(7, parentTasks.size());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(2).getStatus());
        assertEquals("integration_task_2", parentTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(6).getStatus());

        // Sweep the parent again
        sweep(parentWorkflowId);

        // Verify that parent's JOIN task is in IN_PROGRESS with flag reset
        Workflow parentWfAfterSweep =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfAfterSweep.getStatus());
        List<Task> sweepTasks = parentWfAfterSweep.getTasks();
        assertEquals(7, sweepTasks.size());
        assertEquals(TASK_TYPE_FORK, sweepTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, sweepTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, sweepTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(2).getStatus());
        assertFalse(sweepTasks.get(2).isSubworkflowChanged());
        assertEquals("integration_task_2", sweepTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, sweepTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(4).getStatus());
        assertEquals("integration_task_2", sweepTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, sweepTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(6).getStatus());

        // Get JOIN task IDs and poll/complete the retried failed task in the sub workflow
        Workflow workflow = workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        String outerJoinId = workflow.getTaskByRefName("outer_join").getTaskId();
        String innerJoinId = workflow.getTaskByRefName("inner_join").getTaskId();
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // Verify that the subworkflow completed
        Workflow subWfCompleted = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWfCompleted.getStatus());
        List<Task> subCompletedTasks = subWfCompleted.getTasks();
        assertEquals(3, subCompletedTasks.size());
        assertEquals("integration_task_1", subCompletedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, subCompletedTasks.get(0).getStatus());
        assertEquals("integration_task_2", subCompletedTasks.get(1).getTaskType());
        assertEquals(Task.Status.FAILED, subCompletedTasks.get(1).getStatus());
        assertTrue(subCompletedTasks.get(1).isRetried());
        assertEquals("integration_task_2", subCompletedTasks.get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, subCompletedTasks.get(2).getStatus());
        assertEquals(
                subCompletedTasks.get(1).getTaskId(), subCompletedTasks.get(2).getRetriedTaskId());

        // Verify that the parent workflow's sub workflow task is completed
        Workflow parentWfSubCompleted =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfSubCompleted.getStatus());
        List<Task> parentSubCompletedTasks = parentWfSubCompleted.getTasks();
        assertEquals(7, parentSubCompletedTasks.size());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentSubCompletedTasks.get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(2).getStatus());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(6).getStatus());

        // Sweep the parent and execute JOIN tasks
        sweep(parentWorkflowId);
        asyncSystemTaskExecutor.execute(joinTask, innerJoinId);
        asyncSystemTaskExecutor.execute(joinTask, outerJoinId);

        // Verify the parent workflow reaches COMPLETED state
        assertParentWorkflowIsComplete();
    }

    /**
     * On a nested fork join workflow where all workflows reach FAILED state because of a FAILED
     * task in the sub workflow.
     *
     * <p>A retry is executed on the sub workflow.
     *
     * <p>Expectation: The sub workflow retries the failed task. When the sub workflow completes
     * successfully, the parent workflow also completes successfully.
     */
    @Test
    @DisplayName("test retry on the sub workflow in a nested fork join workflow")
    void testRetryOnSubWorkflowInNestedForkJoinWorkflow() {
        // Retry the sub workflow and sweep the parent
        workflowExecutor.retry(subworkflowId, false);
        sweep(parentWorkflowId);

        // Verify that the sub workflow is in RUNNING state with retried task
        Workflow subWfRunning = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, subWfRunning.getStatus());
        List<Task> subTasks = subWfRunning.getTasks();
        assertEquals(3, subTasks.size());
        assertEquals("integration_task_1", subTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, subTasks.get(0).getStatus());
        assertEquals("integration_task_2", subTasks.get(1).getTaskType());
        assertEquals(Task.Status.FAILED, subTasks.get(1).getStatus());
        assertTrue(subTasks.get(1).isRetried());
        assertEquals("integration_task_2", subTasks.get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, subTasks.get(2).getStatus());
        assertEquals(subTasks.get(1).getTaskId(), subTasks.get(2).getRetriedTaskId());

        // Verify that the parent's SUB_WORKFLOW task is updated
        Workflow parentWfRunning =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfRunning.getStatus());
        List<Task> parentTasks = parentWfRunning.getTasks();
        assertEquals(7, parentTasks.size());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(2).getStatus());
        assertEquals("integration_task_2", parentTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentTasks.get(6).getStatus());

        // Sweep the parent again
        sweep(parentWorkflowId);

        // Verify that parent's JOIN task is in IN_PROGRESS with flag reset
        Workflow parentWfAfterSweep =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfAfterSweep.getStatus());
        List<Task> sweepTasks = parentWfAfterSweep.getTasks();
        assertEquals(7, sweepTasks.size());
        assertEquals(TASK_TYPE_FORK, sweepTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, sweepTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, sweepTasks.get(2).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(2).getStatus());
        assertFalse(sweepTasks.get(2).isSubworkflowChanged());
        assertEquals("integration_task_2", sweepTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, sweepTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(4).getStatus());
        assertEquals("integration_task_2", sweepTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, sweepTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, sweepTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, sweepTasks.get(6).getStatus());

        // Get JOIN task IDs and poll/complete the retried failed task in the sub workflow
        Workflow workflow = workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        String outerJoinId = workflow.getTaskByRefName("outer_join").getTaskId();
        String innerJoinId = workflow.getTaskByRefName("inner_join").getTaskId();
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "task2.integration.worker", Map.of("op", "task2.done"));

        // Verify that the subworkflow completed
        Workflow subWfCompleted = workflowExecutionService.getExecutionStatus(subworkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, subWfCompleted.getStatus());
        List<Task> subCompletedTasks = subWfCompleted.getTasks();
        assertEquals(3, subCompletedTasks.size());
        assertEquals("integration_task_1", subCompletedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, subCompletedTasks.get(0).getStatus());
        assertEquals("integration_task_2", subCompletedTasks.get(1).getTaskType());
        assertEquals(Task.Status.FAILED, subCompletedTasks.get(1).getStatus());
        assertTrue(subCompletedTasks.get(1).isRetried());
        assertEquals("integration_task_2", subCompletedTasks.get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, subCompletedTasks.get(2).getStatus());
        assertEquals(
                subCompletedTasks.get(1).getTaskId(), subCompletedTasks.get(2).getRetriedTaskId());

        // Verify that the parent workflow's sub workflow task is completed
        Workflow parentWfSubCompleted =
                workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, parentWfSubCompleted.getStatus());
        List<Task> parentSubCompletedTasks = parentWfSubCompleted.getTasks();
        assertEquals(7, parentSubCompletedTasks.size());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, parentSubCompletedTasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, parentSubCompletedTasks.get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(2).getStatus());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(4).getStatus());
        assertEquals("integration_task_2", parentSubCompletedTasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, parentSubCompletedTasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, parentSubCompletedTasks.get(6).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, parentSubCompletedTasks.get(6).getStatus());

        // Sweep the parent and execute JOIN tasks
        sweep(parentWorkflowId);
        asyncSystemTaskExecutor.execute(joinTask, innerJoinId);
        asyncSystemTaskExecutor.execute(joinTask, outerJoinId);

        // Verify the parent workflow reaches COMPLETED state
        assertParentWorkflowIsComplete();
    }

    private void assertParentWorkflowIsComplete() {
        Workflow workflow = workflowExecutionService.getExecutionStatus(parentWorkflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        List<Task> tasks = workflow.getTasks();
        assertEquals(7, tasks.size());
        assertEquals(TASK_TYPE_FORK, tasks.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(0).getStatus());
        assertEquals(TASK_TYPE_FORK, tasks.get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(1).getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, tasks.get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(2).getStatus());
        assertEquals("integration_task_2", tasks.get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(3).getStatus());
        assertEquals(TASK_TYPE_JOIN, tasks.get(4).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(4).getStatus());
        assertEquals("integration_task_2", tasks.get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(5).getStatus());
        assertEquals(TASK_TYPE_JOIN, tasks.get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, tasks.get(6).getStatus());
    }
}
