/*
 * Copyright 2025 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.redislock.config;

import org.redisson.api.RedissonClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.redisson.api.redisnode.RedisNodes.*;

@Component
@ConditionalOnProperty(name = "management.health.redis.enabled", havingValue = "true")
public class RedisHealthIndicator implements HealthIndicator {
    private final RedissonClient redisClient;
    private final RedisLockProperties redisProperties;

    public RedisHealthIndicator(RedissonClient redisClient, RedisLockProperties redisProperties) {
        this.redisClient = redisClient;
        this.redisProperties = redisProperties;
    }

    @Override
    public Health health() {
        return isHealth() ? Health.up().build() : Health.down().build();
    }

    private boolean isHealth() {
        switch (redisProperties.getServerType()) {
            case SINGLE -> {
                return redisClient.getRedisNodes(SINGLE).pingAll(5, SECONDS);
            }

            case CLUSTER -> {
                return redisClient.getRedisNodes(CLUSTER).pingAll(5, SECONDS);
            }

            case SENTINEL -> {
                return redisClient.getRedisNodes(SENTINEL_MASTER_SLAVE).pingAll(5, SECONDS);
            }

            default -> {
                return false;
            }
        }
    }
}
