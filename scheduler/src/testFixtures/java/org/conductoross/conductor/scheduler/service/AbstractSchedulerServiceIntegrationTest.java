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
package org.conductoross.conductor.scheduler.service;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.config.SchedulerProperties;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution.ExecutionState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.service.WorkflowService;

import static org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution.ExecutionState.EXECUTED;
import static org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution.ExecutionState.FAILED;
import static org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution.ExecutionState.POLLED;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link SchedulerService} using a real {@link SchedulerDAO} (from a concrete
 * subclass) and a mocked {@link WorkflowService}.
 *
 * <p>Subclasses provide the wired {@link SchedulerDAO} and {@link DataSource} via Spring. This
 * abstract class handles DB cleanup between tests and constructs a real {@link SchedulerService}
 * instance (with scheduler disabled so no background thread starts).
 *
 * <p>Every concrete subclass (Postgres, MySQL, SQLite) automatically inherits all tests here.
 */
public abstract class AbstractSchedulerServiceIntegrationTest {

    /** Returns the DAO under test. Subclasses provide this via {@code @Autowired}. */
    protected abstract SchedulerDAO dao();

    /**
     * Returns the datasource wired into the Spring test context. Used to truncate tables between
     * tests.
     */
    protected abstract DataSource dataSource();

    protected WorkflowService workflowService;
    protected SchedulerService service;

    @Before
    public final void setUpService() throws Exception {
        truncateStore();
        workflowService = mock(WorkflowService.class);
        SchedulerProperties props = new SchedulerProperties();
        props.setEnabled(false);
        props.setArchivalMaxRecords(3);
        props.setArchivalMaxRecordThreshold(5);
        props.setPollBatchSize(10);
        service = new SchedulerService(dao(), workflowService, props);
    }

    protected void truncateStore() throws Exception {
        try (Connection conn = dataSource().getConnection()) {
            conn.prepareStatement("DELETE FROM scheduler_execution").executeUpdate();
            conn.prepareStatement("DELETE FROM scheduler").executeUpdate();
        }
    }

    // =========================================================================
    // a. Pruning integration
    // =========================================================================

    /**
     * Verifies that {@code pruneExecutionHistory} is invoked correctly after a poll fires.
     *
     * <p>Setup: 6 pre-existing EXECUTED records (executionTimes 1000..1005). After the poll fires,
     * there are 7 total. {@code pruneExecutionHistory} fetches {@code threshold+1=6} newest
     * records, sees 6 > threshold(5), removes the 3 oldest (indices 3,4,5). After pruning 4 records
     * remain.
     */
    @Test
    public void testPruneExecution_withRealDAO_removesOldestRecords() throws Exception {
        String scheduleName = "prune-test-" + UUID.randomUUID();
        WorkflowSchedule schedule = buildSchedule(scheduleName, "some-wf");
        dao().updateSchedule(schedule);

        // Insert 6 pre-existing EXECUTED records with distinct executionTimes
        for (int i = 0; i < 6; i++) {
            WorkflowScheduleExecution exec = buildExecution(scheduleName);
            exec.setExecutionTime(1000L + i);
            exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
            dao().saveExecutionRecord(exec);
        }

        // Set nextRunTime to the past so the schedule is due
        long pastTime = System.currentTimeMillis() - 2000;
        dao().setNextRunTimeInEpoch(scheduleName, pastTime);

        when(workflowService.startWorkflow(any())).thenReturn("wf-id");

        service.pollAndExecuteSchedules();

        // Pruning mechanics:
        //   Before poll: 6 records with executionTimes [1000..1005]
        //   After poll fires: 7 records — adds dispatched record with executionTime ~now (>> 1005)
        //   pruneExecutionHistory fetches getExecutionRecords(name, threshold+1=6):
        //     returns the 6 NEWEST: [dispatched, 1005, 1004, 1003, 1002, 1001]
        //   Since 6 > threshold(5): prune subList(keep=3, 6) → removes [1003, 1002, 1001]
        //   Record 1000 was NOT in the 6-item batch, so it is NOT pruned.
        //   Final DB state: dispatched + 1005 + 1004 + 1000 = 4 records
        List<WorkflowScheduleExecution> remaining = dao().getExecutionRecords(scheduleName, 100);
        assertEquals(
                "After pruning, 4 records should remain: dispatched + 1005 + 1004 + 1000",
                4,
                remaining.size());

        // Verify that the 3 pruned records (1003, 1002, 1001) are NOT present
        java.util.Set<Long> remainingTimes = new java.util.HashSet<>();
        for (WorkflowScheduleExecution rec : remaining) {
            remainingTimes.add(rec.getExecutionTime());
        }
        assertFalse("executionTime=1001 should be pruned", remainingTimes.contains(1001L));
        assertFalse("executionTime=1002 should be pruned", remainingTimes.contains(1002L));
        assertFalse("executionTime=1003 should be pruned", remainingTimes.contains(1003L));
    }

    // =========================================================================
    // b. Stale POLLED record handling
    // =========================================================================

