/*
 * Copyright 2021 Conductor Authors.
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
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

import static com.netflix.conductor.rest.config.RequestMappingConstants.TASKS;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@RestController
@RequestMapping(value = TASKS)
public class TaskResource {

    private final TaskService taskService;
    private final WorkflowService workflowService;

    public TaskResource(TaskService taskService, WorkflowService workflowService) {
        this.taskService = taskService;
        this.workflowService = workflowService;
    }

    @GetMapping("/poll/{tasktype}")
    @Operation(summary = "Poll for a task of a certain type")
    public ResponseEntity<Task> poll(
            @PathVariable("tasktype") String taskType,
            @RequestParam(value = "workerid", required = false) String workerId,
            @RequestParam(value = "domain", required = false) String domain) {
        // for backwards compatibility with 2.x client which expects a 204 when no Task is found
        return Optional.ofNullable(taskService.poll(taskType, workerId, domain))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/poll/batch/{tasktype}")
    @Operation(summary = "Batch poll for a task of a certain type")
    public ResponseEntity<List<Task>> batchPoll(
            @PathVariable("tasktype") String taskType,
            @RequestParam(value = "workerid", required = false) String workerId,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "count", defaultValue = "1") int count,
            @RequestParam(value = "timeout", defaultValue = "100") int timeout) {
        // for backwards compatibility with 2.x client which expects a 204 when no Task is found
        return Optional.ofNullable(
                        taskService.batchPoll(taskType, workerId, domain, count, timeout))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping(produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Update a task")
    public String updateTask(@RequestBody TaskResult taskResult) {
        taskService.updateTask(taskResult);
        return taskResult.getTaskId();
    }

    @PostMapping("/update-v2")
    @Operation(summary = "Update a task and return the next available task to be processed")
    public ResponseEntity<Task> updateTaskV2(@RequestBody @Valid TaskResult taskResult) {
        TaskModel updatedTask = taskService.updateTask(taskResult);
        if (updatedTask == null) {
            return ResponseEntity.noContent().build();
        }
        String taskType = updatedTask.getTaskType();
        String domain = updatedTask.getDomain();
        return poll(taskType, taskResult.getWorkerId(), domain);
    }

    @PostMapping(value = "/{workflowId}/{taskRefName}/{status}", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Update a task By Ref Name")
    public String updateTask(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("taskRefName") String taskRefName,
            @PathVariable("status") TaskResult.Status status,
            @RequestParam(value = "workerid", required = false) String workerId,
            @RequestBody Map<String, Object> output) {

        return taskService.updateTask(workflowId, taskRefName, status, workerId, output);
    }

    @PostMapping(
            value = "/{workflowId}/{taskRefName}/{status}/sync",
            produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a task By Ref Name synchronously and return the updated workflow")
    public Workflow updateTaskSync(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("taskRefName") String taskRefName,
            @PathVariable("status") TaskResult.Status status,
            @RequestParam(value = "workerid", required = false) String workerId,
            @RequestBody Map<String, Object> output) {

        Task pending = taskService.getPendingTaskForWorkflow(workflowId, taskRefName);
        if (pending == null) {
            throw new NotFoundException(
                    String.format(
                            "Found no running task %s of workflow %s to update",
                            taskRefName, workflowId));
        }

        TaskResult taskResult = new TaskResult(pending);
        taskResult.setStatus(status);
        taskResult.getOutputData().putAll(output);
        taskResult.setWorkerId(workerId);
        taskService.updateTask(taskResult);
        return workflowService.getExecutionStatus(pending.getWorkflowInstanceId(), true);
    }

    @PostMapping("/{taskId}/log")
    @Operation(summary = "Log Task Execution Details")
    public void log(@PathVariable("taskId") String taskId, @RequestBody String log) {
        taskService.log(taskId, log);
    }

    @GetMapping("/{taskId}/log")
    @Operation(summary = "Get Task Execution Logs")
    public List<TaskExecLog> getTaskLogs(@PathVariable("taskId") String taskId) {
        return taskService.getTaskLogs(taskId);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get task by Id")
    public ResponseEntity<Task> getTask(@PathVariable("taskId") String taskId) {
        // for backwards compatibility with 2.x client which expects a 204 when no Task is found
        return Optional.ofNullable(taskService.getTask(taskId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/queue/sizes")
    @Operation(summary = "Deprecated. Please use /tasks/queue/size endpoint")
    @Deprecated
    public Map<String, Integer> size(
            @RequestParam(value = "taskType", required = false) List<String> taskTypes) {
        return taskService.getTaskQueueSizes(taskTypes);
    }

    @GetMapping("/queue/size")
    @Operation(summary = "Get queue size for a task type.")
    public Integer taskDepth(
            @RequestParam("taskType") String taskType,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "isolationGroupId", required = false) String isolationGroupId,
            @RequestParam(value = "executionNamespace", required = false)
                    String executionNamespace) {
        return taskService.getTaskQueueSize(taskType, domain, executionNamespace, isolationGroupId);
    }

    @GetMapping("/queue/all/verbose")
    @Operation(summary = "Get the details about each queue")
    public Map<String, Map<String, Map<String, Long>>> allVerbose() {
        return taskService.allVerbose();
    }

    @GetMapping("/queue/all")
    @Operation(summary = "Get the details about each queue")
    public Map<String, Long> all() {
        return taskService.getAllQueueDetails();
    }

    @GetMapping("/queue/polldata")
    @Operation(summary = "Get the last poll data for a given task type")
    public List<PollData> getPollData(@RequestParam("taskType") String taskType) {
        return taskService.getPollData(taskType);
    }

    @GetMapping("/queue/polldata/all")
    @Operation(summary = "Get the last poll data for all task types")
    public List<PollData> getAllPollData() {
        return taskService.getAllPollData();
    }

    @PostMapping(value = "/queue/requeue/{taskType}", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Requeue pending tasks")
    public String requeuePendingTask(@PathVariable("taskType") String taskType) {
        return taskService.requeuePendingTask(taskType);
    }

    @Operation(
            summary = "Search for tasks based in payload and other parameters",
            description =
                    "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC."
                            + " If order is not specified, defaults to ASC")
    @GetMapping(value = "/search")
    public SearchResult<TaskSummary> search(
            @RequestParam(value = "start", defaultValue = "0", required = false) int start,
            @RequestParam(value = "size", defaultValue = "100", required = false) int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "freeText", defaultValue = "*", required = false) String freeText,
            @RequestParam(value = "query", required = false) String query) {
        return taskService.search(start, size, sort, freeText, query);
    }

    @Operation(
            summary = "Search for tasks based in payload and other parameters",
            description =
                    "use sort options as sort=<field>:ASC|DESC e.g. sort=name&sort=workflowId:DESC."
                            + " If order is not specified, defaults to ASC")
    @GetMapping(value = "/search-v2")
    public SearchResult<Task> searchV2(
            @RequestParam(value = "start", defaultValue = "0", required = false) int start,
            @RequestParam(value = "size", defaultValue = "100", required = false) int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "freeText", defaultValue = "*", required = false) String freeText,
            @RequestParam(value = "query", required = false) String query) {
        return taskService.searchV2(start, size, sort, freeText, query);
    }

    @Operation(summary = "Get the external uri where the task payload is to be stored")
    @GetMapping({"/externalstoragelocation", "external-storage-location"})
    public ExternalStorageLocation getExternalStorageLocation(
            @RequestParam("path") String path,
            @RequestParam("operation") String operation,
            @RequestParam("payloadType") String payloadType) {
        return taskService.getExternalStorageLocation(path, operation, payloadType);
    }
}
