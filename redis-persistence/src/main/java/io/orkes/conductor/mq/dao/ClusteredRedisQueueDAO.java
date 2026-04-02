/*
 * Copyright 2026 Conductor Authors.
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
package io.orkes.conductor.mq.dao;

import java.util.concurrent.ExecutorService;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisCommands;

import io.orkes.conductor.mq.ConductorQueue;
import io.orkes.conductor.mq.redis.cluster.ConductorRedisClusterQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// FIXME: be aware that queues have their own prefix which is different from workflowNamespacePrefix
public class ClusteredRedisQueueDAO extends BaseRedisQueueDAO implements QueueDAO {

    private final JedisCommands jedisCommands;

    public ClusteredRedisQueueDAO(
            JedisCommands jedisCommands,
            RedisProperties redisProperties,
            ConductorProperties conductorProperties) {

        super(jedisCommands, redisProperties, conductorProperties);
        this.jedisCommands = jedisCommands;
        log.info("Queues initialized using {}", ClusteredRedisQueueDAO.class.getName());
    }

    @Override
    protected ConductorQueue getConductorQueue(String queueKey, ExecutorService executorService) {
        return new ConductorRedisClusterQueue(queueKey, jedisCommands, executorService);
    }
}
