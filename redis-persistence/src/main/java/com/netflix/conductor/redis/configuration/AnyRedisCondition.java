package com.netflix.conductor.redis.configuration;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class AnyRedisCondition extends AnyNestedCondition {

    public AnyRedisCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "db", havingValue = "dynomite")
    static class DynomiteClusterCondition {

    }

    @ConditionalOnProperty(name = "db", havingValue = "memory")
    static class InMemoryRedisCondition {

    }

    @ConditionalOnProperty(name = "db", havingValue = "redis_cluster")
    static class RedisClusterConfiguration {

    }

    @ConditionalOnProperty(name = "db", havingValue = "redis_sentinel")
    static class RedisSentinelConfiguration {

    }
}
