package com.netflix.conductor.zookeeper.config;

import com.netflix.conductor.core.config.Configuration;

public interface ZookeeperConfiguration extends Configuration {
    String ZK_CONNECTION = "zk.connection";

    default String getZkConnection() {
        return getProperty(ZK_CONNECTION, "localhost:2181");
    }
}
