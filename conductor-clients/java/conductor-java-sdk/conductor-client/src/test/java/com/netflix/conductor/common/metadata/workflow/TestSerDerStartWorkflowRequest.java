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

import static org.junit.jupiter.api.Assertions.*;

//todo add fields in TaskDef class of sdk - this test will pass
public class TestSerDerStartWorkflowRequest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializeDeserialize() throws Exception {
        // 1. Get the SERVER_JSON from the utility
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("StartWorkflowRequest");

        // 2. Unmarshal SERVER_JSON to SDK POJO
        objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        StartWorkflowRequest startWorkflowRequest = objectMapper.readValue(SERVER_JSON, StartWorkflowRequest.class);

        // 3. Assert that the fields are all correctly populated
        assertNotNull(startWorkflowRequest);

        // Check basic fields
        assertEquals("sample_name", startWorkflowRequest.getName());
        assertEquals(Integer.valueOf(123), startWorkflowRequest.getVersion());
        assertEquals("sample_correlationId", startWorkflowRequest.getCorrelationId());
        assertEquals("sample_externalInputPayloadStoragePath", startWorkflowRequest.getExternalInputPayloadStoragePath());
        assertEquals(Integer.valueOf(123), startWorkflowRequest.getPriority());
        assertEquals("sample_createdBy", startWorkflowRequest.getCreatedBy());
        assertEquals("sample_idempotencyKey", startWorkflowRequest.getIdempotencyKey());
        assertNotNull(startWorkflowRequest.getIdempotencyStrategy());

        // Check Map fields
        assertNotNull(startWorkflowRequest.getInput());
        assertFalse(startWorkflowRequest.getInput().isEmpty());
        assertEquals(1, startWorkflowRequest.getInput().size());

        assertNotNull(startWorkflowRequest.getTaskToDomain());
        assertFalse(startWorkflowRequest.getTaskToDomain().isEmpty());
        assertEquals(1, startWorkflowRequest.getTaskToDomain().size());

        // Check WorkflowDef field
        assertNotNull(startWorkflowRequest.getWorkflowDef());

        // 4. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(startWorkflowRequest);

        // 5. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}