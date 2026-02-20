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
package org.conductoross.conductor.scheduler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.scheduler.config.SchedulerProperties;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 2 stress / edge-case tests for {@link SchedulerService}.
 *
 * <p>Categories covered:
 *
 * <ol>
 *   <li>Failure modes (workflow errors, DAO errors, stale POLLED cleanup)
 *   <li>Scale (multiple due schedules, batch size enforcement, paused schedule)
 *   <li>Archival / pruning (below threshold, above threshold, exact threshold)
 *   <li>Invalid inputs (malformed cron, bad timezone, null/blank name, end ≤ start)
 * </ol>
 */
public class SchedulerServicePhase2Test {

    private SchedulerDAO dao;
    private WorkflowService workflowService;
    private SchedulerService service;
    private SchedulerProperties properties;

    @Before
    public void setUp() {
        dao = mock(SchedulerDAO.class);
        workflowService = mock(WorkflowService.class);
        properties = new SchedulerProperties();
        properties.setEnabled(false); // prevent polling executor from starting
        properties.setArchivalMaxRecords(3);
        properties.setArchivalMaxRecordThreshold(5);
        properties.setPollBatchSize(10);
        service = new SchedulerService(dao, workflowService, properties);
    }

    // =========================================================================
    // Category 4 — Failure modes
    // =========================================================================

