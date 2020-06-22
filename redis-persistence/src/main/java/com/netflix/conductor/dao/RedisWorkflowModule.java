/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dao;

import com.google.inject.AbstractModule;
import com.netflix.conductor.dao.dynomite.RedisEventHandlerDAO;
import com.netflix.conductor.dao.dynomite.RedisExecutionDAO;
import com.netflix.conductor.dao.dynomite.RedisMetadataDAO;
import com.netflix.conductor.dao.dynomite.RedisPollDataDAO;
import com.netflix.conductor.dao.dynomite.RedisRateLimitingDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.dyno.DynoProxy;
import com.netflix.conductor.dyno.RedisQueuesProvider;
import com.netflix.conductor.dyno.RedisQueuesShardingStrategyProvider;
import com.netflix.dyno.queues.redis.RedisQueues;
import com.netflix.dyno.queues.redis.sharding.ShardingStrategy;

/**
 * @author Viren
 */
public class RedisWorkflowModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MetadataDAO.class).to(RedisMetadataDAO.class);
        bind(ExecutionDAO.class).to(RedisExecutionDAO.class);
        bind(RateLimitingDAO.class).to(RedisRateLimitingDAO.class);
        bind(EventHandlerDAO.class).to(RedisEventHandlerDAO.class);
        bind(PollDataDAO.class).to(RedisPollDataDAO.class);
        bind(QueueDAO.class).to(DynoQueueDAO.class);

        bind(ShardingStrategy.class).toProvider(RedisQueuesShardingStrategyProvider.class).asEagerSingleton();
        bind(RedisQueues.class).toProvider(RedisQueuesProvider.class).asEagerSingleton();
        bind(DynoProxy.class).asEagerSingleton();
    }
}
