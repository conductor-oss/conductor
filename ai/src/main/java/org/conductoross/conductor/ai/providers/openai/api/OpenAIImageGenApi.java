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
 * OkHttp REST client for the OpenAI Image Generation API (POST /v1/images/generations).
 *
 * <p>Supports both OpenAI and Azure OpenAI endpoints.
 */
@Slf4j
public class OpenAIImageGenApi {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final String baseUrl;
    private final String authHeaderName;
    private final String authHeaderValue;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIImageGenApi(
            String apiKey, String baseUrl, boolean azureAuth, long timeoutSeconds) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.authHeaderName = azureAuth ? "api-key" : "Authorization";
        this.authHeaderValue = azureAuth ? apiKey : "Bearer " + apiKey;
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build();
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    public OpenAIImageGenApi(String apiKey, String baseUrl, boolean azureAuth) {
        this(apiKey, baseUrl, azureAuth, 120);
    }

    public ImageResult createImage(ImageRequest request) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(request);

        Request httpRequest =
                new Request.Builder()
                        .url(baseUrl + "/images/generations")
                        .header(authHeaderName, authHeaderValue)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON))
                        .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            ResponseBody body = response.body();
            String responseBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(
                        "Image Generation API failed with status %d: %s"
                                .formatted(response.code(), responseBody));
            }
            return objectMapper.readValue(responseBody, ImageResult.class);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImageRequest(
            String model,
            String prompt,
            Integer n,
            String size,
            String style,
            String quality,
            @JsonProperty("response_format") String responseFormat // "url" or "b64_json"
            ) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private String prompt;
            private Integer n;
            private String size;
            private String style;
            private String quality;
            private String responseFormat = "b64_json";

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder prompt(String prompt) {
                this.prompt = prompt;
                return this;
            }

            public Builder n(Integer n) {
                this.n = n;
                return this;
            }

            public Builder size(String size) {
                this.size = size;
                return this;
            }

            public Builder style(String style) {
                this.style = style;
                return this;
            }

            public Builder quality(String quality) {
                this.quality = quality;
                return this;
            }

            public Builder responseFormat(String responseFormat) {
                this.responseFormat = responseFormat;
                return this;
            }

            public ImageRequest build() {
                return new ImageRequest(model, prompt, n, size, style, quality, responseFormat);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageResult(Long created, List<ImageData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageData(
            String url,
            @JsonProperty("b64_json") String b64Json,
            @JsonProperty("revised_prompt") String revisedPrompt) {}
}
