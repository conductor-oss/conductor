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
package io.orkes.conductor.scheduler.dao;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

import static org.junit.Assert.*;

/**
 * Shared contract test suite for all {@link SchedulerDAO} implementations.
 *
 * <p>Every persistence module (Postgres, MySQL, SQLite) subclasses this and provides only the
 * Spring wiring ({@link #dao()} and {@link #dataSource()}). Adding a test here automatically covers
 * all backends.
 *
 * <p><b>Test categories:</b>
 *
 * <ol>
 *   <li>Schedule CRUD — create/read/update/delete and bulk-lookup
 *   <li>JSON round-trip fidelity — all model fields survive the blob serialization/deserialization
 *   <li>Execution tracking — POLLED->EXECUTED/FAILED lifecycle, pending-ID filter
 *   <li>Next-run time management — get/set semantics and interaction with {@code updateSchedule}
 *   <li>Volume — 100-schedule bulk insert/retrieve
 *   <li>Concurrency — 10 threads doing simultaneous upserts on the same schedule name
 * </ol>
 */
public abstract class AbstractSchedulerDAOTest {

    /** Returns the DAO under test. Subclasses provide this via {@code @Autowired}. */
    protected abstract SchedulerDAO dao();

    /**
     * Returns the datasource wired into the Spring test context. Used to truncate tables between
     * tests.
     */
    protected abstract DataSource dataSource();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Before
    public final void setUp() throws Exception {
        try (Connection conn = dataSource().getConnection()) {
            conn.prepareStatement("DELETE FROM scheduler_execution").executeUpdate();
            conn.prepareStatement("DELETE FROM scheduler").executeUpdate();
            conn.prepareStatement("DELETE FROM workflow_scheduled_executions").executeUpdate();
        }
    }

    // =========================================================================
    // 1. Schedule CRUD
    // =========================================================================

    @Test
    public void testSaveAndFindSchedule() {
        WorkflowScheduleModel schedule = buildSchedule("test-schedule", "my-workflow");
        dao().updateSchedule(schedule);

        WorkflowScheduleModel found = dao().findScheduleByName("test-schedule");

        assertNotNull(found);
        assertEquals("test-schedule", found.getName());
        assertEquals("my-workflow", found.getStartWorkflowRequest().getName());
        assertEquals("0 0 9 * * MON-FRI", found.getCronExpression());
        assertEquals("UTC", found.getZoneId());
    }

    @Test
    public void testFindScheduleByName_notFound_returnsNull() {
        assertNull(dao().findScheduleByName("no-such-schedule"));
    }

    @Test
    public void testUpdateSchedule_upserts() {
        WorkflowScheduleModel schedule = buildSchedule("upsert-schedule", "workflow-v1");
        dao().updateSchedule(schedule);

        schedule.setCronExpression("0 0 10 * * *");
        dao().updateSchedule(schedule);

        WorkflowScheduleModel found = dao().findScheduleByName("upsert-schedule");
        assertEquals("0 0 10 * * *", found.getCronExpression());
    }

    @Test
    public void testGetAllSchedules() {
        dao().updateSchedule(buildSchedule("sched-a", "wf-a"));
        dao().updateSchedule(buildSchedule("sched-b", "wf-b"));
        dao().updateSchedule(buildSchedule("sched-c", "wf-c"));

        assertEquals(3, dao().getAllSchedules().size());
    }

    @Test
    public void testFindAllSchedulesByWorkflow() {
        dao().updateSchedule(buildSchedule("s1", "target-wf"));
        dao().updateSchedule(buildSchedule("s2", "target-wf"));
        dao().updateSchedule(buildSchedule("s3", "other-wf"));

        List<WorkflowScheduleModel> results = dao().findAllSchedules("target-wf");
        assertEquals(2, results.size());
        assertTrue(
                results.stream()
                        .allMatch(s -> "target-wf".equals(s.getStartWorkflowRequest().getName())));
    }

    @Test
    public void testFindAllByNames() {
        dao().updateSchedule(buildSchedule("find-a", "wf-a"));
        dao().updateSchedule(buildSchedule("find-b", "wf-b"));
        dao().updateSchedule(buildSchedule("find-c", "wf-c"));

        Map<String, WorkflowScheduleModel> result =
                dao().findAllByNames(Set.of("find-a", "find-c", "no-such-schedule"));
        assertEquals(2, result.size());
        assertTrue(result.containsKey("find-a"));
        assertTrue(result.containsKey("find-c"));
        assertFalse(result.containsKey("find-b"));
        assertFalse(result.containsKey("no-such-schedule"));
    }

