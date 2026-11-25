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
package org.conductoross.conductor.common.metadata.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Guardrail definition DTO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GuardrailConfig {

    private String name;

    /** Guardrail type: regex, llm, custom, external. */
    private String guardrailType;

    /** Position: input or output. */
    @Builder.Default private String position = "output";

    /** Action on failure: retry, raise, fix, human. */
    @Builder.Default private String onFail = "retry";

    @Builder.Default private int maxRetries = 3;

    // --- Regex-specific fields ---
    private List<String> patterns;

    /** Regex mode: block or allow. */
    private String mode;

    /** Custom failure message. */
    private String message;

    // --- LLM-specific fields ---
    private String model;
    private String policy;
    private Integer maxTokens;

    // --- Custom/External-specific fields ---
    /** Task name for custom @guardrail workers or external guardrails. */
    private String taskName;
}
