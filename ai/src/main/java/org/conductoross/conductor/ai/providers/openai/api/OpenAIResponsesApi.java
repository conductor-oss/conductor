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
import java.util.Map;

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
 * OkHttp REST client for the OpenAI Responses API.
 *
 * <p>Supports both OpenAI and Azure OpenAI endpoints via configurable auth.
 *
 * <p>Endpoint: POST /v1/responses (OpenAI) or POST /openai/v1/responses (Azure)
 *
 * @see <a href="https://developers.openai.com/api/reference/resources/responses">OpenAI Responses
 *     API</a>
 */
@Slf4j
public class OpenAIResponsesApi {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final String baseUrl;
    private final String authHeaderName;
    private final String authHeaderValue;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param httpClient Shared OkHttpClient instance
     * @param apiKey API key
     * @param baseUrl Base URL (e.g. "https://api.openai.com/v1" or
     *     "https://resource.openai.azure.com/openai/v1")
     * @param azureAuth true for Azure (api-key header), false for OpenAI (Bearer token)
     */
    public OpenAIResponsesApi(
            OkHttpClient httpClient, String apiKey, String baseUrl, boolean azureAuth) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.authHeaderName = azureAuth ? "api-key" : "Authorization";
        this.authHeaderValue = azureAuth ? apiKey : "Bearer " + apiKey;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    public OpenAIResponsesApi(OkHttpClient httpClient, String apiKey, String baseUrl) {
        this(httpClient, apiKey, baseUrl, false);
    }

    /**
     * Create a model response via POST /v1/responses.
     *
     * @param request The response creation request
     * @return The API response
     */
    public ResponseResult createResponse(ResponseRequest request) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(request);
        log.debug("Responses API request: {}", jsonBody);

