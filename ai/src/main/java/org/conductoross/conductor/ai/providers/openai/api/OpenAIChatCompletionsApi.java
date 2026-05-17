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
package org.conductoross.conductor.ai.providers.openai.api;

import java.io.IOException;
import java.util.List;
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
 * OkHttp REST client for OpenAI-compatible Chat Completions API (POST /chat/completions).
 *
 * <p>Works with any provider that implements the OpenAI Chat Completions format: Perplexity, Grok
 * (xAI), Together AI, etc.
 */
@Slf4j
public class OpenAIChatCompletionsApi {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final String baseUrl;
    private final String apiKey;
    private final String completionsPath;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIChatCompletionsApi(
            String apiKey, String baseUrl, String completionsPath, long timeoutSeconds) {
        this.baseUrl =
                baseUrl != null && baseUrl.endsWith("/")
                        ? baseUrl.substring(0, baseUrl.length() - 1)
                        : baseUrl;
        this.apiKey = apiKey;
        this.completionsPath = completionsPath != null ? completionsPath : "/chat/completions";
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build();
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    public OpenAIChatCompletionsApi(String apiKey, String baseUrl, long timeoutSeconds) {
        this(apiKey, baseUrl, null, timeoutSeconds);
    }

    public ChatCompletionResult createChatCompletion(ChatCompletionRequest request)
            throws IOException {
        String jsonBody = objectMapper.writeValueAsString(request);
        log.debug("Chat Completions API request: {}", jsonBody);

        Request httpRequest =
                new Request.Builder()
                        .url(baseUrl + completionsPath)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON))
                        .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            ResponseBody body = response.body();
            String responseBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(
                        "Chat Completions API failed with status %d: %s"
                                .formatted(response.code(), responseBody));
            }
            log.debug("Chat Completions API response: {}", responseBody);
            return objectMapper.readValue(responseBody, ChatCompletionResult.class);
        }
    }

    // -- Request DTOs --

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
            String model,
            List<MessageItem> messages,
            Double temperature,
            @JsonProperty("top_p") Double topP,
            @JsonProperty("max_tokens") Integer maxTokens,
            List<String> stop,
            @JsonProperty("frequency_penalty") Double frequencyPenalty,
            @JsonProperty("presence_penalty") Double presencePenalty,
            @JsonProperty("top_k") Integer topK,
            @JsonProperty("response_format") ResponseFormat responseFormat,
            List<ToolDef> tools,
            @JsonProperty("tool_choice") Object toolChoice) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private List<MessageItem> messages;
            private Double temperature;
            private Double topP;
            private Integer maxTokens;
            private List<String> stop;
            private Double frequencyPenalty;
            private Double presencePenalty;
            private Integer topK;
            private ResponseFormat responseFormat;
            private List<ToolDef> tools;
            private Object toolChoice;

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder messages(List<MessageItem> messages) {
                this.messages = messages;
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

            public Builder maxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder stop(List<String> stop) {
                this.stop = stop;
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

            public Builder topK(Integer topK) {
                this.topK = topK;
                return this;
            }

            public Builder responseFormat(ResponseFormat responseFormat) {
                this.responseFormat = responseFormat;
                return this;
            }

            public Builder tools(List<ToolDef> tools) {
                this.tools = tools;
                return this;
            }

            public Builder toolChoice(Object toolChoice) {
                this.toolChoice = toolChoice;
                return this;
            }

            public ChatCompletionRequest build() {
                return new ChatCompletionRequest(
                        model,
                        messages,
                        temperature,
                        topP,
                        maxTokens,
                        stop,
                        frequencyPenalty,
                        presencePenalty,
                        topK,
                        responseFormat,
                        tools,
                        toolChoice);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessageItem(
            String role,
            Object content, // String or List<ContentPart>
            String name,
            @JsonProperty("tool_calls") List<ToolCallItem> toolCalls,
            @JsonProperty("tool_call_id") String toolCallId) {

        public static MessageItem system(String content) {
            return new MessageItem("system", content, null, null, null);
        }

        public static MessageItem user(String content) {
            return new MessageItem("user", content, null, null, null);
        }

        public static MessageItem assistant(String content) {
            return new MessageItem("assistant", content, null, null, null);
        }

        public static MessageItem assistant(String content, List<ToolCallItem> toolCalls) {
            return new MessageItem("assistant", content, null, toolCalls, null);
        }

        public static MessageItem tool(String toolCallId, String content) {
            return new MessageItem("tool", content, null, null, toolCallId);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCallItem(String id, String type, FunctionCall function) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCall(String name, String arguments) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseFormat(String type) {

        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolDef(String type, FunctionDef function) {

        public static ToolDef function(String name, String description, Object parameters) {
            return new ToolDef("function", new FunctionDef(name, description, parameters));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionDef(String name, String description, Object parameters) {}

    // -- Response DTOs --

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResult(
            String id, String object, String model, List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Integer index,
            MessageItem message,
            @JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {}
}
