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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//todo add fields in the sdk pojo TaskDef and fix circular dependency of WorkflowDef - it will pass this test
public class TestSerDerTask {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("Task");
        Task task = objectMapper.readValue(SERVER_JSON, Task.class);

        // 2. Assert that fields are correctly populated
        assertNotNull(task);

        // Assert basic fields
        assertEquals("sample_taskType", task.getTaskType());
        assertEquals(Status.IN_PROGRESS, task.getStatus());
        assertEquals("sample_referenceTaskName", task.getReferenceTaskName());
        assertEquals(123, task.getRetryCount());
        assertEquals(123, task.getSeq());
        assertEquals("sample_correlationId", task.getCorrelationId());
        assertEquals(123, task.getPollCount());
        assertEquals("sample_taskDefName", task.getTaskDefName());
        assertEquals(123L, task.getScheduledTime());
        assertEquals(123L, task.getStartTime());
        assertEquals(123L, task.getEndTime());
        assertEquals(123L, task.getUpdateTime());
        assertEquals(123, task.getStartDelayInSeconds());
        assertEquals("sample_retriedTaskId", task.getRetriedTaskId());
        assertEquals(true, task.isRetried());
        assertEquals(true, task.isExecuted());
        assertEquals(true, task.isCallbackFromWorker());
        assertEquals(123L, task.getResponseTimeoutSeconds());
        assertEquals("sample_workflowInstanceId", task.getWorkflowInstanceId());
        assertEquals("sample_workflowType", task.getWorkflowType());
        assertEquals("sample_taskId", task.getTaskId());
        assertEquals("sample_reasonForIncompletion", task.getReasonForIncompletion());
        assertEquals(123L, task.getCallbackAfterSeconds());
        assertEquals("sample_workerId", task.getWorkerId());
        assertEquals("sample_domain", task.getDomain());
        assertEquals(123, task.getRateLimitPerFrequency());
        assertEquals(123, task.getRateLimitFrequencyInSeconds());
        assertEquals("sample_externalInputPayloadStoragePath", task.getExternalInputPayloadStoragePath());
        assertEquals("sample_externalOutputPayloadStoragePath", task.getExternalOutputPayloadStoragePath());
        assertEquals(123, task.getWorkflowPriority());
        assertEquals("sample_executionNameSpace", task.getExecutionNameSpace());
        assertEquals("sample_isolationGroupId", task.getIsolationGroupId());
        assertEquals(123, task.getIteration());
        assertEquals("sample_subWorkflowId", task.getSubWorkflowId());
        assertEquals(true, task.isSubworkflowChanged());
        assertEquals("sample_parentTaskId", task.getParentTaskId());

        // Assert Map fields
        assertNotNull(task.getInputData());
        assertEquals(1, task.getInputData().size());
        assertEquals("sample_value", task.getInputData().get("sample_key"));

        assertNotNull(task.getOutputData());
        assertEquals(1, task.getOutputData().size());
        assertEquals("sample_value", task.getOutputData().get("sample_key"));

        // Assert WorkflowTask field
        assertNotNull(task.getWorkflowTask());

        // 3. Marshall POJO back to JSON
        String serializedJson = objectMapper.writeValueAsString(task);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson)
        );
    }
}