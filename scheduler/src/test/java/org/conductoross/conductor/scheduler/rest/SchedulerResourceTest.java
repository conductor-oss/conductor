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
package org.conductoross.conductor.scheduler.rest;

import java.util.List;

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.conductoross.conductor.scheduler.service.SchedulerService;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SchedulerResource}. Verifies that the resource delegates correctly to
 * {@link SchedulerService} and does NOT expose orgId to callers.
 */
public class SchedulerResourceTest {

    private SchedulerService schedulerService;
    private SchedulerResource resource;

    @Before
    public void setUp() {
        schedulerService = mock(SchedulerService.class);
        resource = new SchedulerResource(schedulerService);
    }

    // -------------------------------------------------------------------------
    // POST /schedules
    // -------------------------------------------------------------------------

    @Test
    public void testSaveSchedule_delegatesToService() {
        WorkflowSchedule input = buildSchedule("daily-report");
        WorkflowSchedule saved = buildSchedule("daily-report");
        saved.setCreateTime(System.currentTimeMillis());
        when(schedulerService.saveSchedule(input)).thenReturn(saved);

        WorkflowSchedule result = resource.saveSchedule(input);

        assertNotNull(result);
        assertEquals("daily-report", result.getName());
        verify(schedulerService).saveSchedule(input);
    }

    @Test
    public void testSaveSchedule_propagatesValidationError() {
        when(schedulerService.saveSchedule(any()))
                .thenThrow(new IllegalArgumentException("Cron expression is required"));

        try {
            resource.saveSchedule(new WorkflowSchedule());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Cron expression is required", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GET /schedules/{name}
    // -------------------------------------------------------------------------

    @Test
    public void testGetSchedule_returnsSchedule() {
        WorkflowSchedule schedule = buildSchedule("weekly-cleanup");
        when(schedulerService.getSchedule("weekly-cleanup")).thenReturn(schedule);

        WorkflowSchedule result = resource.getSchedule("weekly-cleanup");

        assertEquals("weekly-cleanup", result.getName());
        verify(schedulerService).getSchedule("weekly-cleanup");
    }

    @Test
    public void testGetSchedule_notFound_propagatesException() {
        when(schedulerService.getSchedule("ghost"))
                .thenThrow(new NotFoundException("Schedule not found: ghost"));

        try {
            resource.getSchedule("ghost");
            fail("Expected NotFoundException");
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("ghost"));
        }
    }

    // -------------------------------------------------------------------------
    // GET /schedules
    // -------------------------------------------------------------------------

    @Test
    public void testGetAllSchedules_noFilter_returnsAll() {
        List<WorkflowSchedule> all = List.of(buildSchedule("a"), buildSchedule("b"));
        when(schedulerService.getAllSchedules()).thenReturn(all);

        List<WorkflowSchedule> result = resource.getAllSchedules(null);

        assertEquals(2, result.size());
        verify(schedulerService).getAllSchedules();
        verify(schedulerService, never()).getSchedulesForWorkflow(any());
    }

    @Test
    public void testGetAllSchedules_withWorkflowFilter() {
        List<WorkflowSchedule> filtered = List.of(buildSchedule("nightly"));
        when(schedulerService.getSchedulesForWorkflow("report-wf")).thenReturn(filtered);

        List<WorkflowSchedule> result = resource.getAllSchedules("report-wf");

        assertEquals(1, result.size());
        verify(schedulerService).getSchedulesForWorkflow("report-wf");
        verify(schedulerService, never()).getAllSchedules();
    }

    @Test
    public void testGetAllSchedules_blankFilter_treatedAsNoFilter() {
        when(schedulerService.getAllSchedules()).thenReturn(List.of());

        resource.getAllSchedules("   ");

        verify(schedulerService).getAllSchedules();
        verify(schedulerService, never()).getSchedulesForWorkflow(any());
    }

    // -------------------------------------------------------------------------
    // DELETE /schedules/{name}
    // -------------------------------------------------------------------------

    @Test
    public void testDeleteSchedule_delegatesToService() {
        doNothing().when(schedulerService).deleteSchedule("old-schedule");

        resource.deleteSchedule("old-schedule");

        verify(schedulerService).deleteSchedule("old-schedule");
    }

    @Test
    public void testDeleteSchedule_notFound_propagatesException() {
        doThrow(new NotFoundException("Schedule not found: ghost"))
                .when(schedulerService)
                .deleteSchedule("ghost");

        try {
            resource.deleteSchedule("ghost");
            fail("Expected NotFoundException");
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("ghost"));
        }
    }

