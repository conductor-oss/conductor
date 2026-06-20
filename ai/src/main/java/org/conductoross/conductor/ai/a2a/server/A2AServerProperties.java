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
package org.conductoross.conductor.ai.a2a.server;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration for the A2A server — exposing Conductor workflows as A2A agents.
 *
 * <p>A workflow is exposed iff its name is in {@link #exposedWorkflows} OR its {@code
 * WorkflowDef.metadata} carries {@code a2a.enabled=true}. With neither set, nothing is exposed
 * (opt-in by design).
 */
@Data
@Component
@ConfigurationProperties(prefix = "conductor.a2a.server")
public class A2AServerProperties {

    /** Master switch (also enforced by {@code A2AServerEnabledCondition}). */
    private boolean enabled = false;

    /** URL path prefix under which agents are served. Each workflow is at {basePath}/{name}. */
    private String basePath = "/a2a";

    /** Workflow names explicitly exposed as agents (in addition to metadata opt-in). */
    private List<String> exposedWorkflows = new ArrayList<>();

    /**
     * Externally-reachable base URL (scheme://host[:port]) advertised in the Agent Card's {@code
     * url}. If blank, it is derived from the incoming request.
     */
    private String publicUrl;

    /** Provider organization advertised on the Agent Card. */
    private String providerOrganization = "Conductor";

    /** Optional shared secret. When set, card + JSON-RPC endpoints require it as a Bearer token. */
    private String apiKey;

    /** Default accepted input media types for derived skills. */
    private List<String> defaultInputModes =
            new ArrayList<>(List.of("application/json", "text/plain"));

    /** Default produced output media types for derived skills. */
    private List<String> defaultOutputModes =
            new ArrayList<>(List.of("application/json", "text/plain"));
}
