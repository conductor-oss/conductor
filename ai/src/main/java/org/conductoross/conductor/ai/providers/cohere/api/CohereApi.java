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
package org.conductoross.conductor.ai.providers.cohere.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Low-level HTTP client for Cohere API v2. Follows Spring AI patterns (similar to AnthropicApi) for
 * potential contribution.
 */
public class CohereApi {

    public static final String DEFAULT_BASE_URL = "https://api.cohere.com";
    public static final String DEFAULT_CHAT_PATH = "/v2/chat";
    public static final String DEFAULT_EMBED_PATH = "/v2/embed";

    private final RestClient restClient;
    private final String chatPath;
    private final String embedPath;

    private CohereApi(
            String baseUrl,
            String apiKey,
            String chatPath,
            String embedPath,
            RestClient.Builder restClientBuilder) {
        this.chatPath = chatPath;
        this.embedPath = embedPath;

        Consumer<HttpHeaders> defaultHeaders =
                headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(apiKey);
                };

        this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Send a chat request to Cohere API. */
    public ResponseEntity<ChatCompletionResponse> chat(ChatCompletionRequest request) {
        return this.restClient
                .post()
                .uri(this.chatPath)
                .body(request)
                .retrieve()
                .toEntity(ChatCompletionResponse.class);
    }

    /** Send an embedding request to Cohere API. */
    public ResponseEntity<EmbeddingResponse> embed(EmbeddingRequest request) {
        return this.restClient
                .post()
                .uri(this.embedPath)
                .body(request)
                .retrieve()
                .toEntity(EmbeddingResponse.class);
    }

    // ========================================================================
    // Request/Response Records
    // ========================================================================

    /** Chat message with role and content. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(
            @JsonProperty("role") String role, @JsonProperty("content") String content) {

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }
    }

    /** Chat completion request for Cohere v2 API. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<ChatMessage> messages,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("p") Double topP,
            @JsonProperty("k") Integer topK,
            @JsonProperty("frequency_penalty") Double frequencyPenalty,
            @JsonProperty("presence_penalty") Double presencePenalty,
            @JsonProperty("stop_sequences") List<String> stopSequences,
            @JsonProperty("stream") Boolean stream) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private List<ChatMessage> messages;
            private Double temperature;
            private Integer maxTokens;
            private Double topP;
            private Integer topK;
            private Double frequencyPenalty;
            private Double presencePenalty;
            private List<String> stopSequences;
            private Boolean stream;

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder messages(List<ChatMessage> messages) {
                this.messages = messages;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }

            public Builder maxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder topP(Double topP) {
                this.topP = topP;
                return this;
            }

            public Builder topK(Integer topK) {
                this.topK = topK;
                return this;
            }

            public Builder frequencyPenalty(Double frequencyPenalty) {
                this.frequencyPenalty = frequencyPenalty;
                return this;
            }

            public Builder presencePenalty(Double presencePenalty) {
                this.presencePenalty = presencePenalty;
                return this;
            }

            public Builder stopSequences(List<String> stopSequences) {
                this.stopSequences = stopSequences;
                return this;
            }

            public Builder stream(Boolean stream) {
                this.stream = stream;
                return this;
            }

            public ChatCompletionRequest build() {
                return new ChatCompletionRequest(
                        model,
                        messages,
                        temperature,
                        maxTokens,
                        topP,
                        topK,
                        frequencyPenalty,
                        presencePenalty,
                        stopSequences,
                        stream);
            }
        }
    }

    /** Chat completion response from Cohere v2 API. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("message") ResponseMessage message,
            @JsonProperty("finish_reason") String finishReason,
            @JsonProperty("usage") Usage usage) {

        public record ResponseMessage(
                @JsonProperty("role") String role,
                @JsonProperty("content") List<ContentBlock> content) {}

        public record ContentBlock(
                @JsonProperty("type") String type, @JsonProperty("text") String text) {}

        public record Usage(
                @JsonProperty("input_tokens") Integer inputTokens,
                @JsonProperty("output_tokens") Integer outputTokens) {}
    }

    /** Embedding request for Cohere v2 API. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingRequest(
            @JsonProperty("model") String model,
            @JsonProperty("texts") List<String> texts,
            @JsonProperty("input_type") String inputType,
            @JsonProperty("output_dimensions") Integer outputDimensions,
            @JsonProperty("truncate") String truncate) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private List<String> texts;
            private String inputType = "search_document";
            private Integer outputDimensions;
            private String truncate;

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder texts(List<String> texts) {
                this.texts = texts;
                return this;
            }

            public Builder inputType(String inputType) {
                this.inputType = inputType;
                return this;
            }

            public Builder outputDimensions(Integer outputDimensions) {
                this.outputDimensions = outputDimensions;
                return this;
            }

            public Builder truncate(String truncate) {
                this.truncate = truncate;
                return this;
            }

            public EmbeddingRequest build() {
                return new EmbeddingRequest(model, texts, inputType, outputDimensions, truncate);
            }
        }
    }

    /** Embedding response from Cohere v2 API. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingResponse(
            @JsonProperty("id") String id,
            @JsonProperty("embeddings") Embeddings embeddings,
            @JsonProperty("texts") List<String> texts,
            @JsonProperty("meta") Map<String, Object> meta) {

        public record Embeddings(@JsonProperty("float") List<List<Float>> floatEmbeddings) {}
    }

    // ========================================================================
    // Builder
    // ========================================================================

    public static class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String apiKey;
        private String chatPath = DEFAULT_CHAT_PATH;
        private String embedPath = DEFAULT_EMBED_PATH;
        private RestClient.Builder restClientBuilder = RestClient.builder();

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder chatPath(String chatPath) {
            this.chatPath = chatPath;
            return this;
        }

        public Builder embedPath(String embedPath) {
            this.embedPath = embedPath;
            return this;
        }

        public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
            this.restClientBuilder = restClientBuilder;
            return this;
        }

        public CohereApi build() {
            Assert.hasText(this.apiKey, "API key must not be empty");
            return new CohereApi(
                    this.baseUrl,
                    this.apiKey,
                    this.chatPath,
                    this.embedPath,
                    this.restClientBuilder);
        }
    }
}
