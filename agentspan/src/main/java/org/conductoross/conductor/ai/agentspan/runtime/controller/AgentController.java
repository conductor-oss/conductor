/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.controller;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.service.AgentDagService;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.ai.agentspan.runtime.service.PlanAndCompileTask;
import org.conductoross.conductor.common.metadata.agent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Component
@RestController
@RequestMapping({"/api/agent"})
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
public class AgentController {

    private final AgentService agentService;
    private final AgentDagService agentDagService;

    /**
     * Compile an agent configuration into an execution plan. Does not register or execute — useful
     * for inspecting the compiled definition.
     *
     * <p>Accepts either a native {@code AgentConfig} or a framework-specific config via {@code
     * AgentStartRequest} with {@code framework} + {@code rawConfig} fields.
     */
    @PostMapping("/compile")
    public CompileResponse compileAgent(@RequestBody AgentStartRequest request) {
        return agentService.compile(request);
    }

    /**
     * /dg #6: compile a plan against a PLAN_EXECUTE harness config and return the resulting
     * Conductor WorkflowDef, error string, warnings list, and stats — without dispatching the
     * SUB_WORKFLOW.
     *
     * <p>Useful for IDE tooling, plan-debug REPLs, and CI checks that validate a plan compiles
     * cleanly against a fixed agent config before deploy. Uses the same compile path the runtime
     * would, so the inspected output is byte-equal to what the real run would produce for the same
     * plan.
     */
    @PostMapping("/inspect-plan")
    public PlanAndCompileTask.InspectResult inspectPlan(@RequestBody InspectPlanRequest request) {
        return agentService.inspectPlan(request);
    }

    /**
     * Compile and register an agent definition without starting execution. This is a CI/CD
     * operation — the agent is registered on the server and can be triggered later via {@code
     * /start} or by name.
     */
    @PostMapping("/deploy")
    public AgentStartResponse deployAgent(@RequestBody AgentStartRequest request) {
        return agentService.deploy(request);
    }

    /**
     * Start an agent execution. A previously deployed agent can be selected with {@code agentName}
     * and optional {@code agentVersion}; callers that construct agents dynamically can continue to
     * supply an inline config. Returns the execution ID and agent name for tracking.
     */
    @PostMapping("/start")
    public AgentStartResponse startAgent(@RequestBody AgentStartRequest request) {
        return agentService.start(request);
    }

    /**
     * Open an SSE event stream for a running agent execution. Events include: thinking, tool_call,
     * tool_result, guardrail_pass/fail, waiting (HITL), handoff, error, done.
     *
     * <p>Supports reconnection via {@code Last-Event-ID} header — missed events are replayed from
     * an in-memory buffer.
     */
    @GetMapping(value = "/stream/{executionId}")
    public SseEmitter streamAgent(
            @PathVariable("executionId") String executionId,
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        return agentService.openStream(executionId, lastEventId);
    }

    /**
     * Respond to a pending HITL (human-in-the-loop) task. Use when a {@code waiting} SSE event is
     * received.
     *
     * <p>Body examples:
     *
     * <ul>
     *   <li>Approve: {@code {"approved": true}}
     *   <li>Reject: {@code {"approved": false, "reason": "..."}}
     *   <li>Message: {@code {"message": "..."}}
     * </ul>
     */
    @PostMapping("/{executionId}/respond")
    public void respondToAgent(
            @PathVariable("executionId") String executionId,
            @RequestBody Map<String, Object> output) {
        agentService.respond(executionId, output);
    }

    /**
     * Receive an SSE event pushed by a framework worker (LangGraph/LangChain). Always returns 200 —
     * unknown execution IDs are silently dropped.
     */
    @PostMapping("/events/{executionId}")
    public void pushFrameworkEvent(
            @PathVariable("executionId") String executionId,
            @RequestBody Map<String, Object> event) {
        agentService.pushFrameworkEvent(executionId, event);
    }