    /**
     * Verifies that a POLLED execution record stuck longer than the stale threshold (5 minutes) is
     * transitioned to FAILED state by the cleanup pass at the end of {@code
     * pollAndExecuteSchedules}.
     */
    @Test
    public void testStalePollRecord_withRealDAO_transitionsToFailed() throws Exception {
        String scheduleName = "stale-test-" + UUID.randomUUID();
        // We only need the schedule row to exist for FK constraints (if any)
        // but no active schedules that would fire.
        dao().updateSchedule(buildPausedSchedule(scheduleName, "stale-wf"));

        // Create a POLLED record with executionTime 10 minutes in the past
        WorkflowScheduleExecution staleRecord = buildExecution(scheduleName);
        staleRecord.setExecutionTime(System.currentTimeMillis() - 10 * 60 * 1000L);
        staleRecord.setState(WorkflowScheduleExecution.ExecutionState.POLLED);
        dao().saveExecutionRecord(staleRecord);
        String staleId = staleRecord.getExecutionId();

        service.pollAndExecuteSchedules();

        WorkflowScheduleExecution found = dao().readExecutionRecord(staleId);
        assertNotNull("Stale record must still exist after cleanup", found);
        assertEquals(
                "Stale POLLED record must be transitioned to FAILED",
                WorkflowScheduleExecution.ExecutionState.FAILED,
                found.getState());
        assertNotNull("Reason must be set on stale FAILED record", found.getReason());
        assertFalse("Reason must not be blank", found.getReason().isBlank());
    }

    // =========================================================================
    // c. Next-run pointer advancement
    // =========================================================================

    /**
     * Verifies that after a successful poll cycle, the next-run pointer is advanced to a future
     * time (not stuck in the past).
     */
    @Test
    public void testPollCycle_advancesNextRunPointer() throws Exception {
        String scheduleName = "pointer-advance-" + UUID.randomUUID();
        WorkflowSchedule schedule = buildSchedule(scheduleName, "ptr-wf");
        dao().updateSchedule(schedule);

        long pastTime = System.currentTimeMillis() - 2000;
        dao().setNextRunTimeInEpoch(scheduleName, pastTime);

        when(workflowService.startWorkflow(any())).thenReturn("wf-id");

        service.pollAndExecuteSchedules();

        long newNextRun = dao().getNextRunTimeInEpoch(scheduleName);
        assertTrue(
                "Next-run pointer must be advanced to a future time after poll",
                newNextRun > System.currentTimeMillis() - 1000);
    }

    // =========================================================================
    // d. Create-time preservation on update
    // =========================================================================

    /**
     * Verifies that {@code saveSchedule} preserves the original {@code createTime} on subsequent
     * saves (updates), while {@code updatedTime} is bumped.
     */
    @Test
    public void testSaveSchedule_createTimePreservedOnUpdate() throws Exception {
        String scheduleName = "create-time-" + UUID.randomUUID();
        WorkflowSchedule schedule = buildSchedule(scheduleName, "ct-wf");

        WorkflowSchedule saved1 = service.saveSchedule(schedule);
        long createTime1 = saved1.getCreateTime();

        // Ensure at least 1ms passes so updatedTime can be different
        Thread.sleep(5);

        WorkflowSchedule saved2 = service.saveSchedule(schedule);

        WorkflowSchedule found = dao().findScheduleByName(scheduleName);
        assertNotNull(found);
        assertEquals(
                "createTime must not change on subsequent saves",
                createTime1,
                found.getCreateTime().longValue());
        assertTrue(
                "updatedTime must be different from createTime after an update",
                found.getUpdatedTime() > createTime1);
    }

    // =========================================================================
    // e. nextRunTime stored and retrievable
    // =========================================================================

    /**
     * Verifies that after {@code saveSchedule}, the next-run time is computed and retrievable via
     * the DAO.
     */
    @Test
    public void testSaveSchedule_nextRunTimeStoredAndRetrievable() throws Exception {
        String scheduleName = "next-run-stored-" + UUID.randomUUID();
        WorkflowSchedule schedule = buildSchedule(scheduleName, "nrt-wf");

        WorkflowSchedule saved = service.saveSchedule(schedule);

        assertNotNull("saveSchedule must compute and set nextRunTime", saved.getNextRunTime());
        long stored = dao().getNextRunTimeInEpoch(scheduleName);
        assertEquals(
                "DAO-stored next-run time must match the value returned by saveSchedule",
                saved.getNextRunTime().longValue(),
                stored);
    }

    // =========================================================================
    // f. getNextExecutionTimes with endTime bound
    // =========================================================================

    /**
     * Verifies that {@code getNextExecutionTimes} respects the schedule's {@code endTime} bound:
     * even when {@code count} is large, only times before the end are returned.
     */
    @Test
    public void testGetNextExecutionTimes_withEndTimeBound() throws Exception {
        String scheduleName = "end-bound-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        WorkflowSchedule schedule = buildSchedule(scheduleName, "eb-wf");
        // Every minute cron; endTime 2 minutes from now allows at most ~2 firing slots
        schedule.setScheduleEndTime(now + 2 * 60 * 1000L);
        // Use every-second cron to ensure multiple slots fit within 2 minutes
        schedule.setCronExpression("*/10 * * * * *"); // every 10 seconds

        service.saveSchedule(schedule);

        List<Long> times = service.getNextExecutionTimes(scheduleName, 100);

        assertFalse("Should have at least one execution time", times.isEmpty());
        assertTrue(
                "Requesting 100 times with only 2 minutes of window should return far fewer than 100",
                times.size() < 100);
        long endTime = schedule.getScheduleEndTime();
        for (Long t : times) {
            assertTrue("All times must be <= endTime", t <= endTime);
        }
    }

    // =========================================================================
    // g. getNextExecutionTimes with startTime bound
    // =========================================================================

