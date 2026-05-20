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
package org.conductoross.conductor.ai.providers.anthropic;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicTest {

    private static final String ENV_API_KEY = "ANTHROPIC_API_KEY";

    @Nested
    class UnitTests {

        private Anthropic anthropic;

        @BeforeEach
        void setUp() {
            AnthropicConfiguration config = new AnthropicConfiguration();
            config.setApiKey("test-api-key");
            anthropic = new Anthropic(config);
        }

        @Test
        void testGetModelProvider() {
            assertEquals("anthropic", anthropic.getModelProvider());
        }

        @Test
        void testGenerateEmbeddings_throwsUnsupportedException() {
            assertThrows(
                    UnsupportedOperationException.class, () -> anthropic.generateEmbeddings(null));
        }

        @Test
        void testGetImageModel_throwsUnsupportedException() {
            assertThrows(UnsupportedOperationException.class, () -> anthropic.getImageModel());
        }

        @Test
        void testGetChatOptions_basicOptions() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-6");
            input.setMaxTokens(1000);
            input.setTemperature(0.7);
            input.setTopP(0.9);

            var options = anthropic.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(AnthropicChatOptions.class, options);
            AnthropicChatOptions opts = (AnthropicChatOptions) options;
            assertEquals("claude-sonnet-4-6", opts.getModel());
            assertEquals(1000, opts.getMaxTokens());
            assertEquals(0.7, opts.getTemperature());
            assertEquals(0.9, opts.getTopP());
        }

        @Test
        void testGetChatOptions_withThinkingMode() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-20250514");
            input.setMaxTokens(16000);
            input.setTemperature(0.5);
            input.setThinkingTokenLimit(10000);

            var options = anthropic.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(AnthropicChatOptions.class, options);
            AnthropicChatOptions opts = (AnthropicChatOptions) options;
            assertEquals(10000, opts.getThinkingBudgetTokens());
            assertEquals(1.0, opts.getTemperature()); // Forced to 1.0 for thinking
        }

        @Test
        void testGetChatOptions_withTools() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-6");
            input.setMaxTokens(1000);

            ToolSpec tool = new ToolSpec();
            tool.setName("test_tool");
            tool.setDescription("A test tool");
            tool.setInputSchema(Map.of("type", "object", "properties", Map.of()));
            input.setTools(List.of(tool));

            var options = anthropic.getChatOptions(input);

            assertNotNull(options);
            assertInstanceOf(AnthropicChatOptions.class, options);
            AnthropicChatOptions opts = (AnthropicChatOptions) options;
            assertNotNull(opts.getTools());
            assertEquals(1, opts.getTools().size());
            assertEquals("test_tool", opts.getTools().getFirst().name());
            assertEquals("custom", opts.getTools().getFirst().type());
        }

        @Test
        void testGetChatModel_createsModel() {
            var chatModel = anthropic.getChatModel();
            assertNotNull(chatModel);
            assertInstanceOf(AnthropicChatModel.class, chatModel);
        }

        @Test
        void testGetChatOptions_withWebSearch() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-6");
            input.setMaxTokens(500);
            input.setWebSearch(true);

            var options = anthropic.getChatOptions(input);

            assertInstanceOf(AnthropicChatOptions.class, options);
            AnthropicChatOptions opts = (AnthropicChatOptions) options;
            assertNotNull(opts.getTools());
            assertTrue(
                    opts.getTools().stream()
                            .anyMatch(
                                    t ->
                                            "web_search_20250305".equals(t.type())
                                                    && "web_search".equals(t.name())));
        }

        @Test
        void testGetChatOptions_withCodeExecution() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-6");
            input.setMaxTokens(500);
            input.setCodeInterpreter(true);

            var options = anthropic.getChatOptions(input);

            assertInstanceOf(AnthropicChatOptions.class, options);
            AnthropicChatOptions opts = (AnthropicChatOptions) options;
            assertNotNull(opts.getTools());
            assertTrue(
                    opts.getTools().stream()
                            .anyMatch(
                                    t ->
                                            "code_execution_20250825".equals(t.type())
                                                    && "code_execution".equals(t.name())));
        }

        @Test
        void testGetChatOptions_withBothBuiltInTools() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-6");
            input.setMaxTokens(500);
            input.setWebSearch(true);
            input.setCodeInterpreter(true);

            var options = anthropic.getChatOptions(input);

            assertInstanceOf(AnthropicChatOptions.class, options);
            AnthropicChatOptions opts = (AnthropicChatOptions) options;
            assertNotNull(opts.getTools());
            assertEquals(2, opts.getTools().size());
        }

        @Test
        void testGetChatOptions_noTools() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-6");
            input.setMaxTokens(500);

            var options = anthropic.getChatOptions(input);

            assertInstanceOf(AnthropicChatOptions.class, options);
            AnthropicChatOptions opts = (AnthropicChatOptions) options;
            assertNull(opts.getTools());
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+")
    class IntegrationTests {

        private Anthropic anthropic;

        @BeforeEach
        void setUp() {
            AnthropicConfiguration config = new AnthropicConfiguration();
            config.setApiKey(System.getenv(ENV_API_KEY));
            anthropic = new Anthropic(config);
        }

        @Test
        void testChatCompletion() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-haiku-4-5");
            input.setMaxTokens(100);
            input.setTemperature(0.7);

            var chatModel = anthropic.getChatModel();
            var options = anthropic.getChatOptions(input);

            Prompt prompt = new Prompt(List.of(new UserMessage("Say hello in one word")), options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            assertNotNull(response.getResult().getOutput());
            assertFalse(response.getResult().getOutput().getText().isEmpty());
        }

        @Test
        void testChatCompletion_withThinking() {
            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-5");
            input.setMaxTokens(16000);
            input.setThinkingTokenLimit(10000);

            var chatModel = anthropic.getChatModel();
            var options = anthropic.getChatOptions(input);

            Prompt prompt =
                    new Prompt(
                            List.of(new UserMessage("What is 2 + 2? Think step by step.")),
                            options);
            var response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
        }
    }
}
