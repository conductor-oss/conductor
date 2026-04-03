/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.integration;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.providers.anthropic.Anthropic;
import org.conductoross.conductor.ai.providers.anthropic.AnthropicConfiguration;
import org.conductoross.conductor.ai.providers.azureopenai.AzureOpenAI;
import org.conductoross.conductor.ai.providers.azureopenai.AzureOpenAIConfiguration;
import org.conductoross.conductor.ai.providers.bedrock.Bedrock;
import org.conductoross.conductor.ai.providers.bedrock.BedrockConfiguration;
import org.conductoross.conductor.ai.providers.cohere.CohereAI;
import org.conductoross.conductor.ai.providers.cohere.CohereAIConfiguration;
import org.conductoross.conductor.ai.providers.gemini.GeminiVertex;
import org.conductoross.conductor.ai.providers.gemini.GeminiVertexConfiguration;
import org.conductoross.conductor.ai.providers.grok.Grok;
import org.conductoross.conductor.ai.providers.grok.GrokAIConfiguration;
import org.conductoross.conductor.ai.providers.mistral.MistralAI;
import org.conductoross.conductor.ai.providers.mistral.MistralAIConfiguration;
import org.conductoross.conductor.ai.providers.ollama.Ollama;
import org.conductoross.conductor.ai.providers.ollama.OllamaConfiguration;
import org.conductoross.conductor.ai.providers.openai.OpenAI;
import org.conductoross.conductor.ai.providers.openai.OpenAIConfiguration;
import org.conductoross.conductor.ai.providers.perplexity.PerplexityAI;
import org.conductoross.conductor.ai.providers.perplexity.PerplexityAIConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for all AI model providers.
 *
 * <p>Before running these tests, set the required environment variables:
 *
 * <pre>
 * source ai/src/test/resources/ai-test-env.sh
 * </pre>
 *
 * <p>Tests will be skipped automatically if the required API keys are not set.
 */
public class AIModelIntegrationTest {

    private static final String TEST_PROMPT = "What is 2 + 2? Reply with just the number.";
    private static final String EMBEDDING_TEXT =
            "Hello, world! This is a test sentence for embeddings.";
    private static final String AUDIO_TEXT = "Hello, this is a test of text to speech.";

    // ========================================================================
    // Environment variable helpers
    // ========================================================================

    static boolean isOpenAIConfigured() {
        String key = System.getenv("OPENAI_API_KEY");
        return StringUtils.isNotBlank(key) && !key.equals("your-openai-api-key");
    }

