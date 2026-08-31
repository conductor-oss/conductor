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
package org.conductoross.conductor.redis.dao;

import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.conductoross.conductor.dao.schema.SchemaDAOTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/** Runs the {@link SchemaDAO} contract against a real Redis container. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisSchemaDAOTest extends SchemaDAOTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private JedisPool jedisPool;
    private JedisProxy jedisProxy;
    private ObjectMapper objectMapper;
    private ConductorProperties conductorProperties;
    private RedisProperties redisProperties;

    private RedisSchemaDAO schemaDAO;

    private final java.util.List<JedisPool> reopenedPools = new java.util.ArrayList<>();

    @BeforeAll
    void setUp() {
        redis.start();

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);

        jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
        jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        objectMapper = new ObjectMapperProvider().getObjectMapper();
        conductorProperties = new ConductorProperties();
        redisProperties = new RedisProperties(conductorProperties);

        schemaDAO =
                new RedisSchemaDAO(jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @AfterAll
    void tearDown() {
        reopenedPools.forEach(JedisPool::close);
        if (jedisPool != null) {
            jedisPool.close();
        }
        redis.stop();
    }

    @Override
    protected boolean rejectsMalformedRows() {
        return false;
    }

    @Override
    protected SchemaDAO getSchemaDAO() {
        return schemaDAO;
    }

    /**
     * A pool of this test's own against the same Redis, so the re-read crosses a new connection
     * rather than reusing the one the DAO under test holds.
     */
    @Override
    protected SchemaDAO reopenStore() {
        JedisPool reopened =
                new JedisPool(new JedisPoolConfig(), redis.getHost(), redis.getFirstMappedPort());
        reopenedPools.add(reopened);
        return new RedisSchemaDAO(
                new JedisProxy(new JedisStandalone(reopened)),
                objectMapper,
                conductorProperties,
                redisProperties);
    }
}
