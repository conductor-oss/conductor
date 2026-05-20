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
package org.conductoross.conductor.ai.providers.anthropic.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp REST client for the Anthropic Messages API.
 *
 * <p>Endpoint: POST /v1/messages
 *
 * @see <a href="https://docs.anthropic.com/en/api/messages">Anthropic Messages API</a>
 */
@Slf4j
public class AnthropicMessagesApi {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String DEFAULT_VERSION = "2023-06-01";

    private final String baseUrl;
    private final String apiKey;
    private final String anthropicVersion;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicMessagesApi(String apiKey, String baseUrl, long timeoutSeconds) {
        this(apiKey, baseUrl, null, timeoutSeconds);
    }

    public AnthropicMessagesApi(
            String apiKey, String baseUrl, String anthropicVersion, long timeoutSeconds) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.anthropic.com";
        this.apiKey = apiKey;
        this.anthropicVersion = anthropicVersion != null ? anthropicVersion : DEFAULT_VERSION;
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build();
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    /**
     * Create a message via POST /v1/messages.
     *
     * @param request The message creation request
     * @return The API response
     */
    public MessagesResponse createMessage(MessagesRequest request) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(request);
        log.debug("Anthropic Messages API request: {}", jsonBody);

        Request.Builder httpBuilder =
                new Request.Builder()
                        .url(baseUrl + "/v1/messages")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", anthropicVersion)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON));

        // Add beta header if request has beta features
        if (request.betaFeatures() != null && !request.betaFeatures().isEmpty()) {
            httpBuilder.header("anthropic-beta", String.join(",", request.betaFeatures()));
        }

        try (Response response = httpClient.newCall(httpBuilder.build()).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                throw new IOException(
                        "Anthropic Messages API failed with status %d: %s"
                                .formatted(response.code(), responseBody));
            }
            log.debug("Anthropic Messages API response: {}", responseBody);
            return objectMapper.readValue(responseBody, MessagesResponse.class);
        }
    }

    private String readBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body != null ? body.string() : "";
    }

    // -- Request DTOs --

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessagesRequest(
            String model,
            @JsonProperty("max_tokens") Integer maxTokens,
            List<Message> messages,
            String system,
            Double temperature,
            @JsonProperty("top_p") Double topP,
            @JsonProperty("top_k") Integer topK,
            @JsonProperty("stop_sequences") List<String> stopSequences,
            List<Tool> tools,
            Thinking thinking,
            // Not serialized — used to set the anthropic-beta HTTP header
            @JsonIgnoreProperties @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    List<String> betaFeatures) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private Integer maxTokens;
            private List<Message> messages;
            private String system;
            private Double temperature;
            private Double topP;
            private Integer topK;
            private List<String> stopSequences;
            private List<Tool> tools;
            private Thinking thinking;
            private List<String> betaFeatures;

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder maxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder messages(List<Message> messages) {
                this.messages = messages;
                return this;
            }

            public Builder system(String system) {
                this.system = system;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.temperature = temperature;
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

            public Builder stopSequences(List<String> stopSequences) {
                this.stopSequences = stopSequences;
                return this;
            }

            public Builder tools(List<Tool> tools) {
                this.tools = tools;
                return this;
            }

            public Builder thinking(Thinking thinking) {
                this.thinking = thinking;
                return this;
            }

            public Builder betaFeatures(List<String> betaFeatures) {
                this.betaFeatures = betaFeatures;
                return this;
            }

            public MessagesRequest build() {
                return new MessagesRequest(
                        model,
                        maxTokens,
                        messages,
                        system,
                        temperature,
                        topP,
                        topK,
                        stopSequences,
                        tools,
                        thinking,
                        betaFeatures);
            }
        }
    }

    /** A message in the conversation. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            String role, // "user" or "assistant"
            Object content // String or List<ContentBlock>
            ) {

        public static Message user(String text) {
            return new Message("user", text);
        }

        public static Message user(List<ContentBlock> blocks) {
            return new Message("user", blocks);
        }

        public static Message assistant(String text) {
            return new Message("assistant", text);
        }

        public static Message assistant(List<ContentBlock> blocks) {
            return new Message("assistant", blocks);
        }
    }

    /** Content block within a message (request side). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentBlock(
            String type, // "text", "image", "tool_use", "tool_result"
            String text,
            // tool_use fields
            String id,
            String name,
            Object input,
            // tool_result fields
            @JsonProperty("tool_use_id") String toolUseId,
            Object content, // String or nested blocks for tool_result
            @JsonProperty("is_error") Boolean isError,
            // image fields
            Source source) {

        public static ContentBlock text(String text) {
            return new ContentBlock("text", text, null, null, null, null, null, null, null);
        }

        public static ContentBlock toolUse(String id, String name, Object input) {
            return new ContentBlock("tool_use", null, id, name, input, null, null, null, null);
        }

        public static ContentBlock toolResult(String toolUseId, String content) {
            return new ContentBlock(
                    "tool_result", null, null, null, null, toolUseId, content, null, null);
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Source(
                String type, // "base64"
                @JsonProperty("media_type") String mediaType,
                String data) {}
    }

    /** Tool definition. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            String type, // "custom", "web_search_20250305", "code_execution_20250825"
            String name,
            String description,
            @JsonProperty("input_schema") Map<String, Object> inputSchema) {

        /** Create a custom function tool. */
        public static Tool function(
                String name, String description, Map<String, Object> inputSchema) {
            return new Tool("custom", name, description, inputSchema);
        }

        /** Create a web search built-in tool. */
        public static Tool webSearch() {
            return new Tool("web_search_20250305", "web_search", null, null);
        }

        /** Create a code execution built-in tool. */
        public static Tool codeExecution() {
            return new Tool("code_execution_20250825", "code_execution", null, null);
        }
    }

    /** Thinking configuration for extended thinking mode. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Thinking(
            String type, // "enabled" or "disabled"
            @JsonProperty("budget_tokens") Integer budgetTokens) {

        public static Thinking enabled(int budgetTokens) {
            return new Thinking("enabled", budgetTokens);
        }
    }

    // -- Response DTOs --

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessagesResponse(
            String id,
            String type, // "message"
            String role, // "assistant"
            List<ResponseContentBlock> content,
            String model,
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("stop_sequence") String stopSequence,
            ResponseUsage usage) {}

    /** Content block in the response. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseContentBlock(
            String type, // "text", "tool_use", "thinking", "web_search_tool_result",
            // "code_execution_tool_result", etc.
            String text,
            // tool_use fields
            String id,
            String name,
            Object input,
            // thinking fields
            String thinking,
            String signature,
            // server tool result fields (web_search, code_execution)
            Object content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseUsage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("cache_creation_input_tokens") Integer cacheCreationInputTokens,
            @JsonProperty("cache_read_input_tokens") Integer cacheReadInputTokens) {}
}
