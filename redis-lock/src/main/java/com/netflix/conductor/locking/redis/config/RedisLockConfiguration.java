package com.netflix.conductor.locking.redis.config;

import com.netflix.conductor.core.config.Configuration;

public interface RedisLockConfiguration extends Configuration {

    String REDIS_SERVER_TYPE_PROP_NAME = "redis.locking.server.type";
    String REDIS_SERVER_TYPE_DEFAULT_VALUE = "single";
    String REDIS_SERVER_STRING_PROP_NAME = "redis.locking.server.address";
    String REDIS_SERVER_STRING_DEFAULT_VALUE = "redis://127.0.0.1:6379";

    default REDIS_SERVER_TYPE getRedisServerType() {
        return REDIS_SERVER_TYPE.valueOf(getRedisServerStringValue());
    }

    default String getRedisServerStringValue() {
        return getProperty(REDIS_SERVER_TYPE_PROP_NAME, REDIS_SERVER_TYPE_DEFAULT_VALUE).toUpperCase();
    }

    default String getRedisServerAddress() {
        return getProperty(REDIS_SERVER_STRING_PROP_NAME, REDIS_SERVER_STRING_DEFAULT_VALUE);
    }

    enum REDIS_SERVER_TYPE {
        SINGLE, CLUSTER, SENTINEL
    }
}
