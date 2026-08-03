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

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This queue is a single zset: popping a message does not remove it, it bumps the score to
 * now+unackTimeout in place (pop.lua). {@link QueueDAO#peekPostponedIds} exists so the
 * concurrency-slot release can wake a genuinely postponed message without ever selecting a
 * popped-but-unacked one — resetting an in-flight message's score would redeliver it while a worker
 * is still executing it.
 */
public class RedisQueueDAOPeekPostponedTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedisQueueDAO queueDAO;

    @BeforeClass
    public static void startRedis() {
        redis.start();
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);
        JedisPool jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
        ConductorProperties conductorProperties = new ConductorProperties();
        RedisProperties redisProperties = new RedisProperties(conductorProperties);
        queueDAO =
                new RedisQueueDAO(
                        new JedisStandalone(jedisPool), redisProperties, conductorProperties);
    }

    @AfterClass
    public static void stopRedis() {
        redis.stop();
    }

    @Test
    public void peekPostponedIdsSkipsInFlightMessagesAndFindsThePostponedOne() {
        String queueName = "peek_postponed_test";

        // An in-flight message: pushed due-now, then popped - the pop bumps its score to
        // now+unackTimeout (~30s) but leaves it in the zset.
        queueDAO.push(queueName, "inflight-msg", 0);
        List<String> popped = queueDAO.pop(queueName, 1, 500);
        assertEquals(List.of("inflight-msg"), popped);

        // A genuinely postponed sibling, as taskExecutionPostponeDuration produces (60s).
        queueDAO.push(queueName, "postponed-msg", 60);

        // A floor-less peek returns the in-flight message first (lower score) - selecting it for
        // resetOffsetTime is exactly the duplicate-delivery hazard.
        List<String> unfloored = queueDAO.peekFirstIds(queueName, 2);
        assertEquals("inflight-msg", unfloored.get(0));

        // The floored peek must see only the postponed message.
        List<String> floored = queueDAO.peekPostponedIds(queueName, 35_000, 2);
        assertEquals(List.of("postponed-msg"), floored);
        assertFalse(floored.contains("inflight-msg"));
    }

    @Test
    public void peekPostponedIdsReturnsEmptyWhenOnlyInFlightMessagesExist() {
        String queueName = "peek_postponed_inflight_only";

        queueDAO.push(queueName, "inflight-only", 0);
        assertEquals(List.of("inflight-only"), queueDAO.pop(queueName, 1, 500));

        assertTrue(queueDAO.peekPostponedIds(queueName, 35_000, 1).isEmpty());
        // ...while the floor-less peek would have offered it up for a reset.
        assertEquals(List.of("inflight-only"), queueDAO.peekFirstIds(queueName, 1));
    }
}