    /**
     * Verifies that {@code getNextExecutionTimes} returns 5 execution times for a schedule with a
     * future {@code startTime}. The OSS implementation starts the cursor at {@code now} — it does
     * NOT skip forward to startTime. This test documents the actual behavior: times are returned
     * starting from {@code now}, even if the schedule's startTime is in the future.
     *
     * <p>The {@code startTime} constraint is enforced by the polling loop (via {@code isDue()}
     * checking {@code now < scheduleStartTime}), not by {@code getNextExecutionTimes}. This is
     * documented behavior, not a bug.
     */
    @Test
    public void testGetNextExecutionTimes_withStartTimeBound() throws Exception {
        String scheduleName = "start-bound-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        WorkflowSchedule schedule = buildSchedule(scheduleName, "sb-wf");
        // Start 2 minutes from now — getNextExecutionTimes starts from 'now' regardless
        long startTime = now + 2 * 60 * 1000L;
        schedule.setScheduleStartTime(startTime);

        service.saveSchedule(schedule);

        List<Long> times = service.getNextExecutionTimes(scheduleName, 5);

        // The service starts the cursor at now() so we get 5 times regardless of startTime
        assertEquals(
                "getNextExecutionTimes returns count times starting from now, ignoring startTime",
                5,
                times.size());
        // All times should be in the future (cron fires every minute from now)
        for (Long t : times) {
            assertTrue("All times should be in the future", t > now);
        }
    }

    // =========================================================================
    // h. Concurrent poll — documenting double-fire risk
    // =========================================================================

