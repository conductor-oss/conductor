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
package org.conductoross.conductor.scheduler.dao;

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

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import static org.junit.Assert.*;

/**
 * Shared contract test suite for all {@link SchedulerDAO} implementations.
 *
 * <p>Every persistence module (Postgres, MySQL, SQLite) subclasses this and provides only the
 * Spring wiring ({@link #dao()} and {@link #dataSource()}). Adding a test here automatically covers
 * all three backends.
 *
 * <p><b>Test categories:</b>
 *
 * <ol>
 *   <li>Schedule CRUD — create/read/update/delete and bulk-lookup
 *   <li>JSON round-trip fidelity — all model fields survive the blob serialization/deserialization,
 *       including Orkes extension fields
 *   <li>Execution tracking — POLLED→EXECUTED/FAILED lifecycle, pending-ID filter, ordering
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
        WorkflowSchedule schedule = buildSchedule("test-schedule", "my-workflow");
        dao().updateSchedule(schedule);

        WorkflowSchedule found = dao().findScheduleByName("test-schedule");

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
        WorkflowSchedule schedule = buildSchedule("upsert-schedule", "workflow-v1");
        dao().updateSchedule(schedule);

        schedule.setCronExpression("0 0 10 * * *");
        dao().updateSchedule(schedule);

        WorkflowSchedule found = dao().findScheduleByName("upsert-schedule");
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

        List<WorkflowSchedule> results = dao().findAllSchedules("target-wf");
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

        Map<String, WorkflowSchedule> result =
                dao().findAllByNames(Set.of("find-a", "find-c", "no-such-schedule"));
        assertEquals(2, result.size());
        assertTrue(result.containsKey("find-a"));
        assertTrue(result.containsKey("find-c"));
        assertFalse(result.containsKey("find-b"));
        assertFalse(result.containsKey("no-such-schedule"));
    }

    @Test
    public void testFindAllByNames_emptySet_returnsEmpty() {
        Map<String, WorkflowSchedule> result = dao().findAllByNames(Set.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFindAllByNames_nullSet_returnsEmpty() {
        Map<String, WorkflowSchedule> result = dao().findAllByNames(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testDeleteSchedule_removesScheduleAndExecutions() {
        dao().updateSchedule(buildSchedule("to-delete", "some-wf"));
        WorkflowScheduleExecution exec = buildExecution("to-delete");
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
        // Should silently succeed — nothing to delete
        dao().deleteWorkflowSchedule("does-not-exist");
    }

    // =========================================================================
    // 2. JSON round-trip fidelity
    // =========================================================================

    /**
     * Verifies that every field on {@link WorkflowSchedule} survives the JSON blob round-trip. The
     * existing basic tests only check name/cron/zone; this covers the remaining fields that could
     * silently drop if the model or serialization changes.
     */
    @Test
    public void testScheduleJsonRoundTrip_allFields() {
        WorkflowSchedule schedule = buildSchedule("round-trip-schedule", "round-trip-wf");
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

        WorkflowSchedule found = dao().findScheduleByName("round-trip-schedule");

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

    /**
     * Verifies that Orkes-specific extension fields (stored via {@code @JsonAnySetter}) survive the
     * round-trip. This is the compatibility mechanism that allows Orkes Conductor to add fields
     * (e.g. orgId, tags) without breaking the OSS model.
     */
    @Test
    public void testScheduleExtensionFields_roundTrip() {
        WorkflowSchedule schedule = buildSchedule("ext-field-schedule", "wf");
        schedule.setExtensionField("orgId", "org-123");
        schedule.setExtensionField("customTag", "critical");
        dao().updateSchedule(schedule);

        WorkflowSchedule found = dao().findScheduleByName("ext-field-schedule");

        assertNotNull(found.getExtensionFields());
        assertEquals("org-123", found.getExtensionFields().get("orgId"));
        assertEquals("critical", found.getExtensionFields().get("customTag"));
    }

    /**
     * Verifies that every field on {@link WorkflowScheduleExecution} survives the JSON blob
     * round-trip, including the fields added for Orkes compatibility (workflowName, stackTrace,
     * startWorkflowRequest).
     */
    @Test
    public void testExecutionJsonRoundTrip_allFields() {
        dao().updateSchedule(buildSchedule("exec-rt-schedule", "exec-rt-wf"));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName("exec-rt-wf");
        req.setVersion(2);

        WorkflowScheduleExecution exec = buildExecution("exec-rt-schedule");
        exec.setWorkflowId("wf-instance-456");
        exec.setWorkflowName("exec-rt-wf");
        exec.setReason("Something went wrong");
        exec.setStackTrace(
                "java.lang.RuntimeException: Something went wrong\n\tat Foo.bar(Foo.java:42)");
        exec.setState(WorkflowScheduleExecution.ExecutionState.FAILED);
        exec.setStartWorkflowRequest(req);
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecution found = dao().readExecutionRecord(exec.getExecutionId());

        assertNotNull(found);
        assertEquals("wf-instance-456", found.getWorkflowId());
        assertEquals("exec-rt-wf", found.getWorkflowName());
        assertEquals("Something went wrong", found.getReason());
        assertNotNull(found.getStackTrace());
        assertTrue(found.getStackTrace().contains("RuntimeException"));
        assertEquals(WorkflowScheduleExecution.ExecutionState.FAILED, found.getState());
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
        WorkflowScheduleExecution exec = buildExecution("exec-test");
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecution found = dao().readExecutionRecord(exec.getExecutionId());
        assertNotNull(found);
        assertEquals(exec.getExecutionId(), found.getExecutionId());
        assertEquals("exec-test", found.getScheduleName());
        assertEquals(WorkflowScheduleExecution.ExecutionState.POLLED, found.getState());
    }

    @Test
    public void testSaveExecutionRecord_idempotent() {
        dao().updateSchedule(buildSchedule("idem-test", "wf"));
        WorkflowScheduleExecution exec = buildExecution("idem-test");
        dao().saveExecutionRecord(exec);
        dao().saveExecutionRecord(exec); // second save — must not create a duplicate row

        List<String> pending = dao().getPendingExecutionRecordIds();
        assertEquals(
                "Saving the same execution record twice must not produce duplicate rows",
                1,
                pending.size());
    }

    @Test
    public void testUpdateExecutionRecord_transitionToExecuted() {
        dao().updateSchedule(buildSchedule("state-test", "wf"));
        WorkflowScheduleExecution exec = buildExecution("state-test");
        dao().saveExecutionRecord(exec);

        exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
        exec.setWorkflowId("conductor-wf-123");
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecution found = dao().readExecutionRecord(exec.getExecutionId());
        assertEquals(WorkflowScheduleExecution.ExecutionState.EXECUTED, found.getState());
        assertEquals("conductor-wf-123", found.getWorkflowId());
    }

    @Test
    public void testUpdateExecutionRecord_transitionToFailed() {
        dao().updateSchedule(buildSchedule("fail-test", "wf"));
        WorkflowScheduleExecution exec = buildExecution("fail-test");
        dao().saveExecutionRecord(exec);

        exec.setState(WorkflowScheduleExecution.ExecutionState.FAILED);
        exec.setReason("No such workflow defined. name=missing-wf, version=1");
        exec.setStackTrace(
                "com.netflix.conductor.core.exception.NotFoundException: No such workflow\n\tat ...");
        dao().saveExecutionRecord(exec);

        WorkflowScheduleExecution found = dao().readExecutionRecord(exec.getExecutionId());
        assertEquals(WorkflowScheduleExecution.ExecutionState.FAILED, found.getState());
        assertNotNull(found.getReason());
        assertTrue(found.getReason().contains("missing-wf"));
        assertNotNull(found.getStackTrace());
    }

    @Test
    public void testRemoveExecutionRecord() {
        dao().updateSchedule(buildSchedule("remove-exec", "wf"));
        WorkflowScheduleExecution exec = buildExecution("remove-exec");
        dao().saveExecutionRecord(exec);

        dao().removeExecutionRecord(exec.getExecutionId());

        assertNull(dao().readExecutionRecord(exec.getExecutionId()));
    }

    @Test
    public void testGetPendingExecutionRecordIds() {
        dao().updateSchedule(buildSchedule("pending-test", "wf"));

        WorkflowScheduleExecution polled1 = buildExecution("pending-test");
        WorkflowScheduleExecution polled2 = buildExecution("pending-test");
        WorkflowScheduleExecution executed = buildExecution("pending-test");
        executed.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);

        dao().saveExecutionRecord(polled1);
        dao().saveExecutionRecord(polled2);
        dao().saveExecutionRecord(executed);

        List<String> pendingIds = dao().getPendingExecutionRecordIds();
        assertEquals(2, pendingIds.size());
        assertTrue(pendingIds.contains(polled1.getExecutionId()));
        assertTrue(pendingIds.contains(polled2.getExecutionId()));
    }

    /**
     * After a POLLED record transitions to EXECUTED, it must no longer appear in {@code
     * getPendingExecutionRecordIds}. This verifies the state-column write-through is correct.
     */
    @Test
    public void testGetPendingExecutionRecordIds_afterTransition() {
        dao().updateSchedule(buildSchedule("transition-test", "wf"));

        WorkflowScheduleExecution exec = buildExecution("transition-test");
        dao().saveExecutionRecord(exec);
        assertTrue(dao().getPendingExecutionRecordIds().contains(exec.getExecutionId()));

        exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
        dao().saveExecutionRecord(exec);

        assertFalse(
                "EXECUTED record must not appear in pending list",
                dao().getPendingExecutionRecordIds().contains(exec.getExecutionId()));
    }

    @Test
    public void testGetExecutionRecords_orderedByTimeDesc() {
        dao().updateSchedule(buildSchedule("history-test", "wf"));

        for (int i = 0; i < 5; i++) {
            WorkflowScheduleExecution exec = buildExecution("history-test");
            exec.setExecutionTime((long) (1000 + i));
            exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
            dao().saveExecutionRecord(exec);
        }

        List<WorkflowScheduleExecution> records = dao().getExecutionRecords("history-test", 3);
        assertEquals(3, records.size());
        assertTrue(records.get(0).getExecutionTime() >= records.get(1).getExecutionTime());
        assertTrue(records.get(1).getExecutionTime() >= records.get(2).getExecutionTime());
    }

    /**
     * Records inserted in reverse time order must still be returned newest-first. This verifies the
     * ORDER BY in the query, not just insertion order.
     */
    @Test
    public void testGetExecutionRecords_reverseInsertionOrder() {
        dao().updateSchedule(buildSchedule("reverse-order-test", "wf"));

        // Insert in DESCENDING time order (oldest last)
        for (int i = 4; i >= 0; i--) {
            WorkflowScheduleExecution exec = buildExecution("reverse-order-test");
            exec.setExecutionTime((long) (1000 + i));
            exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
            dao().saveExecutionRecord(exec);
        }

        List<WorkflowScheduleExecution> records =
                dao().getExecutionRecords("reverse-order-test", 10);
        assertEquals(5, records.size());
        for (int i = 0; i < records.size() - 1; i++) {
            assertTrue(
                    "Records must be ordered newest-first",
                    records.get(i).getExecutionTime() >= records.get(i + 1).getExecutionTime());
        }
        assertEquals(1004L, records.get(0).getExecutionTime().longValue());
        assertEquals(1000L, records.get(records.size() - 1).getExecutionTime().longValue());
    }

    @Test
    public void testGetExecutionRecords_limitOne() {
        dao().updateSchedule(buildSchedule("limit-one-test", "wf"));

        for (int i = 0; i < 3; i++) {
            WorkflowScheduleExecution exec = buildExecution("limit-one-test");
            exec.setExecutionTime((long) (1000 + i));
            exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
            dao().saveExecutionRecord(exec);
        }

        List<WorkflowScheduleExecution> records = dao().getExecutionRecords("limit-one-test", 1);
        assertEquals(1, records.size());
        assertEquals(1002L, records.get(0).getExecutionTime().longValue());
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

    /**
     * {@code updateSchedule} writes {@code schedule.getNextRunTime()} into the {@code
     * next_run_time} column. When a schedule is edited (e.g. cron change) and re-saved without
     * carrying the cached next run time, that column is reset. This test documents the behavior so
     * callers know to preserve the value if needed.
     */
    @Test
    public void testUpdateSchedule_resetsNextRunTime() {
        WorkflowSchedule schedule = buildSchedule("nrt-reset-test", "wf");
        dao().updateSchedule(schedule);

        long epoch = System.currentTimeMillis() + 60_000;
        dao().setNextRunTimeInEpoch("nrt-reset-test", epoch);
        assertEquals(epoch, dao().getNextRunTimeInEpoch("nrt-reset-test"));

        // Re-save the schedule without setting nextRunTime → column should be reset
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

    /**
     * Inserts 100 schedules and verifies {@code getAllSchedules} returns all of them. Guards
     * against implicit row limits, memory issues, or query pagination bugs.
     */
    @Test
    public void testVolume_getAllSchedules_largeCount() {
        int count = 100;
        for (int i = 0; i < count; i++) {
            dao().updateSchedule(buildSchedule("volume-sched-" + i, "wf-" + (i % 10)));
        }

        List<WorkflowSchedule> all = dao().getAllSchedules();
        assertEquals(count, all.size());
    }

    // =========================================================================
    // 6. Concurrency
    // =========================================================================

    /**
     * Ten threads simultaneously upsert the same schedule name. Regardless of which thread wins,
     * exactly one row must exist afterwards. Exercises {@code ON CONFLICT ... DO UPDATE} (Postgres,
     * SQLite) and {@code ON DUPLICATE KEY UPDATE} (MySQL) semantics under concurrent load.
     *
     * <p>SQLite: the connection pool is bounded to 1, so writes serialize through HikariCP — the
     * test still validates correctness. Postgres and MySQL exercise true concurrent upsert paths.
     */
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

        List<WorkflowSchedule> all = dao().getAllSchedules();
        assertEquals(
                "Concurrent upserts on the same name must yield exactly one row", 1, all.size());
        assertEquals("concurrent-sched", all.get(0).getName());
    }

    // =========================================================================
    // 7. Case sensitivity
    // =========================================================================

    /**
     * Verifies that {@code findAllSchedules(workflowName)} is case-sensitive. The exact workflow
     * name string must match; mixed-case variants must return no results. This is consistent with
     * Postgres and SQLite behavior. MySQL requires {@code COLLATE utf8mb4_bin} on the {@code
     * workflow_name} column (see the MySQL migration script).
     */
    @Test
    public void testFindAllSchedules_caseSensitive() {
        dao().updateSchedule(buildSchedule("case-sched", "MyWorkflow"));

        assertEquals(1, dao().findAllSchedules("MyWorkflow").size());
        assertEquals(
                "Workflow name lookup must be case-sensitive — use exact case",
                0,
                dao().findAllSchedules("myworkflow").size());
        assertEquals(0, dao().findAllSchedules("MYWORKFLOW").size());
    }

    // =========================================================================
    // 8. Large findAllByNames + error conditions
    // =========================================================================

    /**
     * Verifies that {@code findAllByNames} handles a large input set (50 existing + 50 non-existent
     * names) correctly and returns only the rows that exist.
     */
    @Test
    public void testFindAllByNames_largeSet() {
        java.util.Set<String> allNames = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) {
            String name = "large-set-" + i;
            dao().updateSchedule(buildSchedule(name, "wf"));
            allNames.add(name);
        }
        // Mix in 50 non-existent names
        java.util.Set<String> queryNames = new java.util.HashSet<>(allNames);
        for (int i = 50; i < 100; i++) {
            queryNames.add("large-set-" + i);
        }
        java.util.Map<String, WorkflowSchedule> result = dao().findAllByNames(queryNames);
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
    public void testSetNextRunTime_nonExistentSchedule_doesNotThrow() {
        // UPDATE on non-existent row should silently no-op — no row should be created
        dao().setNextRunTimeInEpoch("non-existent-schedule", System.currentTimeMillis());
        // Verify no row was created
        assertEquals(-1L, dao().getNextRunTimeInEpoch("non-existent-schedule"));
    }

    @Test
    public void testGetExecutionRecords_nonExistentSchedule_returnsEmpty() {
        List<WorkflowScheduleExecution> records =
                dao().getExecutionRecords("non-existent-schedule", 10);
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    protected WorkflowSchedule buildSchedule(String name, String workflowName) {
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

    protected WorkflowScheduleExecution buildExecution(String scheduleName) {
        WorkflowScheduleExecution exec = new WorkflowScheduleExecution();
        exec.setExecutionId(UUID.randomUUID().toString());
        exec.setScheduleName(scheduleName);
        exec.setScheduledTime(System.currentTimeMillis());
        exec.setExecutionTime(System.currentTimeMillis());
        exec.setState(WorkflowScheduleExecution.ExecutionState.POLLED);
        exec.setZoneId("UTC");
        return exec;
    }
}
