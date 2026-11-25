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

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Data;

/** Configuration for the MCP server ({@code conductor.mcp.server.*}). */
@Data
@Component
@ConfigurationProperties(prefix = MCPServerProperties.PREFIX)
public class MCPServerProperties {

    public static final String PREFIX = "conductor.mcp.server";
    public static final String ENABLED = PREFIX + ".enabled";

    static final String SYNC_WAIT_SECONDS = "syncWaitSeconds";
    static final String MAX_SYNC_WAIT_SECONDS = "maxSyncWaitSeconds";
    static final String BLOCKING_CAP_SECONDS = "blockingCapSeconds";
    static final String POLL_INTERVAL_MS = "pollIntervalMs";
    static final String TASK_TTL = "taskTtl";

    /** Serves {@code POST /api/mcp}. Off by default. */
    private boolean enabled = false;

    /**
     * Workflow names exposed as MCP tools, in addition to metadata {@code mcp.enabled}. A set:
     * every listing checks each workflow against it.
     */
    private Set<String> exposedWorkflows = new LinkedHashSet<>();

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
     * The {@code ttlMs} returned with a task: how long the client may read it, from the task's
     * creation. It must cover how long the workflow runs plus the time the client needs to read the
     * result, and stay within how long the deployment keeps execution records. Sent in
     * milliseconds. Must be positive.
     */
    private Duration taskTtl = Duration.ofDays(7);

    /**
     * Checks the settings as soon as Spring binds them, so every bean that reads them gets checked
     * values whatever the wiring order. A server that is off is not checked.
     */
    @PostConstruct
    void validateWhenEnabled() {
        if (enabled) {
            validate();
        }
    }

    /**
     * Rejects settings the server cannot run with, at startup. Every rule for {@code
     * conductor.mcp.server.*} is here; the warning about the async request timeout is in {@link
     * MCPServerConfiguration}, because it reads a Spring property.
     *
     * @throws IllegalArgumentException naming the first invalid setting
     */
    void validate() {
        if (taskTtl == null || taskTtl.toMillis() <= 0) {
            throw invalid(TASK_TTL, "must be positive", taskTtl);
        }
        if (pollIntervalMs <= 0) {
            throw invalid(POLL_INTERVAL_MS, "must be positive", pollIntervalMs);
        }
        requireNotNegative(SYNC_WAIT_SECONDS, syncWaitSeconds);
        requireNotNegative(MAX_SYNC_WAIT_SECONDS, maxSyncWaitSeconds);
        requireNotNegative(BLOCKING_CAP_SECONDS, blockingCapSeconds);
    }

    private static void requireNotNegative(String name, int seconds) {
        if (seconds < 0) {
            throw invalid(name, "must not be negative", seconds);
        }
    }

    private static IllegalArgumentException invalid(String name, String rule, Object value) {
        return new IllegalArgumentException(PREFIX + "." + name + " " + rule + ", but is " + value);
    }
}
