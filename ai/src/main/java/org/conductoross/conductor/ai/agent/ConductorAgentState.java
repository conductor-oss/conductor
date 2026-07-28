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

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Lifecycle state derived from the Conductor agent's child workflow execution.
 *
 * <p>Mirrors the coarse states the {@code AGENT} (conductor) task branch routes on: an execution is
 * either still {@link #RUNNING}, paused {@link #WAITING} for external input (human answer or tool
 * result), or has reached one of the terminal states {@link #COMPLETED}/{@link #FAILED}/{@link
 * #CANCELED}.
 */
public enum ConductorAgentState {
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELED;

    /** Normalize native workflow statuses into the portable agent lifecycle. */
    @JsonCreator
    public static ConductorAgentState fromStatus(String status) {
        if (status == null) {
            return RUNNING;
        }
        return switch (status) {
            case "WAITING" -> WAITING;
            case "COMPLETED" -> COMPLETED;
            case "FAILED", "TIMED_OUT" -> FAILED;
            case "CANCELED", "TERMINATED" -> CANCELED;
            default -> RUNNING;
        };
    }

    /** A settled outcome the execution will not move on from. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }

    /**
     * Paused pending external input (human answer / tool result). The Conductor task completes and
     * surfaces the pending request so the workflow can resume with another {@code AGENT} call.
     */
    public boolean isInterrupted() {
        return this == WAITING;
    }
}
