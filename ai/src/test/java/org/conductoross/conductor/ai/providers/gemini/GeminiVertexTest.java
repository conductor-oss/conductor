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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.List;

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;

class GeminiVertexTest {

    private static final String ENV_PROJECT_ID = "GOOGLE_CLOUD_PROJECT";
    private static final String ENV_LOCATION = "GOOGLE_CLOUD_LOCATION";

    @Nested
    class UnitTests {

        private GeminiVertex geminiVertex;

        @BeforeEach
        void setUp() {
            GeminiVertexConfiguration config = new GeminiVertexConfiguration();
            config.setProjectId("test-project");
            config.setLocation("us-central1");
            geminiVertex = new GeminiVertex(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("vertex_ai", geminiVertex.getModelProvider());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-1.5-flash");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);
            input.setTopP(0.9);
            input.setTopK(40);

            var options = geminiVertex.getChatOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetChatOptions_withGoogleSearchRetrieval() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-1.5-pro");
            input.setMaxTokens(2000);
            input.setGoogleSearchRetrieval(true);

            var options = geminiVertex.getChatOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetChatModel_createsModel() {
            var chatModel = geminiVertex.getChatModel();
            assertNotNull(chatModel);
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_PROJECT_ID, matches = ".+")
    class IntegrationTests {

        private GeminiVertex geminiVertex;

        @BeforeEach
        void setUp() {
            GeminiVertexConfiguration config = new GeminiVertexConfiguration();
            config.setProjectId(System.getenv(ENV_PROJECT_ID));
            config.setLocation(
                    System.getenv(ENV_LOCATION) != null
                            ? System.getenv(ENV_LOCATION)
                            : "us-central1");
            geminiVertex = new GeminiVertex(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-1.5-flash");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = geminiVertex.getChatModel();
            var options = geminiVertex.getChatOptions(input);

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
            request.setModel("text-embedding-004");
            request.setText("Hello world");

            var embeddings = geminiVertex.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }
    }
}
