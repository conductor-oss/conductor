/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkflowTaskPojoMethods {

    @Test
    void testWorkflowTaskBasicGettersAndSetters() {
        WorkflowTask workflowTask = new WorkflowTask();

        // Test default values
        assertNull(workflowTask.getName());
        assertNull(workflowTask.getTaskReferenceName());
        assertNull(workflowTask.getDescription());
        assertNotNull(workflowTask.getInputParameters());
        assertEquals(TaskType.SIMPLE.name(), workflowTask.getType());
        assertNull(workflowTask.getDynamicTaskNameParam());
        assertNull(workflowTask.getCaseValueParam());
        assertNull(workflowTask.getCaseExpression());
        assertNull(workflowTask.getScriptExpression());
        assertNotNull(workflowTask.getDecisionCases());
        assertNull(workflowTask.getDynamicForkJoinTasksParam());
        assertNull(workflowTask.getDynamicForkTasksParam());
        assertNull(workflowTask.getDynamicForkTasksInputParamName());
        assertNotNull(workflowTask.getDefaultCase());
        assertNotNull(workflowTask.getForkTasks());
        assertEquals(0, workflowTask.getStartDelay());
        assertNull(workflowTask.getSubWorkflowParam());
        assertNotNull(workflowTask.getJoinOn());
        assertNull(workflowTask.getSink());
        assertFalse(workflowTask.isOptional());
        assertNull(workflowTask.getTaskDefinition());
        assertNull(workflowTask.getRateLimited());
        assertNotNull(workflowTask.getDefaultExclusiveJoinTask());
        assertNotNull(workflowTask.isAsyncComplete());
        assertNull(workflowTask.getLoopCondition());
        assertNotNull(workflowTask.getLoopOver());
        assertNull(workflowTask.getRetryCount());
        assertNull(workflowTask.getEvaluatorType());
        assertNull(workflowTask.getExpression());
        assertNotNull(workflowTask.getOnStateChange());
        assertNull(workflowTask.getJoinStatus());
        assertNull(workflowTask.getCacheConfig());
        assertFalse(workflowTask.isPermissive());

        // Test setters and getters
        workflowTask.setName("TaskName");
        workflowTask.setTaskReferenceName("TaskRefName");
        workflowTask.setDescription("Task Description");
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("key1", "value1");
        workflowTask.setInputParameters(inputParams);
        workflowTask.setType(TaskType.DECISION.name());
        workflowTask.setDynamicTaskNameParam("dynamicTaskParam");
        workflowTask.setCaseValueParam("caseValueParam");
        workflowTask.setCaseExpression("caseExpression");
        workflowTask.setScriptExpression("scriptExpression");

        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();
        List<WorkflowTask> case1Tasks = new ArrayList<>();
        WorkflowTask case1Task = new WorkflowTask();
        case1Task.setName("Case1Task");
        case1Tasks.add(case1Task);
        decisionCases.put("case1", case1Tasks);
        workflowTask.setDecisionCases(decisionCases);

        workflowTask.setDynamicForkJoinTasksParam("dynamicForkJoin");
        workflowTask.setDynamicForkTasksParam("dynamicFork");
        workflowTask.setDynamicForkTasksInputParamName("dynamicForkInput");

        List<WorkflowTask> defaultCase = new ArrayList<>();
        WorkflowTask defaultTask = new WorkflowTask();
        defaultTask.setName("DefaultTask");
        defaultCase.add(defaultTask);
        workflowTask.setDefaultCase(defaultCase);

        List<List<WorkflowTask>> forkTasks = new ArrayList<>();
        List<WorkflowTask> fork1 = new ArrayList<>();
        WorkflowTask fork1Task = new WorkflowTask();
        fork1Task.setName("Fork1Task");
        fork1.add(fork1Task);
        forkTasks.add(fork1);
        workflowTask.setForkTasks(forkTasks);

        workflowTask.setStartDelay(10);

        SubWorkflowParams subWorkflowParam = new SubWorkflowParams();
        workflowTask.setSubWorkflowParam(subWorkflowParam);

        List<String> joinOn = Arrays.asList("task1", "task2");
        workflowTask.setJoinOn(joinOn);

        workflowTask.setSink("sink1");
        workflowTask.setOptional(true);

        TaskDef taskDef = new TaskDef();
        workflowTask.setTaskDefinition(taskDef);

        workflowTask.setRateLimited(true);

        List<String> defaultExclusiveJoin = Arrays.asList("join1", "join2");
        workflowTask.setDefaultExclusiveJoinTask(defaultExclusiveJoin);

        workflowTask.setAsyncComplete(true);

        workflowTask.setLoopCondition("loopCondition");

        List<WorkflowTask> loopTasks = new ArrayList<>();
        WorkflowTask loopTask = new WorkflowTask();
        loopTask.setName("LoopTask");
        loopTasks.add(loopTask);
        workflowTask.setLoopOver(loopTasks);

        workflowTask.setRetryCount(3);

        workflowTask.setEvaluatorType("evaluatorType");
        workflowTask.setExpression("expression");

        Map<String, List<StateChangeEvent>> stateChangeEvents = new HashMap<>();
        workflowTask.setOnStateChange(stateChangeEvents);

        workflowTask.setJoinStatus("joinStatus");

        WorkflowTask.CacheConfig cacheConfig = new WorkflowTask.CacheConfig();
        workflowTask.setCacheConfig(cacheConfig);

        workflowTask.setPermissive(true);

        // Assert all getters return expected values
        assertEquals("TaskName", workflowTask.getName());
        assertEquals("TaskRefName", workflowTask.getTaskReferenceName());
        assertEquals("Task Description", workflowTask.getDescription());
        assertEquals(inputParams, workflowTask.getInputParameters());
        assertEquals(TaskType.DECISION.name(), workflowTask.getType());
        assertEquals("dynamicTaskParam", workflowTask.getDynamicTaskNameParam());
        assertEquals("caseValueParam", workflowTask.getCaseValueParam());
        assertEquals("caseExpression", workflowTask.getCaseExpression());
        assertEquals("scriptExpression", workflowTask.getScriptExpression());
        assertEquals(decisionCases, workflowTask.getDecisionCases());
        assertEquals("dynamicForkJoin", workflowTask.getDynamicForkJoinTasksParam());
        assertEquals("dynamicFork", workflowTask.getDynamicForkTasksParam());
        assertEquals("dynamicForkInput", workflowTask.getDynamicForkTasksInputParamName());
        assertEquals(defaultCase, workflowTask.getDefaultCase());
        assertEquals(forkTasks, workflowTask.getForkTasks());
        assertEquals(10, workflowTask.getStartDelay());
        assertEquals(subWorkflowParam, workflowTask.getSubWorkflowParam());
        assertEquals(joinOn, workflowTask.getJoinOn());
        assertEquals("sink1", workflowTask.getSink());
        assertTrue(workflowTask.isOptional());
        assertEquals(taskDef, workflowTask.getTaskDefinition());
        assertTrue(workflowTask.getRateLimited());
        assertTrue(workflowTask.isRateLimited());
        assertEquals(defaultExclusiveJoin, workflowTask.getDefaultExclusiveJoinTask());
        assertTrue(workflowTask.isAsyncComplete());
        assertEquals("loopCondition", workflowTask.getLoopCondition());
        assertEquals(loopTasks, workflowTask.getLoopOver());
        assertEquals(Integer.valueOf(3), workflowTask.getRetryCount());
        assertEquals("evaluatorType", workflowTask.getEvaluatorType());
        assertEquals("expression", workflowTask.getExpression());
        assertEquals(stateChangeEvents, workflowTask.getOnStateChange());
        assertEquals("joinStatus", workflowTask.getJoinStatus());
        assertEquals(cacheConfig, workflowTask.getCacheConfig());
        assertTrue(workflowTask.isPermissive());
    }

    @Test
    void testSetWorkflowTaskType() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        assertEquals(TaskType.FORK_JOIN.name(), workflowTask.getType());
    }

    @Test
    void testIsRateLimited() {
        WorkflowTask workflowTask = new WorkflowTask();

        // When rateLimited is null
        assertFalse(workflowTask.isRateLimited());

        // When rateLimited is false
        workflowTask.setRateLimited(false);
        assertFalse(workflowTask.isRateLimited());

        // When rateLimited is true
        workflowTask.setRateLimited(true);
        assertTrue(workflowTask.isRateLimited());
    }

    @Test
    void testCollectTasks() {
        // Create a complex workflow task structure for testing
        WorkflowTask mainTask = new WorkflowTask();
        mainTask.setName("MainTask");
        mainTask.setTaskReferenceName("mainTask");
        mainTask.setType(TaskType.FORK_JOIN.name());

        // Create fork tasks
        List<List<WorkflowTask>> forkTasks = new ArrayList<>();

        // Fork branch 1
        List<WorkflowTask> fork1 = new ArrayList<>();
        WorkflowTask fork1Task1 = new WorkflowTask();
        fork1Task1.setName("Fork1Task1");
        fork1Task1.setTaskReferenceName("fork1Task1");
        fork1Task1.setType(TaskType.SIMPLE.name());

        WorkflowTask fork1Task2 = new WorkflowTask();
        fork1Task2.setName("Fork1Task2");
        fork1Task2.setTaskReferenceName("fork1Task2");
        fork1Task2.setType(TaskType.SIMPLE.name());

        fork1.add(fork1Task1);
        fork1.add(fork1Task2);

        // Fork branch 2 with a DECISION task
        List<WorkflowTask> fork2 = new ArrayList<>();
        WorkflowTask fork2Task1 = new WorkflowTask();
        fork2Task1.setName("Fork2Task1");
        fork2Task1.setTaskReferenceName("fork2Task1");
        fork2Task1.setType(TaskType.DECISION.name());

        // Add decision cases
        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();

        // Decision case 1
        List<WorkflowTask> case1Tasks = new ArrayList<>();
        WorkflowTask case1Task = new WorkflowTask();
        case1Task.setName("Case1Task");
        case1Task.setTaskReferenceName("case1Task");
        case1Tasks.add(case1Task);
        decisionCases.put("case1", case1Tasks);

        // Decision case 2
        List<WorkflowTask> case2Tasks = new ArrayList<>();
        WorkflowTask case2Task = new WorkflowTask();
        case2Task.setName("Case2Task");
        case2Task.setTaskReferenceName("case2Task");
        case2Tasks.add(case2Task);
        decisionCases.put("case2", case2Tasks);

        // Decision default case
        List<WorkflowTask> defaultCase = new ArrayList<>();
        WorkflowTask defaultTask = new WorkflowTask();
        defaultTask.setName("DefaultTask");
        defaultTask.setTaskReferenceName("defaultTask");
        defaultCase.add(defaultTask);

        fork2Task1.setDecisionCases(decisionCases);
        fork2Task1.setDefaultCase(defaultCase);

        fork2.add(fork2Task1);

        // Add DO_WHILE task
        List<WorkflowTask> fork3 = new ArrayList<>();
        WorkflowTask doWhileTask = new WorkflowTask();
        doWhileTask.setName("DoWhileTask");
        doWhileTask.setTaskReferenceName("doWhileTask");
        doWhileTask.setType(TaskType.DO_WHILE.name());

        // Set loop over tasks
        List<WorkflowTask> loopTasks = new ArrayList<>();
        WorkflowTask loopTask1 = new WorkflowTask();
        loopTask1.setName("LoopTask1");
        loopTask1.setTaskReferenceName("loopTask1");

        WorkflowTask loopTask2 = new WorkflowTask();
        loopTask2.setName("LoopTask2");
        loopTask2.setTaskReferenceName("loopTask2");

        loopTasks.add(loopTask1);
        loopTasks.add(loopTask2);
        doWhileTask.setLoopOver(loopTasks);

        fork3.add(doWhileTask);

        // Add all fork branches to main task
        forkTasks.add(fork1);
        forkTasks.add(fork2);
        forkTasks.add(fork3);
        mainTask.setForkTasks(forkTasks);

        // Collect all tasks
        List<WorkflowTask> allTasks = mainTask.collectTasks();

        // Verify result - should include all tasks defined in the structure
        assertEquals(10, allTasks.size()); // MainTask + 2 in fork1 + 1 in fork2 + 3 cases + 1 doWhile + 2 loop tasks

        // Verify that all tasks are included
        List<String> taskNames = allTasks.stream()
                .map(WorkflowTask::getName)
                .toList();

        assertTrue(taskNames.contains("MainTask"));
        assertTrue(taskNames.contains("Fork1Task1"));
        assertTrue(taskNames.contains("Fork1Task2"));
        assertTrue(taskNames.contains("Fork2Task1"));
        assertTrue(taskNames.contains("Case1Task"));
        assertTrue(taskNames.contains("Case2Task"));
        assertTrue(taskNames.contains("DefaultTask"));
        assertTrue(taskNames.contains("DoWhileTask"));
        assertTrue(taskNames.contains("LoopTask1"));
        assertTrue(taskNames.contains("LoopTask2"));
    }

    @Test
    void testHasMethod() {
        // Create a workflow task structure
        WorkflowTask mainTask = new WorkflowTask();
        mainTask.setName("MainTask");
        mainTask.setTaskReferenceName("mainTask");
        mainTask.setType(TaskType.DECISION.name());

        // Add decision cases
        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();

        // Decision case 1
        List<WorkflowTask> case1Tasks = new ArrayList<>();
        WorkflowTask case1Task = new WorkflowTask();
        case1Task.setName("Case1Task");
        case1Task.setTaskReferenceName("case1Task");
        case1Tasks.add(case1Task);
        decisionCases.put("case1", case1Tasks);

        // Decision default case
        List<WorkflowTask> defaultCase = new ArrayList<>();
        WorkflowTask defaultTask = new WorkflowTask();
        defaultTask.setName("DefaultTask");
        defaultTask.setTaskReferenceName("defaultTask");
        defaultCase.add(defaultTask);

        mainTask.setDecisionCases(decisionCases);
        mainTask.setDefaultCase(defaultCase);

        // Test the has method
        assertTrue(mainTask.has("mainTask")); // Main task reference
        assertTrue(mainTask.has("case1Task")); // Task in decision case
        assertTrue(mainTask.has("defaultTask")); // Task in default case
        assertFalse(mainTask.has("nonExistentTask")); // Non-existent task
    }

    @Test
    void testGetMethod() {
        // Create a workflow task structure
        WorkflowTask mainTask = new WorkflowTask();
        mainTask.setName("MainTask");
        mainTask.setTaskReferenceName("mainTask");
        mainTask.setType(TaskType.DECISION.name());

        // Add decision cases
        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();

        // Decision case 1
        List<WorkflowTask> case1Tasks = new ArrayList<>();
        WorkflowTask case1Task = new WorkflowTask();
        case1Task.setName("Case1Task");
        case1Task.setTaskReferenceName("case1Task");
        case1Tasks.add(case1Task);
        decisionCases.put("case1", case1Tasks);

        // Decision default case
        List<WorkflowTask> defaultCase = new ArrayList<>();
        WorkflowTask defaultTask = new WorkflowTask();
        defaultTask.setName("DefaultTask");
        defaultTask.setTaskReferenceName("defaultTask");
        defaultCase.add(defaultTask);

        mainTask.setDecisionCases(decisionCases);
        mainTask.setDefaultCase(defaultCase);

        // Test the get method
        assertEquals(mainTask, mainTask.get("mainTask")); // Main task
        assertEquals(case1Task, mainTask.get("case1Task")); // Task in decision case
        assertEquals(defaultTask, mainTask.get("defaultTask")); // Task in default case
        assertNull(mainTask.get("nonExistentTask")); // Non-existent task
    }

    @Test
    void testNextMethod() {
        // Create a DECISION workflow task structure
        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setName("DecisionTask");
        decisionTask.setTaskReferenceName("decisionTask");
        decisionTask.setType(TaskType.DECISION.name());

        // Add decision cases
        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();

        // Case 1: task1 -> task2
        List<WorkflowTask> case1Tasks = new ArrayList<>();

        WorkflowTask case1Task1 = new WorkflowTask();
        case1Task1.setName("Case1Task1");
        case1Task1.setTaskReferenceName("case1Task1");

        WorkflowTask case1Task2 = new WorkflowTask();
        case1Task2.setName("Case1Task2");
        case1Task2.setTaskReferenceName("case1Task2");

        case1Tasks.add(case1Task1);
        case1Tasks.add(case1Task2);
        decisionCases.put("case1", case1Tasks);

        // Default case
        List<WorkflowTask> defaultCase = new ArrayList<>();
        WorkflowTask defaultTask = new WorkflowTask();
        defaultTask.setName("DefaultTask");
        defaultTask.setTaskReferenceName("defaultTask");
        defaultCase.add(defaultTask);

        decisionTask.setDecisionCases(decisionCases);
        decisionTask.setDefaultCase(defaultCase);

        // Test getting next task
        assertEquals(case1Task2, decisionTask.next("case1Task1", null));
        assertNull(decisionTask.next("case1Task2", null)); // Last task in the branch
        assertNull(decisionTask.next("defaultTask", null)); // Last task in default branch
        assertNull(decisionTask.next("nonExistentTask", null)); // Non-existent task

        // Create a FORK_JOIN workflow task structure to test parent reference
        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setName("ForkTask");
        forkTask.setTaskReferenceName("forkTask");
        forkTask.setType(TaskType.FORK_JOIN.name());

        // Fork branch 1
        List<WorkflowTask> fork1 = new ArrayList<>();
        WorkflowTask fork1Task = new WorkflowTask();
        fork1Task.setName("Fork1Task");
        fork1Task.setTaskReferenceName("fork1Task");
        fork1.add(fork1Task);

        // Fork branch 2
        List<WorkflowTask> fork2 = new ArrayList<>();
        WorkflowTask fork2Task = new WorkflowTask();
        fork2Task.setName("Fork2Task");
        fork2Task.setTaskReferenceName("fork2Task");
        fork2.add(fork2Task);

        List<List<WorkflowTask>> forkTasks = new ArrayList<>();
        forkTasks.add(fork1);
        forkTasks.add(fork2);
        forkTask.setForkTasks(forkTasks);

        // Test parent reference in fork join - needs a join task in a parent
        WorkflowTask parentTask = new WorkflowTask();
        parentTask.setName("ParentTask");
        parentTask.setTaskReferenceName("parentTask");

        // The join task that should be returned
        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setName("JoinTask");
        joinTask.setTaskReferenceName("joinTask");

        // Add tasks to parent
        List<WorkflowTask> parentTasks = new ArrayList<>();
        parentTasks.add(forkTask);
        parentTasks.add(joinTask);

        // Create DO_WHILE task
        WorkflowTask doWhileTask = new WorkflowTask();
        doWhileTask.setName("DoWhileTask");
        doWhileTask.setTaskReferenceName("doWhileTask");
        doWhileTask.setType(TaskType.DO_WHILE.name());

        // Set loop over tasks
        List<WorkflowTask> loopTasks = new ArrayList<>();
        WorkflowTask loopTask1 = new WorkflowTask();
        loopTask1.setName("LoopTask1");
        loopTask1.setTaskReferenceName("loopTask1");

        WorkflowTask loopTask2 = new WorkflowTask();
        loopTask2.setName("LoopTask2");
        loopTask2.setTaskReferenceName("loopTask2");

        loopTasks.add(loopTask1);
        loopTasks.add(loopTask2);
        doWhileTask.setLoopOver(loopTasks);

        // Test DO_WHILE next
        assertEquals(loopTask2, doWhileTask.next("loopTask1", null));
        assertEquals(doWhileTask, doWhileTask.next("loopTask2", null)); // Return the DO_WHILE task itself when at last task
    }

    @Test
    void testToString() {
        WorkflowTask task = new WorkflowTask();
        task.setName("TaskName");
        task.setTaskReferenceName("TaskRef");

        assertEquals("TaskName/TaskRef", task.toString());
    }

    @Test
    void testEqualsAndHashCode() {
        WorkflowTask task1 = new WorkflowTask();
        task1.setName("TaskName");
        task1.setTaskReferenceName("TaskRef");

        WorkflowTask task2 = new WorkflowTask();
        task2.setName("TaskName");
        task2.setTaskReferenceName("TaskRef");

        WorkflowTask task3 = new WorkflowTask();
        task3.setName("DifferentName");
        task3.setTaskReferenceName("TaskRef");

        WorkflowTask task4 = new WorkflowTask();
        task4.setName("TaskName");
        task4.setTaskReferenceName("DifferentRef");

        // Test equals
        assertEquals(task1, task1); // Same instance
        assertEquals(task1, task2); // Equivalent instance
        assertNotEquals(task1, task3); // Different name
        assertNotEquals(task1, task4); // Different reference
        assertNotEquals(task1, null); // Null
        assertNotEquals(task1, new Object()); // Different class

        // Test hashCode
        assertEquals(task1.hashCode(), task2.hashCode()); // Equivalent instances should have same hash
        assertNotEquals(task1.hashCode(), task3.hashCode()); // Different name should have different hash
        assertNotEquals(task1.hashCode(), task4.hashCode()); // Different reference should have different hash
    }

    @Test
    void testChildrenMethod() {
        // Test DECISION task
        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());

        Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();
        List<WorkflowTask> case1Tasks = new LinkedList<>();
        case1Tasks.add(new WorkflowTask());
        decisionCases.put("case1", case1Tasks);

        List<WorkflowTask> defaultCase = new LinkedList<>();
        defaultCase.add(new WorkflowTask());

        decisionTask.setDecisionCases(decisionCases);
        decisionTask.setDefaultCase(defaultCase);

        // Call children method through collectTasks
        List<WorkflowTask> allTasks = decisionTask.collectTasks();
        assertEquals(3, allTasks.size()); // Decision task + case1 task + default task

        // Test FORK_JOIN task
        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setType(TaskType.FORK_JOIN.name());

        List<List<WorkflowTask>> forkTasks = new LinkedList<>();
        List<WorkflowTask> fork1 = new LinkedList<>();
        fork1.add(new WorkflowTask());
        List<WorkflowTask> fork2 = new LinkedList<>();
        fork2.add(new WorkflowTask());
        forkTasks.add(fork1);
        forkTasks.add(fork2);

        forkTask.setForkTasks(forkTasks);

        allTasks = forkTask.collectTasks();
        assertEquals(3, allTasks.size()); // Fork task + fork1 task + fork2 task

        // Test DO_WHILE task
        WorkflowTask doWhileTask = new WorkflowTask();
        doWhileTask.setType(TaskType.DO_WHILE.name());

        List<WorkflowTask> loopTasks = new LinkedList<>();
        loopTasks.add(new WorkflowTask());
        loopTasks.add(new WorkflowTask());
        doWhileTask.setLoopOver(loopTasks);

        allTasks = doWhileTask.collectTasks();
        assertEquals(3, allTasks.size()); // DO_WHILE task + 2 loop tasks

        // Test SIMPLE task (no children)
        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setType(TaskType.SIMPLE.name());

        allTasks = simpleTask.collectTasks();
        assertEquals(1, allTasks.size()); // Just the simple task itself
    }
}