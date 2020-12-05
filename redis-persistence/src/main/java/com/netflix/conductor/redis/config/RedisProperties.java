/*
 * Copyright 2020 Netflix, Inc.
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(AnyRedisCondition.class)
public class RedisProperties {

    // TODO Are cluster and cluster name really different things?

    /**
     * name of the stack under which the app is running. e.g. devint, testintg, staging, prod etc.
     */
    @Value("${STACK:test}")
    private String stack;

    @Value("${APP_ID:conductor}")
    private String appId;

    /**
     * Data center region. If hosting on Amazon the value is something like us-east-1, us-west-2 etc.
     */
    @Value("${EC2_REGION:us-east-1}")
    private String region;

    /**
     * Availability zone / rack. For AWS deployments, the value is something like us-east-1a, etc.
     */
    @Value("${EC2_AVAILABILITY_ZONE:us-east-1c}")
    private String availabilityZone;

    @Value("${workflow.dynomite.cluster:#{null}}")
    private String cluster;

    @Value("${workflow.dynomite.cluster.name:}")
    private String clusterName;

    @Value("${workflow.dynomite.cluster.hosts:#{null}}")
    private String hosts;

    @Value("${workflow.namespace.prefix:#{null}}")
    private String workflowNamespacePrefix;

    @Value("${workflow.namespace.queue.prefix:#{null}}")
    private String queueNamespacePrefix;

    @Value("${workflow.dyno.keyspace.domain:#{null}}")
    private String keyspaceDomain;

    @Value("${workflow.dynomite.connection.maxConnsPerHost:10}")
    private int maxConnectionsPerHost;

    @Value("${queues.dynomite.nonQuorum.port:22122}")
    private int queuesNonQuorumPort;

    @Value("${workflow.dyno.queue.sharding.strategy:#{T(com.netflix.conductor.redis.dynoqueue.RedisQueuesShardingStrategyProvider).ROUND_ROBIN_STRATEGY}}")
    private String queueShardingStrategy;

    /**
     * the refresh time for the in-memory task definition cache
     */
    @Value("${conductor.taskdef.cache.refresh.time.seconds:60}")
    private int taskDefCacheRefreshTimeSecs;

    /**
     * The time to live in seconds of the event execution persisted
     */
    @Value("${workflow.event.execution.persistence.ttl.seconds:0}")
    private int eventExecutionPersistenceTTLSecs;

    public String getStack() {
        return stack;
    }

    public String getAppId() {
        return appId;
    }

    public String getRegion() {
        return region;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public String getCluster() {
        return cluster;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getHosts() {
        return hosts;
    }

    public String getWorkflowNamespacePrefix() {
        return workflowNamespacePrefix;
    }

    public String getQueueNamespacePrefix() {
        return queueNamespacePrefix;
    }

    public String getDomain() {
        return keyspaceDomain;
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public int getNonQuorumPort() {
        return queuesNonQuorumPort;
    }

    public String getQueueShardingStrategy() {
        return queueShardingStrategy;
    }

    public int getTaskDefRefreshTimeSecs() {
        return taskDefCacheRefreshTimeSecs;
    }

    public int getEventExecutionPersistenceTTL() {
        return eventExecutionPersistenceTTLSecs;
    }

    public String getQueuePrefix() {
        String prefix = getQueueNamespacePrefix() + "." + getStack();
        if (getDomain() != null) {
            prefix = prefix + "." + getDomain();
        }
        return prefix;
    }
}
