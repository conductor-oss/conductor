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
package org.conductoross.conductor.ai.models;

import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Input for the {@code GET_AGENT_CARD} task: discover a remote agent's capabilities and skills. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class A2AAgentCardRequest extends LLMWorkerInput {

    /** The remote agent's base URL, or a direct URL to its agent-card JSON. */
    private String agentUrl;

    /** HTTP headers for the request (optional). */
    private Map<String, String> headers;
}
