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
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
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
        // a finished task the decider has looked at: it will not be retried
        task.setExecuted(status.isTerminal());
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
    public void testJoinWaitsForAFailedAttemptTheDeciderHasNotLookedAt() {
        // The JOIN can run on a copy of the workflow loaded after the attempt failed and before
        // the decider scheduled its retry. It must wait, not fail on the attempt the retry
        // replaces (DECIDER_BUGS item 2).
        var task1 = createTask("task1", TaskModel.Status.COMPLETED, false, false);
        var task2 = createTask("task2", TaskModel.Status.FAILED, false, false);
        task2.setExecuted(false);

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        assertFalse(join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor));
        assertNotEquals(TaskModel.Status.FAILED, wfJoinPair.getRight().getStatus());

        // once the decider has looked at it and not retried it, the JOIN fails
        task2.setExecuted(true);
        assertTrue(join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor));
        assertEquals(TaskModel.Status.FAILED, wfJoinPair.getRight().getStatus());
    }

    private static WorkflowTask entry(String ref) {
        WorkflowTask entry = new WorkflowTask();
        entry.setName(ref);
        entry.setTaskReferenceName(ref);
        return entry;
    }

    /** A switch that took case "x"; it completed as soon as it picked it. */
    private TaskModel switchTask(String ref, Map<String, List<WorkflowTask>> cases) {
        TaskModel task = createTask(ref, TaskModel.Status.COMPLETED, false, false);
        task.setTaskType(TaskType.TASK_TYPE_SWITCH);
        task.getWorkflowTask().setTaskReferenceName(ref);
        task.getWorkflowTask().setDecisionCases(cases);
        task.getInputData().put("case", "x");
        task.getOutputData().put("selectedCase", "x");
        return task;
    }

    @Test
    public void testJoinNamingASwitchWaitsForTheCaseItTook() {
        // DECIDER_BUGS item 9
        var sw = switchTask("sw", Map.of("x", List.of(entry("a"), entry("b"))));
        var a = createTask("a", TaskModel.Status.COMPLETED, false, false);
        var b = createTask("b", TaskModel.Status.IN_PROGRESS, false, false);
        var wfJoinPair = createJoinWorkflow(List.of(sw, a, b));
        wfJoinPair.getRight().getInputData().put("joinOn", List.of("sw"));
        var join = new Join(properties);

        assertFalse(join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor));

        b.setStatus(TaskModel.Status.COMPLETED);
        b.setExecuted(true);
        assertTrue(join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor));
        assertEquals(TaskModel.Status.COMPLETED, wfJoinPair.getRight().getStatus());
        // the output under the switch's name is still the switch's own
        assertEquals(Map.of("selectedCase", "x"), wfJoinPair.getRight().getOutputData().get("sw"));
    }

    @Test
    public void testJoinFollowsASwitchThatEndsTheCase() {
        var inner = switchTask("inner", Map.of("x", List.of(entry("c"))));
        var outer = switchTask("outer", Map.of("x", List.of(entry("a"), entry("inner"))));
        var a = createTask("a", TaskModel.Status.COMPLETED, false, false);
        var wfJoinPair = createJoinWorkflow(List.of(outer, a, inner));
        wfJoinPair.getRight().getInputData().put("joinOn", List.of("outer"));
        var join = new Join(properties);

        // the inner case's task is not scheduled yet
        assertFalse(join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor));

        wfJoinPair
                .getLeft()
                .getTasks()
                .add(createTask("c", TaskModel.Status.COMPLETED, false, false));
        assertTrue(join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor));
    }

    @Test
    public void testJoinNamingASwitchWithAnEmptyCaseCompletesWithTheSwitch() {
        var sw = switchTask("sw", Map.of("x", List.of()));
        var wfJoinPair = createJoinWorkflow(List.of(sw));
        var join = new Join(properties);

        assertTrue(join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor));
        assertEquals(TaskModel.Status.COMPLETED, wfJoinPair.getRight().getStatus());
    }

    @Test
    public void testJoinInALoopWaitsForTheCaseOfTheSameIteration() {
        var sw = switchTask("sw__2", Map.of("x", List.of(entry("a"))));
        sw.setIteration(2);
        var earlier = createTask("a__1", TaskModel.Status.COMPLETED, false, false);
        var wfJoinPair = createJoinWorkflow(List.of(earlier, sw));
        TaskModel joinTask = wfJoinPair.getRight();
        joinTask.setIteration(2);
        joinTask.getInputData().put("joinOn", List.of("sw"));
        joinTask.setWorkflowTask(new WorkflowTask());
        joinTask.getWorkflowTask().setTaskReferenceName("join");
        joinTask.setReferenceTaskName("join__2");
        var join = new Join(properties);

        assertFalse(join.execute(wfJoinPair.getLeft(), joinTask, executor));

        wfJoinPair
                .getLeft()
                .getTasks()
                .add(createTask("a__2", TaskModel.Status.COMPLETED, false, false));
        assertTrue(join.execute(wfJoinPair.getLeft(), joinTask, executor));
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
    public void testJoinIsCanceledWhenForkedTaskIsCanceled() {
        // a manually terminated sub-workflow surfaces as a CANCELED forked task; the join
        // must propagate the cancellation instead of converting it into a failure, so the
        // decider can map the workflow to TERMINATED instead of FAILED
        var task1 = createTask("task1", TaskModel.Status.COMPLETED, false, false);
        var task2 = createTask("task2", TaskModel.Status.CANCELED, false, false);

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertTrue("Join task should be terminal when a forked task is canceled", result);
        assertEquals(
                "Join task status should be CANCELED when the only unsuccessful forked task is canceled",
                TaskModel.Status.CANCELED,
                wfJoinPair.getRight().getStatus());
    }

    @Test
    public void testJoinFailsWhenAnotherForkedTaskFailsBesidesCancellation() {
        var task1 = createTask("task1", TaskModel.Status.CANCELED, false, false);
        var task2 = createTask("task2", TaskModel.Status.FAILED, false, false);

        var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

        var join = new Join(properties);
        var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
        assertTrue(result);
        assertEquals(
                "Join task status should be FAILED when a genuine failure exists alongside a cancellation",
                TaskModel.Status.FAILED,
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
