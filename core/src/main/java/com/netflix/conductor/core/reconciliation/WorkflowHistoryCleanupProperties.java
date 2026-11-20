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
package com.netflix.conductor.core.reconciliation;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("conductor.workflow-history-cleanup")
public class WorkflowHistoryCleanupProperties {

    /** Master switch for the cleanup batch. Disabled by default. */
    private boolean enabled = false;

    /** Cron expression that drives the cleanup batch. Defaults to top-of-the-hour. */
    private String cron = "0 0 * * * *";

    /** Time zone used to interpret the cron expression. */
    private String zone = "UTC";

    /**
     * Retention window (in days). Terminal workflows whose endTime is older than this threshold are
     * eligible for deletion. {@link
     * com.netflix.conductor.dao.IndexDAO#searchArchivableWorkflows(String, long)} returns a one-day
     * window per call, so this value is treated as the lower bound of the iteration loop.
     */
    private int retentionDays = 30;

    /**
     * Number of additional days to catch up on, starting from {@code retentionDays}. Because the
     * underlying index query is bucketed by day, the batch walks the range {@code [retentionDays,
     * retentionDays + catchUpDays - 1]}. This lets a single run recover from missed schedules. To
     * do a one-time backfill of historical data, temporarily set this to a large value (e.g. 3650)
     * and then restore the default after the initial drain.
     */
    private int catchUpDays = 7;

    /**
     * Hard cap on iterations per day to prevent infinite loops. Each iteration removes at most
     * ~1000 rows.
     */
    private int maxIterationsPerDay = 200;

    /**
     * Size of the LRU cache that tracks recently-processed workflow ids. The index query typically
     * returns the same page until the asynchronous index delete catches up; the cache prevents
     * redundant delete attempts in that window. Sized for the page size (~1000) with headroom.
     */
    private int processedCacheSize = 5_000;

    /** Pause between iterations to spread DB / index load. */
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration batchPause = Duration.ofMillis(500);

    /**
     * Wait inserted when the same ids keep coming back, to give the asynchronous index removal a
     * chance to land. Bounded by {@code maxIterationsPerDay}.
     */
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration indexRefreshWait = Duration.ofSeconds(2);

    /**
     * Identifier passed to {@link com.netflix.conductor.core.sync.Lock} when acquiring the cleanup
     * lock.
     */
    private String lockId = "workflow-history-cleanup";

    /**
     * Upper bound on lock holding time. If the batch ever exceeds this, another instance may take
     * over, so give it some headroom over the expected runtime.
     */
    private Duration lockLeaseTime = Duration.ofHours(2);

    /**
     * Name of the OpenSearch / Elasticsearch index that holds workflow documents. Leave blank to
     * compose it from {@code conductor.elasticsearch.index-prefix} as {@code {prefix}_workflow}.
     */
    private String workflowIndexName = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getCatchUpDays() {
        return catchUpDays;
    }

    public void setCatchUpDays(int catchUpDays) {
        this.catchUpDays = catchUpDays;
    }

    public int getMaxIterationsPerDay() {
        return maxIterationsPerDay;
    }

    public void setMaxIterationsPerDay(int maxIterationsPerDay) {
        this.maxIterationsPerDay = maxIterationsPerDay;
    }

    public int getProcessedCacheSize() {
        return processedCacheSize;
    }

    public void setProcessedCacheSize(int processedCacheSize) {
        this.processedCacheSize = processedCacheSize;
    }

    public Duration getBatchPause() {
        return batchPause;
    }

    public void setBatchPause(Duration batchPause) {
        this.batchPause = batchPause;
    }

    public Duration getIndexRefreshWait() {
        return indexRefreshWait;
    }

    public void setIndexRefreshWait(Duration indexRefreshWait) {
        this.indexRefreshWait = indexRefreshWait;
    }

    public String getLockId() {
        return lockId;
    }

    public void setLockId(String lockId) {
        this.lockId = lockId;
    }

    public Duration getLockLeaseTime() {
        return lockLeaseTime;
    }

    public void setLockLeaseTime(Duration lockLeaseTime) {
        this.lockLeaseTime = lockLeaseTime;
    }

    public String getWorkflowIndexName() {
        return workflowIndexName;
    }

    public void setWorkflowIndexName(String workflowIndexName) {
        this.workflowIndexName = workflowIndexName;
    }
}
