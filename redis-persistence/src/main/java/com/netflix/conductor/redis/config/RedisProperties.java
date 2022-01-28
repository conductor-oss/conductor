/*
 * Copyright 2021 Netflix, Inc.
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

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.dynoqueue.RedisQueuesShardingStrategyProvider;
import com.netflix.dyno.connectionpool.RetryPolicy.RetryPolicyFactory;
import com.netflix.dyno.connectionpool.impl.RetryNTimes;
import com.netflix.dyno.connectionpool.impl.RunOnce;

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

    /**
     * Local rack / availability zone. For AWS deployments, the value is something like us-east-1a,
     * etc.
     */
    private String availabilityZone = "us-east-1c";

    /** The name of the redis / dynomite cluster */
    private String clusterName = "";

    /** Dynomite Cluster details. Format is host:port:rack separated by semicolon */
    private String hosts = null;

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

    /**
     * The maximum amount of time to wait for a connection to become available from the connection
     * pool
     */
    private Duration maxTimeoutWhenExhausted = Duration.ofMillis(800);

    /** The maximum retry attempts to use with this connection pool */
    private int maxRetryAttempts = 0;

    /** The read connection port to be used for connecting to dyno-queues */
    private int queuesNonQuorumPort = 22122;

    /** The sharding strategy to be used for the dyno queue configuration */
    private String queueShardingStrategy = RedisQueuesShardingStrategyProvider.ROUND_ROBIN_STRATEGY;

    /** The time in seconds after which the in-memory task definitions cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    /** The time to live in seconds for which the event execution will be persisted */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration eventExecutionPersistenceTTL = Duration.ofSeconds(60);

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

    public String getQueueShardingStrategy() {
        return queueShardingStrategy;
    }

    public void setQueueShardingStrategy(String queueShardingStrategy) {
        this.queueShardingStrategy = queueShardingStrategy;
    }

    public Duration getTaskDefCacheRefreshInterval() {
        return taskDefCacheRefreshInterval;
    }

    public void setTaskDefCacheRefreshInterval(Duration taskDefCacheRefreshInterval) {
        this.taskDefCacheRefreshInterval = taskDefCacheRefreshInterval;
    }

    public Duration getEventExecutionPersistenceTTL() {
        return eventExecutionPersistenceTTL;
    }

    public void setEventExecutionPersistenceTTL(Duration eventExecutionPersistenceTTL) {
        this.eventExecutionPersistenceTTL = eventExecutionPersistenceTTL;
    }

    public String getQueuePrefix() {
        String prefix = getQueueNamespacePrefix() + "." + conductorProperties.getStack();
        if (getKeyspaceDomain() != null) {
            prefix = prefix + "." + getKeyspaceDomain();
        }
        return prefix;
    }

    public RetryPolicyFactory getConnectionRetryPolicy() {
        if (getMaxRetryAttempts() == 0) {
            return RunOnce::new;
        } else {
            return () -> new RetryNTimes(maxRetryAttempts, false);
        }
    }
}
