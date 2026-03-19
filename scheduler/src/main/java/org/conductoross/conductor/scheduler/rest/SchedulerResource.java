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

import java.util.Arrays;
import java.util.List;

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.conductoross.conductor.scheduler.service.SchedulerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.run.SearchResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * REST API for workflow scheduling.
 *
 * <p>API surface is kept compatible with Orkes Conductor's SchedulerResource (minus tagging and
 * RBAC). OSS Conductor uses a single-tenant schema.
 */
@RestController
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
    public void saveSchedule(@RequestBody WorkflowSchedule schedule) {
        schedulerService.saveSchedule(schedule);
    }

    @GetMapping(value = "/schedules/{name}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a schedule by name")
    public WorkflowSchedule getSchedule(@PathVariable("name") String name) {
        return schedulerService.getSchedule(name);
    }

    @GetMapping(value = "/schedules", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all existing workflow schedules and optionally filter by workflow name")
    public List<WorkflowSchedule> getAllSchedules(
            @RequestParam(value = "workflowName", required = false) String workflowName) {
        if (workflowName != null && !workflowName.isBlank()) {
            return schedulerService.getSchedulesForWorkflow(workflowName);
        }
        return schedulerService.getAllSchedules();
    }

    @GetMapping(value = "/schedules/search", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Search scheduler definitions with pagination support")
    public SearchResult<WorkflowSchedule> searchSchedules(
            @RequestParam(value = "start", defaultValue = "0") int start,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "workflowName", required = false) String workflowName,
            @RequestParam(value = "name", required = false) String scheduleName,
            @RequestParam(value = "paused", required = false) Boolean paused,
            @RequestParam(value = "freeText", defaultValue = "*") String freeText) {
        List<String> sortOptions = sort != null ? Arrays.asList(sort.split(",")) : List.of();
        return schedulerService.searchSchedules(
                workflowName, scheduleName, paused, freeText, start, size, sortOptions);
    }

    @DeleteMapping("/schedules/{name}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Delete a schedule")
    public void deleteSchedule(@PathVariable("name") String name) {
        schedulerService.deleteSchedule(name);
    }

    // -------------------------------------------------------------------------
    // Pause / resume  (GET to match Orkes API)
    // -------------------------------------------------------------------------

    @GetMapping("/schedules/{name}/pause")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Pause a schedule")
    public void pauseSchedule(@PathVariable("name") String name) {
        schedulerService.pauseSchedule(name);
    }

    @GetMapping("/schedules/{name}/resume")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Resume a paused schedule")
    public void resumeSchedule(@PathVariable("name") String name) {
        schedulerService.resumeSchedule(name);
    }

    // -------------------------------------------------------------------------
    // Execution search
    // -------------------------------------------------------------------------

    @GetMapping(value = "/search/executions", produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Search for workflow schedule executions",
            description =
                    "use sort options as sort=<field>:ASC|DESC e.g. sort=startTime:DESC. "
                            + "If order is not specified, defaults to DESC.")
    public SearchResult<WorkflowScheduleExecution> searchExecutions(
            @RequestParam(value = "start", defaultValue = "0") int start,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "freeText", defaultValue = "*") String freeText,
            @RequestParam(value = "query", required = false) String query) {
        return schedulerService.searchExecutions(start, size, sort, query, freeText);
    }

    // -------------------------------------------------------------------------
    // Execution history (per-schedule)
    // -------------------------------------------------------------------------

    @GetMapping(value = "/schedules/{name}/executions", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get execution history for a schedule")
    public List<WorkflowScheduleExecution> getExecutionHistory(
            @PathVariable("name") String name,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return schedulerService.getExecutionHistory(name, limit);
    }

    // -------------------------------------------------------------------------
    // Next execution time preview
    // -------------------------------------------------------------------------

    @GetMapping(value = "/nextFewSchedules", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get list of the next few execution times for a cron expression")
    public List<Long> getNextFewSchedules(
            @RequestParam(value = "cronExpression") String cronExpression,
            @RequestParam(value = "scheduleStartTime", required = false) Long scheduleStartTime,
            @RequestParam(value = "scheduleEndTime", required = false) Long scheduleEndTime,
            @RequestParam(value = "limit", defaultValue = "3") int limit) {
        return schedulerService.getListOfNextSchedules(
                cronExpression, scheduleStartTime, scheduleEndTime, Math.min(5, limit));
    }

    @GetMapping(value = "/schedules/{name}/next-execution-times", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview the next N execution times for a named schedule (epoch millis)")
    public List<Long> getNextExecutionTimes(
            @PathVariable("name") String name,
            @RequestParam(value = "count", defaultValue = "5") int count) {
        return schedulerService.getNextExecutionTimes(name, count);
    }
}