    /** List all registered agents. */
    @GetMapping("/list")
    public List<AgentSummary> listAgents() {
        return agentService.listAgents();
    }

    /**
     * Search agent executions with optional filters.
     *
     * <p>{@code classifier} (comma-separated, e.g. {@code agent}) filters on the indexed execution
     * classifier instead of enumerating agent workflow names. Requires a Conductor core whose
     * search index supports the classifier field.
     */
    @GetMapping("/executions")
    public Map<String, Object> searchAgentExecutions(
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "startTime:DESC") String sort,
            @RequestParam(name = "freeText", required = false) String freeText,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "agentName", required = false) String agentName,
            @RequestParam(name = "sessionId", required = false) String sessionId,
            @RequestParam(name = "classifier", required = false) String classifier) {
        return agentService.searchAgentExecutions(
                start, size, sort, freeText, status, agentName, null, classifier);
    }

    @GetMapping("/{name}")
    public Map<String, Object> getAgentDef(
            @PathVariable("name") String name,
            @RequestParam(name = "version", required = false) Integer version) {
        return agentService.getAgentDef(name, version);
    }

    @DeleteMapping("/{name}")
    public void deleteAgent(
            @PathVariable("name") String name,
            @RequestParam(name = "version", required = false) Integer version) {
        agentService.deleteAgent(name, version);
    }

    /** Get detailed execution status for a single agent execution. */
    @GetMapping("/executions/{executionId}")
    public AgentExecutionDetail getExecutionDetail(
            @PathVariable("executionId") String executionId) {
        return agentService.getExecutionDetail(executionId);
    }

    /** Pause a running agent execution. */
    @PutMapping("/{executionId}/pause")
    public void pauseAgent(@PathVariable("executionId") String executionId) {
        agentService.pauseAgent(executionId);
    }

    /** Resume a paused agent execution. */
    @PutMapping("/{executionId}/resume")
    public void resumeAgent(@PathVariable("executionId") String executionId) {
        agentService.resumeAgent(executionId);
    }

    /** Cancel a running agent execution. */
    @DeleteMapping("/{executionId}/cancel")
    public void cancelAgent(
            @PathVariable("executionId") String executionId,
            @RequestParam(name = "reason", required = false) String reason) {
        agentService.cancelAgent(executionId, reason);
    }

    /** Gracefully stop an agent execution (loop exits after current iteration). */
    @PostMapping("/{executionId}/stop")
    public void stopAgent(@PathVariable("executionId") String executionId) {
        agentService.stopAgent(executionId);
    }

    /** Inject a persistent signal into a running agent's context. */
    @PostMapping("/{executionId}/signal")
    public void signalAgent(
            @PathVariable("executionId") String executionId,
            @RequestBody Map<String, Object> body) {
        String message = body != null ? (String) body.getOrDefault("message", "") : "";
        agentService.signalAgent(executionId, message);
    }

    /**
     * Get the current status of an agent execution. Lightweight polling fallback when SSE is not
     * available.
     */
    @GetMapping("/{executionId}/status")
    public AgentStatusResponse getAgentStatus(@PathVariable("executionId") String executionId) {
        return agentService.getStatus(executionId);
    }

    /**
     * Get an agent execution with its full task list.
     *
     * <p>Used by the SDK for token usage collection — returns task types, output data (including
     * token counts), and sub-workflow IDs for recursive traversal into sub-agents.
     */
    @GetMapping("/execution/{executionId}")
    public AgentRun getExecution(@PathVariable("executionId") String executionId) {
        return agentService.getExecution(executionId);
    }

    /**
     * Inject a display-only task into a running execution's task list. Used by the SDK's DAG hook
     * to record tool calls in the UI. Writes directly to ExecutionDAO — does not trigger decide().
     */
    @PostMapping("/{executionId}/tasks")
    public InjectTaskResponse injectTask(
            @PathVariable("executionId") String executionId, @RequestBody InjectTaskRequest req) {
        return agentDagService.injectTask(executionId, req);
    }

    /**
     * Create a bare tracking execution for sub-agent display. The execution has no tasks in its
     * definition; tasks are injected via injectTask.
     */
    @PostMapping("/execution")
    public CreateTrackingWorkflowResponse createTrackingExecution(
            @RequestBody CreateTrackingWorkflowRequest req) {
        return agentDagService.createTrackingWorkflow(req);
    }

    /** Mark a tracking execution as COMPLETED. */
    @PostMapping("/execution/{executionId}/complete")
    public void completeTrackingExecution(
            @PathVariable("executionId") String executionId,
            @RequestBody(required = false) Map<String, Object> output) {
        agentDagService.completeTrackingWorkflow(executionId, output);
    }

    // ── Execution lifecycle (UI) ────────────────────────────────────

    /** Get full execution with tasks (Conductor Workflow object, used by UI). */
    @GetMapping("/executions/{executionId}/full")
    public Workflow getFullExecution(@PathVariable("executionId") String executionId) {
        return agentService.getFullExecution(executionId);
    }

    /** Restart a completed/failed execution. */
    @PostMapping("/executions/{executionId}/restart")
    public void restartExecution(
            @PathVariable("executionId") String executionId,
            @RequestParam(name = "useLatestDefinitions", defaultValue = "false")
                    boolean useLatestDefinitions) {
        agentService.restartExecution(executionId, useLatestDefinitions);
    }

    /** Retry a failed execution from the failed task. */
    @PostMapping("/executions/{executionId}/retry")
    public void retryExecution(
            @PathVariable("executionId") String executionId,
            @RequestParam(name = "resumeSubworkflowTasks", defaultValue = "false")
                    boolean resumeSubworkflowTasks) {
        agentService.retryExecution(executionId, resumeSubworkflowTasks);
    }

    /** Rerun execution from a specific task. */
    @PostMapping("/executions/{executionId}/rerun")
    public String rerunExecution(
            @PathVariable("executionId") String executionId,
            @RequestBody RerunWorkflowRequest request) {
        return agentService.rerunExecution(executionId, request);
    }

    /** Terminate a running execution (used by UI delete action). */
    @DeleteMapping("/executions/{executionId}")
    public void terminateExecution(
            @PathVariable("executionId") String executionId,
            @RequestParam(name = "reason", required = false) String reason) {
        agentService.cancelAgent(executionId, reason);
    }

    /**
     * Permanently delete an execution record from the database.
     *
     * <p>Unlike {@code DELETE /executions/{id}} (which terminates a running execution), this
     * endpoint removes the record entirely — equivalent to Conductor's {@code removeWorkflow}. Use
     * for storage cleanup of completed executions.
     *
     * @param executionId the execution to delete
     * @param archiveTasks if true, archive task records instead of hard-deleting (default false)
     */
    @DeleteMapping("/executions/{executionId}/record")
    public void deleteExecutionRecord(
            @PathVariable("executionId") String executionId,
            @RequestParam(name = "archiveTasks", defaultValue = "false") boolean archiveTasks) {
        agentService.deleteExecutionRecord(executionId, archiveTasks);
    }

    /**
     * Bulk-delete completed/failed execution records older than {@code olderThanDays} days.
     *
     * @param olderThanDays minimum age in days (default 30)
     * @param archiveTasks if true, archive tasks instead of hard-deleting (default false)
     * @return map with {@code deleted} count
     */
    @PostMapping("/executions/prune")
    public Map<String, Object> pruneExecutions(
            @RequestParam(name = "olderThanDays", defaultValue = "30") int olderThanDays,
            @RequestParam(name = "archiveTasks", defaultValue = "false") boolean archiveTasks) {
        int deleted = agentService.pruneExecutions(olderThanDays, archiveTasks);
        return Map.of("deleted", deleted);
    }

    /** Get paginated task list for an execution. */
    @GetMapping("/executions/{executionId}/tasks")
    public TaskListResponse getExecutionTasks(
            @PathVariable("executionId") String executionId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "count", defaultValue = "15") int count,
            @RequestParam(name = "start", defaultValue = "0") int start) {
        return agentService.getExecutionTasks(executionId, status, count, start);
    }

    /** Update a task's status within an execution. */
    @PostMapping("/tasks/{executionId}/{refTaskName}/{status}")
    public void updateTaskStatus(
            @PathVariable("executionId") String executionId,
            @PathVariable("refTaskName") String refTaskName,
            @PathVariable("status") String status,
            @RequestParam(name = "workerid", defaultValue = "agent-ui") String workerid,
            @RequestBody(required = false) Map<String, Object> body) {
        // Reuse existing task update logic
        Workflow wf = agentService.getFullExecution(executionId);
        Task task =
                wf.getTasks().stream()
                        .filter(t -> refTaskName.equals(t.getReferenceTaskName()))
                        .reduce((first, second) -> second)
                        .orElseThrow(() -> new NotFoundException("Task not found: " + refTaskName));
        TaskResult taskResult = new TaskResult(task);
        taskResult.setStatus(TaskResult.Status.valueOf(status));
        taskResult.setWorkerId(workerid);
        if (body != null) {
            taskResult.setOutputData(body);
        }
        agentService.updateTaskResult(taskResult);
    }

    /** Get task logs. */
    @GetMapping("/tasks/{taskId}/log")
    public List<TaskExecLog> getTaskLogs(@PathVariable("taskId") String taskId) {
        return agentService.getTaskLogs(taskId);
    }

    // ── Search ──────────────────────────────────────────────────────

    /**
     * Search executions (pass-through to Conductor search, used by UI). An optional {@code
     * classifier} filter (comma-separated) is folded into the query as {@code classifier IN (...)};
     * {@code topLevelOnly=true} restricts results to root executions ({@code parentWorkflowId =
     * ""}).
     */
    @GetMapping("/executions/search")
    public SearchResult<WorkflowSummary> searchExecutionsRaw(
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "startTime:DESC") String sort,
            @RequestParam(name = "freeText", required = false) String freeText,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "classifier", required = false) String classifier,
            @RequestParam(name = "topLevelOnly", required = false, defaultValue = "false")
                    boolean topLevelOnly) {
        return agentService.searchExecutionsRaw(
                start, size, sort, freeText, query, classifier, topLevelOnly);
    }

    // ── Bulk operations ─────────────────────────────────────────────

    @PutMapping("/executions/bulk/pause")
    public void bulkPause(@RequestBody List<String> ids) {
        ids.forEach(id -> agentService.pauseAgent(id));
    }

    @PutMapping("/executions/bulk/resume")
    public void bulkResume(@RequestBody List<String> ids) {
        ids.forEach(id -> agentService.resumeAgent(id));
    }

    @PostMapping("/executions/bulk/restart")
    public void bulkRestart(
            @RequestBody List<String> ids,
            @RequestParam(name = "useLatestDefinitions", defaultValue = "false")
                    boolean useLatestDefinitions) {
        ids.forEach(id -> agentService.restartExecution(id, useLatestDefinitions));
    }

    @PostMapping("/executions/bulk/retry")
    public void bulkRetry(@RequestBody List<String> ids) {
        ids.forEach(id -> agentService.retryExecution(id, false));
    }

    @PostMapping("/executions/bulk/terminate")
    public void bulkTerminate(
            @RequestBody List<String> ids,
            @RequestParam(name = "reason", required = false) String reason) {
        ids.forEach(id -> agentService.cancelAgent(id, reason));
    }

    // ── Definition metadata ─────────────────────────────────────────

    @GetMapping("/definitions/{name}")
    public WorkflowDef getAgentDefinition(
            @PathVariable("name") String name,
            @RequestParam(name = "version", required = false) Integer version) {
        return agentService.getAgentDefinition(name, version);
    }
}
