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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.JsonNode;

/** A JSON-RPC error object: in an error response, and in a {@code failed} task. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({MCPFields.Error.CODE, MCPFields.Error.MESSAGE, MCPFields.Error.DATA})
public record MCPError(int code, String message, JsonNode data) {}
