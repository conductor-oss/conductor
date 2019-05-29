package com.netflix.conductor.zookeeper.config;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.service.ExecutionLockService;
import com.netflix.conductor.zookeeper.ZkLock;
import com.netflix.conductor.zookeeper.config.SystemPropertiesZookeeperConfiguration;
import com.netflix.conductor.zookeeper.config.ZookeeperConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;

public class ZookeeperModule extends AbstractModule {

    private static class LockProvider implements Provider<Lock> {
        private ZookeeperConfiguration config;

        @Inject
        public LockProvider(ZookeeperConfiguration config) {
            this.config = config;
        }

        @Override
        public Lock get() {
            return new ZkLock(config, ExecutionLockService.LOCK_NAMESPACE);
        }
    }

    @Override
    protected void configure() {
        bind(ZookeeperConfiguration.class).to(SystemPropertiesZookeeperConfiguration.class);
        bind(new TypeLiteral<Provider<Lock>>(){})
                .annotatedWith(Names.named("executionLock"))
                .to(LockProvider.class);
    }
}
