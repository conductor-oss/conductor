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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestDynamicForkJoinTaskListPojoMethods {

    @Test
    public void testDefaultConstructor() {
        DynamicForkJoinTaskList taskList = new DynamicForkJoinTaskList();
        assertNotNull(taskList.getDynamicTasks());
        assertEquals(0, taskList.getDynamicTasks().size());
    }

    @Test
    public void testAddTaskWithParameters() {
        DynamicForkJoinTaskList taskList = new DynamicForkJoinTaskList();

        String taskName = "testTask";
        String workflowName = "testWorkflow";
        String referenceName = "testReference";
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");

        taskList.add(taskName, workflowName, referenceName, input);

        assertEquals(1, taskList.getDynamicTasks().size());

        DynamicForkJoinTask addedTask = taskList.getDynamicTasks().get(0);
        assertEquals(taskName, addedTask.getTaskName());
        assertEquals(workflowName, addedTask.getWorkflowName());
        assertEquals(referenceName, addedTask.getReferenceName());
        assertEquals(input, addedTask.getInput());
    }

    @Test
    public void testAddTaskWithTaskObject() {
        DynamicForkJoinTaskList taskList = new DynamicForkJoinTaskList();

        String taskName = "testTask";
        String workflowName = "testWorkflow";
        String referenceName = "testReference";
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");

        DynamicForkJoinTask task = new DynamicForkJoinTask(taskName, workflowName, referenceName, input);
        taskList.add(task);

        assertEquals(1, taskList.getDynamicTasks().size());
        assertEquals(task, taskList.getDynamicTasks().get(0));
    }

    @Test
    public void testGetDynamicTasks() {
        DynamicForkJoinTaskList taskList = new DynamicForkJoinTaskList();

        // Add multiple tasks
        Map<String, Object> input1 = new HashMap<>();
        input1.put("key1", "value1");
        DynamicForkJoinTask task1 = new DynamicForkJoinTask("task1", "workflow1", "ref1", input1);

        Map<String, Object> input2 = new HashMap<>();
        input2.put("key2", "value2");
        DynamicForkJoinTask task2 = new DynamicForkJoinTask("task2", "workflow2", "ref2", input2);

        taskList.add(task1);
        taskList.add(task2);

        List<DynamicForkJoinTask> result = taskList.getDynamicTasks();

        assertEquals(2, result.size());
        assertEquals(task1, result.get(0));
        assertEquals(task2, result.get(1));
    }

    @Test
    public void testSetDynamicTasks() {
        DynamicForkJoinTaskList taskList = new DynamicForkJoinTaskList();

        // Create a new list of tasks
        List<DynamicForkJoinTask> tasks = new ArrayList<>();

        Map<String, Object> input1 = new HashMap<>();
        input1.put("key1", "value1");
        DynamicForkJoinTask task1 = new DynamicForkJoinTask("task1", "workflow1", "ref1", input1);

        Map<String, Object> input2 = new HashMap<>();
        input2.put("key2", "value2");
        DynamicForkJoinTask task2 = new DynamicForkJoinTask("task2", "workflow2", "ref2", input2);

        tasks.add(task1);
        tasks.add(task2);

        // Set the tasks list
        taskList.setDynamicTasks(tasks);

        // Verify that the tasks list was set correctly
        assertEquals(tasks, taskList.getDynamicTasks());
        assertEquals(2, taskList.getDynamicTasks().size());
    }

    @Test
    public void testMultipleAddOperations() {
        DynamicForkJoinTaskList taskList = new DynamicForkJoinTaskList();

        // Add first task using parameters
        taskList.add("task1", "workflow1", "ref1", new HashMap<>());

        // Add second task using task object
        DynamicForkJoinTask task2 = new DynamicForkJoinTask("task2", "workflow2", "ref2", new HashMap<>());
        taskList.add(task2);

        assertEquals(2, taskList.getDynamicTasks().size());
        assertEquals("task1", taskList.getDynamicTasks().get(0).getTaskName());
        assertEquals("task2", taskList.getDynamicTasks().get(1).getTaskName());
    }
}