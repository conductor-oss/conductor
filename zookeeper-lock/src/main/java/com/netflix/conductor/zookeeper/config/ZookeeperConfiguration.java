package com.netflix.conductor.zookeeper.config;

import com.netflix.conductor.core.config.Configuration;

public interface ZookeeperConfiguration extends Configuration {
    String getZkConnection();
}
