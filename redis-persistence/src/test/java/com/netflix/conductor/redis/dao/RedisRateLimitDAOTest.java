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

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisRateLimitDAOTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisRateLimitingDAO rateLimitingDao;
    private JedisPool jedisPool;

    @BeforeAll
    void setUp() {
        redis.start();

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);

        jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        ConductorProperties conductorProperties = new ConductorProperties();
        RedisProperties redisProperties = new RedisProperties(conductorProperties);

        rateLimitingDao =
                new RedisRateLimitingDAO(
                        jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @BeforeEach
    void cleanUp() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    @AfterAll
    void tearDown() {
        redis.stop();
    }

    @Test
    void testExceedsRateLimitWhenNoRateLimitSet() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition" + UUID.randomUUID());
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    void testExceedsRateLimitWithinLimit() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition" + UUID.randomUUID());
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setRateLimitPerFrequency(20);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    void testExceedsRateLimitOutOfLimit() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition" + UUID.randomUUID());
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setRateLimitPerFrequency(1);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    void testExceedsRateLimitWithinLimitMultipleCalls() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition" + UUID.randomUUID());
        taskDef.setRateLimitFrequencyInSeconds(3);
        taskDef.setRateLimitPerFrequency(3);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());

        // First 3 calls should be within limit
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));

        // 4th call should exceed limit
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));

        // Wait for window to expire
        Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(4));

        // Should be within limit again
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    void testExceedsRateLimitWithNullTaskDef() {
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName("task_with_null_def");
        // When taskDef is null and task has no rate limit set, should not be rate limited
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, null));
    }

    @Test
    void testExceedsRateLimitWithZeroValues() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition" + UUID.randomUUID());
        taskDef.setRateLimitFrequencyInSeconds(0);
        taskDef.setRateLimitPerFrequency(0);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        // Zero rate limit values mean no rate limiting
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    void testRateLimitIsolationBetweenTaskTypes() {
        TaskDef taskDefA = new TaskDef("TaskTypeA_" + UUID.randomUUID());
        taskDefA.setRateLimitFrequencyInSeconds(60);
        taskDefA.setRateLimitPerFrequency(1);

        TaskDef taskDefB = new TaskDef("TaskTypeB_" + UUID.randomUUID());
        taskDefB.setRateLimitFrequencyInSeconds(60);
        taskDefB.setRateLimitPerFrequency(1);

        TaskModel taskA = new TaskModel();
        taskA.setTaskId(UUID.randomUUID().toString());
        taskA.setTaskDefName(taskDefA.getName());

        TaskModel taskB = new TaskModel();
        taskB.setTaskId(UUID.randomUUID().toString());
        taskB.setTaskDefName(taskDefB.getName());

        // Rate limiting A should not affect B
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(taskA, taskDefA));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(taskA, taskDefA));

        // B should still be within limit
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(taskB, taskDefB));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(taskB, taskDefB));
    }

    @Test
    void testExceedsRateLimitPostponeDuration() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition" + UUID.randomUUID());
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setRateLimitPerFrequency(1);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }

    @Test
    void testExceedsRateLimitPostponeDurationWithDelay() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition" + UUID.randomUUID());
        taskDef.setRateLimitFrequencyInSeconds(13);
        taskDef.setRateLimitPerFrequency(1);
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }
}
