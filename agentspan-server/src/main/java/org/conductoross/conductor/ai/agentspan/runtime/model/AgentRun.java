/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full details of a single agent execution.
 *
 * <p>Returned by {@code GET /api/agent/{id}}. Combines execution metadata, the full task list (for
 * sub-workflow traversal by the SDK), and pre-computed token usage for this execution level.
 *
 * <p>Token usage covers only LLM tasks in <em>this</em> execution. The SDK aggregates across the
 * full sub-agent tree by recursively fetching each {@code SUB_WORKFLOW} task's {@code
 * subWorkflowId}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentRun {

    private String executionId;
    private String agentName;
    private int version;
    private String status;
    private long startTime;
    private long endTime;
    private Map<String, Object> input;
    private Map<String, Object> output;

    /** Token usage for LLM tasks in this execution only — null if none ran. */
    private TokenUsage tokenUsage;

    /** All tasks in this execution. */
    private List<TaskDetail> tasks;

    // ── Inner types ──────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TaskDetail {
        private String taskType;
        private String referenceTaskName;
        private String status;

        /** Populated for {@code SUB_WORKFLOW} tasks — the child workflow ID. */
        private String subWorkflowId;

        private Map<String, Object> outputData;
    }
}
