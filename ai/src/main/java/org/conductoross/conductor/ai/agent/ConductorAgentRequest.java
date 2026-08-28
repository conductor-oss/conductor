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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Input for the {@code AGENT} task's {@code conductor} branch: a {@link ConductorAgentStartRequest}
 * plus the handful of AGENT-task-only orchestration knobs used by the portable annotated AGENT
 * worker.
 *
 * <p>Deliberately does NOT carry any A2A-shaped fields ({@code agentUrl}, {@code message}, {@code
 * parts}, {@code text}, {@code headers}, {@code streaming}, ...) — those belong to {@link
 * org.conductoross.conductor.ai.model.A2ACallRequest} and the {@code a2a} branch only.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ConductorAgentRequest extends ConductorAgentStartRequest {

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

    /**
     * Run the agent's tool calls as tasks in this workflow instead of handing them back.
     *
     * <p>Off by default: a workflow that already wires its own dispatch branch keeps working
     * unchanged. Turned on, the {@code AGENT} task stays {@code IN_PROGRESS} while each requested
     * tool is scheduled as an ordinary task — one per call, in parallel — and resumes the agent
     * with their results once they finish.
     */
    private Boolean autoRunTools;

    /**
     * Optional {@code tool name -> task name} overrides for {@link #autoRunTools}. A tool with no
     * entry runs as a task of its own name, so a worker registered for {@code get_revenue} serves
     * the agent's {@code get_revenue} tool with no configuration at all.
     */
    private Map<String, String> toolTaskNames;
}
