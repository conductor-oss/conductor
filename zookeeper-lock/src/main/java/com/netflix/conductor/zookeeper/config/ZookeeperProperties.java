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

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.zookeeper-lock")
public class ZookeeperProperties {

    /**
     * The connection string to be used to connect to the Zookeeper cluster
     */
    private String connectionString = "localhost:2181";

    /**
     * The session timeout for the curator
     */
    private int sessionTimeoutMs = CuratorFrameworkFactory.builder().getSessionTimeoutMs();

    /**
     * The connection timeout for the curator
     */
    private int connectionTimeoutMs = CuratorFrameworkFactory.builder().getConnectionTimeoutMs();

    /**
     * The namespace to use within the zookeeper cluster
     */
    private String namespace = "";

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(int sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
