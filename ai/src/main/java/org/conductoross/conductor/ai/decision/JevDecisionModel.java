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
package org.conductoross.conductor.ai.decision;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Translates the generic decision contract to Jev System One without chat emulation. */
@Component
public class JevDecisionModel implements DecisionModel {
    private final JevDecisionConfiguration config;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    public JevDecisionModel(
            JevDecisionConfiguration config, ObjectMapper mapper, OkHttpClient client) {
        this.config = config;
        this.mapper = mapper;
        // A task retry is the only retry boundary. Never replay a billed call implicitly,
        // or follow a redirect carrying its credential to another destination.
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
    public String provider() {
        return "jev";
    }

    @Override
    public DecisionResult decide(DecisionRequest input) {
        DecisionValidation.request(input);
        DecisionValidation.require(
                DecisionValidation.text(config.getApiKey()), "Jev API key is not configured");
        DecisionValidation.require(
                config.getApiKey().chars().allMatch(c -> c > 32 && c < 127),
                "invalid Jev credential format");
        Map<String, Object> questions = new LinkedHashMap<>();
        input.questions()
                .forEach(
                        (name, q) -> {
                            Map<String, Object> wire = new LinkedHashMap<>();
                            wire.put("instructions", q.instructions());
                            switch (q.type()) {
                                case CHOICE -> {
                                    wire.put("type", "choice");
                                    wire.put("criteria", q.choices());
                                }
                                case SCORE -> {
                                    wire.put("type", "score");
                                    wire.put("criteria", q.scale());
                                }
                                case BOOLEAN -> wire.put("type", "noul");
                            }
                            questions.put(name, wire);
                        });
        long started = System.nanoTime();
        try {
            byte[] payload =
                    mapper.writeValueAsBytes(
                            Map.of(
                                    "model",
                                    input.model(),
                                    "state",
                                    input.state(),
                                    "questions",
                                    questions));
            Request request =
                    new Request.Builder()
                            .url(config.endpoint())
                            .header("Authorization", "Bearer " + config.getApiKey())
                            .post(RequestBody.create(payload, MediaType.get("application/json")))
                            .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = "Jev HTTP status " + response.code();
                    // Temporary errors can be retried by an explicit Conductor task policy.
                    if (response.code() == 429 || response.code() >= 500)
                        throw new IllegalStateException(error);
                    throw new NonRetryableException(error);
                }
                DecisionValidation.require(response.body() != null, "empty Jev response");
                byte[] bytes = response.body().byteStream().readNBytes(2_000_001);
                DecisionValidation.require(bytes.length <= 2_000_000, "Jev response too large");
                JsonNode data =
                        mapper.reader()
                                .with(
                                        com.fasterxml.jackson.databind.DeserializationFeature
                                                .USE_BIG_DECIMAL_FOR_FLOATS)
                                .readTree(bytes);
                DecisionValidation.require(
                        data != null
                                && data.isObject()
                                && data.path("model").isTextual()
                                && data.path("answers").isObject(),
                        "invalid Jev response");
                Map<String, DecisionResult.Answer> answers = new LinkedHashMap<>();
                data.path("answers")
                        .fields()
                        .forEachRemaining(
                                entry -> {
                                    JsonNode a = entry.getValue();
                                    String type = a.path("type").asText();
                                    DecisionQuestion.Type kind =
                                            switch (type) {
                                                case "choice" -> DecisionQuestion.Type.CHOICE;
                                                case "score" -> DecisionQuestion.Type.SCORE;
                                                case "noul" -> DecisionQuestion.Type.BOOLEAN;
                                                default ->
                                                        throw new NonRetryableException(
                                                                "Invalid Jev answer type");
                                            };
                                    answers.put(
                                            entry.getKey(),
                                            new DecisionResult.Answer(
                                                    kind,
                                                    a.has("choice") && a.get("choice").isTextual()
                                                            ? a.get("choice").textValue()
                                                            : null,
                                                    number(a, "score"),
                                                    number(a, "noul"),
                                                    number(a, "confidence")));
                                });
                JsonNode usage = data.path("usage");
                DecisionValidation.require(
                        usage.isMissingNode() || usage.isObject(), "invalid Jev usage");
                DecisionValidation.require(
                        !usage.has("cost")
                                || (usage.get("cost").isNumber()
                                        && usage.get("cost").decimalValue().signum() >= 0),
                        "invalid Jev cost");
                DecisionResult.Usage reported =
                        new DecisionResult.Usage(
                                tokens(usage, "input_tokens"),
                                tokens(usage, "output_tokens"),
                                usage.path("cost").isNumber()
                                        ? usage.get("cost").decimalValue()
                                        : null,
                                usage.path("cost").isNumber()
                                                && "openrouter".equals(config.getRoute())
                                        ? "USD"
                                        : null);
                DecisionResult result =
                        new DecisionResult(
                                data.get("model").textValue(),
                                answers,
                                reported,
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                                data.path("id").isTextual() ? data.get("id").textValue() : null);
                DecisionValidation.result(input, result);
                return result;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new NonRetryableException("Invalid Jev JSON response");
        } catch (IOException e) {
            // Do not include HTTP bodies, headers, URLs, or exception causes in task errors.
            throw new IllegalStateException("Jev transport failed");
        }
    }

    private static Double number(JsonNode node, String field) {
        if (!node.has(field)) return null;
        DecisionValidation.require(node.get(field).isNumber(), "invalid Jev numeric answer");
        return node.get(field).doubleValue();
    }

    private static Long tokens(JsonNode node, String field) {
        if (!node.has(field)) return null;
        DecisionValidation.require(
                node.get(field).isIntegralNumber()
                        && node.get(field).canConvertToLong()
                        && node.get(field).longValue() >= 0,
                "invalid Jev token usage");
        return node.get(field).longValue();
    }
}
