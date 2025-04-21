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
package com.netflix.conductor.common.metadata.tasks;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSerDerTaskResult {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("TaskResult");
        TaskResult taskResult = objectMapper.readValue(SERVER_JSON, TaskResult.class);

        // 2. Assert that the fields are all correctly populated
        // Basic fields
        assertEquals("sample_workflowInstanceId", taskResult.getWorkflowInstanceId(), "workflowInstanceId should match");
        assertEquals("sample_taskId", taskResult.getTaskId(), "taskId should match");
        assertEquals("sample_reasonForIncompletion", taskResult.getReasonForIncompletion(), "reasonForIncompletion should match");
        assertEquals(123L, taskResult.getCallbackAfterSeconds(), "callbackAfterSeconds should match");
        assertEquals("sample_workerId", taskResult.getWorkerId(), "workerId should match");
        assertEquals("sample_subWorkflowId", taskResult.getSubWorkflowId(), "subWorkflowId should match");
        assertEquals("sample_externalOutputPayloadStoragePath", taskResult.getExternalOutputPayloadStoragePath(), "externalOutputPayloadStoragePath should match");
        assertTrue(taskResult.isExtendLease(), "extendLease should be true");

        // Status enum
        assertNotNull(taskResult.getStatus(), "status should not be null");
        assertEquals(Status.IN_PROGRESS, taskResult.getStatus(), "status should match IN_PROGRESS");

        // Map validation
        Map<String, Object> outputData = taskResult.getOutputData();
        assertNotNull(outputData, "outputData map should not be null");
        assertFalse(outputData.isEmpty(), "outputData map should not be empty");
        assertEquals("sample_value", outputData.get("sample_key"), "outputData map content should match");

        // List validation
        List<TaskExecLog> logs = taskResult.getLogs();
        assertNotNull(logs, "logs list should not be null");
        assertFalse(logs.isEmpty(), "logs list should not be empty");
        assertEquals("sample_log", logs.get(0).getLog(), "logs content should match");

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(taskResult);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson),
                "Original and serialized JSON should be identical"
        );
    }
}