    /**
     * Documents the known OSS behavior: two concurrent poll instances backed by the same DAO (as
     * would happen on a multi-node deployment without distributed locking) can both see the same
     * due schedule and both fire it.
     *
     * <p><b>This is not a bug to fix here</b> — it is a known limitation of the OSS scheduler that
     * is documented via this test. Distributed locking (e.g. Redis or Postgres advisory locks)
     * would be needed to prevent double-fires in production multi-node setups.
     *
     * <p>The assertion uses {@code atLeast(1)}: at a minimum, one instance fires the schedule.
     * Under concurrent conditions the second instance may also fire it before the first one
     * advances the next-run pointer.
     */
    @Test
    public void testConcurrentPoll_documentsDoubleFireRisk() throws Exception {
        String scheduleName = "concurrent-poll-" + UUID.randomUUID();
        WorkflowSchedule schedule = buildSchedule(scheduleName, "concurrent-wf");
        dao().updateSchedule(schedule);
        dao().setNextRunTimeInEpoch(scheduleName, System.currentTimeMillis() - 2000);

        // Both service instances share the same DAO and workflowService mock
        SchedulerProperties props = new SchedulerProperties();
        props.setEnabled(false);
        props.setArchivalMaxRecords(3);
        props.setArchivalMaxRecordThreshold(5);
        props.setPollBatchSize(10);
        SchedulerService service1 = new SchedulerService(dao(), workflowService, props);
        SchedulerService service2 = new SchedulerService(dao(), workflowService, props);

        when(workflowService.startWorkflow(any())).thenReturn("wf-concurrent");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(
                () -> {
                    try {
                        startLatch.await();
                        service1.pollAndExecuteSchedules();
                    } catch (Exception e) {
                        exceptionCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });

        executor.submit(
                () -> {
                    try {
                        startLatch.await();
                        service2.pollAndExecuteSchedules();
                    } catch (Exception e) {
                        exceptionCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });

        startLatch.countDown();
        assertTrue(
                "Concurrent poll must complete within 30 s", doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals("No exceptions expected during concurrent poll", 0, exceptionCount.get());

        // OSS has no distributed lock — concurrent poll instances MAY double-fire.
        // We assert at least 1 to confirm the schedule fired, while acknowledging
        // that 2 invocations are also possible (and expected) in a real multi-node setup.
        verify(workflowService, atLeast(1)).startWorkflow(any());
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
        // Every minute cron for predictable test behavior
        schedule.setCronExpression("0 * * * * *");
        schedule.setZoneId("UTC");
        schedule.setStartWorkflowRequest(startReq);
        schedule.setPaused(false);
        return schedule;
    }

    protected WorkflowSchedule buildPausedSchedule(String name, String workflowName) {
        WorkflowSchedule schedule = buildSchedule(name, workflowName);
        schedule.setPaused(true);
        return schedule;
    }

    // =========================================================================
    // Event field injection
    // =========================================================================

    /**
     * Verifies that the {@code event} field on the {@link StartWorkflowRequest} passed to {@link
     * WorkflowService#startWorkflow} is set to {@code "scheduler:<scheduleName>"} so that the
     * resulting {@link com.netflix.conductor.common.run.Workflow} records which schedule triggered
     * it.
     */
    @Test
    public void testDispatch_setsEventFieldToSchedulerName() throws Exception {
        String scheduleName = "event-field-test-" + UUID.randomUUID();
        WorkflowSchedule schedule = buildSchedule(scheduleName, "some-workflow");
        dao().updateSchedule(schedule);

        long pastTime = System.currentTimeMillis() - 2000;
        dao().setNextRunTimeInEpoch(scheduleName, pastTime);

        when(workflowService.startWorkflow(any())).thenReturn("wf-event-test");

        service.pollAndExecuteSchedules();

        ArgumentCaptor<StartWorkflowRequest> captor =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(workflowService).startWorkflow(captor.capture());
        assertEquals("scheduler:" + scheduleName, captor.getValue().getEvent());
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

    // =========================================================================
    // Helpers for search tests
    // =========================================================================

    /**
     * Saves an execution record with specified state and executionTime. scheduledTime mirrors
     * executionTime. workflowName and workflowId are left null unless explicitly set by the caller.
     */
    private WorkflowScheduleExecution saveExec(
            String scheduleName, ExecutionState state, Long executionTime) {
        WorkflowScheduleExecution e = buildExecution(scheduleName);
        e.setExecutionTime(executionTime);
        e.setScheduledTime(executionTime);
        e.setState(state);
        dao().saveExecutionRecord(e);
        return e;
    }

    /**
     * Saves an execution record with full control over workflowName and workflowId (used by
     * workflowType filter and freeText search tests).
     */
    private WorkflowScheduleExecution saveExecDetailed(
            String scheduleName,
            ExecutionState state,
            Long executionTime,
            String workflowName,
            String workflowId) {
        WorkflowScheduleExecution e = buildExecution(scheduleName);
        e.setExecutionTime(executionTime);
        e.setScheduledTime(executionTime);
        e.setState(state);
        if (workflowName != null) e.setWorkflowName(workflowName);
        if (workflowId != null) e.setWorkflowId(workflowId);
        dao().saveExecutionRecord(e);
        return e;
    }

    // =========================================================================
    // i. searchSchedules — filter, sort, pagination
    // =========================================================================

    @Test
    public void testSearchSchedules_empty_returnsEmptyResult() {
        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of());
        assertEquals(0, result.getTotalHits());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    public void testSearchSchedules_noFilters_returnsAll() {
        service.saveSchedule(buildSchedule("sched-a", "wf-x"));
        service.saveSchedule(buildSchedule("sched-b", "wf-y"));
        service.saveSchedule(buildSchedule("sched-c", "wf-z"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 100, List.of());
        assertEquals(3, result.getTotalHits());
        assertEquals(3, result.getResults().size());
    }

    @Test
    public void testSearchSchedules_workflowNameFilter_matchesExact() {
        service.saveSchedule(buildSchedule("sched-wf1a", "target-workflow"));
        service.saveSchedule(buildSchedule("sched-wf1b", "target-workflow"));
        service.saveSchedule(buildSchedule("sched-wf2", "other-workflow"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules("target-workflow", null, null, "*", 0, 100, List.of());

        assertEquals(2, result.getTotalHits());
        result.getResults()
                .forEach(
                        s ->
                                assertEquals(
                                        "target-workflow", s.getStartWorkflowRequest().getName()));
    }

    @Test
    public void testSearchSchedules_workflowNameFilter_noMatch_returnsEmpty() {
        service.saveSchedule(buildSchedule("sched-1", "wf-a"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules("nonexistent-workflow", null, null, "*", 0, 100, List.of());
        assertEquals(0, result.getTotalHits());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    public void testSearchSchedules_freeText_substringMatch() {
        service.saveSchedule(buildSchedule("daily-backup", "wf"));
        service.saveSchedule(buildSchedule("weekly-backup", "wf"));
        service.saveSchedule(buildSchedule("monthly-report", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "backup", 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
        result.getResults().forEach(s -> assertTrue(s.getName().contains("backup")));
    }

    @Test
    public void testSearchSchedules_freeText_caseInsensitive() {
        service.saveSchedule(buildSchedule("Daily-Cleanup", "wf"));
        service.saveSchedule(buildSchedule("nightly-cleanup", "wf"));
        service.saveSchedule(buildSchedule("unrelated-job", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "CLEANUP", 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearchSchedules_freeText_star_returnsAll() {
        service.saveSchedule(buildSchedule("sched-star-1", "wf"));
        service.saveSchedule(buildSchedule("sched-star-2", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearchSchedules_freeText_null_returnsAll() {
        service.saveSchedule(buildSchedule("sched-null-1", "wf"));
        service.saveSchedule(buildSchedule("sched-null-2", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, null, 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearchSchedules_scheduleNameFilter_substringMatch() {
        service.saveSchedule(buildSchedule("alpha-daily", "wf"));
        service.saveSchedule(buildSchedule("alpha-weekly", "wf"));
        service.saveSchedule(buildSchedule("beta-daily", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, "alpha", null, "*", 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
        result.getResults().forEach(s -> assertTrue(s.getName().startsWith("alpha")));
    }

    @Test
    public void testSearchSchedules_scheduleNameFilter_caseInsensitive() {
        service.saveSchedule(buildSchedule("Alpha-Sched", "wf"));
        service.saveSchedule(buildSchedule("beta-sched", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, "ALPHA", null, "*", 0, 100, List.of());
        assertEquals(1, result.getTotalHits());
    }

    @Test
    public void testSearchSchedules_pausedFilter_true_returnsOnlyPaused() {
        service.saveSchedule(buildSchedule("active-1", "wf"));
        service.saveSchedule(buildSchedule("active-2", "wf"));
        service.saveSchedule(buildPausedSchedule("paused-1", "wf"));
        service.saveSchedule(buildPausedSchedule("paused-2", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, true, "*", 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
        result.getResults().forEach(s -> assertTrue(s.isPaused()));
    }

    @Test
    public void testSearchSchedules_pausedFilter_false_returnsOnlyActive() {
        service.saveSchedule(buildSchedule("active-1", "wf"));
        service.saveSchedule(buildSchedule("active-2", "wf"));
        service.saveSchedule(buildPausedSchedule("paused-1", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, false, "*", 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
        result.getResults().forEach(s -> assertFalse(s.isPaused()));
    }

    @Test
    public void testSearchSchedules_pausedFilter_null_returnsAll() {
        service.saveSchedule(buildSchedule("active-1", "wf"));
        service.saveSchedule(buildPausedSchedule("paused-1", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 100, List.of());
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearchSchedules_pagination_pageSlice() {
        // Pre-set createTime so default sort (newest first) is deterministic.
        for (int i = 0; i < 5; i++) {
            WorkflowSchedule s = buildSchedule("page-sched-" + i, "wf");
            s.setCreateTime((long) (i + 1) * 1000L); // 1000, 2000, ..., 5000
            service.saveSchedule(s);
        }
        // Default sort: newest first → order: sched-4 (5000), sched-3, sched-2, sched-1, sched-0
        // start=1, size=2 → sched-3, sched-2
        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 1, 2, List.of());
        assertEquals(5, result.getTotalHits());
        assertEquals(2, result.getResults().size());
        // Verify these are the middle two results (not the first)
        assertEquals("page-sched-3", result.getResults().get(0).getName());
        assertEquals("page-sched-2", result.getResults().get(1).getName());
    }

    @Test
    public void testSearchSchedules_pagination_totalHitsBeforePagination() {
        for (int i = 0; i < 10; i++) {
            service.saveSchedule(buildSchedule("total-sched-" + i, "wf"));
        }

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 3, List.of());
        assertEquals(10, result.getTotalHits());
        assertEquals(3, result.getResults().size());
    }

    @Test
    public void testSearchSchedules_pagination_startPastEnd_returnsEmptyPage() {
        service.saveSchedule(buildSchedule("only-one", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 5, 10, List.of());
        assertEquals(1, result.getTotalHits());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    public void testSearchSchedules_sortByName_asc() {
        service.saveSchedule(buildSchedule("charlie-sched", "wf"));
        service.saveSchedule(buildSchedule("alpha-sched", "wf"));
        service.saveSchedule(buildSchedule("bravo-sched", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of("name:ASC"));
        List<WorkflowSchedule> res = result.getResults();
        assertEquals(3, res.size());
        assertEquals("alpha-sched", res.get(0).getName());
        assertEquals("bravo-sched", res.get(1).getName());
        assertEquals("charlie-sched", res.get(2).getName());
    }

    @Test
    public void testSearchSchedules_sortByName_desc() {
        service.saveSchedule(buildSchedule("charlie-sched", "wf"));
        service.saveSchedule(buildSchedule("alpha-sched", "wf"));
        service.saveSchedule(buildSchedule("bravo-sched", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of("name:DESC"));
        List<WorkflowSchedule> res = result.getResults();
        assertEquals("charlie-sched", res.get(0).getName());
        assertEquals("bravo-sched", res.get(1).getName());
        assertEquals("alpha-sched", res.get(2).getName());
    }

    @Test
    public void testSearchSchedules_defaultSort_newestCreateTimeFirst() {
        WorkflowSchedule old = buildSchedule("sched-old", "wf");
        old.setCreateTime(1000L);
        WorkflowSchedule fresh = buildSchedule("sched-new", "wf");
        fresh.setCreateTime(9000L);
        service.saveSchedule(old);
        service.saveSchedule(fresh);

        // No sort options → default = newest createTime first
        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of());
        assertEquals("sched-new", result.getResults().get(0).getName());
        assertEquals("sched-old", result.getResults().get(1).getName());
    }

    @Test
    public void testSearchSchedules_sortByCreateTime_asc() {
        WorkflowSchedule old = buildSchedule("sched-old", "wf");
        old.setCreateTime(1000L);
        WorkflowSchedule fresh = buildSchedule("sched-new", "wf");
        fresh.setCreateTime(9000L);
        service.saveSchedule(old);
        service.saveSchedule(fresh);

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of("createTime:ASC"));
        assertEquals("sched-old", result.getResults().get(0).getName());
        assertEquals("sched-new", result.getResults().get(1).getName());
    }

    @Test
    public void testSearchSchedules_sortByUpdatedTime_desc() throws Exception {
        service.saveSchedule(buildSchedule("sched-first-updated", "wf"));
        Thread.sleep(5);
        service.saveSchedule(buildSchedule("sched-last-updated", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of("updatedTime:DESC"));
        // sched-last-updated has the greater updatedTime
        assertEquals("sched-last-updated", result.getResults().get(0).getName());
    }

    @Test
    public void testSearchSchedules_sortByUpdatedTime_asc() throws Exception {
        service.saveSchedule(buildSchedule("sched-first-updated", "wf"));
        Thread.sleep(5);
        service.saveSchedule(buildSchedule("sched-last-updated", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of("updatedTime:ASC"));
        assertEquals("sched-first-updated", result.getResults().get(0).getName());
    }

    @Test
    public void testSearchSchedules_combinedFilters_freeTextAndPaused() {
        service.saveSchedule(buildSchedule("daily-active", "wf"));
        service.saveSchedule(buildPausedSchedule("daily-paused", "wf"));
        service.saveSchedule(buildSchedule("weekly-active", "wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules(null, null, true, "daily", 0, 10, List.of());
        assertEquals(1, result.getTotalHits());
        assertEquals("daily-paused", result.getResults().get(0).getName());
    }

    @Test
    public void testSearchSchedules_combinedFilters_workflowNameAndPaused() {
        service.saveSchedule(buildSchedule("s1", "target-wf"));
        service.saveSchedule(buildPausedSchedule("s2", "target-wf"));
        service.saveSchedule(buildSchedule("s3", "other-wf"));

        SearchResult<WorkflowSchedule> result =
                service.searchSchedules("target-wf", null, false, "*", 0, 10, List.of());
        assertEquals(1, result.getTotalHits());
        assertEquals("s1", result.getResults().get(0).getName());
    }

    @Test
    public void testSearchSchedules_combinedFilters_allThree() {
        service.saveSchedule(buildSchedule("match-alpha", "my-wf"));
        service.saveSchedule(buildPausedSchedule("match-beta-paused", "my-wf"));
        service.saveSchedule(buildSchedule("no-match", "other-wf"));

        // workflowName=my-wf AND freeText=match AND paused=false → only match-alpha
        SearchResult<WorkflowSchedule> result =
                service.searchSchedules("my-wf", null, false, "match", 0, 10, List.of());
        assertEquals(1, result.getTotalHits());
        assertEquals("match-alpha", result.getResults().get(0).getName());
    }

    // =========================================================================
    // j. searchExecutions — query syntax, freeText, sort, pagination
    // =========================================================================

    @Test
    public void testSearchExecutions_empty_returnsEmptyResult() {
        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 10, null, null, "*");
        assertEquals(0, result.getTotalHits());
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    public void testSearchExecutions_noFilters_returnsAll() {
        String sched = "exec-all-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, FAILED, 2000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, "*");
        assertEquals(2, result.getTotalHits());
    }

    // --- query: scheduleName IN ---

    @Test
    public void testSearchExecutions_query_scheduleNameIn_singleMatch() {
        String schedA = "exec-in-a-" + UUID.randomUUID();
        String schedB = "exec-in-b-" + UUID.randomUUID();
        saveExec(schedA, EXECUTED, 1000L);
        saveExec(schedB, EXECUTED, 2000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "scheduleName IN (" + schedA + ")", "*");
        assertEquals(1, result.getTotalHits());
        assertEquals(schedA, result.getResults().get(0).getScheduleName());
    }

    @Test
    public void testSearchExecutions_query_scheduleNameIn_multipleValues() {
        String schedA = "exec-multi-a-" + UUID.randomUUID();
        String schedB = "exec-multi-b-" + UUID.randomUUID();
        String schedC = "exec-multi-c-" + UUID.randomUUID();
        saveExec(schedA, EXECUTED, 1000L);
        saveExec(schedB, EXECUTED, 2000L);
        saveExec(schedC, EXECUTED, 3000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(
                        0, 100, null, "scheduleName IN (" + schedA + "," + schedB + ")", "*");
        assertEquals(2, result.getTotalHits());
        List<String> names =
                result.getResults().stream()
                        .map(WorkflowScheduleExecution::getScheduleName)
                        .collect(Collectors.toList());
        assertTrue(names.contains(schedA));
        assertTrue(names.contains(schedB));
    }

    @Test
    public void testSearchExecutions_query_scheduleNameIn_noMatch_returnsEmpty() {
        String sched = "exec-nomatch-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "scheduleName IN (nonexistent)", "*");
        assertEquals(0, result.getTotalHits());
    }

    // --- query: status IN ---

    @Test
    public void testSearchExecutions_query_statusIn_executed() {
        String sched = "exec-st-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, FAILED, 2000L);
        saveExec(sched, POLLED, 3000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "status IN (EXECUTED)", "*");
        assertEquals(1, result.getTotalHits());
        assertEquals(EXECUTED, result.getResults().get(0).getState());
    }

    @Test
    public void testSearchExecutions_query_statusIn_multiple() {
        String sched = "exec-stm-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, FAILED, 2000L);
        saveExec(sched, POLLED, 3000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "status IN (EXECUTED,FAILED)", "*");
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearchExecutions_query_statusIn_noMatch_returnsEmpty() {
        String sched = "exec-stnm-" + UUID.randomUUID();
        saveExec(sched, POLLED, 1000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "status IN (EXECUTED)", "*");
        assertEquals(0, result.getTotalHits());
    }

    // --- query: workflowType IN ---

    @Test
    public void testSearchExecutions_query_workflowTypeIn_match() {
        String sched = "exec-wft-" + UUID.randomUUID();
        saveExecDetailed(sched, EXECUTED, 1000L, "target-workflow", null);
        saveExecDetailed(sched, EXECUTED, 2000L, "other-workflow", null);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "workflowType IN (target-workflow)", "*");
        assertEquals(1, result.getTotalHits());
        assertEquals("target-workflow", result.getResults().get(0).getWorkflowName());
    }

    @Test
    public void testSearchExecutions_query_workflowTypeIn_nullWorkflowName_excluded() {
        String sched = "exec-wftnull-" + UUID.randomUUID();
        // buildExecution does NOT set workflowName → null
        saveExec(sched, EXECUTED, 1000L);

        // A record with null workflowName must be excluded by workflowType filter
        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "workflowType IN (any-workflow)", "*");
        assertEquals(0, result.getTotalHits());
    }

    // --- query: startTime > / startTime < ---

    @Test
    public void testSearchExecutions_query_startTimeGt_match() {
        String sched = "exec-tgt-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 5000L);
        saveExec(sched, EXECUTED, 9000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "startTime>4000", "*");
        assertEquals(2, result.getTotalHits());
        result.getResults().forEach(r -> assertTrue(r.getExecutionTime() > 4000L));
    }

    @Test
    public void testSearchExecutions_query_startTimeGt_nullExecutionTime_excluded() {
        String sched = "exec-tgtnull-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 5000L); // has executionTime
        WorkflowScheduleExecution nullTime = buildExecution(sched);
        nullTime.setExecutionTime(null);
        nullTime.setState(EXECUTED);
        dao().saveExecutionRecord(nullTime);

        // startTime>0 should only return the record with executionTime=5000, not the null one
        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "startTime>0", "*");
        assertEquals(1, result.getTotalHits());
        assertEquals(5000L, result.getResults().get(0).getExecutionTime().longValue());
    }

    @Test
    public void testSearchExecutions_query_startTimeLt_match() {
        String sched = "exec-tlt-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 5000L);
        saveExec(sched, EXECUTED, 9000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "startTime<6000", "*");
        assertEquals(2, result.getTotalHits());
        result.getResults().forEach(r -> assertTrue(r.getExecutionTime() < 6000L));
    }

    @Test
    public void testSearchExecutions_query_startTimeLt_nullExecutionTime_excluded() {
        String sched = "exec-tltnull-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 2000L); // has executionTime
        WorkflowScheduleExecution nullTime = buildExecution(sched);
        nullTime.setExecutionTime(null);
        nullTime.setState(EXECUTED);
        dao().saveExecutionRecord(nullTime);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "startTime<9999999999", "*");
        assertEquals(1, result.getTotalHits());
        assertEquals(2000L, result.getResults().get(0).getExecutionTime().longValue());
    }

    @Test
    public void testSearchExecutions_query_startTimeRange() {
        String sched = "exec-range-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 3000L);
        saveExec(sched, EXECUTED, 7000L);
        saveExec(sched, EXECUTED, 9000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "startTime>2000 AND startTime<8000", "*");
        assertEquals(2, result.getTotalHits());
        result.getResults()
                .forEach(
                        r -> {
                            assertTrue(r.getExecutionTime() > 2000L);
                            assertTrue(r.getExecutionTime() < 8000L);
                        });
    }

    // --- query: executionId = ---

    @Test
    public void testSearchExecutions_query_executionId_exactMatch() {
        String sched = "exec-eid-" + UUID.randomUUID();
        WorkflowScheduleExecution target = saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 2000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(
                        0, 100, null, "executionId='" + target.getExecutionId() + "'", "*");
        assertEquals(1, result.getTotalHits());
        assertEquals(target.getExecutionId(), result.getResults().get(0).getExecutionId());
    }

    @Test
    public void testSearchExecutions_query_executionId_noMatch_returnsEmpty() {
        String sched = "exec-eidnm-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, "executionId='nonexistent-id'", "*");
        assertEquals(0, result.getTotalHits());
    }

    // --- query: unknown clause passes through ---

    @Test
    public void testSearchExecutions_query_unknownClause_doesNotFilter() {
        String sched = "exec-unk-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 2000L);

        // "unknownField=value" matches no known pattern → passes through, no records excluded
        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(
                        0, 100, null, "unknownField=value AND status IN (EXECUTED)", "*");
        assertEquals(2, result.getTotalHits());
    }

    // --- query: combined AND clauses ---

    @Test
    public void testSearchExecutions_query_combined_scheduleNameAndStatus() {
        String schedA = "exec-comb-a-" + UUID.randomUUID();
        String schedB = "exec-comb-b-" + UUID.randomUUID();
        saveExec(schedA, EXECUTED, 1000L);
        saveExec(schedA, FAILED, 2000L);
        saveExec(schedB, EXECUTED, 3000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(
                        0,
                        100,
                        null,
                        "scheduleName IN (" + schedA + ") AND status IN (EXECUTED)",
                        "*");
        assertEquals(1, result.getTotalHits());
        assertEquals(schedA, result.getResults().get(0).getScheduleName());
        assertEquals(EXECUTED, result.getResults().get(0).getState());
    }

    @Test
    public void testSearchExecutions_query_combined_statusAndTimeRange() {
        String sched = "exec-comb2-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 5000L);
        saveExec(sched, FAILED, 5000L);
        saveExec(sched, EXECUTED, 9000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(
                        0,
                        100,
                        null,
                        "status IN (EXECUTED) AND startTime>2000 AND startTime<8000",
                        "*");
        assertEquals(1, result.getTotalHits());
        assertEquals(EXECUTED, result.getResults().get(0).getState());
        assertEquals(5000L, result.getResults().get(0).getExecutionTime().longValue());
    }

    // --- freeText filter ---

    @Test
    public void testSearchExecutions_freeText_matchesScheduleName() {
        String matchSched = "exec-freetext-needle-" + UUID.randomUUID();
        String otherSched = "exec-freetext-other-" + UUID.randomUUID();
        saveExec(matchSched, EXECUTED, 1000L);
        saveExec(otherSched, EXECUTED, 2000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, "freetext-needle");
        assertEquals(1, result.getTotalHits());
        assertEquals(matchSched, result.getResults().get(0).getScheduleName());
    }

    @Test
    public void testSearchExecutions_freeText_matchesWorkflowId() {
        String sched = "exec-wfid-" + UUID.randomUUID();
        saveExecDetailed(sched, EXECUTED, 1000L, null, "unique-wf-id-abc123");
        saveExecDetailed(sched, EXECUTED, 2000L, null, "different-wf-id-xyz");

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, "abc123");
        assertEquals(1, result.getTotalHits());
        assertEquals("unique-wf-id-abc123", result.getResults().get(0).getWorkflowId());
    }

    @Test
    public void testSearchExecutions_freeText_matchesExecutionId() {
        String sched = "exec-eid2-" + UUID.randomUUID();
        WorkflowScheduleExecution target = buildExecution(sched);
        target.setExecutionId("fixed-exec-id-needle-0001");
        target.setExecutionTime(1000L);
        target.setState(EXECUTED);
        dao().saveExecutionRecord(target);
        saveExec(sched, EXECUTED, 2000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, "needle-0001");
        assertEquals(1, result.getTotalHits());
        assertEquals("fixed-exec-id-needle-0001", result.getResults().get(0).getExecutionId());
    }

    @Test
    public void testSearchExecutions_freeText_noMatch_returnsEmpty() {
        String sched = "exec-freetextnm-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, "zzz-definitely-not-in-any-field");
        assertEquals(0, result.getTotalHits());
    }

    @Test
    public void testSearchExecutions_freeText_star_noFilter_returnsAll() {
        String sched = "exec-star-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 2000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, "*");
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearchExecutions_freeText_null_noFilter_returnsAll() {
        String sched = "exec-nullft-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 2000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, null);
        assertEquals(2, result.getTotalHits());
    }

    // --- sort ---

    @Test
    public void testSearchExecutions_sort_startTimeDesc() {
        String sched = "exec-sortd-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 5000L);
        saveExec(sched, EXECUTED, 3000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, "startTime:DESC", null, "*");
        List<WorkflowScheduleExecution> res = result.getResults();
        assertEquals(5000L, res.get(0).getExecutionTime().longValue());
        assertEquals(3000L, res.get(1).getExecutionTime().longValue());
        assertEquals(1000L, res.get(2).getExecutionTime().longValue());
    }

    @Test
    public void testSearchExecutions_sort_startTimeAsc() {
        String sched = "exec-sorta-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 3000L);
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 5000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, "startTime:ASC", null, "*");
        List<WorkflowScheduleExecution> res = result.getResults();
        assertEquals(1000L, res.get(0).getExecutionTime().longValue());
        assertEquals(3000L, res.get(1).getExecutionTime().longValue());
        assertEquals(5000L, res.get(2).getExecutionTime().longValue());
    }

    @Test
    public void testSearchExecutions_sort_scheduledTimeDesc() {
        String sched = "exec-sortsd-" + UUID.randomUUID();
        WorkflowScheduleExecution e1 = buildExecution(sched);
        e1.setScheduledTime(1000L);
        e1.setExecutionTime(1000L);
        e1.setState(EXECUTED);
        dao().saveExecutionRecord(e1);
        WorkflowScheduleExecution e2 = buildExecution(sched);
        e2.setScheduledTime(9000L);
        e2.setExecutionTime(9000L);
        e2.setState(EXECUTED);
        dao().saveExecutionRecord(e2);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, "scheduledTime:DESC", null, "*");
        assertEquals(9000L, result.getResults().get(0).getScheduledTime().longValue());
        assertEquals(1000L, result.getResults().get(1).getScheduledTime().longValue());
    }

    @Test
    public void testSearchExecutions_sort_scheduledTimeAsc() {
        String sched = "exec-sortsa-" + UUID.randomUUID();
        WorkflowScheduleExecution e1 = buildExecution(sched);
        e1.setScheduledTime(9000L);
        e1.setExecutionTime(9000L);
        e1.setState(EXECUTED);
        dao().saveExecutionRecord(e1);
        WorkflowScheduleExecution e2 = buildExecution(sched);
        e2.setScheduledTime(1000L);
        e2.setExecutionTime(1000L);
        e2.setState(EXECUTED);
        dao().saveExecutionRecord(e2);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, "scheduledTime:ASC", null, "*");
        assertEquals(1000L, result.getResults().get(0).getScheduledTime().longValue());
    }

    @Test
    public void testSearchExecutions_sort_null_doesNotThrow() {
        String sched = "exec-sortnull-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, null, null, "*");
        assertEquals(1, result.getTotalHits());
    }

    @Test
    public void testSearchExecutions_sort_noDirection_defaultsToDesc() {
        // "startTime" with no colon → parts.length < 2 → desc = true
        String sched = "exec-sortnd-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);
        saveExec(sched, EXECUTED, 5000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 100, "startTime", null, "*");
        assertEquals(2, result.getTotalHits());
        // DESC: 5000 first
        assertEquals(5000L, result.getResults().get(0).getExecutionTime().longValue());
    }

    // --- pagination ---

    @Test
    public void testSearchExecutions_pagination_pageSlice() {
        String sched = "exec-page-" + UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            saveExec(sched, EXECUTED, (long) i * 1000);
        }
        // Sort ASC: 1000, 2000, 3000, 4000, 5000
        // start=2, size=2 → [3000, 4000]
        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(2, 2, "startTime:ASC", null, "*");
        assertEquals(5, result.getTotalHits());
        assertEquals(2, result.getResults().size());
        assertEquals(3000L, result.getResults().get(0).getExecutionTime().longValue());
        assertEquals(4000L, result.getResults().get(1).getExecutionTime().longValue());
    }

    @Test
    public void testSearchExecutions_pagination_totalHitsBeforePagination() {
        String sched = "exec-total-" + UUID.randomUUID();
        for (int i = 1; i <= 8; i++) {
            saveExec(sched, EXECUTED, (long) i * 1000);
        }

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(0, 3, null, null, "*");
        assertEquals(8, result.getTotalHits());
        assertEquals(3, result.getResults().size());
    }

    @Test
    public void testSearchExecutions_pagination_startPastEnd_returnsEmptyPage() {
        String sched = "exec-past-" + UUID.randomUUID();
        saveExec(sched, EXECUTED, 1000L);

        SearchResult<WorkflowScheduleExecution> result =
                service.searchExecutions(10, 10, null, null, "*");
        assertEquals(1, result.getTotalHits());
        assertTrue(result.getResults().isEmpty());
    }

    // =========================================================================
    // k. getListOfNextSchedules
    // =========================================================================

    @Test
    public void testGetListOfNextSchedules_basic_returnsUpToLimit() {
        // Every second cron; limit=3 → 3 results
        List<Long> times = service.getListOfNextSchedules("*/10 * * * * *", null, null, 3);
        assertEquals(3, times.size());
        // All times are in the future
        long now = System.currentTimeMillis();
        times.forEach(t -> assertTrue(t > now - 60_000));
    }

    @Test
    public void testGetListOfNextSchedules_withScheduleStartTime() {
        // Start 1 day in the future, limit=2
        long startTime = System.currentTimeMillis() + 24 * 3600 * 1000L;
        List<Long> times = service.getListOfNextSchedules("0 * * * * *", startTime, null, 2);
        assertEquals(2, times.size());
        times.forEach(t -> assertTrue(t >= startTime));
    }

    @Test
    public void testGetListOfNextSchedules_withScheduleEndTime_limitsResults() {
        long now = System.currentTimeMillis();
        // Every 10 seconds, end in 30 seconds → at most 3 results
        long endTime = now + 30_000;
        List<Long> times = service.getListOfNextSchedules("*/10 * * * * *", null, endTime, 100);
        assertFalse(times.isEmpty());
        assertTrue("Should be at most ~3 results within 30s window", times.size() <= 4);
        times.forEach(t -> assertTrue(t <= endTime));
    }

    @Test
    public void testGetListOfNextSchedules_zeroLimit_returnsEmpty() {
        List<Long> times = service.getListOfNextSchedules("0 * * * * *", null, null, 0);
        assertTrue(times.isEmpty());
    }
}
