/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.redislock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.redis-lock")
public class RedisLockProperties {

    /** The redis server configuration to be used. */
    private REDIS_SERVER_TYPE serverType = REDIS_SERVER_TYPE.SINGLE;

    /** The address of the redis server following format -- host:port */
    private String serverAddress = "redis://127.0.0.1:6379";

    /** The password for redis authentication */
    private String serverPassword = null;

    /** The master server name used by Redis Sentinel servers and master change monitoring task */
    private String serverMasterName = "master";

    /** The namespace to use to prepend keys used for locking in redis */
    private String namespace = "";

    /** The number of natty threads to use */
    private Integer numNettyThreads;

    /** If using Cluster Mode, you can use this to set num of min idle connections for replica */
    private int clusterReplicaConnectionMinIdleSize = 24;

    /** If using Cluster Mode, you can use this to set num of min idle connections for replica */
    private int clusterReplicaConnectionPoolSize = 64;

    /** If using Cluster Mode, you can use this to set num of min idle connections for replica */
    private int clusterPrimaryConnectionMinIdleSize = 24;

    /** If using Cluster Mode, you can use this to set num of min idle connections for replica */
    private int clusterPrimaryConnectionPoolSize = 64;

    /**
     * Enable to otionally continue without a lock to not block executions until the locking service
     * becomes available
     */
    private boolean ignoreLockingExceptions = false;

    /** Interval in milliseconds to check the endpoint's DNS (Set -1 to disable). */
    private long dnsMonitoringInterval = 5000L;

    public REDIS_SERVER_TYPE getServerType() {
        return serverType;
    }

    public void setServerType(REDIS_SERVER_TYPE serverType) {
        this.serverType = serverType;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    public String getServerMasterName() {
        return serverMasterName;
    }

    public void setServerMasterName(String serverMasterName) {
        this.serverMasterName = serverMasterName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isIgnoreLockingExceptions() {
        return ignoreLockingExceptions;
    }

    public void setIgnoreLockingExceptions(boolean ignoreLockingExceptions) {
        this.ignoreLockingExceptions = ignoreLockingExceptions;
    }

    public Integer getNumNettyThreads() {
        return numNettyThreads;
    }

    public void setNumNettyThreads(Integer numNettyThreads) {
        this.numNettyThreads = numNettyThreads;
    }

    public Integer getClusterReplicaConnectionMinIdleSize() {
        return clusterReplicaConnectionMinIdleSize;
    }

    public void setClusterReplicaConnectionMinIdleSize(
            Integer clusterReplicaConnectionMinIdleSize) {
        this.clusterReplicaConnectionMinIdleSize = clusterReplicaConnectionMinIdleSize;
    }

    public Integer getClusterReplicaConnectionPoolSize() {
        return clusterReplicaConnectionPoolSize;
    }

    public void setClusterReplicaConnectionPoolSize(Integer clusterReplicaConnectionPoolSize) {
        this.clusterReplicaConnectionPoolSize = clusterReplicaConnectionPoolSize;
    }

    public Integer getClusterPrimaryConnectionMinIdleSize() {
        return clusterPrimaryConnectionMinIdleSize;
    }

    public void setClusterPrimaryConnectionMinIdleSize(
            Integer clusterPrimaryConnectionMinIdleSize) {
        this.clusterPrimaryConnectionMinIdleSize = clusterPrimaryConnectionMinIdleSize;
    }

    public Integer getClusterPrimaryConnectionPoolSize() {
        return clusterPrimaryConnectionPoolSize;
    }

    public void setClusterPrimaryConnectionPoolSize(Integer clusterPrimaryConnectionPoolSize) {
        this.clusterPrimaryConnectionPoolSize = clusterPrimaryConnectionPoolSize;
    }

    public long getDnsMonitoringInterval() {
        return dnsMonitoringInterval;
    }

    public void setDnsMonitoringInterval(long dnsMonitoringInterval) {
        this.dnsMonitoringInterval = dnsMonitoringInterval;
    }

    public enum REDIS_SERVER_TYPE {
        SINGLE,
        CLUSTER,
        SENTINEL
    }
}
