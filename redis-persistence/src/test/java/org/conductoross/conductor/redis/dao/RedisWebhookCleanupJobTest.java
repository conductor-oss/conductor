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

import java.time.Duration;
import java.time.Instant;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisWebhookCleanupJobTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisWebhookDAO dao;
    private RedisWebhookCleanupJob job;
    private JedisPool jedisPool;

    @BeforeAll
    void setUp() {
        redis.start();
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);
        jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
    }

    @AfterAll
    void tearDown() {
        redis.stop();
    }

    @BeforeEach
    void cleanUp() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
        JedisProxy proxy = new JedisProxy(new JedisStandalone(jedisPool));
        ObjectMapper om = new ObjectMapperProvider().getObjectMapper();
        ConductorProperties cp = new ConductorProperties();
        RedisProperties rp = new RedisProperties(cp);
        dao = new RedisWebhookDAO(proxy, om, cp, rp, null);
        job = new RedisWebhookCleanupJob(proxy, om, cp, rp, Duration.ofDays(1));
    }

    @Test
    void deletes_old_events_keeps_recent() {
        long now = Instant.now().toEpochMilli();
        long oldTs = now - Duration.ofDays(5).toMillis();
        long recentTs = now - Duration.ofMinutes(5).toMillis();
        dao.createIncomingWebhookEvent("ev-old", event("ev-old", oldTs));
        dao.createIncomingWebhookEvent("ev-recent", event("ev-recent", recentTs));

        job.run();

        // ev-old gone, ev-recent kept
        assertEquals(null, dao.getWebhookEvent("ev-old"));
        assertEquals("ev-recent", dao.getWebhookEvent("ev-recent").getId());
    }

    @Test
    void empty_hash_noop() {
        job.run();
        assertEquals(null, dao.getWebhookEvent("anything"));
    }

    @Test
    void all_recent_kept() {
        long recentTs = Instant.now().toEpochMilli() - Duration.ofMinutes(5).toMillis();
        dao.createIncomingWebhookEvent("ev-1", event("ev-1", recentTs));
        dao.createIncomingWebhookEvent("ev-2", event("ev-2", recentTs));

        job.run();

        assertEquals("ev-1", dao.getWebhookEvent("ev-1").getId());
        assertEquals("ev-2", dao.getWebhookEvent("ev-2").getId());
    }

    @Test
    void zero_timestamp_event_skipped() {
        // Some events may have timeStamp=0 (e.g. manual fixtures). Treat as "no expiry signal" —
        // leave them alone rather than risking deletion of legitimate data.
        dao.createIncomingWebhookEvent("ev-zero", event("ev-zero", 0L));

        job.run();

        assertEquals("ev-zero", dao.getWebhookEvent("ev-zero").getId());
    }

    private static IncomingWebhookEvent event(String id, long ts) {
        return IncomingWebhookEvent.builder()
                .id(id)
                .webhookId("hook-1")
                .body("{}")
                .timeStamp(ts)
                .build();
    }
}
