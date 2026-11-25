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
package org.conductoross.conductor.ai.mcp.server.model;

import org.conductoross.conductor.ai.mcp.MCPFields;
import org.conductoross.conductor.ai.mcp.MCPResultType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A task as the server reports it: the flat {@code CreateTaskResult} ({@code resultType} {@code
 * task}) and the {@code tasks/get} result. {@code pollIntervalMs} is set for a working task, {@code
 * result} for a completed one, {@code error} for a failed one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    MCPFields.Task.TASK_ID,
    MCPFields.Task.STATUS,
    MCPFields.Task.CREATED_AT,
    MCPFields.Task.LAST_UPDATED_AT,
    MCPFields.Task.TTL_MS,
    MCPFields.Task.POLL_INTERVAL_MS,
    MCPFields.Task.STATUS_MESSAGE,
    MCPFields.Task.RESULT,
    MCPFields.Task.ERROR,
    MCPResultType.FIELD
})
public record MCPTask(
        String taskId,
        String status,
        String createdAt,
        String lastUpdatedAt,
        long ttlMs,
        Long pollIntervalMs,
        String statusMessage,
        MCPCallToolResult result,
        MCPError error,
        String resultType) {}
