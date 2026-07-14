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
package org.conductoross.conductor.ai.agent;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to start a Conductor-agent execution on the embedded runtime.
 *
 * <p>Built by {@link ConductorAgentDelegate} from the {@code AGENT} task input and handed to {@link
 * ConductorAgentRuntime#start(ConductorAgentStartRequest)}. The {@link #idempotencyKey} is derived
 * from durable task identity so retries of the same logical call are de-duplicated by the runtime.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConductorAgentStartRequest {

    /** Name of the registered agent to run. */
    private String agentName;

    /** Optional agent definition version; the runtime picks the latest when null. */
    private Integer agentVersion;

    /** The user prompt / instruction to start the agent with. */
    private String prompt;

    /** Additional context values passed to the agent run. */
    private Map<String, Object> context;

    /** Session id to associate the run with an existing conversation/session. */
    private String sessionId;

    /** Caller-supplied run id, when the workflow wants to control it. */
    private String runId;

    /** Deterministic idempotency key so at-least-once retries dedupe to a single run. */
    private String idempotencyKey;
}
