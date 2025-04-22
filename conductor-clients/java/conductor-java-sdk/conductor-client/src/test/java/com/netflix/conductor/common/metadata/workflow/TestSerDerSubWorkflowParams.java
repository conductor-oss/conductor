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

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerSubWorkflowParams {

    private final ObjectMapper objectMapper;

    public TestSerDerSubWorkflowParams() {
        // Initialize ObjectMapper with recursion depth limit
        objectMapper = new ObjectMapper();

        // Limit recursion depth to 1 to prevent nested SubWorkflowParams processing
        objectMapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(9).build());

        // Additional settings to help with deserialization issues
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Test
    public void testSerializationDeserialization() throws IOException {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("SubWorkflowParams");
        SubWorkflowParams subWorkflowParams = objectMapper.readValue(SERVER_JSON, SubWorkflowParams.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(subWorkflowParams);
        assertEquals("sample_name", subWorkflowParams.getName());
        assertEquals(123, subWorkflowParams.getVersion());

        // Check Map
        Map<String, String> taskToDomain = subWorkflowParams.getTaskToDomain();
        assertNotNull(taskToDomain);
        assertEquals(1, taskToDomain.size());
        assertEquals("sample_value", taskToDomain.get("sample_key"));

        // Check enum
        assertNotNull(subWorkflowParams.getIdempotencyStrategy());

        // Check idempotencyKey
        assertEquals("sample_idempotencyKey", subWorkflowParams.getIdempotencyKey());

        // Check priority (Object type)
        assertNotNull(subWorkflowParams.getPriority());

        // Check workflowDefinition (Object type)
        Object workflowDefinition = subWorkflowParams.getWorkflowDefinition();
        assertNotNull(workflowDefinition);

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(subWorkflowParams);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson)
        );
    }
}