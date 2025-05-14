/*
 * Copyright 2020 Conductor Authors.
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

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerRerunWorkflowRequest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializeDeserialize() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("RerunWorkflowRequest");
        RerunWorkflowRequest rerunWorkflowRequest = objectMapper.readValue(SERVER_JSON, RerunWorkflowRequest.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(rerunWorkflowRequest);

        // Check String fields
        assertEquals("sample_reRunFromWorkflowId", rerunWorkflowRequest.getReRunFromWorkflowId());
        assertEquals("sample_reRunFromTaskId", rerunWorkflowRequest.getReRunFromTaskId());
        assertEquals("sample_correlationId", rerunWorkflowRequest.getCorrelationId());

        // Check Map fields
        Map<String, Object> workflowInput = rerunWorkflowRequest.getWorkflowInput();
        assertNotNull(workflowInput);
        assertEquals(1, workflowInput.size());
        assertEquals("sample_value", workflowInput.get("sample_key"));

        Map<String, Object> taskInput = rerunWorkflowRequest.getTaskInput();
        assertNotNull(taskInput);
        assertEquals(1, taskInput.size());
        assertEquals("sample_value", taskInput.get("sample_key"));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(rerunWorkflowRequest);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}