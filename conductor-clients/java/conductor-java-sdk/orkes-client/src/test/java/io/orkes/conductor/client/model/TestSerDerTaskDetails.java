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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerTaskDetails {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testTaskDetailsSerialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("EventHandler.TaskDetails");
        TaskDetails taskDetails = objectMapper.readValue(SERVER_JSON, TaskDetails.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(taskDetails);
        assertNotNull(taskDetails.getTaskId());
        assertEquals("sample_taskId", taskDetails.getTaskId());

        assertNotNull(taskDetails.getTaskRefName());
        assertEquals("sample_taskRefName", taskDetails.getTaskRefName());

        assertNotNull(taskDetails.getWorkflowId());
        assertEquals("sample_workflowId", taskDetails.getWorkflowId());

        // Check the Map field
        Map<String, Object> output = taskDetails.getOutput();
        assertNotNull(output);
        assertEquals(1, output.size());
        assertEquals("sample_value", output.get("key"));

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(taskDetails);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }

    @Test
    public void testTaskDetailsBuilder() throws Exception {
        // Create a TaskDetails using builder pattern
        Map<String, Object> outputMap = new HashMap<>();
        outputMap.put("key", "sample_value");

        TaskDetails taskDetails = new TaskDetails()
                .taskId("sample_taskId")
                .taskRefName("sample_taskRefName")
                .workflowId("sample_workflowId")
                .output(outputMap);

        // Test individual field getters
        assertEquals("sample_taskId", taskDetails.getTaskId());
        assertEquals("sample_taskRefName", taskDetails.getTaskRefName());
        assertEquals("sample_workflowId", taskDetails.getWorkflowId());
        assertEquals(outputMap, taskDetails.getOutput());

        // Test putOutputItem method
        TaskDetails taskDetails2 = new TaskDetails();
        taskDetails2.putOutputItem("key", "sample_value");
        assertNotNull(taskDetails2.getOutput());
        assertEquals("sample_value", taskDetails2.getOutput().get("key"));

        // Serialize to JSON
        String serializedJson = objectMapper.writeValueAsString(taskDetails);

        // Deserialize back
        TaskDetails deserializedTaskDetails = objectMapper.readValue(serializedJson, TaskDetails.class);

        // Verify equality
        assertEquals(taskDetails, deserializedTaskDetails);
    }
}