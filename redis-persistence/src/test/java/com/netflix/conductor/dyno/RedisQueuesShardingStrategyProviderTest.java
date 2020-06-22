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
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class RedisQueuesShardingStrategyProviderTest {

    @Test
    public void testStrategy() {
        ShardSupplier ss = Mockito.mock(ShardSupplier.class);
        Mockito.doReturn("current").when(ss).getCurrentShard();
        RedisQueuesShardingStrategyProvider.LocalOnlyStrategy strat =
                new RedisQueuesShardingStrategyProvider.LocalOnlyStrategy(ss);

        Assert.assertEquals("current",
                strat.getNextShard(Collections.emptyList(), new Message("a", "b")));
    }

    @Test
    public void testProvider() {
        ShardSupplier ss = Mockito.mock(ShardSupplier.class);
        DynomiteConfiguration dc = Mockito.mock(DynomiteConfiguration.class);
        Mockito.doReturn("localOnly").when(dc)
                .getProperty(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        RedisQueuesShardingStrategyProvider stratProvider
                = new RedisQueuesShardingStrategyProvider(ss, dc);
        Assert.assertTrue(stratProvider.get() instanceof RedisQueuesShardingStrategyProvider.LocalOnlyStrategy);
    }
}