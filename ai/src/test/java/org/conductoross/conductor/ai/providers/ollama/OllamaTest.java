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
package org.conductoross.conductor.ai.providers.ollama;

import java.util.List;

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;

// Test is disabled for now until local ollma can be available on git
@Disabled
class OllamaTest {

    private static final String ENV_BASE_URL = "OLLAMA_BASE_URL";

    @Nested
    class UnitTests {

        private Ollama ollama;

        @BeforeEach
        void setUp() {
            OllamaConfiguration config = new OllamaConfiguration();
            config.setBaseURL("http://localhost:11434");
            ollama = new Ollama(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("ollama", ollama.getModelProvider());
        }

        @Test
        void testGetImageModel_throwsUnsupportedException() {
            assertThrows(UnsupportedOperationException.class, () -> ollama.getImageModel());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("llama3");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);

            var options = ollama.getChatOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetChatModel_createsModel() {
            var chatModel = ollama.getChatModel();
            assertNotNull(chatModel);
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_BASE_URL, matches = ".+")
    class IntegrationTests {

        private Ollama ollama;

        @BeforeEach
        void setUp() {
            OllamaConfiguration config = new OllamaConfiguration();
            config.setBaseURL(System.getenv(ENV_BASE_URL));
            ollama = new Ollama(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("llama3");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = ollama.getChatModel();
            var options = ollama.getChatOptions(input);

            Prompt prompt = new Prompt(List.of(new UserMessage("Say hello in one word")), options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertNotNull(response.getResult().getOutput());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }

        @Test
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setModel("nomic-embed-text");
            request.setText("Hello world");

            var embeddings = ollama.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }
    }
}
