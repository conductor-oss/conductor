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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A lightweight descriptor of an agent registered with the embedded Conductor-agent runtime, as
 * returned by {@link ConductorAgentRuntime#listAgents()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConductorAgentSummary {

    /** Registered agent name (unique key used to start executions). */
    private String name;

    /** Definition version. */
    private int version;

    /** Runtime/framework type the agent was compiled from. */
    private String type;

    /** Free-form tags for grouping/discovery. */
    private List<String> tags;

    /** Creation timestamp (epoch millis). */
    private Long createTime;

    /** Last-update timestamp (epoch millis). */
    private Long updateTime;

    /** Human-readable description. */
    private String description;

    /** Content checksum of the agent definition, for change detection. */
    private String checksum;
}
