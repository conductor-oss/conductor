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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestRerunWorkflowRequestPojoMethods {

    @Test
    public void testConstructor() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        assertNull(request.getReRunFromWorkflowId());
        assertNull(request.getWorkflowInput());
        assertNull(request.getReRunFromTaskId());
        assertNull(request.getTaskInput());
        assertNull(request.getCorrelationId());
    }

    @Test
    public void testSetAndGetReRunFromWorkflowId() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        String workflowId = "test-workflow-id";
        request.setReRunFromWorkflowId(workflowId);
        assertEquals(workflowId, request.getReRunFromWorkflowId());
    }

    @Test
    public void testSetAndGetWorkflowInput() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("key1", "value1");
        workflowInput.put("key2", 123);

        request.setWorkflowInput(workflowInput);
        assertEquals(workflowInput, request.getWorkflowInput());
        assertEquals("value1", request.getWorkflowInput().get("key1"));
        assertEquals(123, request.getWorkflowInput().get("key2"));
    }

    @Test
    public void testSetAndGetReRunFromTaskId() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        String taskId = "test-task-id";
        request.setReRunFromTaskId(taskId);
        assertEquals(taskId, request.getReRunFromTaskId());
    }

    @Test
    public void testSetAndGetTaskInput() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("taskKey1", "taskValue1");
        taskInput.put("taskKey2", 456);

        request.setTaskInput(taskInput);
        assertEquals(taskInput, request.getTaskInput());
        assertEquals("taskValue1", request.getTaskInput().get("taskKey1"));
        assertEquals(456, request.getTaskInput().get("taskKey2"));
    }

    @Test
    public void testSetAndGetCorrelationId() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        String correlationId = "test-correlation-id";
        request.setCorrelationId(correlationId);
        assertEquals(correlationId, request.getCorrelationId());
    }

    @Test
    public void testNullValues() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();

        request.setReRunFromWorkflowId("workflow-id");
        request.setReRunFromWorkflowId(null);
        assertNull(request.getReRunFromWorkflowId());

        request.setWorkflowInput(new HashMap<>());
        request.setWorkflowInput(null);
        assertNull(request.getWorkflowInput());

        request.setReRunFromTaskId("task-id");
        request.setReRunFromTaskId(null);
        assertNull(request.getReRunFromTaskId());

        request.setTaskInput(new HashMap<>());
        request.setTaskInput(null);
        assertNull(request.getTaskInput());

        request.setCorrelationId("correlation-id");
        request.setCorrelationId(null);
        assertNull(request.getCorrelationId());
    }
}