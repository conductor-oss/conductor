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
package com.netflix.conductor.sqlite.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.sqlite")
public class SqliteProperties {

    /** The time (in seconds) after which the in-memory task definitions cache will be refreshed */
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    private Integer deadlockRetryMax = 3;

    private boolean onlyIndexOnStatusChange = false;

    private Integer asyncMaxPoolSize = 10;

    private Integer asyncWorkerQueueSize = 10;

    public Duration getTaskDefCacheRefreshInterval() {
        return taskDefCacheRefreshInterval;
    }

    public void setTaskDefCacheRefreshInterval(Duration taskDefCacheRefreshInterval) {
        this.taskDefCacheRefreshInterval = taskDefCacheRefreshInterval;
    }

    public Integer getDeadlockRetryMax() {
        return deadlockRetryMax;
    }

    public void setDeadlockRetryMax(Integer deadlockRetryMax) {
        this.deadlockRetryMax = deadlockRetryMax;
    }

    public int getAsyncMaxPoolSize() {
        return asyncMaxPoolSize;
    }

    public int getAsyncWorkerQueueSize() {
        return asyncWorkerQueueSize;
    }

    public boolean getOnlyIndexOnStatusChange() {
        return onlyIndexOnStatusChange;
    }
}
