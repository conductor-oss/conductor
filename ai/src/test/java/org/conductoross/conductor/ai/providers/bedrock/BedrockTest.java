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
package org.conductoross.conductor.ai.providers.bedrock;

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

class BedrockTest {

    private static final String ENV_ACCESS_KEY = "AWS_ACCESS_KEY_ID";
    private static final String ENV_SECRET_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_BEARER_TOKEN = "AWS_BEARER_TOKEN_BEDROCK";

    @Nested
    class UnitTests {

        private Bedrock bedrock;

        @BeforeEach
        void setUp() {
            BedrockConfiguration config = new BedrockConfiguration();
            config.setAccessKey("test-access-key");
            config.setSecretKey("test-secret-key");
            config.setRegion("us-east-1");
            bedrock = new Bedrock(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("bedrock", bedrock.getModelProvider());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("anthropic.claude-3-haiku-20240307-v1:0");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);

            var options = bedrock.getChatOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetChatModel_createsModel() {
            var chatModel = bedrock.getChatModel();
            assertNotNull(chatModel);
        }

        @Test
        void testGetImageModel_throwsUnsupportedException() {
            assertThrows(UnsupportedOperationException.class, () -> bedrock.getImageModel());
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_ACCESS_KEY, matches = ".+")
    @EnabledIfEnvironmentVariable(named = ENV_SECRET_KEY, matches = ".+")
    class IntegrationTests {

        private Bedrock bedrock;

        @BeforeEach
        void setUp() {
            BedrockConfiguration config = new BedrockConfiguration();
            config.setAccessKey(System.getenv(ENV_ACCESS_KEY));
            config.setSecretKey(System.getenv(ENV_SECRET_KEY));
            config.setRegion(
                    System.getenv("AWS_REGION") != null
                            ? System.getenv("AWS_REGION")
                            : "us-east-1");
            bedrock = new Bedrock(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("anthropic.claude-3-haiku-20240307-v1:0");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = bedrock.getChatModel();
            var options = bedrock.getChatOptions(input);

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
            request.setModel("amazon.titan-embed-text-v2:0");
            request.setText("Hello world");

            var embeddings = bedrock.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }
    }

    /**
     * Live tests using Bedrock API-key (bearer token) auth — the dedicated {@code
     * AWS_BEARER_TOKEN_BEDROCK} credential, matching the server's {@code
     * conductor.ai.bedrock.bearerToken} binding. Separate from {@link IntegrationTests} because the
     * generic AWS access keys in CI are provisioned for artifact publishing and may not carry
     * Bedrock invoke permissions.
     */
    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_BEARER_TOKEN, matches = ".+")
    class BearerIntegrationTests {

        private Bedrock bedrock;

        @BeforeEach
        void setUp() {
            BedrockConfiguration config = new BedrockConfiguration();
            config.setBearerToken(System.getenv(ENV_BEARER_TOKEN));
            config.setRegion(
                    System.getenv("AWS_REGION") != null
                            ? System.getenv("AWS_REGION")
                            : "us-east-1");
            bedrock = new Bedrock(config);
        }

        @Test
        void testChatCompletionWithImageMedia() throws Exception {
            // Live lock for Bedrock media input (Spring AI Converse maps Media to image
            // blocks). The image embeds a machine-unguessable token, so a correct
            // transcription can only come from the image actually reaching the model.
            byte[] png =
                    Objects.requireNonNull(
                                    getClass().getResourceAsStream("/media/melon7391.png"),
                                    "test asset /media/melon7391.png missing")
                            .readAllBytes();

            ChatCompletion input = new ChatCompletion();
            // Inference-profile id: bare model ids are rejected for on-demand throughput
            // ("Invocation of model ID ... isn't supported. Retry your request with the
            // ID or ARN of an inference profile").
            input.setModel("us.anthropic.claude-3-haiku-20240307-v1:0");
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
                    bedrock.getChatModel()
                            .call(new Prompt(List.of(userMsg), bedrock.getChatOptions(input)));

            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(
                    text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "").contains("MELON7391"),
                    "vision model must transcribe the embedded token MELON7391; got: " + text);
        }
    }
}
