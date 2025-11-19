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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style tests for DoWhile task cleanup functionality. These tests verify the
 * interaction between removeIterations() and ExecutionDAOFacade using a mock that simulates
 * database behavior.
 */
public class DoWhileIntegrationTest {

    private DoWhile doWhile;
    private ExecutionDAOFacade executionDAOFacade;

    // Simulated in-memory database
    private Map<String, TaskModel> taskDatabase;

    @Before
    public void setup() {
        // Create fresh in-memory "database" for each test
        taskDatabase = new ConcurrentHashMap<>();

        // Create mock ExecutionDAOFacade with real behavior
        executionDAOFacade = mock(ExecutionDAOFacade.class);

        // Configure mock to actually remove from our simulated database
        doAnswer(
                        invocation -> {
                            String taskId = invocation.getArgument(0);
                            taskDatabase.remove(taskId);
                            return null;
                        })
                .when(executionDAOFacade)
                .removeTask(anyString());

        // Create real DoWhile task handler
        ParametersUtils parametersUtils = new ParametersUtils(new ObjectMapper());
        doWhile = new DoWhile(parametersUtils, executionDAOFacade);
    }

    @Test
    public void testRemoveIterations_ActuallyRemovesFromDatabase() {
        // Create workflow with 10 iterations (3 tasks per iteration = 30 tasks total)
        WorkflowModel workflow = createAndPersistWorkflow(10, 3);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(10);

        // Verify all 30 tasks exist in "database"
        assertEquals("Should have 30 tasks initially", 30, taskDatabase.size());

        // Execute cleanup - keep last 3 iterations
        doWhile.removeIterations(workflow, doWhileTask, 3);

        // Verify only last 3 iterations remain (9 tasks)
        assertEquals("Should have 9 tasks remaining", 9, taskDatabase.size());

        // Verify correct iterations remain (8, 9, 10)
        Set<Integer> remainingIterations =
                taskDatabase.values().stream()
                        .map(TaskModel::getIteration)
                        .collect(Collectors.toSet());
        assertEquals("Should keep iterations 8, 9, 10", Set.of(8, 9, 10), remainingIterations);
    }

    @Test
    public void testRemoveIterations_WithLargeIterationCount() {
        // Create workflow with 100 iterations
        WorkflowModel workflow = createAndPersistWorkflow(100, 2);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(100);

        // Verify all 200 tasks exist
        assertEquals(200, taskDatabase.size());

        // Execute cleanup - keep last 10 iterations
        doWhile.removeIterations(workflow, doWhileTask, 10);

        // Verify only last 10 iterations remain (20 tasks)
        assertEquals("Should have 20 tasks remaining", 20, taskDatabase.size());

        // Verify correct iteration range
        Set<Integer> remainingIterations =
                taskDatabase.values().stream()
                        .map(TaskModel::getIteration)
                        .collect(Collectors.toSet());

        for (int i = 91; i <= 100; i++) {
            assertTrue("Should contain iteration " + i, remainingIterations.contains(i));
        }
        assertEquals("Should have exactly 10 iterations", 10, remainingIterations.size());
    }

    @Test
    public void testRemoveIterations_DoesNotAffectOtherWorkflows() {
        // Create two separate workflows
        WorkflowModel workflow1 = createAndPersistWorkflow(5, 2);
        WorkflowModel workflow2 = createAndPersistWorkflow(5, 2);

        TaskModel doWhileTask1 = getDoWhileTask(workflow1);
        doWhileTask1.setIteration(5);

        String wf1Id = workflow1.getWorkflowId();
        String wf2Id = workflow2.getWorkflowId();

        // Count tasks for each workflow
        long wf1CountBefore =
                taskDatabase.values().stream()
                        .filter(t -> wf1Id.equals(t.getWorkflowInstanceId()))
                        .count();
        long wf2CountBefore =
                taskDatabase.values().stream()
                        .filter(t -> wf2Id.equals(t.getWorkflowInstanceId()))
                        .count();

        assertEquals(10, wf1CountBefore);
        assertEquals(10, wf2CountBefore);

        // Execute cleanup on workflow1 only
        doWhile.removeIterations(workflow1, doWhileTask1, 2);

        // Count after cleanup
        long wf1CountAfter =
                taskDatabase.values().stream()
                        .filter(t -> wf1Id.equals(t.getWorkflowInstanceId()))
                        .count();
        long wf2CountAfter =
                taskDatabase.values().stream()
                        .filter(t -> wf2Id.equals(t.getWorkflowInstanceId()))
                        .count();

        // Verify workflow1 tasks were removed (keeping 2 iterations = 4 tasks)
        assertEquals(4, wf1CountAfter);

        // Verify workflow2 tasks are unchanged
        assertEquals("Workflow2 should be unaffected", 10, wf2CountAfter);
    }

