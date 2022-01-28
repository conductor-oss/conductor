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

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.service.WorkflowService;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.WORKFLOW;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@RestController
@RequestMapping(WORKFLOW)
public class WorkflowResource {

    private final WorkflowService workflowService;

    public WorkflowResource(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping(produces = TEXT_PLAIN_VALUE)
    @Operation(
            summary =
                    "Start a new workflow with StartWorkflowRequest, which allows task to be executed in a domain")
    public String startWorkflow(@RequestBody StartWorkflowRequest request) {
        return workflowService.startWorkflow(request);
    }

    @PostMapping(value = "/{name}", produces = TEXT_PLAIN_VALUE)
    @Operation(
            summary =
                    "Start a new workflow. Returns the ID of the workflow instance that can be later used for tracking")
    public String startWorkflow(
            @PathVariable("name") String name,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "correlationId", required = false) String correlationId,
            @RequestParam(value = "priority", defaultValue = "0", required = false) int priority,
            @RequestBody Map<String, Object> input) {
        return workflowService.startWorkflow(name, version, correlationId, priority, input);
    }

    @GetMapping("/{name}/correlated/{correlationId}")
    @Operation(summary = "Lists workflows for the given correlation id")
    public List<Workflow> getWorkflows(
            @PathVariable("name") String name,
            @PathVariable("correlationId") String correlationId,
            @RequestParam(value = "includeClosed", defaultValue = "false", required = false)
                    boolean includeClosed,
            @RequestParam(value = "includeTasks", defaultValue = "false", required = false)
                    boolean includeTasks) {
        return workflowService.getWorkflows(name, correlationId, includeClosed, includeTasks);
    }

    @PostMapping(value = "/{name}/correlated")
    @Operation(summary = "Lists workflows for the given correlation id list")
    public Map<String, List<Workflow>> getWorkflows(
            @PathVariable("name") String name,
            @RequestParam(value = "includeClosed", defaultValue = "false", required = false)
                    boolean includeClosed,
            @RequestParam(value = "includeTasks", defaultValue = "false", required = false)
                    boolean includeTasks,
            @RequestBody List<String> correlationIds) {
        return workflowService.getWorkflows(name, includeClosed, includeTasks, correlationIds);
    }

