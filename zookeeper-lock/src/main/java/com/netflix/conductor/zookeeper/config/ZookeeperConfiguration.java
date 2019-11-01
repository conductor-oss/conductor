/*
 * Copyright (c) 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.zookeeper.config;

import com.netflix.conductor.core.config.Configuration;
import org.apache.curator.framework.CuratorFrameworkFactory;

public interface ZookeeperConfiguration extends Configuration {
    String ZK_CONNECTION = "workflow.zookeeper.lock.connection";
    String ZK_SESSIONTIMEOUT_MS = "workflow.zookeeper.lock.sessionTimeoutMs";
    String ZK_CONNECTIONTIMEOUT_MS = "workflow.zookeeper.lock.connectionTimeoutMs";

    default String getZkConnection() {
        return getProperty(ZK_CONNECTION, "localhost:2181");
    }

    default int getZkSessiontimeoutMs() {
        return getIntProperty(ZK_SESSIONTIMEOUT_MS, CuratorFrameworkFactory.builder().getSessionTimeoutMs());
    }

    default int getZkConnectiontimeoutMs() {
        return getIntProperty(ZK_CONNECTIONTIMEOUT_MS, CuratorFrameworkFactory.builder().getConnectionTimeoutMs());
    }
}
