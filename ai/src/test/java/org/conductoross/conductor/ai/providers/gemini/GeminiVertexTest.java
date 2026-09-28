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
import java.util.Locale;
import java.util.Objects;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.*;

class GeminiVertexTest {

    private static final String ENV_PROJECT_ID = "GOOGLE_CLOUD_PROJECT";
    private static final String ENV_LOCATION = "GOOGLE_CLOUD_LOCATION";
    private static final String ENV_API_KEY = "GEMINI_API_KEY";

    @Nested
    class UnitTests {

        private GeminiVertex geminiVertex;

        @BeforeEach
        void setUp() {
            GeminiVertexConfiguration config = new GeminiVertexConfiguration();
            config.setProjectId("test-project");
            config.setLocation("us-central1");
            geminiVertex = new GeminiVertex(config, new OkHttpClient());
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
            assertInstanceOf(GeminiChatOptions.class, options);
            GeminiChatOptions opts = (GeminiChatOptions) options;
            assertEquals("gemini-1.5-flash", opts.getModel());
            assertEquals(1000, opts.getMaxTokens());
            assertEquals(0.7, opts.getTemperature());
            assertEquals(0.9, opts.getTopP());
            assertEquals(40, opts.getTopK());
        }

        @Test
        void testGetChatOptions_withGoogleSearchRetrieval() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-1.5-pro");
            input.setMaxTokens(2000);
            input.setGoogleSearchRetrieval(true);

            var options = geminiVertex.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(GeminiChatOptions.class, options);
            GeminiChatOptions opts = (GeminiChatOptions) options;
            assertTrue(opts.isGoogleSearchRetrieval());
        }

        @Test
        void testGetChatOptions_withWebSearchFlag() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-2.5-flash");
            input.setMaxTokens(500);
            input.setWebSearch(true);

            var options = geminiVertex.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(GeminiChatOptions.class, options);
            GeminiChatOptions opts = (GeminiChatOptions) options;
            assertTrue(opts.isGoogleSearchRetrieval());
        }

        @Test
        void testGetChatOptions_withWebSearchFlag_apiKeyPath() {
            GeminiVertexConfiguration apiKeyConfig = new GeminiVertexConfiguration();
            apiKeyConfig.setApiKey("test-api-key");
            GeminiVertex apiKeyGemini = new GeminiVertex(apiKeyConfig, new OkHttpClient());

            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-2.5-flash");
            input.setMaxTokens(500);
            input.setWebSearch(true);

            var options = apiKeyGemini.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(GeminiChatOptions.class, options);
            GeminiChatOptions opts = (GeminiChatOptions) options;
            assertTrue(opts.isGoogleSearchRetrieval());
        }

        @Test
        void testGetChatOptions_withCodeExecution() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-2.5-flash");
            input.setMaxTokens(500);
            input.setCodeInterpreter(true);

            var options = geminiVertex.getChatOptions(input);

            assertInstanceOf(GeminiChatOptions.class, options);
            GeminiChatOptions opts = (GeminiChatOptions) options;
            assertTrue(opts.isCodeExecution());
        }

        @Test
        void testGetChatModel_createsModel() {
            // getChatModel() creates a real GenAI Client which requires either an API key
            // or GCP Application Default Credentials. Skip gracefully when neither is available.
            try {
                var chatModel = geminiVertex.getChatModel();
                assertNotNull(chatModel);
                assertInstanceOf(GeminiChatModel.class, chatModel);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("credentials")) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(
                            false, "Skipping: no GCP credentials available");
                }
                throw e;
            }
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
            geminiVertex = new GeminiVertex(config, new OkHttpClient());
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-2.5-flash");
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

    /**
     * Live tests in API-key (AI Studio / generativelanguage.googleapis.com) mode — the mode the
     * server uses when only {@code conductor.ai.gemini.api-key} (GEMINI_API_KEY) is configured, as
     * opposed to the Vertex mode covered by {@link IntegrationTests}.
     */
    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+")
    class ApiKeyIntegrationTests {

        private GeminiVertex gemini;

        @BeforeEach
        void setUp() {
            GeminiVertexConfiguration config = new GeminiVertexConfiguration();
            config.setApiKey(System.getenv(ENV_API_KEY));
            gemini = new GeminiVertex(config, new OkHttpClient());
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-2.5-flash");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            Prompt prompt =
                    new Prompt(
                            List.of(new UserMessage("Say hello in one word")),
                            gemini.getChatOptions(input));
            var response = gemini.getChatModel().call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }

        @Test
        void testChatCompletionWithImageMedia() throws Exception {
            // Live regression for the media fix (PR #1241): the vision model must actually
            // see the image bytes forwarded by the adapter. The image embeds a
            // machine-unguessable token, so a correct transcription can only come from
            // the image — pre-fix the media was silently dropped.
            byte[] png =
                    Objects.requireNonNull(
                                    getClass().getResourceAsStream("/media/melon7391.png"),
                                    "test asset /media/melon7391.png missing")
                            .readAllBytes();

            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-2.5-flash");
            input.setMaxTokens(100);

            UserMessage userMsg =
                    UserMessage.builder()
                            .text(
                                    "Transcribe the exact text shown in the image. Reply with"
                                            + " only that text and nothing else.")
                            .media(
                                    List.of(
                                            Media.builder()
                                                    .data(png)
                                                    .mimeType(MimeTypeUtils.IMAGE_PNG)
                                                    .build()))
                            .build();

            var response =
                    gemini.getChatModel()
                            .call(new Prompt(List.of(userMsg), gemini.getChatOptions(input)));

            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(
                    text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "").contains("MELON7391"),
                    "vision model must transcribe the embedded token MELON7391; got: " + text);
        }
    }
}
