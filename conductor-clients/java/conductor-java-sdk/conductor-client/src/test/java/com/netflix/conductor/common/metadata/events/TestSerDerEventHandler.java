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
package com.netflix.conductor.common.metadata.events;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSerDerEventHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("EventHandler");
        EventHandler eventHandler = objectMapper.readValue(SERVER_JSON, EventHandler.class);

        // 2. Assert that fields are correctly populated
        assertNotNull(eventHandler);

        // Basic fields
        assertEquals("sample_name", eventHandler.getName());
        assertEquals("sample_event", eventHandler.getEvent());
        assertEquals("sample_condition", eventHandler.getCondition());
        assertEquals("sample_evaluatorType", eventHandler.getEvaluatorType());
        assertTrue(eventHandler.isActive());

        // Lists
        assertNotNull(eventHandler.getActions());
        assertEquals(1, eventHandler.getActions().size());

        // Verify action details
        EventHandler.Action action = eventHandler.getActions().get(0);
        assertEquals(EventHandler.Action.Type.start_workflow, action.getAction());
        assertTrue(action.isExpandInlineJSON());

        // Verify StartWorkflow
        EventHandler.StartWorkflow startWorkflow = action.getStart_workflow();
        assertNotNull(startWorkflow);
        assertEquals("sample_name", startWorkflow.getName());
        assertEquals(Integer.valueOf(123), startWorkflow.getVersion());
        assertEquals("sample_correlationId", startWorkflow.getCorrelationId());

        // Verify Maps
        assertNotNull(startWorkflow.getInput());
        assertEquals(1, startWorkflow.getInput().size());
        assertTrue(startWorkflow.getInput().containsKey("key"));

        assertNotNull(startWorkflow.getTaskToDomain());
        assertEquals(1, startWorkflow.getTaskToDomain().size());
        assertTrue(startWorkflow.getTaskToDomain().containsKey("key"));

        // Verify remaining action fields
        assertNotNull(action.getComplete_task());
        assertEquals("sample_workflowId", action.getComplete_task().getWorkflowId());
        assertEquals("sample_taskRefName", action.getComplete_task().getTaskRefName());
        assertEquals("sample_taskId", action.getComplete_task().getTaskId());
        assertNotNull(action.getComplete_task().getOutput());
        assertEquals(1, action.getComplete_task().getOutput().size());

        assertNotNull(action.getFail_task());
        assertEquals("sample_workflowId", action.getFail_task().getWorkflowId());
        assertEquals("sample_taskRefName", action.getFail_task().getTaskRefName());
        assertEquals("sample_taskId", action.getFail_task().getTaskId());
        assertNotNull(action.getFail_task().getOutput());
        assertEquals(1, action.getFail_task().getOutput().size());

        assertNotNull(action.getTerminate_workflow());
        assertEquals("sample_workflowId", action.getTerminate_workflow().getWorkflowId());
        assertEquals("sample_terminationReason", action.getTerminate_workflow().getTerminationReason());

        assertNotNull(action.getUpdate_workflow_variables());
        assertEquals("sample_workflowId", action.getUpdate_workflow_variables().getWorkflowId());
        assertNotNull(action.getUpdate_workflow_variables().getVariables());
        assertEquals(1, action.getUpdate_workflow_variables().getVariables().size());
        assertTrue(action.getUpdate_workflow_variables().isAppendArray());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(eventHandler);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}