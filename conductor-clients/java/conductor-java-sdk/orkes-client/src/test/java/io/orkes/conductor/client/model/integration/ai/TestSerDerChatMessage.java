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
package io.orkes.conductor.client.model.integration.ai;

import org.junit.jupiter.api.Test;

import io.orkes.conductor.client.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerChatMessage {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("ChatMessage");
        ChatMessage chatMessage = objectMapper.readValue(SERVER_JSON, ChatMessage.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(chatMessage);
        assertNotNull(chatMessage.getRole());
        assertNotNull(chatMessage.getMessage());

        // Check enum conversion if the role matches an enum value
        try {
            ChatMessage.Actor actorEnum = ChatMessage.Actor.valueOf(chatMessage.getRole());
            assertNotNull(actorEnum, "Actor enum value should be valid");
        } catch (IllegalArgumentException e) {
            // The role might not be one of the enum values, which is allowed
            // since the field is a String that can contain other values
        }

        // 3. Marshall this POJO to JSON again
        String regeneratedJson = objectMapper.writeValueAsString(chatMessage);

        // 4. Compare the JSONs - nothing should be lost
        JsonNode originalJsonNode = objectMapper.readTree(SERVER_JSON);
        JsonNode regeneratedJsonNode = objectMapper.readTree(regeneratedJson);

        assertEquals(originalJsonNode, regeneratedJsonNode,
                "The original and regenerated JSON should be equivalent");

        // Additional verification by deserializing the regenerated JSON
        ChatMessage regeneratedChatMessage = objectMapper.readValue(regeneratedJson, ChatMessage.class);
        assertEquals(chatMessage.getRole(), regeneratedChatMessage.getRole());
        assertEquals(chatMessage.getMessage(), regeneratedChatMessage.getMessage());
    }
}