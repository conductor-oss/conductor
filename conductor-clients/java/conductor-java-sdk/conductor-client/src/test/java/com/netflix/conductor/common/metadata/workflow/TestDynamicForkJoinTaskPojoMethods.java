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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestDynamicForkJoinTaskPojoMethods {

    @Test
    public void testNoArgsConstructor() {
        DynamicForkJoinTask task = new DynamicForkJoinTask();
        assertNotNull(task);
        assertNotNull(task.getInput());
        assertEquals(TaskType.SIMPLE.name(), task.getType());
    }

    @Test
    public void testConstructorWithFourParams() {
        String taskName = "testTask";
        String workflowName = "testWorkflow";
        String referenceName = "testReference";
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");

        DynamicForkJoinTask task = new DynamicForkJoinTask(taskName, workflowName, referenceName, input);

        assertEquals(taskName, task.getTaskName());
        assertEquals(workflowName, task.getWorkflowName());
        assertEquals(referenceName, task.getReferenceName());
        assertEquals(input, task.getInput());
        assertEquals(TaskType.SIMPLE.name(), task.getType());
    }

    @Test
    public void testConstructorWithFiveParams() {
        String taskName = "testTask";
        String workflowName = "testWorkflow";
        String referenceName = "testReference";
        String type = TaskType.DECISION.name();
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");

        DynamicForkJoinTask task = new DynamicForkJoinTask(taskName, workflowName, referenceName, type, input);

        assertEquals(taskName, task.getTaskName());
        assertEquals(workflowName, task.getWorkflowName());
        assertEquals(referenceName, task.getReferenceName());
        assertEquals(input, task.getInput());
        assertEquals(type, task.getType());
    }

    @Test
    public void testTaskNameGetterSetter() {
        DynamicForkJoinTask task = new DynamicForkJoinTask();
        String taskName = "updatedTaskName";
        task.setTaskName(taskName);
        assertEquals(taskName, task.getTaskName());
    }

    @Test
    public void testWorkflowNameGetterSetter() {
        DynamicForkJoinTask task = new DynamicForkJoinTask();
        String workflowName = "updatedWorkflowName";
        task.setWorkflowName(workflowName);
        assertEquals(workflowName, task.getWorkflowName());
    }

    @Test
    public void testReferenceNameGetterSetter() {
        DynamicForkJoinTask task = new DynamicForkJoinTask();
        String referenceName = "updatedReferenceName";
        task.setReferenceName(referenceName);
        assertEquals(referenceName, task.getReferenceName());
    }

    @Test
    public void testInputGetterSetter() {
        DynamicForkJoinTask task = new DynamicForkJoinTask();
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");
        input.put("key2", 123);

        task.setInput(input);
        assertEquals(input, task.getInput());
    }

    @Test
    public void testTypeGetterSetter() {
        DynamicForkJoinTask task = new DynamicForkJoinTask();
        String type = TaskType.FORK_JOIN.name();
        task.setType(type);
        assertEquals(type, task.getType());
    }
}