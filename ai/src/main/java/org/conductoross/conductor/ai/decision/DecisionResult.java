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

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Provider-neutral answers and reported usage. Unknown costs remain null, never zero. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DecisionResult(
        String model, Map<String, Answer> answers, Usage usage, long latencyMs, String requestId) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Answer(
            DecisionQuestion.Type type,
            String choice,
            Double score,
            Double probability,
            Double confidence) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(Long inputTokens, Long outputTokens, BigDecimal cost, String currency) {}
}
