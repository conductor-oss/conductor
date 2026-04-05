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
package org.conductoross.conductor.mcp.controller;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.service.WorkflowService;

@Component
public class WorkflowMcpTools {

    private final WorkflowService workflowService;

    public WorkflowMcpTools(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Tool(description = "Start a new workflow execution. Returns the workflow instance ID.")
    public String startWorkflow(
            @ToolParam(description = "Name of the workflow definition") String name,
            @ToolParam(description = "Version of the workflow definition", required = false)
                    Integer version,
            @ToolParam(
                            description = "Correlation ID for tracking related workflows",
                            required = false)
                    String correlationId,
            @ToolParam(description = "Priority of the workflow (0-99)", required = false)
                    Integer priority,
            @ToolParam(
                            description = "Input parameters for the workflow as key-value pairs",
                            required = false)
                    Map<String, Object> input) {
        return workflowService.startWorkflow(
                name, version, correlationId, priority != null ? priority : 0, input);
    }

    @Tool(
            description =
                    "Get workflow execution status by workflow ID. Returns full workflow details including tasks.")
    public Workflow getWorkflow(
            @ToolParam(description = "The workflow instance ID") String workflowId,
            @ToolParam(
                            description = "Whether to include task details in the response",
                            required = false)
                    Boolean includeTasks) {
        return workflowService.getExecutionStatus(
                workflowId, includeTasks != null ? includeTasks : true);
    }

    @Tool(description = "Pause a running workflow.")
    public String pauseWorkflow(
            @ToolParam(description = "The workflow instance ID to pause") String workflowId) {
        workflowService.pauseWorkflow(workflowId);
        return "Workflow " + workflowId + " paused";
    }

    @Tool(description = "Resume a paused workflow.")
    public String resumeWorkflow(
            @ToolParam(description = "The workflow instance ID to resume") String workflowId) {
        workflowService.resumeWorkflow(workflowId);
        return "Workflow " + workflowId + " resumed";
    }

    @Tool(description = "Terminate a running workflow.")
    public String terminateWorkflow(
            @ToolParam(description = "The workflow instance ID to terminate") String workflowId,
            @ToolParam(description = "Reason for terminating the workflow", required = false)
                    String reason) {
        workflowService.terminateWorkflow(workflowId, reason);
        return "Workflow " + workflowId + " terminated";
    }

    @Tool(description = "Restart a completed workflow from the beginning.")
    public String restartWorkflow(
            @ToolParam(description = "The workflow instance ID to restart") String workflowId,
            @ToolParam(
                            description = "Whether to use the latest workflow definition",
                            required = false)
                    Boolean useLatestDefinitions) {
        workflowService.restartWorkflow(
                workflowId, useLatestDefinitions != null ? useLatestDefinitions : false);
        return "Workflow " + workflowId + " restarted";
    }

    @Tool(description = "Retry the last failed task in a workflow.")
    public String retryWorkflow(
            @ToolParam(description = "The workflow instance ID to retry") String workflowId,
            @ToolParam(description = "Whether to resume sub-workflow tasks", required = false)
                    Boolean resumeSubworkflowTasks) {
        workflowService.retryWorkflow(
                workflowId, resumeSubworkflowTasks != null ? resumeSubworkflowTasks : false);
        return "Workflow " + workflowId + " retried";
    }

    @Tool(description = "Remove a workflow from the system.")
    public String deleteWorkflow(
            @ToolParam(description = "The workflow instance ID to remove") String workflowId,
            @ToolParam(
                            description = "Whether to archive instead of permanently deleting",
                            required = false)
                    Boolean archiveWorkflow) {
        workflowService.deleteWorkflow(
                workflowId, archiveWorkflow != null ? archiveWorkflow : true);
        return "Workflow " + workflowId + " removed";
    }

    @Tool(description = "Get all running workflow IDs for a given workflow name.")
    public List<String> getRunningWorkflows(
            @ToolParam(description = "The workflow definition name") String workflowName,
            @ToolParam(description = "The workflow definition version", required = false)
                    Integer version) {
        return workflowService.getRunningWorkflows(
                workflowName, version != null ? version : 1, null, null);
    }

    @Tool(
            description =
                    "Search for workflows. Use query syntax like 'status=RUNNING' or 'workflowType=myWorkflow'. Returns workflow summaries with pagination.")
    public SearchResult<WorkflowSummary> searchWorkflows(
            @ToolParam(
                            description =
                                    "Search query (e.g. 'status=RUNNING AND workflowType=myWorkflow')",
                            required = false)
                    String query,
            @ToolParam(description = "Free text search", required = false) String freeText,
            @ToolParam(description = "Start index for pagination (default 0)", required = false)
                    Integer start,
            @ToolParam(
                            description = "Number of results to return (default 100, max 5000)",
                            required = false)
                    Integer size) {
        return workflowService.searchWorkflows(
                start != null ? start : 0,
                size != null ? size : 100,
                "updateTime:DESC",
                freeText != null ? freeText : "*",
                query != null ? query : "");
    }

    @Tool(description = "Get workflows by correlation ID.")
    public List<Workflow> getWorkflowsByCorrelationId(
            @ToolParam(description = "The workflow definition name") String name,
            @ToolParam(description = "The correlation ID to search for") String correlationId,
            @ToolParam(description = "Include completed/failed workflows", required = false)
                    Boolean includeClosed,
            @ToolParam(description = "Include task details", required = false)
                    Boolean includeTasks) {
        return workflowService.getWorkflows(
                name,
                correlationId,
                includeClosed != null ? includeClosed : false,
                includeTasks != null ? includeTasks : false);
    }
}
