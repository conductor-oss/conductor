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
package org.conductoross.conductor.ai.a2a.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * A remote agent's self-description, published at {@code /.well-known/agent-card.json} (v0.3.x+) or
 * {@code /.well-known/agent.json} (v0.2.5). Carries identity, the endpoint and transport, declared
 * {@link AgentSkill}s, {@link AgentCapabilities}, and security requirements. Used for discovery.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentCard {

    private String name;
    private String description;
    private String url;
    private String version;
    private String protocolVersion;
    private String preferredTransport;
    private String documentationUrl;
    private String iconUrl;
    private AgentProvider provider;
    private AgentCapabilities capabilities;
    private List<AgentSkill> skills;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;
    private List<AgentInterface> additionalInterfaces;
    private Map<String, Object> securitySchemes;
    private List<Map<String, List<String>>> security;
    private boolean supportsAuthenticatedExtendedCard;
}