    @Test
    public void testRemoveIterations_BelowThreshold_NoRemoval() {
        // Create workflow with 3 iterations
        WorkflowModel workflow = createAndPersistWorkflow(3, 2);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(3);

        // Store initial count
        int initialCount = taskDatabase.size();
        assertEquals("Should start with 6 tasks", 6, initialCount);

        // Execute cleanup with keepLastN > current iteration
        doWhile.removeIterations(workflow, doWhileTask, 5);

        // Verify no tasks were removed
        int finalCount = taskDatabase.size();
        assertEquals("Should not remove any tasks when below threshold", initialCount, finalCount);
    }

    @Test
    public void testRemoveIterations_VerifyTasksActuallyGone() {
        // Create workflow with specific task IDs we can track
        WorkflowModel workflow = createAndPersistWorkflow(4, 2);
        TaskModel doWhileTask = getDoWhileTask(workflow);
        doWhileTask.setIteration(4);

        // Get task IDs from iterations 1 and 2 (should be removed)
        List<String> oldTaskIds =
                workflow.getTasks().stream()
                        .filter(t -> t.getIteration() <= 2)
                        .filter(t -> !t.getTaskId().equals(doWhileTask.getTaskId()))
                        .map(TaskModel::getTaskId)
                        .collect(Collectors.toList());

        assertEquals("Should have 4 old tasks", 4, oldTaskIds.size());

        // Verify all old tasks exist before cleanup
        for (String taskId : oldTaskIds) {
            assertTrue("Task should exist before cleanup", taskDatabase.containsKey(taskId));
        }

        // Execute cleanup (keep last 2 iterations)
        doWhile.removeIterations(workflow, doWhileTask, 2);

        // Verify old tasks are actually gone from database
        for (String taskId : oldTaskIds) {
            assertFalse(
                    "Task " + taskId + " should be removed from database",
                    taskDatabase.containsKey(taskId));
        }

        // Get task IDs from iterations 3 and 4 (should remain)
        List<String> recentTaskIds =
                workflow.getTasks().stream()
                        .filter(t -> t.getIteration() >= 3)
                        .filter(t -> !t.getTaskId().equals(doWhileTask.getTaskId()))
                        .map(TaskModel::getTaskId)
                        .collect(Collectors.toList());

        // Verify recent tasks still exist
        for (String taskId : recentTaskIds) {
            assertTrue("Recent task should still exist", taskDatabase.containsKey(taskId));
        }
    }

