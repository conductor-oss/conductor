/*
 * Copyright 2024 Conductor Authors.
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
import java.util.Map;

import org.conductoross.conductor.common.webhook.WebhookConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.commands.JedisCommands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class RedisWebhookConfigDAOTest {

    private RedisWebhookConfigDAO dao;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void init() {
        ConductorProperties conductorProperties = mock(ConductorProperties.class);
        RedisProperties properties = mock(RedisProperties.class);
        JedisCommands jedisMock = new JedisMock();
        JedisProxy jedisProxy = new JedisProxy(jedisMock);

        dao = new RedisWebhookConfigDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    // -------------------------------------------------------------------------
    // Config CRUD
    // -------------------------------------------------------------------------

    @Test
    public void save_and_get_roundTrip() {
        WebhookConfig config = config("wh-1", "my-hook");
        dao.save("wh-1", config);

        WebhookConfig loaded = dao.get("wh-1");
        assertNotNull(loaded);
        assertEquals("my-hook", loaded.getName());
        assertEquals("wh-1", loaded.getId());
        assertEquals(WebhookConfig.Verifier.NONE, loaded.getVerifier());
    }

    @Test
    public void get_unknownId_returnsNull() {
        assertNull(dao.get("does-not-exist"));
    }

    @Test
    public void save_overwritesExisting() {
        WebhookConfig first = config("wh-2", "original-name");
        dao.save("wh-2", first);

        WebhookConfig second = config("wh-2", "updated-name");
        dao.save("wh-2", second);

        WebhookConfig loaded = dao.get("wh-2");
        assertEquals("updated-name", loaded.getName());
    }

    @Test
    public void remove_deletesConfig() {
        dao.save("wh-3", config("wh-3", "to-delete"));
        assertNotNull(dao.get("wh-3"));

        dao.remove("wh-3");
        assertNull(dao.get("wh-3"));
    }

    @Test
    public void remove_nonexistentId_noError() {
        dao.remove("phantom-id"); // should be a no-op
    }

    @Test
    public void getAll_returnsAllSavedConfigs() {
        dao.save("wh-a", config("wh-a", "hook-a"));
        dao.save("wh-b", config("wh-b", "hook-b"));
        dao.save("wh-c", config("wh-c", "hook-c"));

        List<WebhookConfig> all = dao.getAll();
        assertEquals(3, all.size());
    }

    @Test
    public void getAll_afterRemove_doesNotIncludeRemoved() {
        dao.save("wh-d", config("wh-d", "hook-d"));
        dao.save("wh-e", config("wh-e", "hook-e"));

        dao.remove("wh-d");

        List<WebhookConfig> all = dao.getAll();
        assertEquals(1, all.size());
        assertEquals("hook-e", all.get(0).getName());
    }

    @Test
    public void getAll_empty_returnsEmptyList() {
        assertTrue(dao.getAll().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Matcher index
    // -------------------------------------------------------------------------

    @Test
    public void saveMatchers_and_getMatchers_roundTrip() {
        Map<String, Map<String, Object>> matchers =
                Map.of("myWorkflow;1;waitTask", Map.of("$['event']['type']", "order"));

        dao.saveMatchers("wh-m1", matchers);

        Map<String, Map<String, Object>> loaded = dao.getMatchers("wh-m1");
        assertEquals(1, loaded.size());
        assertTrue(loaded.containsKey("myWorkflow;1;waitTask"));
        assertEquals("order", loaded.get("myWorkflow;1;waitTask").get("$['event']['type']"));
    }

    @Test
    public void getMatchers_unknownId_returnsEmptyMap() {
        assertTrue(dao.getMatchers("no-such-id").isEmpty());
    }

    @Test
    public void saveMatchers_overwritesPrevious() {
        dao.saveMatchers("wh-m2", Map.of("key1", Map.of("path", "v1")));
        dao.saveMatchers("wh-m2", Map.of("key2", Map.of("path", "v2")));

        Map<String, Map<String, Object>> loaded = dao.getMatchers("wh-m2");
        assertEquals(1, loaded.size());
        assertTrue(loaded.containsKey("key2"));
    }

    @Test
    public void removeMatchers_deletesMatcherIndex() {
        dao.saveMatchers("wh-m3", Map.of("k", Map.of("p", "v")));
        dao.removeMatchers("wh-m3");
        assertTrue(dao.getMatchers("wh-m3").isEmpty());
    }

    @Test
    public void removeMatchers_nonexistentId_noError() {
        dao.removeMatchers("ghost-id"); // should be a no-op
    }

    @Test
    public void saveMatchers_multipleEntries_allPreserved() {
        Map<String, Map<String, Object>> matchers =
                Map.of(
                        "wf1;1;task1", Map.of("$['type']", "A"),
                        "wf2;1;task2", Map.of("$['type']", "B", "$['region']", "us-west"));

        dao.saveMatchers("wh-m4", matchers);
        Map<String, Map<String, Object>> loaded = dao.getMatchers("wh-m4");

        assertEquals(2, loaded.size());
        assertEquals("A", loaded.get("wf1;1;task1").get("$['type']"));
        assertEquals("B", loaded.get("wf2;1;task2").get("$['type']"));
        assertEquals("us-west", loaded.get("wf2;1;task2").get("$['region']"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WebhookConfig config(String id, String name) {
        WebhookConfig c = new WebhookConfig();
        c.setId(id);
        c.setName(name);
        c.setVerifier(WebhookConfig.Verifier.NONE);
        return c;
    }
}
