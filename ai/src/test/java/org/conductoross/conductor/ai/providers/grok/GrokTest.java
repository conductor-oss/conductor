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
package org.conductoross.conductor.ai.providers.grok;

import java.util.List;

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;

class GrokTest {

    private static final String ENV_API_KEY = "GROK_API_KEY";

    @Nested
    class UnitTests {

        private Grok grok;

        @BeforeEach
        void setUp() {
            GrokAIConfiguration config = new GrokAIConfiguration();
            config.setApiKey("test-api-key");
            grok = new Grok(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("Grok", grok.getModelProvider());
        }

        @Test
        void testGenerateEmbeddings_throwsUnsupportedException() {
            assertThrows(UnsupportedOperationException.class, () -> grok.generateEmbeddings(null));
        }

        @Test
        void testGetImageModel_throwsUnsupportedException() {
            assertThrows(UnsupportedOperationException.class, () -> grok.getImageModel());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("grok-3-mini");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);

            var options = grok.getChatOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetChatModel_createsModel() {
            var chatModel = grok.getChatModel();
            assertNotNull(chatModel);
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+")
    class IntegrationTests {

        private Grok grok;

        @BeforeEach
        void setUp() {
            GrokAIConfiguration config = new GrokAIConfiguration();
            config.setApiKey(System.getenv(ENV_API_KEY));
            grok = new Grok(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("grok-3-mini");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = grok.getChatModel();
            var options = grok.getChatOptions(input);

            Prompt prompt = new Prompt(List.of(new UserMessage("Say hello in one word")), options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertNotNull(response.getResult().getOutput());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }
    }
}
