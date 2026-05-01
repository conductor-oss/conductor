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
package org.conductoross.conductor.scheduler.cassandra.dao;

import java.util.*;

import org.junit.*;
import org.testcontainers.containers.CassandraContainer;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

import static org.junit.Assert.*;

public class CassandraSchedulerDAOTest {

    private static final String KEYSPACE = "conductor_test";

    @ClassRule
    public static final CassandraContainer<?> cassandra =
            new CassandraContainer<>("cassandra:3.11.2");

    private static Session session;
    private static ObjectMapper objectMapper;
    private CassandraSchedulerDAO dao;

    @BeforeClass
    public static void setUpOnce() {
        session = cassandra.getCluster().newSession();
        session.execute(
                "CREATE KEYSPACE IF NOT EXISTS "
                        + KEYSPACE
                        + " WITH replication = {'class':'SimpleStrategy','replication_factor':1}");
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    @Before
    public void setUp() {
        // Drop and recreate tables to get a clean state
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".scheduler_schedules");
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".scheduler_executions");
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".scheduler_exec_by_schedule");
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".scheduler_exec_by_state");
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".scheduler_sched_by_workflow");
        CassandraProperties properties = new CassandraProperties();
        properties.setKeyspace(KEYSPACE);
        dao = new CassandraSchedulerDAO(session, objectMapper, properties);
    }

    @AfterClass
    public static void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    // =========================================================================
    // Schedule CRUD
    // =========================================================================

    @Test
    public void testSaveAndFindSchedule() {
        WorkflowScheduleModel schedule = buildSchedule("test-schedule", "my-workflow");
        dao.updateSchedule(schedule);

        WorkflowScheduleModel found = dao.findScheduleByName("test-schedule");

        assertNotNull(found);
        assertEquals("test-schedule", found.getName());
        assertEquals("my-workflow", found.getStartWorkflowRequest().getName());
        assertEquals("0 0 9 * * MON-FRI", found.getCronExpression());
        assertEquals("UTC", found.getZoneId());
    }

    @Test
    public void testFindScheduleByName_notFound_returnsNull() {
        assertNull(dao.findScheduleByName("no-such-schedule"));
    }

    @Test
    public void testUpdateSchedule_upserts() {
        WorkflowScheduleModel schedule = buildSchedule("upsert-schedule", "workflow-v1");
        dao.updateSchedule(schedule);

        schedule.setCronExpression("0 0 10 * * *");
        dao.updateSchedule(schedule);

        WorkflowScheduleModel found = dao.findScheduleByName("upsert-schedule");
        assertEquals("0 0 10 * * *", found.getCronExpression());
    }

    @Test
    public void testGetAllSchedules() {
        dao.updateSchedule(buildSchedule("sched-a", "wf-a"));
        dao.updateSchedule(buildSchedule("sched-b", "wf-b"));
        dao.updateSchedule(buildSchedule("sched-c", "wf-c"));

        assertEquals(3, dao.getAllSchedules().size());
    }

    @Test
    public void testFindAllSchedulesByWorkflow() {
        dao.updateSchedule(buildSchedule("s1", "target-wf"));
        dao.updateSchedule(buildSchedule("s2", "target-wf"));
        dao.updateSchedule(buildSchedule("s3", "other-wf"));

        List<WorkflowScheduleModel> results = dao.findAllSchedules("target-wf");
        assertEquals(2, results.size());
        assertTrue(
                results.stream()
                        .allMatch(s -> "target-wf".equals(s.getStartWorkflowRequest().getName())));
    }

    @Test
    public void testFindAllByNames() {
        dao.updateSchedule(buildSchedule("find-a", "wf-a"));
        dao.updateSchedule(buildSchedule("find-b", "wf-b"));
        dao.updateSchedule(buildSchedule("find-c", "wf-c"));

        Map<String, WorkflowScheduleModel> result =
                dao.findAllByNames(Set.of("find-a", "find-c", "no-such-schedule"));
        assertEquals(2, result.size());
        assertTrue(result.containsKey("find-a"));
        assertTrue(result.containsKey("find-c"));
    }

    @Test
    public void testFindAllByNames_emptySet_returnsEmpty() {
        assertTrue(dao.findAllByNames(Set.of()).isEmpty());
    }

    @Test
    public void testFindAllByNames_nullSet_returnsEmpty() {
        assertTrue(dao.findAllByNames(null).isEmpty());
    }

    @Test
    public void testDeleteSchedule_removesScheduleAndExecutions() {
        dao.updateSchedule(buildSchedule("to-delete", "some-wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("to-delete");
        dao.saveExecutionRecord(exec);

        dao.deleteWorkflowSchedule("to-delete");

        assertNull(dao.findScheduleByName("to-delete"));
        assertNull(dao.readExecutionRecord(exec.getExecutionId()));
    }

    @Test
    public void testDeleteSchedule_nonExistent_doesNotThrow() {
        dao.deleteWorkflowSchedule("does-not-exist");
    }

    // =========================================================================
    // JSON round-trip fidelity
    // =========================================================================

    @Test
    public void testScheduleJsonRoundTrip_allFields() {
        WorkflowScheduleModel schedule = buildSchedule("round-trip-schedule", "round-trip-wf");
        schedule.setZoneId("America/New_York");
        schedule.setPaused(true);
        schedule.setPausedReason("maintenance window");
        schedule.setScheduleStartTime(1_000_000L);
        schedule.setScheduleEndTime(2_000_000L);
        schedule.setRunCatchupScheduleInstances(true);
        schedule.setCreateTime(12345L);
        schedule.setUpdatedTime(67890L);
        schedule.setCreatedBy("alice");
        schedule.setUpdatedBy("bob");
        schedule.setDescription("Daily business hours schedule");
        schedule.setNextRunTime(99999L);
        dao.updateSchedule(schedule);

        WorkflowScheduleModel found = dao.findScheduleByName("round-trip-schedule");

        assertNotNull(found);
        assertEquals("America/New_York", found.getZoneId());
        assertTrue(found.isPaused());
        assertEquals("maintenance window", found.getPausedReason());
        assertEquals(Long.valueOf(1_000_000L), found.getScheduleStartTime());
        assertEquals(Long.valueOf(2_000_000L), found.getScheduleEndTime());
        assertTrue(found.isRunCatchupScheduleInstances());
        assertEquals(Long.valueOf(12345L), found.getCreateTime());
        assertEquals(Long.valueOf(67890L), found.getUpdatedTime());
        assertEquals("alice", found.getCreatedBy());
        assertEquals("bob", found.getUpdatedBy());
        assertEquals("Daily business hours schedule", found.getDescription());
        assertEquals(Long.valueOf(99999L), found.getNextRunTime());
    }

    @Test
    public void testExecutionJsonRoundTrip_allFields() {
        dao.updateSchedule(buildSchedule("exec-rt-schedule", "exec-rt-wf"));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName("exec-rt-wf");
        req.setVersion(2);

        WorkflowScheduleExecutionModel exec = buildExecution("exec-rt-schedule");
        exec.setWorkflowId("wf-instance-456");
        exec.setWorkflowName("exec-rt-wf");
        exec.setReason("Something went wrong");
        exec.setStackTrace(
                "java.lang.RuntimeException: Something went wrong\n\tat Foo.bar(Foo.java:42)");
        exec.setState(WorkflowScheduleExecutionModel.State.FAILED);
        exec.setStartWorkflowRequest(req);
        dao.saveExecutionRecord(exec);

        WorkflowScheduleExecutionModel found = dao.readExecutionRecord(exec.getExecutionId());

        assertNotNull(found);
        assertEquals("wf-instance-456", found.getWorkflowId());
        assertEquals("exec-rt-wf", found.getWorkflowName());
        assertEquals("Something went wrong", found.getReason());
        assertNotNull(found.getStackTrace());
        assertTrue(found.getStackTrace().contains("RuntimeException"));
        assertEquals(WorkflowScheduleExecutionModel.State.FAILED, found.getState());
        assertNotNull(found.getStartWorkflowRequest());
        assertEquals("exec-rt-wf", found.getStartWorkflowRequest().getName());
        assertEquals(Integer.valueOf(2), found.getStartWorkflowRequest().getVersion());
    }

    // =========================================================================
    // Execution tracking
    // =========================================================================

    @Test
    public void testSaveAndReadExecutionRecord() {
        dao.updateSchedule(buildSchedule("exec-test", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("exec-test");
        dao.saveExecutionRecord(exec);

        WorkflowScheduleExecutionModel found = dao.readExecutionRecord(exec.getExecutionId());
        assertNotNull(found);
        assertEquals(exec.getExecutionId(), found.getExecutionId());
        assertEquals("exec-test", found.getScheduleName());
        assertEquals(WorkflowScheduleExecutionModel.State.POLLED, found.getState());
    }

    @Test
    public void testSaveExecutionRecord_idempotent() {
        dao.updateSchedule(buildSchedule("idem-test", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("idem-test");
        dao.saveExecutionRecord(exec);
        dao.saveExecutionRecord(exec);

        List<String> pending = dao.getPendingExecutionRecordIds();
        assertEquals(1, pending.size());
    }

    @Test
    public void testUpdateExecutionRecord_transitionToExecuted() {
        dao.updateSchedule(buildSchedule("state-test", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("state-test");
        dao.saveExecutionRecord(exec);

        exec.setState(WorkflowScheduleExecutionModel.State.EXECUTED);
        exec.setWorkflowId("conductor-wf-123");
        dao.saveExecutionRecord(exec);

        WorkflowScheduleExecutionModel found = dao.readExecutionRecord(exec.getExecutionId());
        assertEquals(WorkflowScheduleExecutionModel.State.EXECUTED, found.getState());
        assertEquals("conductor-wf-123", found.getWorkflowId());
    }

    @Test
    public void testRemoveExecutionRecord() {
        dao.updateSchedule(buildSchedule("remove-exec", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("remove-exec");
        dao.saveExecutionRecord(exec);

        dao.removeExecutionRecord(exec.getExecutionId());

        assertNull(dao.readExecutionRecord(exec.getExecutionId()));
    }

    @Test
    public void testGetPendingExecutionRecordIds() {
        dao.updateSchedule(buildSchedule("pending-test", "wf"));

        WorkflowScheduleExecutionModel polled1 = buildExecution("pending-test");
        WorkflowScheduleExecutionModel polled2 = buildExecution("pending-test");
        WorkflowScheduleExecutionModel executed = buildExecution("pending-test");
        executed.setState(WorkflowScheduleExecutionModel.State.EXECUTED);

        dao.saveExecutionRecord(polled1);
        dao.saveExecutionRecord(polled2);
        dao.saveExecutionRecord(executed);

        List<String> pendingIds = dao.getPendingExecutionRecordIds();
        assertEquals(2, pendingIds.size());
        assertTrue(pendingIds.contains(polled1.getExecutionId()));
        assertTrue(pendingIds.contains(polled2.getExecutionId()));
    }

    @Test
    public void testGetPendingExecutionRecordIds_afterTransition() {
        dao.updateSchedule(buildSchedule("transition-test", "wf"));

        WorkflowScheduleExecutionModel exec = buildExecution("transition-test");
        dao.saveExecutionRecord(exec);
        assertTrue(dao.getPendingExecutionRecordIds().contains(exec.getExecutionId()));

        exec.setState(WorkflowScheduleExecutionModel.State.EXECUTED);
        dao.saveExecutionRecord(exec);

        assertFalse(
                "EXECUTED record must not appear in pending list",
                dao.getPendingExecutionRecordIds().contains(exec.getExecutionId()));
    }

    // =========================================================================
    // Next-run time management
    // =========================================================================

    @Test
    public void testSetAndGetNextRunTime() {
        dao.updateSchedule(buildSchedule("next-run-test", "wf"));

        long epochMillis = System.currentTimeMillis() + 60_000;
        dao.setNextRunTimeInEpoch("next-run-test", epochMillis);

        assertEquals(epochMillis, dao.getNextRunTimeInEpoch("next-run-test"));
    }

    @Test
    public void testGetNextRunTime_notSet_returnsMinusOne() {
        dao.updateSchedule(buildSchedule("no-next-run", "wf"));
        assertEquals(-1L, dao.getNextRunTimeInEpoch("no-next-run"));
    }

    @Test
    public void testGetNextRunTime_nonExistent_returnsMinusOne() {
        assertEquals(-1L, dao.getNextRunTimeInEpoch("non-existent-schedule"));
    }

    // =========================================================================
    // Search
    // =========================================================================

    @Test
    public void testSearchSchedules_byWorkflowName() {
        dao.updateSchedule(buildSchedule("search-1", "search-wf"));
        dao.updateSchedule(buildSchedule("search-2", "search-wf"));
        dao.updateSchedule(buildSchedule("search-3", "other-wf"));

        SearchResult<WorkflowScheduleModel> result =
                dao.searchSchedules("search-wf", null, null, null, 0, 10, null);
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearchSchedules_byPaused() {
        WorkflowScheduleModel paused = buildSchedule("paused-sched", "wf");
        paused.setPaused(true);
        dao.updateSchedule(paused);
        dao.updateSchedule(buildSchedule("active-sched", "wf"));

        SearchResult<WorkflowScheduleModel> result =
                dao.searchSchedules(null, null, true, null, 0, 10, null);
        assertEquals(1, result.getTotalHits());
        assertEquals("paused-sched", result.getResults().get(0).getName());
    }

    @Test
    public void testSearchSchedules_pagination() {
        for (int i = 0; i < 5; i++) {
            dao.updateSchedule(buildSchedule("page-" + i, "wf"));
        }

        SearchResult<WorkflowScheduleModel> page1 =
                dao.searchSchedules(null, null, null, null, 0, 2, null);
        assertEquals(5, page1.getTotalHits());
        assertEquals(2, page1.getResults().size());

        SearchResult<WorkflowScheduleModel> page2 =
                dao.searchSchedules(null, null, null, null, 2, 2, null);
        assertEquals(5, page2.getTotalHits());
        assertEquals(2, page2.getResults().size());
    }

    // =========================================================================
    // Volume
    // =========================================================================

    @Test
    public void testVolume_getAllSchedules_largeCount() {
        int count = 100;
        for (int i = 0; i < count; i++) {
            dao.updateSchedule(buildSchedule("volume-sched-" + i, "wf-" + (i % 10)));
        }

        List<WorkflowScheduleModel> all = dao.getAllSchedules();
        assertEquals(count, all.size());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private WorkflowScheduleModel buildSchedule(String name, String workflowName) {
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);

        WorkflowScheduleModel schedule = new WorkflowScheduleModel();
        schedule.setName(name);
        schedule.setCronExpression("0 0 9 * * MON-FRI");
        schedule.setZoneId("UTC");
        schedule.setStartWorkflowRequest(startReq);
        schedule.setPaused(false);
        schedule.setCreateTime(System.currentTimeMillis());
        return schedule;
    }

    private WorkflowScheduleExecutionModel buildExecution(String scheduleName) {
        WorkflowScheduleExecutionModel exec = new WorkflowScheduleExecutionModel();
        exec.setExecutionId(UUID.randomUUID().toString());
        exec.setScheduleName(scheduleName);
        exec.setScheduledTime(System.currentTimeMillis());
        exec.setExecutionTime(System.currentTimeMillis());
        exec.setState(WorkflowScheduleExecutionModel.State.POLLED);
        exec.setZoneId("UTC");
        return exec;
    }
}
