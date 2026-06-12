/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.redis.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;

import lombok.Data;

@Configuration
@ConfigurationProperties("conductor.redis")
public class RedisProperties {

    private final ConductorProperties conductorProperties;

    @Autowired
    public RedisProperties(ConductorProperties conductorProperties) {
        this.conductorProperties = conductorProperties;
    }

    /**
     * Data center region. If hosting on Amazon the value is something like us-east-1, us-west-2
     * etc.
     */
    private String dataCenterRegion = "us-east-1";

    private Duration maxTotalRetriesDuration =
            Duration.ofMillis(5 * 2000); // socket timeout * default attempts

    /**
     * Local rack / availability zone. For AWS deployments, the value is something like us-east-1a,
     * etc.
     */
    private String availabilityZone = "us-east-1c";

    /** The name of the redis / dynomite cluster */
    private String clusterName = "";

    /** Dynomite Cluster details. Format is host:port:rack separated by semicolon */
    private String hosts = null;

    private String user;

    /**
     * Sentinel master name. Required when conductor.db.type=redis_sentinel. This is the name of the
     * master as configured in the Sentinel nodes.
     */
    private String sentinelMasterName = "mymaster";

    /** The time to live in seconds for which the event execution will be persisted */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration eventExecutionPersistenceTTL = Duration.ofSeconds(60);

    /** The prefix used to prepend workflow data in redis */
    private String workflowNamespacePrefix = null;

    /** The prefix used to prepend keys for queues in redis */
    private String queueNamespacePrefix = null;

    /**
     * The domain name to be used in the key prefix for logical separation of workflow data and
     * queues in a shared redis setup
     */
    private String keyspaceDomain = null;

    /**
     * The maximum number of connections that can be managed by the connection pool on a given
     * instance
     */
    private int maxConnectionsPerHost = 10;

    /** Database number. Defaults to a 0. Can be anywhere from 0 to 15 */
    private int database = 0;

    /**
     * The maximum amount of time to wait for a connection to become available from the connection
     * pool
     */
    private Duration maxTimeoutWhenExhausted = Duration.ofMillis(800);

    /** The maximum retry attempts to use with this connection pool */
    private int maxRetryAttempts = 0;

    /** The read connection port to be used for connecting to dyno-queues */
    private int queuesNonQuorumPort = 22122;

    /** The time in seconds after which the in-memory task definitions cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    /** The time in seconds after which the in-memory metadata cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration metadataCacheRefreshInterval = Duration.ofSeconds(60);

    /**
     * Enable in-memory caching of workflow definitions. When enabled, reads are served from cache
     * with zero Redis I/O; the cache is refreshed synchronously on writes and periodically in the
     * background. Disabled by default because in multi-instance deployments only the writing
     * instance refreshes immediately — other instances see stale data until the next background
     * refresh. Enable this if you have a large number of workflow definitions and can accept
     * eventual consistency across instances.
     */
    private boolean workflowDefCacheEnabled = false;

    // Maximum number of idle connections to be maintained
    private int maxIdleConnections = 8;

    // Minimum number of idle connections to be maintained
    private int minIdleConnections = 5;

    private long minEvictableIdleTimeMillis = 180000;

    private long timeBetweenEvictionRunsMillis = 60000;

    private boolean testWhileIdle = true;

    private boolean fairness = true;

    private int numTestsPerEvictionRun = 3;

    private boolean ssl;

    private boolean ignoreSsl;

    private int replicasToSync = 1;

    private int replicaSyncWaitTime = 5_000;

    private long queueCacheExpireAfterAccessSeconds = 3600;

    private long queueCacheMaxSize = 4000;

    private ExecutionDAOProperties executionProperties = new ExecutionDAOProperties();

    public int getReplicasToSync() {
        return replicasToSync;
    }

    public void setReplicasToSync(int replicasToSync) {
        this.replicasToSync = replicasToSync;
    }

    public int getReplicaSyncWaitTime() {
        return replicaSyncWaitTime;
    }

    public void setReplicaSyncWaitTime(int replicaSyncWaitTime) {
        this.replicaSyncWaitTime = replicaSyncWaitTime;
    }

    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    public boolean isFairness() {
        return fairness;
    }

