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

import java.time.Duration;

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.zookeeper-lock")
public class ZookeeperProperties {

    /** The connection string to be used to connect to the Zookeeper cluster */
    private String connectionString = "localhost:2181";

    /** The session timeout for the curator */
    private Duration sessionTimeout =
            Duration.ofMillis(CuratorFrameworkFactory.builder().getSessionTimeoutMs());

    /** The connection timeout for the curator */
    private Duration connectionTimeout =
            Duration.ofMillis(CuratorFrameworkFactory.builder().getConnectionTimeoutMs());

    /** The namespace to use within the zookeeper cluster */
    private String namespace = "";

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public Duration getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(Duration sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
