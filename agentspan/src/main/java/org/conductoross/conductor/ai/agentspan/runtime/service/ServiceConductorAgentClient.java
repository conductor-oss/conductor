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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentStatusResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * In-process {@link ConductorAgentClient} backed by {@link AgentService}.
 *
 * <p>Embedded workers use the same client contract as external SDK workers without making loopback
 * HTTP calls through {@code AgentController}.
 */
@Component
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
public class ServiceConductorAgentClient implements ConductorAgentClient {

    private final AgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public ServiceConductorAgentClient(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
        AgentStartRequest serviceRequest =
                objectMapper.convertValue(request, AgentStartRequest.class);
        AgentStartResponse response = agentService.start(serviceRequest);
        return objectMapper.convertValue(response, ConductorAgentStartResponse.class);
    }

    @Override
    public ConductorAgentStatusResponse getAgentStatus(ConductorAgentStatusRequest request) {
        AgentStatusResponse response = agentService.getStatus(request.getExecutionId());
        return objectMapper.convertValue(response, ConductorAgentStatusResponse.class);
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        agentService.respond(request.getExecutionId(), request.getBody());
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        agentService.cancelAgent(request.getExecutionId(), request.getReason());
    }
}
