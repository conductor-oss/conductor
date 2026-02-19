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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.scheduler.config.SchedulerProperties;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Stress / edge-case tests for {@link SchedulerService}.
 *
 * <p>Categories covered:
 *
 * <ol>
 *   <li>Timing edge cases (DST, slow polling)
 *   <li>Schedule bounds (startTime, endTime)
 *   <li>Catchup behaviour (runCatchupScheduleInstances true/false)
 * </ol>
 */
public class SchedulerServiceStressTest {

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
        service = new SchedulerService(dao, workflowService, properties);
    }

    // =========================================================================
    // Category 1 — Timing edge cases
    // =========================================================================

    /**
     * DST spring-forward gap (America/New_York, 2024-03-10).
     *
     * <p>Clocks jump from 01:59 to 03:00, so 02:30 never exists. A cron firing at "0 30 2 * * *"
     * should skip the missing time and return 02:30 on the NEXT day, not an invalid instant.
     */
    @Test
    public void testComputeNextRunTime_dstSpringForward_skipsNonExistentTime() {
        // 2024-03-10 01:00 AM New York — one hour before clocks spring forward
        ZoneId nyZone = ZoneId.of("America/New_York");
        ZonedDateTime beforeSpring = ZonedDateTime.of(2024, 3, 10, 1, 0, 0, 0, nyZone);
        long afterEpoch = beforeSpring.toInstant().toEpochMilli();

        WorkflowSchedule schedule = buildSchedule("dst-test", "0 30 2 * * *", "America/New_York");
        Long nextRun = service.computeNextRunTime(schedule, afterEpoch);

        assertNotNull("Should return a next run time even across DST gap", nextRun);

        ZonedDateTime result =
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextRun), nyZone);

        // 2:30 AM on March 10 doesn't exist — Spring's CronExpression advances to March 11
        assertEquals("Should skip to next valid 2:30 AM after the gap", 2024, result.getYear());
        assertEquals(3, result.getMonthValue());
        // Either March 10 at a shifted time or March 11 — either way, minute must be 30 and hour
        // must be 2 in a valid wall-clock representation
        assertEquals(30, result.getMinute());
    }

    /**
     * DST fall-back overlap (America/New_York, 2024-11-03).
     *
     * <p>Clocks fall back at 02:00, so 01:30 AM happens twice: once in EDT (UTC-4) and once in EST
     * (UTC-5). Spring's CronExpression fires at BOTH occurrences (this is correct — each is a
     * distinct instant). The two consecutive firings must be exactly 1 hour apart (the length of
     * the overlap), and the third firing must be ~24 hours after the second (next day).
     */
    @Test
    public void testComputeNextRunTime_dstFallBack_firesTwiceOnSameDay() {
        ZoneId nyZone = ZoneId.of("America/New_York");
        // Start just before 01:30 EDT on fall-back day (2024-11-03)
        ZonedDateTime beforeFallBack =
                ZonedDateTime.of(LocalDateTime.of(2024, 11, 3, 1, 0, 0), nyZone);
        long afterEpoch = beforeFallBack.toInstant().toEpochMilli();

        WorkflowSchedule schedule = buildSchedule("dst-fall", "0 30 1 * * *", "America/New_York");
        Long firstRun = service.computeNextRunTime(schedule, afterEpoch);
        assertNotNull(firstRun);
        Long secondRun = service.computeNextRunTime(schedule, firstRun);
        assertNotNull(secondRun);
        Long thirdRun = service.computeNextRunTime(schedule, secondRun);
        assertNotNull(thirdRun);

        long firstToSecondMillis = secondRun - firstRun;
        long secondToThirdMillis = thirdRun - secondRun;

        // First and second firings are 1 hour apart (the DST overlap)
        assertEquals(
                "First two firings should be exactly 1 hour apart during fall-back overlap",
                60 * 60 * 1000L,
                firstToSecondMillis);
        // Third firing is 24 hours after the second — both are in EST so no DST offset applies
        assertEquals(
                "Third firing should be 24 hours after second (both in EST post-fall-back)",
                24 * 60 * 60 * 1000L,
                secondToThirdMillis);

        // All three wall-clock times should show 01:30
        ZonedDateTime first =
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(firstRun), nyZone);
        ZonedDateTime second =
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(secondRun), nyZone);
        assertEquals(1, first.getHour());
        assertEquals(30, first.getMinute());
        assertEquals(1, second.getHour());
        assertEquals(30, second.getMinute());
    }

    /**
     * Slow polling: poll interval is larger than cron interval, so slots are missed. With
     * catchup=false, a single poll should fire once and advance to the next FUTURE slot.
     */
    @Test
    public void testPollAndExecute_slowPolling_catchupDisabled_firesOnceAndAdvances() {
        long tenMinAgo = System.currentTimeMillis() - 10 * 60 * 1000;
        WorkflowSchedule schedule = buildSchedule("slow-poll", "0 * * * * *", "UTC");
        schedule.setRunCatchupScheduleInstances(false);

        when(dao.getAllSchedules(anyString())).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(anyString(), eq("slow-poll"))).thenReturn(tenMinAgo);
        when(workflowService.startWorkflow(any())).thenReturn("wf-id-1");

        service.pollAndExecuteSchedules();

        // Should have fired exactly once
        verify(workflowService, times(1)).startWorkflow(any());

        // Capture the new nextRunTime and assert it is in the future
        ArgumentCaptor<Long> nextRunCaptor = ArgumentCaptor.forClass(Long.class);
        verify(dao).setNextRunTimeInEpoch(anyString(), eq("slow-poll"), nextRunCaptor.capture());
        long advancedTo = nextRunCaptor.getValue();
        assertTrue(
                "nextRunTime should advance past now, not stay 10 min in the past",
                advancedTo > System.currentTimeMillis() - 2000);
    }

    // =========================================================================
    // Category 2 — Schedule bounds
    // =========================================================================

    @Test
    public void testComputeNextRunTime_startTimeInFuture_nextRunRespectsStartTime() {
        long future = System.currentTimeMillis() + 60 * 60 * 1000; // 1 hour from now
        WorkflowSchedule schedule = buildSchedule("bounded", "0 * * * * *", "UTC");
        schedule.setScheduleStartTime(future);

        Long nextRun = service.computeNextRunTime(schedule, System.currentTimeMillis());

        assertNotNull(nextRun);
        assertTrue("nextRunTime must be at or after scheduleStartTime", nextRun >= future);
    }

    @Test
    public void testComputeNextRunTime_endTimeInPast_returnsNull() {
        long past = System.currentTimeMillis() - 60 * 60 * 1000; // 1 hour ago
        WorkflowSchedule schedule = buildSchedule("expired", "0 * * * * *", "UTC");
        schedule.setScheduleEndTime(past);

        Long nextRun = service.computeNextRunTime(schedule, System.currentTimeMillis());

        assertNull("Should return null when scheduleEndTime is in the past", nextRun);
    }

    @Test
    public void testIsDue_endTimeJustExpired_notDue() {
        // nextRunTime is technically "now" but endTime expired 1 second ago
        long now = System.currentTimeMillis();
        WorkflowSchedule schedule = buildSchedule("just-expired", "0 * * * * *", "UTC");
        schedule.setScheduleEndTime(now - 1000);

        when(dao.getAllSchedules(anyString())).thenReturn(List.of(schedule));
        when(dao.getNextRunTimeInEpoch(anyString(), eq("just-expired"))).thenReturn(now - 500);

        service.pollAndExecuteSchedules();

        // Should not have fired — endTime has passed
        verify(workflowService, never()).startWorkflow(any());
    }

    @Test
    public void testSaveSchedule_startTimeInFuture_nextRunTimeIsAtOrAfterStartTime() {
        long futureStart = System.currentTimeMillis() + 2 * 60 * 60 * 1000; // 2 hours from now
        WorkflowSchedule schedule = buildSchedule("future-start", "0 * * * * *", "UTC");
        schedule.setScheduleStartTime(futureStart);

        when(dao.getNextRunTimeInEpoch(anyString(), anyString())).thenReturn(-1L);

        service.saveSchedule(schedule);

        assertNotNull(schedule.getNextRunTime());
        assertTrue(
                "nextRunTime must be at or after scheduleStartTime",
                schedule.getNextRunTime() >= futureStart);
    }

    // =========================================================================
    // Category 3 — Catchup behaviour
    // =========================================================================

    @Test
    public void testResumeSchedule_catchupDisabled_nextRunIsInFuture() {
        long staleNextRun = System.currentTimeMillis() - 5 * 60 * 1000; // 5 min ago

        WorkflowSchedule schedule = buildSchedule("no-catchup", "0 * * * * *", "UTC");
        schedule.setPaused(true);
        schedule.setRunCatchupScheduleInstances(false);

        when(dao.findScheduleByName(anyString(), eq("no-catchup"))).thenReturn(schedule);
        when(dao.getNextRunTimeInEpoch(anyString(), eq("no-catchup"))).thenReturn(staleNextRun);

        service.resumeSchedule("no-catchup");

        verify(dao)
                .updateSchedule(
                        argThat(
                                s -> {
                                    assertFalse("Schedule should be unpaused", s.isPaused());
                                    assertNotNull(s.getNextRunTime());
                                    assertTrue(
                                            "nextRunTime should be in the future (not the stale 5-min-ago value)",
                                            s.getNextRunTime() > System.currentTimeMillis() - 2000);
                                    return true;
                                }));
    }

    @Test
    public void testResumeSchedule_catchupEnabled_nextRunPreservesStaleTime() {
        long staleNextRun = System.currentTimeMillis() - 5 * 60 * 1000; // 5 min ago

        WorkflowSchedule schedule = buildSchedule("with-catchup", "0 * * * * *", "UTC");
        schedule.setPaused(true);
        schedule.setRunCatchupScheduleInstances(true);

        when(dao.findScheduleByName(anyString(), eq("with-catchup"))).thenReturn(schedule);
        when(dao.getNextRunTimeInEpoch(anyString(), eq("with-catchup"))).thenReturn(staleNextRun);

        service.resumeSchedule("with-catchup");

        verify(dao)
                .updateSchedule(
                        argThat(
                                s -> {
                                    assertFalse("Schedule should be unpaused", s.isPaused());
                                    assertEquals(
                                            "nextRunTime should be the preserved stale value so missed slots fire",
                                            staleNextRun,
                                            (long) s.getNextRunTime());
                                    return true;
                                }));
    }

    @Test
    public void testPollAndExecute_catchupEnabled_firesForEachMissedSlotPerCycle() {
        // Three missed minute-slots: 3, 2, and 1 minutes ago
        long now = System.currentTimeMillis();
        long slot1 = now - 3 * 60 * 1000;
        long slot2 = now - 2 * 60 * 1000;
        long slot3 = now - 1 * 60 * 1000;

        WorkflowSchedule schedule = buildSchedule("catchup-sched", "0 * * * * *", "UTC");
        schedule.setRunCatchupScheduleInstances(true);

        when(dao.getAllSchedules(anyString())).thenReturn(List.of(schedule));
        when(workflowService.startWorkflow(any())).thenReturn("wf-1", "wf-2", "wf-3");

        // Each poll cycle calls getNextRunTimeInEpoch twice: once in isDue() and once in
        // handleSchedule() to capture the scheduled time. Provide 2 returns per cycle.
        when(dao.getNextRunTimeInEpoch(anyString(), eq("catchup-sched")))
                .thenReturn(slot1) // cycle 1: isDue check
                .thenReturn(slot1) // cycle 1: handleSchedule scheduledTime
                .thenReturn(slot2) // cycle 2: isDue check
                .thenReturn(slot2) // cycle 2: handleSchedule scheduledTime
                .thenReturn(slot3) // cycle 3: isDue check
                .thenReturn(slot3); // cycle 3: handleSchedule scheduledTime

        // Three poll cycles
        service.pollAndExecuteSchedules();
        service.pollAndExecuteSchedules();
        service.pollAndExecuteSchedules();

        verify(workflowService, times(3)).startWorkflow(any());
        verify(dao, times(3)).setNextRunTimeInEpoch(anyString(), eq("catchup-sched"), anyLong());
    }

    @Test
    public void testPollAndExecute_catchupDisabled_firesOnlyOnce() {
        long staleSlot = System.currentTimeMillis() - 5 * 60 * 1000;

        WorkflowSchedule schedule = buildSchedule("no-catchup-poll", "0 * * * * *", "UTC");
        schedule.setRunCatchupScheduleInstances(false);

        when(dao.getAllSchedules(anyString())).thenReturn(List.of(schedule));
        when(workflowService.startWorkflow(any())).thenReturn("wf-1");

        // After first poll, nextRunTime advances to the future — schedule is no longer due
        long futureSlot = System.currentTimeMillis() + 60 * 1000;
        when(dao.getNextRunTimeInEpoch(anyString(), eq("no-catchup-poll")))
                .thenReturn(staleSlot) // first poll: due
                .thenReturn(futureSlot); // second poll: not due yet

        service.pollAndExecuteSchedules();
        service.pollAndExecuteSchedules();

        verify(workflowService, times(1)).startWorkflow(any());
    }

    // =========================================================================
    // Helper
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
        s.setOrgId(WorkflowSchedule.DEFAULT_ORG_ID);
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
