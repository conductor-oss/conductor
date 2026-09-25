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

/**
 * Callback configuration for agent lifecycle hooks.
 *
 * <p>Each callback maps to a Conductor worker task that is inserted at the specified position in
 * the compiled workflow. The worker task is registered by the SDK (same pattern as {@code @tool}
 * functions).
 *
 * <p>Supported positions:
 *
 * <ul>
 *   <li>{@code before_agent} — before the main DoWhile loop
 *   <li>{@code after_agent} — after the main DoWhile loop
 *   <li>{@code before_model} — before LLM_CHAT_COMPLETE (inside loop)
 *   <li>{@code after_model} — after LLM_CHAT_COMPLETE (inside loop)
 *   <li>{@code before_tool} — before FORK_JOIN_DYNAMIC (tool_call branch)
 *   <li>{@code after_tool} — after JOIN (tool_call branch)
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallbackConfig {

    /**
     * Callback position: before_agent, after_agent, before_model, after_model, before_tool,
     * after_tool.
     */
    private String position;

    /** Conductor worker task name (registered by the SDK). */
    private String taskName;
}
