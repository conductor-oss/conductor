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

/** Tool definition DTO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolConfig {

    private String name;
    private String description;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;

    /**
     * Tool type: worker, http, api, mcp, generate_image, generate_audio, generate_video,
     * generate_pdf, rag_index, rag_search.
     */
    @Builder.Default private String toolType = "worker";

    @Builder.Default private boolean approvalRequired = false;

    private Integer timeoutSeconds;

    private Integer maxCalls;

    /** Type-specific configuration (e.g., server_url for MCP, url/method/headers for HTTP). */
    private Map<String, Object> config;

    /** Tool-level guardrails. */
    private List<GuardrailConfig> guardrails;

    /**
     * When {@code true}, this tool's worker is registered under a per-execution domain so
     * concurrent executions of the same agent cannot steal each other's task results. Matches
     * {@code @tool(stateful=True)} in the Python SDK.
     */
    private boolean stateful;
}
