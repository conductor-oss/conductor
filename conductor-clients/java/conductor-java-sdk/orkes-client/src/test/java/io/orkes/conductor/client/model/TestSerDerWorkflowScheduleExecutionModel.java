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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//todo - adding Missing fields in classes of which this class is composed and it will pass this test
public class TestSerDerWorkflowScheduleExecutionModel {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("WorkflowScheduleExecutionModel");
        objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        objectMapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        WorkflowScheduleExecutionModel model = objectMapper.readValue(SERVER_JSON, WorkflowScheduleExecutionModel.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(model);
        assertEquals("sample_executionId", model.getExecutionId());
        assertEquals(123L, model.getExecutionTime());
        assertEquals("sample_reason", model.getReason());
        assertEquals("sample_scheduleName", model.getScheduleName());
        assertEquals(123L, model.getScheduledTime());
        assertEquals("sample_stackTrace", model.getStackTrace());

        // Check enum
        assertEquals(WorkflowScheduleExecutionModel.StateEnum.POLLED, model.getState());

        assertEquals("sample_workflowId", model.getWorkflowId());
        assertEquals("sample_workflowName", model.getWorkflowName());

        // Check StartWorkflowRequest
        StartWorkflowRequest startWorkflowRequest = model.getStartWorkflowRequest();
        assertNotNull(startWorkflowRequest);
        // Add assertions for StartWorkflowRequest fields if needed
        // This would depend on what's populated in the JSON template

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(model);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}