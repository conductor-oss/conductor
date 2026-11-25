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
package org.conductoross.conductor.ai.mcp;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** Configuration of Conductor as an MCP client ({@code CALL_MCP_TOOL}). */
@Data
@Component
@ConfigurationProperties(prefix = "conductor.mcp.client")
public class MCPClientProperties {

    public static final Duration DEFAULT_MAX_DURATION = Duration.ofHours(24);

    /**
     * The longest time a call that the server runs as a task is polled, from the task's start. The
     * server's {@code ttlMs} shortens it when smaller; when the server gives none, this is the
     * deadline. Must be positive, and at most 30 days.
     */
    private Duration maxDuration = DEFAULT_MAX_DURATION;
}
