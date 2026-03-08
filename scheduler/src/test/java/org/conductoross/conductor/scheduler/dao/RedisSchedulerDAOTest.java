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
package org.conductoross.conductor.scheduler.dao;

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link RedisSchedulerDAO} using an in-memory Redis mock.
 *
 * <p>{@link RedisSchedulerDAO} implements {@link SchedulerCacheDAO} (not the full
 * {@link SchedulerDAO}). Only cache-layer methods are tested here: schedule storage, existence
 * checks, deletion, and next-run-time management. Execution CRUD lives in the authoritative SQL
 * {@link SchedulerDAO} implementations.
 *
 * <p>Uses {@link JedisMock} (backed by {@code org.rarefiedredis.redis.RedisMock}) so no Docker
 * or external Redis instance is needed.
 */
public class RedisSchedulerDAOTest {

    private RedisSchedulerDAO dao;

    @Before
    public void setUp() {
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        JedisProxy jedisProxy = new JedisProxy(new JedisMock());
        dao = new RedisSchedulerDAO(jedisProxy, objectMapper);
    }

    // -------------------------------------------------------------------------
    // Schedule cache CRUD
    // -------------------------------------------------------------------------

    @Test
    public void testUpdateAndFindSchedule() {
        WorkflowSchedule schedule = buildSchedule("test-schedule", "my-workflow");
        dao.updateSchedule(schedule);

        WorkflowSchedule found = dao.findScheduleByName("test-schedule");

        assertNotNull(found);
        assertEquals("test-schedule", found.getName());
        assertEquals("my-workflow", found.getStartWorkflowRequest().getName());
        assertEquals("0 0 9 * * MON-FRI", found.getCronExpression());
        assertEquals("UTC", found.getZoneId());
    }

    @Test
    public void testFindScheduleByName_notFound_returnsNull() {
        WorkflowSchedule found = dao.findScheduleByName("no-such-schedule");
        assertNull(found);
    }

    @Test
    public void testUpdateSchedule_upserts() {
        WorkflowSchedule schedule = buildSchedule("upsert-schedule", "workflow-v1");
        dao.updateSchedule(schedule);

        schedule.setCronExpression("0 0 10 * * *");
        dao.updateSchedule(schedule);

        WorkflowSchedule found = dao.findScheduleByName("upsert-schedule");
        assertEquals("0 0 10 * * *", found.getCronExpression());
    }

    @Test
    public void testExists_presentAndAbsent() {
        assertFalse(dao.exists("my-schedule"));

        dao.updateSchedule(buildSchedule("my-schedule", "wf"));
        assertTrue(dao.exists("my-schedule"));
    }

    @Test
    public void testDeleteWorkflowSchedule_removesScheduleAndRuntime() {
        WorkflowSchedule schedule = buildSchedule("to-delete", "some-wf");
        dao.updateSchedule(schedule);
        dao.setNextRunTimeInEpoch("to-delete", 123456789L);

        assertTrue(dao.exists("to-delete"));
        assertEquals(123456789L, dao.getNextRunTimeInEpoch("to-delete"));

        dao.deleteWorkflowSchedule("to-delete");

        assertNull(dao.findScheduleByName("to-delete"));
        assertFalse(dao.exists("to-delete"));
        assertEquals(-1L, dao.getNextRunTimeInEpoch("to-delete"));
    }

    // -------------------------------------------------------------------------
    // Next-run time management
    // -------------------------------------------------------------------------

    @Test
    public void testSetAndGetNextRunTime() {
        long epochMillis = System.currentTimeMillis() + 60_000;
        dao.setNextRunTimeInEpoch("next-run-test", epochMillis);

        long retrieved = dao.getNextRunTimeInEpoch("next-run-test");
        assertEquals(epochMillis, retrieved);
    }

    @Test
    public void testGetNextRunTime_notSet_returnsMinusOne() {
        long result = dao.getNextRunTimeInEpoch("no-next-run");
        assertEquals(-1L, result);
    }

    @Test
    public void testGetNextRunTime_unknownSchedule_returnsMinusOne() {
        long result = dao.getNextRunTimeInEpoch("completely-unknown-schedule");
        assertEquals(-1L, result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkflowSchedule buildSchedule(String name, String workflowName) {
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName(name);
        schedule.setCronExpression("0 0 9 * * MON-FRI");
        schedule.setZoneId("UTC");
        schedule.setStartWorkflowRequest(startReq);
        schedule.setPaused(false);
        schedule.setCreateTime(System.currentTimeMillis());
        return schedule;
    }
}
