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

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;


public class TestSerDerChatCompletion {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("ChatCompletion");
        ObjectMapper objectMapper = new ObjectMapper();
        ChatCompletion chatCompletion = objectMapper.readValue(SERVER_JSON, ChatCompletion.class);
        assertEquals("sample_instructions", chatCompletion.getInstructions());
        assertTrue(chatCompletion.isJsonOutput());
        assertNotNull(chatCompletion.getMessages());
        assertEquals(1, chatCompletion.getMessages().size());
        ChatMessage message = chatCompletion.getMessages().get(0);
        assertEquals("sample_role", message.getRole());
        assertEquals("sample_message", message.getMessage());

        String serializedJson = objectMapper.writeValueAsString(chatCompletion);
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}
