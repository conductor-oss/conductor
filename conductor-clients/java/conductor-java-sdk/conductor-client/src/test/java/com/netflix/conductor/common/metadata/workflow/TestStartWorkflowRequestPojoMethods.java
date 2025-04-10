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

import static org.junit.jupiter.api.Assertions.*;

public class TestStartWorkflowRequestPojoMethods {

    @Test
    public void testDefaultConstructor() {
        StartWorkflowRequest request = new StartWorkflowRequest();

        assertNull(request.getName());
        assertNull(request.getVersion());
        assertNull(request.getCorrelationId());
        assertNotNull(request.getInput());
        assertTrue(request.getInput().isEmpty());
        assertNotNull(request.getTaskToDomain());
        assertTrue(request.getTaskToDomain().isEmpty());
        assertNull(request.getWorkflowDef());
        assertNull(request.getExternalInputPayloadStoragePath());
        assertEquals(Integer.valueOf(0), request.getPriority());
        assertNull(request.getCreatedBy());
        assertNull(request.getIdempotencyKey());
        assertNull(request.getIdempotencyStrategy());
    }

    @Test
    public void testNameGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getName());

        request.setName("workflowName");
        assertEquals("workflowName", request.getName());
    }

    @Test
    public void testNameWithBuilder() {
        StartWorkflowRequest request = new StartWorkflowRequest().withName("workflowName");
        assertEquals("workflowName", request.getName());
    }

    @Test
    public void testVersionGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getVersion());

        request.setVersion(1);
        assertEquals(Integer.valueOf(1), request.getVersion());
    }

    @Test
    public void testVersionWithBuilder() {
        StartWorkflowRequest request = new StartWorkflowRequest().withVersion(2);
        assertEquals(Integer.valueOf(2), request.getVersion());
    }

    @Test
    public void testCorrelationIdGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getCorrelationId());

        request.setCorrelationId("correlationId123");
        assertEquals("correlationId123", request.getCorrelationId());
    }

    @Test
    public void testCorrelationIdWithBuilder() {
        StartWorkflowRequest request = new StartWorkflowRequest().withCorrelationId("corr456");
        assertEquals("corr456", request.getCorrelationId());
    }

    @Test
    public void testInputGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNotNull(request.getInput());
        assertTrue(request.getInput().isEmpty());

        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");
        input.put("key2", 100);

        request.setInput(input);
        assertEquals(input, request.getInput());
    }

    @Test
    public void testInputWithBuilder() {
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");
        input.put("key2", 100);

        StartWorkflowRequest request = new StartWorkflowRequest().withInput(input);
        assertEquals(input, request.getInput());
    }

    @Test
    public void testTaskToDomainGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNotNull(request.getTaskToDomain());
        assertTrue(request.getTaskToDomain().isEmpty());

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("task1", "domain1");
        taskToDomain.put("task2", "domain2");

        request.setTaskToDomain(taskToDomain);
        assertEquals(taskToDomain, request.getTaskToDomain());
    }

    @Test
    public void testTaskToDomainWithBuilder() {
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("task1", "domain1");
        taskToDomain.put("task2", "domain2");

        StartWorkflowRequest request = new StartWorkflowRequest().withTaskToDomain(taskToDomain);
        assertEquals(taskToDomain, request.getTaskToDomain());
    }

    @Test
    public void testWorkflowDefGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getWorkflowDef());

        WorkflowDef workflowDef = new WorkflowDef();
        request.setWorkflowDef(workflowDef);
        assertEquals(workflowDef, request.getWorkflowDef());
    }

    @Test
    public void testWorkflowDefWithBuilder() {
        WorkflowDef workflowDef = new WorkflowDef();
        StartWorkflowRequest request = new StartWorkflowRequest().withWorkflowDef(workflowDef);
        assertEquals(workflowDef, request.getWorkflowDef());
    }

    @Test
    public void testExternalInputPayloadStoragePathGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getExternalInputPayloadStoragePath());

        request.setExternalInputPayloadStoragePath("/path/to/storage");
        assertEquals("/path/to/storage", request.getExternalInputPayloadStoragePath());
    }

    @Test
    public void testExternalInputPayloadStoragePathWithBuilder() {
        StartWorkflowRequest request = new StartWorkflowRequest()
                .withExternalInputPayloadStoragePath("/path/to/external/storage");
        assertEquals("/path/to/external/storage", request.getExternalInputPayloadStoragePath());
    }

    @Test
    public void testPriorityGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertEquals(Integer.valueOf(0), request.getPriority());

        request.setPriority(5);
        assertEquals(Integer.valueOf(5), request.getPriority());
    }

    @Test
    public void testPriorityWithBuilder() {
        StartWorkflowRequest request = new StartWorkflowRequest().withPriority(10);
        assertEquals(Integer.valueOf(10), request.getPriority());
    }

    @Test
    public void testCreatedByGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getCreatedBy());

        request.setCreatedBy("user123");
        assertEquals("user123", request.getCreatedBy());
    }

    @Test
    public void testCreatedByWithBuilder() {
        StartWorkflowRequest request = new StartWorkflowRequest().withCreatedBy("testUser");
        assertEquals("testUser", request.getCreatedBy());
    }

    @Test
    public void testIdempotencyKeyGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getIdempotencyKey());

        request.setIdempotencyKey("key123");
        assertEquals("key123", request.getIdempotencyKey());
    }

    @Test
    public void testIdempotencyStrategyGetterAndSetter() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        assertNull(request.getIdempotencyStrategy());

        IdempotencyStrategy strategy = IdempotencyStrategy.FAIL;
        request.setIdempotencyStrategy(strategy);
        assertEquals(strategy, request.getIdempotencyStrategy());
    }

    @Test
    public void testChainedBuilderMethods() {
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("task", "domain");

        WorkflowDef workflowDef = new WorkflowDef();

        StartWorkflowRequest request = new StartWorkflowRequest()
                .withName("workflow")
                .withVersion(1)
                .withCorrelationId("corr123")
                .withInput(input)
                .withTaskToDomain(taskToDomain)
                .withWorkflowDef(workflowDef)
                .withExternalInputPayloadStoragePath("/path")
                .withPriority(5)
                .withCreatedBy("user");

        assertEquals("workflow", request.getName());
        assertEquals(Integer.valueOf(1), request.getVersion());
        assertEquals("corr123", request.getCorrelationId());
        assertEquals(input, request.getInput());
        assertEquals(taskToDomain, request.getTaskToDomain());
        assertEquals(workflowDef, request.getWorkflowDef());
        assertEquals("/path", request.getExternalInputPayloadStoragePath());
        assertEquals(Integer.valueOf(5), request.getPriority());
        assertEquals("user", request.getCreatedBy());
    }
}