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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.RetryLogic;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisMetadataDAOTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisMetadataDAO redisMetadataDAO;
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

        redisMetadataDAO =
                new RedisMetadataDAO(
                        jedisProxy, objectMapper, conductorProperties, redisProperties);
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
        // Re-create the DAO to reset the internal taskDefCache after flush
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        ConductorProperties conductorProperties = new ConductorProperties();
        RedisProperties redisProperties = new RedisProperties(conductorProperties);
        redisMetadataDAO =
                new RedisMetadataDAO(
                        jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @Test
    void testDup() {
        WorkflowDef def = new WorkflowDef();
        def.setName("testDup");
        def.setVersion(1);

        redisMetadataDAO.createWorkflowDef(def);
        assertThrows(ConflictException.class, () -> redisMetadataDAO.createWorkflowDef(def));
    }

    @Test
    void testWorkflowDefOperations() {
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
        assertEquals(3, workflow.getVersion());
    }

    @Test
    void testGetAllWorkflowDefsLatestVersions() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test1");
        def.setVersion(1);
        def.setDescription("description");
        def.setCreatedBy("unit_test");
        def.setCreateTime(1L);
        def.setOwnerApp("ownerApp");
        def.setUpdatedBy("unit_test2");
        def.setUpdateTime(2L);
        redisMetadataDAO.createWorkflowDef(def);

        def.setName("test2");
        redisMetadataDAO.createWorkflowDef(def);
        def.setVersion(2);
        redisMetadataDAO.createWorkflowDef(def);

        def.setName("test3");
        def.setVersion(1);
        redisMetadataDAO.createWorkflowDef(def);
        def.setVersion(2);
        redisMetadataDAO.createWorkflowDef(def);
        def.setVersion(3);
        redisMetadataDAO.createWorkflowDef(def);

        Map<String, WorkflowDef> allMap =
                redisMetadataDAO.getAllWorkflowDefsLatestVersions().stream()
                        .collect(Collectors.toMap(WorkflowDef::getName, Function.identity()));

        assertNotNull(allMap);
        assertEquals(3, allMap.size());
        assertEquals(1, allMap.get("test1").getVersion());
        assertEquals(2, allMap.get("test2").getVersion());
        assertEquals(3, allMap.get("test3").getVersion());
    }

    @Test
    void removeInvalidWorkflowDef() {
        assertThrows(NotFoundException.class, () -> redisMetadataDAO.removeWorkflowDef("hello", 1));
    }

    @Test
    void testTaskDefOperations() {
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

    @Test
    void testRemoveTaskDef() {
        assertThrows(
                NotFoundException.class,
                () -> redisMetadataDAO.removeTaskDef("test" + UUID.randomUUID()));
    }

    @Test
    void testDefaultsAreSetForResponseTimeout() {
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
        def.setResponseTimeoutSeconds(0);

        redisMetadataDAO.createTaskDef(def);

        TaskDef found = redisMetadataDAO.getTaskDef(def.getName());
        assertEquals(3600, found.getResponseTimeoutSeconds());
        found.setTimeoutSeconds(200);
        found.setResponseTimeoutSeconds(0);
        redisMetadataDAO.updateTaskDef(found);
        TaskDef foundNew = redisMetadataDAO.getTaskDef(def.getName());
        assertEquals(199, foundNew.getResponseTimeoutSeconds());
    }

    @Test
    void testGetWorkflowDefNotFound() {
        Optional<WorkflowDef> result = redisMetadataDAO.getWorkflowDef("nonexistent", 1);
        assertFalse(result.isPresent());
    }

    @Test
    void testGetLatestWorkflowDefNotFound() {
        Optional<WorkflowDef> result = redisMetadataDAO.getLatestWorkflowDef("nonexistent");
        assertFalse(result.isPresent());
    }

    @Test
    void testGetTaskDefNotFound() {
        TaskDef result = redisMetadataDAO.getTaskDef("nonexistent");
        assertNull(result);
    }

    @Test
    void testUpdateWorkflowDef() {
        WorkflowDef def = new WorkflowDef();
        def.setName("update_test");
        def.setVersion(1);
        def.setDescription("original");

        redisMetadataDAO.createWorkflowDef(def);

        def.setDescription("updated");
        redisMetadataDAO.updateWorkflowDef(def);

        WorkflowDef found = redisMetadataDAO.getWorkflowDef("update_test", 1).get();
        assertEquals("updated", found.getDescription());
    }

    @Test
    void testFindAll() {
        for (int i = 0; i < 3; i++) {
            WorkflowDef def = new WorkflowDef();
            def.setName("wf_" + i);
            def.setVersion(1);
            redisMetadataDAO.createWorkflowDef(def);
        }

        List<String> names = redisMetadataDAO.findAll();
        assertEquals(3, names.size());
    }

    private RedisMetadataDAO newCacheEnabledDAO() {
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        ConductorProperties conductorProperties = new ConductorProperties();
        RedisProperties redisProperties = new RedisProperties(conductorProperties);
        redisProperties.setWorkflowDefCacheEnabled(true);
        return new RedisMetadataDAO(jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @Test
    void testWorkflowDefCacheReflectsCreateAndRemove() {
        RedisMetadataDAO dao = newCacheEnabledDAO();
        assertEquals(0, dao.getAllWorkflowDefs().size());

        WorkflowDef def = new WorkflowDef();
        def.setName("cachedWf");
        def.setVersion(1);
        dao.createWorkflowDef(def);

        List<WorkflowDef> all = dao.getAllWorkflowDefs();
        assertEquals(1, all.size());
        assertEquals("cachedWf", all.get(0).getName());

        dao.removeWorkflowDef("cachedWf", 1);
        assertEquals(0, dao.getAllWorkflowDefs().size());
    }

    @Test
    void testGetAllWorkflowDefsLatestVersionsFromCache() {
        RedisMetadataDAO dao = newCacheEnabledDAO();
        for (int version = 1; version <= 3; version++) {
            WorkflowDef def = new WorkflowDef();
            def.setName("wfA");
            def.setVersion(version);
            dao.createWorkflowDef(def);
        }
        WorkflowDef defB = new WorkflowDef();
        defB.setName("wfB");
        defB.setVersion(1);
        dao.createWorkflowDef(defB);

        Map<String, WorkflowDef> latestByName =
                dao.getAllWorkflowDefsLatestVersions().stream()
                        .collect(Collectors.toMap(WorkflowDef::getName, d -> d));

        assertEquals(2, latestByName.size());
        assertEquals(3, latestByName.get("wfA").getVersion());
        assertEquals(1, latestByName.get("wfB").getVersion());
    }
}
