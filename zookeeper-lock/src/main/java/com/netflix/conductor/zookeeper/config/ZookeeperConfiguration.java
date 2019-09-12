package com.netflix.conductor.zookeeper.config;

import com.netflix.conductor.core.config.Configuration;
import org.apache.curator.framework.CuratorFrameworkFactory;

public interface ZookeeperConfiguration extends Configuration {
    String ZK_CONNECTION = "zk.connection";
    String ZK_SESSIONTIMEOUT_MS = "zk.sessionTimeoutMs";
    String ZK_CONNECTIONTIMEOUT_MS = "zk.connectionTimeoutMs";

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
