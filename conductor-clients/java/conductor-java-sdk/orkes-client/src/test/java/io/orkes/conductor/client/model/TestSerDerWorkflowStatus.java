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

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerWorkflowStatus {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowStatus");
        WorkflowStatus workflowStatus = objectMapper.readValue(SERVER_JSON, WorkflowStatus.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(workflowStatus);

        // Check String fields
        assertEquals("sample_correlationId", workflowStatus.getCorrelationId());
        assertEquals("sample_workflowId", workflowStatus.getWorkflowId());

        // Check Enum field
        assertEquals(WorkflowStatus.StatusEnum.RUNNING, workflowStatus.getStatus());

        // Check Map fields
        assertNotNull(workflowStatus.getOutput());
        assertEquals(1, workflowStatus.getOutput().size());
        assertEquals("sample_value", workflowStatus.getOutput().get("key"));

        assertNotNull(workflowStatus.getVariables());
        assertEquals(1, workflowStatus.getVariables().size());
        assertEquals("sample_value", workflowStatus.getVariables().get("key"));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(workflowStatus);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}