/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.redis.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Condition that matches when a Redis connection is needed for ANY purpose — either as the primary
 * database (conductor.db.type) or as the queue backend (conductor.queue.type).
 *
 * <p>Use this for Redis infrastructure beans (connection pools, proxies, monitors) that must be
 * available whenever Redis is in use, regardless of whether it serves as DB or queue.
 *
 * <p>Contrast with {@link AnyRedisCondition} which only checks conductor.db.type and is used for
 * Redis persistence beans that should only load when Redis IS the primary database.
 */
public class AnyRedisConnectionCondition extends AnyNestedCondition {

    public AnyRedisConnectionCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    // --- conductor.db.type checks ---

    @ConditionalOnProperty(name = "conductor.db.type", havingValue = "memory")
    static class DbInMemory {}

    @ConditionalOnProperty(name = "conductor.db.type", havingValue = "redis_cluster")
    static class DbRedisCluster {}

    @ConditionalOnProperty(name = "conductor.db.type", havingValue = "redis_sentinel")
    static class DbRedisSentinel {}

    @ConditionalOnProperty(name = "conductor.db.type", havingValue = "redis_standalone")
    static class DbRedisStandalone {}

    // --- conductor.queue.type checks ---

    @ConditionalOnProperty(name = "conductor.queue.type", havingValue = "redis_cluster")
    static class QueueRedisCluster {}

    @ConditionalOnProperty(name = "conductor.queue.type", havingValue = "redis_sentinel")
    static class QueueRedisSentinel {}

    @ConditionalOnProperty(name = "conductor.queue.type", havingValue = "redis_standalone")
    static class QueueRedisStandalone {}
}
