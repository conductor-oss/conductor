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
package org.conductoross.conductor.ai.providers.cohere;

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

import static org.junit.jupiter.api.Assertions.*;

class CohereAITest {

    private static final String ENV_API_KEY = "COHERE_API_KEY";

    @Nested
    class UnitTests {

        private CohereAI cohereAI;

        @BeforeEach
        void setUp() {
            CohereAIConfiguration config = new CohereAIConfiguration();
            config.setApiKey("test-api-key");
            cohereAI = new CohereAI(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("cohere", cohereAI.getModelProvider());
        }

        @Test
        void testGetImageModel_throwsUnsupportedException() {
            assertThrows(UnsupportedOperationException.class, () -> cohereAI.getImageModel());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("command-a-03-2025");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);

            var options = cohereAI.getChatOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetChatModel_createsModel() {
            var chatModel = cohereAI.getChatModel();
            assertNotNull(chatModel);
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+")
    class IntegrationTests {

        private CohereAI cohereAI;

        @BeforeEach
        void setUp() {
            CohereAIConfiguration config = new CohereAIConfiguration();
            config.setApiKey(System.getenv(ENV_API_KEY));
            cohereAI = new CohereAI(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("command-a-03-2025");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = cohereAI.getChatModel();
            var options = cohereAI.getChatOptions(input);

            Prompt prompt = new Prompt(List.of(new UserMessage("Say hello in one word")), options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertNotNull(response.getResult().getOutput());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }

        @Test
        void testChatCompletionWithImageMedia() throws Exception {
            // Live regression for the media fix: a vision model must actually see the
            // image bytes, forwarded as an image_url data-URI content part. The image
            // embeds a machine-unguessable token, so a correct transcription can only
            // come from the image — pre-fix the media was dropped and the model could
            // not produce it.
            byte[] png =
                    Objects.requireNonNull(
                                    getClass().getResourceAsStream("/media/melon7391.png"),
                                    "test asset /media/melon7391.png missing")
                            .readAllBytes();

            ChatCompletion input = new ChatCompletion();
            input.setModel("command-a-vision-07-2025");
            input.setMaxTokens(50);

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
                    cohereAI.getChatModel()
                            .call(new Prompt(List.of(userMsg), cohereAI.getChatOptions(input)));

            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(
                    text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "").contains("MELON7391"),
                    "vision model must transcribe the embedded token MELON7391; got: " + text);
        }

        @Test
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setModel("embed-english-v3.0");
            request.setText("Hello world");

            var embeddings = cohereAI.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }
    }
}
