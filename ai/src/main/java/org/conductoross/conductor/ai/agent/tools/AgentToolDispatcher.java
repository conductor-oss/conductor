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
package org.conductoross.conductor.ai.agent.tools;

import java.util.List;
import java.util.Map;

/**
 * Runs the tools a hosted agent asked for, as real Conductor work.
 *
 * <p>An agent on a platform like Microsoft Foundry can only ask: it holds the tool's schema but has
 * no way to execute it. Without this, the owning {@code AGENT} task completes carrying the request
 * and the workflow author has to hand-wire a dispatch branch and a resume task for every agent.
 * With it, the tool calls are scheduled as ordinary tasks — workers pick them up, each gets its own
 * retries and timeout, and they appear in the execution graph under the agent that asked for them.
 *
 * <p>Implementations are expected to be stateless with respect to a batch: {@code status} takes
 * only the handle returned by {@code dispatch}, so a poll landing on another replica still
 * resolves.
 */
public interface AgentToolDispatcher {

    /**
     * Schedules the given tool calls and returns immediately with a handle.
     *
     * @param request what to run and on whose behalf
     * @return a handle in {@code RUNNING} state
     */
    AgentToolDispatch dispatch(Request request);

    /** Current state of a batch previously returned by {@link #dispatch}. */
    AgentToolDispatch status(String dispatchId);

    /**
     * Stops a batch that is no longer wanted — the owning agent task was cancelled, or its workflow
     * terminated. Best effort: the caller is ending regardless, so a failure here is logged rather
     * than raised.
     */
    void cancel(String dispatchId);

    /**
     * @param parentWorkflowId the workflow whose AGENT task is waiting
     * @param parentTaskId id of that AGENT task, so the batch is linked to the task it belongs to
     *     and does not outlive it
     * @param taskRefName reference name of that AGENT task, used to name the batch readably
     * @param executionId the agent execution the tools belong to
     * @param toolCalls one entry per call: {@code tool_name}, {@code tool_call_id}, {@code
     *     arguments} (a JSON string)
     * @param toolTaskNames optional {@code tool_name -> task name} overrides; a tool with no entry
     *     is run as a task of its own name
     */
    record Request(
            String parentWorkflowId,
            String parentTaskId,
            String taskRefName,
            String executionId,
            List<Map<String, Object>> toolCalls,
            Map<String, String> toolTaskNames,
            int maxToolTurns) {}
}
