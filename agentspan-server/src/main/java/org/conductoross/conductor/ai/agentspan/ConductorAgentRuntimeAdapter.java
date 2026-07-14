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
package org.conductoross.conductor.ai.agentspan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentExecution;
import org.conductoross.conductor.ai.agent.ConductorAgentRuntime;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentSummary;
import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.AgentSummary;
import org.conductoross.conductor.ai.agentspan.runtime.model.StartRequest;
import org.conductoross.conductor.ai.agentspan.runtime.model.StartResponse;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bridges the {@code ai}-module {@link ConductorAgentRuntime} interface to AgentSpan's embedded
 * {@link AgentService}.
 *
 * <p>The {@code ai} module defines {@link ConductorAgentRuntime} as an interface-first abstraction
 * with no compile dependency on any runtime implementation. This adapter is the AgentSpan
 * implementation: it translates between the coarse {@code ai}-module types ({@link
 * ConductorAgentStartRequest}, {@link ConductorAgentExecution}, {@link ConductorAgentState}, {@link
 * ConductorAgentSummary}) and the richer AgentSpan runtime types ({@link StartRequest}, {@link
 * StartResponse}, {@link AgentSummary}), delegating the actual orchestration to {@link
 * AgentService}.
 *
 * <p>Registered as a Spring bean only when the embedded runtime is enabled ({@code
 * agentspan.embedded=true}); otherwise the {@code AGENT} (conductor) task receives an empty {@code
 * Optional<ConductorAgentRuntime>} and fails terminally.
 */
public class ConductorAgentRuntimeAdapter implements ConductorAgentRuntime {

    private final AgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public ConductorAgentRuntimeAdapter(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public ConductorAgentExecution start(ConductorAgentStartRequest request) {
        // Resolve the registered agent definition (the AgentSpan config stamped into the
        // WorkflowDef metadata) and rehydrate it into an AgentConfig the runtime can recompile.
        Map<String, Object> agentDef =
                agentService.getAgentDef(request.getAgentName(), request.getAgentVersion());
        AgentConfig agentConfig = objectMapper.convertValue(agentDef, AgentConfig.class);

        StartRequest startRequest =
                StartRequest.builder()
                        .agentConfig(agentConfig)
                        .prompt(request.getPrompt())
                        .sessionId(request.getSessionId())
                        .context(request.getContext())
                        .runId(request.getRunId())
                        .idempotencyKey(request.getIdempotencyKey())
                        .build();

        StartResponse response = agentService.start(startRequest);

        return ConductorAgentExecution.builder()
                .executionId(response.getExecutionId())
                .agentName(response.getAgentName())
                .sessionId(request.getSessionId())
                .state(ConductorAgentState.RUNNING)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConductorAgentExecution getStatus(String executionId) {
        Map<String, Object> status = agentService.getStatus(executionId);

        String statusName = asString(status.get("status"));
        boolean isComplete = asBoolean(status.get("isComplete"));
        boolean isWaiting = asBoolean(status.get("isWaiting"));

        ConductorAgentState state = deriveState(statusName, isComplete, isWaiting);

        Object output = status.get("output");
        Object pendingTool = status.get("pendingTool");

        return ConductorAgentExecution.builder()
                .executionId(asString(status.get("executionId")))
                .state(state)
                .output(output instanceof Map ? (Map<String, Object>) output : null)
                .pendingTool(pendingTool instanceof Map ? (Map<String, Object>) pendingTool : null)
                .reasonForIncompletion(asString(status.get("reasonForIncompletion")))
                .build();
    }

    /**
     * Map the AgentSpan status snapshot onto the coarse {@link ConductorAgentState} the {@code
     * AGENT} task routes on. A waiting execution takes precedence over terminal flags so a paused
     * run surfaces its pending request; otherwise the workflow status determines the terminal
     * outcome.
     */
    private ConductorAgentState deriveState(
            String statusName, boolean isComplete, boolean isWaiting) {
        if (isWaiting) {
            return ConductorAgentState.WAITING;
        }
        if (isComplete && "COMPLETED".equals(statusName)) {
            return ConductorAgentState.COMPLETED;
        }
        if ("FAILED".equals(statusName) || "TIMED_OUT".equals(statusName)) {
            return ConductorAgentState.FAILED;
        }
        if ("TERMINATED".equals(statusName) || "CANCELED".equals(statusName)) {
            return ConductorAgentState.CANCELED;
        }
        return ConductorAgentState.RUNNING;
    }

    @Override
    public void respond(String executionId, Map<String, Object> message) {
        agentService.respond(executionId, message);
    }

    @Override
    public void cancel(String executionId, String reason) {
        agentService.cancelAgent(executionId, reason);
    }

    @Override
    public List<ConductorAgentSummary> listAgents() {
        List<AgentSummary> summaries = agentService.listAgents();
        List<ConductorAgentSummary> result = new ArrayList<>(summaries.size());
        for (AgentSummary summary : summaries) {
            result.add(
                    ConductorAgentSummary.builder()
                            .name(summary.getName())
                            .version(summary.getVersion())
                            .type(summary.getType())
                            .tags(summary.getTags())
                            .createTime(summary.getCreateTime())
                            .updateTime(summary.getUpdateTime())
                            .description(summary.getDescription())
                            .checksum(summary.getChecksum())
                            .build());
        }
        return result;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }
}
