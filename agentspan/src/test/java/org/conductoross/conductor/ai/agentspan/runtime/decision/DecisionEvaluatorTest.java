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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DecisionEvaluatorTest {

    private final DecisionClient client = mock(DecisionClient.class);
    private final DecisionEvaluator evaluator = new DecisionEvaluator(client, new ObjectMapper());

    private static Map<String, Object> input() {
        return Map.of(
                "model", "jev-1.13",
                "state", "I was charged twice.",
                "questions",
                        Map.of(
                                "route",
                                Map.of(
                                        "type", "choice",
                                        "instructions", "Pick a team",
                                        "choices",
                                                Map.of("billing", "Money", "technical", "Bugs"))));
    }

    private static DecisionResult result(String choice) {
        return new DecisionResult(
                "openrouter",
                "typesafe/jev-1.13",
                Map.of(
                        "route",
                        new DecisionResult.Answer(
                                DecisionQuestion.Type.CHOICE, choice, null, null, 1.0)),
                new DecisionResult.Usage(10L, 2L, null, null),
                42L,
                "req-1");
    }

    @Test
    void isDeferredSoTheSwitchIsQueuedNotEvaluatedInTheDecider() {
        assertThat(evaluator.isDeferred()).isTrue();
    }

    @Test
    void returnsWholeResultPlusSelectedCase() {
        when(client.decide(any())).thenReturn(result("billing"));

        Object out = evaluator.evaluate("route", input());

        assertThat(out).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        assertThat(map)
                .containsEntry("selectedCase", "billing")
                .containsEntry("model", "typesafe/jev-1.13")
                .containsKey("answers")
                .containsKey("usage")
                .containsEntry("latencyMs", 42L);
    }

    @Test
    void blankExpressionUsesTheOnlyQuestion() {
        when(client.decide(any())).thenReturn(result("technical"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) evaluator.evaluate("", input());
        assertThat(out).containsEntry("selectedCase", "technical");
    }

    @Test
    void transientProviderFailurePropagatesSoTheTaskIsRetried() {
        when(client.decide(any())).thenThrow(new IllegalStateException("Decision HTTP status 503"));
        assertThatThrownBy(() -> evaluator.evaluate("route", input()))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(NonTransientException.class);
    }

    @Test
    void terminalProviderFailureIsNonTransient() {
        when(client.decide(any())).thenThrow(new NonRetryableException("invalid credential"));
        assertThatThrownBy(() -> evaluator.evaluate("route", input()))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("invalid credential");
    }

    @Test
    void badInputIsNonTransientAndNeverCallsTheProvider() {
        assertThatThrownBy(() -> evaluator.evaluate("route", Map.of("model", "jev-1.13")))
                .isInstanceOf(NonTransientException.class);
        assertThatThrownBy(() -> evaluator.evaluate("missing", input()))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("missing");
        verifyNoInteractions(client);
    }
}
