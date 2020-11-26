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
package com.netflix.conductor.zookeeper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ZookeeperProperties {

    @Value("${workflow.zookeeper.lock.connection:localhost:2181}")
    private String connection;

    @Value("${workflow.zookeeper.lock.sessionTimeoutMs:#{T(org.apache.curator.framework.CuratorFrameworkFactory).builder().getSessionTimeoutMs()}}")
    private int sessionTimeoutMs;

    @Value("${workflow.zookeeper.lock.connectionTimeoutMs#{T(org.apache.curator.framework.CuratorFrameworkFactory).builder().getConnectionTimeoutMs()}}")
    private int connectionTimeoutMs;

    @Value("${workflow.decider.locking.namespace:}")
    private String lockingNamespace;

    public String getZkConnection() {
        return connection;
    }

    public int getZkSessiontimeoutMs() {
        return sessionTimeoutMs;
    }

    public int getZkConnectiontimeoutMs() {
        return connectionTimeoutMs;
    }

    public String getLockingNamespace() {
        return lockingNamespace;
    }
}
