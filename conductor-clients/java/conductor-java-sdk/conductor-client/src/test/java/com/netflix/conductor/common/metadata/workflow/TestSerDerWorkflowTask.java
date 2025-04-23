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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// todo  missing fields in the dependent pojos - fixing that should pass this test
public class TestSerDerWorkflowTask {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializeDeserialize() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowTask");
        objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        WorkflowTask workflowTask = objectMapper.readValue(SERVER_JSON, WorkflowTask.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(workflowTask);

        // Basic fields
        assertEquals("sample_name", workflowTask.getName());
        assertEquals("sample_taskReferenceName", workflowTask.getTaskReferenceName());
        assertEquals("sample_description", workflowTask.getDescription());

        // Maps
        assertNotNull(workflowTask.getInputParameters());
        assertTrue(workflowTask.getInputParameters().containsKey("sample_key"));

        assertNotNull(workflowTask.getDecisionCases());
        assertTrue(workflowTask.getDecisionCases().containsKey("sample_key"));
        assertTrue(workflowTask.getDecisionCases().get("sample_key") instanceof java.util.List);

        assertNotNull(workflowTask.getOnStateChange());
        assertTrue(workflowTask.getOnStateChange().containsKey("sample_key"));
        assertTrue(workflowTask.getOnStateChange().get("sample_key") instanceof java.util.List);

        // Lists
        assertNotNull(workflowTask.getDefaultCase());
        assertNotNull(workflowTask.getForkTasks());
        assertNotNull(workflowTask.getJoinOn());
        assertNotNull(workflowTask.getDefaultExclusiveJoinTask());
        assertNotNull(workflowTask.getLoopOver());

        // Nested objects
        assertNotNull(workflowTask.getSubWorkflowParam());
        assertNotNull(workflowTask.getTaskDefinition());
        assertNotNull(workflowTask.getCacheConfig());

        // Enums and type values
        assertNotNull(workflowTask.getType());

        // Primitive types
        assertEquals(123, workflowTask.getStartDelay());
        assertEquals(true, workflowTask.isOptional());
        assertEquals(true, workflowTask.isPermissive());

        // Boolean object types
        assertNotNull(workflowTask.getRateLimited());
        assertNotNull(workflowTask.isAsyncComplete());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(workflowTask);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson)
        );
    }
}