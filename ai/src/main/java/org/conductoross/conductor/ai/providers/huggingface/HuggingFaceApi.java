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
package org.conductoross.conductor.ai.providers.huggingface;

import java.io.IOException;
import java.util.List;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class HuggingFaceApi {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final String token;
    private final String apiUrl;
    private final ObjectMapper objectMapper;

    public HuggingFaceApi(OkHttpClient httpClient, String token, String apiUrl) {
        this.httpClient = httpClient;
        this.token = token;
        this.apiUrl = apiUrl;
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    public String generate(String inputs) throws IOException {
        String body = objectMapper.writeValueAsString(new GenerationRequest(inputs));
        Request request =
                new Request.Builder()
                        .url(apiUrl)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(body, JSON))
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(
                        "HuggingFace API error %d: %s".formatted(response.code(), responseBody));
            }
            List<GenerationResult> results =
                    objectMapper.readValue(
                            responseBody,
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, GenerationResult.class));
            if (results == null || results.isEmpty()) {
                throw new IOException("Empty response from HuggingFace API");
            }
            return results.get(0).generatedText();
        }
    }

    public record GenerationRequest(String inputs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerationResult(@JsonProperty("generated_text") String generatedText) {}
}