    public void setFairness(boolean fairness) {
        this.fairness = fairness;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    public int getMinIdleConnections() {
        return minIdleConnections;
    }

    public void setMinIdleConnections(int minIdleConnections) {
        this.minIdleConnections = minIdleConnections;
    }

    public int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    public void setMaxIdleConnections(int maxIdleConnections) {
        this.maxIdleConnections = maxIdleConnections;
    }

    public String getDataCenterRegion() {
        return dataCenterRegion;
    }

    public void setDataCenterRegion(String dataCenterRegion) {
        this.dataCenterRegion = dataCenterRegion;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public void setAvailabilityZone(String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public String getWorkflowNamespacePrefix() {
        return workflowNamespacePrefix;
    }

    public void setWorkflowNamespacePrefix(String workflowNamespacePrefix) {
        this.workflowNamespacePrefix = workflowNamespacePrefix;
    }

    public String getQueueNamespacePrefix() {
        return queueNamespacePrefix;
    }

    public void setQueueNamespacePrefix(String queueNamespacePrefix) {
        this.queueNamespacePrefix = queueNamespacePrefix;
    }

    public String getKeyspaceDomain() {
        return keyspaceDomain;
    }

    public void setKeyspaceDomain(String keyspaceDomain) {
        this.keyspaceDomain = keyspaceDomain;
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public Duration getMaxTimeoutWhenExhausted() {
        return maxTimeoutWhenExhausted;
    }

    public void setMaxTimeoutWhenExhausted(Duration maxTimeoutWhenExhausted) {
        this.maxTimeoutWhenExhausted = maxTimeoutWhenExhausted;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public int getQueuesNonQuorumPort() {
        return queuesNonQuorumPort;
    }

    public void setQueuesNonQuorumPort(int queuesNonQuorumPort) {
        this.queuesNonQuorumPort = queuesNonQuorumPort;
    }

    public Duration getTaskDefCacheRefreshInterval() {
        return taskDefCacheRefreshInterval;
    }

    public void setTaskDefCacheRefreshInterval(Duration taskDefCacheRefreshInterval) {
        this.taskDefCacheRefreshInterval = taskDefCacheRefreshInterval;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getQueuePrefix() {
        String prefix = getQueueNamespacePrefix() + "." + conductorProperties.getStack();
        if (getKeyspaceDomain() != null) {
            prefix = prefix + "." + getKeyspaceDomain();
        }
        return prefix;
    }

    public Duration getMetadataCacheRefreshInterval() {
        return metadataCacheRefreshInterval;
    }

    public void setMetadataCacheRefreshInterval(Duration metadataCacheRefreshInterval) {
        this.metadataCacheRefreshInterval = metadataCacheRefreshInterval;
    }

    public boolean isWorkflowDefCacheEnabled() {
        return workflowDefCacheEnabled;
    }

    public void setWorkflowDefCacheEnabled(boolean workflowDefCacheEnabled) {
        this.workflowDefCacheEnabled = workflowDefCacheEnabled;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public void setIgnoreSsl(boolean ignoreSsl) {
        this.ignoreSsl = ignoreSsl;
    }

    public boolean isIgnoreSsl() {
        return ignoreSsl;
    }

    public Duration getMaxTotalRetriesDuration() {
        return maxTotalRetriesDuration;
    }

    public void setMaxTotalRetriesDuration(Duration maxTotalRetriesDuration) {
        this.maxTotalRetriesDuration = maxTotalRetriesDuration;
    }

    public long getQueueCacheExpireAfterAccessSeconds() {
        return queueCacheExpireAfterAccessSeconds;
    }

    public void setQueueCacheExpireAfterAccessSeconds(long queueCacheExpireAfterAccessSeconds) {
        this.queueCacheExpireAfterAccessSeconds = queueCacheExpireAfterAccessSeconds;
    }

    public long getQueueCacheMaxSize() {
        return queueCacheMaxSize;
    }

    public void setQueueCacheMaxSize(long queueCacheMaxSize) {
        this.queueCacheMaxSize = queueCacheMaxSize;
    }

    public void setExecutionProperties(ExecutionDAOProperties executionProperties) {
        this.executionProperties = executionProperties;
    }

    public ExecutionDAOProperties getExecutionProperties() {
        return this.executionProperties;
    }

    @Data
    public static class ExecutionDAOProperties {
        private int workflowDefCacheMaxSize = 10_000;
        private int taskCacheMaxSize = 1000;
        private int taskCacheExpireAfterWriteSeconds = 10;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getSentinelMasterName() {
        return sentinelMasterName;
    }

    public void setSentinelMasterName(String sentinelMasterName) {
        this.sentinelMasterName = sentinelMasterName;
    }

    public Duration getEventExecutionPersistenceTTL() {
        return eventExecutionPersistenceTTL;
    }

    public void setEventExecutionPersistenceTTL(Duration eventExecutionPersistenceTTL) {
        this.eventExecutionPersistenceTTL = eventExecutionPersistenceTTL;
    }
}
