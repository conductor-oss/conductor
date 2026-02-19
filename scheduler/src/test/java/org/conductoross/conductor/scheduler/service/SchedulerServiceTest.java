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

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.conductoross.conductor.scheduler.config.SchedulerProperties;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for {@link SchedulerService}. Uses Mockito — no database or Docker required. */
public class SchedulerServiceTest {

    private SchedulerDAO schedulerDAO;
    private WorkflowService workflowService;
    private SchedulerProperties properties;
    private SchedulerService service;

    @Before
    public void setUp() {
        schedulerDAO = mock(SchedulerDAO.class);
        workflowService = mock(WorkflowService.class);
        properties = new SchedulerProperties();
        // Don't start the polling loop in unit tests
        properties.setEnabled(false);
        service = new SchedulerService(schedulerDAO, workflowService, properties);
    }

    // -------------------------------------------------------------------------
    // saveSchedule
    // -------------------------------------------------------------------------

    @Test
    public void testSaveSchedule_setsOrgIdAndTimestamps() {
        WorkflowSchedule schedule = buildSchedule("s1", "*/5 * * * * *");
        when(schedulerDAO.findScheduleByName(anyString(), eq("s1"))).thenReturn(null);

        WorkflowSchedule saved = service.saveSchedule(schedule);

        assertEquals(WorkflowSchedule.DEFAULT_ORG_ID, saved.getOrgId());
        assertNotNull(saved.getCreateTime());
        assertNotNull(saved.getUpdatedTime());
        assertNotNull(saved.getNextRunTime());
        verify(schedulerDAO).updateSchedule(schedule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_missingName_throws() {
        WorkflowSchedule schedule = buildSchedule(null, "0 0 * * * *");
        service.saveSchedule(schedule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_invalidCron_throws() {
        WorkflowSchedule schedule = buildSchedule("bad-cron", "not-a-cron");
        service.saveSchedule(schedule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSchedule_missingStartWorkflowRequest_throws() {
        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName("no-req");
        schedule.setCronExpression("0 0 * * * *");
        service.saveSchedule(schedule);
    }

    // -------------------------------------------------------------------------
    // getSchedule
    // -------------------------------------------------------------------------

    @Test
    public void testGetSchedule_found() {
        WorkflowSchedule schedule = buildSchedule("s1", "0 0 9 * * *");
        when(schedulerDAO.findScheduleByName(WorkflowSchedule.DEFAULT_ORG_ID, "s1"))
                .thenReturn(schedule);

        WorkflowSchedule found = service.getSchedule("s1");
        assertEquals("s1", found.getName());
    }

    @Test(expected = NotFoundException.class)
    public void testGetSchedule_notFound_throws() {
        when(schedulerDAO.findScheduleByName(anyString(), eq("ghost"))).thenReturn(null);
        service.getSchedule("ghost");
    }

    // -------------------------------------------------------------------------
    // pause / resume
    // -------------------------------------------------------------------------

    @Test
    public void testPauseSchedule() {
        WorkflowSchedule schedule = buildSchedule("p1", "0 0 * * * *");
        when(schedulerDAO.findScheduleByName(WorkflowSchedule.DEFAULT_ORG_ID, "p1"))
                .thenReturn(schedule);

        service.pauseSchedule("p1", "maintenance");

        assertTrue(schedule.isPaused());
        assertEquals("maintenance", schedule.getPausedReason());
        verify(schedulerDAO).updateSchedule(schedule);
    }

    @Test
    public void testResumeSchedule() {
        WorkflowSchedule schedule = buildSchedule("r1", "0 0 * * * *");
        schedule.setPaused(true);
        schedule.setPausedReason("testing");
        when(schedulerDAO.findScheduleByName(WorkflowSchedule.DEFAULT_ORG_ID, "r1"))
                .thenReturn(schedule);

        service.resumeSchedule("r1");

        assertFalse(schedule.isPaused());
        assertNull(schedule.getPausedReason());
        verify(schedulerDAO).updateSchedule(schedule);
    }

    // -------------------------------------------------------------------------
    // deleteSchedule
    // -------------------------------------------------------------------------

    @Test
    public void testDeleteSchedule() {
        WorkflowSchedule schedule = buildSchedule("del", "0 0 * * * *");
        when(schedulerDAO.findScheduleByName(WorkflowSchedule.DEFAULT_ORG_ID, "del"))
                .thenReturn(schedule);

        service.deleteSchedule("del");

        verify(schedulerDAO).deleteWorkflowSchedule(WorkflowSchedule.DEFAULT_ORG_ID, "del");
    }

    @Test(expected = NotFoundException.class)
    public void testDeleteSchedule_notFound_throws() {
        when(schedulerDAO.findScheduleByName(anyString(), anyString())).thenReturn(null);
        service.deleteSchedule("ghost");
    }

    // -------------------------------------------------------------------------
    // computeNextRunTime
    // -------------------------------------------------------------------------

    @Test
    public void testComputeNextRunTime_returnsFirstFutureRun() {
        WorkflowSchedule schedule = buildSchedule("nrt", "0 0 9 * * *"); // daily at 9am
        schedule.setZoneId("UTC");

        // Set afterEpochMillis to midnight UTC so next run should be 9am same day
        ZonedDateTime midnight =
                ZonedDateTime.now(java.time.ZoneId.of("UTC"))
                        .withHour(0)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
        long afterMillis = midnight.toInstant().toEpochMilli();

        Long next = service.computeNextRunTime(schedule, afterMillis);

        assertNotNull(next);
        assertTrue(next > afterMillis);
        // 9am UTC = midnight + 9 hours = 32400 seconds from midnight
        long nineAmMillis = midnight.withHour(9).toInstant().toEpochMilli();
        assertEquals(nineAmMillis, next.longValue());
    }

    @Test
    public void testComputeNextRunTime_respectsEndTime() {
        WorkflowSchedule schedule = buildSchedule("end", "0 0 9 * * *");
        schedule.setZoneId("UTC");
        // End time 1 second in the past. Using now as afterEpochMillis guarantees the
        // next cron occurrence (9am, at least seconds away) is always after endTime,
        // so the method must return null. Using (now - 1 hour) was flaky because a past
        // 9am landing between (now - 1 hour) and endTime would not trigger the null path.
        schedule.setScheduleEndTime(System.currentTimeMillis() - 1000);

        Long next = service.computeNextRunTime(schedule, System.currentTimeMillis());
        assertNull(next); // no future runs within bounds
    }

    @Test
    public void testComputeNextRunTime_respectsStartTime() {
        WorkflowSchedule schedule = buildSchedule("start", "0 0 9 * * *");
        schedule.setZoneId("UTC");
        // Start time far in the future
        long farFuture = System.currentTimeMillis() + 365L * 24 * 3600 * 1000;
        schedule.setScheduleStartTime(farFuture);

        Long next = service.computeNextRunTime(schedule, System.currentTimeMillis());

        assertNotNull(next);
        assertTrue(next >= farFuture);
    }

    @Test
    public void testComputeNextRunTime_invalidCron_returnsNull() {
        WorkflowSchedule schedule = buildSchedule("bad", "not-valid-cron");
        Long next = service.computeNextRunTime(schedule, System.currentTimeMillis());
        assertNull(next);
    }

    // -------------------------------------------------------------------------
    // getNextExecutionTimes
    // -------------------------------------------------------------------------

    @Test
    public void testGetNextExecutionTimes() {
        WorkflowSchedule schedule = buildSchedule("next", "0 */5 * * * *"); // every 5 min
        schedule.setZoneId("UTC");
        when(schedulerDAO.findScheduleByName(WorkflowSchedule.DEFAULT_ORG_ID, "next"))
                .thenReturn(schedule);

        List<Long> times = service.getNextExecutionTimes("next", 3);

        assertEquals(3, times.size());
        // Each successive time should be >= previous
        assertTrue(times.get(1) > times.get(0));
        assertTrue(times.get(2) > times.get(1));
        // Should be ~5 minutes apart
        long diff = times.get(1) - times.get(0);
        assertTrue(diff >= 4 * 60 * 1000 && diff <= 6 * 60 * 1000);
    }

    // -------------------------------------------------------------------------
    // pollAndExecuteSchedules
    // -------------------------------------------------------------------------

    @Test
    public void testPollAndExecute_skips_paused() {
        WorkflowSchedule paused = buildSchedule("paused", "0 0 * * * *");
        paused.setPaused(true);
        when(schedulerDAO.getAllSchedules(WorkflowSchedule.DEFAULT_ORG_ID))
                .thenReturn(List.of(paused));
        when(schedulerDAO.getPendingExecutionRecordIds(anyString()))
                .thenReturn(Collections.emptyList());

        service.pollAndExecuteSchedules();

        verify(workflowService, never()).startWorkflow(any());
    }

    @Test
    public void testPollAndExecute_triggers_due_schedule() {
        WorkflowSchedule schedule = buildSchedule("due", "* * * * * *"); // every second
        long now = System.currentTimeMillis();
        long pastRun = now - 2000; // was due 2 seconds ago

        when(schedulerDAO.getAllSchedules(WorkflowSchedule.DEFAULT_ORG_ID))
                .thenReturn(List.of(schedule));
        when(schedulerDAO.getNextRunTimeInEpoch(WorkflowSchedule.DEFAULT_ORG_ID, "due"))
                .thenReturn(pastRun); // it's due
        when(workflowService.startWorkflow(any())).thenReturn("wf-id-123");
        when(schedulerDAO.getExecutionRecords(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(schedulerDAO.getPendingExecutionRecordIds(anyString()))
                .thenReturn(Collections.emptyList());

        service.pollAndExecuteSchedules();

        verify(workflowService).startWorkflow(any(StartWorkflowRequest.class));
        // Execution record saved twice: once for POLLED, once for EXECUTED
        verify(schedulerDAO, times(2)).saveExecutionRecord(any());
    }

    @Test
    public void testPollAndExecute_records_failure() {
        WorkflowSchedule schedule = buildSchedule("fail", "* * * * * *");
        long now = System.currentTimeMillis();

        when(schedulerDAO.getAllSchedules(WorkflowSchedule.DEFAULT_ORG_ID))
                .thenReturn(List.of(schedule));
        when(schedulerDAO.getNextRunTimeInEpoch(WorkflowSchedule.DEFAULT_ORG_ID, "fail"))
                .thenReturn(now - 1000);
        when(workflowService.startWorkflow(any()))
                .thenThrow(new RuntimeException("workflow definition not found"));
        when(schedulerDAO.getExecutionRecords(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(schedulerDAO.getPendingExecutionRecordIds(anyString()))
                .thenReturn(Collections.emptyList());

        service.pollAndExecuteSchedules();

        // Should still save the FAILED execution record
        verify(schedulerDAO, times(2))
                .saveExecutionRecord(
                        argThat(
                                exec ->
                                        exec.getState() == null
                                                || exec.getState()
                                                        == WorkflowScheduleExecution.ExecutionState
                                                                .POLLED
                                                || exec.getState()
                                                        == WorkflowScheduleExecution.ExecutionState
                                                                .FAILED));
    }

    @Test
    public void testPollAndExecute_skips_before_startTime() {
        WorkflowSchedule schedule = buildSchedule("future", "* * * * * *");
        schedule.setScheduleStartTime(System.currentTimeMillis() + 3_600_000); // 1 hour from now

        when(schedulerDAO.getAllSchedules(WorkflowSchedule.DEFAULT_ORG_ID))
                .thenReturn(List.of(schedule));
        when(schedulerDAO.getNextRunTimeInEpoch(anyString(), anyString()))
                .thenReturn(System.currentTimeMillis() - 1000);
        when(schedulerDAO.getPendingExecutionRecordIds(anyString()))
                .thenReturn(Collections.emptyList());

        service.pollAndExecuteSchedules();

        verify(workflowService, never()).startWorkflow(any());
    }

    @Test
    public void testPollAndExecute_skips_after_endTime() {
        WorkflowSchedule schedule = buildSchedule("expired", "* * * * * *");
        schedule.setScheduleEndTime(System.currentTimeMillis() - 1000); // expired 1 second ago

        when(schedulerDAO.getAllSchedules(WorkflowSchedule.DEFAULT_ORG_ID))
                .thenReturn(List.of(schedule));
        when(schedulerDAO.getNextRunTimeInEpoch(anyString(), anyString()))
                .thenReturn(System.currentTimeMillis() - 2000);
        when(schedulerDAO.getPendingExecutionRecordIds(anyString()))
                .thenReturn(Collections.emptyList());

        service.pollAndExecuteSchedules();

        verify(workflowService, never()).startWorkflow(any());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private WorkflowSchedule buildSchedule(String name, String cron) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName("test-workflow");
        req.setVersion(1);

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName(name);
        schedule.setCronExpression(cron);
        schedule.setZoneId("UTC");
        schedule.setStartWorkflowRequest(req);
        return schedule;
    }
}
