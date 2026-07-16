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

/** Termination condition DTO. Supports recursive AND/OR composites. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TerminationConfig {

    /** Condition type: text_mention, stop_message, max_message, token_usage, and, or. */
    private String type;

    // --- TextMentionTermination ---
    private String text;
    private Boolean caseSensitive;

    // --- StopMessageTermination ---
    private String stopMessage;

    // --- MaxMessageTermination ---
    private Integer maxMessages;

    // --- TokenUsageTermination ---
    private Integer maxTotalTokens;
    private Integer maxPromptTokens;
    private Integer maxCompletionTokens;

    // --- Composite (AND/OR) ---
    private List<TerminationConfig> conditions;
}
