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
package org.conductoross.conductor.ai.mcp.server;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** Configuration for the MCP server ({@code conductor.mcp.server.*}). */
@Data
@Component
@ConfigurationProperties(prefix = "conductor.mcp.server")
public class McpServerProperties {

    /** Serves {@code POST /api/mcp}. Off by default. */
    private boolean enabled = false;

    /** Workflow names exposed as MCP tools, in addition to metadata {@code mcp.enabled}. */
    private List<String> exposedWorkflows = new ArrayList<>();

    /** Exposes every workflow. Off by default. */
    private boolean exposeAll = false;

    /**
     * How long {@code tools/call} waits for the workflow before it answers with a task (Tasks
     * clients) or keeps waiting (other clients). A workflow can override it with metadata {@code
     * mcp.syncWaitSeconds}, from 0 to {@link #maxSyncWaitSeconds}.
     */
    private int syncWaitSeconds = 10;

    /** Upper bound for a tool's own {@code mcp.syncWaitSeconds} (workflow or agent metadata). */
    private int maxSyncWaitSeconds = 25;

    /**
     * Longest time {@code tools/call} holds a request open for a client that does not declare
     * Tasks. Keep it below the servlet async request timeout (30 s on Tomcat by default).
     */
    private int blockingCapSeconds = 25;

    /** The {@code pollIntervalMs} hint returned with a task. */
    private long pollIntervalMs = 5000;

    /**
     * The {@code ttlMs} returned with a task: how long the client may read it. Keep it within how
     * long the deployment keeps execution records.
     */
    private long taskTtlMs = 24L * 60 * 60 * 1000;
}