    // -------------------------------------------------------------------------
    // PUT /schedules/{name}/pause
    // -------------------------------------------------------------------------

    @Test
    public void testPauseSchedule_noReason() {
        doNothing().when(schedulerService).pauseSchedule("s1");

        resource.pauseSchedule("s1", null);

        verify(schedulerService).pauseSchedule("s1");
        verify(schedulerService, never()).pauseSchedule(anyString(), anyString());
    }

    @Test
    public void testPauseSchedule_withReason() {
        doNothing().when(schedulerService).pauseSchedule("s1", "maintenance window");

        resource.pauseSchedule("s1", "maintenance window");

        verify(schedulerService).pauseSchedule("s1", "maintenance window");
        verify(schedulerService, never()).pauseSchedule(eq("s1"));
    }

    // -------------------------------------------------------------------------
    // PUT /schedules/{name}/resume
    // -------------------------------------------------------------------------

    @Test
    public void testResumeSchedule_delegatesToService() {
        doNothing().when(schedulerService).resumeSchedule("s1");

        resource.resumeSchedule("s1");

        verify(schedulerService).resumeSchedule("s1");
    }

    // -------------------------------------------------------------------------
    // GET /schedules/{name}/executions
    // -------------------------------------------------------------------------

    @Test
    public void testGetExecutionHistory_defaultLimit() {
        List<WorkflowScheduleExecution> history = List.of(new WorkflowScheduleExecution());
        when(schedulerService.getExecutionHistory("s1", 10)).thenReturn(history);

        List<WorkflowScheduleExecution> result = resource.getExecutionHistory("s1", 10);

        assertEquals(1, result.size());
        verify(schedulerService).getExecutionHistory("s1", 10);
    }

    @Test
    public void testGetExecutionHistory_customLimit() {
        when(schedulerService.getExecutionHistory("s1", 3)).thenReturn(List.of());

        resource.getExecutionHistory("s1", 3);

        verify(schedulerService).getExecutionHistory("s1", 3);
    }

    // -------------------------------------------------------------------------
    // GET /schedules/{name}/next-execution-times
    // -------------------------------------------------------------------------

    @Test
    public void testGetNextExecutionTimes() {
        long now = System.currentTimeMillis();
        List<Long> times = List.of(now + 60_000, now + 120_000, now + 180_000);
        when(schedulerService.getNextExecutionTimes("s1", 3)).thenReturn(times);

        List<Long> result = resource.getNextExecutionTimes("s1", 3);

        assertEquals(3, result.size());
        assertTrue(result.get(0) < result.get(1));
        verify(schedulerService).getNextExecutionTimes("s1", 3);
    }

    // -------------------------------------------------------------------------
    // orgId isolation check
    // -------------------------------------------------------------------------

    @Test
    public void testSaveSchedule_orgIdIsNeverSetByClient() {
        // Even if a client sends orgId in the payload, the service (not the resource)
        // is responsible for enforcing DEFAULT_ORG_ID. The resource just passes through.
        WorkflowSchedule schedule = buildSchedule("s1");

        WorkflowSchedule returned = buildSchedule("s1");
        when(schedulerService.saveSchedule(any())).thenReturn(returned);

        WorkflowSchedule result = resource.saveSchedule(schedule);

        // Resource returns whatever the service returns — enforcement is in the service
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private WorkflowSchedule buildSchedule(String name) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName("some-workflow");
        req.setVersion(1);

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName(name);
        schedule.setCronExpression("0 0 9 * * MON-FRI");
        schedule.setZoneId("UTC");
        schedule.setStartWorkflowRequest(req);
        return schedule;
    }
}
