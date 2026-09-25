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
package org.conductoross.conductor.ai.model;

import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Input for the {@code CANCEL_AGENT} task: request cancellation of a running agent — a remote A2A
 * task ({@code agentType: "a2a"}) or a conductor agent execution ({@code agentType: "conductor"}).
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class A2ACancelRequest extends LLMWorkerInput {

    /**
     * Agent runtime/protocol. {@code "a2a"} (default) cancels a remote A2A task ({@code agentUrl} +
     * {@code taskId}); {@code "conductor"} terminates a conductor agent execution ({@code
     * executionId}).
     */
    private String agentType = "a2a";

    /** The remote agent's JSON-RPC endpoint URL (agentType {@code "a2a"}). */
    private String agentUrl;

    /** The id of the remote task to cancel (agentType {@code "a2a"}). */
    private String taskId;

    /** HTTP headers for the request (agentType {@code "a2a"}, optional). */
    private Map<String, String> headers;

    /**
     * Execution id of the conductor agent run to terminate (agentType {@code "conductor"}) — the
     * {@code executionId} an {@code AGENT} (conductor) task call returned.
     */
    private String executionId;

    /** Termination reason (agentType {@code "conductor"}). Optional. */
    private String reason;
}
