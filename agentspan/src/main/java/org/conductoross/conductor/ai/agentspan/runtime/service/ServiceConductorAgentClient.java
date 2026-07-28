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

import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentClient;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
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
    public ConductorAgentStatusResponse getAgentStatus(String executionId) {
        AgentStatusResponse response = agentService.getStatus(executionId);
        Map<String, Object> pendingTool = response.getPendingTool();
        String pendingToolTaskRefName = value(pendingTool, "taskRefName");
        String pendingToolName = value(pendingTool, "tool_name");
        if (pendingToolName == null) {
            pendingToolName = pendingToolTaskRefName;
        }
        return ConductorAgentStatusResponse.builder()
                .executionId(response.getExecutionId())
                .status(toState(response))
                .complete(response.isComplete())
                .running(response.isRunning())
                .waiting(response.isWaiting())
                .output(response.getOutput())
                .reasonForIncompletion(response.getReasonForIncompletion())
                .pendingTool(pendingTool)
                .pendingToolName(pendingToolName)
                .pendingToolTaskRefName(pendingToolTaskRefName)
                .startTime(timestamp(response.getStartTime()))
                .endTime(timestamp(response.getEndTime()))
                .build();
    }

    @Override
    public void respond(ConductorAgentRespondRequest request) {
        agentService.respond(request.getExecutionId(), request.getBody());
    }

    @Override
    public void cancelAgent(ConductorAgentCancelRequest request) {
        agentService.cancelAgent(request.getExecutionId(), request.getReason());
    }

    private static ConductorAgentState toState(AgentStatusResponse response) {
        if (response.isWaiting()) {
            return ConductorAgentState.WAITING;
        }
        return ConductorAgentState.fromStatus(response.getStatus());
    }

    private static long timestamp(Long value) {
        return value != null ? value : 0L;
    }

    private static String value(Map<String, Object> values, String key) {
        if (values == null || values.get(key) == null) {
            return null;
        }
        String value = values.get(key).toString();
        return value.isBlank() ? null : value;
    }
}
