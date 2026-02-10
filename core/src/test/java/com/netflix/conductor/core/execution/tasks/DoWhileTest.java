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
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class DoWhileTest {

    @Mock private ExecutionDAOFacade executionDAOFacade;

    private ParametersUtils parametersUtils;
    private DoWhile doWhile;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        parametersUtils = new ParametersUtils(new ObjectMapper());
        doWhile = new DoWhile(parametersUtils, executionDAOFacade);
    }

    @Test
    public void testRemoveIterations_WithKeepLastN_RemovesOldIterations() {
        // Create workflow with 10 iterations, keep last 3
        WorkflowModel workflow = createWorkflowWithIterations(10, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(10);

        // Execute removal
        doWhile.removeIterations(workflow, doWhileTask, 3);

        // Should remove 7 iterations * 3 tasks = 21 tasks
        verify(executionDAOFacade, times(21)).removeTask(anyString());
    }

    @Test
    public void testRemoveIterations_BelowThreshold_RemovesNothing() {
        // Create workflow with 3 iterations, keep last 5
        WorkflowModel workflow = createWorkflowWithIterations(3, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(3);

        // Execute removal
        doWhile.removeIterations(workflow, doWhileTask, 5);

        // Should not remove anything (iteration 3 <= keepLastN 5)
        verify(executionDAOFacade, never()).removeTask(anyString());
    }

    @Test
    public void testRemoveIterations_ExactBoundary_RemovesNothing() {
        // Create workflow with 5 iterations, keep last 5
        WorkflowModel workflow = createWorkflowWithIterations(5, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(5);

        // Execute removal
        doWhile.removeIterations(workflow, doWhileTask, 5);

        // Should not remove anything (iteration 5 == keepLastN 5)
        verify(executionDAOFacade, never()).removeTask(anyString());
    }

    @Test
    public void testRemoveIterations_FirstIteration_RemovesNothing() {
        // Create workflow with 1 iteration
        WorkflowModel workflow = createWorkflowWithIterations(1, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(1);

        // Execute removal
        doWhile.removeIterations(workflow, doWhileTask, 3);

        // Should not remove anything (no old iterations yet)
        verify(executionDAOFacade, never()).removeTask(anyString());
    }

    @Test
    public void testRemoveIterations_KeepLastOne_RemovesAllButLast() {
        // Create workflow with 10 iterations, keep last 1
        WorkflowModel workflow = createWorkflowWithIterations(10, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(10);

        // Execute removal
        doWhile.removeIterations(workflow, doWhileTask, 1);

        // Should remove 9 iterations * 3 tasks = 27 tasks
        verify(executionDAOFacade, times(27)).removeTask(anyString());
    }

    @Test
    public void testRemoveIterations_DoesNotRemoveDoWhileTaskItself() {
        // Create workflow with 5 iterations
        WorkflowModel workflow = createWorkflowWithIterations(5, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(5);

        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);

        // Execute removal
        doWhile.removeIterations(workflow, doWhileTask, 2);

        // Capture all removed task IDs
        verify(executionDAOFacade, atLeastOnce()).removeTask(taskIdCaptor.capture());
        List<String> removedTaskIds = taskIdCaptor.getAllValues();

        // Verify DO_WHILE task itself was not removed
        assertFalse(
                "DO_WHILE task should not be removed",
                removedTaskIds.contains(doWhileTask.getTaskId()));
    }

    @Test
    public void testRemoveIterations_OnlyRemovesTasksFromOldIterations() {
        // Create workflow with 5 iterations, keep last 2
        WorkflowModel workflow = createWorkflowWithIterations(5, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(5);

        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);

        // Execute removal
        doWhile.removeIterations(workflow, doWhileTask, 2);

        // Capture all removed task IDs
        verify(executionDAOFacade, times(9)).removeTask(taskIdCaptor.capture()); // 3 iterations * 3
        // tasks
        List<String> removedTaskIds = taskIdCaptor.getAllValues();

        // Get tasks that should remain (iterations 4, 5)
        List<TaskModel> remainingTasks =
                workflow.getTasks().stream()
                        .filter(t -> t.getIteration() >= 4)
                        .collect(Collectors.toList());

        // Verify no remaining tasks were removed
        for (TaskModel task : remainingTasks) {
            assertFalse(
                    "Task from iteration "
                            + task.getIteration()
                            + " should not be removed: "
                            + task.getReferenceTaskName(),
                    removedTaskIds.contains(task.getTaskId()));
        }

        // Verify old tasks were removed (iterations 1, 2, 3)
        List<TaskModel> oldTasks =
                workflow.getTasks().stream()
                        .filter(t -> t.getIteration() <= 3)
                        .filter(
                                t ->
                                        !t.getReferenceTaskName()
                                                .equals(doWhileTask.getReferenceTaskName()))
                        .collect(Collectors.toList());

        assertEquals("Should have 9 old tasks", 9, oldTasks.size());
        for (TaskModel task : oldTasks) {
            assertTrue(
                    "Task from iteration "
                            + task.getIteration()
                            + " should be removed: "
                            + task.getReferenceTaskName(),
                    removedTaskIds.contains(task.getTaskId()));
        }
    }

    @Test
    public void testRemoveIterations_ContinuesOnDaoFailure() {
        // Create workflow with 3 iterations
        WorkflowModel workflow = createWorkflowWithIterations(3, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(3);

        // Get first task to simulate failure
        TaskModel firstTask =
                workflow.getTasks().stream()
                        .filter(t -> t.getIteration() == 1)
                        .filter(
                                t ->
                                        !t.getReferenceTaskName()
                                                .equals(doWhileTask.getReferenceTaskName()))
                        .findFirst()
                        .orElseThrow();

        // Simulate failure on first task removal
        doThrow(new RuntimeException("Database error"))
                .when(executionDAOFacade)
                .removeTask(firstTask.getTaskId());

        // Execute removal - should not throw exception
        doWhile.removeIterations(workflow, doWhileTask, 2);

        // Should still attempt to remove all old iteration tasks (3 tasks from iteration 1)
        verify(executionDAOFacade, times(3)).removeTask(anyString());
    }

    @Test
    public void testRemoveIterations_VerifiesCorrectTaskIdsRemoved() {
        // Create workflow with specific task IDs
        WorkflowModel workflow = createWorkflowWithIterations(4, 2);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(4);

        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);

        // Execute removal (should remove iterations 1, 2)
        doWhile.removeIterations(workflow, doWhileTask, 2);

        verify(executionDAOFacade, times(4)).removeTask(taskIdCaptor.capture()); // 2 iterations * 2
        // tasks
        List<String> removedTaskIds = taskIdCaptor.getAllValues();

        // Get expected task IDs (from iterations 1 and 2)
        Set<String> expectedRemovedIds =
                workflow.getTasks().stream()
                        .filter(t -> t.getIteration() <= 2)
                        .filter(
                                t ->
                                        !t.getReferenceTaskName()
                                                .equals(doWhileTask.getReferenceTaskName()))
                        .map(TaskModel::getTaskId)
                        .collect(Collectors.toSet());

        assertEquals("Should remove correct number of tasks", 4, expectedRemovedIds.size());
        assertEquals(
                "Should remove exact expected tasks",
                expectedRemovedIds,
                Set.copyOf(removedTaskIds));
    }

    @Test
    public void testRemoveIterations_HandlesEmptyWorkflow() {
        // Create empty workflow
        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(new ArrayList<>());

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.setIteration(5);
        workflow.getTasks().add(doWhileTask);

        // Execute removal - should handle gracefully
        doWhile.removeIterations(workflow, doWhileTask, 3);

        // Should not attempt to remove anything
        verify(executionDAOFacade, never()).removeTask(anyString());
    }

    @Test
    public void testRemoveIterations_WithMultipleTasksPerIteration() {
        // Create workflow with 5 tasks per iteration
        WorkflowModel workflow = createWorkflowWithIterations(5, 5);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(5);

        // Execute removal (keep last 2 iterations)
        doWhile.removeIterations(workflow, doWhileTask, 2);

        // Should remove 3 iterations * 5 tasks = 15 tasks
        verify(executionDAOFacade, times(15)).removeTask(anyString());
    }

    // Helper methods

    private WorkflowModel createWorkflowWithDef() {
        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef def = new WorkflowDef();
        def.setName("test-workflow");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("test-workflow-" + System.currentTimeMillis());
        return workflow;
    }

    private WorkflowModel createWorkflowWithIterations(int iterations, int tasksPerIteration) {
        WorkflowModel workflow = createWorkflowWithDef();

        List<TaskModel> allTasks = new ArrayList<>();

        // Create DO_WHILE task
        TaskModel doWhileTask = createDoWhileTask();
        allTasks.add(doWhileTask);

        // Create tasks for each iteration
        for (int iteration = 1; iteration <= iterations; iteration++) {
            for (int taskNum = 1; taskNum <= tasksPerIteration; taskNum++) {
                TaskModel task = new TaskModel();
                task.setTaskId("task-" + iteration + "-" + taskNum);
                task.setReferenceTaskName("loopTask" + taskNum + "__" + iteration);
                task.setIteration(iteration);
                task.setTaskType("SIMPLE");
                task.setStatus(TaskModel.Status.COMPLETED);

                WorkflowTask workflowTask = new WorkflowTask();
                workflowTask.setTaskReferenceName("loopTask" + taskNum);
                task.setWorkflowTask(workflowTask);

                allTasks.add(task);
            }
        }

        workflow.setTasks(allTasks);
        return workflow;
    }

    private TaskModel createDoWhileTask() {
        TaskModel doWhileTask = new TaskModel();
        doWhileTask.setTaskId("do-while-task");
        doWhileTask.setReferenceTaskName("doWhileTask");
        doWhileTask.setTaskType("DO_WHILE");
        doWhileTask.setStatus(TaskModel.Status.IN_PROGRESS);

        // Create workflow task with loopOver definition
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("doWhileTask");
        workflowTask.setType("DO_WHILE");

        // Add loop over tasks
        List<WorkflowTask> loopOverTasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            WorkflowTask loopTask = new WorkflowTask();
            loopTask.setTaskReferenceName("loopTask" + i);
            loopOverTasks.add(loopTask);
        }
        workflowTask.setLoopOver(loopOverTasks);

        // Set input parameters with keepLastN
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("keepLastN", 3);
        workflowTask.setInputParameters(inputParams);

        doWhileTask.setWorkflowTask(workflowTask);
        return doWhileTask;
    }

    private TaskModel getDoWhileTask(WorkflowModel workflow) {
        return workflow.getTasks().stream()
                .filter(t -> "DO_WHILE".equals(t.getTaskType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No DO_WHILE task found"));
    }

    // List iteration tests

    @Test
    public void testIsListIteration_WithItemsParameter_ReturnsTrue() {
        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("${workflow.input.myList}");

        assertTrue(
                "Should identify as list iteration when items parameter is set",
                doWhile.isListIteration(doWhileTask));
    }

    @Test
    public void testIsListIteration_WithoutItemsParameter_ReturnsFalse() {
        TaskModel doWhileTask = createDoWhileTask();
        // No items parameter set

        assertFalse(
                "Should not identify as list iteration when items parameter is not set",
                doWhile.isListIteration(doWhileTask));
    }

    @Test
    public void testIsListIteration_WithEmptyItemsParameter_ReturnsFalse() {
        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("   ");

        assertFalse(
                "Should not identify as list iteration when items parameter is empty",
                doWhile.isListIteration(doWhileTask));
    }

    @Test
    public void testEvaluateItemsList_WithValidList_ReturnsCorrectItems() {
        WorkflowModel workflow = createWorkflowWithDef();

        // Set up workflow input with a list
        Map<String, Object> workflowInput = new HashMap<>();
        List<String> itemsList = List.of("item1", "item2", "item3");
        workflowInput.put("myList", itemsList);
        workflow.setInput(workflowInput);

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("${workflow.input.myList}");

        List<Object> result = doWhile.evaluateItemsList(workflow, doWhileTask);

        assertEquals("Should return correct number of items", 3, result.size());
        assertEquals("Should have correct first item", "item1", result.get(0));
        assertEquals("Should have correct second item", "item2", result.get(1));
        assertEquals("Should have correct third item", "item3", result.get(2));
    }

    @Test
    public void testEvaluateItemsList_WithEmptyList_ReturnsEmptyList() {
        WorkflowModel workflow = createWorkflowWithDef();

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("myList", new ArrayList<>());
        workflow.setInput(workflowInput);

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("${workflow.input.myList}");

        List<Object> result = doWhile.evaluateItemsList(workflow, doWhileTask);

        assertTrue("Should return empty list for empty input", result.isEmpty());
    }

    @Test
    public void testEvaluateItemsList_WithNullItems_ReturnsEmptyList() {
        WorkflowModel workflow = createWorkflowWithDef();

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems(null);

        List<Object> result = doWhile.evaluateItemsList(workflow, doWhileTask);

        assertTrue("Should return empty list for null items parameter", result.isEmpty());
    }

    @Test
    public void testInjectLoopVariables_InjectsCorrectValues() {
        WorkflowModel workflow = createWorkflowWithDef();

        // Set up workflow input with a list
        Map<String, Object> workflowInput = new HashMap<>();
        List<String> itemsList = List.of("apple", "banana", "cherry");
        workflowInput.put("fruits", itemsList);
        workflow.setInput(workflowInput);

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("${workflow.input.fruits}");
        doWhileTask.setIteration(2); // Second iteration (loopIndex = 1)

        doWhile.injectLoopVariables(workflow, doWhileTask);

        // Verify loopIndex is injected (0-based)
        assertEquals("loopIndex should be 1", 1, doWhileTask.getOutputData().get("loopIndex"));

        // Verify loopItem is injected
        assertEquals(
                "loopItem should be 'banana'",
                "banana",
                doWhileTask.getOutputData().get("loopItem"));
    }

    @Test
    public void testInjectLoopVariables_FirstIteration() {
        WorkflowModel workflow = createWorkflowWithDef();

        Map<String, Object> workflowInput = new HashMap<>();
        List<Integer> itemsList = List.of(10, 20, 30);
        workflowInput.put("numbers", itemsList);
        workflow.setInput(workflowInput);

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("${workflow.input.numbers}");
        doWhileTask.setIteration(1); // First iteration (loopIndex = 0)

        doWhile.injectLoopVariables(workflow, doWhileTask);

        assertEquals("loopIndex should be 0", 0, doWhileTask.getOutputData().get("loopIndex"));
        assertEquals("loopItem should be 10", 10, doWhileTask.getOutputData().get("loopItem"));
    }

    @Test
    public void testInjectLoopVariables_DoesNothingForCounterBased() {
        WorkflowModel workflow = createWorkflowWithDef();

        TaskModel doWhileTask = createDoWhileTask();
        // No items parameter set - counter-based iteration
        doWhileTask.setIteration(3);

        Map<String, Object> outputBefore = new HashMap<>(doWhileTask.getOutputData());
        doWhile.injectLoopVariables(workflow, doWhileTask);
        Map<String, Object> outputAfter = new HashMap<>(doWhileTask.getOutputData());

        assertFalse(
                "Should not inject loopIndex for counter-based loops",
                outputAfter.containsKey("loopIndex"));
        assertFalse(
                "Should not inject loopItem for counter-based loops",
                outputAfter.containsKey("loopItem"));
    }

    @Test
    public void testEvaluateCondition_ListIteration_ContinuesUntilEnd() {
        WorkflowModel workflow = createWorkflowWithDef();

        Map<String, Object> workflowInput = new HashMap<>();
        List<String> itemsList = List.of("a", "b", "c");
        workflowInput.put("items", itemsList);
        workflow.setInput(workflowInput);
        workflow.setTasks(new ArrayList<>());

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("${workflow.input.items}");
        doWhileTask.getWorkflowTask().setLoopCondition(null); // No additional condition

        // Test iteration 1 (loopIndex=0) - should continue
        doWhileTask.setIteration(1);
        assertTrue(
                "Should continue after first iteration", doWhile.evaluateCondition(workflow, doWhileTask));

        // Test iteration 2 (loopIndex=1) - should continue
        doWhileTask.setIteration(2);
        assertTrue(
                "Should continue after second iteration",
                doWhile.evaluateCondition(workflow, doWhileTask));

        // Test iteration 3 (loopIndex=2) - should stop (last item)
        doWhileTask.setIteration(3);
        assertFalse(
                "Should stop after last iteration", doWhile.evaluateCondition(workflow, doWhileTask));
    }

    @Test
    public void testEvaluateCondition_ListIteration_WithCustomCondition() {
        WorkflowModel workflow = createWorkflowWithDef();

        Map<String, Object> workflowInput = new HashMap<>();
        List<Integer> itemsList = List.of(5, 10, 15, 20);
        workflowInput.put("numbers", itemsList);
        workflow.setInput(workflowInput);
        workflow.setTasks(new ArrayList<>());

        TaskModel doWhileTask = createDoWhileTask();
        doWhileTask.getWorkflowTask().setItems("${workflow.input.numbers}");
        // Custom condition: continue only if loopItem < 15
        doWhileTask.getWorkflowTask().setLoopCondition("$.loopItem < 15");

        // Test iteration 1 (loopIndex=0, loopItem=5) - should continue
        doWhileTask.setIteration(1);
        assertTrue(
                "Should continue when loopItem < 15",
                doWhile.evaluateCondition(workflow, doWhileTask));

        // Test iteration 2 (loopIndex=1, loopItem=10) - should continue
        doWhileTask.setIteration(2);
        assertTrue(
                "Should continue when loopItem < 15",
                doWhile.evaluateCondition(workflow, doWhileTask));

        // Test iteration 3 (loopIndex=2, loopItem=15) - should stop (condition false)
        doWhileTask.setIteration(3);
        assertFalse(
                "Should stop when loopItem >= 15",
                doWhile.evaluateCondition(workflow, doWhileTask));
    }

    @Test
    public void testEvaluateCondition_CounterBased_BackwardCompatibility() {
        WorkflowModel workflow = createWorkflowWithDef();

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("maxIterations", 3);
        workflow.setInput(workflowInput);
        workflow.setTasks(new ArrayList<>());

        TaskModel doWhileTask = createDoWhileTask();
        // No items parameter - counter-based iteration
        doWhileTask.getWorkflowTask().setLoopCondition("$.doWhileTask.iteration < 3");

        // Test iteration 1 - should continue
        doWhileTask.setIteration(1);
        doWhileTask.addOutput("iteration", 1);
        assertTrue(
                "Should continue counter-based loop",
                doWhile.evaluateCondition(workflow, doWhileTask));

        // Test iteration 3 - should stop
        doWhileTask.setIteration(3);
        doWhileTask.addOutput("iteration", 3);
        assertFalse(
                "Should stop counter-based loop", doWhile.evaluateCondition(workflow, doWhileTask));
    }
}
