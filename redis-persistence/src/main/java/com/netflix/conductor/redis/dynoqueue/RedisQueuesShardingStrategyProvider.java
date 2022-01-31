/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.redis.dynoqueue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.dyno.queues.Message;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.sharding.RoundRobinStrategy;
import com.netflix.dyno.queues.redis.sharding.ShardingStrategy;

public class RedisQueuesShardingStrategyProvider {

    public static final String LOCAL_ONLY_STRATEGY = "localOnly";
    public static final String ROUND_ROBIN_STRATEGY = "roundRobin";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisQueuesShardingStrategyProvider.class);
    private final ShardSupplier shardSupplier;
    private final RedisProperties properties;

    public RedisQueuesShardingStrategyProvider(
            ShardSupplier shardSupplier, RedisProperties properties) {
        this.shardSupplier = shardSupplier;
        this.properties = properties;
    }

    public ShardingStrategy get() {
        String shardingStrat = properties.getQueueShardingStrategy();
        if (shardingStrat.equals(LOCAL_ONLY_STRATEGY)) {
            LOGGER.info(
                    "Using {} sharding strategy for queues",
                    LocalOnlyStrategy.class.getSimpleName());
            return new LocalOnlyStrategy(shardSupplier);
        } else {
            LOGGER.info(
                    "Using {} sharding strategy for queues",
                    RoundRobinStrategy.class.getSimpleName());
            return new RoundRobinStrategy();
        }
    }

    public static final class LocalOnlyStrategy implements ShardingStrategy {

        private static final Logger LOGGER = LoggerFactory.getLogger(LocalOnlyStrategy.class);

        private final ShardSupplier shardSupplier;

        public LocalOnlyStrategy(ShardSupplier shardSupplier) {
            this.shardSupplier = shardSupplier;
        }

        @Override
        public String getNextShard(List<String> allShards, Message message) {
            LOGGER.debug(
                    "Always using {} shard out of {}", shardSupplier.getCurrentShard(), allShards);
            return shardSupplier.getCurrentShard();
        }
    }
}