    /**
     * When workflowService.startWorkflow throws NotFoundException, the execution record must
     * transition to FAILED state with a reason set. The poll loop must not propagate the exception.
     *
     * <p>NOTE: handleSchedule reuses the same mutable execution object for both saveExecutionRecord
     * calls, so we must snapshot the state at call time using doAnswer rather than ArgumentCaptor.
     */
    @Test
    public void testHandleSchedule_workflowNotFound_createsFailedRecord() {
        WorkflowSchedule schedule = buildSchedule("fail-sched", "0 * * * * *", "UTC");
        long pastSlot = System.currentTimeMillis() - 60_000;

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("fail-sched"))).thenReturn(pastSlot);
        when(workflowService.startWorkflow(any()))
                .thenThrow(new NotFoundException("Workflow not found"));
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        // Snapshot states at call time since the execution object is mutated between calls
        List<WorkflowScheduleExecution.ExecutionState> capturedStates = new ArrayList<>();
        List<String> capturedReasons = new ArrayList<>();
        doAnswer(
                        inv -> {
                            WorkflowScheduleExecution exec = inv.getArgument(0);
                            capturedStates.add(exec.getState());
                            capturedReasons.add(exec.getReason());
                            return null;
                        })
                .when(dao)
                .saveExecutionRecord(any());

        service.pollAndExecuteSchedules();

        assertEquals(2, capturedStates.size());
        assertEquals(WorkflowScheduleExecution.ExecutionState.POLLED, capturedStates.get(0));
        assertEquals(WorkflowScheduleExecution.ExecutionState.FAILED, capturedStates.get(1));
        assertNotNull("Reason must be set on FAILED record", capturedReasons.get(1));
    }

    /**
     * Any RuntimeException from workflowService.startWorkflow must also produce a FAILED record and
     * not crash the poll loop.
     */
    @Test
    public void testHandleSchedule_workflowThrowsRuntimeException_createsFailedRecord() {
        WorkflowSchedule schedule = buildSchedule("runtime-fail", "0 * * * * *", "UTC");
        long pastSlot = System.currentTimeMillis() - 60_000;

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("runtime-fail"))).thenReturn(pastSlot);
        when(workflowService.startWorkflow(any()))
                .thenThrow(new RuntimeException("Unexpected DB timeout"));
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        List<WorkflowScheduleExecution.ExecutionState> capturedStates = new ArrayList<>();
        doAnswer(
                        inv -> {
                            WorkflowScheduleExecution exec = inv.getArgument(0);
                            capturedStates.add(exec.getState());
                            return null;
                        })
                .when(dao)
                .saveExecutionRecord(any());

        // Must not throw
        service.pollAndExecuteSchedules();

        assertEquals(2, capturedStates.size());
        assertEquals(WorkflowScheduleExecution.ExecutionState.POLLED, capturedStates.get(0));
        assertEquals(WorkflowScheduleExecution.ExecutionState.FAILED, capturedStates.get(1));
    }

    /**
     * If getAllSchedules throws, the poll cycle must catch the exception and not propagate it. No
     * workflows should be started.
     */
    @Test
    public void testPollAndExecute_daoGetAllSchedulesThrows_doesNotCrash() {
        when(dao.getAllSchedules()).thenThrow(new RuntimeException("DB connection lost"));
        when(dao.getPendingExecutionRecordIds()).thenReturn(List.of());

        // Must not throw
        service.pollAndExecuteSchedules();

        verify(workflowService, never()).startWorkflow(any());
    }

    /**
     * A POLLED execution record older than 5 minutes must be transitioned to FAILED state by
     * cleanupStalePollRecords. This simulates a server crash mid-execution.
     */
    @Test
    public void testCleanupStalePollRecords_oldRecord_transitionsToFailed() {
        when(dao.getAllSchedules()).thenReturn(List.of());

        long tenMinAgo = System.currentTimeMillis() - 10 * 60 * 1000;
        WorkflowScheduleExecution staleRecord = new WorkflowScheduleExecution();
        staleRecord.setExecutionId("exec-stale");
        staleRecord.setScheduleName("some-sched");
        staleRecord.setState(WorkflowScheduleExecution.ExecutionState.POLLED);
        staleRecord.setExecutionTime(tenMinAgo);

        when(dao.getPendingExecutionRecordIds()).thenReturn(List.of("exec-stale"));
        when(dao.readExecutionRecord(eq("exec-stale"))).thenReturn(staleRecord);

        service.pollAndExecuteSchedules();

        ArgumentCaptor<WorkflowScheduleExecution> captor =
                ArgumentCaptor.forClass(WorkflowScheduleExecution.class);
        verify(dao).saveExecutionRecord(captor.capture());

        WorkflowScheduleExecution saved = captor.getValue();
        assertEquals(WorkflowScheduleExecution.ExecutionState.FAILED, saved.getState());
        assertNotNull("Stale POLLED record must have a reason", saved.getReason());
        assertEquals("exec-stale", saved.getExecutionId());
    }

    /**
     * A POLLED execution record that is only 30 seconds old is NOT stale (threshold is 5 minutes).
     * It must not be transitioned to FAILED.
     */
    @Test
    public void testCleanupStalePollRecords_freshRecord_notTransitioned() {
        when(dao.getAllSchedules()).thenReturn(List.of());

        long thirtySecondsAgo = System.currentTimeMillis() - 30 * 1000;
        WorkflowScheduleExecution freshRecord = new WorkflowScheduleExecution();
        freshRecord.setExecutionId("exec-fresh");
        freshRecord.setScheduleName("some-sched");
        freshRecord.setState(WorkflowScheduleExecution.ExecutionState.POLLED);
        freshRecord.setExecutionTime(thirtySecondsAgo);

        when(dao.getPendingExecutionRecordIds()).thenReturn(List.of("exec-fresh"));
        when(dao.readExecutionRecord(eq("exec-fresh"))).thenReturn(freshRecord);

        service.pollAndExecuteSchedules();

        // saveExecutionRecord must NOT be called for the fresh record
        verify(dao, never()).saveExecutionRecord(any());
    }

    // =========================================================================
    // Category 5 — Scale
    // =========================================================================

    /**
     * When multiple schedules are all due and the batch size is large enough, every schedule fires
     * exactly once in a single poll cycle.
     */
    @Test
    public void testPollAndExecute_multipleDueSchedules_firesAll() {
        long pastSlot = System.currentTimeMillis() - 60_000;

        List<WorkflowSchedule> schedules = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schedules.add(buildSchedule("multi-" + i, "0 * * * * *", "UTC"));
        }

        when(dao.getAllSchedules()).thenReturn(schedules);
        for (int i = 0; i < 5; i++) {
            when(dao.getNextRunTimeInEpoch(eq("multi-" + i))).thenReturn(pastSlot);
        }
        when(workflowService.startWorkflow(any())).thenReturn("wf-id");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        service.pollAndExecuteSchedules();

        verify(workflowService, times(5)).startWorkflow(any());
    }

    /**
     * When more schedules are due than pollBatchSize, only pollBatchSize schedules fire per cycle.
     */
    @Test
    public void testPollAndExecute_exceedsBatchSize_respectsBatchLimit() {
        properties.setPollBatchSize(3);
        long pastSlot = System.currentTimeMillis() - 60_000;

        List<WorkflowSchedule> schedules = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schedules.add(buildSchedule("batch-" + i, "0 * * * * *", "UTC"));
        }

        when(dao.getAllSchedules()).thenReturn(schedules);
        for (int i = 0; i < 5; i++) {
            when(dao.getNextRunTimeInEpoch(eq("batch-" + i))).thenReturn(pastSlot);
        }
        when(workflowService.startWorkflow(any())).thenReturn("wf-id");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        service.pollAndExecuteSchedules();

        verify(workflowService, times(3)).startWorkflow(any());
    }

    /** A paused schedule must never fire, even if its nextRunTime is in the past. */
    @Test
    public void testPollAndExecute_pausedSchedule_doesNotFire() {
        WorkflowSchedule schedule = buildSchedule("paused-sched", "0 * * * * *", "UTC");
        schedule.setPaused(true);

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getPendingExecutionRecordIds()).thenReturn(List.of());

        service.pollAndExecuteSchedules();

        verify(workflowService, never()).startWorkflow(any());
    }

    /**
     * Regression: when computeNextRunTime returns null (no slot within endTime), the next-run
     * pointer must still be advanced past endTime. Without this fix the last slot fires repeatedly
     * until now overtakes scheduleEndTime.
     *
     * <p>Setup: endTime is 30s in the future; cron fires every hour so the next occurrence is well
     * beyond endTime → computeNextRunTime returns null. Uses a stateful mock so
     * setNextRunTimeInEpoch updates the value returned by getNextRunTimeInEpoch, mirroring real DAO
     * behaviour.
     */
    @Test
    public void testHandleSchedule_lastSlotBeforeEndTime_doesNotFireRepeatedly() {
        long now = System.currentTimeMillis();
        long pastSlot = now - 10_000; // a slot 10s ago (within bounds)
        long endTime = now + 30_000; // endTime 30s from now; next hourly slot is far beyond it

        // Hourly cron: next occurrence after now is always > endTime = now+30s
        WorkflowSchedule schedule = buildSchedule("bounded-end", "0 0 * * * *", "UTC");
        schedule.setScheduleEndTime(endTime);

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));

        // Stateful mock: tracks the live pointer value as setNextRunTimeInEpoch updates it
        long[] pointer = {pastSlot};
        doAnswer(inv -> pointer[0]).when(dao).getNextRunTimeInEpoch(eq("bounded-end"));
        doAnswer(
                        inv -> {
                            pointer[0] = inv.getArgument(1); // arg 1 is epochMillis after orgId removal
                            return null;
                        })
                .when(dao)
                .setNextRunTimeInEpoch(eq("bounded-end"), anyLong());

        when(workflowService.startWorkflow(any())).thenReturn("wf-id");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        service.pollAndExecuteSchedules(); // fires once; pointer advances to endTime+1
        service.pollAndExecuteSchedules(); // pointer > now → isDue=false; must NOT fire again

        verify(workflowService, times(1)).startWorkflow(any());
        assertTrue("Pointer must be set past endTime", pointer[0] > endTime);
    }

    // =========================================================================
    // Category 6 — Archival / pruning
    // =========================================================================

    /**
     * When the number of execution records (4) is below the threshold (5), no records are pruned.
     */
    @Test
    public void testPruneExecution_belowThreshold_doesNotPrune() {
        WorkflowSchedule schedule = buildSchedule("prune-below", "0 * * * * *", "UTC");
        long pastSlot = System.currentTimeMillis() - 60_000;

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("prune-below"))).thenReturn(pastSlot);
        when(workflowService.startWorkflow(any())).thenReturn("wf-id");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(buildExecutionList(4));

        service.pollAndExecuteSchedules();

        verify(dao, never()).removeExecutionRecord(anyString());
    }

    /**
     * When the number of records (6) exceeds the threshold (5), records beyond the keep limit (3)
     * are pruned. The records at indexes 3, 4, 5 of the newest-first list are removed.
     */
    @Test
    public void testPruneExecution_exceedsThreshold_prunesOldest() {
        WorkflowSchedule schedule = buildSchedule("prune-over", "0 * * * * *", "UTC");
        long pastSlot = System.currentTimeMillis() - 60_000;

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("prune-over"))).thenReturn(pastSlot);
        when(workflowService.startWorkflow(any())).thenReturn("wf-id");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(buildExecutionList(6));

        service.pollAndExecuteSchedules();

        // 6 records - keep 3 = 3 removed (exec-3, exec-4, exec-5 are the oldest)
        verify(dao, times(3)).removeExecutionRecord(anyString());
        verify(dao).removeExecutionRecord(eq("exec-3"));
        verify(dao).removeExecutionRecord(eq("exec-4"));
        verify(dao).removeExecutionRecord(eq("exec-5"));
    }

    /**
     * Exactly at the threshold (5 records, threshold=5) the pruning condition is NOT triggered
     * (strictly greater than).
     */
    @Test
    public void testPruneExecution_exactlyAtThreshold_doesNotPrune() {
        WorkflowSchedule schedule = buildSchedule("prune-exact", "0 * * * * *", "UTC");
        long pastSlot = System.currentTimeMillis() - 60_000;

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("prune-exact"))).thenReturn(pastSlot);
        when(workflowService.startWorkflow(any())).thenReturn("wf-id");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(buildExecutionList(5));

        service.pollAndExecuteSchedules();

        verify(dao, never()).removeExecutionRecord(anyString());
    }

    // =========================================================================
    // Category 7 — Invalid inputs
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_malformedCron_throwsIllegalArgument() {
        WorkflowSchedule schedule = buildSchedule("bad-cron", "not-a-valid-cron", "UTC");
        service.saveSchedule(schedule);
    }

    /** An unrecognized timezone must be rejected at save time with an IllegalArgumentException. */
    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_unknownTimezone_throwsIllegalArgument() {
        WorkflowSchedule schedule = buildSchedule("bad-zone", "0 * * * * *", "Not/AValidZone");
        service.saveSchedule(schedule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_nullName_throwsIllegalArgument() {
        WorkflowSchedule schedule = buildSchedule(null, "0 * * * * *", "UTC");
        service.saveSchedule(schedule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_blankName_throwsIllegalArgument() {
        WorkflowSchedule schedule = buildSchedule("   ", "0 * * * * *", "UTC");
        service.saveSchedule(schedule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_endTimeBeforeStartTime_throwsIllegalArgument() {
        long now = System.currentTimeMillis();
        WorkflowSchedule schedule = buildSchedule("bad-bounds", "0 * * * * *", "UTC");
        schedule.setScheduleStartTime(now + 2 * 60 * 60 * 1000);
        schedule.setScheduleEndTime(now + 1 * 60 * 60 * 1000); // end before start
        service.saveSchedule(schedule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_endTimeEqualsStartTime_throwsIllegalArgument() {
        long fixed = System.currentTimeMillis() + 60 * 60 * 1000;
        WorkflowSchedule schedule = buildSchedule("eq-bounds", "0 * * * * *", "UTC");
        schedule.setScheduleStartTime(fixed);
        schedule.setScheduleEndTime(fixed); // end == start
        service.saveSchedule(schedule);
    }

    // =========================================================================
    // scheduledTime / executionTime injection
    // =========================================================================

    /**
     * Every triggered workflow must receive scheduledTime and executionTime in its input, injected
     * by the scheduler (matching Orkes Conductor behaviour for convergence).
     */
    @Test
    public void testHandleSchedule_injectsScheduledTimeAndExecutionTimeIntoInput() {
        long slot = System.currentTimeMillis() - 5_000;
        long now = System.currentTimeMillis();
        WorkflowSchedule schedule = buildSchedule("inject-test", "0 * * * * *", "UTC");

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("inject-test"))).thenReturn(slot);

        ArgumentCaptor<StartWorkflowRequest> captor =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        when(workflowService.startWorkflow(captor.capture())).thenReturn("wf-inject");

        service.pollAndExecuteSchedules();

        StartWorkflowRequest fired = captor.getValue();
        assertNotNull("input must not be null", fired.getInput());
        assertTrue("scheduledTime must be present", fired.getInput().containsKey("scheduledTime"));
        assertTrue("executionTime must be present", fired.getInput().containsKey("executionTime"));
        assertEquals(
                "scheduledTime must equal the polled slot",
                slot,
                ((Number) fired.getInput().get("scheduledTime")).longValue());
    }

    /** Existing input keys configured on the schedule must be preserved alongside injected keys. */
    @Test
    public void testHandleSchedule_preservesExistingInputKeys() {
        long slot = System.currentTimeMillis() - 5_000;
        WorkflowSchedule schedule = buildSchedule("preserve-input", "0 * * * * *", "UTC");
        schedule.getStartWorkflowRequest()
                .setInput(new java.util.HashMap<>(java.util.Map.of("customKey", "customValue")));

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("preserve-input"))).thenReturn(slot);

        ArgumentCaptor<StartWorkflowRequest> captor =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        when(workflowService.startWorkflow(captor.capture())).thenReturn("wf-preserve");

        service.pollAndExecuteSchedules();

        StartWorkflowRequest fired = captor.getValue();
        assertEquals("customValue", fired.getInput().get("customKey"));
        assertTrue(fired.getInput().containsKey("scheduledTime"));
        assertTrue(fired.getInput().containsKey("executionTime"));
    }

    // =========================================================================
    // Jitter
    // =========================================================================

    /**
     * When jitter is enabled, dispatch is asynchronous but the workflow must still be started. Uses
     * CountDownLatch for reliable synchronization instead of a fixed sleep.
     */
    @Test
    public void testJitter_enabled_workflowEventuallyFires() throws InterruptedException {
        SchedulerProperties jitterProps = new SchedulerProperties();
        jitterProps.setEnabled(false); // prevent background polling
        jitterProps.setJitterMaxMs(50);
        jitterProps.setPollBatchSize(4);
        jitterProps.setArchivalMaxRecords(3);
        jitterProps.setArchivalMaxRecordThreshold(5);
        SchedulerService jitterService = new SchedulerService(dao, workflowService, jitterProps);
        jitterService.initExecutors(); // create executors only — no background poll thread

        long slot = System.currentTimeMillis() - 5_000;
        WorkflowSchedule schedule = buildSchedule("jitter-sched", "0 * * * * *", "UTC");
        CountDownLatch latch = new CountDownLatch(1);

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("jitter-sched"))).thenReturn(slot);
        when(workflowService.startWorkflow(any()))
                .thenAnswer(
                        inv -> {
                            latch.countDown();
                            return "wf-jitter";
                        });
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        jitterService.pollAndExecuteSchedules();

        assertTrue(
                "startWorkflow must be called within 2s despite jitter delay",
                latch.await(2, TimeUnit.SECONDS));
        jitterService.stop();
    }

    /**
     * When jitter is disabled (jitterMaxMs=0), dispatch is synchronous — startWorkflow is called
     * before pollAndExecuteSchedules returns.
     */
    @Test
    public void testJitter_disabled_dispatchIsSynchronous() {
        // jitterMaxMs defaults to 0 in setUp
        long slot = System.currentTimeMillis() - 5_000;
        WorkflowSchedule schedule = buildSchedule("no-jitter", "0 * * * * *", "UTC");

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("no-jitter"))).thenReturn(slot);
        when(workflowService.startWorkflow(any())).thenReturn("wf-no-jitter");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        service.pollAndExecuteSchedules();

        // Synchronous — no sleep needed
        verify(workflowService, times(1)).startWorkflow(any());
    }

    /**
     * With jitter enabled, the next-run pointer must be advanced synchronously (before dispatch) so
     * that a second poll cycle cannot double-fire the same slot while the jittered task is still
     * pending.
     */
    @Test
    public void testJitter_pointerAdvancedBeforeDispatch() {
        SchedulerProperties jitterProps = new SchedulerProperties();
        jitterProps.setEnabled(false);
        jitterProps.setJitterMaxMs(100);
        jitterProps.setPollBatchSize(4);
        jitterProps.setArchivalMaxRecords(3);
        jitterProps.setArchivalMaxRecordThreshold(5);
        SchedulerService jitterService = new SchedulerService(dao, workflowService, jitterProps);
        jitterService.initExecutors();

        long slot = System.currentTimeMillis() - 5_000;
        WorkflowSchedule schedule = buildSchedule("jitter-ptr", "0 * * * * *", "UTC");

        when(dao.getAllSchedules()).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(eq("jitter-ptr"))).thenReturn(slot);
        when(workflowService.startWorkflow(any())).thenReturn("wf-ptr");
        when(dao.getExecutionRecords(anyString(), anyInt())).thenReturn(List.of());

        jitterService.pollAndExecuteSchedules();

        // Pointer must be advanced immediately (synchronously) before the jitter delay fires
        verify(dao, times(1)).setNextRunTimeInEpoch(eq("jitter-ptr"), anyLong());

        jitterService.stop();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private WorkflowSchedule buildSchedule(String name, String cron, String zone) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName("test-workflow");
        req.setVersion(1);

        WorkflowSchedule s = new WorkflowSchedule();
        s.setName(name);
        s.setCronExpression(cron);
        s.setZoneId(zone);
        s.setStartWorkflowRequest(req);
        return s;
    }

    private List<WorkflowScheduleExecution> buildExecutionList(int count) {
        List<WorkflowScheduleExecution> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WorkflowScheduleExecution ex = new WorkflowScheduleExecution();
            ex.setExecutionId("exec-" + i);
            ex.setScheduleName("test");
            ex.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
            list.add(ex);
        }
        return list;
    }
}
