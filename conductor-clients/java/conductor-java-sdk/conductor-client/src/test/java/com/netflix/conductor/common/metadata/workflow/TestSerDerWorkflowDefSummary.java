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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerWorkflowDefSummary {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Get the server JSON template
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowDefSummary");

        // 2. Unmarshal SERVER_JSON to SDK POJO
        WorkflowDefSummary workflowDefSummary = objectMapper.readValue(SERVER_JSON, WorkflowDefSummary.class);

        // 3. Assert that fields are correctly populated
        assertNotNull(workflowDefSummary);
        assertEquals("sample_name", workflowDefSummary.getName());
        assertEquals(123, workflowDefSummary.getVersion());
        assertEquals(123L, workflowDefSummary.getCreateTime());

        // 4. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(workflowDefSummary);

        // 5. Compare JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}