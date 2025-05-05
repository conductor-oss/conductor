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

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.events.EventExecution.Status;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSerDerEventExecution {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializeDeserialize() throws IOException {
        // 1. Get the server JSON from the template resolver
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("EventExecution");

        // 2. Deserialize JSON to POJO
        EventExecution eventExecution = objectMapper.readValue(SERVER_JSON, EventExecution.class);

        // 3. Verify all fields are correctly populated
        assertNotNull(eventExecution, "Deserialized object should not be null");

        // Check primitive fields
        assertEquals("sample_id", eventExecution.getId(), "id should match");
        assertEquals("sample_messageId", eventExecution.getMessageId(), "messageId should match");
        assertEquals("sample_name", eventExecution.getName(), "name should match");
        assertEquals("sample_event", eventExecution.getEvent(), "event should match");
        assertEquals(123L, eventExecution.getCreated(), "created timestamp should match");

        // Check enum fields
        assertEquals(Status.IN_PROGRESS, eventExecution.getStatus(), "status enum should match");
        assertEquals(Action.Type.start_workflow, eventExecution.getAction(), "action enum should match");

        // Check map field
        Map<String, Object> output = eventExecution.getOutput();
        assertNotNull(output, "output map should not be null");
        assertEquals(1, output.size(), "output map should have 1 entry");
        assertEquals("sample_value", output.get("sample_key"), "output map key-value should match");

        // 4. Serialize POJO back to JSON
        String serializedJson = objectMapper.writeValueAsString(eventExecution);

        // 5. Compare the original and serialized JSON for equivalence
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson),
                "Serialized JSON should match the original server JSON"
        );
    }
}