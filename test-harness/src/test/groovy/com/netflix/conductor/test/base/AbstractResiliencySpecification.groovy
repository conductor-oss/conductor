/*
 * Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *   the License. You may obtain a copy of the License at
 *   <p>
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   <p>
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *   an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *   specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.test.base

import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.redis.dao.DynoQueueDAO
import com.netflix.conductor.redis.jedis.JedisMock
import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.queues.ShardSupplier
import com.netflix.dyno.queues.redis.RedisQueues
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import redis.clients.jedis.commands.JedisCommands
import spock.mock.DetachedMockFactory

@TestPropertySource(properties = [
        "conductor.system-task-workers.enabled=false",
        "conductor.workflow-repair-service.enabled=true",
        "conductor.workflow-reconciler.enabled=false",
        "conductor.integ-test.queue-spy.enabled=true"
])
abstract class AbstractResiliencySpecification extends AbstractSpecification {

    @Configuration
    static class TestQueueConfiguration {

        @Primary
        @Bean
        @ConditionalOnProperty(name = "conductor.integ-test.queue-spy.enabled", havingValue = "true")
        QueueDAO SpyQueueDAO() {
            DetachedMockFactory detachedMockFactory = new DetachedMockFactory()
            JedisCommands jedisMock = new JedisMock()
            ShardSupplier shardSupplier = new ShardSupplier() {
                @Override
                Set<String> getQueueShards() {
                    return new HashSet<>(Collections.singletonList("a"))
                }

                @Override
                String getCurrentShard() {
                    return "a"
                }

                @Override
                String getShardForHost(Host host) {
                    return "a"
                }
            }
            RedisQueues redisQueues = new RedisQueues(jedisMock, jedisMock, "mockedQueues", shardSupplier, 60000, 120000)
            DynoQueueDAO dynoQueueDAO = new DynoQueueDAO(redisQueues)

            return detachedMockFactory.Spy(dynoQueueDAO)
        }
    }

    @Autowired
    QueueDAO queueDAO
}
