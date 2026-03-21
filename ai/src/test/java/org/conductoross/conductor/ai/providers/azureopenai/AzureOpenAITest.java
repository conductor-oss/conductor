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
package org.conductoross.conductor.ai.providers.azureopenai;

import java.util.List;

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.ImageGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;

class AzureOpenAITest {

    private static final String ENV_API_KEY = "AZURE_OPENAI_API_KEY";
    private static final String ENV_ENDPOINT = "AZURE_OPENAI_ENDPOINT";

    @Nested
    class UnitTests {

        private AzureOpenAI azureOpenAI;

        @BeforeEach
        void setUp() {
            AzureOpenAIConfiguration config = new AzureOpenAIConfiguration();
            config.setApiKey("test-api-key");
            config.setBaseURL("https://myresource.openai.azure.com");
            config.setDeploymentName("gpt-4");
            azureOpenAI = new AzureOpenAI(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("azure_openai", azureOpenAI.getModelProvider());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);

            var options = azureOpenAI.getChatOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetImageOptions() {
            ImageGenRequest input = new ImageGenRequest();
            input.setModel("dall-e-3");
            input.setHeight(1024);
            input.setWidth(1024);
            input.setN(1);

            var options = azureOpenAI.getImageOptions(input);

            assertNotNull(options);
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+")
    @EnabledIfEnvironmentVariable(named = ENV_ENDPOINT, matches = ".+")
    class IntegrationTests {

        private AzureOpenAI azureOpenAI;

        @BeforeEach
        void setUp() {
            AzureOpenAIConfiguration config = new AzureOpenAIConfiguration();
            config.setApiKey(System.getenv(ENV_API_KEY));
            config.setBaseURL(System.getenv(ENV_ENDPOINT));
            config.setDeploymentName(
                    System.getenv("AZURE_OPENAI_DEPLOYMENT") != null
                            ? System.getenv("AZURE_OPENAI_DEPLOYMENT")
                            : "gpt-4o-mini");
            azureOpenAI = new AzureOpenAI(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o-mini");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = azureOpenAI.getChatModel();
            var options = azureOpenAI.getChatOptions(input);

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
            request.setModel("text-embedding-ada-002");
            request.setText("Hello world");

            var embeddings = azureOpenAI.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }
    }
}
