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
package org.conductoross.conductor.ai.agentspan.runtime.decision;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS;
import static org.conductoross.conductor.ai.agentspan.runtime.decision.DecisionValidation.require;

/** Provider-neutral HTTP transport for structured decision inference. */
@Component
public class HttpDecisionClient implements DecisionClient {
    private static final int MAX_RESPONSE_BYTES = 2_000_000;
    private final DecisionConfiguration config;
    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final Map<String, DecisionApiAdapter> adapters;

    public HttpDecisionClient(
            DecisionConfiguration config,
            ObjectMapper mapper,
            OkHttpClient client,
            List<DecisionApiAdapter> adapters) {
        this.config = config;
        this.mapper = mapper;
        this.adapters =
                adapters.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        DecisionApiAdapter::name, Function.identity()));
        // Conductor owns retries. Redirects must not forward the API key.
        this.client =
                client.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .retryOnConnectionFailure(false)
                        .callTimeout(config.getTimeout())
                        .readTimeout(config.getTimeout())
                        .build();
    }

    @Override
    public DecisionResult decide(DecisionRequest input) {
        DecisionValidation.request(input);
        DecisionConfiguration.Route route = config.resolve(input.provider(), input.model());
        DecisionApiAdapter adapter = adapters.get(route.apiShape());
        require(adapter != null, "Unknown decision API shape: " + route.apiShape());
        require(StringUtils.isNotBlank(route.apiKey()), "Decision API key is not configured");
        require(
                route.apiKey().chars().allMatch(c -> c > 32 && c < 127),
                "invalid Decision credential format");
        long started = System.nanoTime();
        try {
            byte[] payload = mapper.writeValueAsBytes(adapter.encode(input));
            Request request =
                    new Request.Builder()
                            .url(route.endpoint())
                            .header("Authorization", "Bearer " + route.apiKey())
                            .post(RequestBody.create(payload, MediaType.get("application/json")))
                            .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = "Decision HTTP status " + response.code();
                    if (response.code() == 429 || response.code() >= 500) {
                        throw new IllegalStateException(error);
                    }
                    throw new NonRetryableException(error);
                }
                require(response.body() != null, "empty Decision response");
                byte[] bytes = response.body().byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
                require(bytes.length <= MAX_RESPONSE_BYTES, "Decision response too large");
                JsonNode data = mapper.reader().with(USE_BIG_DECIMAL_FOR_FLOATS).readTree(bytes);
                DecisionResult result =
                        adapter.decode(
                                data,
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                                route.provider());
                DecisionValidation.result(input, result);
                return result;
            }
        } catch (JsonProcessingException e) {
            throw new NonRetryableException("Invalid Decision JSON response");
        } catch (IOException e) {
            // Provider errors may contain credentials; omit response bodies and causes.
            throw new IllegalStateException("Decision transport failed");
        }
    }
}
