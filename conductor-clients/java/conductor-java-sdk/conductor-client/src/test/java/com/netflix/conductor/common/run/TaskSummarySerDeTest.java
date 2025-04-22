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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// todo : add missing field in sdk pojo should pass this test
class TaskSummarySerDeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("TaskSummary");
        TaskSummary taskSummary = objectMapper.readValue(SERVER_JSON, TaskSummary.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(taskSummary);

        // Verify string fields
        assertEquals("sample_workflowId", taskSummary.getWorkflowId());
        assertEquals("sample_workflowType", taskSummary.getWorkflowType());
        assertEquals("sample_correlationId", taskSummary.getCorrelationId());
        assertEquals("sample_scheduledTime", taskSummary.getScheduledTime());
        assertEquals("sample_startTime", taskSummary.getStartTime());
        assertEquals("sample_updateTime", taskSummary.getUpdateTime());
        assertEquals("sample_endTime", taskSummary.getEndTime());
        assertEquals("sample_reasonForIncompletion", taskSummary.getReasonForIncompletion());
        assertEquals("sample_taskDefName", taskSummary.getTaskDefName());
        assertEquals("sample_taskType", taskSummary.getTaskType());
        assertEquals("sample_input", taskSummary.getInput());
        assertEquals("sample_output", taskSummary.getOutput());
        assertEquals("sample_taskId", taskSummary.getTaskId());
        assertEquals("sample_externalInputPayloadStoragePath", taskSummary.getExternalInputPayloadStoragePath());
        assertEquals("sample_externalOutputPayloadStoragePath", taskSummary.getExternalOutputPayloadStoragePath());

        // Verify numeric fields
        assertEquals(123L, taskSummary.getExecutionTime());
        assertEquals(123L, taskSummary.getQueueWaitTime());
        assertEquals(123, taskSummary.getWorkflowPriority());

        // Verify enum
        assertNotNull(taskSummary.getStatus());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(taskSummary);

        // 4. Compare the JSONs - nothing should be lost
        JsonNode originalJsonNode = objectMapper.readTree(SERVER_JSON);
        JsonNode serializedJsonNode = objectMapper.readTree(serializedJson);

        assertEquals(originalJsonNode, serializedJsonNode);
    }
}