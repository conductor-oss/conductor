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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.JsonNode;

import static org.conductoross.conductor.ai.agentspan.runtime.decision.DecisionValidation.requireResponse;

/** Default System One wire contract, isolated from provider selection and transport. */
@Component
public class SystemOneDecisionApiAdapter implements DecisionApiAdapter {
    @Override
    public String name() {
        return "system-one";
    }

    @Override
    public Map<String, Object> encode(DecisionRequest request) {
        return Map.of(
                "model",
                request.model(),
                "state",
                request.state(),
                "questions",
                encodeQuestions(request.questions()));
    }

    @Override
    public DecisionHttpRequest createRequest(
            DecisionRequest request, DecisionConfiguration.Route route, byte[] encodedBody) {
        requireResponse(
                StringUtils.isNotBlank(route.apiKey()), "Decision API key is not configured");
        requireResponse(
                route.apiKey().chars().allMatch(c -> c > 32 && c < 127),
                "invalid Decision credential format");
        return new DecisionHttpRequest(
                route.endpoint(),
                "POST",
                Map.of("Authorization", "Bearer " + route.apiKey()),
                "application/json",
                encodedBody);
    }

    private Map<String, Object> encodeQuestions(Map<String, DecisionQuestion> questions) {
        Map<String, Object> encoded = new LinkedHashMap<>();
        for (var entry : questions.entrySet()) {
            DecisionQuestion question = entry.getValue();
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

    @Override
    public DecisionResult decode(JsonNode data, long latencyMs, String provider) {
        requireResponse(
                data != null
                        && data.isObject()
                        && data.path("model").isTextual()
                        && data.path("answers").isObject(),
                "invalid Decision response");
        Map<String, DecisionResult.Answer> answers = new LinkedHashMap<>();
        data.path("answers")
                .fields()
                .forEachRemaining(
                        entry -> answers.put(entry.getKey(), decodeAnswer(entry.getValue())));
        return new DecisionResult(
                provider,
                data.get("model").textValue(),
                answers,
                decodeUsage(data.path("usage"), provider),
                latencyMs,
                data.path("id").isTextual() ? data.get("id").textValue() : null);
    }

    private DecisionResult.Answer decodeAnswer(JsonNode answer) {
        DecisionQuestion.Type type =
                switch (answer.path("type").asText()) {
                    case "choice" -> DecisionQuestion.Type.CHOICE;
                    case "score" -> DecisionQuestion.Type.SCORE;
                    case "noul" -> DecisionQuestion.Type.BOOLEAN;
                    default -> throw new NonRetryableException("Invalid Decision answer type");
                };
        return new DecisionResult.Answer(
                type,
                answer.path("choice").isTextual() ? answer.get("choice").textValue() : null,
                number(answer, "score"),
                number(answer, "noul"),
                number(answer, "confidence"));
    }

    private DecisionResult.Usage decodeUsage(JsonNode usage, String provider) {
        requireResponse(usage.isMissingNode() || usage.isObject(), "invalid Decision usage");
        requireResponse(
                !usage.has("cost")
                        || (usage.get("cost").isNumber()
                                && usage.get("cost").decimalValue().signum() >= 0),
                "invalid Decision cost");
        return new DecisionResult.Usage(
                tokens(usage, "input_tokens"),
                tokens(usage, "output_tokens"),
                usage.has("cost") ? usage.get("cost").decimalValue() : null,
                usage.has("cost") && "openrouter".equals(provider) ? "USD" : null);
    }

    private static Double number(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        requireResponse(node.get(field).isNumber(), "invalid Decision numeric answer");
        return node.get(field).doubleValue();
    }

    private static Long tokens(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        requireResponse(
                node.get(field).isIntegralNumber()
                        && node.get(field).canConvertToLong()
                        && node.get(field).longValue() >= 0,
                "invalid Decision token usage");
        return node.get(field).longValue();
    }
}
