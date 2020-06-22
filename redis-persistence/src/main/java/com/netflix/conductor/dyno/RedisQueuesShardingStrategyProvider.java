/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dyno;

import com.netflix.dyno.queues.Message;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.sharding.RoundRobinStrategy;
import com.netflix.dyno.queues.redis.sharding.ShardingStrategy;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisQueuesShardingStrategyProvider implements Provider<ShardingStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueuesShardingStrategyProvider.class);
    public static final String STRATEGY_TYPE_PROPERTY = "workflow.dyno.queue.sharding.strategy";
    public static final String ROUND_ROBIN_STRATEGY = "roundRobin";
    public static final String LOCAL_ONLY_STRATEGY = "localOnly";

    private final ShardSupplier shardSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public RedisQueuesShardingStrategyProvider(
            ShardSupplier ss,
            DynomiteConfiguration config
    ) {
        this.shardSupplier = ss;
        this.configuration = config;
    }

    @Override
    public ShardingStrategy get() {
        String shardingStrat = configuration.getProperty(STRATEGY_TYPE_PROPERTY, ROUND_ROBIN_STRATEGY);
        if (shardingStrat.equals(LOCAL_ONLY_STRATEGY)) {
            logger.info("Using {} sharding strategy for queues", LocalOnlyStrategy.class.getSimpleName());
            return new LocalOnlyStrategy(shardSupplier);
        } else {
            logger.info("Using {} sharding strategy for queues", RoundRobinStrategy.class.getSimpleName());
            return new RoundRobinStrategy();
        }
    }

    static final class LocalOnlyStrategy implements ShardingStrategy {
        private static final Logger logger = LoggerFactory.getLogger(RedisQueuesShardingStrategyProvider.class);

        private final ShardSupplier shardSupplier;

        public LocalOnlyStrategy(ShardSupplier shardSupplier) {
            this.shardSupplier = shardSupplier;
        }

        @Override
        public String getNextShard(List<String> allShards, Message message) {
            logger.debug("Always using {} shard out of {}", shardSupplier.getCurrentShard(), allShards);
            return shardSupplier.getCurrentShard();
        }
    }
}