    static boolean isAnthropicConfigured() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return StringUtils.isNotBlank(key) && !key.equals("your-anthropic-api-key");
    }

    static boolean isGeminiConfigured() {
        String projectId = System.getenv("GOOGLE_PROJECT_ID");
        String creds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        String vertexCreds = System.getenv("VERTEX_AI_CREDENTIALS");
        return StringUtils.isNotBlank(projectId)
                && !projectId.equals("your-gcp-project-id")
                && (StringUtils.isNotBlank(creds) || StringUtils.isNotBlank(vertexCreds));
    }

    static boolean isMistralConfigured() {
        String key = System.getenv("MISTRAL_API_KEY");
        return StringUtils.isNotBlank(key) && !key.equals("your-mistral-api-key");
    }

    static boolean isOllamaConfigured() {
        String url = System.getenv("OLLAMA_BASE_URL");
        return StringUtils.isNotBlank(url);
    }

    static boolean isGrokConfigured() {
        String key = System.getenv("GROK_API_KEY");
        return StringUtils.isNotBlank(key) && !key.equals("your-grok-api-key");
    }

    static boolean isCohereConfigured() {
        String key = System.getenv("COHERE_API_KEY");
        return StringUtils.isNotBlank(key) && !key.equals("your-cohere-api-key");
    }

    static boolean isAzureOpenAIConfigured() {
        String key = System.getenv("AZURE_OPENAI_API_KEY");
        String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
        return StringUtils.isNotBlank(key)
                && !key.equals("your-azure-openai-api-key")
                && StringUtils.isNotBlank(endpoint);
    }

    static boolean isBedrockConfigured() {
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        return StringUtils.isNotBlank(accessKey)
                && !accessKey.equals("your-aws-access-key")
                && StringUtils.isNotBlank(secretKey);
    }

    static boolean isPerplexityConfigured() {
        String key = System.getenv("PERPLEXITY_API_KEY");
        return StringUtils.isNotBlank(key) && !key.equals("your-perplexity-api-key");
    }

    // ========================================================================
    // OpenAI Tests
    // ========================================================================

    @Nested
    @DisplayName("OpenAI Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isOpenAIConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OpenAITests {

        private OpenAI openAI;

        @BeforeAll
        void setup() {
            OpenAIConfiguration config = new OpenAIConfiguration();
            config.setApiKey(System.getenv("OPENAI_API_KEY"));
            config.setBaseURL(System.getenv("OPENAI_BASE_URL"));
            openAI = new OpenAI(config);
        }

        @Test
        @DisplayName("Chat completion with GPT-4o-mini")
        void testChatCompletion() {
            ChatModel chatModel = openAI.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("gpt-4o-mini");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = openAI.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Image generation with GPT Image")
        void testImageGeneration() {
            ImageModel imageModel = openAI.getImageModel();
            assertNotNull(imageModel);

            // Use OpenAiImageOptions to set model and parameters
            OpenAiImageOptions imageOptions =
                    OpenAiImageOptions.builder()
                            .model("dall-e-3")
                            .quality("standard")
                            .height(1024)
                            .width(1024)
                            .build();

            ImagePrompt prompt =
                    new ImagePrompt("A simple red circle on white background", imageOptions);
            ImageResponse response = imageModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResults());
            assertFalse(response.getResults().isEmpty());

            var output = response.getResult().getOutput();
            assertTrue(
                    output.getUrl() != null || output.getB64Json() != null,
                    "Expected image URL or base64 data");
        }

        @Test
        @DisplayName("Audio generation with TTS")
        void testAudioGeneration() {
            AudioGenRequest request =
                    AudioGenRequest.builder()
                            .text(AUDIO_TEXT)
                            .voice("alloy")
                            .speed(1.0)
                            .responseFormat("mp3")
                            .build();
            request.setModel("tts-1");

            LLMResponse response = openAI.generateAudio(request);

            assertNotNull(response);
            assertNotNull(response.getMedia());
            assertFalse(response.getMedia().isEmpty());

            var media = response.getMedia().get(0);
            assertNotNull(media.getData());
            assertTrue(media.getData().length > 0, "Expected audio data");
        }

        @Test
        @DisplayName("Embeddings generation")
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setText(EMBEDDING_TEXT);
            request.setModel("text-embedding-3-small");

            List<Float> embeddings = openAI.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
            assertEquals(1536, embeddings.size(), "Expected 1536 dimensions");
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("openai", openAI.getModelProvider());
        }
    }

    // ========================================================================
    // Anthropic Tests
    // ========================================================================

    @Nested
    @DisplayName("Anthropic (Claude) Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isAnthropicConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AnthropicTests {

        private Anthropic anthropic;

        @BeforeAll
        void setup() {
            AnthropicConfiguration config = new AnthropicConfiguration();
            config.setApiKey(System.getenv("ANTHROPIC_API_KEY"));
            config.setBaseURL(System.getenv("ANTHROPIC_BASE_URL"));
            anthropic = new Anthropic(config);
        }

        @Test
        @DisplayName("Chat completion with Claude Haiku (fast model)")
        void testChatCompletionHaiku() {
            ChatModel chatModel = anthropic.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-3-5-haiku-latest");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = anthropic.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Chat with temperature=0 (deterministic)")
        void testDeterministicTemperature() {
            ChatModel chatModel = anthropic.getChatModel();

            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-3-5-haiku-latest");
            input.setMaxTokens(20);
            input.setTemperature(0.0); // Deterministic

            var chatOptions = anthropic.getChatOptions(input);
            Prompt prompt = new Prompt("Say 'hello world' exactly", chatOptions);

            ChatResponse response = chatModel.call(prompt);
            String text1 = response.getResult().getOutput().getText().toLowerCase();

            // Make same call again - should be deterministic
            response = chatModel.call(prompt);
            String text2 = response.getResult().getOutput().getText().toLowerCase();

            assertTrue(text1.contains("hello") && text1.contains("world"));
            assertTrue(text2.contains("hello") && text2.contains("world"));
        }

        @Test
        @DisplayName("Thinking mode - Claude extended thinking")
        void testThinkingMode() {
            ChatModel chatModel = anthropic.getChatModel();

            ChatCompletion input = new ChatCompletion();
            input.setModel("claude-sonnet-4-5"); // Sonnet 4.5 supports thinking
            input.setMaxTokens(16000); // Thinking requires larger token limit
            input.setThinkingTokenLimit(8000); // Enable thinking mode
            // Note: Temperature is forced to 1.0 when thinking is enabled

            var chatOptions = anthropic.getChatOptions(input);
            Prompt prompt =
                    new Prompt("What is 15 * 23? Think through this step by step.", chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("345"), "Expected 345 in response, got: " + text);
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("anthropic", anthropic.getModelProvider());
        }
    }

    // ========================================================================
    // Gemini / Vertex AI Tests
    // ========================================================================

    @Nested
    @DisplayName("Gemini (Vertex AI) Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isGeminiConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GeminiTests {

        private GeminiVertex gemini;

        @BeforeAll
        void setup() throws Exception {
            GeminiVertexConfiguration config = new GeminiVertexConfiguration();
            config.setProjectId(System.getenv("GOOGLE_PROJECT_ID"));
            config.setLocation(System.getenv("GOOGLE_LOCATION"));

            // Try to load credentials from VERTEX_AI_CREDENTIALS env var (JSON string)
            String vertexCreds = System.getenv("VERTEX_AI_CREDENTIALS");
            if (StringUtils.isNotBlank(vertexCreds)) {
                var credentials =
                        com.google.auth.oauth2.GoogleCredentials.fromStream(
                                new java.io.ByteArrayInputStream(
                                        vertexCreds.getBytes(
                                                java.nio.charset.StandardCharsets.UTF_8)));
                config.setGoogleCredentials(credentials);
            }

            gemini = new GeminiVertex(config);
        }

        @Test
        @DisplayName("Chat completion with Gemini Flash")
        void testChatCompletionFlash() {
            ChatModel chatModel = gemini.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("gemini-2.0-flash-001");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = gemini.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Embeddings generation")
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setText(EMBEDDING_TEXT);
            request.setModel("text-embedding-005");

            List<Float> embeddings = gemini.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
            assertEquals(768, embeddings.size(), "Expected 768 dimensions");
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("vertex_ai", gemini.getModelProvider());
        }
    }

    // ========================================================================
    // Mistral AI Tests
    // ========================================================================

    @Nested
    @DisplayName("Mistral AI Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isMistralConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class MistralTests {

        private MistralAI mistral;

        @BeforeAll
        void setup() {
            MistralAIConfiguration config = new MistralAIConfiguration();
            config.setApiKey(System.getenv("MISTRAL_API_KEY"));
            config.setBaseURL(System.getenv("MISTRAL_BASE_URL"));
            mistral = new MistralAI(config);
        }

        @Test
        @DisplayName("Chat completion with Mistral")
        void testChatCompletion() {
            ChatModel chatModel = mistral.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("mistral-small-latest");
            input.setMaxTokens(100);
            input.setTemperature(0.0);

            var chatOptions = mistral.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("JSON output format")
        void testJsonOutputFormat() {
            ChatModel chatModel = mistral.getChatModel();

            ChatCompletion input = new ChatCompletion();
            input.setModel("mistral-small-latest");
            input.setMaxTokens(100);
            input.setTemperature(0.0);
            input.setJsonOutput(true); // Request JSON output

            var chatOptions = mistral.getChatOptions(input);
            Prompt prompt =
                    new Prompt(
                            "Return a JSON object with 'name': 'test' and 'value': 42. Only return JSON, no explanation.",
                            chatOptions);

            ChatResponse response = chatModel.call(prompt);

            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            // Should be valid JSON-like structure
            assertTrue(
                    text.contains("\"name\"") || text.contains("name"),
                    "Expected JSON with 'name' field, got: " + text);
            assertTrue(
                    text.contains("42") || text.contains("\"42\""),
                    "Expected JSON with value 42, got: " + text);
        }

        @Test
        @DisplayName("Chat with topP parameter")
        void testTopPParameter() {
            ChatModel chatModel = mistral.getChatModel();

            ChatCompletion input = new ChatCompletion();
            input.setModel("mistral-small-latest");
            input.setMaxTokens(50);
            input.setTemperature(0.5);
            input.setTopP(0.9); // Nucleus sampling

            var chatOptions = mistral.getChatOptions(input);
            Prompt prompt =
                    new Prompt("What is the capital of France? Reply in one word.", chatOptions);

            ChatResponse response = chatModel.call(prompt);

            String text = response.getResult().getOutput().getText().toLowerCase();
            assertTrue(text.contains("paris"), "Expected Paris, got: " + text);
        }

        @Test
        @DisplayName("Embeddings generation")
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setText(EMBEDDING_TEXT);
            request.setModel("mistral-embed");

            List<Float> embeddings = mistral.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
            assertEquals(1024, embeddings.size(), "Expected 1024 dimensions");
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("mistral", mistral.getModelProvider());
        }
    }

    // ========================================================================
    // Ollama Tests
    // ========================================================================

    @Nested
    @DisplayName("Ollama Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isOllamaConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OllamaTests {

        private Ollama ollama;

        @BeforeAll
        void setup() {
            OllamaConfiguration config = new OllamaConfiguration();
            config.setBaseURL(System.getenv("OLLAMA_BASE_URL"));
            config.setAuthHeaderName(System.getenv("OLLAMA_AUTH_HEADER_NAME"));
            config.setAuthHeader(System.getenv("OLLAMA_AUTH_HEADER"));
            ollama = new Ollama(config);
        }

        @Test
        @DisplayName("Chat completion with Llama")
        void testChatCompletion() {
            ChatModel chatModel = ollama.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("llama3.2");
            input.setTemperature(0.0);

            var chatOptions = ollama.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("JSON output format")
        void testJsonOutputFormat() {
            ChatModel chatModel = ollama.getChatModel();

            ChatCompletion input = new ChatCompletion();
            input.setModel("llama3.2");
            input.setTemperature(0.0);
            input.setJsonOutput(true); // Request JSON output

            var chatOptions = ollama.getChatOptions(input);
            Prompt prompt =
                    new Prompt(
                            "Return a JSON object with 'answer': 42. Only return JSON, no explanation.",
                            chatOptions);

            ChatResponse response = chatModel.call(prompt);

            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("42"), "Expected JSON with 42, got: " + text);
        }

        @Test
        @DisplayName("Chat with numPredict (maxTokens) limit")
        void testNumPredictLimit() {
            ChatModel chatModel = ollama.getChatModel();

            ChatCompletion input = new ChatCompletion();
            input.setModel("llama3.2");
            input.setTemperature(0.0);
            input.setMaxTokens(10); // Very short numPredict

            var chatOptions = ollama.getChatOptions(input);
            Prompt prompt = new Prompt("Tell me a very long story about dragons.", chatOptions);

            ChatResponse response = chatModel.call(prompt);

            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            // Response should be truncated due to low token limit
            assertTrue(
                    text.split("\\s+").length <= 30,
                    "Expected short response due to numPredict limit, got: "
                            + text.length()
                            + " chars");
        }

        @Test
        @DisplayName("Embeddings generation")
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setText(EMBEDDING_TEXT);
            request.setModel("nomic-embed-text");

            List<Float> embeddings = ollama.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("ollama", ollama.getModelProvider());
        }
    }

    // ========================================================================
    // Grok (xAI) Tests
    // ========================================================================

    @Nested
    @DisplayName("Grok (xAI) Integration Tests")
    @EnabledIf("org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isGrokConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GrokTests {

        private Grok grok;

        @BeforeAll
        void setup() {
            GrokAIConfiguration config = new GrokAIConfiguration();
            config.setApiKey(System.getenv("GROK_API_KEY"));
            config.setBaseURL(System.getenv("GROK_BASE_URL"));
            grok = new Grok(config);
        }

        @Test
        @DisplayName("Chat completion with Grok")
        void testChatCompletion() {
            ChatModel chatModel = grok.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("grok-3-mini");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = grok.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("Grok", grok.getModelProvider());
        }
    }

    // ========================================================================
    // Cohere Tests
    // ========================================================================

    @Nested
    @DisplayName("Cohere Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isCohereConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    // Cohere v2 API is not OpenAI-compatible - uses different param names (texts vs
    // input, extra_body rejected)
    // Requires native Cohere SDK integration
    class CohereTests {

        private CohereAI cohere;

        @BeforeAll
        void setup() {
            CohereAIConfiguration config = new CohereAIConfiguration();
            config.setApiKey(System.getenv("COHERE_API_KEY"));
            config.setBaseURL(System.getenv("COHERE_BASE_URL"));
            cohere = new CohereAI(config);
        }

        @Test
        @DisplayName("Chat completion with Cohere")
        void testChatCompletion() {
            ChatModel chatModel = cohere.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("command-a-03-2025");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = cohere.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Embeddings generation")
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setText(EMBEDDING_TEXT);
            request.setModel("embed-english-v3.0");

            List<Float> embeddings = cohere.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("cohere", cohere.getModelProvider());
        }
    }

    // ========================================================================
    // Azure OpenAI Tests
    // ========================================================================

    @Nested
    @DisplayName("Azure OpenAI Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isAzureOpenAIConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AzureOpenAITests {

        private AzureOpenAI azureOpenAI;

        @BeforeAll
        void setup() {
            AzureOpenAIConfiguration config = new AzureOpenAIConfiguration();
            config.setApiKey(System.getenv("AZURE_OPENAI_API_KEY"));
            config.setBaseURL(System.getenv("AZURE_OPENAI_ENDPOINT"));
            azureOpenAI = new AzureOpenAI(config);
        }

        @Test
        @DisplayName("Chat completion with Azure OpenAI")
        void testChatCompletion() {
            ChatModel chatModel = azureOpenAI.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            // Use deployment name from env var, or fall back to "gpt-4o-mini"
            String deploymentName = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
            input.setModel(deploymentName != null ? deploymentName : "gpt-4o-mini");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = azureOpenAI.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Embeddings generation with Azure OpenAI")
        void testEmbeddings() {
            EmbeddingGenRequest request = new EmbeddingGenRequest();
            request.setText(EMBEDDING_TEXT);
            // Use deployment name from env var, or fall back to "text-embedding-3-small"
            String embeddingDeploymentName =
                    System.getenv("AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME");
            request.setModel(
                    embeddingDeploymentName != null
                            ? embeddingDeploymentName
                            : "text-embedding-3-small");
            request.setDimensions(1536);

            List<Float> embeddings = azureOpenAI.generateEmbeddings(request);

            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
            assertEquals(1536, embeddings.size(), "Expected 1536 dimensions");
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("azure_openai", azureOpenAI.getModelProvider());
        }
    }

    // ========================================================================
    // AWS Bedrock Tests
    // ========================================================================

    @Nested
    @DisplayName("AWS Bedrock Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isBedrockConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BedrockTests {

        private Bedrock bedrock;

        @BeforeAll
        void setup() {
            BedrockConfiguration config = new BedrockConfiguration();

            // Check for bearer token first (preferred for API key auth)
            String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
            if (StringUtils.isNotBlank(bearerToken)) {
                config.setBearerToken(bearerToken);
            } else {
                // Fall back to access key/secret key
                config.setAccessKey(System.getenv("AWS_ACCESS_KEY_ID"));
                config.setSecretKey(System.getenv("AWS_SECRET_ACCESS_KEY"));
            }

            String region = System.getenv("AWS_REGION");
            if (StringUtils.isNotBlank(region)) {
                config.setRegion(region);
            }
            bedrock = new Bedrock(config);
        }

        @Test
        @DisplayName("Chat completion with Bedrock Claude")
        void testChatCompletion() {
            ChatModel chatModel = bedrock.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            // Use US cross-region inference profile (required for API key auth)
            input.setModel("us.anthropic.claude-haiku-4-5-20251001-v1:0");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = bedrock.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("bedrock", bedrock.getModelProvider());
        }
    }

    // ========================================================================
    // Perplexity Tests
    // ========================================================================

    @Nested
    @DisplayName("Perplexity AI Integration Tests")
    @EnabledIf(
            "org.conductoross.conductor.ai.integration.AIModelIntegrationTest#isPerplexityConfigured")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PerplexityTests {

        private PerplexityAI perplexity;

        @BeforeAll
        void setup() {
            PerplexityAIConfiguration config = new PerplexityAIConfiguration();
            config.setApiKey(System.getenv("PERPLEXITY_API_KEY"));
            config.setBaseURL(System.getenv("PERPLEXITY_BASE_URL"));
            perplexity = new PerplexityAI(config);
        }

        @Test
        @DisplayName("Chat completion with Perplexity")
        void testChatCompletion() {
            ChatModel chatModel = perplexity.getChatModel();
            assertNotNull(chatModel);

            ChatCompletion input = new ChatCompletion();
            input.setModel("sonar");
            input.setMaxTokens(50);
            input.setTemperature(0.0);

            var chatOptions = perplexity.getChatOptions(input);
            Prompt prompt = new Prompt(TEST_PROMPT, chatOptions);

            ChatResponse response = chatModel.call(prompt);

            assertNotNull(response);
            assertNotNull(response.getResult());
            String text = response.getResult().getOutput().getText();
            assertNotNull(text);
            assertTrue(text.contains("4"), "Expected response to contain '4', got: " + text);
        }

        @Test
        @DisplayName("Model provider name")
        void testModelProviderName() {
            assertEquals("perplexity", perplexity.getModelProvider());
        }
    }
}
