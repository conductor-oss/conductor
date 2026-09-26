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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

/** Reject invalid questions before inference and invalid answers before completing a task. */
public final class DecisionValidation {
    private DecisionValidation() {}

    public static void request(DecisionRequest request) {
        require(request != null, "request required");
        require(
                StringUtils.isNotBlank(request.model()) && StringUtils.isNotBlank(request.state()),
                "model and state required");
        questions(request.questions());
    }

    public static void questions(Map<String, DecisionQuestion> questions) {
        require(!CollectionUtils.isEmpty(questions), "questions required");
        questions.forEach(DecisionValidation::validateQuestion);
    }

    private static void validateQuestion(String name, DecisionQuestion q) {
        require(
                StringUtils.isNotBlank(name)
                        && q != null
                        && q.type() != null
                        && StringUtils.isNotBlank(q.instructions()),
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
                                                StringUtils.isNotBlank(key)
                                                        && StringUtils.isNotBlank(value),
                                                "invalid choice option"));
            }
            case SCORE ->
                    require(
                            q.choices() == null
                                    && q.scale() != null
                                    && q.scale().size() >= 2
                                    && q.scale().size() <= 10
                                    && q.scale().stream().allMatch(StringUtils::isNotBlank),
                            "invalid score scale");
            case BOOLEAN ->
                    require(
                            q.choices() == null && q.scale() == null,
                            "boolean has no choices or scale");
        }
    }

    public static void result(DecisionRequest request, DecisionResult result) {
        requireResponse(
                result != null
                        && StringUtils.isNotBlank(result.model())
                        && result.answers() != null
                        && result.answers().keySet().equals(request.questions().keySet()),
                "invalid response answers");
        request.questions()
                .forEach((name, question) -> validateAnswer(question, result.answers().get(name)));
    }

    private static void validateAnswer(DecisionQuestion q, DecisionResult.Answer answer) {
        requireResponse(answer != null && answer.type() == q.type(), "invalid answer type");
        requireResponse(
                answer.confidence() == null || bounded(answer.confidence(), 1),
                "invalid confidence");
        switch (q.type()) {
            case CHOICE ->
                    requireResponse(
                            answer.choice() != null
                                    && q.choices().containsKey(answer.choice())
                                    && answer.score() == null
                                    && answer.probability() == null,
                            "invalid choice answer");
            case SCORE ->
                    requireResponse(
                            bounded(answer.score(), q.scale().size() - 1)
                                    && answer.choice() == null
                                    && answer.probability() == null,
                            "invalid score answer");
            case BOOLEAN ->
                    requireResponse(
                            bounded(answer.probability(), 1)
                                    && answer.choice() == null
                                    && answer.score() == null,
                            "invalid boolean answer");
        }
    }

    private static boolean bounded(Double value, double upper) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= upper;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException("Decision: " + message);
    }

    static void requireResponse(boolean condition, String message) {
        if (!condition) throw new NonRetryableException("Decision: " + message);
    }
}
