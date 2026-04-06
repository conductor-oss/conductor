/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.redis.dao;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.PollDataDAOTest;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.Assert.*;

public class RedisPollDataDAOTest extends PollDataDAOTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedisPollDataDAO redisPollDataDAO;
    private static JedisPool jedisPool;

    @BeforeClass
    public static void startRedis() {
        redis.start();

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);

        jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        ConductorProperties conductorProperties = new ConductorProperties();
        RedisProperties redisProperties = new RedisProperties(conductorProperties);

        redisPollDataDAO =
                new RedisPollDataDAO(
                        jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @AfterClass
    public static void stopRedis() {
        redis.stop();
    }

    @Before
    public void cleanUp() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    @Override
    protected PollDataDAO getPollDataDAO() {
        return redisPollDataDAO;
    }

    @Test
    public void testPollDataMultipleWorkers() {
        redisPollDataDAO.updateLastPollData("taskDef1", null, "worker1");
        redisPollDataDAO.updateLastPollData("taskDef1", null, "worker2");
        redisPollDataDAO.updateLastPollData("taskDef1", "domain1", "worker1");

        List<PollData> pollDataResult = redisPollDataDAO.getPollData("taskDef1");
        assertNotNull(pollDataResult);
        // 2 entries: one for DEFAULT (last writer wins for same domain), one for domain1
        assertEquals(2, pollDataResult.size());
    }

    @Test
    public void testPollDataMultipleTasks() {
        redisPollDataDAO.updateLastPollData("taskA", null, "worker1");
        redisPollDataDAO.updateLastPollData("taskB", null, "worker1");
        redisPollDataDAO.updateLastPollData("taskC", null, "worker1");

        assertEquals(1, redisPollDataDAO.getPollData("taskA").size());
        assertEquals(1, redisPollDataDAO.getPollData("taskB").size());
        assertEquals(1, redisPollDataDAO.getPollData("taskC").size());
    }

    @Test
    public void testPollDataUpdate() {
        redisPollDataDAO.updateLastPollData("taskDef2", null, "worker1");
        PollData first = redisPollDataDAO.getPollData("taskDef2", null);
        assertNotNull(first);
        long firstPollTime = first.getLastPollTime();

        // Small delay to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        redisPollDataDAO.updateLastPollData("taskDef2", null, "worker2");
        PollData second = redisPollDataDAO.getPollData("taskDef2", null);
        assertNotNull(second);
        assertTrue(second.getLastPollTime() >= firstPollTime);
        assertEquals("worker2", second.getWorkerId());
    }

    @Test
    public void testPollDataWithDomains() {
        redisPollDataDAO.updateLastPollData("taskDef3", "domain1", "worker1");
        redisPollDataDAO.updateLastPollData("taskDef3", "domain2", "worker2");
        redisPollDataDAO.updateLastPollData("taskDef3", null, "worker3");

        PollData d1 = redisPollDataDAO.getPollData("taskDef3", "domain1");
        assertNotNull(d1);
        assertEquals("domain1", d1.getDomain());
        assertEquals("worker1", d1.getWorkerId());

        PollData d2 = redisPollDataDAO.getPollData("taskDef3", "domain2");
        assertNotNull(d2);
        assertEquals("domain2", d2.getDomain());
        assertEquals("worker2", d2.getWorkerId());

        PollData defaultDomain = redisPollDataDAO.getPollData("taskDef3", null);
        assertNotNull(defaultDomain);
        assertEquals("worker3", defaultDomain.getWorkerId());

        List<PollData> all = redisPollDataDAO.getPollData("taskDef3");
        assertEquals(3, all.size());
    }

    @Test
    public void testPollDataNonExistent() {
        PollData result = redisPollDataDAO.getPollData("nonexistent_task", null);
        assertNull(result);

        List<PollData> results = redisPollDataDAO.getPollData("nonexistent_task");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
