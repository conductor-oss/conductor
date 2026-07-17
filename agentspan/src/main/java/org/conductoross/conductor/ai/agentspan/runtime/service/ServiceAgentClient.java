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

import org.conductoross.conductor.ai.agent.AgentClient;
import org.conductoross.conductor.ai.agent.AgentEventStream;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentStatusResponse;
import org.conductoross.conductor.common.metadata.agent.CompileResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * In-process {@link AgentClient} backed by {@link AgentService}.
 *
 * <p>Embedded workers use the same client contract as external SDK workers without making loopback
 * HTTP calls through {@code AgentController}.
 */
@Component
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
public class ServiceAgentClient implements AgentClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentService agentService;
    private final AgentStreamRegistry streamRegistry;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public ServiceAgentClient(AgentService agentService, AgentStreamRegistry streamRegistry) {
        this.agentService = agentService;
        this.streamRegistry = streamRegistry;
    }

    @Override
    public CompileResponse compileAgent(AgentStartRequest request) {
        return agentService.compile(request);
    }

    @Override
    public AgentStartResponse deployAgent(AgentStartRequest request) {
        return agentService.deploy(request);
    }

    @Override
    public AgentStartResponse startAgent(AgentStartRequest request) {
        return agentService.start(request);
    }

    @Override
    public AgentStatusResponse getAgentStatus(String executionId) {
        return agentService.getStatus(executionId);
    }

    @Override
    public Map<String, Object> getExecution(String executionId) {
        return objectMapper.convertValue(agentService.getExecution(executionId), MAP_TYPE);
    }

    @Override
    public Map<String, Object> listExecutions(Map<String, Object> params) {
        Map<String, Object> values = params != null ? params : Map.of();
        return agentService.searchAgentExecutions(
                intValue(values.get("start"), 0),
                intValue(values.get("size"), 20),
                stringValue(values.getOrDefault("sort", "startTime:DESC")),
                stringValue(values.get("freeText")),
                stringValue(values.get("status")),
                stringValue(values.get("agentName")),
                stringValue(values.get("sessionId")),
                stringValue(values.get("classifier")));
    }

    @Override
    public void respond(String executionId, Map<String, Object> body) {
        agentService.respond(executionId, body);
    }

    @Override
    public void stopAgent(String executionId) {
        agentService.stopAgent(executionId);
    }

    @Override
    public void signalAgent(String executionId, String message) {
        agentService.signalAgent(executionId, message);
    }

    @Override
    public void cancelAgent(String executionId, String reason) {
        agentService.cancelAgent(executionId, reason);
    }

    @Override
    public AgentEventStream streamSse(String executionId, String lastEventId) {
        return streamRegistry.openStream(executionId, lastEventId);
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                // Use the endpoint default for malformed optional query parameters.
            }
        }
        return defaultValue;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
