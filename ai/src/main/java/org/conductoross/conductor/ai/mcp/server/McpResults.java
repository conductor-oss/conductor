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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.conductoross.conductor.ai.mcp.McpResultType;
import org.conductoross.conductor.ai.mcp.McpTaskStatus;

import com.netflix.conductor.common.run.Workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the MCP results the server returns for an execution. Client-visible text names a "call"
 * and an "execution id" only, never a workflow or an agent: an MCP client sees tools only.
 */
public class McpResults {

    private final ObjectMapper mapper;
    private final McpServerProperties properties;

    public McpResults(ObjectMapper mapper, McpServerProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /** The {@code CallToolResult} of an ended execution. */
    public ObjectNode callResult(Workflow execution) {
        if (execution.getStatus() == Workflow.WorkflowStatus.COMPLETED) {
            Map<String, Object> output =
                    execution.getOutput() == null ? Map.of() : execution.getOutput();
            JsonNode structured = mapper.valueToTree(output);
            ObjectNode result = textResult(mapper.writeValueAsString(structured), false);
            result.set("structuredContent", structured);
            return result;
        }
        String id = ". Execution id: " + execution.getWorkflowId();
        if (execution.getStatus() == Workflow.WorkflowStatus.TIMED_OUT) {
            // The engine writes this reason itself ("Workflow timed out after ..."), so it would
            // name the kind; say it in tool terms instead.
            return textResult("The call timed out" + id, true);
        }
        String reason = clientReason(execution.getReasonForIncompletion());
        String text =
                "The call ended with status "
                        + execution.getStatus()
                        + (reason == null || reason.isBlank() ? "" : ": " + reason)
                        + id;
        return textResult(text, true);
    }

    /**
     * The reason of a failed or terminated execution as the client may see it. Reasons are data
     * (often user or worker text), but the engine writes some itself and those name the kind: known
     * engine templates are rewritten, and the word "workflow" (with "sub-workflow" and plurals)
     * left in any reason becomes "call". "agent" is left alone, since user text uses it in other
     * senses.
     *
     * @return the rewritten reason, or {@code null} for a blank one
     */
    static String clientReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        Matcher terminate = TERMINATE_TEMPLATE.matcher(reason);
        if (terminate.matches()) {
            return "Stopped by a TERMINATE step with status " + terminate.group(1);
        }
        if (RESTART_TEMPLATE.equals(reason)) {
            return "Error when restarting the call";
        }
        return WORKFLOW_WORD
                .matcher(reason)
                .replaceAll(
                        match -> {
                            String word = match.group();
                            String call = Character.isUpperCase(word.charAt(0)) ? "Call" : "call";
                            return word.toLowerCase(Locale.ROOT).endsWith("s") ? call + "s" : call;
                        });
    }

    /** {@code WorkflowExecutorOps}: a TERMINATE task with no {@code terminationReason}. */
    private static final Pattern TERMINATE_TEMPLATE =
            Pattern.compile("Workflow is (\\w+) by TERMINATE task: \\S+");

    /** {@code WorkflowExecutorOps}: a failed restart. */
    private static final String RESTART_TEMPLATE = "Error when restarting the workflow";

    /** "workflow", "workflows", "sub-workflow", "subworkflow", any case, as a whole word. */
    private static final Pattern WORKFLOW_WORD =
            Pattern.compile("\\b(?:sub[- ]?)?workflows?\\b", Pattern.CASE_INSENSITIVE);

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
        ObjectNode result = task(execution, McpTaskStatus.WORKING);
        result.put(McpResultType.FIELD, McpResultType.TASK.value());
        return result;
    }

    /**
     * The task fields every task response carries: {@code taskId}, {@code status}, {@code
     * createdAt}, {@code lastUpdatedAt} (ISO-8601), {@code ttlMs}, and {@code pollIntervalMs} for a
     * working task.
     */
    public ObjectNode task(Workflow execution, McpTaskStatus status) {
        ObjectNode task = mapper.createObjectNode();
        task.put("taskId", execution.getWorkflowId());
        task.put("status", status.value());
        Long created = execution.getCreateTime();
        Long updated = execution.getUpdateTime() != null ? execution.getUpdateTime() : created;
        task.put("createdAt", iso(created));
        task.put("lastUpdatedAt", iso(updated));
        task.put("ttlMs", properties.getTaskTtlMs());
        if (status == McpTaskStatus.WORKING) {
            task.put("pollIntervalMs", properties.getPollIntervalMs());
        }
        return task;
    }

    private ObjectNode textResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        result.put(McpResultType.FIELD, McpResultType.COMPLETE.value());
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
