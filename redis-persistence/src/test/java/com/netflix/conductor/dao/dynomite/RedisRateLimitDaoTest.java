package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.redis.JedisMock;
import com.netflix.conductor.dyno.DynoProxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import redis.clients.jedis.commands.JedisCommands;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RedisRateLimitDaoTest {

    private RedisRateLimitingDAO rateLimitingDao;
    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    @Before
    public void init() {
        Configuration config = new TestConfiguration();
        JedisCommands jedisMock = new JedisMock();
        DynoProxy dynoClient = new DynoProxy(jedisMock);

        rateLimitingDao = new RedisRateLimitingDAO(dynoClient, objectMapper, config);
    }


    @Test
    public void testExceedsRateLimitWhenNoRateLimitSet() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition");
        Task task =new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }
    @Test
    public void testExceedsRateLimitWithinLimit() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition");
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setRateLimitPerFrequency(20);
        Task task =new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }
    @Test
    public void testExceedsRateLimitOutOfLimit() {
        TaskDef taskDef = new TaskDef("TestTaskDefinition");
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setRateLimitPerFrequency(1);
        Task task =new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDef.getName());
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task, taskDef));
    }
}