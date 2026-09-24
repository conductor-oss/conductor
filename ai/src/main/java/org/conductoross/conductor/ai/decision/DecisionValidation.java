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

import java.util.Map;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

/** Reject invalid questions before inference and invalid answers before completing a task. */
final class DecisionValidation {
    private DecisionValidation() {}

    public static void request(DecisionRequest request) {
        require(request != null, "request required");
        require(
                text(request.provider()) && text(request.model()) && text(request.state()),
                "provider, model and state required");
        require(
                request.questions() != null && !request.questions().isEmpty(),
                "questions required");
        for (var entry : request.questions().entrySet()) {
            DecisionQuestion q = entry.getValue();
            require(
                    text(entry.getKey()) && q != null && q.type() != null && text(q.instructions()),
                    "invalid question");
            switch (q.type()) {
                case CHOICE -> {
                    require(
                            q.scale() == null
                                    && q.choices() != null
                                    && q.choices().size() >= 2
                                    && q.choices().size() <= 255,
                            "invalid choice options");
                    q.choices()
                            .forEach(
                                    (key, value) ->
                                            require(
                                                    text(key) && text(value),
                                                    "invalid choice option"));
                }
                case SCORE ->
                        require(
                                q.choices() == null
                                        && q.scale() != null
                                        && q.scale().size() >= 2
                                        && q.scale().size() <= 10
                                        && q.scale().stream().allMatch(DecisionValidation::text),
                                "invalid score scale");
                case BOOLEAN ->
                        require(
                                q.choices() == null && q.scale() == null,
                                "boolean has no choices or scale");
            }
        }
    }

    public static void result(DecisionRequest request, DecisionResult result) {
        require(
                result != null
                        && text(result.model())
                        && result.answers() != null
                        && result.answers().keySet().equals(request.questions().keySet()),
                "invalid response answers");
        for (Map.Entry<String, DecisionQuestion> entry : request.questions().entrySet()) {
            var q = entry.getValue();
            var answer = result.answers().get(entry.getKey());
            require(answer != null && answer.type() == q.type(), "invalid answer type");
            require(
                    answer.confidence() == null || bounded(answer.confidence(), 1),
                    "invalid confidence");
            switch (q.type()) {
                case CHOICE ->
                        require(
                                answer.choice() != null
                                        && q.choices().containsKey(answer.choice())
                                        && answer.score() == null
                                        && answer.probability() == null,
                                "invalid choice answer");
                case SCORE ->
                        require(
                                bounded(answer.score(), q.scale().size() - 1)
                                        && answer.choice() == null
                                        && answer.probability() == null,
                                "invalid score answer");
                case BOOLEAN ->
                        require(
                                bounded(answer.probability(), 1)
                                        && answer.choice() == null
                                        && answer.score() == null,
                                "invalid boolean answer");
            }
        }
    }

    public static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean bounded(Double value, double upper) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= upper;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new NonRetryableException("Decision model: " + message);
    }
}
