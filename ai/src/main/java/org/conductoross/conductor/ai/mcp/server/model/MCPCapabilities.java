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

import java.util.Map;

import org.conductoross.conductor.ai.mcp.MCPFields;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Capabilities: the server's own (tools, and the Tasks extension on {@code 2026-07-28}), or the
 * ones a request lacks. Each capability is an empty object; an absent one is left out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({MCPFields.Session.TOOLS, MCPFields.Session.EXTENSIONS})
public record MCPCapabilities(Map<String, Object> tools, Map<String, Object> extensions) {

    /** Only the tools capability, as {@code initialize} reports it. */
    public static MCPCapabilities toolsOnly() {
        return new MCPCapabilities(Map.of(), null);
    }

    /** One extension and no tools: what a request without that extension lacks. */
    public static MCPCapabilities extension(String extension) {
        return new MCPCapabilities(null, Map.of(extension, Map.of()));
    }

    /** The tools capability and one extension, as {@code server/discover} reports them. */
    public static MCPCapabilities toolsAndExtension(String extension) {
        return new MCPCapabilities(Map.of(), Map.of(extension, Map.of()));
    }
}
