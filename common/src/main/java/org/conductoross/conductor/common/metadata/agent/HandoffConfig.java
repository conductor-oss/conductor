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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Handoff condition DTO for swarm strategy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HandoffConfig {

    /** Handoff type: on_tool_result, on_text_mention, on_condition. */
    private String type;

    /** Target agent name. */
    private String target;

    // --- on_tool_result ---
    private String toolName;
    private String resultContains;

    // --- on_text_mention ---
    private String text;

    // --- on_condition (worker-based) ---
    /** Task name for the condition evaluation worker. */
    private String taskName;
}
