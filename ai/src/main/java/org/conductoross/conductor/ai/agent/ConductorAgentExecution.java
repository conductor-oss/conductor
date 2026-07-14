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

import lombok.Builder;
import lombok.Data;

/**
 * An immutable snapshot of a Conductor-agent execution returned by {@link ConductorAgentRuntime}.
 *
 * <p>{@link #state} drives how {@link ConductorAgentDelegate} routes the owning Conductor task:
 * running snapshots keep it polling, {@link ConductorAgentState#WAITING} snapshots complete it and
 * surface {@link #pendingTool}/{@link #text}, and terminal snapshots map to the matching task
 * status (with {@link #output}/{@link #reasonForIncompletion}).
 */
@Data
@Builder
public class ConductorAgentExecution {

    /** Runtime-assigned execution id, used for polling/responding/cancelling. */
    private String executionId;

    /** Name of the agent being executed. */
    private String agentName;

    /** Session id this execution belongs to. */
    private String sessionId;

    /** Current lifecycle state. */
    private ConductorAgentState state;

    /** Structured output of a completed run. */
    private Map<String, Object> output;

    /** Latest text emitted by the agent (partial while running, final when complete). */
    private String text;

    /** When {@link #state} is {@link ConductorAgentState#WAITING}: the pending tool/human request. */
    private Map<String, Object> pendingTool;

    /** Failure/cancel explanation for terminal non-completed states. */
    private String reasonForIncompletion;

    /** Whether the run finished successfully. */
    public boolean isComplete() {
        return state == ConductorAgentState.COMPLETED;
    }

    /** Whether the run is still in progress. */
    public boolean isRunning() {
        return state == ConductorAgentState.RUNNING;
    }

    /** Whether the run is paused pending external input (human answer / tool result). */
    public boolean isWaiting() {
        return state == ConductorAgentState.WAITING;
    }
}
