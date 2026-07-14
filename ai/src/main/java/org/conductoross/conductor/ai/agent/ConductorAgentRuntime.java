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

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the embedded Conductor-agent runtime (agentspan).
 *
 * <p>Implemented by the {@code agentspan-server} module and injected into the {@code AGENT} task's
 * {@code conductor} branch as an {@code Optional}: when the embedded runtime is not on the
 * classpath / not enabled, the {@code AGENT} (conductor) task fails terminally. Keeping this as an
 * interface in the {@code ai} module preserves Conductor's pluggable, interface-first design
 * without a compile dependency on the runtime implementation.
 */
public interface ConductorAgentRuntime {

    /** Starts a new agent execution and returns its initial snapshot. */
    ConductorAgentExecution start(ConductorAgentStartRequest request);

    /** Returns the current snapshot of an execution. */
    ConductorAgentExecution getStatus(String executionId);

    /** Supplies a response (human answer / tool result) to a waiting execution. */
    void respond(String executionId, Map<String, Object> message);

    /** Requests cancellation of an execution. */
    void cancel(String executionId, String reason);

    /** Lists the agents registered with the runtime. */
    List<ConductorAgentSummary> listAgents();
}
