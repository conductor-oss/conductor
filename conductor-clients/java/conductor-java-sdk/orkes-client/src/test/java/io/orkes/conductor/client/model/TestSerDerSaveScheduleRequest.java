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

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//todo - adding Missing fields in classes of which this class is composed and it will pass this test
class TestSerDerSaveScheduleRequest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializeDeserialize() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("SaveScheduleRequest");
        objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        objectMapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        SaveScheduleRequest saveScheduleRequest = objectMapper.readValue(SERVER_JSON, SaveScheduleRequest.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(saveScheduleRequest);
        assertEquals("sample_createdBy", saveScheduleRequest.getCreatedBy());
        assertEquals("sample_cronExpression", saveScheduleRequest.getCronExpression());
        assertEquals("sample_name", saveScheduleRequest.getName());
        assertTrue(saveScheduleRequest.isPaused());
        assertTrue(saveScheduleRequest.isRunCatchupScheduleInstances());
        assertEquals(123L, saveScheduleRequest.getScheduleEndTime());
        assertEquals(123L, saveScheduleRequest.getScheduleStartTime());
        assertNotNull(saveScheduleRequest.getStartWorkflowRequest());
        assertEquals("sample_updatedBy", saveScheduleRequest.getUpdatedBy());
        assertEquals("sample_zoneId", saveScheduleRequest.getZoneId());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(saveScheduleRequest);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }

}