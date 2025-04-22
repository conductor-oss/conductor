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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSerDerWorkflowSummary {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowSummary");
        WorkflowSummary workflowSummary = objectMapper.readValue(SERVER_JSON, WorkflowSummary.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(workflowSummary);

        // String fields
        assertEquals("sample_workflowType", workflowSummary.getWorkflowType());
        assertEquals("sample_workflowId", workflowSummary.getWorkflowId());
        assertEquals("sample_correlationId", workflowSummary.getCorrelationId());
        assertEquals("sample_startTime", workflowSummary.getStartTime());
        assertEquals("sample_updateTime", workflowSummary.getUpdateTime());
        assertEquals("sample_endTime", workflowSummary.getEndTime());
        assertEquals("sample_input", workflowSummary.getInput());
        assertEquals("sample_output", workflowSummary.getOutput());
        assertEquals("sample_reasonForIncompletion", workflowSummary.getReasonForIncompletion());
        assertEquals("sample_event", workflowSummary.getEvent());
        assertEquals("sample_failedReferenceTaskNames", workflowSummary.getFailedReferenceTaskNames());
        assertEquals("sample_externalInputPayloadStoragePath", workflowSummary.getExternalInputPayloadStoragePath());
        assertEquals("sample_externalOutputPayloadStoragePath", workflowSummary.getExternalOutputPayloadStoragePath());
        assertEquals("sample_createdBy", workflowSummary.getCreatedBy());

        // Primitive fields
        assertEquals(123, workflowSummary.getVersion());
        assertEquals(123L, workflowSummary.getExecutionTime());
        assertEquals(123, workflowSummary.getPriority());

        // Enum field
        assertEquals(WorkflowStatus.RUNNING, workflowSummary.getStatus());

        // Set field (failedTaskNames)
        Set<String> expectedFailedTaskNames = new HashSet<>();
        expectedFailedTaskNames.add("sample_failedTaskNames");
        assertEquals(expectedFailedTaskNames, workflowSummary.getFailedTaskNames());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(workflowSummary);

        // 4. Compare the JSONs - nothing should be lost from the original
        JsonNode originalJson = objectMapper.readTree(SERVER_JSON);
        JsonNode reserializedJson = objectMapper.readTree(serializedJson);

        // Verify each field from the original exists in the reserialized version with same value
        originalJson.fieldNames().forEachRemaining(fieldName -> {
            assertTrue(reserializedJson.has(fieldName), "Field " + fieldName + " should exist in reserialized JSON");
            assertEquals(
                    originalJson.get(fieldName),
                    reserializedJson.get(fieldName),
                    "Field " + fieldName + " should have same value in both JSONs"
            );
        });
    }
}