    @Test
    public void testRemoveIterations_IncrementalCleanup() {
        // Create workflow with 5 iterations initially
        WorkflowModel workflow = createAndPersistWorkflow(5, 2);
        TaskModel doWhileTask = getDoWhileTask(workflow);

        // First cleanup at iteration 5 - keep last 3
        doWhileTask.setIteration(5);
        doWhile.removeIterations(workflow, doWhileTask, 3);

        // Should have iterations 3, 4, 5 remaining (6 tasks)
        assertEquals("Should have 6 tasks after first cleanup", 6, taskDatabase.size());
        Set<Integer> iterations1 =
                taskDatabase.values().stream()
                        .map(TaskModel::getIteration)
                        .collect(Collectors.toSet());
        assertEquals("Should have iterations 3, 4, 5", Set.of(3, 4, 5), iterations1);

        // Simulate adding more iterations (6, 7, 8)
        for (int iteration = 6; iteration <= 8; iteration++) {
            for (int taskNum = 1; taskNum <= 2; taskNum++) {
                TaskModel task = createIterationTask(workflow.getWorkflowId(), iteration, taskNum);
                workflow.getTasks().add(task);
                taskDatabase.put(task.getTaskId(), task);
            }
        }

        // Second cleanup at iteration 8 - keep last 3
        doWhileTask.setIteration(8);
        doWhile.removeIterations(workflow, doWhileTask, 3);

        // Should have iterations 6, 7, 8 remaining (6 tasks)
        assertEquals("Should have 6 tasks after second cleanup", 6, taskDatabase.size());

        Set<Integer> iterations2 =
                taskDatabase.values().stream()
                        .map(TaskModel::getIteration)
                        .collect(Collectors.toSet());
        assertEquals("Should have iterations 6, 7, 8", Set.of(6, 7, 8), iterations2);
    }

    // Helper methods

    private WorkflowModel createAndPersistWorkflow(int iterations, int tasksPerIteration) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("test-workflow-" + UUID.randomUUID());

        List<TaskModel> allTasks = new ArrayList<>();

        // Create DO_WHILE task (not stored in iteration tasks)
        TaskModel doWhileTask = createDoWhileTask(workflow.getWorkflowId(), tasksPerIteration);
        allTasks.add(doWhileTask);

        // Create and persist tasks for each iteration
        for (int iteration = 1; iteration <= iterations; iteration++) {
            for (int taskNum = 1; taskNum <= tasksPerIteration; taskNum++) {
                TaskModel task = createIterationTask(workflow.getWorkflowId(), iteration, taskNum);
                allTasks.add(task);

                // Persist task to simulated database
                taskDatabase.put(task.getTaskId(), task);
            }
        }

        workflow.setTasks(allTasks);
        return workflow;
    }

    private TaskModel createDoWhileTask(String workflowId, int tasksPerIteration) {
        TaskModel doWhileTask = new TaskModel();
        doWhileTask.setTaskId("do-while-" + UUID.randomUUID());
        doWhileTask.setWorkflowInstanceId(workflowId);
        doWhileTask.setReferenceTaskName("doWhileTask");
        doWhileTask.setTaskType("DO_WHILE");
        doWhileTask.setStatus(TaskModel.Status.IN_PROGRESS);

        // Create workflow task with loopOver definition
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("doWhileTask");
        workflowTask.setType("DO_WHILE");

        // Add loop over tasks
        List<WorkflowTask> loopOverTasks = new ArrayList<>();
        for (int i = 1; i <= tasksPerIteration; i++) {
            WorkflowTask loopTask = new WorkflowTask();
            loopTask.setTaskReferenceName("loopTask" + i);
            loopOverTasks.add(loopTask);
        }
        workflowTask.setLoopOver(loopOverTasks);

        // Set input parameters
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("keepLastN", 3);
        workflowTask.setInputParameters(inputParams);

        doWhileTask.setWorkflowTask(workflowTask);
        return doWhileTask;
    }

    private TaskModel createIterationTask(String workflowId, int iteration, int taskNum) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-" + UUID.randomUUID());
        task.setWorkflowInstanceId(workflowId);
        task.setReferenceTaskName("loopTask" + taskNum + "__" + iteration);
        task.setIteration(iteration);
        task.setTaskType("SIMPLE");
        task.setTaskDefName("loopTask" + taskNum);
        task.setStatus(TaskModel.Status.COMPLETED);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("loopTask" + taskNum);
        task.setWorkflowTask(workflowTask);

        return task;
    }

    private TaskModel getDoWhileTask(WorkflowModel workflow) {
        return workflow.getTasks().stream()
                .filter(t -> "DO_WHILE".equals(t.getTaskType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No DO_WHILE task found"));
    }
}
