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
package com.netflix.conductor.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Conductor workflow scheduler.
 *
 * <p>All properties are prefixed with {@code conductor.scheduler}. Defaults match the Orkes
 * Conductor scheduler where applicable, for easier convergence.
 */
@ConfigurationProperties(prefix = "conductor.scheduler")
public class SchedulerProperties {

    /** Enable or disable the scheduler module entirely. */
    private boolean enabled = true;

    /** Number of threads used for polling due schedules. */
    private int pollingThreadCount = 1;

    /** Milliseconds between polling cycles. */
    private long pollingInterval = 100;

    /** Maximum number of schedules to process per polling cycle. */
    private int pollBatchSize = 5;

    /** Default timezone for schedules that do not specify one. */
    private String schedulerTimeZone = "UTC";

    /**
     * Maximum number of execution history records to retain per schedule. Older records beyond this
     * limit are pruned during the cleanup job.
     */
    private int archivalMaxRecords = 5;

    /**
     * Threshold at which the cleanup job starts pruning records. Must be &gt; {@link
     * #archivalMaxRecords}.
     */
    private int archivalMaxRecordThreshold = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPollingThreadCount() {
        return pollingThreadCount;
    }

    public void setPollingThreadCount(int pollingThreadCount) {
        this.pollingThreadCount = pollingThreadCount;
    }

    public long getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public int getPollBatchSize() {
        return pollBatchSize;
    }

    public void setPollBatchSize(int pollBatchSize) {
        this.pollBatchSize = pollBatchSize;
    }

    public String getSchedulerTimeZone() {
        return schedulerTimeZone;
    }

    public void setSchedulerTimeZone(String schedulerTimeZone) {
        this.schedulerTimeZone = schedulerTimeZone;
    }

    public int getArchivalMaxRecords() {
        return archivalMaxRecords;
    }

    public void setArchivalMaxRecords(int archivalMaxRecords) {
        this.archivalMaxRecords = archivalMaxRecords;
    }

    public int getArchivalMaxRecordThreshold() {
        return archivalMaxRecordThreshold;
    }

    public void setArchivalMaxRecordThreshold(int archivalMaxRecordThreshold) {
        this.archivalMaxRecordThreshold = archivalMaxRecordThreshold;
    }
}
