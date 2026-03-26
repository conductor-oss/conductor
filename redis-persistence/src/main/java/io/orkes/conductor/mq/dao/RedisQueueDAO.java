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
import io.orkes.conductor.mq.redis.single.ConductorRedisQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// FIXME: be aware that queues have their own prefix which is different from workflowNamespacePrefix
public class RedisQueueDAO extends BaseRedisQueueDAO implements QueueDAO {

    private final JedisCommands jedisPool;

    public RedisQueueDAO(
            JedisCommands jedisPool,
            RedisProperties redisProperties,
            ConductorProperties conductorProperties) {

        super(redisProperties, conductorProperties);
        this.jedisPool = jedisPool;
        log.info("Queues initialized using {}", RedisQueueDAO.class.getName());
    }

    @Override
    protected ConductorQueue getConductorQueue(String queueKey, ExecutorService executorService) {
        return new ConductorRedisQueue(queueKey, jedisPool, executorService);
    }

    @Override
    public void updateScore(String queueName) {
        String key =
                redisProperties.getQueueNamespacePrefix()
                        + "."
                        + conductorProperties.getStack()
                        + "."
                        + CDC_BUFFER_QUEUES_KEY;
        jedisPool.zadd(key, -(System.currentTimeMillis()), queueName);
    }
}
