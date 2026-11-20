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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.conductoross.conductor.ai.mcp.MCPResultType;
import org.conductoross.conductor.ai.mcp.MCPTaskStatus;

import com.netflix.conductor.common.run.Workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the MCP results the server returns for an execution. Client-visible text names a "call"
 * and an "execution id" only, never a workflow or an agent: an MCP client sees tools only.
 */
public class MCPResults {

    private final ObjectMapper mapper;
    private final MCPServerProperties properties;

    public MCPResults(ObjectMapper mapper, MCPServerProperties properties) {
        Duration ttl = properties.getTaskTtl();
        if (ttl == null || ttl.toMillis() <= 0) {
            throw new IllegalArgumentException(
                    "conductor.mcp.server.taskTtl must be positive, but is " + ttl);
        }
        if (properties.getPollIntervalMs() <= 0) {
            throw new IllegalArgumentException(
                    "conductor.mcp.server.pollIntervalMs must be positive, but is "
                            + properties.getPollIntervalMs());
        }
        this.mapper = mapper;
        this.properties = properties;
    }

    /** The {@code CallToolResult} of an ended execution. */
    public ObjectNode callResult(Workflow execution) {
        String externalOutput = execution.getExternalOutputPayloadStoragePath();
        if (execution.getStatus() == Workflow.WorkflowStatus.COMPLETED
                && externalOutput != null
                && !externalOutput.isBlank()) {
            // The output was moved to external payload storage and is not read back here. An empty
            // structuredContent would not match the tool's outputSchema, so this is an error the
            // caller can act on.
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
            ObjectNode result = textResult(mapper.writeValueAsString(structured), false);
            result.set("structuredContent", structured);
            return result;
        }
        String id = ". Execution id: " + execution.getWorkflowId();
        // The reason as the engine or a worker wrote it, as the A2A server sends it.
        String reason = execution.getReasonForIncompletion();
        String text =
                "The call ended with status "
                        + execution.getStatus()
                        + (reason == null || reason.isBlank() ? "" : ": " + reason)
                        + id;
        return textResult(text, true);
    }

    /** The {@code CallToolResult} for a client without Tasks when the blocking cap passes. */
    public ObjectNode stillRunning(Workflow execution) {
        return textResult(
                "The call is still running. Execution id: " + execution.getWorkflowId(), true);
    }

    /** The {@code CallToolResult} when the execution could not be started. */
    public ObjectNode startFailed() {
        return textResult("The call could not be started.", true);
    }

    /** The {@code CallToolResult} when the arguments were rejected, with the reason. */
    public ObjectNode startFailed(String reason) {
        return reason == null
                ? startFailed()
                : textResult("The call could not be started: " + reason, true);
    }

    /** The flat {@code CreateTaskResult} for a running execution. */
    public ObjectNode createTask(Workflow execution) {
        ObjectNode result = task(execution, MCPTaskStatus.WORKING);
        result.put(MCPResultType.FIELD, MCPResultType.TASK.value());
        return result;
    }

    /**
     * The task fields every task response carries: {@code taskId}, {@code status}, {@code
     * createdAt}, {@code lastUpdatedAt} (ISO-8601), {@code ttlMs}, and {@code pollIntervalMs} for a
     * working task.
     */
    public ObjectNode task(Workflow execution, MCPTaskStatus status) {
        ObjectNode task = mapper.createObjectNode();
        task.put("taskId", execution.getWorkflowId());
        task.put("status", status.value());
        Long created = execution.getCreateTime();
        Long updated = execution.getUpdateTime() != null ? execution.getUpdateTime() : created;
        task.put("createdAt", iso(created));
        task.put("lastUpdatedAt", iso(updated));
        task.put("ttlMs", properties.getTaskTtl().toMillis());
        if (status == MCPTaskStatus.WORKING) {
            task.put("pollIntervalMs", properties.getPollIntervalMs());
        }
        return task;
    }

    private ObjectNode textResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        result.put(MCPResultType.FIELD, MCPResultType.COMPLETE.value());
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", text);
        result.put("isError", isError);
        return result;
    }

    private static String iso(Long epochMillis) {
        return Instant.ofEpochMilli(epochMillis == null ? System.currentTimeMillis() : epochMillis)
                .toString();
    }
}
