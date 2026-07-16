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

import com.netflix.conductor.model.TaskModel;

/**
 * Output keys and result-shaping for the {@code AGENT} (conductor) task branch, so the task output
 * is consistent regardless of whether it settled on the initial call or after polling.
 */
public final class ConductorAgentResults {

    /** Child workflow execution id (used to poll/respond/cancel across retries). */
    public static final String KEY_EXECUTION_ID = "executionId";

    /** Name of the executed agent. */
    public static final String KEY_AGENT_NAME = "agentName";

    /** Normalized execution state. */
    public static final String KEY_STATE = "state";

    /** Bookkeeping: when the agent call started (epoch millis), for the absolute deadline guard. */
    public static final String KEY_STARTED_AT = "agentStartedAt";

    /** Bookkeeping: consecutive transient poll failures so far. */
    public static final String KEY_POLL_FAILURES = "agentPollFailures";

    /** True when the execution paused for external input (human answer / tool result). */
    public static final String KEY_WAITING = "waiting";

    /** The pending tool/human request surfaced while waiting. */
    public static final String KEY_PENDING_TOOL = "pendingTool";

    /** Latest/final text emitted by the agent. */
    public static final String KEY_TEXT = "text";

    /** Structured output of a completed run. */
    public static final String KEY_OUTPUT = "output";

    private ConductorAgentResults() {}

    /**
     * Writes the output of a completed execution: the workflow's structured {@code output} map is
     * surfaced verbatim under {@link #KEY_OUTPUT} and the final text under {@link #KEY_TEXT}.
     */
    public static void writeCompleted(TaskModel task, ConductorAgentExecution execution) {
        if (execution.getOutput() != null) {
            task.addOutput(KEY_OUTPUT, execution.getOutput());
        }
        if (execution.getText() != null) {
            task.addOutput(KEY_TEXT, execution.getText());
        }
    }
}