    @Test
    public void testFindAllByNames_emptySet_returnsEmpty() {
        Map<String, WorkflowScheduleModel> result = dao().findAllByNames(Set.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFindAllByNames_nullSet_returnsEmpty() {
        Map<String, WorkflowScheduleModel> result = dao().findAllByNames(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testDeleteSchedule_removesScheduleAndExecutions() {
        dao().updateSchedule(buildSchedule("to-delete", "some-wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("to-delete");
        dao().saveExecutionRecord(exec);

        dao().deleteWorkflowSchedule("to-delete");

        assertNull(dao().findScheduleByName("to-delete"));
        assertNull(dao().readExecutionRecord(exec.getExecutionId()));
    }

    @Test
    public void testDeleteSchedule_cascadesMultipleExecutions() {
        dao().updateSchedule(buildSchedule("cascade-delete", "some-wf"));
        for (int i = 0; i < 5; i++) {
            dao().saveExecutionRecord(buildExecution("cascade-delete"));
        }

        dao().deleteWorkflowSchedule("cascade-delete");

        assertNull(dao().findScheduleByName("cascade-delete"));
        assertTrue(dao().getPendingExecutionRecordIds().isEmpty());
    }

    @Test
    public void testDeleteSchedule_nonExistent_doesNotThrow() {
        dao().deleteWorkflowSchedule("does-not-exist");
    }

    // =========================================================================
    // 2. JSON round-trip fidelity
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
        dao().updateSchedule(schedule);

        WorkflowScheduleModel found = dao().findScheduleByName("round-trip-schedule");

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
        dao().updateSchedule(buildSchedule("exec-rt-schedule", "exec-rt-wf"));

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
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecutionModel found = dao().readExecutionRecord(exec.getExecutionId());

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
    // 3. Execution tracking
    // =========================================================================

    @Test
    public void testSaveAndReadExecutionRecord() {
        dao().updateSchedule(buildSchedule("exec-test", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("exec-test");
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecutionModel found = dao().readExecutionRecord(exec.getExecutionId());
        assertNotNull(found);
        assertEquals(exec.getExecutionId(), found.getExecutionId());
        assertEquals("exec-test", found.getScheduleName());
        assertEquals(WorkflowScheduleExecutionModel.State.POLLED, found.getState());
    }

    @Test
    public void testSaveExecutionRecord_idempotent() {
        dao().updateSchedule(buildSchedule("idem-test", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("idem-test");
        dao().saveExecutionRecord(exec);
        dao().saveExecutionRecord(exec);

        List<String> pending = dao().getPendingExecutionRecordIds();
        assertEquals(
                "Saving the same execution record twice must not produce duplicate rows",
                1,
                pending.size());
    }

    @Test
    public void testUpdateExecutionRecord_transitionToExecuted() {
        dao().updateSchedule(buildSchedule("state-test", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("state-test");
        dao().saveExecutionRecord(exec);

        exec.setState(WorkflowScheduleExecutionModel.State.EXECUTED);
        exec.setWorkflowId("conductor-wf-123");
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecutionModel found = dao().readExecutionRecord(exec.getExecutionId());
        assertEquals(WorkflowScheduleExecutionModel.State.EXECUTED, found.getState());
        assertEquals("conductor-wf-123", found.getWorkflowId());
    }

    @Test
    public void testUpdateExecutionRecord_transitionToFailed() {
        dao().updateSchedule(buildSchedule("fail-test", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("fail-test");
        dao().saveExecutionRecord(exec);

        exec.setState(WorkflowScheduleExecutionModel.State.FAILED);
        exec.setReason("No such workflow defined. name=missing-wf, version=1");
        exec.setStackTrace(
                "com.netflix.conductor.core.exception.NotFoundException: No such workflow\n\tat ...");
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecutionModel found = dao().readExecutionRecord(exec.getExecutionId());
        assertEquals(WorkflowScheduleExecutionModel.State.FAILED, found.getState());
        assertNotNull(found.getReason());
        assertTrue(found.getReason().contains("missing-wf"));
        assertNotNull(found.getStackTrace());
    }

    @Test
    public void testRemoveExecutionRecord() {
        dao().updateSchedule(buildSchedule("remove-exec", "wf"));
        WorkflowScheduleExecutionModel exec = buildExecution("remove-exec");
        dao().saveExecutionRecord(exec);

        dao().removeExecutionRecord(exec.getExecutionId());

        assertNull(dao().readExecutionRecord(exec.getExecutionId()));
    }

    @Test
    public void testGetPendingExecutionRecordIds() {
        dao().updateSchedule(buildSchedule("pending-test", "wf"));

        WorkflowScheduleExecutionModel polled1 = buildExecution("pending-test");
        WorkflowScheduleExecutionModel polled2 = buildExecution("pending-test");
        WorkflowScheduleExecutionModel executed = buildExecution("pending-test");
        executed.setState(WorkflowScheduleExecutionModel.State.EXECUTED);

        dao().saveExecutionRecord(polled1);
        dao().saveExecutionRecord(polled2);
        dao().saveExecutionRecord(executed);

        List<String> pendingIds = dao().getPendingExecutionRecordIds();
        assertEquals(2, pendingIds.size());
        assertTrue(pendingIds.contains(polled1.getExecutionId()));
        assertTrue(pendingIds.contains(polled2.getExecutionId()));
    }

    @Test
    public void testGetPendingExecutionRecordIds_afterTransition() {
        dao().updateSchedule(buildSchedule("transition-test", "wf"));

        WorkflowScheduleExecutionModel exec = buildExecution("transition-test");
        dao().saveExecutionRecord(exec);
        assertTrue(dao().getPendingExecutionRecordIds().contains(exec.getExecutionId()));

        exec.setState(WorkflowScheduleExecutionModel.State.EXECUTED);
        dao().saveExecutionRecord(exec);

        assertFalse(
                "EXECUTED record must not appear in pending list",
                dao().getPendingExecutionRecordIds().contains(exec.getExecutionId()));
    }

    // =========================================================================
    // 4. Next-run time management
    // =========================================================================

    @Test
    public void testSetAndGetNextRunTime() {
        dao().updateSchedule(buildSchedule("next-run-test", "wf"));

        long epochMillis = System.currentTimeMillis() + 60_000;
        dao().setNextRunTimeInEpoch("next-run-test", epochMillis);

        assertEquals(epochMillis, dao().getNextRunTimeInEpoch("next-run-test"));
    }

    @Test
    public void testGetNextRunTime_notSet_returnsMinusOne() {
        dao().updateSchedule(buildSchedule("no-next-run", "wf"));
        assertEquals(-1L, dao().getNextRunTimeInEpoch("no-next-run"));
    }

    @Test
    public void testUpdateSchedule_resetsNextRunTime() {
        WorkflowScheduleModel schedule = buildSchedule("nrt-reset-test", "wf");
        dao().updateSchedule(schedule);

        long epoch = System.currentTimeMillis() + 60_000;
        dao().setNextRunTimeInEpoch("nrt-reset-test", epoch);
        assertEquals(epoch, dao().getNextRunTimeInEpoch("nrt-reset-test"));

        schedule.setCronExpression("0 0 10 * * *");
        schedule.setNextRunTime(null);
        dao().updateSchedule(schedule);

        assertEquals(
                "updateSchedule with null nextRunTime must reset the cached column",
                -1L,
                dao().getNextRunTimeInEpoch("nrt-reset-test"));
    }

    // =========================================================================
    // 5. Volume
    // =========================================================================

    @Test
    public void testVolume_getAllSchedules_largeCount() {
        int count = 100;
        for (int i = 0; i < count; i++) {
            dao().updateSchedule(buildSchedule("volume-sched-" + i, "wf-" + (i % 10)));
        }

        List<WorkflowScheduleModel> all = dao().getAllSchedules();
        assertEquals(count, all.size());
    }

    // =========================================================================
    // 6. Concurrency
    // =========================================================================

    @Test
    public void testConcurrentUpserts_sameSchedule() throws Exception {
        dao().updateSchedule(buildSchedule("concurrent-sched", "wf-initial"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            dao().updateSchedule(buildSchedule("concurrent-sched", "wf-" + idx));
                        } catch (Exception e) {
                            errors.add(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        assertTrue(
                "Concurrent upserts did not complete within 30 s",
                doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue("Unexpected errors during concurrent upserts: " + errors, errors.isEmpty());

        List<WorkflowScheduleModel> all = dao().getAllSchedules();
        assertEquals(
                "Concurrent upserts on the same name must yield exactly one row", 1, all.size());
        assertEquals("concurrent-sched", all.get(0).getName());
    }

    // =========================================================================
    // 7. Case sensitivity
    // =========================================================================

    @Test
    public void testFindAllSchedules_caseSensitive() {
        dao().updateSchedule(buildSchedule("case-sched", "MyWorkflow"));

        assertEquals(1, dao().findAllSchedules("MyWorkflow").size());
        assertEquals(
                "Workflow name lookup must be case-sensitive",
                0,
                dao().findAllSchedules("myworkflow").size());
        assertEquals(0, dao().findAllSchedules("MYWORKFLOW").size());
    }

    // =========================================================================
    // 8. Large findAllByNames + error conditions
    // =========================================================================

    @Test
    public void testFindAllByNames_largeSet() {
        java.util.Set<String> allNames = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) {
            String name = "large-set-" + i;
            dao().updateSchedule(buildSchedule(name, "wf"));
            allNames.add(name);
        }
        java.util.Set<String> queryNames = new java.util.HashSet<>(allNames);
        for (int i = 50; i < 100; i++) {
            queryNames.add("large-set-" + i);
        }
        Map<String, WorkflowScheduleModel> result = dao().findAllByNames(queryNames);
        assertEquals(50, result.size());
        for (String name : allNames) {
            assertTrue(result.containsKey(name));
        }
    }

    @Test
    public void testGetNextRunTime_nonExistentSchedule_returnsMinusOne() {
        assertEquals(-1L, dao().getNextRunTimeInEpoch("non-existent-schedule"));
    }

    @Test
    public void testSetNextRunTime_arbitraryKey_persists() {
        // setNextRunTimeInEpoch must store any key, not only registered schedule names.
        // Multi-cron schedules use JSON payload keys; rejecting them was the root cause of
        // the misfire bug (schedules firing on every poll cycle instead of on their cron time).
        long epoch = System.currentTimeMillis() + 60_000;
        dao().setNextRunTimeInEpoch("non-existent-schedule", epoch);
        assertEquals(epoch, dao().getNextRunTimeInEpoch("non-existent-schedule"));
    }

    /**
     * BUG REGRESSION — multi-cron next-run-time must survive a DAO round-trip.
     *
     * <p>When a schedule carries multiple cron expressions, {@code SchedulerService} keys
     * next-run-time entries by a JSON payload string (e.g. {@code {"name":"s","cron":"0 0 8 * * ?
     * UTC","id":0}}) rather than by the schedule name. The DAO must store and retrieve that value
     * transparently.
     *
     * <p>SQL backends ({@code UPDATE scheduler SET next_run_time = ? WHERE scheduler_name = ?})
     * silently update 0 rows for a JSON payload key, so {@code getNextRunTimeInEpoch} returns
     * {@code -1}. {@code SchedulerService} then maps {@code -1} to epoch 1970 and deduces that the
     * schedule is perpetually overdue, causing every multi-cron message to fire on every poll cycle
     * instead of waiting for its cron time.
     *
     * <p>This test will FAIL against the current SQL DAO implementations, confirming the bug. It
     * should pass once the DAO is fixed to support arbitrary payload keys.
     */
    @Test
    public void testSetAndGetNextRunTime_withMultiCronPayloadKey() {
        // This is the exact key format SchedulerService uses for multi-cron schedule entries:
        // buildMultiCronPayload(scheduleName, cronExpr + " " + zoneId, index)
        String multiCronPayloadKey =
                "{\"name\":\"multi-cron-sched\",\"cron\":\"0 0 8 * * ? UTC\",\"id\":0}";
        long futureEpoch = System.currentTimeMillis() + 3_600_000L; // 1 hour from now

        dao().setNextRunTimeInEpoch(multiCronPayloadKey, futureEpoch);

        long stored = dao().getNextRunTimeInEpoch(multiCronPayloadKey);
        assertEquals(
                "setNextRunTimeInEpoch must persist multi-cron JSON payload keys. "
                        + "SQL DAOs currently use UPDATE WHERE scheduler_name=? which silently "
                        + "skips rows that don't exist, dropping the write. "
                        + "As a result getNextRunTimeInEpoch returns -1, SchedulerService maps "
                        + "that to epoch 1970 and treats the schedule as perpetually overdue, "
                        + "causing multi-cron schedules to fire on every poll cycle.",
                futureEpoch,
                stored);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    protected WorkflowScheduleModel buildSchedule(String name, String workflowName) {
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

    protected WorkflowScheduleExecutionModel buildExecution(String scheduleName) {
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
