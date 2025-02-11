package com.netflix.conductor.sqlite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("conductor.sqlite")
public class SqliteProperties {


    /** The time (in seconds) after which the in-memory task definitions cache will be refreshed */
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    private Integer deadlockRetryMax = 3;

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
}
