/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.conductoross.conductor.scheduler.redis.service;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.redis.dao.RedisSchedulerDAO;
import org.conductoross.conductor.scheduler.service.AbstractSchedulerServiceIntegrationTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisSchedulerServiceIntegrationTest extends AbstractSchedulerServiceIntegrationTest {

    @ClassRule
    @SuppressWarnings("resource")
    public static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static JedisPool jedisPool;
    private static RedisSchedulerDAO redisDAO;

    @BeforeClass
    public static void setUpClass() {
        jedisPool = new JedisPool(redis.getHost(), redis.getMappedPort(6379));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        redisDAO = new RedisSchedulerDAO(jedisProxy, objectMapper);
    }

    @AfterClass
    public static void tearDownClass() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Override
    protected SchedulerDAO dao() {
        return redisDAO;
    }

    @Override
    protected DataSource dataSource() {
        throw new UnsupportedOperationException("Redis does not use a DataSource");
    }

    @Override
    protected void truncateStore() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }
}
