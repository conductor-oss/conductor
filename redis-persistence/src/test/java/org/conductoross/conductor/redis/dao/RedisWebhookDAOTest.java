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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisWebhookDAOTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisWebhookDAO dao;
    private MetadataDAO metadataDAO;
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
        metadataDAO = Mockito.mock(MetadataDAO.class);
        dao = new RedisWebhookDAO(proxy, om, cp, rp, metadataDAO);
    }

    @Test
    void crud_webhookConfig_roundtrip() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        config.setName("my-hook");

        dao.createWebhook("hook-1", config);

        WebhookConfig loaded = dao.getWebhook("hook-1");
        assertNotNull(loaded);
        assertEquals("hook-1", loaded.getId());
        assertEquals(1, dao.getAllWebhooks().size());

        dao.removeWebhook("hook-1");
        assertNull(dao.getWebhook("hook-1"));
        assertTrue(dao.getAllWebhooks().isEmpty());
    }

    @Test
    void createWebhook_upsertsExistingId() {
        WebhookConfig v1 = new WebhookConfig();
        v1.setId("hook-1");
        v1.setName("original");
        dao.createWebhook("hook-1", v1);

        WebhookConfig v2 = new WebhookConfig();
        v2.setId("hook-1");
        v2.setName("updated");
        dao.createWebhook("hook-1", v2);

        assertEquals("updated", dao.getWebhook("hook-1").getName());
    }

    @Test
    void removeWebhook_unknownId_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> dao.removeWebhook("missing"));
    }

    @Test
    void crud_incomingWebhookEvent_roundtrip() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-1")
                        .body("{\"event\":\"push\"}")
                        .timeStamp(123L)
                        .build();
        dao.createIncomingWebhookEvent("ev-1", event);
        assertEquals("hook-1", dao.getWebhookEvent("ev-1").getWebhookId());
        dao.removeWebhookEvent("ev-1");
        assertNull(dao.getWebhookEvent("ev-1"));
    }

    @Test
    void getMatchers_emptyOrNullTargets_returnsEmpty() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);

        dao.createMatchers(config, Map.of());
        assertTrue(dao.getMatchers("hook-1").isEmpty());

        dao.createMatchers(config, null);
        assertTrue(dao.getMatchers("hook-1").isEmpty());
    }

    @Test
    void getMatchers_recomputesFromMetadataDAO_onEachCall_noStaleCache() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);
        dao.createMatchers(config, Map.of("wf-a", 1));

        WorkflowTask t1 = new WorkflowTask();
        t1.setType("WAIT_FOR_WEBHOOK");
        t1.setTaskReferenceName("wait_ref");
        t1.setInputParameters(Map.of("matches", Map.of("event", "push")));
        WorkflowDef def1 = new WorkflowDef();
        def1.setName("wf-a");
        def1.setVersion(1);
        def1.setTasks(List.of(t1));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def1));

        assertEquals(Map.of("event", "push"), dao.getMatchers("hook-1").get("wf-a;1;wait_ref"));

        WorkflowTask t2 = new WorkflowTask();
        t2.setType("WAIT_FOR_WEBHOOK");
        t2.setTaskReferenceName("wait_ref");
        t2.setInputParameters(Map.of("matches", Map.of("event", "merge")));
        WorkflowDef def2 = new WorkflowDef();
        def2.setName("wf-a");
        def2.setVersion(1);
        def2.setTasks(List.of(t2));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def2));

        assertEquals(Map.of("event", "merge"), dao.getMatchers("hook-1").get("wf-a;1;wait_ref"));
    }

    @Test
    void removeMatchers_drops() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);
        dao.createMatchers(config, Map.of("wf-a", 1));

        dao.removeMatchers("hook-1");
        assertTrue(dao.getMatchers("hook-1").isEmpty());
    }
}
