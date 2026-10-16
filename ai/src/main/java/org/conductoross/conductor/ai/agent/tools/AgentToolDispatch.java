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

import java.util.Map;

/**
 * State of one in-flight batch of agent tool calls.
 *
 * @param dispatchId handle to poll, and what the owning task records so a later poll — possibly on
 *     another replica — can find the batch again
 * @param state whether the batch is still running, done, or failed
 * @param resultsByToolCallId one result per {@code tool_call_id}, populated when {@code COMPLETED}
 * @param reason why it failed, when {@code FAILED}
 */
public record AgentToolDispatch(
        String dispatchId,
        AgentToolDispatch.State state,
        Map<String, Object> resultsByToolCallId,
        String reason) {

    public enum State {
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static AgentToolDispatch running(String dispatchId) {
        return new AgentToolDispatch(dispatchId, State.RUNNING, Map.of(), null);
    }

    public static AgentToolDispatch completed(
            String dispatchId, Map<String, Object> resultsByToolCallId) {
        return new AgentToolDispatch(dispatchId, State.COMPLETED, resultsByToolCallId, null);
    }

    public static AgentToolDispatch failed(String dispatchId, String reason) {
        return new AgentToolDispatch(dispatchId, State.FAILED, Map.of(), reason);
    }
}
