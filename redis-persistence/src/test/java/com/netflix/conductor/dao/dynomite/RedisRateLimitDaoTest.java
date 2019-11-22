package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.redis.JedisMock;
import com.netflix.conductor.dyno.DynoProxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import redis.clients.jedis.commands.JedisCommands;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RedisRateLimitDaoTest {

    private RedisRateLimitingDao rateLimitingDao;
    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    @Before
    public void init() {
        Configuration config = new TestConfiguration();
        JedisCommands jedisMock = new JedisMock();
        DynoProxy dynoClient = new DynoProxy(jedisMock);

        rateLimitingDao = new RedisRateLimitingDao(dynoClient, objectMapper, config);
    }


    @Test
    public void testExceedsRateLimitWhenNoRateLimitSet() {
        Task task =new Task();
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task));
    }
    @Test
    public void testExceedsRateLimitWithinLimit() {
        Task task =new Task();
        task.setRateLimitFrequencyInSeconds(60);
        task.setRateLimitPerFrequency(20);
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task));
    }
    @Test
    public void testExceedsRateLimitOutOfLimit() {
        Task task =new Task();
        task.setRateLimitFrequencyInSeconds(60);
        task.setRateLimitPerFrequency(1);
        assertFalse(rateLimitingDao.exceedsRateLimitPerFrequency(task));
        assertTrue(rateLimitingDao.exceedsRateLimitPerFrequency(task));
    }
}
