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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisWorkflowMessageQueueDAOTest {

    private JedisProxy jedisProxy;
    private WorkflowMessageQueueProperties wmqProperties;
    private RedisWorkflowMessageQueueDAO dao;

    @Before
    public void setUp() {
        jedisProxy = mock(JedisProxy.class);
        wmqProperties = new WorkflowMessageQueueProperties();
        wmqProperties.setMaxQueueSize(10);
        wmqProperties.setTtlSeconds(3600);

        ObjectMapper objectMapper = new ObjectMapper();
        ConductorProperties conductorProperties = mock(ConductorProperties.class);
        RedisProperties redisProperties = mock(RedisProperties.class);

        dao =
                new RedisWorkflowMessageQueueDAO(
                        jedisProxy,
                        objectMapper,
                        conductorProperties,
                        redisProperties,
                        wmqProperties);
    }

    @Test
    public void testPushInvokesRpushAndExpire() {
        when(jedisProxy.llen(anyString())).thenReturn(0L);

        WorkflowMessage msg = new WorkflowMessage("id1", "wf1", Map.of("key", "value"), "now");
        dao.push("wf1", msg);

        verify(jedisProxy).rpush(anyString(), anyString());
        verify(jedisProxy).expire(anyString(), anyLong());
    }

    @Test(expected = IllegalStateException.class)
    public void testPushRejectsWhenQueueFull() {
        when(jedisProxy.llen(anyString())).thenReturn(10L);
        WorkflowMessage msg = new WorkflowMessage("id1", "wf1", null, "now");
        dao.push("wf1", msg);
    }

    @Test
    public void testPopReturnsDeserializedMessages() throws Exception {
        ObjectMapper om = new ObjectMapper();
        WorkflowMessage msg = new WorkflowMessage("id1", "wf1", Map.of("k", "v"), "now");
        String json = om.writeValueAsString(msg);

        when(jedisProxy.lrange(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(json));

        List<WorkflowMessage> result = dao.pop("wf1", 5);

        assertEquals(1, result.size());
        assertEquals("id1", result.get(0).getId());
        assertNotNull(result.get(0).getPayload());
        verify(jedisProxy).ltrim(anyString(), anyLong(), anyLong());
    }

    @Test
    public void testPopEmptyQueueReturnsEmptyList() {
        when(jedisProxy.lrange(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());

        List<WorkflowMessage> result = dao.pop("wf1", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testSizeDelegatesToLlen() {
        when(jedisProxy.llen(anyString())).thenReturn(7L);
        assertEquals(7L, dao.size("wf1"));
    }

    @Test
    public void testDeleteDelegatesToDel() {
        dao.delete("wf1");
        verify(jedisProxy).del(anyString());
    }
}
