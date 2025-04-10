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

class TestUpgradeWorkflowRequestPojoMethods {

    @Test
    void testDefaultConstructor() {
        UpgradeWorkflowRequest request = new UpgradeWorkflowRequest();

        assertNull(request.getTaskOutput());
        assertNull(request.getWorkflowInput());
        assertNull(request.getName());
        assertNull(request.getVersion());
    }

    @Test
    void testTaskOutputGetterSetter() {
        UpgradeWorkflowRequest request = new UpgradeWorkflowRequest();
        Map<String, Object> taskOutput = new HashMap<>();
        taskOutput.put("key1", "value1");
        taskOutput.put("key2", 123);

        request.setTaskOutput(taskOutput);
        assertEquals(taskOutput, request.getTaskOutput());
    }

    @Test
    void testWorkflowInputGetterSetter() {
        UpgradeWorkflowRequest request = new UpgradeWorkflowRequest();
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "value1");
        workflowInput.put("param2", 456);

        request.setWorkflowInput(workflowInput);
        assertEquals(workflowInput, request.getWorkflowInput());
    }

    @Test
    void testNameGetterSetter() {
        UpgradeWorkflowRequest request = new UpgradeWorkflowRequest();
        String workflowName = "testWorkflow";

        request.setName(workflowName);
        assertEquals(workflowName, request.getName());
    }

    @Test
    void testVersionGetterSetter() {
        UpgradeWorkflowRequest request = new UpgradeWorkflowRequest();
        Integer version = 2;

        request.setVersion(version);
        assertEquals(version, request.getVersion());
    }

    @Test
    void testAllProperties() {
        UpgradeWorkflowRequest request = new UpgradeWorkflowRequest();

        String name = "myWorkflow";
        Integer version = 3;
        Map<String, Object> taskOutput = new HashMap<>();
        taskOutput.put("result", "success");
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("inputParam", "inputValue");

        request.setName(name);
        request.setVersion(version);
        request.setTaskOutput(taskOutput);
        request.setWorkflowInput(workflowInput);

        assertEquals(name, request.getName());
        assertEquals(version, request.getVersion());
        assertEquals(taskOutput, request.getTaskOutput());
        assertEquals(workflowInput, request.getWorkflowInput());
    }
}