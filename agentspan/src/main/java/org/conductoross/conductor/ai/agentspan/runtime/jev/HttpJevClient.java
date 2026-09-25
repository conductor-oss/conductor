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
package org.conductoross.conductor.ai.agentspan.runtime.jev;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import static org.conductoross.conductor.ai.agentspan.runtime.jev.JevValidation.require;

/** Jev System One provider. */
@Component
public class HttpJevClient implements JevClient {
    private static final int MAX_RESPONSE_BYTES = 2_000_000;
    private final JevConfiguration config;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    public HttpJevClient(JevConfiguration config, ObjectMapper mapper, OkHttpClient client) {
        this.config = config;
        this.mapper = mapper;
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
    public JevResult decide(JevRequest input) {
        JevValidation.request(input);
        require(JevValidation.text(config.getApiKey()), "Jev API key is not configured");
        require(
                config.getApiKey().chars().allMatch(c -> c > 32 && c < 127),
                "invalid Jev credential format");
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
                                    encodeQuestions(input.questions())));
            Request request =
                    new Request.Builder()
                            .url(config.endpoint())
                            .header("Authorization", "Bearer " + config.getApiKey())
                            .post(RequestBody.create(payload, MediaType.get("application/json")))
                            .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = "Jev HTTP status " + response.code();
                    if (response.code() == 429 || response.code() >= 500) {
                        throw new IllegalStateException(error);
                    }
                    throw new NonRetryableException(error);
                }
                require(response.body() != null, "empty Jev response");
                byte[] bytes = response.body().byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
                require(bytes.length <= MAX_RESPONSE_BYTES, "Jev response too large");
                JsonNode data = mapper.reader().with(USE_BIG_DECIMAL_FOR_FLOATS).readTree(bytes);
                JevResult result =
                        decodeResponse(
                                data, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                JevValidation.result(input, result);
                return result;
            }
        } catch (JsonProcessingException e) {
            throw new NonRetryableException("Invalid Jev JSON response");
        } catch (IOException e) {
            // Provider errors may contain credentials; omit response bodies and causes.
            throw new IllegalStateException("Jev transport failed");
        }
    }

    private Map<String, Object> encodeQuestions(Map<String, JevQuestion> questions) {
        Map<String, Object> encoded = new LinkedHashMap<>();
        for (var entry : questions.entrySet()) {
            JevQuestion question = entry.getValue();
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("instructions", question.instructions());
            switch (question.type()) {
                case CHOICE -> {
                    wire.put("type", "choice");
                    wire.put("criteria", question.choices());
                }
                case SCORE -> {
                    wire.put("type", "score");
                    wire.put("criteria", question.scale());
                }
                case BOOLEAN -> wire.put("type", "noul");
            }
            encoded.put(entry.getKey(), wire);
        }
        return encoded;
    }

    private JevResult decodeResponse(JsonNode data, long latencyMs) {
        require(
                data != null
                        && data.isObject()
                        && data.path("model").isTextual()
                        && data.path("answers").isObject(),
                "invalid Jev response");
        Map<String, JevResult.Answer> answers = new LinkedHashMap<>();
        data.path("answers")
                .fields()
                .forEachRemaining(
                        entry -> answers.put(entry.getKey(), decodeAnswer(entry.getValue())));
        return new JevResult(
                data.get("model").textValue(),
                answers,
                decodeUsage(data.path("usage")),
                latencyMs,
                data.path("id").isTextual() ? data.get("id").textValue() : null);
    }

    private JevResult.Answer decodeAnswer(JsonNode answer) {
        JevQuestion.Type type =
                switch (answer.path("type").asText()) {
                    case "choice" -> JevQuestion.Type.CHOICE;
                    case "score" -> JevQuestion.Type.SCORE;
                    case "noul" -> JevQuestion.Type.BOOLEAN;
                    default -> throw new NonRetryableException("Invalid Jev answer type");
                };
        return new JevResult.Answer(
                type,
                answer.path("choice").isTextual() ? answer.get("choice").textValue() : null,
                number(answer, "score"),
                number(answer, "noul"),
                number(answer, "confidence"));
    }

    private JevResult.Usage decodeUsage(JsonNode usage) {
        require(usage.isMissingNode() || usage.isObject(), "invalid Jev usage");
        require(
                !usage.has("cost")
                        || (usage.get("cost").isNumber()
                                && usage.get("cost").decimalValue().signum() >= 0),
                "invalid Jev cost");
        return new JevResult.Usage(
                tokens(usage, "input_tokens"),
                tokens(usage, "output_tokens"),
                usage.has("cost") ? usage.get("cost").decimalValue() : null,
                usage.has("cost") && "openrouter".equals(config.getRoute()) ? "USD" : null);
    }

    private static Double number(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        require(node.get(field).isNumber(), "invalid Jev numeric answer");
        return node.get(field).doubleValue();
    }

    private static Long tokens(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        require(
                node.get(field).isIntegralNumber()
                        && node.get(field).canConvertToLong()
                        && node.get(field).longValue() >= 0,
                "invalid Jev token usage");
        return node.get(field).longValue();
    }
}
