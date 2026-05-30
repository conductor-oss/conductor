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
package io.orkes.conductor.scheduler.rest;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.run.SearchResult;

import io.orkes.conductor.scheduler.model.WorkflowSchedule;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.service.SchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * REST API for workflow scheduling.
 *
 * <p>Maps to the public API surface of {@link SchedulerService}. All endpoints are relative to
 * {@code /api/scheduler}.
 */
@RestController
@ConditionalOnBean(SchedulerService.class)
@RequestMapping("/api/scheduler")
@Tag(name = "Scheduler", description = "Workflow scheduling API")
public class SchedulerResource {

    private final SchedulerService schedulerService;

    public SchedulerResource(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @PostMapping(value = "/schedules", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Create or update a workflow schedule")
    public WorkflowSchedule createOrUpdateSchedule(@RequestBody WorkflowSchedule schedule) {
        return schedulerService.createOrUpdateWorkflowSchedule(schedule);
    }

    @GetMapping(value = "/schedules", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "List all schedules, optionally filtered by workflow name")
    public List<? extends WorkflowSchedule> getAllSchedules(
            @RequestParam(value = "workflowName", required = false) String workflowName) {
        if (workflowName != null && !workflowName.isBlank()) {
            return schedulerService.getAllSchedules(workflowName);
        }
        return schedulerService.getAllSchedules();
    }

    @GetMapping(value = "/schedules/search", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Search for workflow schedules")
    public SearchResult<? extends WorkflowSchedule> searchSchedules(
            @RequestParam(value = "workflowName", required = false) String workflowName,
            @RequestParam(value = "scheduleName", required = false) String scheduleName,
            @RequestParam(value = "paused", required = false) Boolean paused,
            @RequestParam(value = "freeText", required = false, defaultValue = "*") String freeText,
            @RequestParam(value = "start", required = false, defaultValue = "0") int start,
            @RequestParam(value = "size", required = false, defaultValue = "100") int size,
            @RequestParam(value = "sort", required = false) List<String> sortOptions) {
        return schedulerService.searchSchedules(
                workflowName, scheduleName, paused, freeText, start, size, sortOptions);
    }

    @GetMapping(value = "/schedules/{name}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a schedule by name")
    public WorkflowSchedule getSchedule(@PathVariable("name") String name) {
        return schedulerService.getSchedule(name);
    }

    @DeleteMapping("/schedules/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a schedule")
    public void deleteSchedule(@PathVariable("name") String name) {
        schedulerService.deleteSchedule(name);
    }

    // -------------------------------------------------------------------------
    // Pause / Resume
    // -------------------------------------------------------------------------

    @PutMapping("/schedules/{name}/pause")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Pause a schedule")
    public void pauseSchedule(
            @PathVariable("name") String name,
            @RequestParam(value = "reason", required = false) String reason) {
        schedulerService.pauseSchedule(name, reason);
    }

    @PutMapping("/schedules/{name}/resume")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Resume a paused schedule")
    public void resumeSchedule(@PathVariable("name") String name) {
        schedulerService.resumeSchedule(name);
    }

    // -------------------------------------------------------------------------
    // Next schedule preview
    // -------------------------------------------------------------------------

    @GetMapping(value = "/nextFewSchedules", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview the next few execution times for a cron expression")
    public List<Long> getNextFewSchedules(
            @RequestParam("cronExpression") String cronExpression,
            @RequestParam(value = "scheduleStartTime", required = false) Long scheduleStartTime,
            @RequestParam(value = "scheduleEndTime", required = false) Long scheduleEndTime,
            @RequestParam(value = "limit", required = false, defaultValue = "5") int limit) {
        return schedulerService.getListOfNextSchedules(
                cronExpression, scheduleStartTime, scheduleEndTime, limit);
    }

    // -------------------------------------------------------------------------
    // Execution search
    // -------------------------------------------------------------------------

    @GetMapping(value = "/search/executions", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Search for scheduled workflow executions")
    public SearchResult<WorkflowScheduleExecutionModel> searchScheduledExecutions(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "freeText", required = false, defaultValue = "*") String freeText,
            @RequestParam(value = "start", required = false, defaultValue = "0") int start,
            @RequestParam(value = "size", required = false, defaultValue = "100") int size,
            @RequestParam(value = "sort", required = false) List<String> sortOptions) {
        return schedulerService.searchScheduledExecutions(
                query, freeText, start, size, sortOptions);
    }
}
