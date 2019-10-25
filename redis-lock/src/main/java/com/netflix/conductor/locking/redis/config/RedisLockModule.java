package com.netflix.conductor.locking.redis.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.locking.redis.RedisLock;

public class RedisLockModule extends AbstractModule{
    @Override
    protected void configure() {
        bind(RedisLockConfiguration.class).to(SystemPropertiesRedisLockConfiguration.class);
    }

    @Provides
    protected Lock provideLock(RedisLockConfiguration configuration) {
        return new RedisLock(configuration);
    }
}
