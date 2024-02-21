/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.postgres.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("conductor.postgres")
public class PostgresProperties {

    /** The time in seconds after which the in-memory task definitions cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    private Integer deadlockRetryMax = 3;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration pollDataFlushInterval = Duration.ofMillis(0);

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration pollDataCacheValidityPeriod = Duration.ofMillis(0);

    private boolean experimentalQueueNotify = false;

    private Integer experimentalQueueNotifyStalePeriod = 5000;

    private boolean onlyIndexOnStatusChange = false;

    public String schema = "public";

    public boolean allowFullTextQueries = true;

    public boolean allowJsonQueries = true;

    /** The maximum number of threads allowed in the async pool */
    private int asyncMaxPoolSize = 12;

    /** The size of the queue used for holding async indexing tasks */
    private int asyncWorkerQueueSize = 100;

    public boolean getExperimentalQueueNotify() {
        return experimentalQueueNotify;
    }

    public void setExperimentalQueueNotify(boolean experimentalQueueNotify) {
        this.experimentalQueueNotify = experimentalQueueNotify;
    }

    public Integer getExperimentalQueueNotifyStalePeriod() {
        return experimentalQueueNotifyStalePeriod;
    }

    public void setExperimentalQueueNotifyStalePeriod(Integer experimentalQueueNotifyStalePeriod) {
        this.experimentalQueueNotifyStalePeriod = experimentalQueueNotifyStalePeriod;
    }

    public Duration getTaskDefCacheRefreshInterval() {
        return taskDefCacheRefreshInterval;
    }

    public void setTaskDefCacheRefreshInterval(Duration taskDefCacheRefreshInterval) {
        this.taskDefCacheRefreshInterval = taskDefCacheRefreshInterval;
    }

    public boolean getOnlyIndexOnStatusChange() {
        return onlyIndexOnStatusChange;
    }

    public void setOnlyIndexOnStatusChange(boolean onlyIndexOnStatusChange) {
        this.onlyIndexOnStatusChange = onlyIndexOnStatusChange;
    }

    public Integer getDeadlockRetryMax() {
        return deadlockRetryMax;
    }

    public void setDeadlockRetryMax(Integer deadlockRetryMax) {
        this.deadlockRetryMax = deadlockRetryMax;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public boolean getAllowFullTextQueries() {
        return allowFullTextQueries;
    }

    public void setAllowFullTextQueries(boolean allowFullTextQueries) {
        this.allowFullTextQueries = allowFullTextQueries;
    }

    public boolean getAllowJsonQueries() {
        return allowJsonQueries;
    }

    public void setAllowJsonQueries(boolean allowJsonQueries) {
        this.allowJsonQueries = allowJsonQueries;
    }

    public int getAsyncWorkerQueueSize() {
        return asyncWorkerQueueSize;
    }

    public void setAsyncWorkerQueueSize(int asyncWorkerQueueSize) {
        this.asyncWorkerQueueSize = asyncWorkerQueueSize;
    }

    public int getAsyncMaxPoolSize() {
        return asyncMaxPoolSize;
    }

    public void setAsyncMaxPoolSize(int asyncMaxPoolSize) {
        this.asyncMaxPoolSize = asyncMaxPoolSize;
    }

    public Duration getPollDataFlushInterval() {
        return pollDataFlushInterval;
    }

    public void setPollDataFlushInterval(Duration interval) {
        this.pollDataFlushInterval = interval;
    }

    public Duration getPollDataCacheValidityPeriod() {
        return pollDataCacheValidityPeriod;
    }

    public void setPollDataCacheValidityPeriod(Duration period) {
        this.pollDataCacheValidityPeriod = period;
    }
}
