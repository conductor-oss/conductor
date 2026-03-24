/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Workflow Message Queue (WMQ) feature.
 *
 * <p>All properties are under the {@code conductor.workflow-message-queue} prefix.
 */
@ConfigurationProperties("conductor.workflow-message-queue")
public class WorkflowMessageQueueProperties {

    /**
     * Master switch for the WMQ feature. When false (default), no WMQ beans are registered and the
     * push endpoint does not exist.
     */
    private boolean enabled = false;

    /**
     * Maximum number of messages allowed in a single workflow's queue at one time. Push attempts
     * that exceed this limit will be rejected with an error.
     */
    private int maxQueueSize = 1000;

    /** TTL in seconds applied to the Redis key. Reset on every push. Default is 24 hours. */
    private long ttlSeconds = 86400;

    /**
     * Server-side upper bound on {@code batchSize} for any single PULL_WORKFLOW_MESSAGES execution.
     * Prevents runaway batch sizes.
     */
    private int maxBatchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }
}