        Request httpRequest =
                new Request.Builder()
                        .url(baseUrl + "/responses")
                        .header(authHeaderName, authHeaderValue)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON))
                        .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                // o-series and some newer OpenAI models reject temperature — retry without it.
                if (response.code() == 400
                        && responseBody.contains("temperature")
                        && request.temperature() != null) {
                    return createResponse(request.withoutTemperature());
                }
                throw new IOException(
                        "Responses API failed with status %d: %s"
                                .formatted(response.code(), responseBody));
            }
            log.debug("Responses API response: {}", responseBody);
            return objectMapper.readValue(responseBody, ResponseResult.class);
        }
    }

    private String readBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body != null ? body.string() : "";
    }

    // -- Request DTOs --

    /**
     * Reasoning config block on the Responses API request. OpenAI's Responses API takes a nested
     * object {@code "reasoning": {"effort": "...", "summary": "..."}} rather than the flat {@code
     * "reasoning_effort"} parameter the legacy Chat Completions API used. Sending the flat shape
     * produces an HTTP 400: "Unsupported parameter: 'reasoning_effort'. ... has moved to
     * 'reasoning.effort'."
     *
     * <p>{@code summary} (values like {@code "auto"}, {@code "concise"}, {@code "detailed"}) is
     * required to receive human-readable chain-of-thought summaries on reasoning items in the
     * response — without it, reasoning items carry no summary text.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reasoning(String effort, String summary) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseRequest(
            String model,
            Object input, // String or List<InputItem>
            String instructions,
            List<Tool> tools,
            @JsonProperty("previous_response_id") String previousResponseId,
            Double temperature,
            @JsonProperty("top_p") Double topP,
            @JsonProperty("max_output_tokens") Integer maxOutputTokens,
            Reasoning reasoning,
            TextFormat text,
            @JsonProperty("tool_choice") Object toolChoice,
            Boolean store) {

        public static Builder builder() {
            return new Builder();
        }

        public ResponseRequest withoutTemperature() {
            return new ResponseRequest(
                    model,
                    input,
                    instructions,
                    tools,
                    previousResponseId,
                    null,
                    topP,
                    maxOutputTokens,
                    reasoning,
                    text,
                    toolChoice,
                    store);
        }

        public static class Builder {
            private String model;
            private Object input;
            private String instructions;
            private List<Tool> tools;
            private String previousResponseId;
            private Double temperature;
            private Double topP;
            private Integer maxOutputTokens;
            private String reasoningEffort;
            private String reasoningSummary;
            private TextFormat text;
            private Object toolChoice;
            private Boolean store;

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder input(Object input) {
                this.input = input;
                return this;
            }

            public Builder instructions(String instructions) {
                this.instructions = instructions;
                return this;
            }

            public Builder tools(List<Tool> tools) {
                this.tools = tools;
                return this;
            }

            public Builder previousResponseId(String previousResponseId) {
                this.previousResponseId = previousResponseId;
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

            public Builder maxOutputTokens(Integer maxOutputTokens) {
                this.maxOutputTokens = maxOutputTokens;
                return this;
            }

            public Builder reasoningEffort(String reasoningEffort) {
                // Public API stays a flat String for caller convenience;
                // the request body is serialized via the nested ``Reasoning``
                // record so OpenAI's Responses API sees ``reasoning.effort``.
                this.reasoningEffort = reasoningEffort;
                return this;
            }

            public Builder reasoningSummary(String reasoningSummary) {
                // OpenAI emits chain-of-thought summary text on reasoning
                // items only when ``reasoning.summary`` is set on the request
                // (e.g. "auto", "concise", "detailed"). Without this, the
                // response's reasoning items carry no summary text.
                this.reasoningSummary = reasoningSummary;
                return this;
            }

            public Builder text(TextFormat text) {
                this.text = text;
                return this;
            }

            public Builder toolChoice(Object toolChoice) {
                this.toolChoice = toolChoice;
                return this;
            }

            public Builder store(Boolean store) {
                this.store = store;
                return this;
            }

            public ResponseRequest build() {
                boolean hasEffort = reasoningEffort != null && !reasoningEffort.isBlank();
                boolean hasSummary = reasoningSummary != null && !reasoningSummary.isBlank();
                Reasoning reasoning =
                        (hasEffort || hasSummary)
                                ? new Reasoning(
                                        hasEffort ? reasoningEffort : null,
                                        hasSummary ? reasoningSummary : null)
                                : null;
                // OpenAI's Responses API rejects an empty-string previous_response_id
                // with HTTP 400 ("Invalid 'previous_response_id': ''"). Normalize
                // blank to null so the field gets omitted from the wire payload.
                String normalizedPrevRespId =
                        (previousResponseId != null && !previousResponseId.isBlank())
                                ? previousResponseId
                                : null;
                return new ResponseRequest(
                        model,
                        input,
                        instructions,
                        tools,
                        normalizedPrevRespId,
                        temperature,
                        topP,
                        maxOutputTokens,
                        reasoning,
                        text,
                        toolChoice,
                        store);
            }
        }
    }

    /** Input item for the Responses API input array. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputItem(
            String type, // "message", "function_call", "function_call_output"
            String role, // "user", "assistant", "system"
            Object content, // String or List<ContentPart>
            String id,
            @JsonProperty("call_id") String callId,
            String name,
            String arguments,
            String output,
            String status) {

        public static InputItem userMessage(String text) {
            return new InputItem("message", "user", text, null, null, null, null, null, null);
        }

        public static InputItem userMessage(List<ContentPart> parts) {
            return new InputItem("message", "user", parts, null, null, null, null, null, null);
        }

        public static InputItem assistantMessage(String text) {
            List<ContentPart> content = List.of(ContentPart.outputText(text));
            return new InputItem(
                    "message", "assistant", content, null, null, null, null, null, null);
        }

        public static InputItem functionCall(String callId, String name, String arguments) {
            return new InputItem(
                    "function_call", null, null, null, callId, name, arguments, null, null);
        }

        public static InputItem functionCallOutput(String callId, String outputJson) {
            return new InputItem(
                    "function_call_output", null, null, null, callId, null, null, outputJson, null);
        }
    }

    /** Content part within a message. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentPart(
            String type, // "input_text", "input_image", "output_text"
            String text,
            @JsonProperty("image_url") String imageUrl,
            List<Object> annotations) {

        public static ContentPart inputText(String text) {
            return new ContentPart("input_text", text, null, null);
        }

        public static ContentPart outputText(String text) {
            return new ContentPart("output_text", text, null, List.of());
        }

        public static ContentPart inputImage(String url) {
            return new ContentPart("input_image", null, url, null);
        }
    }

    /** Tool definition for the Responses API. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            String type, // "function", "web_search", "code_interpreter", "file_search"
            String name,
            String description,
            Object parameters, // JSON Schema object for function tools
            Object container, // for code_interpreter
            @JsonProperty("vector_store_ids") List<String> vectorStoreIds // for file_search
            ) {

        public static Tool function(String name, String description, Object parameters) {
            return new Tool("function", name, description, parameters, null, null);
        }

        public static Tool webSearch() {
            return new Tool("web_search", null, null, null, null, null);
        }

        public static Tool codeInterpreter() {
            return new Tool("code_interpreter", null, null, null, Map.of("type", "auto"), null);
        }

        public static Tool fileSearch(List<String> vectorStoreIds) {
            return new Tool("file_search", null, null, null, null, vectorStoreIds);
        }
    }

    /** Text format configuration (for JSON mode). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TextFormat(Format format) {

        public static TextFormat jsonObject() {
            return new TextFormat(new Format("json_object", null));
        }

        public static TextFormat jsonSchema(Object schema) {
            return new TextFormat(new Format("json_schema", schema));
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Format(String type, Object schema) {}
    }

    // -- Response DTOs --

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseResult(
            String id,
            String object,
            String model,
            String status,
            List<OutputItem> output,
            @JsonProperty("output_text") String outputText,
            Usage usage,
            @JsonProperty("previous_response_id") String previousResponseId,
            @JsonProperty("reasoning_effort") String reasoningEffort,
            @JsonProperty("incomplete_details") Object incompleteDetails,
            Object error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(
            String type, // "message", "function_call", "reasoning"
            String id,
            String role,
            List<OutputContent> content,
            String status,
            // function_call fields
            @JsonProperty("call_id") String callId,
            String name,
            String arguments,
            // reasoning item: list of chain-of-thought summary blocks the model
            // emitted while thinking. Populated only when the request set
            // ``reasoning.summary``. Surfaced via ChatResponseMetadata so
            // downstream consumers can render it separately from the final
            // message.
            List<ReasoningSummary> summary) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReasoningSummary(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputContent(
            String type, // "output_text"
            String text,
            List<Object> annotations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            @JsonProperty("output_tokens_details") OutputTokensDetails outputTokensDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputTokensDetails(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {}
}
