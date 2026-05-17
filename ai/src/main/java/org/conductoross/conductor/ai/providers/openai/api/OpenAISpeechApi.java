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
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.common.config.ObjectMapperProvider;

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
 * OkHttp REST client for the OpenAI Text-to-Speech API (POST /v1/audio/speech).
 *
 * <p>Supports both OpenAI and Azure OpenAI endpoints.
 */
@Slf4j
public class OpenAISpeechApi {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final String baseUrl;
    private final String authHeaderName;
    private final String authHeaderValue;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAISpeechApi(String apiKey, String baseUrl, boolean azureAuth, long timeoutSeconds) {
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

    public OpenAISpeechApi(String apiKey, String baseUrl, boolean azureAuth) {
        this(apiKey, baseUrl, azureAuth, 120);
    }

    /**
     * Generate speech audio from text.
     *
     * @param request Speech request parameters
     * @return Raw audio bytes in the requested format
     */
    public byte[] createSpeech(SpeechRequest request) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(request);

        Request httpRequest =
                new Request.Builder()
                        .url(baseUrl + "/audio/speech")
                        .header(authHeaderName, authHeaderValue)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON))
                        .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody body = response.body();
                String errorBody = body != null ? body.string() : "";
                throw new IOException(
                        "Speech API failed with status %d: %s"
                                .formatted(response.code(), errorBody));
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Speech API returned empty body");
            }
            return body.bytes();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpeechRequest(
            String model,
            String input,
            String voice,
            @JsonProperty("response_format") String responseFormat,
            Double speed) {}
}
