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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestJoin {

        private final ConductorProperties properties = new ConductorProperties();
        private final WorkflowExecutor executor = mock(WorkflowExecutor.class);
        private SystemTaskRegistry systemTaskRegistry;

        @Before
        public void setUp() {
                // Create a mock SystemTaskRegistry and register Switch task
                systemTaskRegistry = mock(SystemTaskRegistry.class);
                Switch switchTask = new Switch();

                when(systemTaskRegistry.get(TaskType.TASK_TYPE_SWITCH)).thenReturn(switchTask);
                when(systemTaskRegistry.isSystemTask(TaskType.TASK_TYPE_SWITCH)).thenReturn(true);
        }

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
                var taskRefNames = tasks.stream().map(TaskModel::getReferenceTaskName).collect(Collectors.toList());
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

                var join = new Join(properties, systemTaskRegistry);
                var result = join.execute(wfJoinPair.getLeft(), wfJoinPair.getRight(), executor);
                assertFalse(result);
        }

        @Test
        public void testJoinCompletesSuccessfullyWhenAllTasksSucceed() {
                var task1 = createTask("task1", TaskModel.Status.COMPLETED, false, false);
                var task2 = createTask("task2", TaskModel.Status.COMPLETED, false, false);

                var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

                var join = new Join(properties, systemTaskRegistry);
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

                var join = new Join(properties, systemTaskRegistry);
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

                var join = new Join(properties, systemTaskRegistry);
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

                var join = new Join(properties, systemTaskRegistry);
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

                var join = new Join(properties, systemTaskRegistry);
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
                var task2 = createTask(
                                "task2",
                                TaskModel.Status.IN_PROGRESS,
                                false,
                                false); // Initially not in a terminal state.

                var wfJoinPair = createJoinWorkflow(List.of(task1, task2));

                // First execution: Task 2 is not yet terminal.
                var join = new Join(properties, systemTaskRegistry);
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
                var join = new Join(properties, systemTaskRegistry);
                var taskModel = createTask("join1", TaskModel.Status.COMPLETED, false, false);
                taskModel.setPollCount(properties.getSystemTaskPostponeThreshold() - 1);
                var opt = join.getEvaluationOffset(taskModel, 30L);
                assertEquals(0L, (long) opt.orElseThrow());
        }

        @Test
        public void testEvaluationOffsetWhenPollCountIsAboveThreshold() {
                final var maxOffset = 30L;
                var join = new Join(properties, systemTaskRegistry);
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

        // ==================== Tests for Switch Task Resolution ====================

        private TaskModel createSwitchTask(
                        String referenceName,
                        TaskModel.Status status,
                        String selectedCase,
                        Map<String, List<WorkflowTask>> decisionCases,
                        List<WorkflowTask> defaultCase) {
                TaskModel task = new TaskModel();
                task.setStatus(status);
                task.setReferenceTaskName(referenceName);
                task.setTaskType("SWITCH");

                WorkflowTask workflowTask = new WorkflowTask();
                workflowTask.setType("SWITCH");
                workflowTask.setTaskReferenceName(referenceName);
                workflowTask.setDecisionCases(decisionCases);
                workflowTask.setDefaultCase(defaultCase != null ? defaultCase : new ArrayList<>());
                task.setWorkflowTask(workflowTask);

                if (selectedCase != null) {
                        task.addOutput("selectedCase", selectedCase);
                }

                return task;
        }

        private WorkflowTask createWorkflowTaskDef(String name, String refName, String type) {
                WorkflowTask wt = new WorkflowTask();
                wt.setName(name);
                wt.setTaskReferenceName(refName);
                wt.setType(type);
                return wt;
        }

        @Test
        public void testResolveTaskReference_NonSwitchTask() {
                // Simple task, should not be resolved to anything else
                var task1 = createTask("task1", TaskModel.Status.COMPLETED, false, false);

                WorkflowModel workflow = new WorkflowModel();
                workflow.getTasks().add(task1);

                var join = new Join(properties, systemTaskRegistry);
                String resolved = join.resolveTaskReference(workflow, "task1");

                assertEquals("task1", resolved);
        }

        @Test
        public void testResolveTaskReference_SwitchTaskNotCompleted() {
                // Switch task that is IN_PROGRESS should not be resolved
                Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
                decisionCases.put(
                                "caseA", List.of(createWorkflowTaskDef("childTask", "childTask", "SIMPLE")));

                var switchTask = createSwitchTask(
                                "switchTask", TaskModel.Status.IN_PROGRESS, null, decisionCases, null);

                WorkflowModel workflow = new WorkflowModel();
                workflow.getTasks().add(switchTask);

                var join = new Join(properties, systemTaskRegistry);
                String resolved = join.resolveTaskReference(workflow, "switchTask");

                assertEquals("switchTask", resolved);
        }

        @Test
        public void testResolveTaskReference_SwitchTaskWithChildTasks() {
                // Switch task that is completed should resolve to its last child task
                WorkflowTask childTask1 = createWorkflowTaskDef("childTask1", "t1", "SIMPLE");
                WorkflowTask childTask2 = createWorkflowTaskDef("childTask2", "t2", "SIMPLE");

                Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
                decisionCases.put("caseA", List.of(childTask1, childTask2));

                var switchTask = createSwitchTask(
                                "switchTask", TaskModel.Status.COMPLETED, "caseA", decisionCases, null);

                // Also add the child tasks to the workflow
                var t1 = createTask("t1", TaskModel.Status.COMPLETED, false, false);
                var t2 = createTask("t2", TaskModel.Status.IN_PROGRESS, false, false);

                WorkflowModel workflow = new WorkflowModel();
                workflow.getTasks().add(switchTask);
                workflow.getTasks().add(t1);
                workflow.getTasks().add(t2);

                var join = new Join(properties, systemTaskRegistry);
                String resolved = join.resolveTaskReference(workflow, "switchTask");

                assertEquals("t2", resolved); // Should resolve to the last task in the case branch
        }

        @Test
        public void testResolveTaskReference_SwitchTaskWithDefaultCase() {
                // Switch task using default case
                WorkflowTask defaultTask = createWorkflowTaskDef("defaultTask", "defaultRef", "SIMPLE");

                Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
                decisionCases.put(
                                "caseA", List.of(createWorkflowTaskDef("caseATask", "caseARef", "SIMPLE")));

                var switchTask = createSwitchTask(
                                "switchTask",
                                TaskModel.Status.COMPLETED,
                                "unknownCase", // This case doesn't exist, so default should be used
                                decisionCases,
                                List.of(defaultTask));

                var defaultRefTask = createTask("defaultRef", TaskModel.Status.IN_PROGRESS, false, false);

                WorkflowModel workflow = new WorkflowModel();
                workflow.getTasks().add(switchTask);
                workflow.getTasks().add(defaultRefTask);

                var join = new Join(properties, systemTaskRegistry);
                String resolved = join.resolveTaskReference(workflow, "switchTask");

                assertEquals("defaultRef", resolved);
        }

        @Test
        public void testResolveTaskReference_SwitchTaskWithEmptyBranch() {
                // Switch task with empty branch should fall back to the switch task itself
                Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
                decisionCases.put("caseA", new ArrayList<>()); // Empty case

                var switchTask = createSwitchTask(
                                "switchTask", TaskModel.Status.COMPLETED, "caseA", decisionCases, null);

                WorkflowModel workflow = new WorkflowModel();
                workflow.getTasks().add(switchTask);

                var join = new Join(properties, systemTaskRegistry);
                String resolved = join.resolveTaskReference(workflow, "switchTask");

                assertEquals("switchTask", resolved); // Falls back to switch itself
        }

        @Test
        public void testResolveTaskReference_TaskNotScheduled() {
                // Reference to a task that is not yet scheduled should keep original reference
                WorkflowModel workflow = new WorkflowModel();
                // No tasks added to workflow

                var join = new Join(properties, systemTaskRegistry);
                String resolved = join.resolveTaskReference(workflow, "notScheduledTask");

                assertEquals("notScheduledTask", resolved);
        }

        @Test
        public void testResolveTaskReference_MultipleTasksWithSwitch() {
                // Mix of regular tasks and switch tasks
                WorkflowTask childTask = createWorkflowTaskDef("childTask", "switchChild", "SIMPLE");
                Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
                decisionCases.put("caseA", List.of(childTask));

                var regularTask = createTask("regularTask", TaskModel.Status.COMPLETED, false, false);
                var switchTask = createSwitchTask(
                                "switchTask", TaskModel.Status.COMPLETED, "caseA", decisionCases, null);
                var switchChildTask = createTask("switchChild", TaskModel.Status.IN_PROGRESS, false, false);

                WorkflowModel workflow = new WorkflowModel();
                workflow.getTasks().add(regularTask);
                workflow.getTasks().add(switchTask);
                workflow.getTasks().add(switchChildTask);

                var join = new Join(properties, systemTaskRegistry);
                String resolvedRegular = join.resolveTaskReference(workflow, "regularTask");
                String resolvedSwitch = join.resolveTaskReference(workflow, "switchTask");

                assertEquals("regularTask", resolvedRegular);
                assertEquals("switchChild", resolvedSwitch); // Switch resolved to child
        }

        @Test
        public void testJoinWaitsForSwitchChildTasks() {
                // Test that Join waits for Switch child tasks, not just the Switch itself
                WorkflowTask childTask1 = createWorkflowTaskDef("childTask1", "t1", "SIMPLE");
                WorkflowTask childTask2 = createWorkflowTaskDef("childTask2", "t2", "SIMPLE");

                Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
                decisionCases.put("caseA", List.of(childTask1, childTask2));

                var switchTask = createSwitchTask(
                                "switchTask", TaskModel.Status.COMPLETED, "caseA", decisionCases, null);
                var t1 = createTask("t1", TaskModel.Status.COMPLETED, false, false);
                var t2 = createTask(
                                "t2",
                                TaskModel.Status.IN_PROGRESS,
                                false,
                                false); // Last task still running

                // Create workflow with join
                WorkflowModel workflow = new WorkflowModel();
                var joinTask = new TaskModel();
                joinTask.setReferenceTaskName("join");
                joinTask.getInputData().put("joinOn", List.of("switchTask"));

                workflow.getTasks().add(switchTask);
                workflow.getTasks().add(t1);
                workflow.getTasks().add(t2);
                workflow.getTasks().add(joinTask);

                var join = new Join(properties, systemTaskRegistry);
                boolean result = join.execute(workflow, joinTask, executor);

                // Join should not complete because t2 (the resolved switch child) is still
                // running
                assertFalse("Join should wait for Switch child task t2", result);

                // Now complete t2
                t2.setStatus(TaskModel.Status.COMPLETED);
                result = join.execute(workflow, joinTask, executor);

                assertTrue("Join should complete when all resolved tasks are terminal", result);
                assertEquals(TaskModel.Status.COMPLETED, joinTask.getStatus());
        }

        @Test
        public void testJoinFailsWhenSwitchChildTaskFails() {
                // Test that Join fails when a Switch child task fails
                WorkflowTask childTask = createWorkflowTaskDef("childTask", "t1", "SIMPLE");

                Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
                decisionCases.put("caseA", List.of(childTask));

                var switchTask = createSwitchTask(
                                "switchTask", TaskModel.Status.COMPLETED, "caseA", decisionCases, null);
                var t1 = createTask("t1", TaskModel.Status.FAILED, false, false);
                t1.setReasonForIncompletion("Child task failed");

                WorkflowModel workflow = new WorkflowModel();
                var joinTask = new TaskModel();
                joinTask.setReferenceTaskName("join");
                joinTask.getInputData().put("joinOn", List.of("switchTask"));

                workflow.getTasks().add(switchTask);
                workflow.getTasks().add(t1);
                workflow.getTasks().add(joinTask);

                var join = new Join(properties, systemTaskRegistry);
                boolean result = join.execute(workflow, joinTask, executor);

                assertTrue("Join should complete (with failure)", result);
                assertEquals(TaskModel.Status.FAILED, joinTask.getStatus());
                assertTrue(joinTask.getReasonForIncompletion().contains("Child task failed"));
        }

        @Test
        public void testNestedSwitch_TerminalTaskResolution() {
                // Test that nested Switch tasks are properly resolved via getTerminalTaskRef
                WorkflowTask innerChildTask = createWorkflowTaskDef("innerChild", "innerChildRef", "SIMPLE");

                Map<String, List<WorkflowTask>> innerDecisionCases = new HashMap<>();
                innerDecisionCases.put("innerCase", List.of(innerChildTask));

                WorkflowTask innerSwitchTaskDef = createWorkflowTaskDef("innerSwitch", "innerSwitchRef", "SWITCH");
                innerSwitchTaskDef.setDecisionCases(innerDecisionCases);

                Map<String, List<WorkflowTask>> outerDecisionCases = new HashMap<>();
                outerDecisionCases.put("outerCase", List.of(innerSwitchTaskDef));

                var outerSwitchTask = createSwitchTask(
                                "outerSwitch",
                                TaskModel.Status.COMPLETED,
                                "outerCase",
                                outerDecisionCases,
                                null);

                // Create the inner switch task model that is already completed
                var innerSwitchTask = createSwitchTask(
                                "innerSwitchRef",
                                TaskModel.Status.COMPLETED,
                                "innerCase",
                                innerDecisionCases,
                                null);

                var innerChildTaskModel = createTask("innerChildRef", TaskModel.Status.COMPLETED, false, false);

                WorkflowModel workflow = new WorkflowModel();
                workflow.getTasks().add(outerSwitchTask);
                workflow.getTasks().add(innerSwitchTask);
                workflow.getTasks().add(innerChildTaskModel);

                // Test polymorphic resolution via Switch.getTerminalTaskRef()
                Switch switchSystemTask = new Switch();
                String lastTaskRef = switchSystemTask.getTerminalTaskRef(workflow, outerSwitchTask, systemTaskRegistry);

                // Should resolve through the nested switch to the innermost task
                assertEquals("innerChildRef", lastTaskRef);
        }
}
