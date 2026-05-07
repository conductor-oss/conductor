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
package org.conductoross.conductor.ai.providers.openai;

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

class OpenAITest {

    private static final String ENV_API_KEY = "OPENAI_API_KEY";

    @Nested
    class UnitTests {

        private OpenAI openAI;

        @BeforeEach
        void setUp() {
            OpenAIConfiguration config = new OpenAIConfiguration();
            config.setApiKey("test-api-key");
            openAI = new OpenAI(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("openai", openAI.getModelProvider());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o-mini");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);
            input.setTopP(0.9);

            var options = openAI.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(OpenAIResponsesChatOptions.class, options);
            assertEquals("gpt-4o-mini", options.getModel());
        }

        @Test
        void testGetChatOptions_withStopWords() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o");
            input.setMaxTokens(500);
            input.setStopWords(List.of("STOP", "END"));

            var options = openAI.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(OpenAIResponsesChatOptions.class, options);
        }

        @Test
        void testGetChatOptions_withPreviousResponseId() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o");
            input.setPreviousResponseId("resp_abc123");

            var options = openAI.getChatOptions(input);

            assertInstanceOf(OpenAIResponsesChatOptions.class, options);
            OpenAIResponsesChatOptions responsesOpts = (OpenAIResponsesChatOptions) options;
            assertEquals("resp_abc123", responsesOpts.getPreviousResponseId());
        }

        @Test
        void testGetChatOptions_reasoningModelDisablesTemperature() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("o3");
            input.setTemperature(0.7);
            input.setTopP(0.9);
            input.setReasoningEffort("medium");

            var options = openAI.getChatOptions(input);
            assertInstanceOf(OpenAIResponsesChatOptions.class, options);
            OpenAIResponsesChatOptions responsesOpts = (OpenAIResponsesChatOptions) options;
            assertNull(responsesOpts.getTemperature());
            assertNull(responsesOpts.getTopP());
            assertEquals("medium", responsesOpts.getReasoningEffort());
        }

        @Test
        void testGetChatOptions_withWebSearch() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o");
            input.setMaxTokens(500);
            input.setWebSearch(true);

            var options = openAI.getChatOptions(input);

            assertInstanceOf(OpenAIResponsesChatOptions.class, options);
            OpenAIResponsesChatOptions responsesOpts = (OpenAIResponsesChatOptions) options;
            assertNotNull(responsesOpts.getResponsesApiTools());
            assertFalse(responsesOpts.getResponsesApiTools().isEmpty());
            assertEquals("web_search", responsesOpts.getResponsesApiTools().getFirst().type());
        }

        @Test
        void testGetChatOptions_withCodeInterpreter() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o");
            input.setMaxTokens(500);
            input.setCodeInterpreter(true);

            var options = openAI.getChatOptions(input);

            assertInstanceOf(OpenAIResponsesChatOptions.class, options);
            OpenAIResponsesChatOptions responsesOpts = (OpenAIResponsesChatOptions) options;
            assertNotNull(responsesOpts.getResponsesApiTools());
            assertEquals(
                    "code_interpreter", responsesOpts.getResponsesApiTools().getFirst().type());
        }

        @Test
        void testGetChatOptions_withFileSearch() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o");
            input.setMaxTokens(500);
            input.setFileSearchVectorStoreIds(List.of("vs_abc123"));

            var options = openAI.getChatOptions(input);

            assertInstanceOf(OpenAIResponsesChatOptions.class, options);
            OpenAIResponsesChatOptions responsesOpts = (OpenAIResponsesChatOptions) options;
            assertNotNull(responsesOpts.getResponsesApiTools());
            assertEquals("file_search", responsesOpts.getResponsesApiTools().getFirst().type());
        }

        @Test
        void testGetImageOptions() {
            ImageGenRequest input = new ImageGenRequest();
            input.setModel("dall-e-3");
            input.setHeight(1024);
            input.setWidth(1024);
            input.setN(1);
            input.setStyle("vivid");

            var options = openAI.getImageOptions(input);

            assertNotNull(options);
        }

        @Test
        void testGetChatModel_createsModel() {
            var chatModel = openAI.getChatModel();
            assertNotNull(chatModel);
            assertInstanceOf(OpenAIResponsesChatModel.class, chatModel);
        }

        @Test
        void testGetImageModel_createsModel() {
            var imageModel = openAI.getImageModel();
            assertNotNull(imageModel);
            assertInstanceOf(OpenAIHttpImageModel.class, imageModel);
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+")
    class IntegrationTests {

        private OpenAI openAI;

        @BeforeEach
        void setUp() {
            OpenAIConfiguration config = new OpenAIConfiguration();
            config.setApiKey(System.getenv(ENV_API_KEY));
            openAI = new OpenAI(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o-mini");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = openAI.getChatModel();
            var options = openAI.getChatOptions(input);

            Prompt prompt = new Prompt(List.of(new UserMessage("Say hello in one word")), options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertNotNull(response.getResult().getOutput());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "OPENAI_CODEX_MODEL", matches = ".+")
        void testChatCompletion_withCodexModel() {
            String codexModel =
                    System.getenv("OPENAI_CODEX_MODEL") != null
                            ? System.getenv("OPENAI_CODEX_MODEL")
                            : "codex-mini-latest";
            ChatCompletion input = new ChatCompletion();
            input.setModel(codexModel);
            input.setMaxTokens(200);

            var chatModel = openAI.getChatModel();
            var options = openAI.getChatOptions(input);

            Prompt prompt =
                    new Prompt(
                            List.of(new UserMessage("Write a hello world function in Python")),
                            options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }

        @Test
        void testChatCompletion_withWebSearch() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o-mini");
            input.setMaxTokens(200);
            input.setTemperature(0.0);
            input.setWebSearch(true);

            var chatModel = openAI.getChatModel();
            var options = openAI.getChatOptions(input);

            Prompt prompt =
                    new Prompt(
                            List.of(
                                    new UserMessage(
                                            "What is the current weather in San Francisco?")),
                            options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }

        @Test
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setModel("text-embedding-3-small");
            request.setText("Hello world");

            var embeddings = openAI.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }
    }
}
