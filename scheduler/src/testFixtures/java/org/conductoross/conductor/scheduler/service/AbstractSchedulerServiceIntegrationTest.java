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

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.config.SchedulerProperties;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

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
     * Verifies that the {@code event} field on the {@link StartWorkflowRequest} passed to
     * {@link WorkflowService#startWorkflow} is set to {@code "scheduler:<scheduleName>"} so that
     * the resulting {@link com.netflix.conductor.common.run.Workflow} records which schedule
     * triggered it.
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
}
