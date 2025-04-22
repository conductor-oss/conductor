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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// todo fix circular dependency handling and missing field in dependent sdk pojos should pass the test
public class TestSerDerWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void testSerializationAndDeserialization() throws Exception {
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("Workflow");
        // 1. Unmarshal SERVER_JSON to SDK POJO
        Workflow workflow = objectMapper.readValue(SERVER_JSON, Workflow.class);

        // 2. Assert that the fields are correctly populated
        assertNotNull(workflow);

        // Check Enum
        assertNotNull(workflow.getStatus());
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        // Check primitive fields
        assertEquals(123L, workflow.getEndTime());
        assertEquals("sample_workflowId", workflow.getWorkflowId());
        assertEquals("sample_parentWorkflowId", workflow.getParentWorkflowId());
        assertEquals("sample_parentWorkflowTaskId", workflow.getParentWorkflowTaskId());

        // Check Lists
        List<Task> tasks = workflow.getTasks();
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        // Check Maps
        Map<String, Object> input = workflow.getInput();
        assertNotNull(input);
        assertEquals(1, input.size());
        assertTrue(input.containsKey("sample_key"));

        Map<String, Object> output = workflow.getOutput();
        assertNotNull(output);
        assertEquals(1, output.size());
        assertTrue(output.containsKey("sample_key"));

        Map<String, String> taskToDomain = workflow.getTaskToDomain();
        assertNotNull(taskToDomain);
        assertEquals(1, taskToDomain.size());
        assertTrue(taskToDomain.containsKey("sample_key"));

        // Check Sets
        Set<String> failedReferenceTaskNames = workflow.getFailedReferenceTaskNames();
        assertNotNull(failedReferenceTaskNames);
        assertEquals(1, failedReferenceTaskNames.size());

        Set<String> failedTaskNames = workflow.getFailedTaskNames();
        assertNotNull(failedTaskNames);
        assertEquals(1, failedTaskNames.size());

        // Check embedded objects
        assertNotNull(workflow.getWorkflowDefinition());

        // Check other fields
        assertEquals("sample_correlationId", workflow.getCorrelationId());
        assertEquals("sample_reRunFromWorkflowId", workflow.getReRunFromWorkflowId());
        assertEquals("sample_reasonForIncompletion", workflow.getReasonForIncompletion());
        assertEquals("sample_event", workflow.getEvent());
        assertEquals("sample_externalInputPayloadStoragePath", workflow.getExternalInputPayloadStoragePath());
        assertEquals("sample_externalOutputPayloadStoragePath", workflow.getExternalOutputPayloadStoragePath());
        assertEquals(123, workflow.getPriority());
        assertEquals(123L, workflow.getLastRetriedTime());
        assertEquals("sample_idempotencyKey", workflow.getIdempotencyKey());
        assertEquals("sample_rateLimitKey", workflow.getRateLimitKey());
        assertTrue(workflow.isRateLimited());

        Map<String, Object> variables = workflow.getVariables();
        assertNotNull(variables);
        assertEquals(1, variables.size());
        assertTrue(variables.containsKey("sample_key"));

        // Check history
        List<Workflow> history = workflow.getHistory();
        assertNotNull(history);
        assertEquals(1, history.size());

        // 3. Marshall POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(workflow);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}