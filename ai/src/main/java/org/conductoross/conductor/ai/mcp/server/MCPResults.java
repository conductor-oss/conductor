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
package org.conductoross.conductor.ai.mcp.server;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.mcp.MCPJsonRpc;
import org.conductoross.conductor.ai.mcp.MCPResultType;
import org.conductoross.conductor.ai.mcp.MCPTaskStatus;
import org.conductoross.conductor.ai.mcp.server.model.MCPCallToolResult;
import org.conductoross.conductor.ai.mcp.server.model.MCPContent;
import org.conductoross.conductor.ai.mcp.server.model.MCPError;
import org.conductoross.conductor.ai.mcp.server.model.MCPTask;

import com.netflix.conductor.common.run.Workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds the MCP results the server returns for an execution. Client-visible text names a "call"
 * and an "execution id" only, never a workflow or an agent: an MCP client sees tools only.
 */
public class MCPResults {

    private final ObjectMapper mapper;
    private final MCPServerProperties properties;

    /**
     * The properties are checked when Spring binds them: {@link MCPServerProperties#validate()}.
     */
    public MCPResults(ObjectMapper mapper, MCPServerProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /** The {@code CallToolResult} of an ended execution. */
    public MCPCallToolResult callResult(Workflow execution) {
        String externalOutput = execution.getExternalOutputPayloadStoragePath();
        if (execution.getStatus() == Workflow.WorkflowStatus.COMPLETED
                && externalOutput != null
                && !externalOutput.isBlank()) {
            // Not read back; an empty structuredContent would not match the tool's outputSchema.
            return textResult(
                    "The call completed, but its output is in external payload storage, which this"
                            + " server does not read. Read it through the Conductor API. Execution"
                            + " id: "
                            + execution.getWorkflowId(),
                    true);
        }
        if (execution.getStatus() == Workflow.WorkflowStatus.COMPLETED) {
            Map<String, Object> output =
                    execution.getOutput() == null ? Map.of() : execution.getOutput();
            JsonNode structured = mapper.valueToTree(output);
            return new MCPCallToolResult(
                    MCPResultType.COMPLETE.value(),
                    List.of(MCPContent.ofText(mapper.writeValueAsString(structured))),
                    false,
                    structured);
        }
        String text = "The call ended with status " + execution.getStatus();
        // The reason as the engine or a worker wrote it, as the A2A server sends it.
        String reason = execution.getReasonForIncompletion();
        if (reason != null && !reason.isBlank()) {
            text += ": " + reason;
        }
        return textResult(text + ". Execution id: " + execution.getWorkflowId(), true);
    }

    /** The {@code CallToolResult} for a client without Tasks when the blocking cap passes. */
    public MCPCallToolResult stillRunning(Workflow execution) {
        return textResult(
                "The call is still running. Execution id: " + execution.getWorkflowId(), true);
    }

    /** The {@code CallToolResult} when the execution could not be started. */
    public MCPCallToolResult startFailed() {
        return textResult("The call could not be started.", true);
    }

    /** The {@code CallToolResult} when the arguments were rejected, with the reason. */
    public MCPCallToolResult startFailed(String reason) {
        return reason == null
                ? startFailed()
                : textResult("The call could not be started: " + reason, true);
    }

    /** The flat {@code CreateTaskResult} for a running execution. */
    public MCPTask createTask(Workflow execution) {
        return task(execution, MCPTaskStatus.WORKING, null, null, null, MCPResultType.TASK.value());
    }

    /** A running execution, as {@code tasks/get} reports it. */
    public MCPTask working(Workflow execution) {
        return task(execution, MCPTaskStatus.WORKING, null, null, null, null);
    }

    /** A terminated execution, with its reason when there is one. */
    public MCPTask cancelled(Workflow execution) {
        String reason = execution.getReasonForIncompletion();
        return task(
                execution,
                MCPTaskStatus.CANCELLED,
                reason == null || reason.isBlank() ? null : reason,
                null,
                null,
                null);
    }

    /** An ended execution with its {@code CallToolResult}. */
    public MCPTask completed(Workflow execution, MCPCallToolResult result) {
        return task(execution, MCPTaskStatus.COMPLETED, null, result, null, null);
    }

    /** An ended execution whose result could not be built: an internal error. */
    public MCPTask failed(Workflow execution, int code) {
        return task(
                execution,
                MCPTaskStatus.FAILED,
                "The call result could not be read. Execution id: " + execution.getWorkflowId(),
                null,
                new MCPError(code, MCPJsonRpc.INTERNAL_ERROR_MESSAGE, null),
                null);
    }

    /**
     * The task fields every task response carries: {@code taskId}, {@code status}, {@code
     * createdAt}, {@code lastUpdatedAt} (ISO-8601), {@code ttlMs}, and {@code pollIntervalMs} for a
     * working task.
     */
    private MCPTask task(
            Workflow execution,
            MCPTaskStatus status,
            String statusMessage,
            MCPCallToolResult result,
            MCPError error,
            String resultType) {
        Long created = execution.getCreateTime();
        Long updated = execution.getUpdateTime() != null ? execution.getUpdateTime() : created;
        return new MCPTask(
                execution.getWorkflowId(),
                status.value(),
                iso(created),
                iso(updated),
                properties.getTaskTtl().toMillis(),
                status == MCPTaskStatus.WORKING ? properties.getPollIntervalMs() : null,
                statusMessage,
                result,
                error,
                resultType);
    }

    private static MCPCallToolResult textResult(String text, boolean isError) {
        return new MCPCallToolResult(
                MCPResultType.COMPLETE.value(), List.of(MCPContent.ofText(text)), isError, null);
    }

    private static String iso(Long epochMillis) {
        return Instant.ofEpochMilli(epochMillis == null ? System.currentTimeMillis() : epochMillis)
                .toString();
    }
}
