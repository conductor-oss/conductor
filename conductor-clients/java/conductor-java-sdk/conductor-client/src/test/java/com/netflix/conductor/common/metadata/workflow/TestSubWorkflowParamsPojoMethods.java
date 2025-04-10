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
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSubWorkflowParamsPojoMethods {

    @Test
    void testIdempotencyKeyGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        String idempotencyKey = "test-idempotency-key";

        params.setIdempotencyKey(idempotencyKey);
        assertEquals(idempotencyKey, params.getIdempotencyKey());
    }

    @Test
    void testIdempotencyStrategyGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        IdempotencyStrategy strategy = IdempotencyStrategy.FAIL;

        params.setIdempotencyStrategy(strategy);
        assertEquals(strategy, params.getIdempotencyStrategy());
    }

    @Test
    void testNameGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        String name = "test-workflow";

        params.setName(name);
        assertEquals(name, params.getName());
    }

    @Test
    void testVersionGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        Integer version = 1;

        params.setVersion(version);
        assertEquals(version, params.getVersion());
    }

    @Test
    void testTaskToDomainGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("task1", "domain1");

        params.setTaskToDomain(taskToDomain);
        assertEquals(taskToDomain, params.getTaskToDomain());
    }

    @Test
    void testWorkflowDefinitionGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test-workflow");
        workflowDef.setVersion(1);

        params.setWorkflowDefinition(workflowDef);
        assertEquals(workflowDef, params.getWorkflowDefinition());
    }

    @Test
    void testDeprecatedWorkflowDefGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test-workflow");
        workflowDef.setVersion(1);

        params.setWorkflowDef(workflowDef);
        assertEquals(workflowDef, params.getWorkflowDef());
    }

    @Test
    void testGetNameFromWorkflowDef() {
        SubWorkflowParams params = new SubWorkflowParams();
        WorkflowDef workflowDef = new WorkflowDef();
        String workflowName = "test-workflow-from-def";
        workflowDef.setName(workflowName);

        params.setWorkflowDefinition(workflowDef);
        assertEquals(workflowName, params.getName());
    }

    @Test
    void testGetVersionFromWorkflowDef() {
        SubWorkflowParams params = new SubWorkflowParams();
        WorkflowDef workflowDef = new WorkflowDef();
        Integer workflowVersion = 2;
        workflowDef.setVersion(workflowVersion);

        params.setWorkflowDefinition(workflowDef);
        assertEquals(workflowVersion, params.getVersion());
    }

    @Test
    void testWorkflowDefinitionSetterWithString() {
        SubWorkflowParams params = new SubWorkflowParams();
        String dslString = "${workflow.input.someString}";

        params.setWorkflowDefinition(dslString);
        assertEquals(dslString, params.getWorkflowDefinition());
    }

    @Test
    void testWorkflowDefinitionSetterWithInvalidString() {
        SubWorkflowParams params = new SubWorkflowParams();
        String invalidString = "not-a-dsl-string";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            params.setWorkflowDefinition(invalidString);
        });

        assertTrue(exception.getMessage().contains("not a valid DSL string"));
    }

    @Test
    void testWorkflowDefinitionSetterWithLinkedHashMap() {
        SubWorkflowParams params = new SubWorkflowParams();
        LinkedHashMap<String, Object> workflowMap = new LinkedHashMap<>();
        workflowMap.put("name", "test-workflow");
        workflowMap.put("version", 1);

        params.setWorkflowDefinition(workflowMap);
        assertTrue(params.getWorkflowDefinition() instanceof WorkflowDef);
    }

    @Test
    void testWorkflowDefinitionSetterWithInvalidObject() {
        SubWorkflowParams params = new SubWorkflowParams();
        Integer invalidObject = 123;

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            params.setWorkflowDefinition(invalidObject);
        });

        assertTrue(exception.getMessage().contains("must be either null, or WorkflowDef, or a valid DSL string"));
    }

    @Test
    void testWorkflowDefinitionSetterWithNull() {
        SubWorkflowParams params = new SubWorkflowParams();
        WorkflowDef workflowDef = new WorkflowDef();
        params.setWorkflowDefinition(workflowDef);

        params.setWorkflowDefinition(null);
        assertNull(params.getWorkflowDefinition());
    }

    @Test
    void testEqualsWithSameObject() {
        SubWorkflowParams params = new SubWorkflowParams();
        params.setName("test-workflow");
        params.setVersion(1);

        assertTrue(params.equals(params));
    }

    @Test
    void testEqualsWithNull() {
        SubWorkflowParams params = new SubWorkflowParams();

        assertFalse(params.equals(null));
    }

    @Test
    void testEqualsWithDifferentClass() {
        SubWorkflowParams params = new SubWorkflowParams();
        String differentClass = "not-a-subworkflow-params";

        assertFalse(params.equals(differentClass));
    }

    @Test
    void testEqualsWithEqualObjects() {
        SubWorkflowParams params1 = new SubWorkflowParams();
        params1.setName("test-workflow");
        params1.setVersion(1);

        SubWorkflowParams params2 = new SubWorkflowParams();
        params2.setName("test-workflow");
        params2.setVersion(1);

        assertTrue(params1.equals(params2));
    }

    @Test
    void testEqualsWithDifferentObjects() {
        SubWorkflowParams params1 = new SubWorkflowParams();
        params1.setName("test-workflow-1");
        params1.setVersion(1);

        SubWorkflowParams params2 = new SubWorkflowParams();
        params2.setName("test-workflow-2");
        params2.setVersion(1);

        assertFalse(params1.equals(params2));
    }

    @Test
    void testPriorityGetterSetter() {
        SubWorkflowParams params = new SubWorkflowParams();
        Integer priority = 100;

        params.setPriority(priority);
        assertEquals(priority, params.getPriority());
    }
}