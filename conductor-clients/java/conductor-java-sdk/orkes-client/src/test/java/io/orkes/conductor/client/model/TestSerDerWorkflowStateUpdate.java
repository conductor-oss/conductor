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

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskResult;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerWorkflowStateUpdate {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Get the server JSON template
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowStateUpdate");

        // 2. Deserialize JSON to SDK POJO
        WorkflowStateUpdate workflowStateUpdate = objectMapper.readValue(SERVER_JSON, WorkflowStateUpdate.class);

        // 3. Assert that the fields are correctly populated
        assertNotNull(workflowStateUpdate);
        assertNotNull(workflowStateUpdate.getTaskReferenceName());
        assertEquals("sample_taskReferenceName", workflowStateUpdate.getTaskReferenceName());

        // Check Map field
        Map<String, Object> variables = workflowStateUpdate.getVariables();
        assertNotNull(variables);
        assertEquals(1, variables.size());
        assertEquals("sample_value", variables.get("sample_key"));

        // Check TaskResult field
        TaskResult taskResult = workflowStateUpdate.getTaskResult();
        assertNotNull(taskResult);

        // 4. Serialize the POJO back to JSON
        String serializedJson = objectMapper.writeValueAsString(workflowStateUpdate);

        // 5. Compare the JSONs to ensure nothing is lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}