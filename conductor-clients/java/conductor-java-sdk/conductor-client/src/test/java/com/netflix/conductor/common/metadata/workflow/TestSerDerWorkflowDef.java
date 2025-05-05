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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef.TimeoutPolicy;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//todo add missing fields in Taskdef - should pass this test
class TestSerDerWorkflowDef {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowDef");
        objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        WorkflowDef workflowDef = objectMapper.readValue(SERVER_JSON, WorkflowDef.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(workflowDef);

        // Basic fields
        assertEquals("sample_name", workflowDef.getName());
        assertEquals("sample_description", workflowDef.getDescription());
        assertEquals(123, workflowDef.getVersion());
        assertEquals("sample_failureWorkflow", workflowDef.getFailureWorkflow());
        assertEquals(123, workflowDef.getSchemaVersion());
        assertTrue(workflowDef.isRestartable());
        assertTrue(workflowDef.isWorkflowStatusListenerEnabled());
        assertEquals("sample_ownerEmail", workflowDef.getOwnerEmail());
        assertEquals(TimeoutPolicy.TIME_OUT_WF, workflowDef.getTimeoutPolicy());
        assertEquals(123L, workflowDef.getTimeoutSeconds());
        assertEquals("sample_workflowStatusListenerSink", workflowDef.getWorkflowStatusListenerSink());
        assertTrue(workflowDef.isEnforceSchema());

        // Check lists
        List<WorkflowTask> tasks = workflowDef.getTasks();
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        List<String> inputParameters = workflowDef.getInputParameters();
        assertNotNull(inputParameters);
        assertFalse(inputParameters.isEmpty());
        assertEquals("sample_inputParameters", inputParameters.get(0));

        // Check maps
        Map<String, Object> outputParameters = workflowDef.getOutputParameters();
        assertNotNull(outputParameters);
        assertFalse(outputParameters.isEmpty());
        assertTrue(outputParameters.containsKey("sample_key"));

        Map<String, Object> variables = workflowDef.getVariables();
        assertNotNull(variables);
        assertFalse(variables.isEmpty());

        Map<String, Object> inputTemplate = workflowDef.getInputTemplate();
        assertNotNull(inputTemplate);
        assertFalse(inputTemplate.isEmpty());

        // Check complex types
        assertNotNull(workflowDef.getRateLimitConfig());
        assertNotNull(workflowDef.getInputSchema());
        assertNotNull(workflowDef.getOutputSchema());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(workflowDef);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}