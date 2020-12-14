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
package com.netflix.conductor.cassandra.config;

import com.datastax.driver.core.ConsistencyLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "db", havingValue = "cassandra")
public class CassandraProperties {

    @Value("${workflow.cassandra.host:127.0.0.1}")
    private String hostAddress;

    @Value("${workflow.cassandra.port:9142}")
    private int port;

    @Value("${workflow.cassandra.cluster:}")
    private String cluster;

    @Value("${workflow.cassandra.keyspace:conductor}")
    private String keyspace;

    @Value("${workflow.cassandra.shard.size:100}")
    private int shardSize;

    @Value("${workflow.cassandra.replication.strategy:SimpleStrategy}")
    private String replicationStrategy;

    @Value("${workflow.cassandra.replication.factor.key:replication_factor}")
    private String replicationFactorKey;

    @Value("${workflow.cassandra.replication.factor.value:3}")
    private int replicationFactorValue;

    @Value("${workflow.cassandra.read.consistency.level:#{T(com.datastax.driver.core.ConsistencyLevel).LOCAL_QUORUM.name()}}")
    private String readConsistencyLevel;

    @Value("${workflow.cassandra.write.consistency.level:#{T(com.datastax.driver.core.ConsistencyLevel).LOCAL_QUORUM.name()}}")
    private String writeConsistencyLevel;

    /**
     * the refresh time for the in-memory task definition cache
     */
    @Value("${conductor.taskdef.cache.refresh.time.seconds:60}")
    private int taskDefCacheRefreshTimeSecs;

    /**
     * the refresh time for the in-memory event handler cache
     */
    @Value("${conductor.eventhandler.cache.refresh.time.seconds:60}")
    private int eventHandlerRefreshTimeSecs;

    /**
     * The time to live in seconds of the event execution persisted
     */
    @Value("${workflow.event.execution.persistence.ttl.seconds:0}")
    private int eventExecutionPersistenceTTLSecs;

    public String getHostAddress() {
        return hostAddress;
    }

    public int getPort() {
        return port;
    }

    public String getCassandraCluster() {
        return cluster;
    }

    public String getCassandraKeyspace() {
        return keyspace;
    }

    public int getShardSize() {
        return shardSize;
    }

    public String getReplicationStrategy() {
        return replicationStrategy;
    }

    public String getReplicationFactorKey() {
        return replicationFactorKey;
    }

    public int getReplicationFactorValue() {
        return replicationFactorValue;
    }

    public ConsistencyLevel getReadConsistencyLevel() {
        return ConsistencyLevel.valueOf(readConsistencyLevel);
    }

    public ConsistencyLevel getWriteConsistencyLevel() {
        return ConsistencyLevel.valueOf(writeConsistencyLevel);
    }

    public int getTaskDefRefreshTimeSecs() {
        return taskDefCacheRefreshTimeSecs;
    }

    public int getEventHandlerRefreshTimeSecs() {
        return eventHandlerRefreshTimeSecs;
    }

    public int getEventExecutionPersistenceTTL() {
        return eventExecutionPersistenceTTLSecs;
    }
}