    @GetMapping("/{workflowId}")
    @Operation(summary = "Gets the workflow by workflow id")
    public Workflow getExecutionStatus(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "includeTasks", defaultValue = "true", required = false)
                    boolean includeTasks) {
        return workflowService.getExecutionStatus(workflowId, includeTasks);
    }

    @DeleteMapping("/{workflowId}/remove")
    @Operation(summary = "Removes the workflow from the system")
    public void delete(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "archiveWorkflow", defaultValue = "true", required = false)
                    boolean archiveWorkflow) {
        workflowService.deleteWorkflow(workflowId, archiveWorkflow);
    }

    @GetMapping("/running/{name}")
    @Operation(summary = "Retrieve all the running workflows")
    public List<String> getRunningWorkflow(
            @PathVariable("name") String workflowName,
            @RequestParam(value = "version", defaultValue = "1", required = false) int version,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime) {
        return workflowService.getRunningWorkflows(workflowName, version, startTime, endTime);
    }

    @PutMapping("/decide/{workflowId}")
    @Operation(summary = "Starts the decision task for a workflow")
    public void decide(@PathVariable("workflowId") String workflowId) {
        workflowService.decideWorkflow(workflowId);
    }

    @PutMapping("/{workflowId}/pause")
    @Operation(summary = "Pauses the workflow")
    public void pauseWorkflow(@PathVariable("workflowId") String workflowId) {
        workflowService.pauseWorkflow(workflowId);
    }

    @PutMapping("/{workflowId}/resume")
    @Operation(summary = "Resumes the workflow")
    public void resumeWorkflow(@PathVariable("workflowId") String workflowId) {
        workflowService.resumeWorkflow(workflowId);
    }

    @PutMapping("/{workflowId}/skiptask/{taskReferenceName}")
    @Operation(summary = "Skips a given task from a current running workflow")
    public void skipTaskFromWorkflow(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("taskReferenceName") String taskReferenceName,
            SkipTaskRequest skipTaskRequest) {
        workflowService.skipTaskFromWorkflow(workflowId, taskReferenceName, skipTaskRequest);
    }

    @PostMapping(value = "/{workflowId}/rerun", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Reruns the workflow from a specific task")
    public String rerun(
            @PathVariable("workflowId") String workflowId,
            @RequestBody RerunWorkflowRequest request) {
        return workflowService.rerunWorkflow(workflowId, request);
    }

    @PostMapping("/{workflowId}/restart")
    @Operation(summary = "Restarts a completed workflow")
    @ResponseStatus(
            value = HttpStatus.NO_CONTENT) // for backwards compatibility with 2.x client which
    // expects a 204 for this request
    public void restart(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "useLatestDefinitions", defaultValue = "false", required = false)
                    boolean useLatestDefinitions) {
        workflowService.restartWorkflow(workflowId, useLatestDefinitions);
    }

    @PostMapping("/{workflowId}/retry")
    @Operation(summary = "Retries the last failed task")
    @ResponseStatus(
            value = HttpStatus.NO_CONTENT) // for backwards compatibility with 2.x client which
    // expects a 204 for this request
    public void retry(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(
                            value = "resumeSubworkflowTasks",
                            defaultValue = "false",
                            required = false)
                    boolean resumeSubworkflowTasks) {
        workflowService.retryWorkflow(workflowId, resumeSubworkflowTasks);
    }

    @PostMapping("/{workflowId}/resetcallbacks")
    @Operation(summary = "Resets callback times of all non-terminal SIMPLE tasks to 0")
    @ResponseStatus(
            value = HttpStatus.NO_CONTENT) // for backwards compatibility with 2.x client which
    // expects a 204 for this request
    public void resetWorkflow(@PathVariable("workflowId") String workflowId) {
        workflowService.resetWorkflow(workflowId);
    }

    @DeleteMapping("/{workflowId}")
    @Operation(summary = "Terminate workflow execution")
    public void terminate(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "reason", required = false) String reason) {
        workflowService.terminateWorkflow(workflowId, reason);
    }

    @Operation(
            summary = "Search for workflows based on payload and other parameters",
            description =
                    "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC."
                            + " If order is not specified, defaults to ASC.")
    @GetMapping(value = "/search")
    public SearchResult<WorkflowSummary> search(
            @RequestParam(value = "start", defaultValue = "0", required = false) int start,
            @RequestParam(value = "size", defaultValue = "100", required = false) int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "freeText", defaultValue = "*", required = false) String freeText,
            @RequestParam(value = "query", required = false) String query) {
        return workflowService.searchWorkflows(start, size, sort, freeText, query);
    }

    @Operation(
            summary = "Search for workflows based on payload and other parameters",
            description =
                    "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC."
                            + " If order is not specified, defaults to ASC.")
    @GetMapping(value = "/search-v2")
    public SearchResult<Workflow> searchV2(
            @RequestParam(value = "start", defaultValue = "0", required = false) int start,
            @RequestParam(value = "size", defaultValue = "100", required = false) int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "freeText", defaultValue = "*", required = false) String freeText,
            @RequestParam(value = "query", required = false) String query) {
        return workflowService.searchWorkflowsV2(start, size, sort, freeText, query);
    }

    @Operation(
            summary = "Search for workflows based on task parameters",
            description =
                    "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC."
                            + " If order is not specified, defaults to ASC")
    @GetMapping(value = "/search-by-tasks")
    public SearchResult<WorkflowSummary> searchWorkflowsByTasks(
            @RequestParam(value = "start", defaultValue = "0", required = false) int start,
            @RequestParam(value = "size", defaultValue = "100", required = false) int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "freeText", defaultValue = "*", required = false) String freeText,
            @RequestParam(value = "query", required = false) String query) {
        return workflowService.searchWorkflowsByTasks(start, size, sort, freeText, query);
    }

    @Operation(
            summary = "Search for workflows based on task parameters",
            description =
                    "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC."
                            + " If order is not specified, defaults to ASC")
    @GetMapping(value = "/search-by-tasks-v2")
    public SearchResult<Workflow> searchWorkflowsByTasksV2(
            @RequestParam(value = "start", defaultValue = "0", required = false) int start,
            @RequestParam(value = "size", defaultValue = "100", required = false) int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "freeText", defaultValue = "*", required = false) String freeText,
            @RequestParam(value = "query", required = false) String query) {
        return workflowService.searchWorkflowsByTasksV2(start, size, sort, freeText, query);
    }

    @Operation(
            summary =
                    "Get the uri and path of the external storage where the workflow payload is to be stored")
    @GetMapping("/externalstoragelocation")
    public ExternalStorageLocation getExternalStorageLocation(
            @RequestParam("path") String path,
            @RequestParam("operation") String operation,
            @RequestParam("payloadType") String payloadType) {
        return workflowService.getExternalStorageLocation(path, operation, payloadType);
    }
}
