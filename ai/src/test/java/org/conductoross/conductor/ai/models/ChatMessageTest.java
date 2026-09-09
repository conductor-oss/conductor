/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.models;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    void objectMessageIsSerializedToJson() {
        Map<String, Object> input =
                Map.of(
                        "messages",
                        List.of(Map.of("role", "user", "message", Map.of("randomInt", 42))));

        ChatCompletion chatCompletion = objectMapper.convertValue(input, ChatCompletion.class);

        assertEquals("{\"randomInt\":42}", chatCompletion.getMessages().get(0).getMessage());
    }

    @Test
    void stringMessageIsUnchanged() {
        Map<String, Object> input =
                Map.of("messages", List.of(Map.of("role", "user", "message", "hello")));

        ChatCompletion chatCompletion = objectMapper.convertValue(input, ChatCompletion.class);

        assertEquals("hello", chatCompletion.getMessages().get(0).getMessage());
    }

    @Test
    void listMessageIsSerializedToJson() {
        Map<String, Object> input =
                Map.of("messages", List.of(Map.of("role", "user", "message", List.of(1, 2))));

        ChatCompletion chatCompletion = objectMapper.convertValue(input, ChatCompletion.class);

        assertEquals("[1,2]", chatCompletion.getMessages().get(0).getMessage());
    }

    @Test
    void nullMessageStaysNull() {
        ChatMessage message = new ChatMessage();
        message.setMessage((Object) null);
        assertNull(message.getMessage());
    }
}
