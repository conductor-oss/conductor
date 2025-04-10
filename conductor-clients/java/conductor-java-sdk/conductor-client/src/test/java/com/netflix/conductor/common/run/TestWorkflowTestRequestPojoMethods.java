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
package com.netflix.conductor.common.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestWorkflowTestRequestPojoMethods {

    @Test
    void testWorkflowTestRequestDefaultConstructor() {
        WorkflowTestRequest request = new WorkflowTestRequest();
        assertNotNull(request.getTaskRefToMockOutput());
        assertNotNull(request.getSubWorkflowTestRequest());
    }

    @Test
    void testTaskRefToMockOutputGetterSetter() {
        WorkflowTestRequest request = new WorkflowTestRequest();
        Map<String, List<WorkflowTestRequest.TaskMock>> mockOutputMap = new HashMap<>();

        List<WorkflowTestRequest.TaskMock> mockOutputList = new ArrayList<>();
        mockOutputList.add(new WorkflowTestRequest.TaskMock());

        mockOutputMap.put("task1", mockOutputList);

        request.setTaskRefToMockOutput(mockOutputMap);
        assertEquals(mockOutputMap, request.getTaskRefToMockOutput());
    }

    @Test
    void testSubWorkflowTestRequestGetterSetter() {
        WorkflowTestRequest request = new WorkflowTestRequest();
        Map<String, WorkflowTestRequest> subWorkflowMap = new HashMap<>();

        WorkflowTestRequest subWorkflow = new WorkflowTestRequest();
        subWorkflowMap.put("subWorkflow1", subWorkflow);

        request.setSubWorkflowTestRequest(subWorkflowMap);
        assertEquals(subWorkflowMap, request.getSubWorkflowTestRequest());
    }

    @Test
    void testTaskMockDefaultConstructor() {
        WorkflowTestRequest.TaskMock taskMock = new WorkflowTestRequest.TaskMock();
        assertEquals(TaskResult.Status.COMPLETED, taskMock.getStatus());
    }

    @Test
    void testTaskMockParameterizedConstructor() {
        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");

        WorkflowTestRequest.TaskMock taskMock = new WorkflowTestRequest.TaskMock(TaskResult.Status.FAILED, output);

        assertEquals(TaskResult.Status.FAILED, taskMock.getStatus());
        assertEquals(output, taskMock.getOutput());
    }

    @Test
    void testTaskMockStatusGetterSetter() {
        WorkflowTestRequest.TaskMock taskMock = new WorkflowTestRequest.TaskMock();
        taskMock.setStatus(TaskResult.Status.IN_PROGRESS);
        assertEquals(TaskResult.Status.IN_PROGRESS, taskMock.getStatus());
    }

    @Test
    void testTaskMockOutputGetterSetter() {
        WorkflowTestRequest.TaskMock taskMock = new WorkflowTestRequest.TaskMock();
        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");

        taskMock.setOutput(output);
        assertEquals(output, taskMock.getOutput());
    }

    @Test
    void testTaskMockExecutionTimeGetterSetter() {
        WorkflowTestRequest.TaskMock taskMock = new WorkflowTestRequest.TaskMock();
        long executionTime = 1000L;

        taskMock.setExecutionTime(executionTime);
        assertEquals(executionTime, taskMock.getExecutionTime());
    }

    @Test
    void testTaskMockQueueWaitTimeGetterSetter() {
        WorkflowTestRequest.TaskMock taskMock = new WorkflowTestRequest.TaskMock();
        long queueWaitTime = 5000L;

        taskMock.setQueueWaitTime(queueWaitTime);
        assertEquals(queueWaitTime, taskMock.getQueueWaitTime());
    }

    @Test
    void testInheritanceFromStartWorkflowRequest() {
        WorkflowTestRequest request = new WorkflowTestRequest();
        assertNotNull(request);
        assertEquals(StartWorkflowRequest.class, request.getClass().getSuperclass());
    }
}