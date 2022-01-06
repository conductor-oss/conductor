/*
 * Copyright 2021 Netflix, Inc.
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.RetryLogic;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.commands.JedisCommands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class RedisMetadataDAOTest {

    private RedisMetadataDAO redisMetadataDAO;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void init() {
        ConductorProperties conductorProperties = mock(ConductorProperties.class);
        RedisProperties properties = mock(RedisProperties.class);
        when(properties.getTaskDefCacheRefreshInterval()).thenReturn(Duration.ofSeconds(60));
        JedisCommands jedisMock = new JedisMock();
        JedisProxy jedisProxy = new JedisProxy(jedisMock);

        redisMetadataDAO =
                new RedisMetadataDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Test(expected = ApplicationException.class)
    public void testDup() {
        WorkflowDef def = new WorkflowDef();
        def.setName("testDup");
        def.setVersion(1);

        redisMetadataDAO.createWorkflowDef(def);
        redisMetadataDAO.createWorkflowDef(def);
    }

    @Test
    public void testWorkflowDefOperations() {

        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        def.setVersion(1);
        def.setDescription("description");
        def.setCreatedBy("unit_test");
        def.setCreateTime(1L);
        def.setOwnerApp("ownerApp");
        def.setUpdatedBy("unit_test2");
        def.setUpdateTime(2L);

        redisMetadataDAO.createWorkflowDef(def);

        List<WorkflowDef> all = redisMetadataDAO.getAllWorkflowDefs();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(1, all.get(0).getVersion());

        WorkflowDef found = redisMetadataDAO.getWorkflowDef("test", 1).get();
        assertEquals(def, found);

        def.setVersion(2);
        redisMetadataDAO.createWorkflowDef(def);

        all = redisMetadataDAO.getAllWorkflowDefs();
        assertNotNull(all);
        assertEquals(2, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals(1, all.get(0).getVersion());

        found = redisMetadataDAO.getLatestWorkflowDef(def.getName()).get();
        assertEquals(def.getName(), found.getName());
        assertEquals(def.getVersion(), found.getVersion());
        assertEquals(2, found.getVersion());

        all = redisMetadataDAO.getAllVersions(def.getName());
        assertNotNull(all);
        assertEquals(2, all.size());
        assertEquals("test", all.get(0).getName());
        assertEquals("test", all.get(1).getName());
        assertEquals(1, all.get(0).getVersion());
        assertEquals(2, all.get(1).getVersion());

        def.setDescription("updated");
        redisMetadataDAO.updateWorkflowDef(def);
        found = redisMetadataDAO.getWorkflowDef(def.getName(), def.getVersion()).get();
        assertEquals(def.getDescription(), found.getDescription());

        List<String> allnames = redisMetadataDAO.findAll();
        assertNotNull(allnames);
        assertEquals(1, allnames.size());
        assertEquals(def.getName(), allnames.get(0));

        redisMetadataDAO.removeWorkflowDef("test", 1);
        Optional<WorkflowDef> deleted = redisMetadataDAO.getWorkflowDef("test", 1);
        assertFalse(deleted.isPresent());
        redisMetadataDAO.removeWorkflowDef("test", 2);
        Optional<WorkflowDef> latestDef = redisMetadataDAO.getLatestWorkflowDef("test");
        assertFalse(latestDef.isPresent());

        WorkflowDef[] workflowDefsArray = new WorkflowDef[3];
        for (int i = 1; i <= 3; i++) {
            workflowDefsArray[i - 1] = new WorkflowDef();
            workflowDefsArray[i - 1].setName("test");
            workflowDefsArray[i - 1].setVersion(i);
            workflowDefsArray[i - 1].setDescription("description");
            workflowDefsArray[i - 1].setCreatedBy("unit_test");
            workflowDefsArray[i - 1].setCreateTime(1L);
            workflowDefsArray[i - 1].setOwnerApp("ownerApp");
            workflowDefsArray[i - 1].setUpdatedBy("unit_test2");
            workflowDefsArray[i - 1].setUpdateTime(2L);
            redisMetadataDAO.createWorkflowDef(workflowDefsArray[i - 1]);
        }
        redisMetadataDAO.removeWorkflowDef("test", 1);
        redisMetadataDAO.removeWorkflowDef("test", 2);
        WorkflowDef workflow = redisMetadataDAO.getLatestWorkflowDef("test").get();
        assertEquals(workflow.getVersion(), 3);
    }

    @Test(expected = ApplicationException.class)
    public void removeInvalidWorkflowDef() {
        redisMetadataDAO.removeWorkflowDef("hello", 1);
    }

    @Test
    public void testTaskDefOperations() {

        TaskDef def = new TaskDef("taskA");
        def.setDescription("description");
        def.setCreatedBy("unit_test");
        def.setCreateTime(1L);
        def.setInputKeys(Arrays.asList("a", "b", "c"));
        def.setOutputKeys(Arrays.asList("01", "o2"));
        def.setOwnerApp("ownerApp");
        def.setRetryCount(3);
        def.setRetryDelaySeconds(100);
        def.setRetryLogic(RetryLogic.FIXED);
        def.setTimeoutPolicy(TimeoutPolicy.ALERT_ONLY);
        def.setUpdatedBy("unit_test2");
        def.setUpdateTime(2L);
        def.setRateLimitPerFrequency(50);
        def.setRateLimitFrequencyInSeconds(1);

        redisMetadataDAO.createTaskDef(def);

        TaskDef found = redisMetadataDAO.getTaskDef(def.getName());
        assertEquals(def, found);

        def.setDescription("updated description");
        redisMetadataDAO.updateTaskDef(def);
        found = redisMetadataDAO.getTaskDef(def.getName());
        assertEquals(def, found);
        assertEquals("updated description", found.getDescription());

        for (int i = 0; i < 9; i++) {
            TaskDef tdf = new TaskDef("taskA" + i);
            redisMetadataDAO.createTaskDef(tdf);
        }

        List<TaskDef> all = redisMetadataDAO.getAllTaskDefs();
        assertNotNull(all);
        assertEquals(10, all.size());
        Set<String> allnames = all.stream().map(TaskDef::getName).collect(Collectors.toSet());
        assertEquals(10, allnames.size());
        List<String> sorted = allnames.stream().sorted().collect(Collectors.toList());
        assertEquals(def.getName(), sorted.get(0));

        for (int i = 0; i < 9; i++) {
            assertEquals(def.getName() + i, sorted.get(i + 1));
        }

        for (int i = 0; i < 9; i++) {
            redisMetadataDAO.removeTaskDef(def.getName() + i);
        }
        all = redisMetadataDAO.getAllTaskDefs();
        assertNotNull(all);
        assertEquals(1, all.size());
        assertEquals(def.getName(), all.get(0).getName());
    }

    @Test(expected = ApplicationException.class)
    public void testRemoveTaskDef() {
        redisMetadataDAO.removeTaskDef("test" + UUID.randomUUID().toString());
    }
}
