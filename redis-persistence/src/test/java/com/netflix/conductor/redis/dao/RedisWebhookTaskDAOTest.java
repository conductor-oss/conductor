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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class RedisWebhookTaskDAOTest {

    private RedisWebhookTaskDAO dao;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void init() {
        ConductorProperties conductorProperties = mock(ConductorProperties.class);
        RedisProperties properties = mock(RedisProperties.class);
        JedisCommands jedisMock = new JedisMock();
        JedisProxy jedisProxy = new JedisProxy(jedisMock);

        dao = new RedisWebhookTaskDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Test
    public void put_and_get_singleTask() {
        dao.put("hash1", "task-a");

        List<String> result = dao.get("hash1");
        assertEquals(1, result.size());
        assertTrue(result.contains("task-a"));
    }

    @Test
    public void put_multipleTasksUnderSameHash_allReturned() {
        dao.put("hash2", "task-x");
        dao.put("hash2", "task-y");
        dao.put("hash2", "task-z");

        List<String> result = dao.get("hash2");
        assertEquals(3, result.size());
        assertTrue(result.contains("task-x"));
        assertTrue(result.contains("task-y"));
        assertTrue(result.contains("task-z"));
    }

    @Test
    public void get_unknownHash_returnsEmpty() {
        List<String> result = dao.get("no-such-hash");
        assertTrue(result.isEmpty());
    }

    @Test
    public void remove_singleTask_removedFromSet() {
        dao.put("hash3", "task-to-remove");
        dao.put("hash3", "task-to-keep");

        dao.remove("hash3", "task-to-remove");

        List<String> result = dao.get("hash3");
        assertEquals(1, result.size());
        assertFalse(result.contains("task-to-remove"));
        assertTrue(result.contains("task-to-keep"));
    }

    @Test
    public void remove_nonexistentTask_noError() {
        dao.put("hash4", "task-real");
        dao.remove("hash4", "task-phantom"); // should be a no-op
        List<String> result = dao.get("hash4");
        assertEquals(1, result.size());
    }

    @Test
    public void popAll_returnsAndRemovesAllTasks() {
        dao.put("hash5", "task-1");
        dao.put("hash5", "task-2");

        List<String> popped = dao.popAll("hash5");
        assertEquals(2, popped.size());
        assertTrue(popped.contains("task-1"));
        assertTrue(popped.contains("task-2"));

        // After popAll, hash is empty
        assertTrue(dao.get("hash5").isEmpty());
    }

    @Test
    public void popAll_emptyHash_returnsEmpty() {
        List<String> popped = dao.popAll("hash-empty");
        assertTrue(popped.isEmpty());
    }

    @Test
    public void differentHashes_areIndependent() {
        dao.put("hashA", "task-a1");
        dao.put("hashB", "task-b1");

        assertEquals(List.of("task-a1"), dao.get("hashA"));
        assertEquals(List.of("task-b1"), dao.get("hashB"));

        dao.remove("hashA", "task-a1");
        assertTrue(dao.get("hashA").isEmpty());
        assertEquals(List.of("task-b1"), dao.get("hashB"));
    }
}
