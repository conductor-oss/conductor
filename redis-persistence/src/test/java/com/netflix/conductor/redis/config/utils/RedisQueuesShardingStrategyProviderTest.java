/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.redis.config.utils;

import java.util.Collections;

import org.junit.Test;

import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dynoqueue.RedisQueuesShardingStrategyProvider;
import com.netflix.dyno.queues.Message;
import com.netflix.dyno.queues.ShardSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RedisQueuesShardingStrategyProviderTest {

    @Test
    public void testStrategy() {
        ShardSupplier shardSupplier = mock(ShardSupplier.class);
        doReturn("current").when(shardSupplier).getCurrentShard();
        RedisQueuesShardingStrategyProvider.LocalOnlyStrategy strat =
                new RedisQueuesShardingStrategyProvider.LocalOnlyStrategy(shardSupplier);

        assertEquals("current", strat.getNextShard(Collections.emptyList(), new Message("a", "b")));
    }

    @Test
    public void testProvider() {
        ShardSupplier shardSupplier = mock(ShardSupplier.class);
        RedisProperties properties = mock(RedisProperties.class);
        when(properties.getQueueShardingStrategy()).thenReturn("localOnly");
        RedisQueuesShardingStrategyProvider stratProvider =
                new RedisQueuesShardingStrategyProvider(shardSupplier, properties);
        assertTrue(
                stratProvider.get()
                        instanceof RedisQueuesShardingStrategyProvider.LocalOnlyStrategy);
    }
}
