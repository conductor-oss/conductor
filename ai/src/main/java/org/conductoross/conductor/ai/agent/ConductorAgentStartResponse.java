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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI-module response returned after starting a native Conductor agent execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConductorAgentStartResponse {

    private String executionId;
    private String agentName;
    private List<String> requiredWorkers;

    /**
     * Outcome of the start call, when the runtime already knows it. Runtimes with no status API
     * (Bedrock streams the whole response inside the invoke) finish or block on a tool before
     * {@code startAgent} returns, and reporting that here is what lets the result land in the task
     * output instead of in a map the next poll may not reach. Null means the run is in flight and
     * the caller should poll.
     */
    private ConductorAgentState state;

    /** Final output, when {@link #state} is already {@code COMPLETED}. */
    private Map<String, Object> output;

    /** Pending tool call, when {@link #state} is already {@code WAITING}. */
    private Map<String, Object> pendingTool;

    private List<Map<String, Object>> pendingTools;

    private String pendingToolName;

    /** Failure detail, when {@link #state} is already {@code FAILED}. */
    private String reasonForIncompletion;
}
