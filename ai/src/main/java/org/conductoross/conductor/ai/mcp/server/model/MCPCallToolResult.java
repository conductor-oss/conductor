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

import java.util.List;

import org.conductoross.conductor.ai.mcp.MCPFields;
import org.conductoross.conductor.ai.mcp.MCPResultType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.JsonNode;

/**
 * A {@code CallToolResult}. {@code structuredContent} is the workflow output as sent, so it stays a
 * {@link JsonNode}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    MCPResultType.FIELD,
    MCPFields.ToolResult.CONTENT,
    MCPFields.ToolResult.IS_ERROR,
    MCPFields.ToolResult.STRUCTURED_CONTENT
})
public record MCPCallToolResult(
        String resultType,
        List<MCPContent> content,
        @JsonProperty(MCPFields.ToolResult.IS_ERROR) boolean isError,
        JsonNode structuredContent) {}
