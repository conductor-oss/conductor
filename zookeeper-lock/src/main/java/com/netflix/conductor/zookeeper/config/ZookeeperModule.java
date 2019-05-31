package com.netflix.conductor.zookeeper.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.service.ExecutionLockService;
import com.netflix.conductor.zookeeper.ZookeeperLock;

public class ZookeeperModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ZookeeperConfiguration.class).to(SystemPropertiesZookeeperConfiguration.class);
    }

    @Provides
    protected Lock provideLock(ZookeeperConfiguration config) {
        return new ZookeeperLock(config, ExecutionLockService.LOCK_NAMESPACE);
    }
}
