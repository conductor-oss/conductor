/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.core.execution.tasks;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class TestJoin {

    private final ConductorProperties properties = new ConductorProperties();

    private final WorkflowExecutor executor = mock(WorkflowExecutor.class);

    private TaskModel createTask(
            String referenceName,
            TaskModel.Status status,
            boolean isOptional,
            boolean isPermissive) {
        TaskModel task = new TaskModel();
        task.setStatus(status);
        task.setReferenceTaskName(referenceName);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setOptional(isOptional);
        workflowTask.setPermissive(isPermissive);
        task.setWorkflowTask(workflowTask);
        return task;
    }

    private Pair<WorkflowModel, TaskModel> createJoinWorkflow(
            List<TaskModel> tasks, String... extraTaskRefNames) {
        WorkflowModel workflow = new WorkflowModel();
        var join = new TaskModel();
        join.setReferenceTaskName("join");
        var taskRefNames =
                tasks.stream().map(TaskModel::getReferenceTaskName).collect(Collectors.toList());
        taskRefNames.addAll(List.of(extraTaskRefNames));
        join.getInputData().put("joinOn", taskRefNames);
        workflow.getTasks().addAll(tasks);
        workflow.getTasks().add(join);
        return Pair.of(workflow, join);
    }

    @Test
    public void testShouldNotMarkJoinAsCompletedWithErrorsWhenNotDone() {
        var task1 = createTask("task1", TaskModel.Status.COMPLETED_WITH_ERRORS, true, false);

        // task2 is not scheduled yet, so the join is not completed
        var wfJoinPair = createJoinWorkflow(List.of(task1), "task2");

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertFalse(result);
    }

    @Test
    public void testJoinCompletesSuccessfullyWhenAllTasksSucceed() {
        var task1 = createTask("task1", TaskModel.Status.COMPLETED, false, false);
        var task2 = createTask("task2", TaskModel.Status.COMPLETED, false, false);

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertTrue("Join task should execute successfully when all tasks succeed", result);
        assertEquals(
                "Join task status should be COMPLETED when all tasks succeed",
                TaskModel.Status.COMPLETED,
                wfJoinPair.getRight().getStatus());
    }

    @Test
    public void testJoinWaitsWhenAnyTaskIsNotTerminal() {
        var task1 = createTask("task1", TaskModel.Status.IN_PROGRESS, false, false);
        var task2 = createTask("task2", TaskModel.Status.COMPLETED, false, false);

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertFalse("Join task should wait when any task is not in terminal state", result);
    }

    @Test
    public void testJoinFailsWhenMandatoryTaskFails() {
        // Mandatory task fails
        var task1 = createTask("task1", TaskModel.Status.FAILED, false, false);
        // Optional task completes with errors
        var task2 = createTask("task2", TaskModel.Status.COMPLETED_WITH_ERRORS, true, false);

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertTrue("Join task should be executed when a mandatory task fails", result);
        assertEquals(
                "Join task status should be FAILED when a mandatory task fails",
                TaskModel.Status.FAILED,
                wfJoinPair.getRight().getStatus());
    }

    @Test
    public void testJoinCompletesWithErrorsWhenOnlyOptionalTasksFail() {
        // Mandatory task succeeds
        var task1 = createTask("task1", TaskModel.Status.COMPLETED, false, false);
        // Optional task completes with errors
        var task2 = createTask("task2", TaskModel.Status.COMPLETED_WITH_ERRORS, true, false);

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertTrue("Join task should be executed when only optional tasks fail", result);
        assertEquals(
                "Join task status should be COMPLETED_WITH_ERRORS when only optional tasks fail",
                TaskModel.Status.COMPLETED_WITH_ERRORS,
                wfJoinPair.getRight().getStatus());
    }

    @Test
    public void testJoinAggregatesFailureReasonsCorrectly() {
        var task1 = createTask("task1", TaskModel.Status.FAILED, false, false);
        task1.setReasonForIncompletion("Task1 failed");
        var task2 = createTask("task2", TaskModel.Status.FAILED, false, false);
        task2.setReasonForIncompletion("Task2 failed");

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertTrue("Join task should be executed when tasks fail", result);
        assertEquals(
                "Join task status should be FAILED when tasks fail",
                TaskModel.Status.FAILED,
                wfJoinPair.getRight().getStatus());
        assertTrue(
                "Join task reason for incompletion should aggregate failure reasons",
                wfJoinPair.getRight().getReasonForIncompletion().contains("Task1 failed")
                        && wfJoinPair
                                .getRight()
                                .getReasonForIncompletion()
                                .contains("Task2 failed"));
    }

    @Test
    public void testJoinWaitsForAllTasksBeforeFailingDueToPermissiveTaskFailure() {
        // Task 1 is a permissive task that fails.
        var task1 = createTask("task1", TaskModel.Status.FAILED, false, true);
        // Task 2 is a non-permissive task that eventually succeeds.
        var task2 =
                createTask(
                        "task2",
                        TaskModel.Status.IN_PROGRESS,
                        false,
                        false); // Initially not in a terminal state.

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        // First execution: Task 2 is not yet terminal.
        var join = new Join(properties);
        boolean result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertFalse("Join task should wait as not all tasks are terminal", result);

        // Simulate Task 2 reaching a terminal state.
        task2.setStatus(TaskModel.Status.COMPLETED);

        // Second execution: Now all tasks are terminal.
        result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertTrue("Join task should proceed as now all tasks are terminal", result);
        assertEquals(
                "Join task should be marked as FAILED due to permissive task failure",
                TaskModel.Status.FAILED,
                wfJoinPair.getRight().getStatus());
    }

    @Test
    public void testEvaluationOffsetWhenPollCountIsBelowThreshold() {
        var join = new Join(properties);
        var taskModel = createTask("join1", TaskModel.Status.COMPLETED, false, false);
        taskModel.setPollCount(properties.getSystemTaskPostponeThreshold() - 1);
        var opt = join.getEvaluationOffset(taskModel, 30L);
        assertEquals(0L, (long) opt.orElseThrow());
    }

    @Test
    public void testEvaluationOffsetWhenPollCountIsAboveThreshold() {
        final var maxOffset = 30L;
        var join = new Join(properties);
        var taskModel = createTask("join1", TaskModel.Status.COMPLETED, false, false);

        taskModel.setPollCount(properties.getSystemTaskPostponeThreshold() + 1);
        var opt = join.getEvaluationOffset(taskModel, maxOffset);
        assertEquals(1L, (long) opt.orElseThrow());

        taskModel.setPollCount(properties.getSystemTaskPostponeThreshold() + 10);
        opt = join.getEvaluationOffset(taskModel, maxOffset);
        long expected = (long) Math.pow(Join.EVALUATION_OFFSET_BASE, 10);
        assertEquals(expected, (long) opt.orElseThrow());

        taskModel.setPollCount(properties.getSystemTaskPostponeThreshold() + 40);
        opt = join.getEvaluationOffset(taskModel, maxOffset);
        assertEquals(maxOffset, (long) opt.orElseThrow());
    }
}
