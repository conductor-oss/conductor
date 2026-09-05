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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI-module status snapshot for a native Conductor agent execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConductorAgentStatusResponse {

    private String executionId;
    private ConductorAgentState status;

    @JsonProperty("isComplete")
    private boolean complete;

    @JsonProperty("isRunning")
    private boolean running;

    @JsonProperty("isWaiting")
    private boolean waiting;

    private Map<String, Object> output;
    private String reasonForIncompletion;

    /**
     * First outstanding tool call. Retained because task output, the UI and existing workflows all
     * read it; {@link #pendingTools} is the complete set.
     */
    private Map<String, Object> pendingTool;

    /**
     * Every tool call the agent is blocked on. A model may ask for several independent tools in one
     * turn, and all of them must be answered before the run resumes — so reporting only the first
     * leaves the caller unable to execute the rest.
     */
    private List<Map<String, Object>> pendingTools;

    /**
     * Tool calls the platform ran by itself during this turn - web search, code interpreter, file
     * search - with the input each was given. Unlike pendingTools these were never handed back to
     * the workflow, so without recording them the run's only trace is its final answer.
     */
    private List<Map<String, Object>> executedTools;

    private String pendingToolName;
    private String pendingToolTaskRefName;
    private long startTime;
    private long endTime;
}
