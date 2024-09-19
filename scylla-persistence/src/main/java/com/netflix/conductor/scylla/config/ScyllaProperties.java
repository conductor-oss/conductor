/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.scylla.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import com.datastax.driver.core.ConsistencyLevel;

@ConfigurationProperties("conductor.scylla")
public class ScyllaProperties {

    /** The address for the cassandra database host */
    private String hostAddress = "127.0.0.1";

    /** The port to be used to connect to the cassandra database instance */
    private int port = 9142;

    /** The name of the cassandra cluster */
    private String cluster = "";

    /** The keyspace to be used in the cassandra datastore */
    private String keyspace = "conductor";

    /** The username to be used in the cassandra */
    private String userName = "scylla";

    /** The password to be used in the cassandra */
    private String password = "scylla";

    /**
     * The number of tasks to be stored in a single partition which will be used for sharding
     * workflows in the datastore
     */
    private int shardSize = 100;

    /** The replication strategy with which to configure the keyspace */
    private String replicationStrategy = "SimpleStrategy";

    /** The key to be used while configuring the replication factor */
    private String replicationFactorKey = "replication_factor";

    /** The replication factor value with which the keyspace is configured */
    private int replicationFactorValue = 3;

    /** The consistency level to be used for read operations */
    private ConsistencyLevel readConsistencyLevel = ConsistencyLevel.LOCAL_QUORUM;

    /** The consistency level to be used for write operations */
    private ConsistencyLevel writeConsistencyLevel = ConsistencyLevel.LOCAL_QUORUM;

    /** The time in seconds after which the in-memory task definitions cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    /** The time in seconds after which the in-memory event handler cache will be refreshed */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration eventHandlerCacheRefreshInterval = Duration.ofSeconds(60);

    /** The time to live in seconds for which the event execution will be persisted */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration eventExecutionPersistenceTtl = Duration.ZERO;

    public String getHostAddress() {
        return hostAddress;
    }

    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    public int getShardSize() {
        return shardSize;
    }

    public void setShardSize(int shardSize) {
        this.shardSize = shardSize;
    }

    public String getReplicationStrategy() {
        return replicationStrategy;
    }

    public void setReplicationStrategy(String replicationStrategy) {
        this.replicationStrategy = replicationStrategy;
    }

    public String getReplicationFactorKey() {
        return replicationFactorKey;
    }

    public void setReplicationFactorKey(String replicationFactorKey) {
        this.replicationFactorKey = replicationFactorKey;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getReplicationFactorValue() {
        return replicationFactorValue;
    }

    public void setReplicationFactorValue(int replicationFactorValue) {
        this.replicationFactorValue = replicationFactorValue;
    }

    public ConsistencyLevel getReadConsistencyLevel() {
        return readConsistencyLevel;
    }

    public void setReadConsistencyLevel(ConsistencyLevel readConsistencyLevel) {
        this.readConsistencyLevel = readConsistencyLevel;
    }

    public ConsistencyLevel getWriteConsistencyLevel() {
        return writeConsistencyLevel;
    }

    public void setWriteConsistencyLevel(ConsistencyLevel writeConsistencyLevel) {
        this.writeConsistencyLevel = writeConsistencyLevel;
    }

    public Duration getTaskDefCacheRefreshInterval() {
        return taskDefCacheRefreshInterval;
    }

    public void setTaskDefCacheRefreshInterval(Duration taskDefCacheRefreshInterval) {
        this.taskDefCacheRefreshInterval = taskDefCacheRefreshInterval;
    }

    public Duration getEventHandlerCacheRefreshInterval() {
        return eventHandlerCacheRefreshInterval;
    }

    public void setEventHandlerCacheRefreshInterval(Duration eventHandlerCacheRefreshInterval) {
        this.eventHandlerCacheRefreshInterval = eventHandlerCacheRefreshInterval;
    }

    public Duration getEventExecutionPersistenceTtl() {
        return eventExecutionPersistenceTtl;
    }

    public void setEventExecutionPersistenceTtl(Duration eventExecutionPersistenceTtl) {
        this.eventExecutionPersistenceTtl = eventExecutionPersistenceTtl;
    }
}
