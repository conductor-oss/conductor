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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


// todo fix missing fields in dependent sdk pojos - should pass this test
public class TestSerDerWorkflowTestRequest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowTestRequest");
        // Configure ObjectMapper to ignore deprecated fields
        objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        WorkflowTestRequest workflowTestRequest = objectMapper.readValue(SERVER_JSON, WorkflowTestRequest.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(workflowTestRequest);

        // Check taskRefToMockOutput map
        Map<String, List<WorkflowTestRequest.TaskMock>> taskRefToMockOutput = workflowTestRequest.getTaskRefToMockOutput();
        assertNotNull(taskRefToMockOutput);
        assertEquals(1, taskRefToMockOutput.size());

        String taskRefKey = taskRefToMockOutput.keySet().iterator().next();
        assertNotNull(taskRefKey);

        List<WorkflowTestRequest.TaskMock> taskMocks = taskRefToMockOutput.get(taskRefKey);
        assertNotNull(taskMocks);
        assertEquals(1, taskMocks.size());

        WorkflowTestRequest.TaskMock taskMock = taskMocks.get(0);
        assertNotNull(taskMock);
        assertEquals(TaskResult.Status.IN_PROGRESS, taskMock.getStatus());
        assertNotNull(taskMock.getOutput());

        // Check executionTime and queueWaitTime
        assertEquals(123L, taskMock.getExecutionTime());
        assertEquals(123L, taskMock.getQueueWaitTime());

        // Check subWorkflowTestRequest map
        Map<String, WorkflowTestRequest> subWorkflowTestRequest = workflowTestRequest.getSubWorkflowTestRequest();
        assertNotNull(subWorkflowTestRequest);
        assertEquals(1, subWorkflowTestRequest.size());

        String subWorkflowKey = subWorkflowTestRequest.keySet().iterator().next();
        assertNotNull(subWorkflowKey);

        WorkflowTestRequest subWorkflow = subWorkflowTestRequest.get(subWorkflowKey);
        assertNotNull(subWorkflow);

        // Also check parent class (StartWorkflowRequest) fields
        assertNotNull(workflowTestRequest.getName());
        assertNotNull(workflowTestRequest.getInput());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(workflowTestRequest);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}