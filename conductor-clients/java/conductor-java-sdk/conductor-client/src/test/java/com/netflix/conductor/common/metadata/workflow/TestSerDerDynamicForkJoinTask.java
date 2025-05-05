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

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerDynamicForkJoinTask {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Get SERVER_JSON from template
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("DynamicForkJoinTask");

        // 2. Unmarshal SERVER_JSON to SDK POJO
        DynamicForkJoinTask dynamicForkJoinTask = objectMapper.readValue(SERVER_JSON, DynamicForkJoinTask.class);

        // 3. Assert that the fields are correctly populated
        assertNotNull(dynamicForkJoinTask);
        assertNotNull(dynamicForkJoinTask.getTaskName());
        assertEquals("sample_taskName", dynamicForkJoinTask.getTaskName());

        assertNotNull(dynamicForkJoinTask.getWorkflowName());
        assertEquals("sample_workflowName", dynamicForkJoinTask.getWorkflowName());

        assertNotNull(dynamicForkJoinTask.getReferenceName());
        assertEquals("sample_referenceName", dynamicForkJoinTask.getReferenceName());

        assertNotNull(dynamicForkJoinTask.getType());
        assertEquals(TaskType.SIMPLE.name(), dynamicForkJoinTask.getType());

        // Check the Map field
        assertNotNull(dynamicForkJoinTask.getInput());
        assertEquals(1, dynamicForkJoinTask.getInput().size());
        assertTrue(dynamicForkJoinTask.getInput().containsKey("sample_key"));
        assertEquals("sample_value", dynamicForkJoinTask.getInput().get("sample_key"));

        // 4. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(dynamicForkJoinTask);

        // 5. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}