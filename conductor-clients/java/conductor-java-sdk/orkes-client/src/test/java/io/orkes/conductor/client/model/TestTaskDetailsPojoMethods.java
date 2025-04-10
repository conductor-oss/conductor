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
package io.orkes.conductor.client.model;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskDetailsPojoMethods {

    @Test
    void testEmptyConstructor() {
        TaskDetails taskDetails = new TaskDetails();
        assertNull(taskDetails.getOutput());
        assertNull(taskDetails.getTaskId());
        assertNull(taskDetails.getTaskRefName());
        assertNull(taskDetails.getWorkflowId());
    }

    @Test
    void testSetAndGetOutput() {
        TaskDetails taskDetails = new TaskDetails();
        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");

        taskDetails.setOutput(output);
        assertEquals(output, taskDetails.getOutput());
    }

    @Test
    void testSetAndGetTaskId() {
        TaskDetails taskDetails = new TaskDetails();
        String taskId = "task123";

        taskDetails.setTaskId(taskId);
        assertEquals(taskId, taskDetails.getTaskId());
    }

    @Test
    void testSetAndGetTaskRefName() {
        TaskDetails taskDetails = new TaskDetails();
        String taskRefName = "taskRef123";

        taskDetails.setTaskRefName(taskRefName);
        assertEquals(taskRefName, taskDetails.getTaskRefName());
    }

    @Test
    void testSetAndGetWorkflowId() {
        TaskDetails taskDetails = new TaskDetails();
        String workflowId = "workflow123";

        taskDetails.setWorkflowId(workflowId);
        assertEquals(workflowId, taskDetails.getWorkflowId());
    }

    @Test
    void testOutputBuilderMethod() {
        TaskDetails taskDetails = new TaskDetails();
        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");

        TaskDetails result = taskDetails.output(output);

        assertSame(taskDetails, result);
        assertEquals(output, taskDetails.getOutput());
    }

    @Test
    void testTaskIdBuilderMethod() {
        TaskDetails taskDetails = new TaskDetails();
        String taskId = "task123";

        TaskDetails result = taskDetails.taskId(taskId);

        assertSame(taskDetails, result);
        assertEquals(taskId, taskDetails.getTaskId());
    }

    @Test
    void testTaskRefNameBuilderMethod() {
        TaskDetails taskDetails = new TaskDetails();
        String taskRefName = "taskRef123";

        TaskDetails result = taskDetails.taskRefName(taskRefName);

        assertSame(taskDetails, result);
        assertEquals(taskRefName, taskDetails.getTaskRefName());
    }

    @Test
    void testWorkflowIdBuilderMethod() {
        TaskDetails taskDetails = new TaskDetails();
        String workflowId = "workflow123";

        TaskDetails result = taskDetails.workflowId(workflowId);

        assertSame(taskDetails, result);
        assertEquals(workflowId, taskDetails.getWorkflowId());
    }

    @Test
    void testPutOutputItem() {
        TaskDetails taskDetails = new TaskDetails();

        TaskDetails result = taskDetails.putOutputItem("key1", "value1");

        assertSame(taskDetails, result);
        assertNotNull(taskDetails.getOutput());
        assertEquals("value1", taskDetails.getOutput().get("key1"));
    }

    @Test
    void testPutOutputItemWithExistingOutput() {
        TaskDetails taskDetails = new TaskDetails();
        Map<String, Object> output = new HashMap<>();
        output.put("existing", "value");
        taskDetails.setOutput(output);

        TaskDetails result = taskDetails.putOutputItem("key1", "value1");

        assertSame(taskDetails, result);
        assertEquals("value", taskDetails.getOutput().get("existing"));
        assertEquals("value1", taskDetails.getOutput().get("key1"));
    }

    @Test
    void testEqualsAndHashCode() {
        TaskDetails taskDetails1 = new TaskDetails()
                .taskId("task1")
                .taskRefName("ref1")
                .workflowId("workflow1");
        taskDetails1.putOutputItem("key1", "value1");

        TaskDetails taskDetails2 = new TaskDetails()
                .taskId("task1")
                .taskRefName("ref1")
                .workflowId("workflow1");
        taskDetails2.putOutputItem("key1", "value1");

        TaskDetails taskDetails3 = new TaskDetails()
                .taskId("task2")
                .taskRefName("ref1")
                .workflowId("workflow1");

        assertEquals(taskDetails1, taskDetails2);
        assertEquals(taskDetails1.hashCode(), taskDetails2.hashCode());

        assertNotEquals(taskDetails1, taskDetails3);
        assertNotEquals(taskDetails1.hashCode(), taskDetails3.hashCode());
    }

    @Test
    void testToString() {
        TaskDetails taskDetails = new TaskDetails()
                .taskId("task1")
                .taskRefName("ref1")
                .workflowId("workflow1");
        taskDetails.putOutputItem("key1", "value1");

        String toStringResult = taskDetails.toString();

        assertTrue(toStringResult.contains("task1"));
        assertTrue(toStringResult.contains("ref1"));
        assertTrue(toStringResult.contains("workflow1"));
        assertTrue(toStringResult.contains("key1"));
        assertTrue(toStringResult.contains("value1"));
    }

    @Test
    void testChainedBuilderMethods() {
        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");

        TaskDetails taskDetails = new TaskDetails()
                .output(output)
                .taskId("task123")
                .taskRefName("taskRef123")
                .workflowId("workflow123");

        assertEquals(output, taskDetails.getOutput());
        assertEquals("task123", taskDetails.getTaskId());
        assertEquals("taskRef123", taskDetails.getTaskRefName());
        assertEquals("workflow123", taskDetails.getWorkflowId());
    }
}