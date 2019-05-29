package com.netflix.conductor.zookeeper.config;

import com.netflix.conductor.core.config.SystemPropertiesConfiguration;

public class SystemPropertiesZookeeperConfiguration extends SystemPropertiesConfiguration implements ZookeeperConfiguration {

    public String ZK_CONNECTION = "zk.connection";

    public String getZkConnection() {
        return getProperty(ZK_CONNECTION, "localhost:2181");
    }
}
