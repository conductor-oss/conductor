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
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.execution.tasks.Switch;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SWITCH evaluator backed by the decision engine. Deferred: the SWITCH is queued and evaluated by
 * the system task worker, and transient provider failures are retried by the task's retry policy.
 *
 * <p>Input is the SWITCH's inputParameters: {@code model}, {@code state}, {@code questions} and
 * optional {@code provider}. The expression names the choice question to route on; it may be blank
 * when there is exactly one question. The result holds the full decision output plus {@code
 * selectedCase}.
 */
@Component(DecisionEvaluator.NAME)
@Conditional(AIIntegrationEnabledCondition.class)
public class DecisionEvaluator implements Evaluator {

    public static final String NAME = "decision";

    private final DecisionClient client;
    private final ObjectMapper mapper;

    public DecisionEvaluator(DecisionClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public boolean isDeferred() {
        return true;
    }

    @Override
    public Object evaluate(String expression, Object input) {
        DecisionRequest request;
        String question;
        try {
            if (!(input instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("decision evaluator input must be an object");
            }
            request = mapper.convertValue(input, DecisionRequest.class);
            DecisionValidation.request(request);
            question = questionKey(expression, request);
        } catch (NonRetryableException | IllegalArgumentException e) {
            throw new NonTransientException(e.getMessage(), e);
        }

        DecisionResult result;
        try {
            result = client.decide(request);
            DecisionValidation.result(request, result);
        } catch (NonRetryableException | IllegalArgumentException e) {
            throw new NonTransientException(e.getMessage(), e);
        }

        Map<String, Object> output =
                new LinkedHashMap<>(
                        mapper.convertValue(result, new TypeReference<Map<String, Object>>() {}));
        output.put(Switch.SELECTED_CASE, result.answers().get(question).choice());
        return output;
    }

    private static String questionKey(String expression, DecisionRequest request) {
        String key = expression;
        if (StringUtils.isBlank(key)) {
            if (request.questions().size() != 1) {
                throw new IllegalArgumentException(
                        "decision evaluator expression must name the question to route on");
            }
            key = request.questions().keySet().iterator().next();
        }
        DecisionQuestion selected = request.questions().get(key);
        if (selected == null || selected.type() != DecisionQuestion.Type.CHOICE) {
            throw new IllegalArgumentException(
                    "decision evaluator question '" + key + "' must be a choice question");
        }
        return key;
    }
}
