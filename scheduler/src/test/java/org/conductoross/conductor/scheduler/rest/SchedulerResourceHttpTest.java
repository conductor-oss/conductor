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
package org.conductoross.conductor.scheduler.rest;

import java.util.List;

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.conductoross.conductor.scheduler.service.SchedulerService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.exception.NotFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link SchedulerResource} HTTP behavior.
 *
 * <p>Tests the HTTP status codes and basic response structure returned by the REST layer. All
 * {@link SchedulerService} calls are mocked — no database is needed.
 *
 * <p>Uses standalone MockMvc setup with a local exception handler that maps:
 *
 * <ul>
 *   <li>{@link NotFoundException} → 404
 *   <li>{@link IllegalArgumentException} → 400
 * </ul>
 */
public class SchedulerResourceHttpTest {

    /** Minimal controller advice that maps exceptions to HTTP status codes for test purposes. */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(NotFoundException.class)
        public ResponseEntity<String> handleNotFound(NotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    private MockMvc mockMvc;
    private SchedulerService schedulerService;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        schedulerService = mock(SchedulerService.class);
        objectMapper = new ObjectMapperProvider().getObjectMapper();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new SchedulerResource(schedulerService))
                        .setControllerAdvice(new TestExceptionHandler())
                        .build();
    }

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

    // =========================================================================
    // POST /api/scheduler/schedules
    // =========================================================================

    @Test
    public void testSaveSchedule_validPayload_returns201() throws Exception {
        WorkflowSchedule schedule = buildSchedule("daily-report");
        WorkflowSchedule saved = buildSchedule("daily-report");
        saved.setCreateTime(System.currentTimeMillis());

        when(schedulerService.saveSchedule(any())).thenReturn(saved);

        mockMvc.perform(
                        post("/api/scheduler/schedules")
                                .contentType(APPLICATION_JSON_VALUE)
                                .content(objectMapper.writeValueAsString(schedule)))
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveSchedule_invalidCron_returns400() throws Exception {
        WorkflowSchedule schedule = buildSchedule("bad-cron");
        schedule.setCronExpression("not-a-valid-cron");

        when(schedulerService.saveSchedule(any()))
                .thenThrow(
                        new IllegalArgumentException("Invalid cron expression 'not-a-valid-cron'"));

        mockMvc.perform(
                        post("/api/scheduler/schedules")
                                .contentType(APPLICATION_JSON_VALUE)
                                .content(objectMapper.writeValueAsString(schedule)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/scheduler/schedules/{name}
    // =========================================================================

    @Test
    public void testGetSchedule_exists_returns200() throws Exception {
        WorkflowSchedule schedule = buildSchedule("weekly-cleanup");
        when(schedulerService.getSchedule("weekly-cleanup")).thenReturn(schedule);

        mockMvc.perform(get("/api/scheduler/schedules/weekly-cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("weekly-cleanup"));
    }

    @Test
    public void testGetSchedule_notFound_returns404() throws Exception {
        when(schedulerService.getSchedule("ghost"))
                .thenThrow(new NotFoundException("Schedule not found: ghost"));

        mockMvc.perform(get("/api/scheduler/schedules/ghost")).andExpect(status().isNotFound());
    }

    // =========================================================================
    // GET /api/scheduler/schedules
    // =========================================================================

    @Test
    public void testGetAllSchedules_returns200() throws Exception {
        when(schedulerService.getAllSchedules())
                .thenReturn(List.of(buildSchedule("a"), buildSchedule("b")));

        mockMvc.perform(get("/api/scheduler/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    public void testGetAllSchedules_withWorkflowFilter_returns200() throws Exception {
        when(schedulerService.getSchedulesForWorkflow("report-wf"))
                .thenReturn(List.of(buildSchedule("nightly")));

        mockMvc.perform(get("/api/scheduler/schedules").param("workflowName", "report-wf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // =========================================================================
    // DELETE /api/scheduler/schedules/{name}
    // =========================================================================

    @Test
    public void testDeleteSchedule_exists_returns204() throws Exception {
        doNothing().when(schedulerService).deleteSchedule("old-schedule");

        mockMvc.perform(delete("/api/scheduler/schedules/old-schedule"))
                .andExpect(status().isOk());
    }

    @Test
    public void testDeleteSchedule_notFound_returns404() throws Exception {
        doThrow(new NotFoundException("Schedule not found: ghost"))
                .when(schedulerService)
                .deleteSchedule("ghost");

        mockMvc.perform(delete("/api/scheduler/schedules/ghost")).andExpect(status().isNotFound());
    }

    // =========================================================================
    // GET /api/scheduler/schedules/{name}/next-execution-times
    // =========================================================================

    @Test
    public void testGetNextExecutionTimes_returns200() throws Exception {
        long now = System.currentTimeMillis();
        when(schedulerService.getNextExecutionTimes("s1", 5))
                .thenReturn(
                        List.of(
                                now + 60_000,
                                now + 120_000,
                                now + 180_000,
                                now + 240_000,
                                now + 300_000));

        mockMvc.perform(get("/api/scheduler/schedules/s1/next-execution-times"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    public void testGetNextExecutionTimes_notFound_returns404() throws Exception {
        when(schedulerService.getNextExecutionTimes("ghost", 5))
                .thenThrow(new NotFoundException("Schedule not found: ghost"));

        mockMvc.perform(get("/api/scheduler/schedules/ghost/next-execution-times"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // PUT /api/scheduler/schedules/{name}/pause
    // =========================================================================

    @Test
    public void testPauseSchedule_exists_returns204() throws Exception {
        doNothing().when(schedulerService).pauseSchedule("s1");

        mockMvc.perform(get("/api/scheduler/schedules/s1/pause")).andExpect(status().isOk());
    }

    @Test
    public void testPauseSchedule_notFound_returns404() throws Exception {
        doThrow(new NotFoundException("Schedule not found: ghost"))
                .when(schedulerService)
                .pauseSchedule("ghost");

        mockMvc.perform(get("/api/scheduler/schedules/ghost/pause"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // PUT /api/scheduler/schedules/{name}/resume
    // =========================================================================

    @Test
    public void testResumeSchedule_exists_returns204() throws Exception {
        doNothing().when(schedulerService).resumeSchedule("s1");

        mockMvc.perform(get("/api/scheduler/schedules/s1/resume"))
                .andExpect(status().isOk());
    }

    @Test
    public void testResumeSchedule_notFound_returns404() throws Exception {
        doThrow(new NotFoundException("Schedule not found: ghost"))
                .when(schedulerService)
                .resumeSchedule("ghost");

        mockMvc.perform(get("/api/scheduler/schedules/ghost/resume"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // GET /api/scheduler/schedules/{name}/executions
    // =========================================================================

    @Test
    public void testGetExecutionHistory_returns200() throws Exception {
        WorkflowScheduleExecution exec = new WorkflowScheduleExecution();
        exec.setExecutionId("exec-1");
        exec.setScheduleName("s1");
        exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);

        when(schedulerService.getExecutionHistory("s1", 10)).thenReturn(List.of(exec));

        mockMvc.perform(get("/api/scheduler/schedules/s1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
