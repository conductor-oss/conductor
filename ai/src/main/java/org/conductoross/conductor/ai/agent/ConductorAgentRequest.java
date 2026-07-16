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

import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Input for the {@code AGENT} task's {@code conductor} branch: an {@link AgentStartRequest} (the
 * same DTO {@code POST /api/agent/start} takes) plus the handful of AGENT-task-only orchestration
 * knobs that don't belong on that REST contract — resuming an in-flight run and the durability
 * guards ({@link org.conductoross.conductor.ai.a2a.AgentTask} mirrors these for its A2A branch).
 *
 * <p>Deliberately does NOT carry any A2A-shaped fields ({@code agentUrl}, {@code message}, {@code
 * parts}, {@code text}, {@code headers}, {@code streaming}, ...) — those belong to {@link
 * org.conductoross.conductor.ai.model.A2ACallRequest} and the {@code a2a} branch only.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ConductorAgentRequest extends AgentStartRequest {

    /**
     * Execution id of an in-flight run. When set, the call resumes that execution (e.g. to provide
     * a requested human/tool response) instead of starting a new one.
     */
    private String executionId;

    /** Poll interval (seconds) while the run is not terminal. Defaults to 5. */
    private Integer pollIntervalSeconds;

    /**
     * Absolute deadline (seconds) for the run to reach a terminal state. Past this, the Conductor
     * task fails terminally rather than waiting forever. Defaults to 86400 (24h).
     */
    private Integer maxDurationSeconds;

    /**
     * Max consecutive transient poll failures (executor unreachable) before failing terminally.
     * Defaults to 30.
     */
    private Integer maxPollFailures;
}
