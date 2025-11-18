/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.test.base

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.ConductorTestApp
import com.netflix.conductor.core.config.SchedulerConfiguration
import com.netflix.conductor.core.events.DefaultEventProcessor
import com.netflix.conductor.core.events.DefaultEventQueueManager
import com.netflix.conductor.core.events.queue.ConductorEventQueueProvider
import com.netflix.conductor.core.execution.mapper.DoWhileTaskMapper
import com.netflix.conductor.core.execution.mapper.EventTaskMapper
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper
import com.netflix.conductor.core.execution.mapper.HumanTaskMapper
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper
import com.netflix.conductor.core.execution.mapper.SwitchTaskMapper
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper
import com.netflix.conductor.core.execution.tasks.DoWhile
import com.netflix.conductor.core.execution.tasks.Event
import com.netflix.conductor.core.execution.tasks.ExclusiveJoin
import com.netflix.conductor.core.execution.tasks.Human
import com.netflix.conductor.core.execution.tasks.Inline
import com.netflix.conductor.core.execution.tasks.Join
import com.netflix.conductor.core.execution.tasks.SetVariable
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.core.execution.tasks.Wait
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.redis.dao.DynoQueueDAO
import com.netflix.conductor.redis.jedis.JedisMock
import com.netflix.conductor.tasks.json.JsonJqTransform
import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.queues.ShardSupplier
import com.netflix.dyno.queues.redis.RedisQueues

import redis.clients.jedis.commands.JedisCommands
import spock.mock.DetachedMockFactory

@TestPropertySource(properties = [
        "conductor.system-task-workers.enabled=false",
        "conductor.workflow-repair-service.enabled=true",
        "conductor.workflow-reconciler.enabled=false",
        "conductor.integ-test.queue-spy.enabled=true",
        "conductor.queue.type=xxx"
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
