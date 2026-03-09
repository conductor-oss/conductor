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

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;

class BedrockTest {

    private static final String ENV_ACCESS_KEY = "AWS_ACCESS_KEY_ID";
    private static final String ENV_SECRET_KEY = "AWS_SECRET_ACCESS_KEY";

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
}
