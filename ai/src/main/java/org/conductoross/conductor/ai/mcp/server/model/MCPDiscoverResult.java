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
import java.util.Map;

import org.conductoross.conductor.ai.mcp.MCPFields;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The {@code server/discover} result. {@code _meta} carries the server info under its key. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    MCPFields.Session.SUPPORTED_VERSIONS,
    MCPFields.Session.CAPABILITIES,
    MCPFields.Session.META
})
public record MCPDiscoverResult(
        List<String> supportedVersions,
        MCPCapabilities capabilities,
        @JsonProperty(MCPFields.Session.META) Map<String, MCPServerInfo> meta) {}
