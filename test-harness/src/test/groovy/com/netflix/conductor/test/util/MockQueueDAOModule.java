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
package com.netflix.conductor.test.util;

import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.jedis.JedisMock;
import com.netflix.conductor.tests.utils.TestModule;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;
import redis.clients.jedis.commands.JedisCommands;
import spock.mock.DetachedMockFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates a Spock "Spy"'ed QueueDAO Guice injection, that can be used in integration testing.
 */
public class MockQueueDAOModule extends TestModule {
    @Override
    public void configureQueueDAO() {
        DetachedMockFactory detachedMockFactory = new DetachedMockFactory();

        JedisCommands jedisMock = new JedisMock();
        ShardSupplier shardSupplier = new ShardSupplier() {
            @Override
            public Set<String> getQueueShards() {
                return new HashSet<>(Collections.singletonList("a"));
            }

            @Override
            public String getCurrentShard() {
                return "a";
            }

            @Override
            public String getShardForHost(Host host) {
                return "a";
            }
        };
        RedisQueues redisQueues = new RedisQueues(jedisMock, jedisMock, "mockedQueues", shardSupplier, 60000, 120000);
        DynoQueueDAO dynoQueueDAO = new DynoQueueDAO(redisQueues);

        bind(QueueDAO.class).toInstance(detachedMockFactory.Spy(dynoQueueDAO));
    }
}
