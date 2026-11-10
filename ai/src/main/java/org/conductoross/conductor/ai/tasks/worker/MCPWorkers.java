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
package org.conductoross.conductor.ai.tasks.worker;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.mcp.MCPService;
import org.conductoross.conductor.ai.mcp.McpOptions;
import org.conductoross.conductor.ai.model.MCPListToolsRequest;
import org.conductoross.conductor.ai.model.MCPToolCallRequest;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.conductoross.conductor.core.execution.tasks.TaskCancellationHandler;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker tasks for interacting with MCP (Model Context Protocol) servers.
 *
 * <p>Supports remote (HTTP/HTTPS) MCP servers.
 */
@Slf4j
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class MCPWorkers implements AnnotatedSystemTaskWorker, TaskCancellationHandler {

    private final MCPService mcpService;
    private final McpTasksCall mcpTasksCall;

    public MCPWorkers(MCPService mcpService) {
        this.mcpService = mcpService;
        this.mcpTasksCall = new McpTasksCall(mcpService);
        log.debug("MCP Workers initialized");
    }

    /**
     * Lists all available tools from an MCP server.
     *
     * <p>Supports HTTP/HTTPS servers: "http://localhost:3000/sse" or "https://api.example.com/mcp"
     *
     * @param request MCP list tools request
     * @return List of tool definitions
     */
    @WorkerTask("LIST_MCP_TOOLS")
    public @OutputParam("tools") List<ToolInfo> listTools(MCPListToolsRequest request) {
        log.debug("Listing MCP tools from server: {}", request.getMcpServer());

        List<McpSchema.Tool> tools =
                mcpService.listTools(request.getMcpServer(), request.getHeaders());

        log.debug("Found {} tools from MCP server", tools.size());

        // Convert to simplified ToolInfo for output
        return tools.stream().map(ToolInfo::from).collect(Collectors.toList());
    }

    /**
     * Calls a specific tool on an MCP server.
     *
     * <p>Tool arguments are taken only from the {@code arguments} input. Without {@code
     * mcpOptions.protocolVersion}, the call is a single synchronous {@code tools/call}, exactly as
     * before MCP Tasks support. With {@code protocolVersion: "2026-07-28"}, the call uses that
     * protocol and declares the MCP Tasks extension; the server decides whether it answers
     * immediately or creates a task to poll. {@code mcpOptions} is validated before any request; an
     * invalid value fails the task with a terminal error, because a retry cannot fix it.
     *
     * @param request MCP tool call request
     * @return Tool call result
     */
    @WorkerTask("CALL_MCP_TOOL")
    public ToolCallResult callTool(MCPToolCallRequest request) {
        String protocolVersion;
        try {
            protocolVersion = McpOptions.protocolVersion(request.getMcpOptions());
        } catch (IllegalArgumentException e) {
            throw new NonRetryableException(e.getMessage());
        }
        if (protocolVersion != null) {
            return mcpTasksCall.run(request);
        }

        log.debug(
                "Calling MCP tool '{}' on server: {} with args: {}",
                request.getMethod(),
                request.getMcpServer(),
                request.getArguments());

        Map<String, Object> result =
                mcpService.callTool(
                        request.getMcpServer(),
                        request.getMethod(),
                        request.getArguments(),
                        request.getHeaders());

        log.debug("MCP tool call completed. IsError: {}", result.get("isError"));

        return ToolCallResult.from(result);
    }

    /**
     * Propagates a Conductor cancellation (for example, workflow termination) to the MCP server: a
     * best-effort {@code tasks/cancel} for a {@code CALL_MCP_TOOL} call that selected a protocol
     * version and is polling a remote task. {@code LIST_MCP_TOOLS} shares this bean and is ignored.
     */
    @Override
    public void cancel(Task task, String reason) {
        if (!"CALL_MCP_TOOL".equals(task.getTaskType())) {
            return;
        }
        mcpTasksCall.cancel(task, reason);
    }

    /** Simplified tool information for output. */
    public static class ToolInfo {
        public String name;
        public String description;
        public Object inputSchema;

        public static ToolInfo from(McpSchema.Tool tool) {
            ToolInfo info = new ToolInfo();
            info.name = tool.name();
            info.description = tool.description();
            info.inputSchema = tool.inputSchema();
            return info;
        }
    }

    /**
     * Persisted polling state of a remote MCP task (output key {@code mcpTask}): an immutable
     * snapshot, written to task output on each run and read back from the output map. Part of the
     * persisted contract: fields never change type or meaning; new optional fields may be added.
     *
     * @param taskId the remote task ID
     * @param pollFailures consecutive transient {@code tasks/get} failures; {@code 0} after a
     *     successful check
     * @param retryCount the Conductor task's {@code retryCount} when the remote task was created.
     *     It tells this execution's remote task apart from one that an earlier execution left in
     *     the output (the engine can reset a task in place, keeping its output, and increments
     *     {@code retryCount} when it does).
     * @param pollIntervalMs the last {@code pollIntervalMs} the server gave, or {@code null} if it
     *     gave none. Reused when a later {@code tasks/get} gives none, and as the base of the wait
     *     after a failed check.
     */
    public record McpTaskState(
            String taskId, Integer pollFailures, Integer retryCount, Long pollIntervalMs) {}

    /** Tool call result for output. */
    public static class ToolCallResult {
        public List<ContentItem> content;
        public Boolean isError;

        /**
         * Polling state of a remote MCP task, for calls that select a protocol version and whose
         * server creates a task. {@code null} otherwise, and then omitted from task output.
         */
        public McpTaskState mcpTask;

        public static ToolCallResult from(McpSchema.CallToolResult result) {
            ToolCallResult callResult = new ToolCallResult();
            callResult.isError = result.isError();
            callResult.content =
                    result.content().stream().map(ContentItem::from).collect(Collectors.toList());
            return callResult;
        }

        @SuppressWarnings("unchecked")
        public static ToolCallResult from(Map<String, Object> resultMap) {
            ToolCallResult callResult = new ToolCallResult();
            callResult.isError = (Boolean) resultMap.get("isError");
            List<Map<String, Object>> contentList =
                    (List<Map<String, Object>>) resultMap.get("content");
            callResult.content =
                    contentList.stream().map(ContentItem::fromMap).collect(Collectors.toList());
            return callResult;
        }
    }

    /** Content item in tool result. */
    public static class ContentItem {
        public String type;
        public String text;
        public String data;
        public String mimeType;
        public Object parsed; // Parsed JSON content when text contains valid JSON

        public static ContentItem from(Object content) {
            ContentItem item = new ContentItem();

            if (content instanceof McpSchema.TextContent) {
                McpSchema.TextContent textContent = (McpSchema.TextContent) content;
                item.type = textContent.type();
                item.text = textContent.text();
            } else if (content instanceof McpSchema.ImageContent) {
                McpSchema.ImageContent imageContent = (McpSchema.ImageContent) content;
                item.type = imageContent.type();
                item.data = imageContent.data();
                item.mimeType = imageContent.mimeType();
            } else if (content instanceof McpSchema.EmbeddedResource) {
                McpSchema.EmbeddedResource resource = (McpSchema.EmbeddedResource) content;
                item.type = resource.type();
                // Handle embedded resource fields
                if (resource.resource() instanceof McpSchema.TextResourceContents) {
                    McpSchema.TextResourceContents textResource =
                            (McpSchema.TextResourceContents) resource.resource();
                    item.text = textResource.text();
                    item.mimeType = textResource.mimeType();
                }
            }

            return item;
        }

        @SuppressWarnings("unchecked")
        public static ContentItem fromMap(Map<String, Object> contentMap) {
            ContentItem item = new ContentItem();
            item.type = (String) contentMap.get("type");
            item.text = (String) contentMap.get("text");
            item.data = (String) contentMap.get("data");
            item.mimeType = (String) contentMap.get("mimeType");
            item.parsed = contentMap.get("parsed"); // Extract the parsed field
            return item;
        }
    }
}
