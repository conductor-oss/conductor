/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.rest.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.service.AdminService;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.ADMIN;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@RestController
@RequestMapping(ADMIN)
public class AdminResource {

    private final AdminService adminService;

    public AdminResource(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Get all the configuration parameters")
    @GetMapping("/config")
    public Map<String, Object> getAllConfig() {
        return adminService.getAllConfig();
    }

    @GetMapping("/task/{tasktype}")
    @Operation(summary = "Get the list of pending tasks for a given task type")
    public List<Task> view(
            @PathVariable("tasktype") String taskType,
            @RequestParam(value = "start", defaultValue = "0", required = false) int start,
            @RequestParam(value = "count", defaultValue = "100", required = false) int count) {
        return adminService.getListOfPendingTask(taskType, start, count);
    }

    @PostMapping(value = "/sweep/requeue/{workflowId}", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Queue up all the running workflows for sweep")
    public String requeueSweep(@PathVariable("workflowId") String workflowId) {
        return adminService.requeueSweep(workflowId);
    }

    @PostMapping(value = "/consistency/verifyAndRepair/{workflowId}", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Verify and repair workflow consistency")
    public String verifyAndRepairWorkflowConsistency(
            @PathVariable("workflowId") String workflowId) {
        return String.valueOf(adminService.verifyAndRepairWorkflowConsistency(workflowId));
    }

    @GetMapping("/queues")
    @Operation(summary = "Get registered queues")
    public Map<String, ?> getEventQueues(
            @RequestParam(value = "verbose", defaultValue = "false", required = false)
                    boolean verbose) {
        return adminService.getEventQueues(verbose);
    }
}
