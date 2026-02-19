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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * REST API for workflow scheduling.
 *
 * <p>{@code orgId} is never exposed to callers — it is always set to {@link
 * WorkflowSchedule#DEFAULT_ORG_ID} ("default") inside {@link SchedulerService}.
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
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or update a workflow schedule")
    public WorkflowSchedule saveSchedule(@RequestBody WorkflowSchedule schedule) {
        return schedulerService.saveSchedule(schedule);
    }

    @GetMapping(value = "/schedules/{name}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a schedule by name")
    public WorkflowSchedule getSchedule(@PathVariable("name") String name) {
        return schedulerService.getSchedule(name);
    }

    @GetMapping(value = "/schedules", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "List all schedules")
    public List<WorkflowSchedule> getAllSchedules(
            @RequestParam(value = "workflowName", required = false) String workflowName) {
        if (workflowName != null && !workflowName.isBlank()) {
            return schedulerService.getSchedulesForWorkflow(workflowName);
        }
        return schedulerService.getAllSchedules();
    }

    @DeleteMapping("/schedules/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a schedule")
    public void deleteSchedule(@PathVariable("name") String name) {
        schedulerService.deleteSchedule(name);
    }

    // -------------------------------------------------------------------------
    // Pause / resume
    // -------------------------------------------------------------------------

    @PutMapping("/schedules/{name}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Pause a schedule")
    public void pauseSchedule(
            @PathVariable("name") String name,
            @RequestParam(value = "reason", required = false) String reason) {
        if (reason != null) {
            schedulerService.pauseSchedule(name, reason);
        } else {
            schedulerService.pauseSchedule(name);
        }
    }

    @PutMapping("/schedules/{name}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Resume a paused schedule")
    public void resumeSchedule(@PathVariable("name") String name) {
        schedulerService.resumeSchedule(name);
    }

    // -------------------------------------------------------------------------
    // Execution history
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

    @GetMapping(value = "/schedules/{name}/next-execution-times", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview the next N execution times for a schedule (epoch millis)")
    public List<Long> getNextExecutionTimes(
            @PathVariable("name") String name,
            @RequestParam(value = "count", defaultValue = "5") int count) {
        return schedulerService.getNextExecutionTimes(name, count);
    }
}
